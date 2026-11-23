package io.github.ldev22live.gdxunity.editor.core.project;

import io.github.ldev22live.gdxunity.engine.scene.GameMode;

import com.badlogic.gdx.utils.JsonReader;

import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The starter palette a new 3D or 2.5D project gets (roadmap M3c.5, finding H2 of M0.4): project-owned copies of the World strip's
 * level objects ({@value #LEVEL_FOLDER}), characters ({@value #CHARACTER_FOLDER}) and buildings ({@value #BUILDING_FOLDER}, UI M3), so
 * Objects, Characters and Buildings have something to place
 * in a new project, not only in the shipped assets folder. Most shipped prefabs are built from primitives and are original; the curated
 * CC0 starter pack (roadmap M2b.2) places Kenney models, so a prefab's model files, and what those name, are copied with it, and their
 * licence records go into the new project's {@code licenses/manifest.json} ({@link ProjectLicences}). A file the project already has
 * is never overwritten. 2D projects get none: these prefabs are 3D objects.
 */
public final class StarterPalette {
    /** Folder (project relative) whose prefabs are the World strip's Objects. */
    public static final String LEVEL_FOLDER = "prefabs/level";
    /** Folder (project relative) whose prefabs are the World strip's Characters: the player and the spawn point (M3c.2). */
    public static final String CHARACTER_FOLDER = "prefabs/characters";
    /** Folder (project relative) whose prefabs are the World strip's Buildings (UI M3). */
    public static final String BUILDING_FOLDER = "prefabs/buildings";
    private static final String PREFAB_EXTENSION = ".prefab";

    private StarterPalette() { }

    /**
     * The game mode a scene file names, or empty when it names none (an older scene, whose mode the editor infers when it opens it;
     * such a project gets no palette rather than a guessed one).
     *
     * @throws IOException if the file cannot be read or names an unknown mode
     */
    public static Optional<GameMode> modeNamedBy(File sceneFile) throws IOException {
        Objects.requireNonNull(sceneFile, "sceneFile cannot be null");
        String value;
        try {
            value = new JsonReader().parse(Files.readString(sceneFile.toPath(), StandardCharsets.UTF_8)).getString("gameMode", null);
        } catch (RuntimeException e) {
            throw new IOException("Cannot read " + sceneFile.getName() + ": " + e.getMessage(), e);
        }
        if (value == null) {
            return Optional.empty();
        }
        GameMode mode = GameMode.fromFileValue(value);
        if (mode == null) {
            throw new IOException(sceneFile.getName() + " names an unknown game mode: " + value);
        }
        return Optional.of(mode);
    }

    /** Whether a new project in {@code mode} gets the palette. */
    public static boolean appliesTo(GameMode mode) {
        Objects.requireNonNull(mode, "mode cannot be null");
        return mode == GameMode.MODE_3D || mode == GameMode.MODE_2_5D;
    }

    /**
     * The palette's files in {@code sourceRoot}, project relative and in name order: its prefabs, every file they need (models and
     * what those name, M2b.2) and each prefab's thumbnail, the {@code .png} beside it (M2b.3); empty when it has none.
     */
    public static List<String> files(File sourceRoot) throws IOException {
        Objects.requireNonNull(sourceRoot, "sourceRoot cannot be null");
        Path root = sourceRoot.toPath().toAbsolutePath().normalize();
        TreeSet<String> found = new TreeSet<>();
        for (String prefab : prefabs(sourceRoot)) {
            found.addAll(TemplateProject.findDependencies(root, prefab));
            String thumbnail = prefab.substring(0, prefab.length() - PREFAB_EXTENSION.length()) + ".png";
            if (Files.isRegularFile(root.resolve(thumbnail))) {
                found.add(thumbnail);
            }
        }
        return new ArrayList<>(found);
    }

    /** The palette's prefabs in {@code sourceRoot}, project relative and in name order; empty when it has none. */
    public static List<String> prefabs(File sourceRoot) throws IOException {
        Objects.requireNonNull(sourceRoot, "sourceRoot cannot be null");
        TreeSet<String> found = new TreeSet<>();
        for (String folder : List.of(LEVEL_FOLDER, CHARACTER_FOLDER, BUILDING_FOLDER)) {
            Path dir = sourceRoot.toPath().resolve(folder);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> list = Files.list(dir)) {
                list.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(PREFAB_EXTENSION))
                    .forEach(p -> found.add(folder + "/" + p.getFileName()));
            }
        }
        return new ArrayList<>(found);
    }

    /** Total size of {@link #files} in bytes. */
    public static long bytes(File sourceRoot) throws IOException {
        long total = 0;
        for (String file : files(sourceRoot)) {
            total += Files.size(sourceRoot.toPath().resolve(file));
        }
        return total;
    }

    /**
     * Copies the palette from {@code sourceRoot} into a project just made, when its mode gets one.
     *
     * @return the project made, with the copied files added to its list
     * @throws IOException if a file cannot be copied
     */
    public static TemplateProject.Result addTo(File sourceRoot, TemplateProject.Result made, GameMode mode) throws IOException {
        Objects.requireNonNull(sourceRoot, "sourceRoot cannot be null");
        Objects.requireNonNull(made, "made cannot be null");
        if (!appliesTo(mode)) {
            return made;
        }
        TreeSet<String> files = new TreeSet<>(made.files());
        List<String> copied = new ArrayList<>();
        for (String file : files(sourceRoot)) {
            Path target = made.folder().toPath().resolve(file);
            if (Files.exists(target)) {
                continue; // the template's own copy wins
            }
            Files.createDirectories(target.getParent());
            Files.copy(sourceRoot.toPath().resolve(file), target, StandardCopyOption.COPY_ATTRIBUTES);
            files.add(file);
            copied.add(file);
        }
        // The starter pack's Kenney models keep their licence records in the new project (TD-094, M2b.2)
        if (ProjectLicences.carry(sourceRoot, made.folder(), copied)) {
            files.add(ThirdPartyManifest.PATH);
        }
        return new TemplateProject.Result(made.folder(), made.startScene(), new ArrayList<>(files));
    }
}
