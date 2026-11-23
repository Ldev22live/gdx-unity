package io.github.ldev22live.gdxunity.editor.terrain;

import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainDelta;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;

import java.util.Objects;

/**
 * One finished brush stroke on the editor's undo stack (TD-082, TD-018). The stroke is already drawn when the mouse button goes up, so
 * the first {@link #execute()} puts the same heights in place again, which changes nothing visible, and writes the file; undo and redo
 * move the heights and write the file again, so the file always matches what the viewport shows.
 */
public final class TerrainStrokeCommand implements EditorCommand {
    private final TerrainLibrary library;
    private final String path;
    private final Terrain terrain;
    private final TerrainDelta delta;
    private final String description;

    /**
     * @param path        the terrain's project-relative path
     * @param terrain     the library's terrain for that path
     * @param delta       what the stroke changed; must not be empty
     * @param description what to call it in the undo list, for example "Terrain: Raise"
     * @throws IllegalArgumentException if the delta is empty
     */
    public TerrainStrokeCommand(TerrainLibrary library, String path, Terrain terrain, TerrainDelta delta, String description) {
        this.library = Objects.requireNonNull(library, "library cannot be null");
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.terrain = Objects.requireNonNull(terrain, "terrain cannot be null");
        this.delta = Objects.requireNonNull(delta, "delta cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
        if (delta.isEmpty()) {
            throw new IllegalArgumentException("A stroke that changed nothing has nothing to undo");
        }
    }

    @Override
    public boolean execute() {
        terrain.apply(delta, true);
        library.save(path, terrain);
        return true;
    }

    @Override
    public boolean undo() {
        terrain.apply(delta, false);
        library.save(path, terrain);
        return true;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /** Approximate memory this undo step holds, in bytes. */
    public long memoryBytes() {
        return delta.memoryBytes();
    }
}
