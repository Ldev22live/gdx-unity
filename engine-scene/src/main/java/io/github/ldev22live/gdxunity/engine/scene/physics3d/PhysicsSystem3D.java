package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptContactForwarder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs 3D physics for a scene on a {@link PhysicsBackend3D} (Phase 15, TD-028).
 * <p>
 * Each frame it creates bodies for entities that gained colliders, removes bodies of entities that left the
 * scene or were deactivated, then advances the simulation in fixed steps. Kinematic bodies follow their
 * transforms; dynamic bodies write their pose back to their entity. Contact events are reported after the
 * steps, so listeners may destroy entities safely: their bodies are removed before the next step.
 * <p>
 * Every entity with an enabled 3D collider gets up to two bodies: one for its solid colliders and one sensor
 * body for its trigger colliders, which follows the entity. Entities without a {@link RigidBody3DComponent}
 * are static. Collider sizes are read when the body is created; changing them during play has no effect.
 * <p>
 * While it exists the system is the scene's {@link Scene#getPhysics3D()}, and scripts get their 3D contact callbacks
 * through it (TD-048). Scripts act on bodies with {@link #addForce}, {@link #addImpulse}, {@link #setLinearVelocity} and
 * the like. All vectors in this class's public methods are in scene units (100 per meter): positions and distances in
 * units, velocities in units per second, forces in kilogram units per second squared, impulses in kilogram units per second
 * and torques in kilogram square units per second squared. Angular velocity is in radians per second.
 * <p>
 * An entity with a {@link CharacterController3DComponent} is a walking character instead of a rigid body (TD-049): scripts give
 * it a horizontal velocity with {@link #moveCharacter} and jump with {@link #jumpCharacter}; the system adds gravity in every
 * fixed step, lets the backend collide the capsule and writes the position back to the entity.
 */
public class PhysicsSystem3D implements Disposable {
    public static final float FIXED_TIMESTEP = 1f / 60f;
    /** Longest frame time simulated; longer frames (breakpoints, loading) are cut to this. */
    public static final float MAX_FRAME_DELTA = 0.1f;
    public static final int MAX_SUB_STEPS = 5;
    /** Earth gravity in meters per second squared. */
    public static final Vector3 DEFAULT_GRAVITY = new Vector3(0f, -9.81f, 0f);

    /** What happened between two entities. */
    public enum ContactKind {
        COLLISION_ENTER,
        COLLISION_EXIT,
        TRIGGER_ENTER,
        TRIGGER_EXIT
    }

    public record Contact(Entity entityA, Entity entityB, ContactKind kind) {}

    public interface ContactListener {
        void onContact(Contact contact);
    }

    /** Closest ray hit in scene units. */
    public record RaycastHit(Entity entity, Vector3 point, Vector3 normal, float distance) {}

    private static final int NO_BODY = -1;
    /** A convex hull collider keeps at most this many points (TD-052); Jolt works best with small hulls. */
    public static final int MAX_HULL_POINTS = 64;
    /** A collider mesh with more triangles than this is reported in {@link #getWarnings()}. */
    public static final int LARGE_MESH_TRIANGLES = 100_000;
    /**
     * A rising character that moved less than this share of what its speed asked for in a step has hit a ceiling. Not 0, because
     * sliding along a slanted ceiling still gains some height.
     */
    private static final float CEILING_HIT_FRACTION = 0.25f;

    private final Scene scene;
    private final PhysicsBackend3D backend;
    private final Map<UUID, EntityBodies> bodiesByEntity = new LinkedHashMap<>();
    private final Map<Integer, Entity> entityByHandle = new HashMap<>();
    private final Set<Integer> sensorHandles = new HashSet<>();
    private final Map<UUID, CharacterState> charactersByEntity = new LinkedHashMap<>();
    private final Map<UUID, JointState> jointsByEntity = new LinkedHashMap<>();
    private final List<ContactListener> listeners = new CopyOnWriteArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<PhysicsBackend3D.ContactEvent> pendingEvents = new ArrayList<>();
    /** Forces and torques in SI units waiting for the next steps, see {@link #addForce}. */
    private final Map<UUID, Vector3> pendingForces = new HashMap<>();
    private final Map<UUID, Vector3> pendingTorques = new HashMap<>();
    private final List<Integer> overlapHandles = new ArrayList<>();
    private final Vector3 tmpPosition = new Vector3();
    private final Vector3 tmpVelocity = new Vector3();
    private final Quaternion tmpRotation = new Quaternion();
    private final Matrix4 tmpMatrix = new Matrix4();
    private float accumulator;
    private int stepCount;
    private boolean disposed;

    /** A character's state between fixed steps; speeds are meters per second. */
    private static final class CharacterState {
        final Entity entity;
        final int handle;
        final CharacterController3DComponent controller;
        float desiredX;
        float desiredZ;
        float verticalSpeed;
        boolean grounded;
        final Vector3 position = new Vector3();
        final Vector3 velocity = new Vector3();

        CharacterState(Entity entity, int handle, CharacterController3DComponent controller) {
            this.entity = entity;
            this.handle = handle;
            this.controller = controller;
        }
    }

    /** A live joint: the handles of the two bodies it was made for, so it can be replaced when one of them is. */
    private static final class JointState {
        final Entity entity;
        final Entity connected;
        final int handle;
        final int bodyA;
        final int bodyB;
        final Joint3DComponent.Type type;
        final float motorMaxForce;

        JointState(Entity entity, Entity connected, int handle, int bodyA, int bodyB, Joint3DComponent.Type type, float motorMaxForce) {
            this.entity = entity;
            this.connected = connected;
            this.handle = handle;
            this.bodyA = bodyA;
            this.bodyB = bodyB;
            this.type = type;
            this.motorMaxForce = motorMaxForce;
        }
    }

    private static final class EntityBodies {
        final Entity entity;
        final RigidBody3DComponent.MotionType motionType;
        int solid = NO_BODY;
        int sensor = NO_BODY;

        EntityBodies(Entity entity, RigidBody3DComponent.MotionType motionType) {
            this.entity = entity;
            this.motionType = motionType;
        }
    }

    /**
     * Whether {@code scene} has any 3D collider on an active entity. Callers create a backend (which loads a native
     * library) only for scenes where this is true, so 2D games never pay for 3D physics.
     */
    public static boolean isUsedBy(Scene scene) {
        Objects.requireNonNull(scene, "scene cannot be null");
        for (Entity entity : scene.getAllEntities()) {
            if (entity.isActiveInHierarchy() && (!colliders(entity).isEmpty() || controllerOf(entity) != null)) {
                return true;
            }
        }
        return false;
    }

    public PhysicsSystem3D(Scene scene, PhysicsBackend3D backend) {
        this.scene = Objects.requireNonNull(scene, "scene cannot be null");
        this.backend = Objects.requireNonNull(backend, "backend cannot be null");
        backend.setGravity(DEFAULT_GRAVITY);
        // Last: when the scene already has a system this throws, and nothing has been registered yet
        scene.attachPhysics3D(this);
        listeners.add(new ScriptContactForwarder());
    }

    public void addContactListener(ContactListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    public void removeContactListener(ContactListener listener) {
        listeners.remove(listener);
    }

    /** Fixed steps taken so far. */
    public int getStepCount() {
        return stepCount;
    }

    /** Whether {@code entity} currently has a body or is a character. */
    public boolean hasBody(Entity entity) {
        return entity != null && (bodiesByEntity.containsKey(entity.getId()) || charactersByEntity.containsKey(entity.getId()));
    }

    /**
     * Advances the simulation by {@code deltaTime} seconds in {@link #FIXED_TIMESTEP} steps (at most
     * {@link #MAX_SUB_STEPS} per call), then syncs transforms and reports contacts.
     */
    public void update(float deltaTime) {
        if (disposed) {
            return;
        }
        syncBodies();
        accumulator += Math.min(Math.max(0f, deltaTime), MAX_FRAME_DELTA);
        int steps = 0;
        while (accumulator >= FIXED_TIMESTEP && steps < MAX_SUB_STEPS) {
            pushKinematicPoses();
            applyPendingForces();
            stepCharacters();
            backend.step(FIXED_TIMESTEP);
            accumulator -= FIXED_TIMESTEP;
            steps++;
            stepCount++;
        }
        if (steps > 0) {
            // A force acts for the simulated time since the script applied it; with no step yet it waits for the next update
            pendingForces.clear();
            pendingTorques.clear();
        }
        if (accumulator > FIXED_TIMESTEP) {
            // Too far behind: drop the backlog rather than spiral
            accumulator = 0f;
        }
        pullDynamicPoses();
        pullCharacterPoses();
        dispatchContacts();
    }

    /**
     * Casts a ray in scene units.
     *
     * @param layerMask layers that can be hit, see {@link PhysicsLayers3D}
     */
    public Optional<RaycastHit> raycast(Vector3 origin, Vector3 direction, float maxDistance, int layerMask) {
        Objects.requireNonNull(origin, "origin cannot be null");
        Objects.requireNonNull(direction, "direction cannot be null");
        if (direction.isZero() || !(maxDistance > 0f)) {
            throw new IllegalArgumentException("Ray needs a direction and a positive distance: " + direction + ", " + maxDistance);
        }
        PhysicsBackend3D.RayHit hit = backend.raycast(new Vector3(origin).scl(PhysicsUnits3D.METERS_PER_UNIT),
            new Vector3(direction).nor(), PhysicsUnits3D.toMeters(maxDistance), layerMask);
        if (hit == null) {
            return Optional.empty();
        }
        Entity entity = entityByHandle.get(hit.body());
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(new RaycastHit(entity, new Vector3(hit.point()).scl(PhysicsUnits3D.UNITS_PER_METER),
            new Vector3(hit.normal()), PhysicsUnits3D.toSceneUnits(hit.distance())));
    }

    /**
     * Entities whose shape overlaps a sphere, each once, in no particular order. Sensors (trigger colliders) are not reported.
     *
     * @param center    in scene units
     * @param radius    in scene units, greater than zero
     * @param layerMask layers that can be hit, see {@link PhysicsLayers3D}
     */
    public List<Entity> overlapSphere(Vector3 center, float radius, int layerMask) {
        Objects.requireNonNull(center, "center cannot be null");
        requireFinite(center, "center");
        if (!(radius > 0f) || Float.isInfinite(radius)) {
            throw new IllegalArgumentException("Sphere radius must be positive and finite: " + radius);
        }
        overlapHandles.clear();
        backend.overlapSphere(new Vector3(center).scl(PhysicsUnits3D.METERS_PER_UNIT), PhysicsUnits3D.toMeters(radius), layerMask,
            overlapHandles);
        Set<Entity> found = new LinkedHashSet<>();
        for (int handle : overlapHandles) {
            Entity entity = entityByHandle.get(handle);
            if (entity != null) {
                found.add(entity);
            }
        }
        return new ArrayList<>(found);
    }

    // --- Forces and velocities (TD-048) ---

    /**
     * Pushes a dynamic entity with a force that keeps acting until the next physics update has stepped: call it every frame
     * the push should last. The force acts on every fixed step of that update, so a push that lasts a second moves the body
     * the same at 30 and at 60 frames per second.
     *
     * @throws IllegalStateException    when the entity has no dynamic body (no body, only triggers, or not dynamic)
     * @throws IllegalArgumentException when a component of {@code force} is not finite
     */
    public void addForce(Entity entity, Vector3 force) {
        dynamicBodyOf(entity, "addForce");
        Vector3 meters = finiteScaled(force, "force", PhysicsUnits3D.METERS_PER_UNIT);
        pendingForces.computeIfAbsent(entity.getId(), id -> new Vector3()).add(meters);
    }

    /** Like {@link #addForce} for a torque. */
    public void addTorque(Entity entity, Vector3 torque) {
        dynamicBodyOf(entity, "addTorque");
        Vector3 meters = finiteScaled(torque, "torque", PhysicsUnits3D.METERS_PER_UNIT * PhysicsUnits3D.METERS_PER_UNIT);
        pendingTorques.computeIfAbsent(entity.getId(), id -> new Vector3()).add(meters);
    }

    /**
     * Changes a dynamic entity's velocity at once by {@code impulse / mass}: a kick, a jump, a shot's recoil.
     *
     * @throws IllegalStateException    when the entity has no dynamic body
     * @throws IllegalArgumentException when a component of {@code impulse} is not finite
     */
    public void addImpulse(Entity entity, Vector3 impulse) {
        int handle = dynamicBodyOf(entity, "addImpulse");
        backend.addImpulse(handle, finiteScaled(impulse, "impulse", PhysicsUnits3D.METERS_PER_UNIT));
    }

    /**
     * Sets the velocity of a dynamic or kinematic entity, in scene units per second. For a kinematic body the velocity
     * does not move it (its transform does, see {@link RigidBody3DComponent.MotionType}); it only changes what it pushes.
     *
     * @throws IllegalStateException    when the entity has no non-static body
     * @throws IllegalArgumentException when a component of {@code velocity} is not finite
     */
    public void setLinearVelocity(Entity entity, Vector3 velocity) {
        int handle = movingBodyOf(entity, "setLinearVelocity");
        backend.setLinearVelocity(handle, finiteScaled(velocity, "velocity", PhysicsUnits3D.METERS_PER_UNIT));
    }

    /**
     * The entity's linear velocity in scene units per second; zero for a static body.
     *
     * @throws IllegalStateException when the entity has no solid body
     */
    public Vector3 getLinearVelocity(Entity entity) {
        int handle = solidBodyOf(entity, "getLinearVelocity");
        Vector3 velocity = new Vector3();
        backend.getLinearVelocity(handle, velocity);
        return velocity.scl(PhysicsUnits3D.UNITS_PER_METER);
    }

    /** Sets the angular velocity of a dynamic or kinematic entity, in radians per second around the world axes. */
    public void setAngularVelocity(Entity entity, Vector3 velocity) {
        int handle = movingBodyOf(entity, "setAngularVelocity");
        backend.setAngularVelocity(handle, finiteScaled(velocity, "velocity", 1f));
    }

    /** The entity's angular velocity in radians per second around the world axes; zero for a static body. */
    public Vector3 getAngularVelocity(Entity entity) {
        int handle = solidBodyOf(entity, "getAngularVelocity");
        Vector3 velocity = new Vector3();
        backend.getAngularVelocity(handle, velocity);
        return velocity;
    }

    // --- Characters (TD-049) ---

    /**
     * Sets the speed a character walks at, in scene units per second along the world X and Z axes; it keeps walking until this
     * is called again. The Y component is ignored: gravity and {@link #jumpCharacter} decide the vertical movement.
     *
     * @throws IllegalStateException    when the entity is not a character (yet)
     * @throws IllegalArgumentException when a component of {@code velocity} is not finite
     */
    public void moveCharacter(Entity entity, Vector3 velocity) {
        CharacterState state = characterOf(entity, "moveCharacter");
        Vector3 meters = finiteScaled(velocity, "velocity", PhysicsUnits3D.METERS_PER_UNIT);
        state.desiredX = meters.x;
        state.desiredZ = meters.z;
    }

    /**
     * Makes a character leave the ground at {@code speed} scene units per second, but only while it stands on ground.
     *
     * @return whether it jumped
     * @throws IllegalStateException    when the entity is not a character (yet)
     * @throws IllegalArgumentException when {@code speed} is not a positive finite number
     */
    public boolean jumpCharacter(Entity entity, float speed) {
        CharacterState state = characterOf(entity, "jumpCharacter");
        if (!Float.isFinite(speed) || speed <= 0f) {
            throw new IllegalArgumentException("Jump speed must be positive and finite: " + speed);
        }
        if (!state.grounded) {
            return false;
        }
        state.verticalSpeed = PhysicsUnits3D.toMeters(speed);
        state.grounded = false;
        return true;
    }

    /**
     * Puts a character at {@code position} (scene units, the capsule's center) at once, for a respawn or a portal. It is not
     * swept: nothing between the old and the new place is touched. Its fall speed and the walking velocity set with
     * {@link #moveCharacter} are cleared, it counts as not grounded until the next fixed step, and the entity's position is
     * updated immediately.
     *
     * @throws IllegalStateException    when the entity is not a character (yet)
     * @throws IllegalArgumentException when a component of {@code position} is not finite
     */
    public void teleportCharacter(Entity entity, Vector3 position) {
        CharacterState state = characterOf(entity, "teleportCharacter");
        Vector3 meters = finiteScaled(position, "position", PhysicsUnits3D.METERS_PER_UNIT);
        backend.setCharacterPosition(state.handle, meters);
        state.desiredX = 0f;
        state.desiredZ = 0f;
        state.verticalSpeed = 0f;
        state.grounded = false;
        state.velocity.setZero();
        setWorldPose(state.entity.getTransform(), tmpPosition.set(position), tmpRotation.set(state.entity.getTransform().getWorldRotation()));
    }

    /** Whether a character stands on ground it can walk on. False in the air and on slopes above its limit. */
    public boolean isCharacterGrounded(Entity entity) {
        return characterOf(entity, "isCharacterGrounded").grounded;
    }

    /** How fast a character actually moved in its last fixed step, in scene units per second (walls and floors included). */
    public Vector3 getCharacterVelocity(Entity entity) {
        return new Vector3(characterOf(entity, "getCharacterVelocity").velocity).scl(PhysicsUnits3D.UNITS_PER_METER);
    }

    // --- Joints (TD-051) ---

    /**
     * Changes the motor of the joint on {@code entity} while the game runs. Hinge and slider only. {@code target} is degrees per
     * second or degrees from the rest pose (hinge), scene units per second or scene units from the rest pose (slider). The most
     * the motor pushes is the component's {@code motorMaxForce}.
     *
     * @throws IllegalStateException    when the entity has no joint (yet), or the joint has no motor (not a hinge or slider)
     * @throws IllegalArgumentException when {@code target} is not finite
     */
    public void setJointMotor(Entity entity, Joint3DComponent.MotorMode mode, float target) {
        Objects.requireNonNull(mode, "mode cannot be null");
        JointState state = jointOf(entity, "setJointMotor");
        if (!Float.isFinite(target)) {
            throw new IllegalArgumentException("target must be a finite number: " + target);
        }
        requireMotorType(state.entity, state.type);
        backend.setJointMotor(state.handle, mode, motorTargetInSi(state.type, target), state.motorMaxForce);
    }

    /**
     * How far a hinge has turned in degrees, or a slider has slid in scene units, from where it stood when the joint was made.
     *
     * @throws IllegalStateException when the entity has no joint (yet) or it is not a hinge or slider
     */
    public float getJointPosition(Entity entity) {
        JointState state = jointOf(entity, "getJointPosition");
        requireMotorType(state.entity, state.type);
        float value = backend.getJointPosition(state.handle);
        return state.type == Joint3DComponent.Type.HINGE ? (float) Math.toDegrees(value) : PhysicsUnits3D.toSceneUnits(value);
    }

    /** Whether {@code entity} currently has a joint in the simulation. */
    public boolean hasJoint(Entity entity) {
        return entity != null && jointsByEntity.containsKey(entity.getId());
    }

    private static void requireMotorType(Entity entity, Joint3DComponent.Type type) {
        if (type != Joint3DComponent.Type.HINGE && type != Joint3DComponent.Type.SLIDER) {
            throw new IllegalStateException(entity.getName() + " has a " + type + " joint, and only a HINGE or SLIDER has a motor or a position.");
        }
    }

    private static float motorTargetInSi(Joint3DComponent.Type type, float target) {
        return type == Joint3DComponent.Type.HINGE ? (float) Math.toRadians(target) : PhysicsUnits3D.toMeters(target);
    }

    private JointState jointOf(Entity entity, String operation) {
        Objects.requireNonNull(entity, "entity cannot be null");
        if (disposed) {
            throw new IllegalStateException(operation + ": the 3D physics of this scene has stopped");
        }
        JointState state = jointsByEntity.get(entity.getId());
        if (state == null) {
            throw new IllegalStateException(operation + ": " + entity.getName() + " has no joint. Add a Joint 3D to it and keep it, "
                + "its body and the connected entity active; the joint is created at the start of the next physics update.");
        }
        return state;
    }

    private CharacterState characterOf(Entity entity, String operation) {
        Objects.requireNonNull(entity, "entity cannot be null");
        if (disposed) {
            throw new IllegalStateException(operation + ": the 3D physics of this scene has stopped");
        }
        CharacterState state = charactersByEntity.get(entity.getId());
        if (state == null) {
            throw new IllegalStateException(operation + ": " + entity.getName() + " is not a character. Add a Character Controller 3D "
                + "to it and keep it active; the character is created at the start of the next physics update, so an entity "
                + "created this frame has none yet.");
        }
        return state;
    }

    private void stepCharacters() {
        float gravity = DEFAULT_GRAVITY.y;
        for (CharacterState state : charactersByEntity.values()) {
            // Gravity as the average speed over the step, so a jump follows the exact parabola whatever the step length
            float moveY;
            float speedChange;
            if (state.grounded && state.verticalSpeed <= 0f) {
                state.verticalSpeed = 0f;
                moveY = 0f;
                speedChange = 0f;
            } else {
                speedChange = gravity * state.controller.getGravityScale() * FIXED_TIMESTEP;
                moveY = state.verticalSpeed + 0.5f * speedChange;
            }
            backend.getCharacterPosition(state.handle, tmpPosition);
            float x = tmpPosition.x;
            float y = tmpPosition.y;
            float z = tmpPosition.z;
            backend.moveCharacter(state.handle, tmpVelocity.set(state.desiredX, moveY, state.desiredZ), FIXED_TIMESTEP);
            backend.getCharacterPosition(state.handle, tmpPosition);
            state.grounded = backend.isCharacterGrounded(state.handle);
            state.velocity.set(tmpPosition.x - x, tmpPosition.y - y, tmpPosition.z - z).scl(1f / FIXED_TIMESTEP);
            if (moveY > 0f && tmpPosition.y - y < CEILING_HIT_FRACTION * moveY * FIXED_TIMESTEP) {
                state.verticalSpeed = 0f; // the head hit something: stop rising
            } else {
                state.verticalSpeed += speedChange;
            }
        }
    }

    private void pullCharacterPoses() {
        for (CharacterState state : charactersByEntity.values()) {
            backend.getCharacterPosition(state.handle, tmpPosition);
            tmpPosition.scl(PhysicsUnits3D.UNITS_PER_METER);
            // Only the position belongs to the simulation: the capsule is always upright and the entity keeps its rotation
            setWorldPose(state.entity.getTransform(), tmpPosition, tmpRotation.set(state.entity.getTransform().getWorldRotation()));
        }
    }

    private void applyPendingForces() {
        for (Map.Entry<UUID, Vector3> entry : pendingForces.entrySet()) {
            EntityBodies bodies = bodiesByEntity.get(entry.getKey());
            if (bodies != null && bodies.solid != NO_BODY) {
                backend.addForce(bodies.solid, entry.getValue());
            }
        }
        for (Map.Entry<UUID, Vector3> entry : pendingTorques.entrySet()) {
            EntityBodies bodies = bodiesByEntity.get(entry.getKey());
            if (bodies != null && bodies.solid != NO_BODY) {
                backend.addTorque(bodies.solid, entry.getValue());
            }
        }
    }

    /** Handle of the entity's solid body; the error says why there is none. */
    private int solidBodyOf(Entity entity, String operation) {
        Objects.requireNonNull(entity, "entity cannot be null");
        if (disposed) {
            throw new IllegalStateException(operation + ": the 3D physics of this scene has stopped");
        }
        if (charactersByEntity.containsKey(entity.getId())) {
            throw new IllegalStateException(operation + ": " + entity.getName() + " is a character (it has a Character Controller 3D), "
                + "not a rigid body. Move it with moveCharacter3D and jumpCharacter3D.");
        }
        EntityBodies bodies = bodiesByEntity.get(entity.getId());
        if (bodies == null) {
            throw new IllegalStateException(operation + ": " + entity.getName() + " has no physics body. It needs an enabled 3D "
                + "collider and must be active; a body is created at the start of the next physics update, so an entity created "
                + "this frame has none yet.");
        }
        if (bodies.solid == NO_BODY) {
            throw new IllegalStateException(operation + ": " + entity.getName() + " has only trigger colliders, which are not "
                + "simulated bodies. Add a solid collider.");
        }
        return bodies.solid;
    }

    private int movingBodyOf(Entity entity, String operation) {
        int handle = solidBodyOf(entity, operation);
        RigidBody3DComponent.MotionType motion = bodiesByEntity.get(entity.getId()).motionType;
        if (motion == RigidBody3DComponent.MotionType.STATIC) {
            throw new IllegalStateException(operation + ": " + entity.getName() + " is static and never moves. Give it a Rigid Body "
                + "3D (dynamic or kinematic).");
        }
        return handle;
    }

    private int dynamicBodyOf(Entity entity, String operation) {
        int handle = solidBodyOf(entity, operation);
        RigidBody3DComponent.MotionType motion = bodiesByEntity.get(entity.getId()).motionType;
        if (motion != RigidBody3DComponent.MotionType.DYNAMIC) {
            throw new IllegalStateException(operation + ": " + entity.getName() + " is " + motion + ", and only a dynamic body "
                + "is moved by forces and impulses. Set its Rigid Body 3D motion type to DYNAMIC, or move its transform.");
        }
        return handle;
    }

    private static Vector3 finiteScaled(Vector3 vector, String name, float factor) {
        Objects.requireNonNull(vector, name + " cannot be null");
        requireFinite(vector, name);
        return new Vector3(vector).scl(factor);
    }

    private static void requireFinite(Vector3 vector, String name) {
        if (!Float.isFinite(vector.x) || !Float.isFinite(vector.y) || !Float.isFinite(vector.z)) {
            throw new IllegalArgumentException(name + " must be finite: " + vector);
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        scene.detachPhysics3D(this);
        for (JointState state : new ArrayList<>(jointsByEntity.values())) {
            removeJoint(state);
        }
        for (EntityBodies bodies : new ArrayList<>(bodiesByEntity.values())) {
            removeBodies(bodies);
        }
        for (CharacterState state : new ArrayList<>(charactersByEntity.values())) {
            removeCharacter(state);
        }
        listeners.clear();
        backend.dispose();
    }

    // --- Body lifecycle ---

    private void syncBodies() {
        Set<UUID> wanted = new HashSet<>();
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy()) {
                continue;
            }
            CharacterController3DComponent controller = controllerOf(entity);
            if (controller != null) {
                wanted.add(entity.getId());
                if (!charactersByEntity.containsKey(entity.getId())) {
                    createCharacter(entity, controller);
                }
                continue;
            }
            if (colliders(entity).isEmpty()) {
                continue;
            }
            wanted.add(entity.getId());
            if (!bodiesByEntity.containsKey(entity.getId())) {
                createBodies(entity);
            }
        }
        // Joints go before the bodies they join
        for (JointState state : new ArrayList<>(jointsByEntity.values())) {
            if (isStale(state, wanted)) {
                removeJoint(state);
            }
        }
        for (EntityBodies bodies : new ArrayList<>(bodiesByEntity.values())) {
            if (!wanted.contains(bodies.entity.getId()) || bodies.entity.getScene() != scene) {
                removeBodies(bodies);
            }
        }
        for (CharacterState state : new ArrayList<>(charactersByEntity.values())) {
            if (!wanted.contains(state.entity.getId()) || state.entity.getScene() != scene) {
                removeCharacter(state);
            }
        }
        for (Entity entity : scene.getAllEntities()) {
            Joint3DComponent joint = entity.isActiveInHierarchy() ? entity.getComponent(Joint3DComponent.class) : null;
            if (joint != null && joint.isEnabled() && !jointsByEntity.containsKey(entity.getId())) {
                createJoint(entity, joint);
            }
        }
    }

    /** Whether a joint must go: its entity, its component or one of its bodies is gone, inactive or replaced. */
    private boolean isStale(JointState state, Set<UUID> wanted) {
        Joint3DComponent component = state.entity.getComponent(Joint3DComponent.class);
        if (component == null || !component.isEnabled() || !wanted.contains(state.entity.getId()) || state.entity.getScene() != scene
            || solidHandleOf(state.entity) != state.bodyA) {
            return true;
        }
        return state.connected != null && (!wanted.contains(state.connected.getId()) || state.connected.getScene() != scene
            || solidHandleOf(state.connected) != state.bodyB);
    }

    private int solidHandleOf(Entity entity) {
        EntityBodies bodies = bodiesByEntity.get(entity.getId());
        return bodies != null ? bodies.solid : NO_BODY;
    }

    private void removeJoint(JointState state) {
        backend.removeJoint(state.handle);
        jointsByEntity.remove(state.entity.getId());
    }

    /**
     * Creates the joint of {@code entity} once its body and the connected entity's body exist. Waits (without error) while the
     * connected entity is inactive; every other problem is an error naming the entity, as a joint that silently does not exist
     * would look like a bug in the physics.
     */
    private void createJoint(Entity entity, Joint3DComponent joint) {
        int bodyA = solidBodyForJoint(entity, "has the joint");
        Entity connected = null;
        int bodyB = JointDescriptor3D.WORLD;
        if (!joint.getConnectedName().isEmpty()) {
            connected = scene.findEntityByName(joint.getConnectedName());
            if (connected == null) {
                throw new IllegalStateException(entity.getName() + " has a Joint 3D connected to \"" + joint.getConnectedName()
                    + "\", but the scene has no entity with that name.");
            }
            if (connected == entity) {
                throw new IllegalStateException(entity.getName() + " has a Joint 3D connected to itself.");
            }
            if (!connected.isActiveInHierarchy()) {
                return; // the joint appears when the connected entity does
            }
            bodyB = solidBodyForJoint(connected, "is joined by " + entity.getName());
        }
        RigidBody3DComponent.MotionType motionA = bodiesByEntity.get(entity.getId()).motionType;
        RigidBody3DComponent.MotionType motionB = connected != null ? bodiesByEntity.get(connected.getId()).motionType : null;
        if (motionA != RigidBody3DComponent.MotionType.DYNAMIC && motionB != RigidBody3DComponent.MotionType.DYNAMIC) {
            throw new IllegalStateException(entity.getName() + " has a Joint 3D, but " + (connected != null ? "neither it nor "
                + connected.getName() + " is" : "it is not") + " a dynamic body, so nothing could move. Give a Rigid Body 3D with "
                + "motion type DYNAMIC to one of them.");
        }
        Joint3DComponent.Type type = joint.getType();
        boolean hasMotor = type == Joint3DComponent.Type.HINGE || type == Joint3DComponent.Type.SLIDER;
        if (!hasMotor && joint.getMotorMode() != Joint3DComponent.MotorMode.OFF) {
            throw new IllegalStateException(entity.getName() + " has a " + type + " joint with a motor, which only a HINGE or SLIDER has. "
                + "Set the motor mode to OFF.");
        }
        checkLimits(entity, joint);

        Transform transform = entity.getTransform();
        Vector3 anchor = joint.getAnchor().mul(transform.getWorldMatrix()).scl(PhysicsUnits3D.METERS_PER_UNIT);
        Vector3 anchorB;
        if (type != Joint3DComponent.Type.DISTANCE) {
            anchorB = new Vector3(anchor);
        } else if (connected != null) {
            anchorB = joint.getConnectedAnchor().mul(connected.getTransform().getWorldMatrix()).scl(PhysicsUnits3D.METERS_PER_UNIT);
        } else {
            anchorB = joint.getConnectedAnchor().scl(PhysicsUnits3D.METERS_PER_UNIT);
        }
        Vector3 axis = transform.getWorldRotation().transform(joint.getAxis()).nor();
        boolean hinge = type == Joint3DComponent.Type.HINGE;
        float lower = hinge ? (float) Math.toRadians(joint.getLowerLimit()) : PhysicsUnits3D.toMeters(joint.getLowerLimit());
        float upper = hinge ? (float) Math.toRadians(joint.getUpperLimit()) : PhysicsUnits3D.toMeters(joint.getUpperLimit());
        JointDescriptor3D descriptor = new JointDescriptor3D(type, bodyA, bodyB, anchor, anchorB, axis, joint.isCollideConnected(),
            joint.isLimitsEnabled(), lower, upper, joint.getMotorMode(), hasMotor ? motorTargetInSi(type, joint.getMotorTarget()) : 0f,
            joint.getMotorMaxForce());
        int handle = backend.addJoint(descriptor);
        jointsByEntity.put(entity.getId(), new JointState(entity, connected, handle, bodyA, bodyB, type, joint.getMotorMaxForce()));
    }

    private int solidBodyForJoint(Entity entity, String role) {
        if (charactersByEntity.containsKey(entity.getId())) {
            throw new IllegalStateException(entity.getName() + " " + role + " but is a character (it has a Character Controller 3D). "
                + "Characters cannot be joined; use a rigid body.");
        }
        EntityBodies bodies = bodiesByEntity.get(entity.getId());
        if (bodies == null || bodies.solid == NO_BODY) {
            throw new IllegalStateException(entity.getName() + " " + role + " but has no solid 3D collider. Add a Box, Sphere or "
                + "Capsule Collider 3D (not a trigger).");
        }
        return bodies.solid;
    }

    private static void checkLimits(Entity entity, Joint3DComponent joint) {
        if (!joint.isLimitsEnabled()) {
            return;
        }
        float lower = joint.getLowerLimit();
        float upper = joint.getUpperLimit();
        switch (joint.getType()) {
            case HINGE -> {
                if (lower < -Joint3DComponent.MAX_HINGE_DEGREES || upper > Joint3DComponent.MAX_HINGE_DEGREES || lower > 0f || upper < 0f) {
                    throw new IllegalStateException(entity.getName() + " has a hinge with limits " + lower + " to " + upper + " degrees. "
                        + "They must lie between -180 and 180 and include 0, the angle the hinge starts at.");
                }
            }
            case SLIDER -> {
                if (lower > 0f || upper < 0f) {
                    throw new IllegalStateException(entity.getName() + " has a slider with limits " + lower + " to " + upper + ". "
                        + "They must include 0, the position the slider starts at.");
                }
            }
            case DISTANCE -> {
                if (lower < 0f) {
                    throw new IllegalStateException(entity.getName() + " has a distance joint with a smallest distance of " + lower
                        + ". A distance cannot be negative.");
                }
            }
            default -> {
                // FIXED and BALL have no limits to check
            }
        }
    }

    private static CharacterController3DComponent controllerOf(Entity entity) {
        CharacterController3DComponent controller = entity.getComponent(CharacterController3DComponent.class);
        return controller != null && controller.isEnabled() ? controller : null;
    }

    private void createCharacter(Entity entity, CharacterController3DComponent controller) {
        RigidBody3DComponent rigidBody = entity.getComponent(RigidBody3DComponent.class);
        if ((rigidBody != null && rigidBody.isEnabled()) || !colliders(entity).isEmpty()) {
            throw new IllegalStateException(entity.getName() + " has a Character Controller 3D together with a "
                + (rigidBody != null && rigidBody.isEnabled() ? "Rigid Body 3D" : "3D collider")
                + ". The character is its own body: remove the other physics components or disable the controller.");
        }
        Vector3 position = entity.getTransform().getWorldPosition().scl(PhysicsUnits3D.METERS_PER_UNIT);
        CharacterDescriptor3D descriptor = new CharacterDescriptor3D(position, PhysicsUnits3D.toMeters(controller.getRadius()),
            PhysicsUnits3D.toMeters(controller.getHeight()) * 0.5f, (float) Math.toRadians(controller.getSlopeLimitDegrees()),
            PhysicsUnits3D.toMeters(controller.getStepHeight()), controller.getMass(), controller.getLayer(), controller.getCollisionMask());
        int handle = backend.addCharacter(descriptor);
        CharacterState state = new CharacterState(entity, handle, controller);
        state.position.set(position);
        charactersByEntity.put(entity.getId(), state);
        entityByHandle.put(handle, entity);
    }

    private void removeCharacter(CharacterState state) {
        backend.removeCharacter(state.handle);
        entityByHandle.remove(state.handle);
        charactersByEntity.remove(state.entity.getId());
    }

    private void createBodies(Entity entity) {
        RigidBody3DComponent rigidBody = entity.getComponent(RigidBody3DComponent.class);
        if (rigidBody != null && !rigidBody.isEnabled()) {
            rigidBody = null;
        }
        RigidBody3DComponent.MotionType motion = rigidBody != null ? rigidBody.getMotionType() : RigidBody3DComponent.MotionType.STATIC;
        Transform transform = entity.getTransform();
        Vector3 scale = transform.getWorldScale();
        List<ShapeDescriptor3D> solid = new ArrayList<>();
        List<ShapeDescriptor3D> triggers = new ArrayList<>();
        for (Collider3DComponent collider : colliders(entity)) {
            (collider.isTrigger() ? triggers : solid).add(describe(collider, scale, geometryFor(entity, collider, motion)));
        }

        EntityBodies bodies = new EntityBodies(entity, motion);
        Vector3 position = transform.getWorldPosition().scl(PhysicsUnits3D.METERS_PER_UNIT);
        Quaternion rotation = transform.getWorldRotation();
        if (!solid.isEmpty()) {
            bodies.solid = backend.addBody(descriptor(motion, position, rotation, solid, false, rigidBody));
            entityByHandle.put(bodies.solid, entity);
        }
        if (!triggers.isEmpty()) {
            // The sensor follows the entity: it never simulates on its own
            RigidBody3DComponent.MotionType sensorMotion = motion == RigidBody3DComponent.MotionType.STATIC
                ? RigidBody3DComponent.MotionType.STATIC : RigidBody3DComponent.MotionType.KINEMATIC;
            bodies.sensor = backend.addBody(descriptor(sensorMotion, position, rotation, triggers, true, rigidBody));
            entityByHandle.put(bodies.sensor, entity);
            sensorHandles.add(bodies.sensor);
        }
        bodiesByEntity.put(entity.getId(), bodies);
    }

    private static BodyDescriptor3D descriptor(RigidBody3DComponent.MotionType motion, Vector3 position, Quaternion rotation,
                                               List<ShapeDescriptor3D> shapes, boolean sensor, RigidBody3DComponent rb) {
        return new BodyDescriptor3D(motion, position, rotation, shapes, sensor,
            rb != null ? rb.getMass() : RigidBody3DComponent.DEFAULT_MASS,
            rb != null ? rb.getLinearDamping() : RigidBody3DComponent.DEFAULT_DAMPING,
            rb != null ? rb.getAngularDamping() : RigidBody3DComponent.DEFAULT_DAMPING,
            rb != null ? rb.getGravityFactor() : 1f,
            rb != null ? rb.getFriction() : RigidBody3DComponent.DEFAULT_FRICTION,
            rb != null ? rb.getRestitution() : 0f,
            rb != null ? rb.getLayer() : PhysicsLayers3D.DEFAULT_LAYER,
            rb != null ? rb.getCollisionMask() : PhysicsLayers3D.ALL_LAYERS);
    }

    /**
     * The mesh a mesh or hull collider is built from. A mesh collider needs a static body (Jolt has no dynamic triangle
     * meshes). Returns null for the other colliders.
     */
    private MeshGeometry3D geometryFor(Entity entity, Collider3DComponent collider, RigidBody3DComponent.MotionType motion) {
        boolean mesh = collider instanceof MeshCollider3DComponent;
        if (!mesh && !(collider instanceof ConvexHullCollider3DComponent)) {
            return null;
        }
        if (mesh && motion != RigidBody3DComponent.MotionType.STATIC) {
            throw new IllegalStateException(entity.getName() + " has a Mesh Collider 3D on a " + motion + " body. Triangle meshes only work "
                + "on static bodies: use a Convex Hull Collider 3D, or Box, Sphere or Capsule colliders, on bodies that move.");
        }
        ColliderGeometrySource source = scene.getColliderGeometry();
        if (source == null) {
            throw new IllegalStateException(entity.getName() + " has a " + (mesh ? "Mesh" : "Convex Hull") + " Collider 3D, but this scene "
                + "cannot read meshes here (no geometry source is registered).");
        }
        MeshGeometry3D geometry = source.geometryOf(entity);
        if (geometry == null || geometry.triangleCount() == 0) {
            throw new IllegalStateException(entity.getName() + " has a " + (mesh ? "Mesh" : "Convex Hull") + " Collider 3D, but its mesh has no "
                + "triangles.");
        }
        if (geometry.triangleCount() > LARGE_MESH_TRIANGLES) {
            warnings.add(entity.getName() + " has a collider mesh of " + geometry.triangleCount() + " triangles (more than "
                + LARGE_MESH_TRIANGLES + "). Physics can start slowly; consider a simpler mesh or a convex hull.");
        }
        return geometry;
    }

    /** Shape in meters with the entity's world scale applied; also used to draw colliders in the editor. */
    public static ShapeDescriptor3D describe(Collider3DComponent collider, Vector3 worldScale) {
        return describe(collider, worldScale, null);
    }

    /**
     * Like {@link #describe(Collider3DComponent, Vector3)}; a mesh or convex hull collider is built from {@code geometry}
     * (its mesh, read by a {@link ColliderGeometrySource}), the other colliders ignore it.
     *
     * @throws IllegalArgumentException when a mesh or hull collider has no geometry
     */
    public static ShapeDescriptor3D describe(Collider3DComponent collider, Vector3 worldScale, MeshGeometry3D geometry) {
        float sx = Math.abs(worldScale.x);
        float sy = Math.abs(worldScale.y);
        float sz = Math.abs(worldScale.z);
        float m = PhysicsUnits3D.METERS_PER_UNIT;
        Vector3 offset = collider.getOffset().scl(worldScale).scl(m);
        if (collider instanceof BoxCollider3DComponent box) {
            Vector3 size = box.getSize();
            return ShapeDescriptor3D.box(new Vector3(size.x * sx, size.y * sy, size.z * sz).scl(0.5f * m), offset);
        }
        if (collider instanceof SphereCollider3DComponent sphere) {
            return ShapeDescriptor3D.sphere(sphere.getRadius() * Math.max(sx, Math.max(sy, sz)) * m, offset);
        }
        if (collider instanceof CapsuleCollider3DComponent capsule) {
            return ShapeDescriptor3D.capsule(capsule.getRadius() * Math.max(sx, sz) * m, capsule.getHeight() * sy * 0.5f * m, offset);
        }
        if (collider instanceof ConvexHullCollider3DComponent || collider instanceof MeshCollider3DComponent) {
            if (geometry == null) {
                throw new IllegalArgumentException(collider.getClass().getSimpleName() + " needs the mesh it is built from");
            }
            float[] points = scaledPoints(geometry.positions(), sx * m, sy * m, sz * m);
            return collider instanceof MeshCollider3DComponent
                ? ShapeDescriptor3D.mesh(points, geometry.indices(), offset)
                : ShapeDescriptor3D.hull(ColliderPoints3D.reduce(points, MAX_HULL_POINTS), offset);
        }
        throw new IllegalArgumentException("Unsupported 3D collider: " + collider.getClass().getName());
    }

    private static float[] scaledPoints(float[] positions, float sx, float sy, float sz) {
        float[] result = new float[positions.length];
        for (int i = 0; i < positions.length; i += 3) {
            result[i] = positions[i] * sx;
            result[i + 1] = positions[i + 1] * sy;
            result[i + 2] = positions[i + 2] * sz;
        }
        return result;
    }

    private static List<Collider3DComponent> colliders(Entity entity) {
        List<Collider3DComponent> result = new ArrayList<>();
        for (Collider3DComponent collider : entity.getComponents(Collider3DComponent.class)) {
            if (collider.isEnabled()) {
                result.add(collider);
            }
        }
        return result;
    }

    private void removeBodies(EntityBodies bodies) {
        for (int handle : new int[] {bodies.solid, bodies.sensor}) {
            if (handle != NO_BODY) {
                backend.removeBody(handle);
                entityByHandle.remove(handle);
                sensorHandles.remove(handle);
            }
        }
        bodiesByEntity.remove(bodies.entity.getId());
        pendingForces.remove(bodies.entity.getId());
        pendingTorques.remove(bodies.entity.getId());
    }

    /** Things that work but may surprise, such as a very large collider mesh; oldest first. */
    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    // --- Transform sync ---

    private void pushKinematicPoses() {
        for (EntityBodies bodies : bodiesByEntity.values()) {
            boolean kinematicSolid = bodies.motionType == RigidBody3DComponent.MotionType.KINEMATIC && bodies.solid != NO_BODY;
            boolean movingSensor = bodies.motionType != RigidBody3DComponent.MotionType.STATIC && bodies.sensor != NO_BODY;
            if (!kinematicSolid && !movingSensor) {
                continue;
            }
            Transform transform = bodies.entity.getTransform();
            tmpPosition.set(transform.getWorldPosition()).scl(PhysicsUnits3D.METERS_PER_UNIT);
            tmpRotation.set(transform.getWorldRotation());
            if (kinematicSolid) {
                backend.moveKinematic(bodies.solid, tmpPosition, tmpRotation, FIXED_TIMESTEP);
            }
            if (movingSensor) {
                backend.moveKinematic(bodies.sensor, tmpPosition, tmpRotation, FIXED_TIMESTEP);
            }
        }
    }

    private void pullDynamicPoses() {
        for (EntityBodies bodies : bodiesByEntity.values()) {
            if (bodies.motionType != RigidBody3DComponent.MotionType.DYNAMIC || bodies.solid == NO_BODY) {
                continue;
            }
            backend.getBodyTransform(bodies.solid, tmpPosition, tmpRotation);
            tmpPosition.scl(PhysicsUnits3D.UNITS_PER_METER);
            setWorldPose(bodies.entity.getTransform(), tmpPosition, tmpRotation);
        }
    }

    /** Sets a transform's local values so its world position and rotation become the given ones. */
    private void setWorldPose(Transform transform, Vector3 worldPosition, Quaternion worldRotation) {
        Transform parent = transform.getParent();
        if (parent == null) {
            transform.setLocalPosition(worldPosition);
            transform.setLocalRotation(worldRotation);
            return;
        }
        tmpMatrix.set(parent.getWorldMatrix());
        if (Math.abs(tmpMatrix.det()) < 1e-12f) {
            return; // degenerate parent scale: no local pose maps to this world pose
        }
        tmpMatrix.inv();
        transform.setLocalPosition(new Vector3(worldPosition).mul(tmpMatrix));
        Quaternion local = new Quaternion(parent.getWorldRotation()).conjugate().mul(worldRotation);
        transform.setLocalRotation(local);
    }

    // --- Events ---

    private void dispatchContacts() {
        pendingEvents.clear();
        backend.drainContactEvents(pendingEvents);
        for (PhysicsBackend3D.ContactEvent event : pendingEvents) {
            Entity a = entityByHandle.get(event.bodyA());
            Entity b = entityByHandle.get(event.bodyB());
            if (a == null || b == null || a == b) {
                continue;
            }
            boolean trigger = sensorHandles.contains(event.bodyA()) || sensorHandles.contains(event.bodyB());
            ContactKind kind = trigger
                ? (event.began() ? ContactKind.TRIGGER_ENTER : ContactKind.TRIGGER_EXIT)
                : (event.began() ? ContactKind.COLLISION_ENTER : ContactKind.COLLISION_EXIT);
            Contact contact = new Contact(a, b, kind);
            for (ContactListener listener : listeners) {
                listener.onContact(contact);
            }
        }
    }
}
