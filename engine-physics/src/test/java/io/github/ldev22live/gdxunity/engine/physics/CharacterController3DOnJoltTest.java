package io.github.ldev22live.gdxunity.engine.physics;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Walking characters (Phase 15-G, TD-049) on the real Jolt backend, in scene units (100 per meter): they stand, fall, walk,
 * jump, climb steps and slopes, are blocked by what is too high or steep, push crates, and are seen by other bodies and rays.
 */
class CharacterController3DOnJoltTest {
    private static final float FRAME = 1f / 60f;
    /** A 20 m wide, 1 m thick slab whose top face is at y = 0. */
    private static final Vector3 FLOOR_SIZE = new Vector3(2000f, 100f, 2000f);
    /** Where a default character's center is when its feet stand on the floor: half of 80 + 2 * 30. */
    private static final float STANDING_Y = 70f;
    private static final float TOLERANCE = 5f;
    private static final float WALK_SPEED = 150f;
    private static final float GRAVITY_UNITS = 981f;

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
        Entity floor = scene.createEntity("Floor");
        floor.getTransform().setLocalPosition(0f, -50f, 0f);
        floor.addComponent(new BoxCollider3DComponent(FLOOR_SIZE));
    }

    @AfterEach
    void tearDown() {
        physics.dispose();
        scene.dispose();
    }

    private Entity character(float x, float y) {
        Entity hero = scene.createEntity("Hero");
        hero.getTransform().setLocalPosition(x, y, 0f);
        hero.addComponent(new CharacterController3DComponent());
        return hero;
    }

    private Entity box(String name, float x, float y, float width, float height, float depth) {
        Entity box = scene.createEntity(name);
        box.getTransform().setLocalPosition(x, y, 0f);
        box.addComponent(new BoxCollider3DComponent(new Vector3(width, height, depth)));
        return box;
    }

    private void run(int frames, float frameSeconds) {
        for (int i = 0; i < frames; i++) {
            physics.update(frameSeconds);
        }
    }

    private void run(int frames) {
        run(frames, FRAME);
    }

    private float y(Entity entity) {
        return entity.getTransform().getWorldPosition().y;
    }

    private float x(Entity entity) {
        return entity.getTransform().getWorldPosition().x;
    }

    @Test
    void shouldStandOnTheFloorForTenSecondsWithoutSinkingOrDrifting() {
        Entity hero = character(0f, STANDING_Y + 5f);

        run(10 * 60);

        assertTrue(physics.isCharacterGrounded(hero), "grounded after settling");
        assertEquals(STANDING_Y, y(hero), 2f, "feet on the floor");
        assertEquals(0f, x(hero), 0.5f, "no drift in x");
        assertEquals(0f, hero.getTransform().getWorldPosition().z, 0.5f, "no drift in z");
    }

    @Test
    void shouldFallUnderGravityBeforeItLands() {
        Entity hero = character(0f, 570f);

        run(30);

        assertFalse(physics.isCharacterGrounded(hero), "still falling after half a second");
        // After 0.5 s of free fall from rest: g * t^2 / 2 = 122.6 units
        assertEquals(570f - 0.5f * GRAVITY_UNITS * 0.25f, y(hero), TOLERANCE);
        run(120);
        assertTrue(physics.isCharacterGrounded(hero), "landed");
        assertEquals(STANDING_Y, y(hero), 2f);
    }

    @Test
    void shouldWalkTheSameDistanceAtThirtyAndSixtyFramesPerSecond() {
        float at60 = walkedInOneSecond(1f / 60f, 60);
        float at30 = walkedInOneSecond(1f / 30f, 30);

        assertEquals(WALK_SPEED, at60, TOLERANCE);
        assertEquals(WALK_SPEED, at30, TOLERANCE);
    }

    /** Distance a character walks in one simulated second in a scene of its own, with frames of {@code frameSeconds}. */
    private float walkedInOneSecond(float frameSeconds, int frames) {
        Scene other = new Scene("Other");
        PhysicsSystem3D otherPhysics = new PhysicsSystem3D(other, new JoltPhysicsBackend3D());
        try {
            Entity floor = other.createEntity("Floor");
            floor.getTransform().setLocalPosition(0f, -50f, 0f);
            floor.addComponent(new BoxCollider3DComponent(FLOOR_SIZE));
            Entity hero = other.createEntity("Hero");
            hero.getTransform().setLocalPosition(-500f, STANDING_Y, 0f);
            hero.addComponent(new CharacterController3DComponent());
            for (int i = 0; i < 30; i++) {
                otherPhysics.update(frameSeconds);
            }
            float start = hero.getTransform().getWorldPosition().x;
            otherPhysics.moveCharacter(hero, new Vector3(WALK_SPEED, 0f, 0f));
            for (int i = 0; i < frames; i++) {
                otherPhysics.update(frameSeconds);
            }
            return hero.getTransform().getWorldPosition().x - start;
        } finally {
            otherPhysics.dispose();
            other.dispose();
        }
    }

    @Test
    void shouldJumpToTheHeightPhysicsPredictsAndOnlyWhileGrounded() {
        Entity hero = character(0f, STANDING_Y);
        run(30);
        assertTrue(physics.isCharacterGrounded(hero));
        float jumpSpeed = 400f;
        float rest = y(hero);

        assertTrue(physics.jumpCharacter(hero, jumpSpeed), "jumps from the ground");
        assertFalse(physics.jumpCharacter(hero, jumpSpeed), "cannot jump again in the same frame");
        float apex = rest;
        for (int i = 0; i < 120; i++) {
            physics.update(FRAME);
            apex = Math.max(apex, y(hero));
        }

        float expected = jumpSpeed * jumpSpeed / (2f * GRAVITY_UNITS);
        assertEquals(expected, apex - rest, expected * 0.05f, "apex within 5% of v^2 / 2g");
        assertTrue(physics.isCharacterGrounded(hero), "back on the ground");
    }

    @Test
    void shouldStopRisingAtACeilingAndComeBackDown() {
        box("Ceiling", 0f, 250f, 400f, 100f, 400f); // its underside is at y = 200; the capsule's top is at 140 when standing
        Entity hero = character(0f, STANDING_Y);
        run(30);

        assertTrue(physics.jumpCharacter(hero, 700f), "a jump that would reach 245 units without the ceiling");
        float highest = 0f;
        for (int i = 0; i < 20; i++) {
            physics.update(FRAME);
            highest = Math.max(highest, y(hero));
        }

        assertTrue(highest + 70f <= 200f + 3f, "the head stopped at the ceiling's underside (y=200): center " + highest);
        run(120);
        assertTrue(physics.isCharacterGrounded(hero), "and it is back on the floor");
        assertEquals(STANDING_Y, y(hero), 2f);
    }

    @Test
    void shouldClimbAStepLowerThanItsStepHeightAndStopAtAHigherOne() {
        box("Low step", 400f, 10f, 200f, 20f, 400f); // top at y = 20, below the 30 unit step height
        Entity high = scene.createEntity("High step");
        high.getTransform().setLocalPosition(400f, 25f, 1000f);
        high.addComponent(new BoxCollider3DComponent(new Vector3(200f, 50f, 400f))); // top at y = 50, in another lane
        Entity climber = character(0f, STANDING_Y);
        Entity blocked = scene.createEntity("Blocked");
        blocked.getTransform().setLocalPosition(0f, STANDING_Y, 1000f);
        blocked.addComponent(new CharacterController3DComponent());
        run(30);
        physics.moveCharacter(climber, new Vector3(WALK_SPEED, 0f, 0f));
        physics.moveCharacter(blocked, new Vector3(WALK_SPEED, 0f, 0f));

        run(3 * 60); // 450 units: on the low step (x 300 to 500), not yet off its far side

        assertTrue(x(climber) > 400f, "the climber is on the low step: " + x(climber));
        assertEquals(STANDING_Y + 20f, y(climber), TOLERANCE * 2f, "standing on the low step");
        assertTrue(x(blocked) < 400f - 100f + TOLERANCE, "the high step stops the other one at its face (x=300): " + x(blocked));
        assertEquals(STANDING_Y, y(blocked), 3f, "it did not climb");
    }

    @Test
    void shouldWalkUpAGentleSlopeAndBeStoppedByASteepOne() {
        Entity gentle = ramp("Gentle", 30f, 0f);
        Entity steep = ramp("Steep", 60f, 1000f);
        Entity easy = scene.createEntity("Easy");
        easy.getTransform().setLocalPosition(0f, STANDING_Y, 0f);
        easy.addComponent(new CharacterController3DComponent());
        Entity hard = scene.createEntity("Hard");
        hard.getTransform().setLocalPosition(0f, STANDING_Y, 1000f);
        hard.addComponent(new CharacterController3DComponent());
        run(30);
        physics.moveCharacter(easy, new Vector3(WALK_SPEED, 0f, 0f));
        physics.moveCharacter(hard, new Vector3(WALK_SPEED, 0f, 0f));

        run(4 * 60);

        assertNotNull(gentle);
        assertNotNull(steep);
        assertTrue(y(easy) > STANDING_Y + 60f, "climbed the 30 degree slope: " + y(easy));
        assertTrue(y(hard) < STANDING_Y + 40f, "did not climb the 60 degree slope: " + y(hard));
    }

    /** A 6 m long ramp rising along +x whose surface crosses y = 0 at x = 400, in the lane at {@code z}. */
    private Entity ramp(String name, float degrees, float z) {
        Entity ramp = scene.createEntity(name);
        ramp.getTransform().setLocalPosition(400f, 0f, z);
        ramp.getTransform().setLocalRotation(new Quaternion(Vector3.Z, degrees));
        ramp.addComponent(new BoxCollider3DComponent(new Vector3(600f, 20f, 400f)));
        return ramp;
    }

    @Test
    void shouldPushALightCrateItWalksInto() {
        Entity crate = scene.createEntity("Crate");
        crate.getTransform().setLocalPosition(100f, 25f, 0f);
        crate.addComponent(new RigidBody3DComponent(MotionType.DYNAMIC));
        crate.addComponent(new BoxCollider3DComponent(new Vector3(50f, 50f, 50f)));
        Entity hero = character(-100f, STANDING_Y);
        run(30);
        physics.moveCharacter(hero, new Vector3(WALK_SPEED, 0f, 0f));

        run(3 * 60);

        assertTrue(x(crate) > 100f + 100f, "the crate was pushed along: " + x(crate));
    }

    @Test
    void shouldLetABallLandOnTheCharacter() {
        Entity hero = character(0f, STANDING_Y);
        Entity ball = scene.createEntity("Ball");
        ball.getTransform().setLocalPosition(0f, 600f, 0f);
        ball.addComponent(new RigidBody3DComponent(MotionType.DYNAMIC));
        ball.addComponent(new SphereCollider3DComponent(20f));

        run(4 * 60);

        float headTop = y(hero) + 80f / 2f + 30f;
        assertTrue(y(ball) > headTop, "the ball rests on the character's head instead of falling through: ball " + y(ball) + ", head " + headTop);
        assertTrue(contacts.stream().anyMatch(c -> c.kind() == ContactKind.COLLISION_ENTER && involves(c, hero) && involves(c, ball)),
            "the ball and the character touched: " + contacts);
    }

    private static boolean involves(Contact contact, Entity entity) {
        return contact.entityA() == entity || contact.entityB() == entity;
    }

    @Test
    void shouldReportLandingOnceAndNothingWhileWalkingAcrossTheFloor() {
        Entity hero = character(0f, STANDING_Y + 40f);
        Entity floor = scene.getAllEntities().stream().filter(e -> e.getName().equals("Floor")).findFirst().orElseThrow();
        run(60);
        physics.moveCharacter(hero, new Vector3(WALK_SPEED, 0f, 0f));
        run(2 * 60);

        long enters = contacts.stream().filter(c -> c.kind() == ContactKind.COLLISION_ENTER && involves(c, hero) && involves(c, floor)).count();
        long exits = contacts.stream().filter(c -> c.kind() == ContactKind.COLLISION_EXIT && involves(c, hero) && involves(c, floor)).count();
        assertEquals(1, enters, "one landing: " + contacts);
        assertEquals(0, exits, "never left the floor while walking: " + contacts);
    }

    @Test
    void shouldReportEnteringAndLeavingATriggerZone() {
        Entity zone = scene.createEntity("Zone");
        zone.getTransform().setLocalPosition(300f, 70f, 0f);
        BoxCollider3DComponent trigger = new BoxCollider3DComponent(new Vector3(100f, 200f, 200f));
        trigger.setTrigger(true);
        zone.addComponent(trigger);
        Entity hero = character(0f, STANDING_Y);
        run(30);
        physics.moveCharacter(hero, new Vector3(WALK_SPEED, 0f, 0f));

        run(5 * 60);

        List<ContactKind> kinds = contacts.stream().filter(c -> involves(c, hero) && involves(c, zone)).map(Contact::kind).toList();
        assertEquals(List.of(ContactKind.TRIGGER_ENTER, ContactKind.TRIGGER_EXIT), kinds, contacts.toString());
    }

    @Test
    void shouldBeFoundByRaysAndSphereOverlaps() {
        Entity hero = character(0f, STANDING_Y);
        run(30);

        PhysicsSystem3D.RaycastHit hit = physics.raycast(new Vector3(0f, 400f, 0f), new Vector3(0f, -1f, 0f), 1000f,
            PhysicsLayers3D.ALL_LAYERS).orElseThrow();

        assertEquals(hero, hit.entity());
        assertEquals(400f - (y(hero) + 70f), hit.distance(), TOLERANCE);
        assertTrue(physics.overlapSphere(new Vector3(40f, y(hero), 0f), 20f, PhysicsLayers3D.ALL_LAYERS).contains(hero));
    }

    @Test
    void shouldRefuseACharacterThatAlsoHasARigidBody() {
        Entity hero = scene.createEntity("Hero");
        hero.addComponent(new CharacterController3DComponent());
        hero.addComponent(new RigidBody3DComponent(MotionType.DYNAMIC));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> physics.update(FRAME));

        assertTrue(error.getMessage().contains("Hero") && error.getMessage().contains("Rigid Body 3D"), error.getMessage());
    }

    @Test
    void shouldTeleportACharacterAndLetItLandAndWalkFromThere() {
        Entity hero = character(0f, STANDING_Y);
        run(2);
        physics.moveCharacter(hero, new Vector3(WALK_SPEED, 0f, 0f));
        run(60);
        assertTrue(x(hero) > 100f, "it was walking");

        physics.teleportCharacter(hero, new Vector3(-500f, 400f, 0f));
        assertEquals(-500f, x(hero), 0.01f, "the entity moves at once");
        assertEquals(400f, y(hero), 0.01f);
        assertFalse(physics.isCharacterGrounded(hero));
        run(1);
        assertTrue(Math.abs(x(hero) + 500f) < 5f, "walking speed was cleared: " + x(hero));

        run(120);
        assertEquals(STANDING_Y, y(hero), TOLERANCE, "it fell and landed at the new place");
        assertTrue(physics.isCharacterGrounded(hero));
        physics.moveCharacter(hero, new Vector3(WALK_SPEED, 0f, 0f));
        run(60);
        assertEquals(-500f + WALK_SPEED, x(hero), TOLERANCE, "and walks on from there");
    }

    @Test
    void shouldRefuseToTeleportAnEntityThatIsNotACharacterOrToANonFinitePlace() {
        Entity hero = character(0f, STANDING_Y);
        Entity wall = box("Wall", 300f, 50f, 100f, 100f, 100f);
        run(2);

        assertTrue(assertThrows(IllegalStateException.class, () -> physics.teleportCharacter(wall, new Vector3()))
            .getMessage().contains("Wall"));
        assertThrows(IllegalArgumentException.class, () -> physics.teleportCharacter(hero, new Vector3(Float.NaN, 0f, 0f)));
        assertEquals(0f, x(hero), TOLERANCE, "a refused call moves nothing");
    }

    @Test
    void shouldRemoveTheCharacterWhenItsEntityIsDeactivated() {
        Entity hero = character(0f, STANDING_Y);
        run(5);
        assertTrue(physics.hasBody(hero));

        hero.setActive(false);
        run(5);

        assertFalse(physics.hasBody(hero));
        assertThrows(IllegalStateException.class, () -> physics.moveCharacter(hero, new Vector3(1f, 0f, 0f)));
    }
}
