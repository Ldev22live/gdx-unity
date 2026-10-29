package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    private final Scene scene;
    private final PhysicsBackend3D backend;
    private final Map<UUID, EntityBodies> bodiesByEntity = new LinkedHashMap<>();
    private final Map<Integer, Entity> entityByHandle = new HashMap<>();
    private final Set<Integer> sensorHandles = new HashSet<>();
    private final List<ContactListener> listeners = new CopyOnWriteArrayList<>();
    private final List<PhysicsBackend3D.ContactEvent> pendingEvents = new ArrayList<>();
    private final Vector3 tmpPosition = new Vector3();
    private final Quaternion tmpRotation = new Quaternion();
    private final Matrix4 tmpMatrix = new Matrix4();
    private float accumulator;
    private int stepCount;
    private boolean disposed;

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

    public PhysicsSystem3D(Scene scene, PhysicsBackend3D backend) {
        this.scene = Objects.requireNonNull(scene, "scene cannot be null");
        this.backend = Objects.requireNonNull(backend, "backend cannot be null");
        backend.setGravity(DEFAULT_GRAVITY);
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

    /** Whether {@code entity} currently has a body. */
    public boolean hasBody(Entity entity) {
        return entity != null && bodiesByEntity.containsKey(entity.getId());
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
            backend.step(FIXED_TIMESTEP);
            accumulator -= FIXED_TIMESTEP;
            steps++;
            stepCount++;
        }
        if (accumulator > FIXED_TIMESTEP) {
            // Too far behind: drop the backlog rather than spiral
            accumulator = 0f;
        }
        pullDynamicPoses();
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

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (EntityBodies bodies : new ArrayList<>(bodiesByEntity.values())) {
            removeBodies(bodies);
        }
        listeners.clear();
        backend.dispose();
    }

    // --- Body lifecycle ---

    private void syncBodies() {
        Set<UUID> wanted = new HashSet<>();
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy() || colliders(entity).isEmpty()) {
                continue;
            }
            wanted.add(entity.getId());
            if (!bodiesByEntity.containsKey(entity.getId())) {
                createBodies(entity);
            }
        }
        for (EntityBodies bodies : new ArrayList<>(bodiesByEntity.values())) {
            if (!wanted.contains(bodies.entity.getId()) || bodies.entity.getScene() != scene) {
                removeBodies(bodies);
            }
        }
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
            (collider.isTrigger() ? triggers : solid).add(describe(collider, scale));
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

    /** Shape in meters with the entity's world scale applied; also used to draw colliders in the editor. */
    public static ShapeDescriptor3D describe(Collider3DComponent collider, Vector3 worldScale) {
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
        throw new IllegalArgumentException("Unsupported 3D collider: " + collider.getClass().getName());
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
