package io.github.ldev22live.gdxunity.engine.desktop;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Properties;

/**
 * The {@value #FILE_NAME} file of a project, as far as the game runtime needs it (TD-046): the game's name and the scene
 * it starts with. The editor writes the same keys when it exports a game ({@code GameExporter}).
 *
 * @param name       title of the game, or an empty string when the file has none
 * @param startScene scene played when no {@code --scene} is given, relative to the project, or an empty string
 * @param windowIcon PNG inside the project used as the window icon, or an empty string for the Forgeroom icon
 */
public record GameProjectFile(String name, String startScene, String windowIcon) {
    /** Name of the project file, the marker the editor looks for. */
    public static final String FILE_NAME = "gdxunity.project";
    /** Key of the game's name. */
    public static final String KEY_NAME = "name";
    /** Key of the scene the game starts with. */
    public static final String KEY_START_SCENE = "startScene";
    /** Key of the window icon. */
    public static final String KEY_WINDOW_ICON = "windowIcon";

    public GameProjectFile {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(startScene, "startScene cannot be null");
        Objects.requireNonNull(windowIcon, "windowIcon cannot be null");
    }

    /** True when the file names a start scene. */
    public boolean hasStartScene() {
        return !startScene.isBlank();
    }

    /**
     * Reads the project file of {@code projectDir}.
     *
     * @throws IOException if the folder has no project file or it cannot be read
     */
    public static GameProjectFile read(File projectDir) throws IOException {
        Objects.requireNonNull(projectDir, "projectDir cannot be null");
        File file = new File(projectDir, FILE_NAME);
        if (!file.isFile()) {
            throw new IOException("No " + FILE_NAME + " in " + projectDir.getAbsolutePath());
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath());
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return new GameProjectFile(properties.getProperty(KEY_NAME, "").trim(), properties.getProperty(KEY_START_SCENE, "").trim(),
            properties.getProperty(KEY_WINDOW_ICON, "").trim());
    }
}
