package io.github.ldev22live.gdxunity.engine.scene;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the position, rotation, and scale of an entity in local and world space.
 * Maintains parent-child hierarchical relationships and propagates dirty flags down the subtree.
 */
public final class Transform {
    private final Entity entity;

    // Local transform properties
    private final Vector3 localPosition = new Vector3();
    private final Quaternion localRotation = new Quaternion();
    private final Vector3 localVarScale = new Vector3(1f, 1f, 1f);

    // Cached world transform properties
    private final Vector3 worldPosition = new Vector3();
    private final Quaternion worldRotation = new Quaternion();
    private final Vector3 worldScale = new Vector3(1f, 1f, 1f);
    private final Matrix4 worldMatrix = new Matrix4();
    private final Matrix4 localMatrix = new Matrix4();

    private Transform parent;
    private final List<Transform> children = new ArrayList<>();
    private final List<Transform> unmodifiableChildren = Collections.unmodifiableList(children);

    private boolean dirty = true;

    Transform(Entity entity) {
        this.entity = Objects.requireNonNull(entity, "entity cannot be null");
    }

    public Entity getEntity() {
        return entity;
    }

    // --- Local getters & setters ---

    public Vector3 getLocalPosition() {
        return new Vector3(localPosition);
    }

    public void setLocalPosition(float x, float y, float z) {
        if (localPosition.x != x || localPosition.y != y || localPosition.z != z) {
            localPosition.set(x, y, z);
            setDirty();
        }
    }

    public void setLocalPosition(Vector3 position) {
        Objects.requireNonNull(position, "position cannot be null");
        setLocalPosition(position.x, position.y, position.z);
    }

    public Quaternion getLocalRotation() {
        return new Quaternion(localRotation);
    }

    public void setLocalRotation(Quaternion rotation) {
        Objects.requireNonNull(rotation, "rotation cannot be null");
        if (!localRotation.equals(rotation)) {
            localRotation.set(rotation);
            setDirty();
        }
    }

    public void setLocalEulerAngles(float yaw, float pitch, float roll) {
        localRotation.setEulerAngles(yaw, pitch, roll);
        setDirty();
    }

    public Vector3 getLocalScale() {
        return new Vector3(localVarScale);
    }

    public void setLocalScale(float x, float y, float z) {
        if (localVarScale.x != x || localVarScale.y != y || localVarScale.z != z) {
            localVarScale.set(x, y, z);
            setDirty();
        }
    }

    public void setLocalScale(Vector3 scale) {
        Objects.requireNonNull(scale, "scale cannot be null");
        setLocalScale(scale.x, scale.y, scale.z);
    }

    // --- Hierarchy & Reparenting ---

    public Transform getParent() {
        return parent;
    }

    public List<Transform> getChildren() {
        return unmodifiableChildren;
    }

    public int getChildCount() {
        return children.size();
    }

    public Transform getChild(int index) {
        return children.get(index);
    }

    /**
     * Sets the parent transform without preserving world position/rotation/scale.
     * Equivalent to {@code setParent(newParent, false)}.
     */
    public void setParent(Transform newParent) {
        setParent(newParent, false);
    }

    /**
     * Sets the parent transform, with option to preserve current world transform.
     * Prevents self-parenting and circular ancestor relationships.
     *
     * @param newParent           new parent transform, or null to detach to root
     * @param preserveWorldTransform whether to recalculate local values to keep world position/rotation/scale
     */
    public void setParent(Transform newParent, boolean preserveWorldTransform) {
        if (this.parent == newParent) {
            return;
        }

        if (newParent == this) {
            throw new IllegalArgumentException("Cannot parent a Transform to itself: " + entity.getName());
        }

        if (newParent != null && newParent.isDescendantOf(this)) {
            throw new IllegalArgumentException("Cannot create cyclical hierarchy: " + newParent.getEntity().getName() +
                " is already a descendant of " + entity.getName());
        }

        // Cache world values if preservation is requested
        Vector3 curWorldPos = null;
        Quaternion curWorldRot = null;
        Vector3 curWorldScale = null;
        if (preserveWorldTransform) {
            curWorldPos = getWorldPosition();
            curWorldRot = getWorldRotation();
            curWorldScale = getWorldScale();
        }

        // Detach from current parent
        if (this.parent != null) {
            this.parent.children.remove(this);
        }

        // Attach to new parent
        Transform oldParent = this.parent;
        this.parent = newParent;
        if (this.parent != null) {
            this.parent.children.add(this);
        }

        // Synchronize scene's rootEntities collection
        if (entity.getScene() != null) {
            if (oldParent == null && newParent != null) {
                entity.getScene().notifyEntityNoLongerRoot(entity);
            } else if (oldParent != null && newParent == null) {
                entity.getScene().notifyEntityBecameRoot(entity);
            }
        }

        setDirty();

        if (preserveWorldTransform) {
            if (newParent == null) {
                setLocalPosition(curWorldPos);
                setLocalRotation(curWorldRot);
                setLocalScale(curWorldScale);
            } else {
                // localPos = parentWorldRot^-1 * ((curWorldPos - parentWorldPos) / parentWorldScale)
                Vector3 parentWPos = newParent.getWorldPosition();
                Quaternion parentWRot = newParent.getWorldRotation();
                Vector3 parentWScale = newParent.getWorldScale();

                // Position
                Vector3 diff = new Vector3(curWorldPos).sub(parentWPos);
                Quaternion invParentRot = new Quaternion(parentWRot).conjugate();
                invParentRot.transform(diff);
                if (parentWScale.x != 0) diff.x /= parentWScale.x;
                if (parentWScale.y != 0) diff.y /= parentWScale.y;
                if (parentWScale.z != 0) diff.z /= parentWScale.z;
                setLocalPosition(diff);

                // Rotation: localRot = parentWRot^-1 * curWorldRot
                Quaternion newLocalRot = new Quaternion(parentWRot).conjugate().mul(curWorldRot);
                setLocalRotation(newLocalRot);

                // Scale: localScale = curWorldScale / parentWScale
                Vector3 newLocalScale = new Vector3(curWorldScale);
                if (parentWScale.x != 0) newLocalScale.x /= parentWScale.x;
                if (parentWScale.y != 0) newLocalScale.y /= parentWScale.y;
                if (parentWScale.z != 0) newLocalScale.z /= parentWScale.z;
                setLocalScale(newLocalScale);
            }
        }
    }

    public boolean isDescendantOf(Transform potentialAncestor) {
        if (potentialAncestor == null) {
            return false;
        }
        Transform current = this.parent;
        while (current != null) {
            if (current == potentialAncestor) {
                return true;
            }
            current = current.parent;
        }
        return false;
    }

    // --- World transform calculations with dirty flag propagation ---

    public Vector3 getWorldPosition() {
        validateWorldTransform();
        return new Vector3(worldPosition);
    }

    public Quaternion getWorldRotation() {
        validateWorldTransform();
        return new Quaternion(worldRotation);
    }

    public Vector3 getWorldScale() {
        validateWorldTransform();
        return new Vector3(worldScale);
    }

    public Matrix4 getWorldMatrix() {
        validateWorldTransform();
        return new Matrix4(worldMatrix);
    }

    private void validateWorldTransform() {
        if (dirty) {
            updateWorldTransform();
        }
    }

    private void updateWorldTransform() {
        // Build local matrix: Translation * Rotation * Scale
        localMatrix.set(localPosition, localRotation, localVarScale);

        if (parent == null) {
            worldMatrix.set(localMatrix);
            worldPosition.set(localPosition);
            worldRotation.set(localRotation);
            worldScale.set(localVarScale);
        } else {
            // World matrix = Parent.worldMatrix * localMatrix
            parent.validateWorldTransform();
            worldMatrix.set(parent.worldMatrix).mul(localMatrix);

            // Extract decomposed world components
            worldMatrix.getTranslation(worldPosition);
            worldMatrix.getRotation(worldRotation, true);
            worldMatrix.getScale(worldScale);
        }

        dirty = false;
    }

    public void setDirty() {
        if (!dirty) {
            dirty = true;
            // Propagate dirty flag down the entire child tree
            for (int i = 0; i < children.size(); i++) {
                children.get(i).setDirty();
            }
        }
    }

    public boolean isDirty() {
        return dirty;
    }
}
