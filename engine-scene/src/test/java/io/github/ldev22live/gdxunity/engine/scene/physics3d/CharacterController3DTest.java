package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.CharacterController3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The character controller component, its system logic on a recording backend, and the script API (TD-049). */
class CharacterController3DTest {
    private static final float FRAME = PhysicsSystem3D.FIXED_TIMESTEP;
    private static final float GRAVITY = PhysicsSystem3D.DEFAULT_GRAVITY.y;

    /** A script that does nothing by itself: the tests call the character API on it. */
    static final class Driver extends ScriptComponent {
    }

    private Scene scene;
    private PhysicsSystem3DTest.FakeBackend backend;
    private PhysicsSystem3D physics;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Characters");
        backend = new PhysicsSystem3DTest.FakeBackend();
        physics = new PhysicsSystem3D(scene, backend);
    }

    private Entity hero() {
        Entity hero = scene.createEntity("Hero");
        hero.getTransform().setLocalPosition(100f, 200f, 300f);
        hero.addComponent(new CharacterController3DComponent());
        return hero;
    }

    private Vector3 lastMove() {
        return backend.characterMoves.get(backend.characterMoves.size() - 1);
    }

    // --- Component ---

    @Test
    void shouldValidateTheComponentsValues() {
        CharacterController3DComponent controller = new CharacterController3DComponent();

        assertThrows(IllegalArgumentException.class, () -> controller.setRadius(0f));
        assertThrows(IllegalArgumentException.class, () -> controller.setHeight(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> controller.setSlopeLimitDegrees(90f));
        assertThrows(IllegalArgumentException.class, () -> controller.setSlopeLimitDegrees(-1f));
        assertThrows(IllegalArgumentException.class, () -> controller.setStepHeight(-1f));
        assertThrows(IllegalArgumentException.class, () -> controller.setMass(0f));
        assertThrows(IllegalArgumentException.class, () -> controller.setLayer(PhysicsLayers3D.LAYER_COUNT));
        assertEquals(CharacterController3DComponent.DEFAULT_HEIGHT + 2f * CharacterController3DComponent.DEFAULT_RADIUS,
            controller.getTotalHeight());
    }

    @Test
    void shouldRoundTripTheComponentThroughTheSceneFile() {
        Entity hero = scene.createEntity("Hero");
        CharacterController3DComponent controller = new CharacterController3DComponent(20f, 90f);
        controller.setSlopeLimitDegrees(30f);
        controller.setStepHeight(15f);
        controller.setMass(55f);
        controller.setGravityScale(0.5f);
        controller.setLayer(3);
        controller.setCollisionMask(0b101);
        controller.setEnabled(false);
        hero.addComponent(controller);
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new CharacterController3DComponentSerializer());
        SceneSerializer serializer = new SceneSerializer(registry);

        CharacterController3DComponent read = serializer.fromJson(serializer.toJson(scene)).findEntityById(hero.getId())
            .getComponent(CharacterController3DComponent.class);

        assertEquals(20f, read.getRadius());
        assertEquals(90f, read.getHeight());
        assertEquals(30f, read.getSlopeLimitDegrees());
        assertEquals(15f, read.getStepHeight());
        assertEquals(55f, read.getMass());
        assertEquals(0.5f, read.getGravityScale());
        assertEquals(3, read.getLayer());
        assertEquals(0b101, read.getCollisionMask());
        assertFalse(read.isEnabled());
    }

    // --- System ---

    @Test
    void shouldCreateTheCharacterInMetersAndRadiansAtTheEntitysPosition() {
        Entity hero = hero();
        CharacterController3DComponent controller = hero.getComponent(CharacterController3DComponent.class);
        controller.setSlopeLimitDegrees(60f);
        controller.setLayer(2);

        physics.update(FRAME);

        CharacterDescriptor3D character = backend.characterDescriptors.values().iterator().next();
        assertEquals(new Vector3(1f, 2f, 3f), character.position());
        assertEquals(0.3f, character.radius(), 1e-5f);
        assertEquals(0.4f, character.halfHeight(), 1e-5f);
        assertEquals((float) Math.toRadians(60.0), character.slopeRadians(), 1e-5f);
        assertEquals(0.3f, character.stepHeight(), 1e-5f);
        assertEquals(70f, character.mass());
        assertEquals(2, character.layer());
        assertTrue(physics.hasBody(hero));
        assertTrue(backend.bodies.isEmpty(), "a character is not a rigid body");
    }

    @Test
    void shouldTeleportInMetersClearTheSpeedAndMoveTheEntityAtOnce() {
        Entity hero = hero();
        backend.grounded = true;
        physics.update(FRAME);
        physics.update(FRAME);
        physics.moveCharacter(hero, new Vector3(300f, 0f, 0f));
        assertTrue(physics.jumpCharacter(hero, 400f));

        physics.teleportCharacter(hero, new Vector3(1000f, 500f, -200f));

        assertEquals(new Vector3(10f, 5f, -2f), backend.characterPositions.values().iterator().next(), "backend gets meters");
        assertEquals(1000f, hero.getTransform().getWorldPosition().x, 1e-3f);
        assertEquals(500f, hero.getTransform().getWorldPosition().y, 1e-3f);
        assertFalse(physics.isCharacterGrounded(hero));
        physics.update(FRAME);
        assertEquals(0f, lastMove().x, 0f, "walking speed cleared");
        assertEquals(GRAVITY * FRAME * 0.5f, lastMove().y, 1e-5f, "rising speed cleared: the fall starts again");
    }

    @Test
    void shouldRefuseToTeleportWhatIsNotACharacterOrToANonFinitePlace() {
        Entity hero = hero();
        Entity other = scene.createEntity("Other");
        physics.update(FRAME);

        assertTrue(assertThrows(IllegalStateException.class, () -> physics.teleportCharacter(other, new Vector3()))
            .getMessage().contains("Other"));
        assertThrows(IllegalArgumentException.class, () -> physics.teleportCharacter(hero, new Vector3(0f, Float.POSITIVE_INFINITY, 0f)));
    }

    @Test
    void shouldFallUnderGravityUntilTheBackendSaysItStandsOnGround() {
        Entity hero = hero();
        physics.update(FRAME); // one fixed step in the air

        assertEquals(GRAVITY * FRAME * 0.5f, lastMove().y, 1e-5f, "the first step moves at the average speed of the step");
        physics.update(FRAME);
        assertEquals(GRAVITY * FRAME * 1.5f, lastMove().y, 1e-5f);

        backend.grounded = true;
        physics.update(FRAME);
        physics.update(FRAME);
        assertEquals(0f, lastMove().y, 0f, "standing: no downward speed piles up");
        assertTrue(physics.isCharacterGrounded(hero));
    }

    @Test
    void shouldWalkAtTheSetHorizontalSpeedInMetersAndIgnoreTheVerticalPart() {
        Entity hero = hero();
        physics.update(FRAME);
        backend.grounded = true;
        physics.update(FRAME); // the character learns it landed
        physics.update(FRAME);

        physics.moveCharacter(hero, new Vector3(200f, 9999f, -100f));
        physics.update(FRAME);

        assertEquals(2f, lastMove().x, 1e-5f);
        assertEquals(-1f, lastMove().z, 1e-5f);
        assertEquals(0f, lastMove().y, 0f, "the Y part of the walk velocity is ignored");
        physics.update(FRAME);
        assertEquals(2f, lastMove().x, 1e-5f, "the walk goes on until it is changed");
    }

    @Test
    void shouldMoveTheEntityToWhereTheBackendPutTheCharacterAndKeepItsRotation() {
        Entity hero = hero();
        Quaternion turned = new Quaternion(Vector3.Y, 90f);
        hero.getTransform().setLocalRotation(turned);
        physics.update(FRAME);
        backend.grounded = true;
        physics.moveCharacter(hero, new Vector3(600f, 0f, 0f)); // 6 m/s

        for (int i = 0; i < 10; i++) {
            physics.update(FRAME);
        }

        // 6 m/s for ten fixed steps of 1/60 s: one meter
        assertEquals(100f + 100f, hero.getTransform().getWorldPosition().x, 1f);
        assertEquals(200f, hero.getTransform().getWorldPosition().y, 1f, "height is whatever the backend says: it did not move");
        assertEquals(1f, Math.abs(hero.getTransform().getWorldRotation().dot(turned)), 1e-4f, "rotation is not the simulation's");
    }

    @Test
    void shouldJumpOnlyFromTheGroundAndRiseAtTheGivenSpeed() {
        Entity hero = hero();
        physics.update(FRAME);

        assertFalse(physics.jumpCharacter(hero, 400f), "in the air at the start");

        backend.grounded = true;
        physics.update(FRAME);
        assertTrue(physics.jumpCharacter(hero, 400f));
        assertFalse(physics.jumpCharacter(hero, 400f), "already jumping");
        backend.grounded = false;
        physics.update(FRAME);

        assertEquals(4f + GRAVITY * FRAME * 0.5f, lastMove().y, 1e-4f, "rises at 4 m/s, less half a step of gravity");
        assertThrows(IllegalArgumentException.class, () -> physics.jumpCharacter(hero, 0f));
        assertThrows(IllegalArgumentException.class, () -> physics.jumpCharacter(hero, Float.NaN));
    }

    @Test
    void shouldWalkTheSameDistanceAtThirtyAndSixtyFramesPerSecond() {
        Entity hero = hero();
        physics.update(FRAME);
        backend.grounded = true;
        physics.moveCharacter(hero, new Vector3(100f, 0f, 0f));
        float start = hero.getTransform().getWorldPosition().x;

        for (int i = 0; i < 60; i++) {
            physics.update(1f / 60f);
        }
        float at60 = hero.getTransform().getWorldPosition().x - start;

        start = hero.getTransform().getWorldPosition().x;
        for (int i = 0; i < 30; i++) {
            physics.update(1f / 30f);
        }
        float at30 = hero.getTransform().getWorldPosition().x - start;

        assertEquals(100f, at60, 0.5f);
        assertEquals(100f, at30, 0.5f);
    }

    @Test
    void shouldReportHowFastTheCharacterActuallyMoved() {
        Entity hero = hero();
        physics.update(FRAME);
        backend.grounded = true;
        physics.moveCharacter(hero, new Vector3(300f, 0f, 0f));

        physics.update(FRAME);

        assertEquals(300f, physics.getCharacterVelocity(hero).x, 0.5f);
    }

    @Test
    void shouldRefuseCharacterCallsOnOtherEntitiesAndForcesOnCharacters() {
        Entity hero = hero();
        Entity crate = scene.createEntity("Crate");
        crate.addComponent(new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC));
        crate.addComponent(new BoxCollider3DComponent());
        physics.update(FRAME);

        IllegalStateException notCharacter = assertThrows(IllegalStateException.class, () -> physics.moveCharacter(crate, Vector3.X));
        IllegalStateException isCharacter = assertThrows(IllegalStateException.class, () -> physics.addForce(hero, Vector3.X));

        assertTrue(notCharacter.getMessage().contains("Crate") && notCharacter.getMessage().contains("Character Controller 3D"),
            notCharacter.getMessage());
        assertTrue(isCharacter.getMessage().contains("Hero") && isCharacter.getMessage().contains("character"), isCharacter.getMessage());
        assertThrows(IllegalArgumentException.class, () -> physics.moveCharacter(hero, new Vector3(Float.NaN, 0f, 0f)));
    }

    @Test
    void shouldRefuseACharacterWithARigidBodyOrAColliderNamingTheEntity() {
        Entity withBody = scene.createEntity("WithBody");
        withBody.addComponent(new CharacterController3DComponent());
        withBody.addComponent(new RigidBody3DComponent());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> physics.update(FRAME));

        assertTrue(error.getMessage().contains("WithBody") && error.getMessage().contains("Rigid Body 3D"), error.getMessage());
        withBody.removeComponent(withBody.getComponent(RigidBody3DComponent.class));
        withBody.addComponent(new SphereCollider3DComponent());
        error = assertThrows(IllegalStateException.class, () -> physics.update(FRAME));
        assertTrue(error.getMessage().contains("3D collider"), error.getMessage());
    }

    @Test
    void shouldRemoveTheCharacterWhenDisabledDeactivatedOrDisposed() {
        Entity hero = hero();
        physics.update(FRAME);
        assertEquals(1, backend.characterDescriptors.size());

        hero.getComponent(CharacterController3DComponent.class).setEnabled(false);
        physics.update(FRAME);
        assertTrue(backend.characterDescriptors.isEmpty(), "a disabled controller removes the character");
        assertFalse(physics.hasBody(hero));

        hero.getComponent(CharacterController3DComponent.class).setEnabled(true);
        physics.update(FRAME);
        assertEquals(1, backend.characterDescriptors.size(), "and enabling brings it back");
        hero.setActive(false);
        physics.update(FRAME);
        assertTrue(backend.characterDescriptors.isEmpty());

        hero.setActive(true);
        physics.update(FRAME);
        physics.dispose();
        assertTrue(backend.characterDescriptors.isEmpty(), "dispose removes the characters before the backend goes");
    }

    @Test
    void shouldCountSceneWithOnlyACharacterAsUsing3DPhysics() {
        Scene empty = new Scene("Empty");
        assertFalse(PhysicsSystem3D.isUsedBy(empty));
        Entity hero = empty.createEntity("Hero");
        hero.addComponent(new CharacterController3DComponent());

        assertTrue(PhysicsSystem3D.isUsedBy(empty), "so a backend is created for it");
    }

    // --- Script API ---

    @Test
    void shouldLetAScriptWalkJumpAndAskAboutTheGround() {
        Entity hero = hero();
        Driver script = new Driver();
        hero.addComponent(script);
        physics.update(FRAME);
        backend.grounded = true;
        physics.update(FRAME);

        script.moveCharacter3D(new Vector3(100f, 0f, 0f));
        assertTrue(script.isGrounded3D());
        assertTrue(script.jumpCharacter3D(300f));
        physics.update(FRAME);

        assertEquals(1f, lastMove().x, 1e-5f);
        assertTrue(lastMove().y > 2.9f, "rising: " + lastMove());
        assertTrue(script.getCharacterVelocity3D().x > 99f);
    }

    @Test
    void shouldExplainWhenTheScriptsEntityIsNotACharacterOrThereIsNoPhysics() {
        Entity plain = scene.createEntity("Plain");
        Driver script = new Driver();
        plain.addComponent(script);
        physics.update(FRAME);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> script.moveCharacter3D(Vector3.X));
        assertTrue(error.getMessage().contains("Plain") && error.getMessage().contains("Character Controller 3D"), error.getMessage());

        physics.dispose();
        assertThrows(IllegalStateException.class, script::isGrounded3D);
    }
}
