package io.github.ldev22live.gdxunity.editor.core.inspector;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.Objects;

/**
 * Model mediating reads and validated edits for an inspected {@link Entity}.
 * Synchronizes local position, scale, and Euler angle rotation with the underlying Transform.
 */
public class EntityInspectorModel {
    private final Entity entity;

    public EntityInspectorModel(Entity entity) {
        this.entity = Objects.requireNonNull(entity, "entity cannot be null");
    }

    public Entity getEntity() {
        return entity;
    }

    public String getName() {
        return entity.getName();
    }

    public void setName(String name) {
        entity.setName(name);
    }

    public boolean isActive() {
        return entity.isActiveSelf();
    }

    public void setActive(boolean active) {
        entity.setActive(active);
    }

    // --- Transform local position ---

    public Vector3 getLocalPosition() {
        return entity.getTransform().getLocalPosition();
    }

    public void setLocalPosition(float x, float y, float z) {
        if (!Float.isNaN(x) && !Float.isNaN(y) && !Float.isNaN(z) &&
            !Float.isInfinite(x) && !Float.isInfinite(y) && !Float.isInfinite(z)) {
            entity.getTransform().setLocalPosition(x, y, z);
        }
    }

    // --- Transform local rotation (Euler angles in degrees) ---

    public Vector3 getLocalEulerAngles() {
        Quaternion q = entity.getTransform().getLocalRotation();
        return new Vector3(q.getYaw(), q.getPitch(), q.getRoll());
    }

    public void setLocalEulerAngles(float yaw, float pitch, float roll) {
        if (!Float.isNaN(yaw) && !Float.isNaN(pitch) && !Float.isNaN(roll) &&
            !Float.isInfinite(yaw) && !Float.isInfinite(pitch) && !Float.isInfinite(roll)) {
            entity.getTransform().setLocalEulerAngles(yaw, pitch, roll);
        }
    }

    // --- Transform local scale ---

    public Vector3 getLocalScale() {
        return entity.getTransform().getLocalScale();
    }

    public void setLocalScale(float x, float y, float z) {
        if (!Float.isNaN(x) && !Float.isNaN(y) && !Float.isNaN(z) &&
            !Float.isInfinite(x) && !Float.isInfinite(y) && !Float.isInfinite(z)) {
            entity.getTransform().setLocalScale(x, y, z);
        }
    }

    // --- World transform readouts ---

    public Vector3 getWorldPosition() {
        return entity.getTransform().getWorldPosition();
    }

    public Vector3 getWorldScale() {
        return entity.getTransform().getWorldScale();
    }
}
