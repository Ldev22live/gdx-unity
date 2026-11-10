package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import java.util.List;

/**
 * The physics engine behind {@link PhysicsSystem3D} (TD-028). Implementations wrap one library (the
 * evaluation recommends Jolt Physics through jolt-jni); the rest of the engine never sees library types.
 * <p>
 * All values are SI units (meters, seconds, kilograms). Bodies are named by integer handles the backend
 * chooses. Calls come from one thread and never during {@link #step}. Collision filtering must follow
 * {@link PhysicsLayers3D#collide}.
 */
public interface PhysicsBackend3D extends Disposable {

    /** A pair of bodies started or stopped touching (or overlapping, when one is a sensor). */
    record ContactEvent(int bodyA, int bodyB, boolean began) {}

    /** Closest hit of a ray; {@code distance} is along the normalized direction. */
    record RayHit(int body, Vector3 point, Vector3 normal, float distance) {}

    void setGravity(Vector3 gravity);

    /** @return the new body's handle */
    int addBody(BodyDescriptor3D body);

    void removeBody(int handle);

    /** Moves a kinematic body so it reaches the pose after {@code deltaSeconds}, pushing dynamic bodies. */
    void moveKinematic(int handle, Vector3 position, Quaternion rotation, float deltaSeconds);

    void getBodyTransform(int handle, Vector3 outPosition, Quaternion outRotation);

    void step(float deltaSeconds);

    /** Appends the contact events since the last call to {@code out}. */
    void drainContactEvents(List<ContactEvent> out);

    /** @return the closest hit on a body whose layer is in {@code layerMask}, or null */
    RayHit raycast(Vector3 origin, Vector3 direction, float maxDistance, int layerMask);

    /**
     * Adds a force (newtons) that acts during the next {@link #step} only, then is gone; call it before every step it
     * should act in. The body must be dynamic.
     */
    void addForce(int handle, Vector3 force);

    /** Like {@link #addForce} for a torque (newton meters). */
    void addTorque(int handle, Vector3 torque);

    /** Changes the body's velocity at once by {@code impulse / mass} (impulse in kilogram meters per second). The body must be dynamic. */
    void addImpulse(int handle, Vector3 impulse);

    /** Sets the linear velocity (meters per second). The body must be dynamic or kinematic. */
    void setLinearVelocity(int handle, Vector3 velocity);

    void getLinearVelocity(int handle, Vector3 outVelocity);

    /** Sets the angular velocity (radians per second around each world axis). The body must be dynamic or kinematic. */
    void setAngularVelocity(int handle, Vector3 velocity);

    void getAngularVelocity(int handle, Vector3 outVelocity);

    /**
     * Appends the handles of the bodies whose shape overlaps the sphere and whose layer is in {@code layerMask}.
     * Sensor bodies are not reported. Each body appears once.
     */
    void overlapSphere(Vector3 center, float radius, int layerMask, List<Integer> outBodies);

    // --- Walking characters (TD-049) ---

    /**
     * Creates a character: an upright capsule that is moved by {@link #moveCharacter}, not by forces. Other bodies collide with
     * it and a dynamic body it walks into is pushed. Character handles are separate from body handles and never equal one;
     * contact events name the character by its handle, and {@link #raycast} and {@link #overlapSphere} report it by handle.
     *
     * @return the new character's handle
     */
    int addCharacter(CharacterDescriptor3D character);

    void removeCharacter(int handle);

    /**
     * Moves the character for {@code deltaSeconds} at {@code velocity} (meters per second): it slides along walls, climbs
     * steps within its step height, stays on the ground when walking down slopes and stops at slopes steeper than its limit.
     * The caller applies gravity by including it in {@code velocity}. Call it before {@link #step}.
     */
    void moveCharacter(int handle, Vector3 velocity, float deltaSeconds);

    /**
     * Puts the capsule's center at {@code position} (meters) at once, without sweeping through anything in between. Overlaps
     * with other bodies are resolved by the next {@link #moveCharacter}.
     */
    void setCharacterPosition(int handle, Vector3 position);

    /** The capsule's center in meters. */
    void getCharacterPosition(int handle, Vector3 outPosition);

    /** Whether the character stands on ground it can walk on (not a slope above its limit, not in the air). */
    boolean isCharacterGrounded(int handle);

    // --- Joints (TD-051) ---

    /**
     * Joins two bodies, or a body and the world. Joint handles are separate from body and character handles. Remove a joint
     * before removing a body it joins.
     *
     * @return the new joint's handle
     * @throws UnsupportedOperationException when the backend has no joints
     */
    default int addJoint(JointDescriptor3D joint) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " has no joints");
    }

    default void removeJoint(int handle) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " has no joints");
    }

    /**
     * Changes the motor of a hinge or slider: {@code target} is radians per second or radians (hinge), meters per second or
     * meters (slider) depending on {@code mode}; {@code maxForce} is newton meters (hinge) or newtons (slider).
     */
    default void setJointMotor(int handle, Joint3DComponent.MotorMode mode, float target, float maxForce) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " has no joints");
    }

    /** The hinge angle in radians or the slider position in meters, from the joint's rest pose. */
    default float getJointPosition(int handle) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " has no joints");
    }
}
