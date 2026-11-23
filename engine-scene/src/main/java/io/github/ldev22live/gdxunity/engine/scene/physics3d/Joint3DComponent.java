package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * Joins this entity's 3D body to another body, or to the world (TD-051). The joint is created when physics starts and is
 * removed while this entity, the connected entity or this component is inactive; it is created again when they are back.
 * <p>
 * The pose both bodies have at that moment is the joint's rest pose: a hinge angle or slider position of 0 is where they stood
 * when the joint was made. Lengths are scene units, angles degrees. The joint needs a solid collider on this entity and on the
 * connected entity, and at least one of the two must be a dynamic body; a character is never joined (see
 * {@link PhysicsSystem3D}, which refuses with a message naming the entity).
 * <p>
 * Everything except the motor is read once, when the joint is created; changing it during play has no effect. The motor can be
 * changed any time with {@link PhysicsSystem3D#setJointMotor}.
 */
public class Joint3DComponent extends BaseComponent {

    /** What the joint allows. */
    public enum Type {
        /** The two bodies keep their pose relative to each other. */
        FIXED,
        /** Rotation around one axis through the anchor, like a door. Limits are angles in degrees from the rest pose. */
        HINGE,
        /** Sliding along one axis, like a drawer. Limits are distances in scene units from the rest pose. */
        SLIDER,
        /** The anchor points stay between a smallest and a largest distance: a rod, or a rope that cannot stretch. */
        DISTANCE,
        /** The anchor points stay together and the bodies turn freely, like a shoulder or a chain link. */
        BALL
    }

    /** What the motor of a hinge or slider does. */
    public enum MotorMode {
        OFF,
        /** Drives the joint at {@link #getMotorTarget()}: degrees per second (hinge) or scene units per second (slider). */
        VELOCITY,
        /** Drives the joint to {@link #getMotorTarget()}: degrees (hinge) or scene units (slider) from the rest pose. */
        POSITION
    }

    /** Largest hinge angle either side of the rest pose, in degrees. */
    public static final float MAX_HINGE_DEGREES = 180f;
    /** Strongest push a motor may apply when none is set: newton meters for a hinge, newtons for a slider. */
    public static final float DEFAULT_MOTOR_MAX_FORCE = 1000f;

    private Type type = Type.HINGE;
    private String connectedName = "";
    private final Vector3 anchor = new Vector3();
    private final Vector3 connectedAnchor = new Vector3();
    private final Vector3 axis = new Vector3(Vector3.Y);
    private boolean collideConnected;
    private boolean limitsEnabled;
    private float lowerLimit;
    private float upperLimit;
    private MotorMode motorMode = MotorMode.OFF;
    private float motorTarget;
    private float motorMaxForce = DEFAULT_MOTOR_MAX_FORCE;

    public Joint3DComponent() {
    }

    public Joint3DComponent(Type type) {
        setType(type);
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
    }

    /** Name of the entity this one is joined to (first match); empty joins this entity to the world. */
    public String getConnectedName() {
        return connectedName;
    }

    public void setConnectedName(String connectedName) {
        this.connectedName = Objects.requireNonNull(connectedName, "connectedName cannot be null");
    }

    /** The joint's anchor point in this entity's local space, in scene units. */
    public Vector3 getAnchor() {
        return new Vector3(anchor);
    }

    public void setAnchor(Vector3 anchor) {
        this.anchor.set(requireFinite(anchor, "anchor"));
    }

    /**
     * Only for {@link Type#DISTANCE}: the second end of the rod or rope, in the connected entity's local space, or a world point
     * when the joint is connected to the world, in scene units.
     */
    public Vector3 getConnectedAnchor() {
        return new Vector3(connectedAnchor);
    }

    public void setConnectedAnchor(Vector3 connectedAnchor) {
        this.connectedAnchor.set(requireFinite(connectedAnchor, "connectedAnchor"));
    }

    /** The hinge or slider axis in this entity's local space; length does not matter. Unused by the other types. */
    public Vector3 getAxis() {
        return new Vector3(axis);
    }

    public void setAxis(Vector3 axis) {
        requireFinite(axis, "axis");
        if (axis.isZero(1e-6f)) {
            throw new IllegalArgumentException("axis cannot be zero");
        }
        this.axis.set(axis);
    }

    /** Whether the two joined bodies still collide with each other. Off by default: joined parts usually overlap. */
    public boolean isCollideConnected() {
        return collideConnected;
    }

    public void setCollideConnected(boolean collideConnected) {
        this.collideConnected = collideConnected;
    }

    /**
     * Whether {@link #getLowerLimit()} and {@link #getUpperLimit()} apply. For {@link Type#DISTANCE} off means the distance
     * the anchors have at the start stays exactly, on means they may be anywhere between the limits.
     */
    public boolean isLimitsEnabled() {
        return limitsEnabled;
    }

    public void setLimitsEnabled(boolean limitsEnabled) {
        this.limitsEnabled = limitsEnabled;
    }

    /** Smallest hinge angle (degrees, at most 0), slider position (scene units, at most 0) or anchor distance (scene units). */
    public float getLowerLimit() {
        return lowerLimit;
    }

    /** Largest hinge angle (degrees, at least 0), slider position (scene units, at least 0) or anchor distance (scene units). */
    public float getUpperLimit() {
        return upperLimit;
    }

    /**
     * Sets both limits together so that they can be checked against each other.
     *
     * @throws IllegalArgumentException when a limit is not finite or {@code lower} is above {@code upper}
     */
    public void setLimits(float lower, float upper) {
        if (!Float.isFinite(lower) || !Float.isFinite(upper)) {
            throw new IllegalArgumentException("limits must be finite numbers: " + lower + ", " + upper);
        }
        if (lower > upper) {
            throw new IllegalArgumentException("lower limit " + lower + " is above upper limit " + upper);
        }
        this.lowerLimit = lower;
        this.upperLimit = upper;
    }

    public MotorMode getMotorMode() {
        return motorMode;
    }

    public void setMotorMode(MotorMode motorMode) {
        this.motorMode = Objects.requireNonNull(motorMode, "motorMode cannot be null");
    }

    /** Target speed or position of the motor, see {@link MotorMode}. */
    public float getMotorTarget() {
        return motorTarget;
    }

    public void setMotorTarget(float motorTarget) {
        if (!Float.isFinite(motorTarget)) {
            throw new IllegalArgumentException("motorTarget must be a finite number: " + motorTarget);
        }
        this.motorTarget = motorTarget;
    }

    /** The most the motor pushes: newton meters for a hinge, newtons for a slider. */
    public float getMotorMaxForce() {
        return motorMaxForce;
    }

    public void setMotorMaxForce(float motorMaxForce) {
        if (!Float.isFinite(motorMaxForce) || motorMaxForce <= 0f) {
            throw new IllegalArgumentException("motorMaxForce must be positive and finite: " + motorMaxForce);
        }
        this.motorMaxForce = motorMaxForce;
    }

    private static Vector3 requireFinite(Vector3 vector, String name) {
        Objects.requireNonNull(vector, name + " cannot be null");
        if (!Float.isFinite(vector.x) || !Float.isFinite(vector.y) || !Float.isFinite(vector.z)) {
            throw new IllegalArgumentException(name + " must be finite: " + vector);
        }
        return vector;
    }
}
