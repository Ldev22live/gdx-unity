package io.github.ldev22live.gdxunity.editor.core.command;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;
import java.util.UUID;

/**
 * Command to remove a component from an entity, supporting complete undo/redo.
 */
public class RemoveComponentCommand implements EditorCommand {
    private final EditorState editorState;
    private final UUID entityId;
    private final Component component;

    public RemoveComponentCommand(EditorState editorState, UUID entityId, Component component) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId cannot be null");
        this.component = Objects.requireNonNull(component, "component cannot be null");
    }

    @Override
    public boolean execute() {
        Entity entity = findTarget();
        if (entity == null) {
            return false;
        }
        boolean removed = entity.removeComponent(component);
        if (!removed) {
            return false;
        }
        editorState.notifyHierarchyModified();
        editorState.notifyEntityTransformModified(entity);
        editorState.setStatusMessage("Removed " + component.getClass().getSimpleName() + " from " + entity.getName(), false);
        return true;
    }

    @Override
    public boolean undo() {
        Entity entity = findTarget();
        if (entity == null) {
            return false;
        }
        entity.addComponent(component);
        editorState.notifyHierarchyModified();
        editorState.notifyEntityTransformModified(entity);
        editorState.setStatusMessage("Undid removing " + component.getClass().getSimpleName() + " from " + entity.getName(), false);
        return true;
    }

    @Override
    public String getDescription() {
        return "Remove Component " + component.getClass().getSimpleName();
    }

    private Entity findTarget() {
        if (editorState.getActiveScene() == null) return null;
        return editorState.getActiveScene().findEntityById(entityId);
    }
}
