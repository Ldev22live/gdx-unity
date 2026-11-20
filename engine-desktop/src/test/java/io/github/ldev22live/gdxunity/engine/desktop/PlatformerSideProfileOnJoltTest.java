package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.render.play.PlaneRunMover;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileCameraRig;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The acceptance test of Platformer + 2.5D (roadmap M3b.3, TD-088), on real Jolt through the game runtime's {@link GameSession},
 * which runs the same profile step as editor Play. The sample {@code platformer-profile-25d.json} has no scripts: its genre and mode
 * supply everything. Keys come from a fake source the test holds down.
 */
class PlatformerSideProfileOnJoltTest {
    private static final float FRAME = 1f / 60f;
    private static final String SCENE = "samples/platformer-profile-25d.json";
    /** A default character's centre above what it stands on: half of 80 plus the 30 radius. */
    private static final float STANDING = 70f;
    /** The capsule's radius: how far its centre stays from a wall it walks into. */
    private static final float RADIUS = 30f;
    /** Jolt lets a resting character sink up to about 2 cm into what it stands on, and stop that far from a wall. */
    private static final float SLOP = 5f;

    private final AssetSource project = AssetSource.directory(new File("../assets"));
    private final Set<Integer> held = new HashSet<>();
    private GameSession session;
    private Scene scene;
    private Entity runner;

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
        runner = scene.findEntityByName("Runner");
        run(30);
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) {
            session.update(FRAME);
        }
    }

    private Vector3 position() {
        return runner.getTransform().getWorldPosition();
    }

    private float rightEdgeLimit(String solid, float width) {
        return scene.findEntityByName(solid).getTransform().getWorldPosition().x - width / 2f - RADIUS;
    }

    @Test
    void shouldGiveThePlayerTheSideCameraAndTheRunningWithoutAScript() throws IOException {
        start();

        assertTrue(session.getProfileMessage().startsWith("Playing as Platformer / Side-scroller + 2.5D."), session.getProfileMessage());
        assertNotNull(runner.getComponent(PlaneRunMover.class));
        Entity camera = scene.findEntityByName("Main Camera");
        assertInstanceOf(ProfileCameraRig.class, camera.getComponent(CameraRigComponent.class));
        assertTrue(scene.getPhysics3D().isCharacterGrounded(runner), "stands on the ground");
        assertEquals(STANDING, position().y, SLOP);
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        SceneSerializationException unsaved = assertThrows(SceneSerializationException.class,
            () -> new SceneSerializer(registry).toJson(scene), "the profile's parts are never saved");
        assertTrue(unsaved.getMessage().contains("never saved"), unsaved.getMessage());
    }

    @Test
    void shouldRunAlongThePlaneAndStopAtTheLevel() throws IOException {
        start();

        held.add(Keys.D);
        run(120);

        float limit = rightEdgeLimit("Low Platform", 300f);
        assertEquals(limit, position().x, SLOP, "ran right and stopped against the low platform's side");
        assertEquals(0f, position().z, 0.01f, "on the play plane");
        Vector3 camera = scene.findEntityByName("Main Camera").getTransform().getWorldPosition();
        assertEquals(position().x, camera.x, 20f, "the side camera followed");
        assertEquals(GameGenres.SIDE_CAMERA_DISTANCE, camera.z, 1f, "in front of the play plane");

        held.clear();
        held.add(Keys.LEFT);
        run(60);
        assertTrue(position().x < limit - 200f, "the arrows run too: " + position());
    }

    @Test
    void shouldJumpOncePerPressOntoAPlatform() throws IOException {
        start();
        held.add(Keys.D);
        run(90);
        float before = position().y;

        held.add(Keys.SPACE);
        float highest = before;
        for (int frame = 0; frame < 120; frame++) {
            if (frame == 30) {
                held.remove(Keys.D); // over the platform by now: stop running, keep holding Space
            }
            run(1);
            highest = Math.max(highest, position().y);
        }

        PlaneRunMover mover = runner.getComponent(PlaneRunMover.class);
        assertEquals(1, mover.getJumps(), "holding Space jumps once, not again on landing");
        assertTrue(highest > before + 150f, "rose: " + highest);
        assertEquals(120f + STANDING, position().y, SLOP, "landed on the low platform (top 120)");
        assertTrue(scene.getPhysics3D().isCharacterGrounded(runner));
    }

    @Test
    void shouldStopAtTheWallAndStayOnThePlaneAgainstTheAngledCrate() throws IOException {
        start();
        scene.getPhysics3D().teleportCharacter(runner, new Vector3(1550f, STANDING + 5f, 0f));
        held.add(Keys.D);
        run(120);
        assertEquals(rightEdgeLimit("Jump Wall", 60f), position().x, SLOP, "the wall stops the runner");

        scene.getPhysics3D().teleportCharacter(runner, new Vector3(2050f, STANDING + 5f, 0f));
        float worstDepth = 0f;
        for (int frame = 0; frame < 120; frame++) {
            run(1);
            worstDepth = Math.max(worstDepth, Math.abs(position().z));
        }
        assertTrue(position().x < 2300f, "the crate stops the runner: " + position());
        assertTrue(worstDepth < 0.01f, "the angled face did not push the runner off the plane: " + worstDepth);
        assertEquals(List.of(), session.getRuntimeErrors());
    }
}
