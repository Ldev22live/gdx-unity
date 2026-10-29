package io.github.ldev22live.gdxunity.editor.core.command;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Command to create a new entity in the active scene.
 */
public class CreateEntityCommand implements EditorCommand {
    private final EditorState editorState;
    private final String entityName;
    private final Vector3 initialPosition;
    private final List<Component> initialComponents;
    private Entity createdEntity;
    /** Components detached by Scene.removeEntity on undo; re-attached on redo. */
    private List<Component> componentsToRestore = List.of();
    private UUID createdId;

    public CreateEntityCommand(EditorState editorState, String entityName) {
        this(editorState, entityName, null, List.of());
    }

    /**
     * Creates an entity at {@code position} with {@code components} already attached, as one undo step.
     *
     * @param position   initial local position, or null for the origin
     * @param components components to attach on first execution; redo re-adds the same entity and components
     */
    public CreateEntityCommand(EditorState editorState, String entityName, Vector3 position, List<Component> components) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.entityName = (entityName != null && !entityName.isBlank()) ? entityName : "New Entity";
        this.initialPosition = position != null ? new Vector3(position) : null;
        this.initialComponents = List.copyOf(Objects.requireNonNull(components, "components cannot be null"));
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
            if (initialPosition != null) {
                createdEntity.getTransform().setLocalPosition(initialPosition);
            }
            for (Component component : initialComponents) {
                createdEntity.addComponent(component);
            }
        } else {
            // Redo: re-add existing entity instance into scene with the components undo detached
            scene.addEntity(createdEntity);
            for (Component component : componentsToRestore) {
                createdEntity.addComponent(component);
            }
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

        // Scene.removeEntity detaches all components; remember them for redo
        componentsToRestore = new ArrayList<>(createdEntity.getAllComponents());
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
