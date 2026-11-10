package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PhysicsSystem3DTest {

    /** Records what the system asks for; tests move bodies and raise contacts by hand. */
    static final class FakeBackend implements PhysicsBackend3D {
        final Map<Integer, BodyDescriptor3D> bodies = new LinkedHashMap<>();
        final Map<Integer, Vector3> positions = new LinkedHashMap<>();
        final Map<Integer, Quaternion> rotations = new LinkedHashMap<>();
        final List<Integer> kinematicMoves = new ArrayList<>();
        final List<ContactEvent> events = new ArrayList<>();
        Vector3 gravity;
        RayHit nextHit;
        int steps;
        int nextHandle = 7;
        boolean disposed;

        @Override
        public void setGravity(Vector3 gravity) {
            this.gravity = new Vector3(gravity);
        }

        @Override
        public int addBody(BodyDescriptor3D body) {
            int handle = nextHandle++;
            bodies.put(handle, body);
            positions.put(handle, new Vector3(body.position()));
            rotations.put(handle, new Quaternion(body.rotation()));
            return handle;
        }

        @Override
        public void removeBody(int handle) {
            assertNotNull(bodies.remove(handle), "Removed a body that does not exist");
        }

        @Override
        public void moveKinematic(int handle, Vector3 position, Quaternion rotation, float deltaSeconds) {
            kinematicMoves.add(handle);
            positions.get(handle).set(position);
        }

        @Override
        public void getBodyTransform(int handle, Vector3 outPosition, Quaternion outRotation) {
            outPosition.set(positions.get(handle));
            outRotation.set(rotations.get(handle));
        }

        @Override
        public void step(float deltaSeconds) {
            assertEquals(PhysicsSystem3D.FIXED_TIMESTEP, deltaSeconds);
            steps++;
        }

        @Override
        public void drainContactEvents(List<ContactEvent> out) {
            out.addAll(events);
            events.clear();
        }

        @Override
        public RayHit raycast(Vector3 origin, Vector3 direction, float maxDistance, int layerMask) {
            return nextHit;
        }

        /** Every call of the force and velocity API, as "name handle x y z", in call order. */
        final List<String> calls = new ArrayList<>();
        final Map<Integer, Vector3> linearVelocities = new LinkedHashMap<>();
        final Map<Integer, Vector3> angularVelocities = new LinkedHashMap<>();
        final List<Integer> overlaps = new ArrayList<>();
        Vector3 lastOverlapCenter;
        float lastOverlapRadius;

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
            linearVelocities.put(handle, new Vector3(velocity));
        }

        @Override
        public void getLinearVelocity(int handle, Vector3 outVelocity) {
            outVelocity.set(linearVelocities.getOrDefault(handle, Vector3.Zero));
        }

        @Override
        public void setAngularVelocity(int handle, Vector3 velocity) {
            angularVelocities.put(handle, new Vector3(velocity));
        }

        @Override
        public void getAngularVelocity(int handle, Vector3 outVelocity) {
            outVelocity.set(angularVelocities.getOrDefault(handle, Vector3.Zero));
        }

        @Override
        public void overlapSphere(Vector3 center, float radius, int layerMask, List<Integer> outBodies) {
            lastOverlapCenter = new Vector3(center);
            lastOverlapRadius = radius;
            outBodies.addAll(overlaps);
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
            disposed = true;
        }

        int solidHandle() {
            return bodies.entrySet().stream().filter(e -> !e.getValue().sensor()).findFirst().orElseThrow().getKey();
        }
    }

    private Scene scene;
    private FakeBackend backend;
    private PhysicsSystem3D physics;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Physics");
        backend = new FakeBackend();
        physics = new PhysicsSystem3D(scene, backend);
    }

    private static void assertNear(Vector3 expected, Vector3 actual, String message) {
        assertTrue(expected.epsilonEquals(actual, 1e-4f), message + ": expected " + expected + " but was " + actual);
    }

    private Entity crate(RigidBody3DComponent.MotionType motion) {
        Entity crate = scene.createEntity("Crate");
        crate.getTransform().setLocalPosition(200f, 300f, 0f);
        crate.addComponent(new RigidBody3DComponent(motion));
        crate.addComponent(new BoxCollider3DComponent());
        return crate;
    }

    @Test
    void shouldStepAtFixedRateAndCapLongFrames() {
        assertEquals(new Vector3(0f, -9.81f, 0f), backend.gravity);
        physics.update(0.051f);
        assertEquals(3, backend.steps, "Just over 0.05 s is three 1/60 s steps");
        physics.update(5f);
        assertEquals(3 + PhysicsSystem3D.MAX_SUB_STEPS, backend.steps, "Long frames are capped");
        physics.update(-1f);
        assertEquals(8, backend.steps);
    }

    @Test
    void shouldDescribeBodiesInMetersWithScaleApplied() {
        Entity crate = crate(RigidBody3DComponent.MotionType.DYNAMIC);
        crate.getTransform().setLocalScale(2f, 1f, 1f);
        crate.getComponent(RigidBody3DComponent.class).setMass(4f);
        crate.getComponent(RigidBody3DComponent.class).setLayer(3);
        physics.update(0f);

        BodyDescriptor3D body = backend.bodies.values().iterator().next();
        assertEquals(RigidBody3DComponent.MotionType.DYNAMIC, body.motionType());
        assertNear(new Vector3(2f, 3f, 0f), body.position(), "300 scene units are 3 m");
        assertNear(new Vector3(1f, 0.5f, 0.5f), body.shapes().get(0).halfExtents(), "100-unit box scaled 2x on X");
        assertEquals(4f, body.mass());
        assertEquals(3, body.layer());
        assertFalse(body.sensor());
    }

    @Test
    void shouldMakeCollidersWithoutRigidBodyStaticAndSplitTriggersIntoASensorBody() {
        Entity wall = scene.createEntity("Wall");
        wall.addComponent(new BoxCollider3DComponent());
        Entity zone = crate(RigidBody3DComponent.MotionType.DYNAMIC);
        SphereCollider3DComponent trigger = new SphereCollider3DComponent(150f);
        trigger.setTrigger(true);
        zone.addComponent(trigger);
        Entity bodyOnly = scene.createEntity("No collider");
        bodyOnly.addComponent(new RigidBody3DComponent());
        physics.update(0f);

        assertEquals(3, backend.bodies.size(), "Wall, zone solid, zone sensor; nothing for a body without colliders");
        assertFalse(physics.hasBody(bodyOnly));
        List<BodyDescriptor3D> all = List.copyOf(backend.bodies.values());
        assertEquals(RigidBody3DComponent.MotionType.STATIC, all.get(0).motionType());
        BodyDescriptor3D sensor = all.stream().filter(BodyDescriptor3D::sensor).findFirst().orElseThrow();
        assertEquals(RigidBody3DComponent.MotionType.KINEMATIC, sensor.motionType(), "A moving entity's sensor follows it");
        assertEquals(1.5f, sensor.shapes().get(0).radius(), 0.0001f);
    }

    @Test
    void shouldWriteDynamicPosesBackIncludingUnderAParent() {
        Entity parent = scene.createEntity("Parent");
        parent.getTransform().setLocalPosition(100f, 0f, 0f);
        Entity ball = crate(RigidBody3DComponent.MotionType.DYNAMIC);
        ball.getTransform().setParent(parent.getTransform(), false);
        physics.update(0f);

        int handle = backend.solidHandle();
        backend.positions.get(handle).set(5f, 1f, 0f);
        backend.rotations.get(handle).setEulerAngles(90f, 0f, 0f);
        physics.update(PhysicsSystem3D.FIXED_TIMESTEP);

        Vector3 world = ball.getTransform().getWorldPosition();
        assertEquals(500f, world.x, 0.01f);
        assertEquals(100f, world.y, 0.01f);
        assertEquals(400f, ball.getTransform().getLocalPosition().x, 0.01f, "Local position is relative to the parent");
        assertEquals(90f, ball.getTransform().getWorldRotation().getYaw(), 0.01f);
    }

    @Test
    void shouldDriveKinematicBodiesFromTheirTransformEveryStep() {
        Entity platform = crate(RigidBody3DComponent.MotionType.KINEMATIC);
        physics.update(0f);
        platform.getTransform().setLocalPosition(0f, 50f, 0f);
        physics.update(2 * PhysicsSystem3D.FIXED_TIMESTEP + 0.001f);

        assertEquals(2, backend.kinematicMoves.size());
        assertNear(new Vector3(0f, 0.5f, 0f), backend.positions.get(backend.solidHandle()), "50 units up");
        assertEquals(new Vector3(0f, 50f, 0f), platform.getTransform().getLocalPosition(), "Kinematic poses are not written back");
    }

    @Test
    void shouldReportCollisionsAndTriggersAsEntityContacts() {
        Entity a = crate(RigidBody3DComponent.MotionType.DYNAMIC);
        Entity b = scene.createEntity("Zone");
        SphereCollider3DComponent trigger = new SphereCollider3DComponent();
        trigger.setTrigger(true);
        b.addComponent(trigger);
        physics.update(0f);
        List<Integer> handles = List.copyOf(backend.bodies.keySet());

        List<PhysicsSystem3D.Contact> seen = new ArrayList<>();
        physics.addContactListener(seen::add);
        backend.events.add(new PhysicsBackend3D.ContactEvent(handles.get(0), handles.get(1), true));
        backend.events.add(new PhysicsBackend3D.ContactEvent(handles.get(0), handles.get(1), false));
        physics.update(0f);

        assertEquals(2, seen.size());
        assertEquals(PhysicsSystem3D.ContactKind.TRIGGER_ENTER, seen.get(0).kind());
        assertEquals(a, seen.get(0).entityA());
        assertEquals(b, seen.get(0).entityB());
        assertEquals(PhysicsSystem3D.ContactKind.TRIGGER_EXIT, seen.get(1).kind());
    }

    @Test
    void shouldLetListenersDestroyEntitiesAndRemoveTheirBodiesBeforeTheNextStep() {
        Entity a = crate(RigidBody3DComponent.MotionType.DYNAMIC);
        Entity b = crate(RigidBody3DComponent.MotionType.STATIC);
        physics.update(0f);
        List<Integer> handles = List.copyOf(backend.bodies.keySet());
        physics.addContactListener(contact -> scene.removeEntity(contact.entityA()));
        backend.events.add(new PhysicsBackend3D.ContactEvent(handles.get(0), handles.get(1), true));
        physics.update(0f);
        assertNull(scene.findEntityById(a.getId()));

        physics.update(PhysicsSystem3D.FIXED_TIMESTEP);
        assertFalse(physics.hasBody(a));
        assertTrue(physics.hasBody(b));
        assertEquals(1, backend.bodies.size());

        b.setActive(false);
        physics.update(0f);
        assertTrue(backend.bodies.isEmpty(), "Deactivated entities leave the simulation");
    }

    @Test
    void shouldRaycastInSceneUnits() {
        Entity crate = crate(RigidBody3DComponent.MotionType.STATIC);
        physics.update(0f);
        assertTrue(physics.raycast(new Vector3(), Vector3.Y, 1000f, PhysicsLayers3D.ALL_LAYERS).isEmpty());

        backend.nextHit = new PhysicsBackend3D.RayHit(backend.solidHandle(), new Vector3(2f, 2.5f, 0f), Vector3.Y, 2.5f);
        PhysicsSystem3D.RaycastHit hit = physics.raycast(new Vector3(200f, 0f, 0f), Vector3.Y, 1000f, PhysicsLayers3D.ALL_LAYERS).orElseThrow();
        assertEquals(crate, hit.entity());
        assertEquals(250f, hit.distance(), 0.001f);
        assertEquals(new Vector3(200f, 250f, 0f), hit.point());
        assertThrows(IllegalArgumentException.class, () -> physics.raycast(new Vector3(), new Vector3(), 10f, 1));
    }

    @Test
    void shouldFilterLayersBothWaysAndDisposeTheBackend() {
        int onlyLayer2 = 1 << 2;
        assertTrue(PhysicsLayers3D.collide(1, PhysicsLayers3D.ALL_LAYERS, 2, PhysicsLayers3D.ALL_LAYERS));
        assertFalse(PhysicsLayers3D.collide(1, onlyLayer2, 2, onlyLayer2), "Layer 2's mask does not include layer 1");
        assertTrue(PhysicsLayers3D.collide(2, onlyLayer2, 2, onlyLayer2));

        crate(RigidBody3DComponent.MotionType.DYNAMIC);
        physics.update(0f);
        physics.dispose();
        assertTrue(backend.bodies.isEmpty());
        assertTrue(backend.disposed);
        physics.update(1f);
        assertEquals(0, backend.steps);
    }
}
