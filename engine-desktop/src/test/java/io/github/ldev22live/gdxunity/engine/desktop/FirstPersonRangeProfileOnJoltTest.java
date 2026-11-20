package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.render.play.FirstPersonMover;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileCameraRig;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileShooter;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.EntityLogicComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The acceptance test of First-person Shooter + 3D (roadmap M3b.7, slice 3), on real Jolt through the game runtime's
 * {@link GameSession}, which runs the same profile step as editor Play: in {@code first-person-range-3d.json} the Shooter, with no
 * script or graph, sees through its own eyes, turns with Q and E, walks and steps sideways, and knocks the crates off the shelf
 * with shots along the view. Keys come from a fake source the test holds down.
 */
class FirstPersonRangeProfileOnJoltTest {
    private static final float FRAME = 1f / 60f;
    private static final String SCENE = "samples/first-person-range-3d.json";
    /** One frame's turn at the profile's rate: 120 degrees per second. */
    private static final float TURN_PER_FRAME = GameGenres.SHOOTER_TURN_DEGREES_PER_SECOND / 60f;
    /** How far one frame of walking goes: the latency between a key change and the next physics step. */
    private static final float ONE_FRAME_OF_WALKING = GameGenres.SHOOTER_WALK_SPEED / 60f;

    private final AssetSource project = AssetSource.directory(new File("../assets"));
    private final Set<Integer> held = new HashSet<>();
    private GameSession session;
    private Scene scene;
    private Entity shooter;
    private Entity camera;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.dispose();
        }
    }

    private void start() throws IOException {
        scene = GameSession.loadScene(project, SCENE);
        session = new GameSession(project, scene, new ScriptRegistry(), GameSession.CONSOLE);
        session.setInput(held::contains);
        assertEquals(List.of(), session.start(), "the sample plays with its genre and mode alone");
        shooter = scene.findEntityByName("Shooter");
        camera = scene.findEntityByName("Main Camera");
        run(30); // settle on the floor; the crates settle on the shelf
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) {
            session.update(FRAME);
        }
    }

    private void hold(int key, int frames) {
        held.add(key);
        run(frames);
        held.remove(key);
    }

    private Vector3 position(String name) {
        return scene.findEntityByName(name).getTransform().getWorldPosition();
    }

    private Vector3 view() {
        return camera.getTransform().getWorldRotation().transform(new Vector3(0f, 0f, -1f));
    }

    @Test
    void shouldSeeThroughTheShootersEyesWithoutAScript() throws IOException {
        start();

        assertTrue(shooter.getComponents(EntityLogicComponent.class).isEmpty(), "no script or graph on the Shooter");
        assertTrue(session.getProfileMessage().startsWith("Playing as First-person Shooter + 3D."), session.getProfileMessage());
        assertNotNull(shooter.getComponent(FirstPersonMover.class));
        assertNotNull(shooter.getComponent(ProfileShooter.class));
        CameraRigComponent rig = camera.getComponent(CameraRigComponent.class);
        assertInstanceOf(ProfileCameraRig.class, rig);
        assertEquals(CameraRigComponent.Mode.FIRST_PERSON, rig.getMode());
        Vector3 eye = camera.getTransform().getWorldPosition();
        Vector3 body = shooter.getTransform().getWorldPosition();
        assertEquals(body.x, eye.x, 0.01f);
        assertEquals(body.z, eye.z, 0.01f);
        assertEquals(body.y + GameGenres.FIRST_PERSON_EYE_HEIGHT, eye.y, 0.5f, "at the eyes");
        assertEquals(-1f, view().z, 0.001f, "looking the way the Shooter faces, level: " + view());
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        SceneSerializationException unsaved = assertThrows(SceneSerializationException.class,
            () -> new SceneSerializer(registry).toJson(scene), "the profile's parts are never saved");
        assertTrue(unsaved.getMessage().contains("never saved"), unsaved.getMessage());
    }

    @Test
    void shouldWalkStepSidewaysWithoutTurningAndTurnTheViewWithQAndE() throws IOException {
        start();
        FirstPersonMover mover = shooter.getComponent(FirstPersonMover.class);

        Vector3 from = position("Shooter");
        hold(Keys.W, 60);
        Vector3 forward = position("Shooter").sub(from);
        assertTrue(forward.z < -350f, "W walks the way it faces, along -Z: " + forward);
        assertEquals(0f, forward.x, 5f, forward.toString());

        from = position("Shooter");
        hold(Keys.D, 60);
        Vector3 side = position("Shooter").sub(from);
        assertTrue(side.x > 350f, "D steps right: " + side);
        // Physics steps before the mover reads the keys, so W's last frame carries over: one frame, 450 / 60 units
        assertEquals(0f, side.z, ONE_FRAME_OF_WALKING + 1f, side.toString());
        assertEquals(0f, mover.getFacingYaw(), 0.01f, "stepping sideways does not turn the Shooter");
        assertEquals(-1f, view().z, 0.001f, "nor the view");

        hold(Keys.E, 45); // 120 degrees per second for three quarters of a second: 90 degrees to the right
        assertEquals(-90f, mover.getFacingYaw(), 0.5f);
        assertEquals(1f, view().x, 0.01f, "the view turned with the Shooter, now along +X: " + view());
        from = position("Shooter");
        hold(Keys.W, 30);
        assertTrue(position("Shooter").sub(from).x > 150f, "W now walks along +X");

        hold(Keys.Q, 45);
        assertEquals(0f, mover.getFacingYaw(), 0.5f, "Q turns it back");
        assertEquals(List.of(), session.getRuntimeErrors());
    }

    @Test
    void shouldKnockTheCrateItAimsAtOffTheShelfOncePerPress() throws IOException {
        start();
        ProfileShooter gun = shooter.getComponent(ProfileShooter.class);
        Vector3 middle = position("Crate Middle");
        Vector3 left = position("Crate Left");
        Vector3 right = position("Crate Right");

        hold(Keys.F, 60); // held for a second: one shot
        assertEquals(1, gun.getShots(), "once per press");
        assertEquals("Crate Middle", gun.getLastHit().map(Entity::getName).orElse("nothing"));
        assertEquals(1, gun.getKnocks());
        assertTrue(position("Crate Middle").z < middle.z - 100f, "knocked back off the shelf: " + position("Crate Middle"));
        assertEquals(0f, position("Crate Left").dst(left), 1f, "the others stay");
        assertEquals(0f, position("Crate Right").dst(right), 1f);

        // About 11 degrees to the right is the right-hand crate: six frames of E turn 12
        hold(Keys.E, Math.round(12f / TURN_PER_FRAME));
        hold(Keys.CONTROL_LEFT, 1); // the other fire key
        run(60);
        assertEquals(2, gun.getShots());
        assertEquals("Crate Right", gun.getLastHit().map(Entity::getName).orElse("nothing"));
        assertTrue(position("Crate Right").z < right.z - 100f, "knocked back: " + position("Crate Right"));
        assertEquals(0f, position("Crate Left").dst(left), 1f, "the left one is still there");

        // Facing the east wall: the shot stops there and knocks nothing
        hold(Keys.E, Math.round(78f / TURN_PER_FRAME));
        hold(Keys.F, 1);
        assertEquals(3, gun.getShots());
        assertEquals(Optional.of("East Wall"), gun.getLastHit().map(Entity::getName));
        assertEquals(2, gun.getKnocks(), "a wall is not knocked");
        assertEquals(List.of(), session.getRuntimeErrors());
    }
}
