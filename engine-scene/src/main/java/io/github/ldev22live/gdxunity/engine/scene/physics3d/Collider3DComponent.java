package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * Base of the 3D collision shapes. Sizes and the offset are in the entity's local space (scene units) and
 * scale with the entity. A trigger collider reports overlaps but does not block anything.
 */
public abstract class Collider3DComponent extends BaseComponent {
    private final Vector3 offset = new Vector3();
    private boolean trigger;

    /** Shape center relative to the entity origin, in local units. */
    public Vector3 getOffset() {
        return new Vector3(offset);
    }

    public void setOffset(Vector3 offset) {
        Objects.requireNonNull(offset, "offset cannot be null");
        RigidBody3DComponent.requireFinite(offset.x, "offset.x");
        RigidBody3DComponent.requireFinite(offset.y, "offset.y");
        RigidBody3DComponent.requireFinite(offset.z, "offset.z");
        this.offset.set(offset);
    }

    public boolean isTrigger() {
        return trigger;
    }

    public void setTrigger(boolean trigger) {
        this.trigger = trigger;
    }

    static float requirePositive(float value, String name) {
        RigidBody3DComponent.requireFinite(value, name);
        if (value <= 0f) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
        return value;
    }
}
