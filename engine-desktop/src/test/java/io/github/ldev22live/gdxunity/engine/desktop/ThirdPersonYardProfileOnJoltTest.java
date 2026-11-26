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
 * The acceptance test of Third-person Shooter + 3D (roadmap M3b.7, slice 4), on real Jolt through the game runtime's
 * {@link GameSession}, which runs the same profile step as editor Play: in {@code third-person-yard-3d.json} the Gunner, with no
 * script or graph, is seen from behind and above by a camera that always looks the way it faces; Q and E turn it (and so the view),
 * it walks and steps sideways, and level shots from its chest knock the crates on the floor. Keys come from a fake source the test
 * holds down.
 */
class ThirdPersonYardProfileOnJoltTest {
    private static final float FRAME = 1f / 60f;
    private static final String SCENE = "samples/third-person-yard-3d.json";
    /** One frame's turn at the profile's rate: 120 degrees per second. */
    private static final float TURN_PER_FRAME = GameGenres.SHOOTER_TURN_DEGREES_PER_SECOND / 60f;
    /** How far one frame of walking goes: the latency between a key change and the next physics step. */
    private static final float ONE_FRAME_OF_WALKING = GameGenres.SHOOTER_WALK_SPEED / 60f;
    /** The camera's distance behind the Gunner along the ground: the profile's distance at its tilt. */
    private static final float BEHIND = GameGenres.BEHIND_DISTANCE * (float) Math.cos(Math.toRadians(GameGenres.BEHIND_PITCH));

    private final AssetSource project = AssetSource.directory(new File("../assets"));
    private final Set<Integer> held = new HashSet<>();
    private GameSession session;
    private Scene scene;
    private Entity gunner;
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
        gunner = scene.findEntityByName("Gunner");
        camera = scene.findEntityByName("Main Camera");
        run(30); // settle on the floor; the crates settle too
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

    /** The camera's offset from the Gunner on the ground plane. */
    private Vector3 cameraOffset() {
        Vector3 offset = camera.getTransform().getWorldPosition().cpy().sub(gunner.getTransform().getWorldPosition());
        offset.y = 0f;
        return offset;
    }

    @Test
    void shouldSeeTheGunnerFromBehindAndAboveWithoutAScript() throws IOException {
        start();

        assertTrue(gunner.getComponents(EntityLogicComponent.class).isEmpty(), "no script or graph on the Gunner");
        assertTrue(session.getProfileMessage().startsWith("Playing as Third-person Shooter + 3D."), session.getProfileMessage());
        assertNotNull(gunner.getComponent(FirstPersonMover.class));
        assertNotNull(gunner.getComponent(ProfileShooter.class));
        CameraRigComponent rig = camera.getComponent(CameraRigComponent.class);
        assertInstanceOf(ProfileCameraRig.class, rig);
        assertEquals(CameraRigComponent.Mode.ORBIT, rig.getMode());
        Vector3 offset = cameraOffset();
        assertEquals(0f, offset.x, 5f, "straight behind: " + offset);
        assertEquals(BEHIND, offset.z, 20f, "behind the way the Gunner faces (it faces -Z): " + offset);
        assertTrue(camera.getTransform().getWorldPosition().y > gunner.getTransform().getWorldPosition().y + GameGenres.BEHIND_PIVOT_HEIGHT,
            "above the pivot");
        assertTrue(view().z < -0.9f && view().y < 0f, "looking ahead and a little down: " + view());
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        SceneSerializationException unsaved = assertThrows(SceneSerializationException.class,
            () -> new SceneSerializer(registry).toJson(scene), "the profile's parts are never saved");
        assertTrue(unsaved.getMessage().contains("never saved"), unsaved.getMessage());
    }

    @Test
    void shouldTurnTheGunnerAndTheCameraTogetherAndStepSidewaysWithoutTurning() throws IOException {
        start();
        FirstPersonMover mover = gunner.getComponent(FirstPersonMover.class);

        Vector3 from = position("Gunner");
        hold(Keys.A, 60);
        Vector3 side = position("Gunner").sub(from);
        assertTrue(side.x < -350f, "A steps left: " + side);
        assertEquals(0f, side.z, ONE_FRAME_OF_WALKING + 1f, side.toString());
        assertEquals(0f, mover.getFacingYaw(), 0.01f, "stepping sideways does not turn the Gunner");
        run(1);
        assertEquals(0f, cameraOffset().x, 5f, "the camera stays straight behind: " + cameraOffset());

        hold(Keys.E, 45); // 120 degrees per second for three quarters of a second: 90 degrees to the right
        run(1);
        assertEquals(-90f, mover.getFacingYaw(), 0.5f);
        Vector3 offset = cameraOffset();
        assertEquals(-BEHIND, offset.x, 20f, "now facing +X, the camera is behind it on -X: " + offset);
        assertEquals(0f, offset.z, 20f, offset.toString());
        assertTrue(view().x > 0.9f, "and looks along +X: " + view());

        from = position("Gunner");
        hold(Keys.W, 30);
        assertTrue(position("Gunner").sub(from).x > 150f, "W walks the way it faces, now +X");

        hold(Keys.Q, 45);
        run(1);
        assertEquals(0f, mover.getFacingYaw(), 0.5f, "Q turns it back");
        assertEquals(BEHIND, cameraOffset().z, 20f, "and the camera with it: " + cameraOffset());
        assertEquals(List.of(), session.getRuntimeErrors());
    }

    @Test
    void shouldKnockTheCrateItFacesWithALevelShotFromItsChestOncePerPress() throws IOException {
        start();
        ProfileShooter gun = gunner.getComponent(ProfileShooter.class);
        Vector3 middle = position("Crate Middle");
        Vector3 left = position("Crate Left");
        Vector3 right = position("Crate Right");

        hold(Keys.F, 60); // held for a second: one shot
        assertEquals(1, gun.getShots(), "once per press");
        assertEquals("Crate Middle", gun.getLastHit().map(Entity::getName).orElse("nothing"),
            "the camera looks down from behind, but the shot leaves the chest level and reaches the crate, not the floor");
        assertEquals(1, gun.getKnocks());
        assertTrue(position("Crate Middle").z < middle.z - 100f, "knocked back: " + position("Crate Middle"));
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
