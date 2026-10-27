package io.github.ldev22live.gdxunity.editor.core.command;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Objects;
import java.util.UUID;

/**
 * Command to create a new entity in the active scene.
 */
public class CreateEntityCommand implements EditorCommand {
    private final EditorState editorState;
    private final String entityName;
    private Entity createdEntity;
    private UUID createdId;

    public CreateEntityCommand(EditorState editorState, String entityName) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.entityName = (entityName != null && !entityName.isBlank()) ? entityName : "New Entity";
    }

    @Override
    public boolean execute() {
        if (editorState.isPlayModeActive()) {
            editorState.setStatusMessage("Cannot create entity during Play/Pause mode", true);
            return false;
        }
        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            return false;
        }

        if (createdEntity == null) {
            createdEntity = scene.createEntity(entityName);
            createdId = createdEntity.getId();
        } else {
            // Redo: re-add existing entity instance into scene
            scene.addEntity(createdEntity);
        }

        editorState.setSelectedEntity(createdEntity);
        editorState.notifyHierarchyModified();
        editorState.setStatusMessage("Created entity: " + createdEntity.getName(), false);
        return true;
    }

    @Override
    public boolean undo() {
        if (createdEntity == null) {
            return false;
        }
        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            return false;
        }

        if (Objects.equals(editorState.getSelectedEntityId(), createdId)) {
            editorState.clearSelection();
        }

        boolean removed = scene.removeEntity(createdEntity, true);
        if (removed) {
            editorState.notifyHierarchyModified();
            editorState.setStatusMessage("Undid create entity: " + entityName, false);
        }
        return removed;
    }

    public Entity getCreatedEntity() {
        return createdEntity;
    }

    @Override
    public String getDescription() {
        return "Create Entity '" + entityName + "'";
    }
}
