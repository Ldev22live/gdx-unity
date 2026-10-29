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
}
