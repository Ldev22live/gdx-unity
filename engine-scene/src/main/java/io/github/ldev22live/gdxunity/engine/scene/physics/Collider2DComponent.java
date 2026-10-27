package io.github.ldev22live.gdxunity.engine.scene.physics;

import com.badlogic.gdx.math.Vector2;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * Base abstract component for 2D colliders containing physical material and collision filter properties.
 */
public abstract class Collider2DComponent extends BaseComponent {
    private final Vector2 offset = new Vector2(0f, 0f);
    private float density = 1.0f;
    private float friction = 0.2f;
    private float restitution = 0.0f;
    private boolean isSensor = false;
    private short categoryBits = 0x0001;
    private short maskBits = (short) 0xFFFF;

    public Vector2 getOffset() {
        return new Vector2(offset);
    }

    public void setOffset(float x, float y) {
        validateFinite(x, "offset.x");
        validateFinite(y, "offset.y");
        this.offset.set(x, y);
    }

    public void setOffset(Vector2 offset) {
        Objects.requireNonNull(offset, "offset cannot be null");
        setOffset(offset.x, offset.y);
    }

    public float getDensity() {
        return density;
    }

    public void setDensity(float density) {
        validateFinite(density, "density");
        if (density < 0f) {
            throw new IllegalArgumentException("density cannot be negative: " + density);
        }
        this.density = density;
    }

    public float getFriction() {
        return friction;
    }

    public void setFriction(float friction) {
        validateFinite(friction, "friction");
        if (friction < 0f) {
            throw new IllegalArgumentException("friction cannot be negative: " + friction);
        }
        this.friction = friction;
    }

    public float getRestitution() {
        return restitution;
    }

    public void setRestitution(float restitution) {
        validateFinite(restitution, "restitution");
        if (restitution < 0f || restitution > 1f) {
            throw new IllegalArgumentException("restitution must be between 0.0 and 1.0: " + restitution);
        }
        this.restitution = restitution;
    }

    public boolean isSensor() {
        return isSensor;
    }

    public void setSensor(boolean sensor) {
        isSensor = sensor;
    }

    public short getCategoryBits() {
        return categoryBits;
    }

    public void setCategoryBits(short categoryBits) {
        this.categoryBits = categoryBits;
    }

    public short getMaskBits() {
        return maskBits;
    }

    public void setMaskBits(short maskBits) {
        this.maskBits = maskBits;
    }

    protected static void validateFinite(float value, String fieldName) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be a finite number: " + value);
        }
    }
}
