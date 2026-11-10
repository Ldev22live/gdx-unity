package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BodyDescriptor3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterDescriptor3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsBackend3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A 3D physics backend for graph tests: bodies stay where they were put, contacts and ray hits are set by the test, and
 * every force, impulse and velocity call is written down.
 */
final class StubPhysicsBackend3D implements PhysicsBackend3D {
    /** Handles of the bodies in the order they were added, with their descriptors. */
    final List<Integer> handles = new ArrayList<>();
    final List<BodyDescriptor3D> descriptors = new ArrayList<>();
    final List<ContactEvent> events = new ArrayList<>();
    /** "force", "torque" or "impulse", the handle and the vector, e.g. {@code impulse 1 0.0 5.0 0.0}. */
    final List<String> calls = new ArrayList<>();
    final Map<Integer, Vector3> linearVelocities = new HashMap<>();
    /** Body a ray hits, or null for a miss. */
    Integer hitHandle;
    private int nextHandle = 1;

    /** Handle of the {@code index}-th body added. */
    int handle(int index) {
        return handles.get(index);
    }

    @Override
    public void setGravity(Vector3 gravity) {
    }

    @Override
    public int addBody(BodyDescriptor3D body) {
        int handle = nextHandle++;
        handles.add(handle);
        descriptors.add(body);
        return handle;
    }

    @Override
    public void removeBody(int handle) {
    }

    @Override
    public void moveKinematic(int handle, Vector3 position, Quaternion rotation, float deltaSeconds) {
    }

    @Override
    public void getBodyTransform(int handle, Vector3 outPosition, Quaternion outRotation) {
        outPosition.set(descriptors.get(handles.indexOf(handle)).position());
        outRotation.set(descriptors.get(handles.indexOf(handle)).rotation());
    }

    @Override
    public void step(float deltaSeconds) {
    }

    @Override
    public void drainContactEvents(List<ContactEvent> out) {
        out.addAll(events);
        events.clear();
    }

    @Override
    public RayHit raycast(Vector3 origin, Vector3 direction, float maxDistance, int layerMask) {
        return hitHandle == null ? null : new RayHit(hitHandle, new Vector3(0f, 0.5f, 0f), new Vector3(0f, 1f, 0f), 0.5f);
    }

    private void record(String name, int handle, Vector3 v) {
        calls.add(name + " " + handle + " " + v.x + " " + v.y + " " + v.z);
    }

    @Override
    public void addForce(int handle, Vector3 force) {
        record("force", handle, force);
    }

    @Override
    public void addTorque(int handle, Vector3 torque) {
        record("torque", handle, torque);
    }

    @Override
    public void addImpulse(int handle, Vector3 impulse) {
        record("impulse", handle, impulse);
    }

    @Override
    public void setLinearVelocity(int handle, Vector3 velocity) {
        record("velocity", handle, velocity);
        linearVelocities.put(handle, new Vector3(velocity));
    }

    @Override
    public void getLinearVelocity(int handle, Vector3 outVelocity) {
        outVelocity.set(linearVelocities.getOrDefault(handle, Vector3.Zero));
    }

    @Override
    public void setAngularVelocity(int handle, Vector3 velocity) {
    }

    @Override
    public void getAngularVelocity(int handle, Vector3 outVelocity) {
        outVelocity.setZero();
    }

    @Override
    public void overlapSphere(Vector3 center, float radius, int layerMask, List<Integer> outBodies) {
    }

    // --- Characters (TD-049): a test sets grounded; moves integrate the velocity ---
    final Map<Integer, CharacterDescriptor3D> characterDescriptors = new LinkedHashMap<>();
    final Map<Integer, Vector3> characterPositions = new LinkedHashMap<>();
    /** Velocity of every moveCharacter call, in call order. */
    final List<Vector3> characterMoves = new ArrayList<>();
    boolean grounded;
    int nextCharacterHandle = -2;

    @Override
    public int addCharacter(CharacterDescriptor3D character) {
        int handle = nextCharacterHandle--;
        characterDescriptors.put(handle, character);
        characterPositions.put(handle, new Vector3(character.position()));
        return handle;
    }

    @Override
    public void removeCharacter(int handle) {
        if (characterDescriptors.remove(handle) == null) {
            throw new IllegalArgumentException("Removed a character that does not exist: " + handle);
        }
        characterPositions.remove(handle);
    }

    @Override
    public void moveCharacter(int handle, Vector3 velocity, float deltaSeconds) {
        characterMoves.add(new Vector3(velocity));
        characterPositions.get(handle).mulAdd(velocity, deltaSeconds);
    }

    @Override
    public void setCharacterPosition(int handle, Vector3 position) {
        characterPositions.put(handle, new Vector3(position));
    }

    @Override
    public void getCharacterPosition(int handle, Vector3 outPosition) {
        outPosition.set(characterPositions.get(handle));
    }

    @Override
    public boolean isCharacterGrounded(int handle) {
        return grounded;
    }

    @Override
    public void dispose() {
    }
}
