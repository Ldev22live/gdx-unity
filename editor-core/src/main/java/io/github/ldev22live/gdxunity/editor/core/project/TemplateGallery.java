package io.github.ldev22live.gdxunity.editor.core.project;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The New Game template gallery (roadmap M1.4): what the dialog offers and what choosing one does. It offers an empty 2D, 2.5D and
 * 3D start ({@link StarterProject}) and the sample scenes listed in {@value #CATALOG_FILE}, each with its genre, dimension (the
 * scene's own game mode), size and platform status. <b>Use as Template</b> makes a separate project ({@link TemplateProject},
 * {@link StarterProject}); <b>Play Sample</b> is the editor's Open Sample on the listed scene.
 *
 * <p>The catalog is read strictly: a listed scene that is missing, or has no game mode, is an error that names it, so the gallery
 * never offers something it cannot open. Headless; the dialog is in {@code editor-desktop}.
 */
public final class TemplateGallery {
    /** The catalog, relative to the project. */
    public static final String CATALOG_FILE = "templates/gallery.json";
    /** Folder of the preview images, relative to the project: {@code <scene name>.png}. */
    public static final String PREVIEW_FOLDER = "templates/previews/";
    /** What the desktop column says for every offered choice: the gallery's test opens, plays and exports each one. */
    public static final String DESKTOP_STATUS = "Desktop: opens, plays and exports (checked by test)";
    /** Web column of a sample whose every script has a web-player port; that port was run headless, never in a browser. */
    public static final String WEB_PORTED_STATUS = "Web player: scripts ported (headless tests, not run in a browser)";
    /** Web column of everything else. */
    public static final String WEB_UNKNOWN_STATUS = "Web player: not checked";
    /** Files of an empty start: its scene and the project file. */
    static final int EMPTY_START_FILES = 2;
    private static final long KILOBYTE = 1024;
    private static final long MEGABYTE = KILOBYTE * KILOBYTE;

    /** An empty start or a sample. */
    public enum Kind { EMPTY, SAMPLE }

    /**
     * One entry of the gallery.
     *
     * @param kind      empty start or sample
     * @param title     shown in the list
     * @param genre     what kind of game it is; "Empty" for an empty start
     * @param mode      its dimension
     * @param summary   one line about it
     * @param scene     the sample scene relative to the project; null for an empty start
     * @param preview   the preview image relative to the project, or null when there is none
     * @param files     how many files a project made from it holds
     * @param bytes     their total size
     * @param webPorted whether the web player has every script it uses
     */
    public record Choice(Kind kind, String title, String genre, GameMode mode, String summary, String scene, String preview,
                         int files, long bytes, boolean webPorted) {
        public Choice {
            Objects.requireNonNull(kind, "kind cannot be null");
            Objects.requireNonNull(title, "title cannot be null");
            Objects.requireNonNull(genre, "genre cannot be null");
            Objects.requireNonNull(mode, "mode cannot be null");
            Objects.requireNonNull(summary, "summary cannot be null");
            if ((kind == Kind.SAMPLE) != (scene != null)) {
                throw new IllegalArgumentException("a sample has a scene and an empty start has none: " + kind + ", " + scene);
            }
        }

        /** Whether Play Sample applies: only a sample has something to play before a project is made. */
        public boolean canPlaySample() {
            return kind == Kind.SAMPLE;
        }

        /** "2 files, 14 KB" and similar; an empty start's size is not known until it is written, so only its files are given. */
        public String sizeText() {
            String count = files + (files == 1 ? " file" : " files");
            return kind == Kind.EMPTY ? count : count + ", " + formatBytes(bytes);
        }

        /** The platform lines of the details pane. */
        public List<String> platformText() {
            return List.of(DESKTOP_STATUS, webPorted ? WEB_PORTED_STATUS : WEB_UNKNOWN_STATUS);
        }
    }

    private TemplateGallery() { }

    /**
     * Everything the gallery offers: the three empty starts, then the catalog's samples in its order.
     *
     * @param projectRoot the project that holds the catalog and the samples
     * @throws IOException if the catalog cannot be read, or names a scene that is missing or has no game mode
     */
    public static List<Choice> choices(File projectRoot) throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        List<Choice> choices = new ArrayList<>(emptyStarts());
        choices.addAll(samples(projectRoot));
        return List.copyOf(choices);
    }

    /** The empty 2D, 2.5D and 3D starts. */
    public static List<Choice> emptyStarts() {
        List<Choice> starts = new ArrayList<>();
        for (GameMode mode : GameMode.values()) {
            // Its scene and the project file; the scene is a few kilobytes, written when the project is made
            starts.add(new Choice(Kind.EMPTY, "Empty " + mode.getLabel(), "Empty", mode, summaryOf(mode), null, null,
                EMPTY_START_FILES, 0, false));
        }
        return starts;
    }

    /** The catalog's samples, in its order. */
    public static List<Choice> samples(File projectRoot) throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        File catalog = new File(projectRoot, CATALOG_FILE);
        if (!catalog.isFile()) {
            return List.of();
        }
        JsonValue root;
        try {
            root = new JsonReader().parse(Files.readString(catalog.toPath(), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IOException("Cannot read " + CATALOG_FILE + ": " + e.getMessage(), e);
        }
        JsonValue templates = root.get("templates");
        if (templates == null || !templates.isArray()) {
            throw new IOException(CATALOG_FILE + " has no \"templates\" list");
        }
        List<Choice> samples = new ArrayList<>();
        for (JsonValue entry = templates.child; entry != null; entry = entry.next) {
            String scene = required(entry, "scene");
            File sceneFile = new File(projectRoot, scene);
            if (!sceneFile.isFile()) {
                throw new IOException(CATALOG_FILE + " lists " + scene + ", which does not exist");
            }
            TemplateProject.Plan plan = TemplateProject.plan(projectRoot, scene);
            samples.add(new Choice(Kind.SAMPLE, required(entry, "title"), required(entry, "genre"), modeOf(sceneFile, scene),
                required(entry, "summary"), scene, previewOf(projectRoot, scene),
                // The project file is added to what is copied
                plan.files().size() + 1, plan.bytes(), entry.getBoolean("web", false)));
        }
        return samples;
    }

    /**
     * Makes the project for {@code choice} in {@code projectsDir}; the source project is never written.
     *
     * @param name       the project's name; blank uses the sample's scene name or "My &lt;mode&gt; Game"
     * @param serializer the editor's scene serializer, for an empty start's scene
     * @throws IOException if the project cannot be made; nothing is left behind
     */
    public static TemplateProject.Result create(Choice choice, File projectRoot, File projectsDir, String name,
                                                SceneSerializer serializer) throws IOException {
        Objects.requireNonNull(choice, "choice cannot be null");
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        Objects.requireNonNull(projectsDir, "projectsDir cannot be null");
        Objects.requireNonNull(serializer, "serializer cannot be null");
        return choice.kind() == Kind.EMPTY
            ? StarterProject.create(projectsDir, name, choice.mode(), serializer)
            : TemplateProject.create(projectRoot, choice.scene(), projectsDir, name);
    }

    /** The preview image of a sample scene, relative to the project, or null when there is none. */
    static String previewOf(File projectRoot, String scene) {
        String fileName = new File(scene).getName();
        String base = fileName.substring(0, fileName.length() - ".json".length());
        String preview = PREVIEW_FOLDER + base + ".png";
        return new File(projectRoot, preview).isFile() ? preview : null;
    }

    static String formatBytes(long bytes) {
        if (bytes < KILOBYTE) {
            return bytes + " bytes";
        }
        if (bytes < MEGABYTE) {
            return (bytes + KILOBYTE / 2) / KILOBYTE + " KB";
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (double) MEGABYTE);
    }

    private static String summaryOf(GameMode mode) {
        return switch (mode) {
            case MODE_2D -> "An empty 2D scene with a camera: add sprites, tiles and 2D physics.";
            case MODE_2_5D -> "An empty side-view scene: a camera, a sun, a sky light and a ground strip at depth 0.";
            case MODE_3D -> "An empty 3D world: a camera, a sun, a sky light and a flat ground.";
        };
    }

    private static GameMode modeOf(File sceneFile, String scene) throws IOException {
        JsonValue json;
        try {
            json = new JsonReader().parse(Files.readString(sceneFile.toPath(), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IOException("Cannot read " + scene + ": " + e.getMessage(), e);
        }
        String value = json.getString("gameMode", null);
        GameMode mode = value == null ? null : GameMode.fromFileValue(value);
        if (mode == null) {
            throw new IOException(scene + " has no game mode (\"gameMode\"), so the gallery cannot say if it is 2D, 2.5D or 3D");
        }
        return mode;
    }

    private static String required(JsonValue entry, String key) throws IOException {
        String value = entry.getString(key, null);
        if (value == null || value.isBlank()) {
            throw new IOException(CATALOG_FILE + ": an entry has no \"" + key + "\"" + (key.equals("scene") ? "" : " ("
                + entry.getString("scene", "?") + ")"));
        }
        return value;
    }
}
