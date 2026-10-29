package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * Makes an entity's 3D colliders take part in the simulation as one rigid body (Phase 15, TD-028).
 * Without this component, an entity's colliders form a static body.
 * <p>
 * Lengths are scene units; the physics system converts them with {@link PhysicsUnits3D}. Friction and
 * restitution belong to the body, as in the physics engines evaluated for Phase 15.
 */
public class RigidBody3DComponent extends BaseComponent {

    public enum MotionType {
        /** Never moves; other bodies collide with it. */
        STATIC,
        /** Moved by its transform (animation, scripts); pushes dynamic bodies but is not pushed. */
        KINEMATIC,
        /** Moved by the simulation: gravity, collisions, forces. */
        DYNAMIC
    }

    public static final float DEFAULT_MASS = 1f;
    public static final float DEFAULT_DAMPING = 0.05f;
    public static final float DEFAULT_FRICTION = 0.5f;

    private MotionType motionType = MotionType.DYNAMIC;
    private float mass = DEFAULT_MASS;
    private float linearDamping = DEFAULT_DAMPING;
    private float angularDamping = DEFAULT_DAMPING;
    private float gravityFactor = 1f;
    private float friction = DEFAULT_FRICTION;
    private float restitution = 0f;
    private int layer = PhysicsLayers3D.DEFAULT_LAYER;
    private int collisionMask = PhysicsLayers3D.ALL_LAYERS;

    public RigidBody3DComponent() {
    }

    public RigidBody3DComponent(MotionType motionType) {
        setMotionType(motionType);
    }

    public MotionType getMotionType() {
        return motionType;
    }

    public void setMotionType(MotionType motionType) {
        this.motionType = Objects.requireNonNull(motionType, "motionType cannot be null");
    }

    /** Mass in kilograms; only used by dynamic bodies. */
    public float getMass() {
        return mass;
    }

    public void setMass(float mass) {
        requireFinite(mass, "mass");
        if (mass <= 0f) {
            throw new IllegalArgumentException("mass must be positive: " + mass);
        }
        this.mass = mass;
    }

    public float getLinearDamping() {
        return linearDamping;
    }

    public void setLinearDamping(float linearDamping) {
        this.linearDamping = requireNonNegative(linearDamping, "linearDamping");
    }

    public float getAngularDamping() {
        return angularDamping;
    }

    public void setAngularDamping(float angularDamping) {
        this.angularDamping = requireNonNegative(angularDamping, "angularDamping");
    }

    /** Multiplier on world gravity: 0 floats, 1 is normal, negative rises. */
    public float getGravityFactor() {
        return gravityFactor;
    }

    public void setGravityFactor(float gravityFactor) {
        requireFinite(gravityFactor, "gravityFactor");
        this.gravityFactor = gravityFactor;
    }

    public float getFriction() {
        return friction;
    }

    public void setFriction(float friction) {
        this.friction = requireNonNegative(friction, "friction");
    }

    /** Bounciness from 0 (no bounce) to 1 (keeps all energy). */
    public float getRestitution() {
        return restitution;
    }

    public void setRestitution(float restitution) {
        requireFinite(restitution, "restitution");
        if (restitution < 0f || restitution > 1f) {
            throw new IllegalArgumentException("restitution must be between 0 and 1: " + restitution);
        }
        this.restitution = restitution;
    }

    /** Collision layer, 0 to {@link PhysicsLayers3D#LAYER_COUNT} - 1. */
    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        PhysicsLayers3D.requireValidLayer(layer);
        this.layer = layer;
    }

    /** Bit {@code n} set means this body collides with bodies on layer {@code n}. */
    public int getCollisionMask() {
        return collisionMask;
    }

    public void setCollisionMask(int collisionMask) {
        this.collisionMask = collisionMask & PhysicsLayers3D.ALL_LAYERS;
    }

    static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number: " + value);
        }
    }

    static float requireNonNegative(float value, String name) {
        requireFinite(value, name);
        if (value < 0f) {
            throw new IllegalArgumentException(name + " cannot be negative: " + value);
        }
        return value;
    }
}
