package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.render.play.GroundWalkMover;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileCameraRig;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The acceptance test of Action RPG + 2.5D (roadmap M3c.3, TD-088), validated on its own and not inferred from 3D: on real Jolt through
 * the game runtime's {@link GameSession}, in {@code action-rpg-iso-25d.json}, a walled courtyard with no scripts. Hero starts at the
 * scene's spawn point (M3c.2), an isometric camera follows it without turning, and the move keys walk across the ground relative to
 * the screen. Keys come from a fake source the test holds down.
 */
class ActionRpgIsometricProfileOnJoltTest {
    private static final float FRAME = 1f / 60f;
    private static final String SCENE = "samples/action-rpg-iso-25d.json";
    /** A default character's centre above what it stands on: half of 80 plus the 30 radius. */
    private static final float STANDING = 70f;
    private static final float RADIUS = 30f;
    /** Jolt's contact margin. */
    private static final float SLOP = 5f;
    /** The sample's spawn point, on the ground. */
    private static final float SPAWN_X = -600f;
    private static final float SPAWN_Z = 600f;

    private final AssetSource project = AssetSource.directory(new File("../assets"));
    private final Set<Integer> held = new HashSet<>();
    private GameSession session;
    private Scene scene;
    private Entity hero;
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
        hero = scene.findEntityByName("Hero");
        camera = scene.findEntityByName("Main Camera");
        run(30);
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) {
            session.update(FRAME);
        }
    }

    private Vector3 position() {
        return hero.getTransform().getWorldPosition();
    }

    private Vector3 walk(int key, int frames) {
        Vector3 from = position().cpy();
        held.add(key);
        run(frames);
        held.remove(key);
        return position().cpy().sub(from);
    }

    @Test
    void shouldStartAtTheSpawnPointUnderAnIsometricCameraWithoutAScript() throws IOException {
        start();

        assertTrue(hero.getComponents(EntityLogicComponent.class).isEmpty(), "no script or graph on Hero");
        String message = session.getProfileMessage();
        assertTrue(message.startsWith("Playing as Action RPG + 2.5D."), message);
        assertTrue(message.contains("Hero starts at Spawn Point."), message);
        assertEquals(SPAWN_X, position().x, SLOP, "at the spawn point, not where Hero stands in the scene");
        assertEquals(SPAWN_Z, position().z, SLOP);
        assertEquals(STANDING, position().y, SLOP, "standing on the courtyard");
        assertNotNull(hero.getComponent(GroundWalkMover.class));
        CameraRigComponent rig = camera.getComponent(CameraRigComponent.class);
        assertInstanceOf(ProfileCameraRig.class, rig);
        assertEquals(CameraRigComponent.Mode.ORBIT, rig.getMode());
        assertEquals(ProfileCameraRig.ISOMETRIC_YAW_DEGREES, rig.getYaw(), 0.01f);
        assertEquals(-GameGenres.ISOMETRIC_PITCH, rig.getPitch(), 0.01f);
        Vector3 eye = camera.getTransform().getWorldPosition();
        assertTrue(eye.y > position().y + 600f, "high above Hero: " + eye);
        assertTrue(eye.x > position().x + 400f && eye.z > position().z + 400f, "diagonally behind it: " + eye);
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        SceneSerializationException unsaved = assertThrows(SceneSerializationException.class,
            () -> new SceneSerializer(registry).toJson(scene), "the profile's parts are never saved");
        assertTrue(unsaved.getMessage().contains("never saved"), unsaved.getMessage());
    }

    @Test
    void shouldWalkRelativeToTheScreenAndNeverTurnTheCamera() throws IOException {
        start();
        CameraRigComponent rig = camera.getComponent(CameraRigComponent.class);

        // The camera looks diagonally (yaw 45): up the screen is along (-1, 0, -1)
        Vector3 up = walk(Keys.W, 60);
        assertTrue(up.x < -150f && up.z < -150f, "W walks up the screen: " + up);
        assertEquals(up.x, up.z, 20f, "diagonally across the ground: " + up);

        Vector3 right = walk(Keys.D, 60);
        assertTrue(right.x > 150f && right.z < -150f, "D walks right on the screen, along (1, 0, -1): " + right);

        held.add(Keys.Q);
        run(60);
        held.clear();
        assertEquals(ProfileCameraRig.ISOMETRIC_YAW_DEGREES, rig.getYaw(), 0.01f, "the turn keys do not turn the isometric camera");
        Vector3 eye = camera.getTransform().getWorldPosition();
        Vector3 pivot = position().add(0f, GameGenres.ISOMETRIC_PIVOT_HEIGHT, 0f);
        assertEquals(GameGenres.ISOMETRIC_DISTANCE, eye.dst(pivot), 5f, "the camera followed Hero");
        assertEquals(List.of(), session.getRuntimeErrors());
    }

    @Test
    void shouldBeStoppedByTheCourtyardWallAndJumpOncePerPress() throws IOException {
        start();
        scene.getPhysics3D().teleportCharacter(hero, new Vector3(500f, STANDING + 5f, 800f));
        run(20);

        walk(Keys.D, 150);
        float wallFace = scene.findEntityByName("East Wall").getTransform().getWorldPosition().x - 50f;
        assertEquals(wallFace - RADIUS, position().x, SLOP, "the east wall stops Hero: " + position());

        float before = position().y;
        held.add(Keys.SPACE);
        float highest = before;
        for (int frame = 0; frame < 120; frame++) {
            run(1);
            highest = Math.max(highest, position().y);
        }
        held.clear();
        assertEquals(1, hero.getComponent(GroundWalkMover.class).getJumps(), "holding Space jumps once");
        assertTrue(highest > before + 120f, "rose: " + (highest - before));
        assertTrue(scene.getPhysics3D().isCharacterGrounded(hero), "landed");
        assertEquals(List.of(), session.getRuntimeErrors());
    }
}
