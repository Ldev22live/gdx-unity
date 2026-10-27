package io.github.ldev22live.gdxunity.editor.core.command;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;
import java.util.UUID;

/**
 * Command to add a component to an entity.
 */
public class AddComponentCommand implements EditorCommand {
    private final EditorState editorState;
    private final UUID entityId;
    private final Component component;

    public AddComponentCommand(EditorState editorState, UUID entityId, Component component) {
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
        entity.addComponent(component);
        editorState.notifyHierarchyModified();
        editorState.notifyEntityTransformModified(entity);
        editorState.setStatusMessage("Added " + component.getClass().getSimpleName() + " to " + entity.getName(), false);
        return true;
    }

    @Override
    public boolean undo() {
        Entity entity = findTarget();
        if (entity == null) {
            return false;
        }
        entity.removeComponent(component);
        editorState.notifyHierarchyModified();
        editorState.notifyEntityTransformModified(entity);
        editorState.setStatusMessage("Undid adding component to " + entity.getName(), false);
        return true;
    }

    @Override
    public String getDescription() {
        return "Add Component " + component.getClass().getSimpleName();
    }

    private Entity findTarget() {
        if (editorState.getActiveScene() == null) return null;
        return editorState.getActiveScene().findEntityById(entityId);
    }
}
