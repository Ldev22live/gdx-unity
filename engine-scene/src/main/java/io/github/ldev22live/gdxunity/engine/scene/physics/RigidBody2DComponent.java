package io.github.ldev22live.gdxunity.engine.scene.physics;

import com.badlogic.gdx.math.Vector2;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * Serialized component defining 2D rigid body simulation parameters.
 * Does not hold live Box2D native handles in authored scene data.
 */
public class RigidBody2DComponent extends BaseComponent {

    public enum BodyType {
        STATIC,
        KINEMATIC,
        DYNAMIC
    }

    private BodyType bodyType = BodyType.DYNAMIC;
    private float gravityScale = 1.0f;
    private float linearDamping = 0.0f;
    private float angularDamping = 0.05f;
    private boolean fixedRotation = false;
    private boolean bullet = false;
    private final Vector2 initialLinearVelocity = new Vector2(0f, 0f);
    private float initialAngularVelocity = 0.0f;

    public RigidBody2DComponent() {
    }

    public RigidBody2DComponent(BodyType bodyType) {
        setBodyType(bodyType);
    }

    public BodyType getBodyType() {
        return bodyType;
    }

    public void setBodyType(BodyType bodyType) {
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType cannot be null");
    }

    public float getGravityScale() {
        return gravityScale;
    }

    public void setGravityScale(float gravityScale) {
        validateFinite(gravityScale, "gravityScale");
        this.gravityScale = gravityScale;
    }

    public float getLinearDamping() {
        return linearDamping;
    }

    public void setLinearDamping(float linearDamping) {
        validateFinite(linearDamping, "linearDamping");
        if (linearDamping < 0f) {
            throw new IllegalArgumentException("linearDamping cannot be negative: " + linearDamping);
        }
        this.linearDamping = linearDamping;
    }

    public float getAngularDamping() {
        return angularDamping;
    }

    public void setAngularDamping(float angularDamping) {
        validateFinite(angularDamping, "angularDamping");
        if (angularDamping < 0f) {
            throw new IllegalArgumentException("angularDamping cannot be negative: " + angularDamping);
        }
        this.angularDamping = angularDamping;
    }

    public boolean isFixedRotation() {
        return fixedRotation;
    }

    public void setFixedRotation(boolean fixedRotation) {
        this.fixedRotation = fixedRotation;
    }

    public boolean isBullet() {
        return bullet;
    }

    public void setBullet(boolean bullet) {
        this.bullet = bullet;
    }

    public Vector2 getInitialLinearVelocity() {
        return new Vector2(initialLinearVelocity);
    }

    public void setInitialLinearVelocity(float x, float y) {
        validateFinite(x, "initialLinearVelocity.x");
        validateFinite(y, "initialLinearVelocity.y");
        this.initialLinearVelocity.set(x, y);
    }

    public void setInitialLinearVelocity(Vector2 velocity) {
        Objects.requireNonNull(velocity, "velocity cannot be null");
        setInitialLinearVelocity(velocity.x, velocity.y);
    }

    public float getInitialAngularVelocity() {
        return initialAngularVelocity;
    }

    public void setInitialAngularVelocity(float initialAngularVelocity) {
        validateFinite(initialAngularVelocity, "initialAngularVelocity");
        this.initialAngularVelocity = initialAngularVelocity;
    }

    private static void validateFinite(float value, String fieldName) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be a finite number: " + value);
        }
    }
}
