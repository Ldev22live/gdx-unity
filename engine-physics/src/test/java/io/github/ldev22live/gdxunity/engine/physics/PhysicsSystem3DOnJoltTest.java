package io.github.ldev22live.gdxunity.engine.physics;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D.Contact;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D.ContactKind;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent.MotionType;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PhysicsSystem3D} running on the real Jolt backend, in scene units (100 per meter): entities fall, land, report
 * contacts and triggers, and are found by rays, as a game and editor Play see them.
 */
class PhysicsSystem3DOnJoltTest {
    private static final float FRAME = 1f / 60f;
    private static final int THREE_SECONDS = 180;
    /** A 20 m wide, 1 m thick slab whose top face is at y = 0. */
    private static final Vector3 FLOOR_SIZE = new Vector3(2000f, 100f, 2000f);
    private static final float BALL_RADIUS = 50f;
    private static final float TOLERANCE = 5f;

    private Scene scene;
    private PhysicsSystem3D physics;
    private final List<Contact> contacts = new ArrayList<>();

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Jolt");
        physics = new PhysicsSystem3D(scene, new JoltPhysicsBackend3D());
        physics.addContactListener(contacts::add);
    }

    @AfterEach
    void tearDown() {
        physics.dispose();
        scene.dispose();
    }

    private Entity floor() {
        Entity floor = scene.createEntity("Floor");
        floor.getTransform().setLocalPosition(0f, -50f, 0f);
        floor.addComponent(new BoxCollider3DComponent(FLOOR_SIZE));
        return floor;
    }

    private Entity ball(float height) {
        Entity ball = scene.createEntity("Ball");
        ball.getTransform().setLocalPosition(0f, height, 0f);
        ball.addComponent(new RigidBody3DComponent(MotionType.DYNAMIC));
        ball.addComponent(new SphereCollider3DComponent(BALL_RADIUS));
        return ball;
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) {
            physics.update(FRAME);
        }
    }

    @Test
    void shouldLetAnEntityFallAndLandOnAnotherInSceneUnits() {
        floor();
        Entity ball = ball(400f);

        run(THREE_SECONDS);

        assertEquals(BALL_RADIUS, ball.getTransform().getWorldPosition().y, TOLERANCE, "resting on the floor's top face");
    }

    @Test
    void shouldReportOneCollisionEnterBetweenTheEntities() {
        Entity floor = floor();
        Entity ball = ball(200f);

        run(THREE_SECONDS);

        List<Contact> enters = contacts.stream().filter(c -> c.kind() == ContactKind.COLLISION_ENTER).toList();
        assertEquals(1, enters.size(), "contacts: " + contacts);
        assertTrue(List.of(floor, ball).containsAll(List.of(enters.get(0).entityA(), enters.get(0).entityB())));
        assertTrue(contacts.stream().noneMatch(c -> c.kind() == ContactKind.COLLISION_EXIT), "it stays on the floor: " + contacts);
    }

    @Test
    void shouldReportTriggerEnterAndExitAsAnEntityFallsThroughAZone() {
        Entity zone = scene.createEntity("Zone");
        zone.getTransform().setLocalPosition(0f, 500f, 0f);
        BoxCollider3DComponent trigger = new BoxCollider3DComponent(new Vector3(400f, 100f, 400f));
        trigger.setTrigger(true);
        zone.addComponent(trigger);
        Entity ball = ball(800f);

        run(THREE_SECONDS);

        assertEquals(List.of(ContactKind.TRIGGER_ENTER, ContactKind.TRIGGER_EXIT), contacts.stream().map(Contact::kind).toList(),
            "contacts: " + contacts);
        assertTrue(ball.getTransform().getWorldPosition().y < 0f, "a trigger does not stop the ball");
    }

    @Test
    void shouldFindTheClosestEntityOnARayInSceneUnits() {
        floor();
        Entity ball = ball(BALL_RADIUS);
        run(30);

        Optional<PhysicsSystem3D.RaycastHit> hit = physics.raycast(new Vector3(0f, 1000f, 0f), new Vector3(0f, -1f, 0f), 5000f, 0xFFFF);

        assertTrue(hit.isPresent());
        assertEquals(ball, hit.get().entity());
        assertEquals(1000f - 2 * BALL_RADIUS, hit.get().distance(), TOLERANCE, "the top of the ball");
        assertEquals(1f, hit.get().normal().y, 0.05f);
    }

    @Test
    void shouldCarryAKinematicEntitysMovesIntoTheSimulation() {
        Entity platform = scene.createEntity("Platform");
        platform.getTransform().setLocalPosition(0f, -50f, 0f);
        platform.addComponent(new RigidBody3DComponent(MotionType.KINEMATIC));
        platform.addComponent(new BoxCollider3DComponent(FLOOR_SIZE));
        Entity ball = ball(BALL_RADIUS);
        run(60);
        float resting = ball.getTransform().getWorldPosition().y;

        for (int i = 0; i < 60; i++) {
            platform.getTransform().setLocalPosition(0f, -50f + (i + 1) * 2f, 0f);
            physics.update(FRAME);
        }

        assertEquals(resting + 120f, ball.getTransform().getWorldPosition().y, 15f, "the platform rose 120 units and carried the ball");
    }

    @Test
    void shouldDropTheBodyOfADestroyedEntityWithoutCrashingOrReportingItAgain() {
        floor();
        Entity ball = ball(200f);
        run(60);

        scene.removeEntity(ball);
        run(60);

        assertFalse(physics.hasBody(ball));
        // Events are delivered to entities that still have a body; a removed entity gets no exit (PhysicsSystem3D)
        assertEquals(1, contacts.size(), "only the enter before the removal: " + contacts);
        assertEquals(ContactKind.COLLISION_ENTER, contacts.get(0).kind());
    }

    @Test
    void shouldTellWhichScenesNeedPhysicsSoOthersNeverLoadTheLibrary() {
        Scene empty = new Scene("Empty");
        empty.createEntity("Just an entity");
        assertFalse(PhysicsSystem3D.isUsedBy(empty));
        empty.dispose();

        Entity floor = floor();
        assertTrue(PhysicsSystem3D.isUsedBy(scene));
        floor.setActive(false);
        assertFalse(PhysicsSystem3D.isUsedBy(scene), "an inactive entity has no body");
    }

    // --- Script access (TD-048) ---

    /** Writes down the callbacks it gets. */
    private static final class Recorder extends ScriptComponent {
        final List<String> calls = new ArrayList<>();

        @Override
        public void onCollisionEnter3D(Entity other) {
            calls.add("collisionEnter:" + other.getName());
        }

        @Override
        public void onTriggerEnter3D(Entity other) {
            calls.add("triggerEnter:" + other.getName());
        }

        @Override
        public void onTriggerExit3D(Entity other) {
            calls.add("triggerExit:" + other.getName());
        }
    }

    @Test
    void shouldCallScriptsOfBothEntitiesWhenABallLandsOnTheFloor() {
        Entity floor = floor();
        Entity ball = ball(200f);
        Recorder ballScript = new Recorder();
        Recorder floorScript = new Recorder();
        ball.addComponent(ballScript);
        floor.addComponent(floorScript);

        run(THREE_SECONDS);

        assertEquals(List.of("collisionEnter:Floor"), ballScript.calls);
        assertEquals(List.of("collisionEnter:Ball"), floorScript.calls);
    }

    @Test
    void shouldCallTriggerCallbacksWhenAScriptedBallPassesThroughAZone() {
        Entity zone = scene.createEntity("Zone");
        zone.getTransform().setLocalPosition(0f, 200f, 0f);
        SphereCollider3DComponent sensor = new SphereCollider3DComponent(80f);
        sensor.setTrigger(true);
        zone.addComponent(sensor);
        Recorder zoneScript = new Recorder();
        zone.addComponent(zoneScript);
        Entity ball = ball(500f);

        run(THREE_SECONDS);

        assertEquals(List.of("triggerEnter:Ball", "triggerExit:Ball"), zoneScript.calls, "the ball falls through the zone and leaves it");
        assertTrue(ball.getTransform().getWorldPosition().y < 0f, "no floor: it kept falling");
    }

    /** A ball with no gravity and a 1 kg mass pushed by 100 units/s2 (1 N) for one simulated second, at {@code fps}. */
    private float speedAfterAPushOfOneSecond(int fps) {
        Scene other = new Scene("Push " + fps);
        PhysicsSystem3D system = new PhysicsSystem3D(other, new JoltPhysicsBackend3D());
        try {
            Entity ball = other.createEntity("Ball");
            RigidBody3DComponent body = new RigidBody3DComponent(MotionType.DYNAMIC);
            body.setGravityFactor(0f);
            body.setLinearDamping(0f);
            ball.addComponent(body);
            ball.addComponent(new SphereCollider3DComponent(BALL_RADIUS));
            system.update(0f);
            for (int frame = 0; frame < fps; frame++) {
                system.addForce(ball, new Vector3(100f, 0f, 0f));
                system.update(1f / fps);
            }
            return system.getLinearVelocity(ball).x;
        } finally {
            system.dispose();
            other.dispose();
        }
    }

    @Test
    void shouldReachTheSameSpeedFromAOneSecondPushAtThirtyAndSixtyFramesPerSecond() {
        float slow = speedAfterAPushOfOneSecond(30);
        float fast = speedAfterAPushOfOneSecond(60);

        assertEquals(100f, fast, 3f, "1 N on 1 kg for 1 s is 1 m/s, which is 100 scene units/s");
        assertEquals(fast, slow, 3f, "same speed whatever the frame rate");
    }

    @Test
    void shouldKickABallWithAnImpulseAndReadItsVelocityBack() {
        Entity ball = ball(500f);
        ball.getComponent(RigidBody3DComponent.class).setGravityFactor(0f);
        physics.update(0f);

        physics.addImpulse(ball, new Vector3(0f, 0f, 250f));

        assertEquals(250f, physics.getLinearVelocity(ball).z, 0.5f, "250 kg unit/s on 1 kg is 250 units/s");
        run(60);
        assertTrue(ball.getTransform().getWorldPosition().z > 200f, "it moved about 2.4 m along Z in a second");
    }

    @Test
    void shouldFindEntitiesInASphereAndLetAScriptRaycast() {
        Entity floor = floor();
        Entity ball = ball(300f);
        Recorder script = new Recorder();
        ball.addComponent(script);
        physics.update(0f);

        List<Entity> hits = physics.overlapSphere(new Vector3(0f, 300f, 0f), 20f, 0xFFFF);
        PhysicsSystem3D.RaycastHit ray = script.raycast3D(new Vector3(0f, 200f, 0f), new Vector3(0f, -1f, 0f), 1000f);

        assertEquals(List.of(ball), hits);
        assertEquals(floor, ray.entity());
        assertEquals(200f, ray.distance(), TOLERANCE, "from y = 200, just under the ball, down to the floor's top face at 0");
    }

    @Test
    void shouldNotLetAForceMoveAStaticFloorOnTheRealBackend() {
        Entity floor = floor();
        physics.update(0f);

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> physics.addForce(floor, new Vector3(0f, 1000f, 0f)));

        assertTrue(error.getMessage().contains("Floor"), error.getMessage());
    }
}
