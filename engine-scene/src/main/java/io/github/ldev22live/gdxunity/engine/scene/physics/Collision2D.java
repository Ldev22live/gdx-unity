package io.github.ldev22live.gdxunity.engine.scene.physics;

import com.badlogic.gdx.math.Vector2;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

/**
 * Immutable collision event data passed to collision callbacks.
 */
public record Collision2D(
    Type type,
    Entity entity,
    Entity otherEntity,
    Component collider,
    Component otherCollider,
    Vector2 contactPoint,
    Vector2 normal,
    boolean isSensor
) {
    public enum Type {
        COLLISION_ENTER,
        COLLISION_EXIT,
        SENSOR_ENTER,
        SENSOR_EXIT
    }
}
