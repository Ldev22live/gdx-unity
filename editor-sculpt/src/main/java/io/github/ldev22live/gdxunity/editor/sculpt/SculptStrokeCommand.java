package io.github.ldev22live.gdxunity.editor.sculpt;

import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptSurface;
import io.github.ldev22live.gdxunity.engine.sculpt.StrokeDelta;

import java.util.Objects;

/**
 * One finished brush stroke on the editor's undo stack. The stroke is already drawn on the surface when the mouse
 * button goes up, so the first {@link #execute()} puts the same positions in place again, which changes nothing
 * visible; later redos restore them after an undo.
 */
public final class SculptStrokeCommand implements EditorCommand {
    private final SculptSurface surface;
    private final StrokeDelta delta;
    private final String description;

    /**
     * @param surface     the surface the stroke was made on
     * @param delta       what the stroke changed; must not be empty
     * @param description what to call it in the undo list, for example "Sculpt: Draw"
     * @throws IllegalArgumentException if the delta is empty
     */
    public SculptStrokeCommand(SculptSurface surface, StrokeDelta delta, String description) {
        this.surface = Objects.requireNonNull(surface, "surface cannot be null");
        this.delta = Objects.requireNonNull(delta, "delta cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
        if (delta.isEmpty()) {
            throw new IllegalArgumentException("A stroke that changed nothing has nothing to undo");
        }
    }

    @Override
    public boolean execute() {
        surface.apply(delta, true);
        return true;
    }

    @Override
    public boolean undo() {
        surface.apply(delta, false);
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
