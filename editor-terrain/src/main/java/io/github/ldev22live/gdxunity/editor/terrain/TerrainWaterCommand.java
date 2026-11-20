package io.github.ldev22live.gdxunity.editor.terrain;

import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;

import java.util.Locale;
import java.util.Objects;

/**
 * Sets or removes a terrain's water level as one undo step (roadmap M3.4, TD-089). Like a stroke, every execute and undo writes the
 * {@code .terrain} file, so the file always matches what the viewport shows.
 */
public final class TerrainWaterCommand implements EditorCommand {
    private final TerrainLibrary library;
    private final String path;
    private final Terrain terrain;
    private final float before;
    private final float after;

    /**
     * @param path  the terrain's project-relative path
     * @param after the new water level, or NaN to remove the water
     * @throws IllegalArgumentException if the level would not change
     */
    public TerrainWaterCommand(TerrainLibrary library, String path, Terrain terrain, float after) {
        this.library = Objects.requireNonNull(library, "library cannot be null");
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.terrain = Objects.requireNonNull(terrain, "terrain cannot be null");
        this.before = terrain.getWaterLevel();
        this.after = after;
        if (Float.compare(before, after) == 0) {
            throw new IllegalArgumentException("The water level is already " + describe(after));
        }
    }

    @Override
    public boolean execute() {
        terrain.setWaterLevel(after);
        library.save(path, terrain);
        return true;
    }

    @Override
    public boolean undo() {
        terrain.setWaterLevel(before);
        library.save(path, terrain);
        return true;
    }

    @Override
    public String getDescription() {
        return Float.isNaN(after) ? "Terrain: Remove Water" : "Terrain: Water at " + describe(after);
    }

    /** "none", or the level with one decimal. */
    static String describe(float level) {
        return Float.isNaN(level) ? "none" : String.format(Locale.ROOT, "%.1f", level);
    }
}
