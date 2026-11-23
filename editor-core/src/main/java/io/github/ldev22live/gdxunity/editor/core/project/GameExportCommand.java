package io.github.ldev22live.gdxunity.editor.core.project;

import io.github.ldev22live.gdxunity.editor.core.scripting.ScriptDiagnostic;
import io.github.ldev22live.gdxunity.editor.core.scripting.ScriptManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * The steps of "Export Game" (Phase 18-C, TD-046) shared by the File menu and the {@code --export-game} command line:
 * compile the project's scripts, choose the game's name and start scene, and hand over to {@link GameExporter}.
 * Headless: it needs no window, so it runs in tests and on a build machine.
 */
public final class GameExportCommand {
    /** Key of the game's title in the project file. */
    static final String KEY_NAME = "name";
    /** Key of the start scene in the project file. */
    static final String KEY_START_SCENE = "startScene";
    /** Key of the PNG, relative to the project, used as the game window's icon. */
    static final String KEY_WINDOW_ICON = "windowIcon";

    private GameExportCommand() {}

    /**
     * Compiles the scripts and exports the project.
     *
     * @param startScene scene to start with, relative to the project; null to use the project file's {@code startScene}
     * @param template   app image to copy the runtime from, see {@link GameExporter#locateTemplate()}
     * @throws IOException if the scripts do not compile (the message lists the errors), there is no start scene, or the
     *                     export fails
     */
    public static GameExporter.Result run(EditorProject project, String startScene, File template, File outputDir)
        throws IOException {
        Objects.requireNonNull(project, "project cannot be null");
        Objects.requireNonNull(outputDir, "outputDir cannot be null");
        Properties settings = readProjectFile(project);
        String scene = startScene != null ? startScene : settings.getProperty(KEY_START_SCENE, "").trim();
        if (scene.isEmpty()) {
            throw new IOException("No start scene. Pass one, or set startScene in " + EditorProject.MARKER_FILE + ".");
        }
        String name = settings.getProperty(KEY_NAME, "").trim();
        if (name.isEmpty()) {
            name = project.getRoot().getName();
        }
        // Fail on a missing template or bad output folder before spending time compiling
        GameExporter.requireTemplate(template);
        compileScripts(project);
        return GameExporter.export(project, scene, name, settings.getProperty(KEY_WINDOW_ICON, "").trim(), template, outputDir);
    }

    /** Compiles the project's scripts into {@code build/scripts}; does nothing if there are no script sources. */
    static void compileScripts(EditorProject project) throws IOException {
        if (!hasSources(project)) {
            return;
        }
        ScriptManager scripts = new ScriptManager(project.getScriptsDir(), project.getScriptsBuildDir());
        try {
            if (!scripts.compileAll()) {
                String errors = scripts.getLatestDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.kind() == ScriptDiagnostic.Kind.ERROR)
                    .map(ScriptDiagnostic::toString)
                    .collect(Collectors.joining(System.lineSeparator()));
                throw new IOException("The scripts do not compile:" + System.lineSeparator() + errors);
            }
        } finally {
            scripts.dispose();
        }
    }

    private static boolean hasSources(EditorProject project) throws IOException {
        if (!project.getScriptsDir().isDirectory()) {
            return false;
        }
        try (var files = Files.walk(project.getScriptsDir().toPath())) {
            List<String> names = files.filter(Files::isRegularFile)
                .map(file -> file.getFileName().toString().toLowerCase()).toList();
            return names.stream().anyMatch(name -> name.endsWith(".java") || name.endsWith(".kt"));
        }
    }

    private static Properties readProjectFile(EditorProject project) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(new File(project.getRoot(), EditorProject.MARKER_FILE).toPath());
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
