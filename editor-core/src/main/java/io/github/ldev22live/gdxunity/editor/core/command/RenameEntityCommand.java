package io.github.ldev22live.gdxunity.editor.core.command;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;
import java.util.UUID;

/**
 * Command to rename an entity.
 */
public class RenameEntityCommand implements EditorCommand {
    private final EditorState editorState;
    private final UUID entityId;
    private final String oldName;
    private final String newName;

    public RenameEntityCommand(EditorState editorState, UUID entityId, String oldName, String newName) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId cannot be null");
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override
    public boolean execute() {
        if (Objects.equals(oldName, newName)) {
            return false;
        }
        Entity entity = findTarget();
        if (entity == null) {
            return false;
        }
        entity.setName(newName);
        editorState.notifyHierarchyModified();
        editorState.setStatusMessage("Renamed entity to: " + newName, false);
        return true;
    }

    @Override
    public boolean undo() {
        Entity entity = findTarget();
        if (entity == null) {
            return false;
        }
        entity.setName(oldName);
        editorState.notifyHierarchyModified();
        editorState.setStatusMessage("Undid rename to: " + oldName, false);
        return true;
    }

    @Override
    public String getDescription() {
        return "Rename Entity to '" + newName + "'";
    }

    private Entity findTarget() {
        if (editorState.getActiveScene() == null) return null;
        return editorState.getActiveScene().findEntityById(entityId);
    }
}
