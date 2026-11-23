package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.render.play.GroundWalkMover;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileCameraRig;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.EntityLogicComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The acceptance test of Action RPG + 3D (roadmap M3c.1, TD-088), on real Jolt through the game runtime's {@link GameSession},
 * which runs the same profile step as editor Play: in {@code action-rpg-valley-3d.json}, Hero walks the sculpted valley with no
 * script or graph, the genre and mode supplying the third-person camera and the walking. Keys come from a fake source the test
 * holds down. The game runtime's renderer gives the scene its terrain height fields; without a GL context the test does the same.
 */
class ActionRpgValleyProfileOnJoltTest {
    private static final float FRAME = 1f / 60f;
    private static final String SCENE = "samples/action-rpg-valley-3d.json";
    /** A default character's centre above the ground it stands on: half of 80 plus the 30 radius. */
    private static final float STANDING = 70f;
    /** "Within a step of the ground" (TD-087's test plan): a default character's step height. */
    private static final float WITHIN_A_STEP = 30f;
    /** Jolt's contact margin and the turn's last fraction of a degree. */
    private static final float SLOP = 5f;

    private final AssetSource project = AssetSource.directory(new File("../assets"));
    private final Set<Integer> held = new HashSet<>();
    private TerrainLibrary library;
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
        library = new TerrainLibrary(project);
        scene.setHeightFields(new TerrainHeightFields(library));
        session = new GameSession(project, scene, new ScriptRegistry(), GameSession.CONSOLE);
        session.setInput(held::contains);
        assertEquals(List.of(), session.start(), "the sample plays with its genre and mode alone");
        hero = scene.findEntityByName("Hero");
        camera = scene.findEntityByName("Main Camera");
        run(90); // drop the short way onto the valley floor
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) {
            session.update(FRAME);
        }
    }

    private Vector3 position() {
        return hero.getTransform().getWorldPosition();
    }

    private float groundUnder() {
        Terrain valley = library.get(scene.findEntityByName("Valley").getComponent(TerrainComponent.class).getTerrainPath());
        return valley.heightAt(position().x, position().z);
    }

    /** Holds {@code key} for {@code frames} frames, checking every frame that Hero stays on the valley; returns how far it went. */
    private Vector3 walk(int key, int frames) {
        Vector3 from = position().cpy();
        held.add(key);
        float worst = 0f;
        for (int frame = 0; frame < frames; frame++) {
            run(1);
            worst = Math.max(worst, Math.abs(position().y - STANDING - groundUnder()));
        }
        held.remove(key);
        assertTrue(worst < WITHIN_A_STEP, "stayed on the valley, at worst " + worst + " from standing on it");
        return position().cpy().sub(from);
    }

    @Test
    void shouldCheckTheGroundBeforePlayFromTheProjectsTerrainsAlone() throws IOException {
        // As the launcher does: start before any renderer exists, so the session reads the valley from the project itself
        scene = GameSession.loadScene(project, SCENE);
        session = new GameSession(project, scene, new ScriptRegistry(), GameSession.CONSOLE);
        session.setInput(held::contains);

        assertEquals(List.of(), session.start(), "the valley is under Hero");
        assertTrue(scene.getPhysics3D().hasBody(scene.findEntityByName("Valley")), "the ground was built for the check");
        assertTrue(session.getProfileMessage().startsWith("Playing as Action RPG + 3D."), session.getProfileMessage());
        assertFalse(session.getProfileMessage().contains("not checked"), session.getProfileMessage());
    }

    @Test
    void shouldRefuseToStartWithNothingUnderHero() throws IOException {
        scene = GameSession.loadScene(project, SCENE);
        // Far beyond the valley's edge (it spans 5120 units around the origin)
        scene.findEntityByName("Hero").getTransform().setLocalPosition(new Vector3(9000f, 300f, 9000f));
        session = new GameSession(project, scene, new ScriptRegistry(), GameSession.CONSOLE);
        session.setInput(held::contains);

        List<String> problems = session.start();

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).startsWith("Nothing under Hero to stand on"), problems.get(0));
    }

    @Test
    void shouldGiveHeroTheThirdPersonCameraAndTheWalkingWithoutAScript() throws IOException {
        start();

        assertTrue(hero.getComponents(EntityLogicComponent.class).isEmpty(), "no script or graph on Hero");
        assertTrue(session.getProfileMessage().startsWith("Playing as Action RPG + 3D."), session.getProfileMessage());
        assertNotNull(hero.getComponent(GroundWalkMover.class));
        CameraRigComponent rig = camera.getComponent(CameraRigComponent.class);
        assertInstanceOf(ProfileCameraRig.class, rig);
        assertEquals(CameraRigComponent.Mode.ORBIT, rig.getMode());
        assertTrue(scene.getPhysics3D().isCharacterGrounded(hero), "stands on the valley");
        assertEquals(groundUnder() + STANDING, position().y, WITHIN_A_STEP);
        Vector3 behind = camera.getTransform().getWorldPosition();
        assertTrue(behind.z > position().z + 200f, "the camera is behind Hero (Hero faces -Z): " + behind);
        assertTrue(behind.y > position().y, "and above it: " + behind);
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        SceneSerializationException unsaved = assertThrows(SceneSerializationException.class,
            () -> new SceneSerializer(registry).toJson(scene), "the profile's parts are never saved");
        assertTrue(unsaved.getMessage().contains("never saved"), unsaved.getMessage());
    }

    @Test
    void shouldWalkTheValleyRelativeToTheCameraAndFaceWhereItWalks() throws IOException {
        start();
        GroundWalkMover mover = hero.getComponent(GroundWalkMover.class);

        Vector3 forward = walk(Keys.W, 120);
        assertTrue(forward.z < -500f, "W walks away from the camera, along -Z: " + forward);
        assertEquals(0f, forward.x, 40f, "straight: " + forward);
        assertEquals(0f, mover.getFacingYaw(), 1f, "faces where it walks");

        Vector3 right = walk(Keys.D, 60);
        assertTrue(right.x > 200f, "D walks to the camera's right, along +X: " + right);
        assertEquals(-90f, mover.getFacingYaw(), 1f, "turned to face right");

        Vector3 back = walk(Keys.DOWN, 60);
        assertTrue(back.z > 200f, "the arrows walk too; back is toward the camera: " + back);
        assertTrue(scene.getPhysics3D().isCharacterGrounded(hero));
        assertEquals(List.of(), session.getRuntimeErrors());
    }

    @Test
    void shouldTurnTheCameraWithQAndEAndWalkTheNewWay() throws IOException {
        start();
        CameraRigComponent rig = camera.getComponent(CameraRigComponent.class);
        float yaw = rig.getYaw();

        held.add(Keys.Q);
        run(45); // 120 degrees per second for three quarters of a second: 90 degrees to the left
        held.clear();
        assertEquals(yaw + 90f, rig.getYaw(), 3f, "Q turns the camera left");
        Vector3 view = camera.getTransform().getWorldPosition().cpy().sub(position());
        assertTrue(view.x > 200f, "the camera swung round to Hero's +X side, looking along -X: " + view);

        Vector3 forward = walk(Keys.W, 60);
        assertTrue(forward.x < -200f, "W now walks along -X, away from the turned camera: " + forward);
        assertEquals(0f, forward.z, 40f, forward.toString());

        held.add(Keys.E);
        run(45);
        held.clear();
        assertEquals(yaw, rig.getYaw(), 3f, "E turns it back");
    }

    @Test
    void shouldJumpOncePerPressAndBeStoppedByTheBoulder() throws IOException {
        start();
        GroundWalkMover mover = hero.getComponent(GroundWalkMover.class);
        float before = position().y;

        held.add(Keys.SPACE);
        float highest = before;
        for (int frame = 0; frame < 120; frame++) {
            run(1);
            highest = Math.max(highest, position().y);
        }
        held.clear();
        assertEquals(1, mover.getJumps(), "holding Space jumps once, not again on landing");
        assertTrue(highest > before + 120f, "rose: " + (highest - before));
        assertTrue(scene.getPhysics3D().isCharacterGrounded(hero), "landed");

        // Walk at the boulder from straight in front of it (camera behind Hero, so W walks along -Z)
        Vector3 boulder = scene.findEntityByName("Boulder").getTransform().getWorldPosition();
        scene.getPhysics3D().teleportCharacter(hero, new Vector3(boulder.x, groundAt(boulder.x, boulder.z + 400f) + STANDING + 5f,
            boulder.z + 400f));
        run(30);
        held.add(Keys.W);
        run(150);
        held.clear();
        float stop = boulder.z + 80f + 30f; // the boulder's near face plus Hero's radius
        assertEquals(stop, position().z, SLOP + 10f, "stopped against the boulder: " + position());
        assertEquals(List.of(), session.getRuntimeErrors());
        assertEquals(GameGenres.THIRD_PERSON_DISTANCE, camera.getComponent(CameraRigComponent.class).getDistance(), 0.01f);
    }

    private float groundAt(float x, float z) {
        Terrain valley = library.get(scene.findEntityByName("Valley").getComponent(TerrainComponent.class).getTerrainPath());
        return valley.heightAt(x, z);
    }
}
