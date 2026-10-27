package io.github.ldev22live.gdxunity.engine.scene.physics;

import com.badlogic.gdx.math.Vector2;

/**
 * Axis-aligned box collider component with width, height, and offset in scene units.
 */
public class BoxCollider2DComponent extends Collider2DComponent {
    private float width = 50.0f;
    private float height = 50.0f;

    public BoxCollider2DComponent() {
    }

    public BoxCollider2DComponent(float width, float height) {
        setWidth(width);
        setHeight(height);
    }

    public BoxCollider2DComponent(float width, float height, Vector2 offset) {
        setWidth(width);
        setHeight(height);
        if (offset != null) {
            setOffset(offset);
        }
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        validateFinite(width, "width");
        if (width <= 0f) {
            throw new IllegalArgumentException("width must be positive: " + width);
        }
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        validateFinite(height, "height");
        if (height <= 0f) {
            throw new IllegalArgumentException("height must be positive: " + height);
        }
        this.height = height;
    }
}
