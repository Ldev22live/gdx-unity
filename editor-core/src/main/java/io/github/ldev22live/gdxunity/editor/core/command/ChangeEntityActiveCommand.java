package io.github.ldev22live.gdxunity.editor.core.command;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;
import java.util.UUID;

/**
 * Command to toggle or change an entity's active state.
 */
public class ChangeEntityActiveCommand implements EditorCommand {
    private final EditorState editorState;
    private final UUID entityId;
    private final boolean oldActive;
    private final boolean newActive;

    public ChangeEntityActiveCommand(EditorState editorState, UUID entityId, boolean oldActive, boolean newActive) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId cannot be null");
        this.oldActive = oldActive;
        this.newActive = newActive;
    }

    @Override
    public boolean execute() {
        if (oldActive == newActive) {
            return false;
        }
        Entity entity = findTarget();
        if (entity == null) {
            return false;
        }
        entity.setActive(newActive);
        editorState.notifyHierarchyModified();
        editorState.setStatusMessage("Set " + entity.getName() + " active=" + newActive, false);
        return true;
    }

    @Override
    public boolean undo() {
        Entity entity = findTarget();
        if (entity == null) {
            return false;
        }
        entity.setActive(oldActive);
        editorState.notifyHierarchyModified();
        editorState.setStatusMessage("Undid active state for " + entity.getName(), false);
        return true;
    }

    @Override
    public String getDescription() {
        return (newActive ? "Activate" : "Deactivate") + " Entity";
    }

    private Entity findTarget() {
        if (editorState.getActiveScene() == null) return null;
        return editorState.getActiveScene().findEntityById(entityId);
    }
}
