package io.github.ldev22live.gdxunity.engine.physics;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BodyDescriptor3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsBackend3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsBackend3D.ContactEvent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent.MotionType;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ShapeDescriptor3D;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Jolt backend against the real library (TD-028): the compatibility spike of the evaluation (a sphere dropped on a
 * box comes to rest on it) and what {@code PhysicsSystem3D} relies on: contacts, sensors, layer masks, raycasts,
 * kinematic bodies, shapes and disposal. These tests load the native library, so they need a supported platform.
 */
class JoltPhysicsBackend3DTest {
    private static final float STEP = 1f / 60f;
    /** Two seconds: long enough for a 3 m drop to land and settle. */
    private static final int SETTLE_STEPS = 120;
    private static final float TOLERANCE = 0.05f;
    private static final Vector3 ORIGIN = new Vector3();
    private static final Quaternion NO_ROTATION = new Quaternion();

    private JoltPhysicsBackend3D backend;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        backend = new JoltPhysicsBackend3D();
        backend.setGravity(new Vector3(0f, -9.81f, 0f));
    }

    @AfterEach
    void tearDown() {
        backend.dispose();
    }

    private static BodyDescriptor3D body(MotionType motion, Vector3 position, ShapeDescriptor3D shape, boolean sensor, int layer, int mask) {
        return new BodyDescriptor3D(motion, position, NO_ROTATION, List.of(shape), sensor, 1f, 0.05f, 0.05f, 1f, 0.5f, 0f, layer, mask);
    }

    private static BodyDescriptor3D body(MotionType motion, Vector3 position, ShapeDescriptor3D shape) {
        return body(motion, position, shape, false, PhysicsLayers3D.DEFAULT_LAYER, PhysicsLayers3D.ALL_LAYERS);
    }

    /** A wide floor whose top face is at y = 0. */
    private int floor() {
        return backend.addBody(body(MotionType.STATIC, new Vector3(0f, -0.5f, 0f),
            ShapeDescriptor3D.box(new Vector3(20f, 0.5f, 20f), ORIGIN)));
    }

    private int ball(float height) {
        return backend.addBody(body(MotionType.DYNAMIC, new Vector3(0f, height, 0f), ShapeDescriptor3D.sphere(0.5f, ORIGIN)));
    }

    private void run(int steps) {
        for (int i = 0; i < steps; i++) {
            backend.step(STEP);
        }
    }

    private float heightOf(int handle) {
        Vector3 position = new Vector3();
        backend.getBodyTransform(handle, position, new Quaternion());
        return position.y;
    }

    private List<ContactEvent> events() {
        List<ContactEvent> out = new ArrayList<>();
        backend.drainContactEvents(out);
        return out;
    }

    @Test
    void shouldLetASphereDroppedOnABoxComeToRestOnIt() {
        floor();
        int ball = ball(3f);

        run(SETTLE_STEPS);

        assertEquals(0.5f, heightOf(ball), TOLERANCE, "radius above the floor's top face");
    }

    @Test
    void shouldFallUnderTheGravityItWasGiven() {
        int ball = ball(10f);
        backend.setGravity(new Vector3(0f, -2f, 0f));

        run(60);

        // y = y0 - g t^2 / 2 for one second, less a little damping
        assertEquals(9f, heightOf(ball), 0.1f);
    }

    @Test
    void shouldStandACapsuleOnItsEndAndKeepAnOffsetShapeWhereItWasPut() {
        floor();
        int capsule = backend.addBody(body(MotionType.DYNAMIC, new Vector3(0f, 3f, 0f), ShapeDescriptor3D.capsule(0.5f, 1f, ORIGIN)));
        int offset = backend.addBody(body(MotionType.DYNAMIC, new Vector3(5f, 3f, 0f),
            ShapeDescriptor3D.sphere(0.5f, new Vector3(0f, 2f, 0f))));

        run(SETTLE_STEPS);

        assertEquals(1.5f, heightOf(capsule), TOLERANCE, "half height 1 plus radius 0.5");
        assertEquals(-1.5f, heightOf(offset), 0.1f, "the sphere sits 2 m above the body's origin, so the origin rests 1.5 m below the floor line");
    }

    @Test
    void shouldHoldAStaticBodyStill() {
        int wall = floor();

        run(30);

        assertEquals(-0.5f, heightOf(wall), 1e-4f);
    }

    @Test
    void shouldReportTouchingOnceAndLeavingOnceForASolidContact() {
        int floor = floor();
        int ball = ball(1f);

        run(SETTLE_STEPS);
        List<ContactEvent> landed = events();

        assertEquals(1, landed.stream().filter(ContactEvent::began).count(), "one began event for the pair, however many sub-contacts: " + landed);
        assertEquals(List.of(true), landed.stream().map(ContactEvent::began).toList(), "still touching after settling: " + landed);
        ContactEvent began = landed.get(0);
        assertTrue((began.bodyA() == floor && began.bodyB() == ball) || (began.bodyA() == ball && began.bodyB() == floor));

        backend.removeBody(ball);
        List<ContactEvent> left = events();
        assertEquals(1, left.size(), "removing a body ends its contacts: " + left);
        assertFalse(left.get(0).began());
    }

    @Test
    void shouldReportASensorOverlapBeginAndEndWithoutBlocking() {
        int sensor = backend.addBody(body(MotionType.STATIC, new Vector3(0f, 5f, 0f),
            ShapeDescriptor3D.box(new Vector3(2f, 0.5f, 2f), ORIGIN), true, 0, PhysicsLayers3D.ALL_LAYERS));
        int ball = ball(8f);

        run(SETTLE_STEPS);
        List<ContactEvent> all = events();

        assertEquals(List.of(true, false), all.stream().map(ContactEvent::began).toList(),
            "the ball enters the zone, then leaves it, falling on: " + all);
        assertTrue(heightOf(ball) < 0f, "a sensor does not stop the ball");
        assertTrue(all.stream().allMatch(e -> e.bodyA() == Math.min(sensor, ball) && e.bodyB() == Math.max(sensor, ball)));
    }

    @Test
    void shouldLetBodiesPassWhenOneMaskExcludesTheOthersLayer() {
        int floor = backend.addBody(body(MotionType.STATIC, new Vector3(0f, -0.5f, 0f),
            ShapeDescriptor3D.box(new Vector3(20f, 0.5f, 20f), ORIGIN), false, 2, PhysicsLayers3D.ALL_LAYERS));
        // The floor accepts everything, but the ball's mask leaves out layer 2: the rule needs both to agree
        int ball = backend.addBody(body(MotionType.DYNAMIC, new Vector3(0f, 3f, 0f), ShapeDescriptor3D.sphere(0.5f, ORIGIN),
            false, 0, PhysicsLayers3D.ALL_LAYERS & ~(1 << 2)));

        run(SETTLE_STEPS);

        assertTrue(heightOf(ball) < -3f, "the ball fell through the floor " + floor);
        assertTrue(events().isEmpty(), "no contact is reported for bodies that do not collide");
    }

    @Test
    void shouldCollideWhenBothMasksAcceptTheOtherLayer() {
        backend.addBody(body(MotionType.STATIC, new Vector3(0f, -0.5f, 0f),
            ShapeDescriptor3D.box(new Vector3(20f, 0.5f, 20f), ORIGIN), false, 2, 1 << 5));
        int ball = backend.addBody(body(MotionType.DYNAMIC, new Vector3(0f, 3f, 0f), ShapeDescriptor3D.sphere(0.5f, ORIGIN),
            false, 5, 1 << 2));

        run(SETTLE_STEPS);

        assertEquals(0.5f, heightOf(ball), TOLERANCE);
    }

    @Test
    void shouldFindTheClosestSolidBodyOnARayWithItsNormal() {
        int floor = floor();
        int ball = ball(2f);

        PhysicsBackend3D.RayHit hit = backend.raycast(new Vector3(0f, 10f, 0f), new Vector3(0f, -1f, 0f), 50f, PhysicsLayers3D.ALL_LAYERS);

        assertNotNull(hit);
        assertEquals(ball, hit.body(), "the ball is above the floor");
        assertEquals(7.5f, hit.distance(), 0.01f, "the ball's top is at 2.5");
        assertEquals(1f, hit.normal().y, 0.01f);
        assertEquals(2.5f, hit.point().y, 0.01f);

        PhysicsBackend3D.RayHit toFloor = backend.raycast(new Vector3(5f, 10f, 0f), new Vector3(0f, -1f, 0f), 50f, PhysicsLayers3D.ALL_LAYERS);
        assertNotNull(toFloor);
        assertEquals(floor, toFloor.body());
        assertEquals(10f, toFloor.distance(), 0.01f);
    }

    @Test
    void shouldRespectTheRaysDistanceAndLayerMaskAndSkipSensors() {
        int floor = floor();
        backend.addBody(body(MotionType.STATIC, new Vector3(0f, 5f, 0f), ShapeDescriptor3D.box(new Vector3(2f, 0.5f, 2f), ORIGIN),
            true, 0, PhysicsLayers3D.ALL_LAYERS));
        Vector3 from = new Vector3(0f, 10f, 0f);
        Vector3 down = new Vector3(0f, -1f, 0f);

        assertNull(backend.raycast(from, down, 5f, PhysicsLayers3D.ALL_LAYERS), "too short to reach the floor, and the sensor is not a target");
        PhysicsBackend3D.RayHit hit = backend.raycast(from, down, 50f, PhysicsLayers3D.ALL_LAYERS);
        assertNotNull(hit);
        assertEquals(floor, hit.body(), "the ray passes through the sensor");
        assertNull(backend.raycast(from, down, 50f, 1 << 3), "the floor is on layer 0, not in the mask");
    }

    @Test
    void shouldMoveAKinematicBodyToTheRequestedPose() {
        int platform = backend.addBody(body(MotionType.KINEMATIC, new Vector3(0f, 1f, 0f), ShapeDescriptor3D.box(new Vector3(2f, 0.25f, 2f), ORIGIN)));

        backend.moveKinematic(platform, new Vector3(3f, 1f, 0f), NO_ROTATION, STEP);
        backend.step(STEP);

        Vector3 position = new Vector3();
        backend.getBodyTransform(platform, position, new Quaternion());
        assertEquals(3f, position.x, 0.01f);
        assertEquals(1f, position.y, 0.01f, "gravity does not move a kinematic body");
    }

    @Test
    void shouldCarryABodyOnAMovingKinematicPlatform() {
        int platform = backend.addBody(body(MotionType.KINEMATIC, new Vector3(0f, -0.25f, 0f), ShapeDescriptor3D.box(new Vector3(5f, 0.25f, 5f), ORIGIN)));
        int ball = ball(1f);
        run(SETTLE_STEPS);
        float before = heightOf(ball);

        for (int i = 0; i < SETTLE_STEPS; i++) {
            backend.moveKinematic(platform, new Vector3(0f, -0.25f + 0.5f * (i + 1) * STEP, 0f), NO_ROTATION, STEP);
            backend.step(STEP);
        }

        assertEquals(before + 1f, heightOf(ball), 0.1f, "the platform rose 1 m in 2 s and took the ball with it");
    }

    @Test
    void shouldGiveABodyItsMass() {
        floor();
        BodyDescriptor3D heavy = new BodyDescriptor3D(MotionType.DYNAMIC, new Vector3(0f, 2f, 0f), NO_ROTATION,
            List.of(ShapeDescriptor3D.box(new Vector3(0.5f, 0.5f, 0.5f), ORIGIN)), false, 50f, 0f, 0f, 1f, 0.5f, 0f, 0, PhysicsLayers3D.ALL_LAYERS);
        int crate = backend.addBody(heavy);

        run(SETTLE_STEPS);

        assertEquals(0.5f, heightOf(crate), TOLERANCE);
    }

    @Test
    void shouldRefuseAnUnknownOrRemovedBodyHandleInsteadOfCrashing() {
        int ball = ball(1f);
        backend.removeBody(ball);

        assertThrows(IllegalArgumentException.class, () -> backend.getBodyTransform(123_456, new Vector3(), new Quaternion()));
        assertThrows(IllegalArgumentException.class, () -> backend.removeBody(ball));
        assertThrows(IllegalArgumentException.class, () -> backend.moveKinematic(ball, ORIGIN, NO_ROTATION, STEP));
    }

    @Test
    void shouldRefuseALayerOutsideTheSixteen() {
        assertThrows(IllegalArgumentException.class, () -> backend.addBody(body(MotionType.STATIC, ORIGIN,
            ShapeDescriptor3D.sphere(1f, ORIGIN), false, PhysicsLayers3D.LAYER_COUNT, PhysicsLayers3D.ALL_LAYERS)));
    }

    @Test
    void shouldPackLayerMaskAndSensorFlagIntoUserData() {
        long data = JoltPhysicsBackend3D.pack(7, 0b1010_0101_0000_1111, true);

        assertEquals(7, JoltPhysicsBackend3D.layerOf(data));
        assertEquals(0b1010_0101_0000_1111, JoltPhysicsBackend3D.maskOf(data));
        assertTrue(JoltPhysicsBackend3D.isSensor(data));
        assertFalse(JoltPhysicsBackend3D.isSensor(JoltPhysicsBackend3D.pack(7, PhysicsLayers3D.ALL_LAYERS, false)));
    }

    @Test
    void shouldDisposeTwiceAndRefuseUseAfterwards() {
        JoltPhysicsBackend3D other = new JoltPhysicsBackend3D();
        other.dispose();
        other.dispose();

        assertThrows(IllegalStateException.class, () -> other.step(STEP));
        assertThrows(IllegalStateException.class, () -> other.addBody(body(MotionType.STATIC, ORIGIN, ShapeDescriptor3D.sphere(1f, ORIGIN))));
    }

    @Test
    void shouldNameUnsupportedPlatforms() {
        assertEquals("windows/x86-64/com/github/stephengold/joltjni.dll", JoltNatives.resourceFor("Windows 11", "amd64"));
        assertEquals("linux/x86-64/com/github/stephengold/libjoltjni.so", JoltNatives.resourceFor("Linux", "x86_64"));
        assertEquals("osx/aarch64/com/github/stephengold/libjoltjni.dylib", JoltNatives.resourceFor("Mac OS X", "aarch64"));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> JoltNatives.resourceFor("Windows 11", "aarch64"));
        assertTrue(error.getMessage().contains("aarch64"), error.getMessage());
    }

    // --- Forces, impulses, velocities and overlaps (TD-048) ---

    private Vector3 linearVelocityOf(int handle) {
        Vector3 velocity = new Vector3();
        backend.getLinearVelocity(handle, velocity);
        return velocity;
    }

    @Test
    void shouldChangeVelocityByImpulseOverMass() {
        backend.setGravity(new Vector3());
        int ball = ball(5f);

        backend.addImpulse(ball, new Vector3(0f, 0f, 3f));

        assertEquals(3f, linearVelocityOf(ball).z, 1e-4f, "mass is 1 kg, so 3 kg m/s is 3 m/s, at once");
    }

    @Test
    void shouldAccelerateUnderAForceOnlyForTheStepItWasAddedBefore() {
        backend.setGravity(new Vector3());
        int ball = ball(5f);

        backend.addForce(ball, new Vector3(10f, 0f, 0f));
        backend.step(STEP);
        float afterOneStep = linearVelocityOf(ball).x;
        run(10);

        assertEquals(10f * STEP, afterOneStep, 0.005f, "10 N on 1 kg for 1/60 s");
        assertEquals(afterOneStep, linearVelocityOf(ball).x, 0.005f, "the force is gone after the step: no more acceleration");
    }

    @Test
    void shouldSpinUnderATorque() {
        backend.setGravity(new Vector3());
        int ball = ball(5f);

        backend.addTorque(ball, new Vector3(0f, 5f, 0f));
        run(10);
        Vector3 spin = new Vector3();
        backend.getAngularVelocity(ball, spin);

        assertTrue(spin.y > 0f, "turning around Y: " + spin);
        assertEquals(0f, spin.x, 1e-4f);
    }

    @Test
    void shouldKeepTheVelocitiesItWasGiven() {
        backend.setGravity(new Vector3());
        int ball = ball(5f);

        backend.setLinearVelocity(ball, new Vector3(1f, 2f, 3f));
        backend.setAngularVelocity(ball, new Vector3(0f, 4f, 0f));
        Vector3 angular = new Vector3();
        backend.getAngularVelocity(ball, angular);

        assertEquals(new Vector3(1f, 2f, 3f), linearVelocityOf(ball));
        assertEquals(new Vector3(0f, 4f, 0f), angular);
    }

    @Test
    void shouldFindBodiesInsideASphereOnTheAllowedLayersOnly() {
        int onLayer0 = ball(3f);
        int onLayer5 = backend.addBody(body(MotionType.DYNAMIC, new Vector3(0.3f, 3f, 0f), ShapeDescriptor3D.sphere(0.5f, ORIGIN), false, 5,
            PhysicsLayers3D.ALL_LAYERS));
        int sensor = backend.addBody(body(MotionType.STATIC, new Vector3(0f, 3f, 0.2f), ShapeDescriptor3D.sphere(0.5f, ORIGIN), true,
            PhysicsLayers3D.DEFAULT_LAYER, PhysicsLayers3D.ALL_LAYERS));
        backend.addBody(body(MotionType.STATIC, new Vector3(50f, 3f, 0f), ShapeDescriptor3D.sphere(0.5f, ORIGIN)));

        List<Integer> all = new ArrayList<>();
        backend.overlapSphere(new Vector3(0f, 3f, 0f), 0.2f, PhysicsLayers3D.ALL_LAYERS, all);
        List<Integer> layer0 = new ArrayList<>();
        backend.overlapSphere(new Vector3(0f, 3f, 0f), 0.2f, 1 << PhysicsLayers3D.DEFAULT_LAYER, layer0);
        List<Integer> nothing = new ArrayList<>();
        backend.overlapSphere(new Vector3(0f, 30f, 0f), 0.2f, PhysicsLayers3D.ALL_LAYERS, nothing);

        assertEquals(2, all.size(), "both solid bodies, once each, and not the sensor or the faraway body: " + all);
        assertTrue(all.contains(onLayer0) && all.contains(onLayer5));
        assertFalse(all.contains(sensor));
        assertEquals(List.of(onLayer0), layer0);
        assertTrue(nothing.isEmpty());
    }

    @Test
    void shouldRefuseForcesOnARemovedBody() {
        int ball = ball(5f);
        backend.removeBody(ball);

        assertThrows(IllegalArgumentException.class, () -> backend.addForce(ball, new Vector3(1f, 0f, 0f)));
        assertThrows(IllegalArgumentException.class, () -> backend.addImpulse(ball, new Vector3(1f, 0f, 0f)));
        assertThrows(IllegalArgumentException.class, () -> backend.setLinearVelocity(ball, new Vector3()));
        assertThrows(IllegalArgumentException.class, () -> backend.getAngularVelocity(ball, new Vector3()));
    }
}
