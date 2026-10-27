package io.github.ldev22live.gdxunity.engine.scene.physics;

import com.badlogic.gdx.math.Vector2;

/**
 * Circle collider component with radius and offset in scene units.
 */
public class CircleCollider2DComponent extends Collider2DComponent {
    private float radius = 25.0f;

    public CircleCollider2DComponent() {
    }

    public CircleCollider2DComponent(float radius) {
        setRadius(radius);
    }

    public CircleCollider2DComponent(float radius, Vector2 offset) {
        setRadius(radius);
        if (offset != null) {
            setOffset(offset);
        }
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        validateFinite(radius, "radius");
        if (radius <= 0f) {
            throw new IllegalArgumentException("radius must be positive: " + radius);
        }
        this.radius = radius;
    }
}
