package io.github.ldev22live.gdxunity.editor.core.project;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Exports a project as a standalone game (Phase 18-C, TD-046; layouts for Linux and macOS in TD-047): a launcher named
 * after the game, a bundled Java runtime and the game runtime's jars, plus the project's scenes and assets and its compiled
 * scripts. The player needs nothing installed.
 * <p>
 * The runtime comes from the packaged editor: its app image holds a second launcher, {@value #LAUNCHER_NAME}, that starts
 * the game runtime instead of the editor ({@code editor-desktop/build.gradle}). Export copies that image without the
 * editor's own jars and renames the launcher, so only a <b>packaged</b> editor can export. The layout of an app image
 * depends on the operating system that built it ({@link Layout}); the layout is recognised from the files, not from the
 * system running the editor. <b>Only the Windows layout has been exercised on a real app image</b>; Linux and macOS are
 * written from jpackage's documented layout and tested against fake images.
 * <p>
 * Result, with {@code <Name>} the game's name (symbols removed) and {@code game/} the project:
 * <pre>
 *   Windows  &lt;Name&gt;.exe  runtime/  app/(jars, &lt;Name&gt;.cfg, game/)
 *   Linux    bin/&lt;Name&gt;  lib/(runtime/, app/(jars, &lt;Name&gt;.cfg, game/), launcher library, &lt;Name&gt;.png)
 *   macOS    &lt;Name&gt;.app/Contents/(MacOS/&lt;Name&gt;, runtime/, app/(jars, &lt;Name&gt;.cfg, game/), Info.plist, Resources/)
 * </pre>
 * {@code game/} holds {@code gdxunity.project} (name, start scene, window icon), scenes and assets, and
 * {@code build/scripts} (compiled scripts).
 */
public final class GameExporter {
    /** Name of the game launcher inside the packaged editor's app image, without extension. */
    public static final String LAUNCHER_NAME = "ForgeroomGame";
    /** Name of the editor launcher in the same image; not copied. */
    public static final String EDITOR_LAUNCHER_NAME = "ForgeroomEditor";
    /** Folder inside the app folder that holds the project. The launcher's {@code gdxunity.project} property points at it. */
    public static final String GAME_FOLDER = "game";
    /** System property that names an app image to export from instead of the running editor's own (tests, development). */
    public static final String TEMPLATE_PROPERTY = "gdxunity.gameTemplate";
    /** System property the jpackage launcher sets to the path of the launcher that started the process. */
    public static final String LAUNCHER_PATH_PROPERTY = "jpackage.app-path";
    /** Format version written to the project file. */
    static final String PROJECT_FORMAT = "1";

    /** The three app image layouts jpackage produces. */
    public enum Layout {
        /** {@code ForgeroomGame.exe}, {@code app/}, {@code runtime/} side by side. */
        WINDOWS,
        /** {@code bin/ForgeroomGame}, {@code lib/app/}, {@code lib/runtime/}. */
        LINUX,
        /** A {@code .app} bundle: {@code Contents/MacOS/ForgeroomGame}, {@code Contents/app/}, {@code Contents/runtime/}. */
        MACOS
    }

    private static final String APP_FOLDER = "app";
    private static final String RUNTIME_FOLDER = "runtime";
    private static final String LIB_FOLDER = "lib";
    private static final String BIN_FOLDER = "bin";
    private static final String CONTENTS_FOLDER = "Contents";
    private static final String MACOS_FOLDER = "MacOS";
    private static final String RESOURCES_FOLDER = "Resources";
    private static final String CONFIG_EXTENSION = ".cfg";
    private static final String WINDOWS_EXTENSION = ".exe";
    private static final String APP_BUNDLE_EXTENSION = ".app";
    private static final String INFO_PLIST = "Info.plist";
    /** Editor-only entries of the app folder: never part of a game. */
    private static final String EDITOR_JAR_PREFIX = "editor-";
    private static final String SAMPLE_PROJECT_FOLDER = "sample-project";
    /** Jars of the Kotlin compiler, which only the editor uses; compiled Kotlin scripts need just the standard library. */
    private static final List<String> KOTLIN_COMPILER_JAR_PREFIXES = List.of(
        "kotlin-compiler", "kotlin-daemon", "kotlin-script-runtime");
    /** Project files the editor keeps for itself. */
    private static final List<String> EDITOR_FILES = List.of(
        "assets.txt", "workspace_layout.json", "editor_theme.json", "scene_backup.json");
    private static final String BUILD_FOLDER = "build";
    private static final String SCRIPTS_FOLDER = "scripts";
    /** The window icon of a game must be a PNG: it is handed to the window system as is. */
    private static final String WINDOW_ICON_EXTENSION = ".png";

    private GameExporter() {}

    /**
     * What an export produced.
     *
     * @param folder       the folder the game was written to
     * @param executable   the game's launcher
     * @param projectFiles number of project files copied (the project file itself not counted)
     */
    public record Result(File folder, File executable, int projectFiles) {}

    /** Where the pieces of an app image are, for one layout. */
    record Image(Layout layout, File root) {
        /** Base folder holding {@code app} and {@code runtime} (inside the bundle on macOS, {@code lib} on Linux). */
        File base() {
            return switch (layout) {
                case WINDOWS -> root;
                case LINUX -> new File(root, LIB_FOLDER);
                case MACOS -> new File(root, CONTENTS_FOLDER);
            };
        }

        File appDir() {
            return new File(base(), APP_FOLDER);
        }

        File runtimeDir() {
            return new File(base(), RUNTIME_FOLDER);
        }

        File launcher(String name) {
            return switch (layout) {
                case WINDOWS -> new File(root, name + WINDOWS_EXTENSION);
                case LINUX -> new File(new File(root, BIN_FOLDER), name);
                case MACOS -> new File(new File(new File(root, CONTENTS_FOLDER), MACOS_FOLDER), name);
            };
        }

        File config(String name) {
            return new File(appDir(), name + CONFIG_EXTENSION);
        }
    }

    /**
     * The app image to export from: {@value #TEMPLATE_PROPERTY} if set, else the root of the image of the launcher that
     * started this editor ({@value #LAUNCHER_PATH_PROPERTY}), else null (a development run has no packaged runtime).
     * The root is the launcher's folder on Windows, the folder above {@code bin} on Linux and the {@code .app} bundle on macOS.
     */
    public static File locateTemplate() {
        String override = System.getProperty(TEMPLATE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return new File(override);
        }
        String launcher = System.getProperty(LAUNCHER_PATH_PROPERTY);
        return launcher == null || launcher.isBlank() ? null : imageRootOf(new File(launcher).getAbsoluteFile());
    }

    /** The app image root for the path of one of its launchers, recognised by the folder the launcher sits in. */
    static File imageRootOf(File launcher) {
        File folder = launcher.getParentFile();
        if (folder != null && folder.getName().equals(MACOS_FOLDER) && folder.getParentFile() != null
            && folder.getParentFile().getParentFile() != null) {
            return folder.getParentFile().getParentFile();
        }
        if (folder != null && folder.getName().equals(BIN_FOLDER) && folder.getParentFile() != null) {
            return folder.getParentFile();
        }
        return folder;
    }

    /** A file name made of letters, digits, spaces, dashes and underscores; "Game" when nothing is left. */
    public static String safeName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        return cleaned.isEmpty() ? "Game" : cleaned;
    }

    /** As {@link #export(EditorProject, String, String, String, File, File)} with no window icon of its own. */
    public static Result export(EditorProject project, String startScene, String gameName, File template, File outputDir)
        throws IOException {
        return export(project, startScene, gameName, "", template, outputDir);
    }

    /**
     * Writes the game to {@code outputDir}.
     *
     * @param project    the project to export; its scripts must already be compiled
     * @param startScene scene the game starts with, relative to the project (forward or back slashes)
     * @param gameName   title of the game; also the launcher's name (symbols removed)
     * @param windowIcon PNG inside the project used as the game window's icon, or an empty string for the Forgeroom icon
     * @param template   app image of the packaged editor, see {@link #locateTemplate()}
     * @param outputDir  folder to create; it may exist only if it is empty, and must not be inside the project
     * @throws IOException if anything is missing or wrong; the message says what and where. Nothing is overwritten.
     */
    public static Result export(EditorProject project, String startScene, String gameName, String windowIcon, File template,
                                File outputDir) throws IOException {
        Objects.requireNonNull(project, "project cannot be null");
        Objects.requireNonNull(startScene, "startScene cannot be null");
        Objects.requireNonNull(gameName, "gameName cannot be null");
        Objects.requireNonNull(windowIcon, "windowIcon cannot be null");
        Objects.requireNonNull(outputDir, "outputDir cannot be null");
        String scene = checkStartScene(project, startScene);
        String icon = checkWindowIcon(project, windowIcon);
        requireCompiledScripts(project);
        Image image = requireTemplate(template);
        requireFreeOutput(project, outputDir);

        String launcher = safeName(gameName);
        Path out = outputDir.toPath().toAbsolutePath();
        Files.createDirectories(out);
        // On macOS the game is a bundle inside the output folder; elsewhere the output folder is the game
        Path root = image.layout() == Layout.MACOS ? out.resolve(launcher + APP_BUNDLE_EXTENSION) : out;
        Image target = new Image(image.layout(), root.toFile());
        Files.createDirectories(root);

        copyTree(image.runtimeDir().toPath(), target.runtimeDir().toPath(), path -> true);
        copyTree(image.appDir().toPath(), target.appDir().toPath(), GameExporter::isGameRuntimeFile);
        Files.createDirectories(target.launcher(launcher).getParentFile().toPath());
        Files.copy(image.launcher(LAUNCHER_NAME).toPath(), target.launcher(launcher).toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        // The launcher finds its config by its own name; the template's name must not be left behind
        Files.copy(image.config(LAUNCHER_NAME).toPath(), target.config(launcher).toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(target.config(LAUNCHER_NAME).toPath());
        copyLayoutExtras(image, target, launcher);

        Path game = target.appDir().toPath().resolve(GAME_FOLDER);
        int copied = copyProject(project.getRoot().toPath(), game);
        writeProjectFile(game.resolve(EditorProject.MARKER_FILE), gameName, scene, icon);
        return new Result(out.toFile(), target.launcher(launcher), copied);
    }

    /** The start scene as a project-relative path with forward slashes; fails if it is outside the project or missing. */
    static String checkStartScene(EditorProject project, String startScene) throws IOException {
        if (startScene.isBlank()) {
            throw new IOException("No start scene given. Choose the scene the game should start with.");
        }
        return projectFile(project, startScene, "start scene");
    }

    /** The window icon as a project-relative path, or an empty string for none; it must be a PNG inside the project. */
    static String checkWindowIcon(EditorProject project, String windowIcon) throws IOException {
        if (windowIcon.isBlank()) {
            return "";
        }
        String relative = projectFile(project, windowIcon, "window icon");
        if (!relative.toLowerCase(Locale.ROOT).endsWith(WINDOW_ICON_EXTENSION)) {
            throw new IOException("The window icon must be a " + WINDOW_ICON_EXTENSION + " file: " + windowIcon);
        }
        return relative;
    }

    private static String projectFile(EditorProject project, String path, String what) throws IOException {
        Path root = project.getRoot().toPath().toAbsolutePath().normalize();
        Path file = root.resolve(path.replace('\\', '/')).normalize();
        if (!file.startsWith(root)) {
            throw new IOException("The " + what + " must be inside the project: " + path);
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("The " + what + " does not exist: " + file);
        }
        return root.relativize(file).toString().replace('\\', '/');
    }

    /** Scripts that were not compiled would be silently missing from the game, so their absence is an error. */
    static void requireCompiledScripts(EditorProject project) throws IOException {
        if (!hasFile(project.getScriptsDir().toPath(), name -> name.endsWith(".java") || name.endsWith(".kt"))) {
            return;
        }
        if (!hasFile(project.getScriptsBuildDir().toPath(), name -> name.endsWith(".class"))) {
            throw new IOException("The project has scripts but " + project.getScriptsBuildDir()
                + " holds no compiled classes. Compile the scripts first (press Play once).");
        }
    }

    private static boolean hasFile(Path folder, Predicate<String> nameFilter) throws IOException {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile).anyMatch(file -> nameFilter.test(file.getFileName().toString().toLowerCase(Locale.ROOT)));
        }
    }

    /**
     * Recognises the layout of {@code template} from the files it holds.
     *
     * @throws IOException if {@code template} is not a packaged editor's app image with a game launcher
     */
    static Image requireTemplate(File template) throws IOException {
        if (template == null) {
            throw new IOException("This editor has no game runtime to copy. Export from the packaged editor "
                + "(ForgeroomEditor), not from a development run.");
        }
        for (Layout layout : Layout.values()) {
            Image candidate = new Image(layout, template);
            if (candidate.launcher(LAUNCHER_NAME).isFile() && candidate.config(LAUNCHER_NAME).isFile()
                && candidate.runtimeDir().isDirectory() && candidate.appDir().isDirectory()) {
                return candidate;
            }
        }
        throw new IOException(template.getAbsolutePath() + " is not a packaged Forgeroom editor with a game launcher. Expected "
            + LAUNCHER_NAME + ".exe with app/ and runtime/ (Windows), bin/" + LAUNCHER_NAME + " with lib/app/ and lib/runtime/ "
            + "(Linux), or Contents/MacOS/" + LAUNCHER_NAME + " with Contents/app/ and Contents/runtime/ (macOS).");
    }

    private static void requireFreeOutput(EditorProject project, File outputDir) throws IOException {
        Path out = outputDir.toPath().toAbsolutePath().normalize();
        Path root = project.getRoot().toPath().toAbsolutePath().normalize();
        if (out.startsWith(root)) {
            throw new IOException("Export the game outside the project folder: " + out);
        }
        if (Files.isRegularFile(out)) {
            throw new IOException(out + " is a file, not a folder");
        }
        if (Files.isDirectory(out)) {
            try (Stream<Path> entries = Files.list(out)) {
                if (entries.findAny().isPresent()) {
                    throw new IOException(out + " is not empty. Choose a new folder; nothing is overwritten.");
                }
            }
        }
    }

    /** Whether an entry of the editor's app folder belongs in a game. */
    private static boolean isGameRuntimeFile(Path path) {
        String name = path.getFileName().toString();
        return !name.startsWith(EDITOR_JAR_PREFIX) && !name.equals(SAMPLE_PROJECT_FOLDER)
            && KOTLIN_COMPILER_JAR_PREFIXES.stream().noneMatch(name::startsWith)
            && !name.equals(EDITOR_LAUNCHER_NAME + CONFIG_EXTENSION) && !name.equals(LAUNCHER_NAME + CONFIG_EXTENSION);
    }

    /**
     * The files of an app image outside {@code app} and {@code runtime}: on Linux the launcher library and the icon in
     * {@code lib}, on macOS {@code Info.plist}, {@code PkgInfo} and the icons in {@code Resources}. Whatever is named after
     * the editor is left out, and whatever is named after the game launcher takes the game's name.
     */
    private static void copyLayoutExtras(Image from, Image to, String name) throws IOException {
        switch (from.layout()) {
            case WINDOWS -> { }
            case LINUX -> copyFlat(from.base(), to.base(), name);
            case MACOS -> {
                File fromResources = new File(from.base(), RESOURCES_FOLDER);
                if (fromResources.isDirectory()) {
                    Files.createDirectories(new File(to.base(), RESOURCES_FOLDER).toPath());
                    copyFlat(fromResources, new File(to.base(), RESOURCES_FOLDER), name);
                }
                File pkgInfo = new File(from.base(), "PkgInfo");
                if (pkgInfo.isFile()) {
                    Files.copy(pkgInfo.toPath(), new File(to.base(), "PkgInfo").toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
                File plist = new File(from.base(), INFO_PLIST);
                if (plist.isFile()) {
                    String text = Files.readString(plist.toPath(), StandardCharsets.UTF_8);
                    Files.writeString(new File(to.base(), INFO_PLIST).toPath(), rewriteInfoPlist(text, name), StandardCharsets.UTF_8);
                }
            }
        }
    }

    /** Copies the files directly inside {@code from}: editor-named ones are skipped, game-launcher-named ones renamed. */
    private static void copyFlat(File from, File to, String name) throws IOException {
        File[] files = from.listFiles(File::isFile);
        if (files == null) {
            return;
        }
        Files.createDirectories(to.toPath());
        for (File file : files) {
            String fileName = file.getName();
            if (fileName.startsWith(EDITOR_LAUNCHER_NAME)) {
                continue;
            }
            String targetName = fileName.startsWith(LAUNCHER_NAME) ? name + fileName.substring(LAUNCHER_NAME.length()) : fileName;
            Files.copy(file.toPath(), new File(to, targetName).toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    /**
     * The editor bundle's {@code Info.plist} turned into the game's: the executable, names, identifier and icon file name
     * follow the game. Each is a {@code <key>} followed by a {@code <string>}; keys the file lacks are left alone.
     */
    static String rewriteInfoPlist(String plist, String name) {
        String identifier = "forgeroom.game." + name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        String result = setPlistString(plist, "CFBundleExecutable", name);
        result = setPlistString(result, "CFBundleName", name);
        result = setPlistString(result, "CFBundleDisplayName", name);
        result = setPlistString(result, "CFBundleIdentifier", identifier);
        return setPlistString(result, "CFBundleIconFile", name + ".icns");
    }

    private static String setPlistString(String plist, String key, String value) {
        return plist.replaceAll("(<key>" + key + "</key>\\s*<string>)[^<]*(</string>)", "$1" + java.util.regex.Matcher.quoteReplacement(value) + "$2");
    }

    private static void copyTree(Path source, Path target, Predicate<Path> include) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(source) && !include.test(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (include.test(file)) {
                    Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Copies the project without what a game does not need: script sources (their classes are in {@code build/scripts}),
     * other build output, hidden entries and the editor's own settings. Returns the number of files copied.
     */
    private static int copyProject(Path root, Path target) throws IOException {
        int[] copied = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(root) && !wanted(root.relativize(dir), true)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(root.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (wanted(root.relativize(file), false)) {
                    Files.copy(file, target.resolve(root.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                    copied[0]++;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return copied[0] - (Files.exists(target.resolve(EditorProject.MARKER_FILE)) ? 1 : 0);
    }

    /** Whether a project-relative entry is part of the game. */
    private static boolean wanted(Path relative, boolean directory) {
        String first = relative.getName(0).toString();
        if (first.startsWith(".")) {
            return false;
        }
        if (relative.getNameCount() == 1) {
            if (directory) {
                // Only the compiled scripts survive of the build folder; script sources are not shipped
                return !first.equals(SCRIPTS_FOLDER);
            }
            return !EDITOR_FILES.contains(first);
        }
        if (first.equals(BUILD_FOLDER)) {
            // build/scripts and below only
            return relative.getName(1).toString().equals(SCRIPTS_FOLDER);
        }
        return true;
    }

    private static void writeProjectFile(Path file, String name, String startScene, String windowIcon) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("format", PROJECT_FORMAT);
        properties.setProperty("name", name);
        properties.setProperty("startScene", startScene);
        if (!windowIcon.isEmpty()) {
            properties.setProperty("windowIcon", windowIcon);
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            properties.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), "Forgeroom game (exported)");
        }
    }
}
