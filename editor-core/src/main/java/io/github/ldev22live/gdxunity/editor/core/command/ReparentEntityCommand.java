package io.github.ldev22live.gdxunity.editor.core.command;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.Objects;
import java.util.UUID;

/**
 * Command to reparent an entity to a new parent or to the scene root (null).
 * Accurately tracks previous parent, validates against cyclic hierarchies and self-parenting,
 * and maintains consistent undo/redo semantics.
 */
public class ReparentEntityCommand implements EditorCommand {
    private final EditorState editorState;
    private final UUID entityId;
    private final UUID newParentId; // null means reparent to scene root
    private final boolean preserveWorldTransform;

    private UUID previousParentId; // captured during first execution
    private boolean executed = false;

    /**
     * Creates a new ReparentEntityCommand.
     *
     * @param editorState            active editor state
     * @param entityId               entity to reparent (cannot be null)
     * @param newParentId            new parent entity UUID, or null to unparent to scene root
     * @param preserveWorldTransform whether to recalculate local transform to preserve world position/rotation/scale
     */
    public ReparentEntityCommand(EditorState editorState, UUID entityId, UUID newParentId, boolean preserveWorldTransform) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId cannot be null");
        this.newParentId = newParentId;
        this.preserveWorldTransform = preserveWorldTransform;
    }

    public ReparentEntityCommand(EditorState editorState, UUID entityId, UUID newParentId) {
        this(editorState, entityId, newParentId, false);
    }

    public UUID getEntityId() {
        return entityId;
    }

    public UUID getNewParentId() {
        return newParentId;
    }

    public UUID getPreviousParentId() {
        return previousParentId;
    }

    public boolean isPreserveWorldTransform() {
        return preserveWorldTransform;
    }

    @Override
    public boolean execute() throws Exception {
        if (editorState.isPlayModeActive()) {
            editorState.setStatusMessage("Cannot reparent entities during Play/Pause mode", true);
            return false;
        }

        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            return false;
        }

        Entity targetEntity = scene.findEntityById(entityId);
        if (targetEntity == null) {
            editorState.setStatusMessage("Cannot reparent: Target entity not found in scene", true);
            return false;
        }

        Transform targetTransform = targetEntity.getTransform();

        // Validate new parent
        Transform newParentTransform = null;
        if (newParentId != null) {
            if (newParentId.equals(entityId)) {
                editorState.setStatusMessage("Cannot parent entity to itself: " + targetEntity.getName(), true);
                return false;
            }

            Entity parentEntity = scene.findEntityById(newParentId);
            if (parentEntity == null) {
                editorState.setStatusMessage("Cannot reparent: New parent entity not found in scene", true);
                return false;
            }

            newParentTransform = parentEntity.getTransform();

            // Check if new parent is a descendant of target entity (prevent cycles)
            if (newParentTransform.isDescendantOf(targetTransform)) {
                editorState.setStatusMessage("Cannot reparent: " + parentEntity.getName() + " is already a descendant of " + targetEntity.getName(), true);
                return false;
            }
        }

        // Check if this is a no-op (already has this parent)
        Transform currentParentTransform = targetTransform.getParent();
        UUID currentParentUuid = (currentParentTransform != null) ? currentParentTransform.getEntity().getId() : null;

        if (Objects.equals(currentParentUuid, newParentId)) {
            // Already parented to this entity / root
            return false;
        }

        // Record previous parent on initial execution
        if (!executed) {
            this.previousParentId = currentParentUuid;
            this.executed = true;
        }

        // Apply reparenting with configured transform preservation
        targetTransform.setParent(newParentTransform, preserveWorldTransform);

        editorState.notifyHierarchyModified();
        editorState.setStatusMessage("Reparented " + targetEntity.getName(), false);
        return true;
    }

    @Override
    public boolean undo() throws Exception {
        if (editorState.isPlayModeActive()) {
            editorState.setStatusMessage("Cannot undo during Play/Pause mode", true);
            return false;
        }

        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            return false;
        }

        Entity targetEntity = scene.findEntityById(entityId);
        if (targetEntity == null) {
            return false;
        }

        Transform targetTransform = targetEntity.getTransform();
        Transform originalParentTransform = null;

        if (previousParentId != null) {
            Entity originalParent = scene.findEntityById(previousParentId);
            if (originalParent != null) {
                originalParentTransform = originalParent.getTransform();
            }
        }

        // Restore original parent
        targetTransform.setParent(originalParentTransform, preserveWorldTransform);

        editorState.notifyHierarchyModified();
        editorState.setStatusMessage("Undid reparenting of " + targetEntity.getName(), false);
        return true;
    }

    @Override
    public String getDescription() {
        return "Reparent Entity (" + entityId + ")";
    }
}
