package io.github.ldev22live.gdxunity.editor.core.command;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Command to delete an entity and preserve all its components, transform, and hierarchy state
 * so it can be restored on undo.
 */
public class DeleteEntityCommand implements EditorCommand {
    private final EditorState editorState;
    private final UUID entityId;

    // Preserved state for undo
    private Entity deletedEntity;
    private String entityName;
    private boolean activeSelf;
    private Vector3 localPosition;
    private Quaternion localRotation;
    private Vector3 localScale;
    private UUID parentId;
    private final List<Component> components = new ArrayList<>();

    public DeleteEntityCommand(EditorState editorState, UUID entityId) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId cannot be null");
    }

    @Override
    public boolean execute() {
        if (editorState.isPlayModeActive()) {
            editorState.setStatusMessage("Cannot delete entity during Play/Pause mode", true);
            return false;
        }
        Scene scene = editorState.getActiveScene();
        if (scene == null) return false;

        Entity entity = scene.findEntityById(entityId);
        if (entity == null) return false;

        // Capture state on first execution
        this.deletedEntity = entity;
        this.entityName = entity.getName();
        this.activeSelf = entity.isActiveSelf();

        Transform t = entity.getTransform();
        this.localPosition = new Vector3(t.getLocalPosition());
        this.localRotation = new Quaternion(t.getLocalRotation());
        this.localScale = new Vector3(t.getLocalScale());
        this.parentId = (t.getParent() != null) ? t.getParent().getEntity().getId() : null;

        this.components.clear();
        this.components.addAll(entity.getAllComponents());

        if (Objects.equals(editorState.getSelectedEntityId(), entityId)) {
            editorState.clearSelection();
        }

        // Remove entity from scene (doRemoveEntity detaches components and unparents)
        boolean removed = scene.removeEntity(entity, false);
        if (removed) {
            editorState.notifyHierarchyModified();
            editorState.setStatusMessage("Deleted entity: " + entityName, false);
        }
        return removed;
    }

    @Override
    public boolean undo() {
        Scene scene = editorState.getActiveScene();
        if (scene == null || deletedEntity == null) return false;

        // Re-construct entity with identical ID and properties
        Entity restored = new Entity(entityId, entityName);
        restored.setActive(activeSelf);
        restored.getTransform().setLocalPosition(localPosition);
        restored.getTransform().setLocalRotation(localRotation);
        restored.getTransform().setLocalScale(localScale);

        // Add back to scene
        scene.addEntity(restored);

        // Restore parent if still present
        if (parentId != null) {
            Entity parent = scene.findEntityById(parentId);
            if (parent != null) {
                restored.getTransform().setParent(parent.getTransform());
            }
        }

        // Restore components
        for (Component c : components) {
            restored.addComponent(c);
        }

        this.deletedEntity = restored;
        editorState.setSelectedEntity(restored);
        editorState.notifyHierarchyModified();
        editorState.setStatusMessage("Restored deleted entity: " + entityName, false);
        return true;
    }

    @Override
    public String getDescription() {
        return "Delete Entity '" + (entityName != null ? entityName : entityId.toString()) + "'";
    }
}
