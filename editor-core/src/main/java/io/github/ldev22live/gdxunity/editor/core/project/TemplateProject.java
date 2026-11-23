package io.github.ldev22live.gdxunity.editor.core.project;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * "Use as Template" (roadmap M1.3, Appendix F1): makes a separate, self-contained project from one of a project's sample scenes, so the
 * person can change everything in it and the shipped sample stays as it was. The new project holds the scene as its start scene and
 * every file the game needs, found by following what refers to what: the scene's file names, then the file names inside those files
 * (a graph that names a sound, a material that names a texture, a glTF that names its buffers), and for Java scripts the other scripts
 * they import or sit beside and the files they open by name. A template must not lean on sample code that is not in it, so a script
 * that imports another sample's shared code brings that code along.
 *
 * <p>Nothing is ever written into the source project, and an existing folder is never overwritten: the new project gets a name no
 * other folder has. If anything fails the half-made folder is removed.
 */
public final class TemplateProject {
    /** Folder of the new project that holds its scene. */
    public static final String SCENE_FOLDER = "scenes";
    /** Where the scripts are, relative to a project. */
    private static final String SCRIPTS_FOLDER = "scripts/";
    /** An import of another sample's code: {@code import samples.common.SideBody;}. */
    private static final Pattern SAMPLE_IMPORT = Pattern.compile("^\\s*import\\s+samples\\.([\\w.]+)\\.(\\w+)\\s*;", Pattern.MULTILINE);
    /** A string literal in source code; the ones that name an existing file are files the script opens. */
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"\\\\\\n]+)\"");
    /** A binary glTF's magic number ("glTF"), little-endian. */
    private static final int GLB_MAGIC = 0x46546C67;
    /** The chunk type of a binary glTF's JSON chunk ("JSON"). */
    private static final int GLB_JSON_CHUNK = 0x4E4F534A;
    /** The largest JSON chunk read from a binary glTF; a real one is a few hundred kilobytes at most. */
    private static final int GLB_JSON_LIMIT = 16 * 1024 * 1024;
    /** Files whose text is searched for the names of other files. */
    private static final List<String> TEXT_EXTENSIONS = List.of("json", "graph", "material", "anim", "prefab", "song", "terrain",
        "sprite", "sculpt", "gltf", "project");

    /**
     * What was made.
     *
     * @param folder     the new project's folder
     * @param startScene the scene it opens with, relative to the project
     * @param files      every project-relative path copied, in name order, the scene included
     */
    public record Result(File folder, String startScene, List<String> files) {
        public Result {
            Objects.requireNonNull(folder, "folder cannot be null");
            Objects.requireNonNull(startScene, "startScene cannot be null");
            files = List.copyOf(files);
        }
    }

    private TemplateProject() { }

    /**
     * What {@link #create} would copy for a sample, without writing anything: for the template gallery's size (roadmap M1.4).
     *
     * @param files every project-relative path the template needs, the scene included, in name order
     * @param bytes their total size on disk
     */
    public record Plan(List<String> files, long bytes) {
        public Plan {
            files = List.copyOf(files);
        }
    }

    /**
     * Lists the files a template made from {@code scenePath} would hold.
     *
     * @throws IOException if the scene is not a scene file inside {@code sourceRoot}, or a file it names cannot be read
     */
    public static Plan plan(File sourceRoot, String scenePath) throws IOException {
        Objects.requireNonNull(sourceRoot, "sourceRoot cannot be null");
        Objects.requireNonNull(scenePath, "scenePath cannot be null");
        Path root = sourceRoot.toPath().toAbsolutePath().normalize();
        Path scene = root.resolve(scenePath).normalize();
        if (!scene.startsWith(root) || !Files.isRegularFile(scene) || !scene.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new IOException("'" + scenePath + "' is not a scene file inside " + root);
        }
        TreeSet<String> needed = findDependencies(root, scenePath.replace('\\', '/'));
        long bytes = 0;
        for (String relative : needed) {
            bytes += Files.size(root.resolve(relative));
        }
        return new Plan(new ArrayList<>(needed), bytes);
    }

    /**
     * Makes a project from a sample scene.
     *
     * @param sourceRoot  the project that holds the sample (its asset folder)
     * @param scenePath   the sample scene, relative to {@code sourceRoot}, for example {@code samples/hopper-3d.json}
     * @param projectsDir where the new project goes; it is created if it does not exist
     * @param name        what to call the project; a letter-and-digit name is made from it, and a number is added if the folder exists
     * @throws IOException if the scene cannot be read or the project cannot be written; nothing is left behind
     */
    public static Result create(File sourceRoot, String scenePath, File projectsDir, String name) throws IOException {
        Objects.requireNonNull(sourceRoot, "sourceRoot cannot be null");
        Objects.requireNonNull(scenePath, "scenePath cannot be null");
        Objects.requireNonNull(projectsDir, "projectsDir cannot be null");
        Path root = sourceRoot.toPath().toAbsolutePath().normalize();
        Path scene = root.resolve(scenePath).normalize();
        if (!scene.startsWith(root) || !Files.isRegularFile(scene) || !scene.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new IOException("'" + scenePath + "' is not a scene file inside " + root);
        }
        String sceneName = scene.getFileName().toString();
        String base = sceneName.substring(0, sceneName.length() - ".json".length());
        String startScene = SCENE_FOLDER + "/" + sceneName;

        TreeSet<String> needed = findDependencies(root, scenePath.replace('\\', '/'));
        needed.remove(scenePath.replace('\\', '/'));
        boolean licences = false;

        Files.createDirectories(projectsDir.toPath());
        Path folder = uniqueFolder(projectsDir.toPath(), name == null || name.isBlank() ? base : name);
        try {
            Files.createDirectory(folder);
            Files.createDirectories(folder.resolve(SCENE_FOLDER));
            Files.copy(scene, folder.resolve(startScene), StandardCopyOption.COPY_ATTRIBUTES);
            for (String relative : needed) {
                Path target = folder.resolve(relative).normalize();
                Files.createDirectories(target.getParent());
                Files.copy(root.resolve(relative), target, StandardCopyOption.COPY_ATTRIBUTES);
            }
            Files.writeString(folder.resolve(EditorProject.MARKER_FILE),
                "format=1\nname=" + folder.getFileName() + "\nstartScene=" + startScene + "\n", StandardCharsets.UTF_8);
            // Downloaded files keep their licence records (TD-094, M2b.2)
            if (ProjectLicences.carry(root.toFile(), folder.toFile(), needed)) {
                licences = true;
            }
        } catch (IOException | RuntimeException e) {
            IOException cleanup = deleteTree(folder);
            if (cleanup != null) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
        List<String> files = new ArrayList<>(needed);
        files.add(startScene);
        if (licences) {
            files.add(ThirdPartyManifest.PATH);
        }
        java.util.Collections.sort(files);
        return new Result(folder.toFile(), startScene, files);
    }

    /**
     * The project-relative paths of every file {@code path} needs (itself included), by following its references the way a new
     * project copies them: a scene's prefabs, models and materials, a glTF's buffers and images, a script's helpers. Used by the asset
     * support matrix (roadmap M2b.7) to tell which shipped files the samples use and which models stand alone.
     *
     * @param root the asset or project root
     * @param path a file under {@code root}, with forward slashes
     * @throws IOException if a file on the way cannot be read or followed
     */
    public static Set<String> dependenciesOf(File root, String path) throws IOException {
        Objects.requireNonNull(root, "root cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
        return Collections.unmodifiableSet(findDependencies(root.toPath(), path.replace('\\', '/')));
    }

    /** The project-relative paths of every file the scene needs (the scene itself included), by following the references. */
    static TreeSet<String> findDependencies(Path root, String scenePath) throws IOException {
        TreeSet<String> found = new TreeSet<>();
        Deque<String> queue = new ArrayDeque<>();
        found.add(scenePath);
        queue.add(scenePath);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            Path file = root.resolve(current);
            String extension = extensionOf(current);
            List<String> references = new ArrayList<>();
            if (extension.equals("java") || extension.equals("kt")) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                references.addAll(scriptReferences(root, current, text));
            } else if (TEXT_EXTENSIONS.contains(extension)) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                collectStrings(parse(current, text), root, current, references);
            } else if (extension.equals("glb")) {
                // A binary glTF can name outside files too (Kenney's Building Kit names Textures/colormap.png), in its JSON chunk
                collectStrings(parse(current, glbJson(file, current)), root, current, references);
            }
            for (String reference : references) {
                if (found.add(reference)) {
                    queue.add(reference);
                }
            }
        }
        return found;
    }

    /** The JSON chunk of a binary glTF, which names its images and buffers. */
    static String glbJson(Path file, String name) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            ByteBuffer header = ByteBuffer.wrap(in.readNBytes(20)).order(ByteOrder.LITTLE_ENDIAN);
            if (header.limit() < 20 || header.getInt(0) != GLB_MAGIC || header.getInt(16) != GLB_JSON_CHUNK) {
                throw new IOException("Cannot read " + name + " to find the files it needs: not a binary glTF");
            }
            int length = header.getInt(12);
            if (length < 0 || length > GLB_JSON_LIMIT) {
                throw new IOException("Cannot read " + name + " to find the files it needs: a JSON chunk of " + length + " bytes");
            }
            byte[] json = in.readNBytes(length);
            if (json.length != length) {
                throw new IOException("Cannot read " + name + " to find the files it needs: it ends inside its JSON chunk");
            }
            return new String(json, StandardCharsets.UTF_8);
        }
    }

    /** @throws IOException if the text is not JSON: a file that cannot be read cannot be followed, and guessing would hide a missing file */
    private static JsonValue parse(String file, String text) throws IOException {
        try {
            return new JsonReader().parse(text);
        } catch (RuntimeException e) {
            throw new IOException("Cannot read " + file + " to find the files it needs: " + e.getMessage(), e);
        }
    }

    /** Adds every string in a JSON value that names an existing file, relative to the project or (for glTF buffers) to the file. */
    private static void collectStrings(JsonValue value, Path root, String from, List<String> out) {
        if (value == null) {
            return;
        }
        if (value.isString()) {
            String text = value.asString();
            if (text.length() > 1 && text.length() < 260 && !text.startsWith("data:")) {
                addIfFile(root, text, out);
                String folder = from.contains("/") ? from.substring(0, from.lastIndexOf('/') + 1) : "";
                if (!folder.isEmpty()) {
                    addIfFile(root, folder + text, out);
                }
            }
            return;
        }
        for (JsonValue child = value.child; child != null; child = child.next) {
            collectStrings(child, root, from, out);
        }
    }

    private static void addIfFile(Path root, String relative, List<String> out) {
        String clean = relative.replace('\\', '/');
        if (clean.startsWith("/") || clean.contains("..") || clean.contains(":")) {
            return;
        }
        Path candidate;
        try {
            candidate = root.resolve(clean).normalize();
        } catch (java.nio.file.InvalidPathException e) {
            // Text such as "Wave 1 / 3" is not a valid path on every system, so it cannot name a file
            return;
        }
        if (candidate.startsWith(root) && Files.isRegularFile(candidate)) {
            out.add(root.relativize(candidate).toString().replace('\\', '/'));
        }
    }

    /** What a Java script needs: the scripts beside it (same package), the sample code it imports, and files it opens by name. */
    private static List<String> scriptReferences(Path root, String script, String text) throws IOException {
        List<String> out = new ArrayList<>();
        addSiblings(root, script.substring(0, script.lastIndexOf('/') + 1), out);
        Matcher imports = SAMPLE_IMPORT.matcher(text);
        while (imports.find()) {
            addSiblings(root, SCRIPTS_FOLDER + "samples/" + imports.group(1).replace('.', '/') + "/", out);
        }
        Matcher literals = STRING_LITERAL.matcher(text);
        while (literals.find()) {
            addIfFile(root, literals.group(1), out);
        }
        return out;
    }

    private static void addSiblings(Path root, String folder, List<String> out) throws IOException {
        Path directory = root.resolve(folder).normalize();
        if (!directory.startsWith(root) || !Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> list = Files.list(directory)) {
            for (Path path : (Iterable<Path>) list.sorted()::iterator) {
                String name = path.getFileName().toString();
                if (Files.isRegularFile(path) && (name.endsWith(".java") || name.endsWith(".kt"))) {
                    out.add(root.relativize(path).toString().replace('\\', '/'));
                }
            }
        }
    }

    private static String extensionOf(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** A folder name made from {@code name} that does not exist yet in {@code parent}: {@code Name}, then {@code Name 2} and so on. */
    static Path uniqueFolder(Path parent, String name) {
        String clean = GameExporter.safeName(name);
        Path candidate = parent.resolve(clean);
        for (int n = 2; Files.exists(candidate); n++) {
            candidate = parent.resolve(clean + " " + n);
        }
        return candidate;
    }

    /** Removes a half-made folder; returns what went wrong, if anything, so it can be attached to the original failure. */
    static IOException deleteTree(Path folder) {
        if (!Files.exists(folder)) {
            return null;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            IOException failure = null;
            for (Path path : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                if (!path.toFile().delete() && failure == null) {
                    failure = new IOException("Could not remove " + path + " while cleaning up a failed template");
                }
            }
            return failure;
        } catch (IOException e) {
            return e;
        }
    }
}
