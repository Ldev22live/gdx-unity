package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

/**
 * Makes an entity a walking character in the 3D physics simulation (Phase 15-G, TD-049): a capsule that slides along walls,
 * climbs steps and slopes, stays on the ground and pushes crates, without tipping over or bouncing like a rigid body would.
 * <p>
 * The entity's position is the capsule's center. Scripts drive it with {@code moveCharacter3D} and {@code jumpCharacter3D}
 * on {@link io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent}; the simulation applies gravity, collides
 * and writes the resulting position back to the entity. The entity's rotation and scale are left alone: the capsule always
 * stands upright and is sized by this component only. An entity with a controller must not also have a
 * {@link RigidBody3DComponent} or a {@link Collider3DComponent}.
 * <p>
 * The simulation owns the position once play has started: it writes it back every physics update, so a script that sets the
 * entity's position moves nothing. There is no teleport yet; a character starts where the entity is when it is created.
 * <p>
 * Lengths are scene units, like the colliders. {@link #getLayer()} and {@link #getCollisionMask()} decide what the capsule is
 * seen as by other bodies (a ball only lands on it when the ball's mask contains this layer); the character itself walks into
 * everything solid.
 */
public class CharacterController3DComponent extends BaseComponent {
    public static final float DEFAULT_RADIUS = 30f;
    public static final float DEFAULT_HEIGHT = 80f;
    public static final float DEFAULT_SLOPE_LIMIT_DEGREES = 45f;
    /** Highest step the character walks onto without jumping: 0.3 m. */
    public static final float DEFAULT_STEP_HEIGHT = 30f;
    public static final float DEFAULT_MASS = 70f;
    /** Steepest slope limit accepted: a vertical wall is not a slope. */
    public static final float MAX_SLOPE_LIMIT_DEGREES = 89f;

    private float radius = DEFAULT_RADIUS;
    private float height = DEFAULT_HEIGHT;
    private float slopeLimitDegrees = DEFAULT_SLOPE_LIMIT_DEGREES;
    private float stepHeight = DEFAULT_STEP_HEIGHT;
    private float mass = DEFAULT_MASS;
    private float gravityScale = 1f;
    private int layer = PhysicsLayers3D.DEFAULT_LAYER;
    private int collisionMask = PhysicsLayers3D.ALL_LAYERS;

    public CharacterController3DComponent() {
    }

    public CharacterController3DComponent(float radius, float height) {
        setRadius(radius);
        setHeight(height);
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = requirePositive(radius, "radius");
    }

    /** Height of the cylindrical middle part; the whole capsule is {@code height + 2 * radius} tall. */
    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = requirePositive(height, "height");
    }

    /** Total height of the capsule in scene units. */
    public float getTotalHeight() {
        return height + 2f * radius;
    }

    /** Steepest slope the character can stand on or walk up, in degrees from flat. */
    public float getSlopeLimitDegrees() {
        return slopeLimitDegrees;
    }

    public void setSlopeLimitDegrees(float slopeLimitDegrees) {
        requireFinite(slopeLimitDegrees, "slopeLimitDegrees");
        if (slopeLimitDegrees < 0f || slopeLimitDegrees > MAX_SLOPE_LIMIT_DEGREES) {
            throw new IllegalArgumentException("slopeLimitDegrees must be between 0 and " + MAX_SLOPE_LIMIT_DEGREES + ": "
                + slopeLimitDegrees);
        }
        this.slopeLimitDegrees = slopeLimitDegrees;
    }

    /** Highest step the character walks onto, in scene units; 0 turns stair climbing off. */
    public float getStepHeight() {
        return stepHeight;
    }

    public void setStepHeight(float stepHeight) {
        requireFinite(stepHeight, "stepHeight");
        if (stepHeight < 0f) {
            throw new IllegalArgumentException("stepHeight cannot be negative: " + stepHeight);
        }
        this.stepHeight = stepHeight;
    }

    /** Mass in kilograms, which decides how hard the character can push dynamic bodies. */
    public float getMass() {
        return mass;
    }

    public void setMass(float mass) {
        this.mass = requirePositive(mass, "mass");
    }

    /** Multiplier on world gravity: 0 floats, 1 is normal, negative rises. */
    public float getGravityScale() {
        return gravityScale;
    }

    public void setGravityScale(float gravityScale) {
        requireFinite(gravityScale, "gravityScale");
        this.gravityScale = gravityScale;
    }

    /** Collision layer, 0 to {@link PhysicsLayers3D#LAYER_COUNT} - 1. */
    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        PhysicsLayers3D.requireValidLayer(layer);
        this.layer = layer;
    }

    /** Bit {@code n} set means other bodies' contacts with this character on layer {@code n} are reported. */
    public int getCollisionMask() {
        return collisionMask;
    }

    public void setCollisionMask(int collisionMask) {
        this.collisionMask = collisionMask & PhysicsLayers3D.ALL_LAYERS;
    }

    private static float requirePositive(float value, String name) {
        requireFinite(value, name);
        if (value <= 0f) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
        return value;
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number: " + value);
        }
    }
}
