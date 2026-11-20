package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.render.play.FallRespawner;
import io.github.ldev22live.gdxunity.engine.render.play.GroundWalkMover;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileCameraRig;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
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
 * The acceptance test of Platformer + 3D (roadmap M3b.7, slice 2), on real Jolt through the game runtime's {@link GameSession}, which
 * runs the same profile step as editor Play: in {@code platformer-profile-3d.json} the Jumper, with no script or graph, gets a chase
 * camera, running relative to it and jumping, and crosses the floating stones to the goal with keys alone; a fall off the course puts
 * it back on the start platform. Keys come from a fake source the test holds down.
 */
class PlatformerChaseProfileOnJoltTest {
    private static final float FRAME = 1f / 60f;
    private static final String SCENE = "samples/platformer-profile-3d.json";
    /** A default character's centre above what it stands on: half of 80 plus the 30 radius. */
    private static final float STANDING = 70f;
    /** How far inside a platform's edge the autopilot takes off, and how near a point counts as reached. */
    private static final float EDGE_MARGIN = 60f;
    private static final float CLOSE = 25f;
    /** The course in the order it is crossed. */
    private static final List<String> COURSE = List.of("Start Platform", "Stone 1", "Stone 2", "Stone 3", "Goal Platform");

    private final AssetSource project = AssetSource.directory(new File("../assets"));
    private final Set<Integer> held = new HashSet<>();
    private GameSession session;
    private Scene scene;
    private Entity jumper;
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
        jumper = scene.findEntityByName("Jumper");
        camera = scene.findEntityByName("Main Camera");
        run(30); // settle on the start platform
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) {
            session.update(FRAME);
        }
    }

    private Vector3 position() {
        return jumper.getTransform().getWorldPosition();
    }

    /** A course platform's top centre and half sizes in x and z, from its collider. */
    private record Platform(String name, float x, float top, float z, float halfX, float halfZ) {
        boolean holds(Vector3 p) {
            return Math.abs(p.x - x) <= halfX && Math.abs(p.z - z) <= halfZ && Math.abs(p.y - STANDING - top) < 10f;
        }
    }

    private Platform platform(String name) {
        Entity entity = scene.findEntityByName(name);
        Vector3 size = entity.getComponent(BoxCollider3DComponent.class).getSize();
        Vector3 centre = entity.getTransform().getWorldPosition();
        return new Platform(name, centre.x, centre.y + size.y / 2f, centre.z, size.x / 2f, size.z / 2f);
    }

    /** Holds the keys that run toward (x, z): W and S along z (the camera looks along -Z), D and A along x. */
    private void steer(float x, float z) {
        Vector3 p = position();
        held.remove(Keys.W);
        held.remove(Keys.S);
        held.remove(Keys.A);
        held.remove(Keys.D);
        if (p.z > z + CLOSE) {
            held.add(Keys.W);
        } else if (p.z < z - CLOSE) {
            held.add(Keys.S);
        }
        if (p.x < x - CLOSE) {
            held.add(Keys.D);
        } else if (p.x > x + CLOSE) {
            held.add(Keys.A);
        }
    }

    @Test
    void shouldGiveTheJumperTheChaseCameraTheRunningAndTheReturnWithoutAScript() throws IOException {
        start();

        assertTrue(jumper.getComponents(EntityLogicComponent.class).isEmpty(), "no script or graph on the Jumper");
        assertTrue(session.getProfileMessage().startsWith("Playing as Platformer / Side-scroller + 3D."), session.getProfileMessage());
        assertNotNull(jumper.getComponent(GroundWalkMover.class));
        assertEquals(GameGenres.PLATFORMER_3D_FALL_DEPTH, jumper.getComponent(FallRespawner.class).getDepth());
        CameraRigComponent rig = camera.getComponent(CameraRigComponent.class);
        assertInstanceOf(ProfileCameraRig.class, rig);
        assertEquals(GameGenres.CHASE_DISTANCE, rig.getDistance(), 0.01f);
        assertTrue(scene.getPhysics3D().isCharacterGrounded(jumper), "stands on the start platform");
        Vector3 behind = camera.getTransform().getWorldPosition();
        assertTrue(behind.z > position().z + 300f, "the camera is behind the Jumper (it faces -Z): " + behind);
        assertTrue(behind.y > position().y + 200f, "and well above it, to see the landings: " + behind);
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        SceneSerializationException unsaved = assertThrows(SceneSerializationException.class,
            () -> new SceneSerializer(registry).toJson(scene), "the profile's parts are never saved");
        assertTrue(unsaved.getMessage().contains("never saved"), unsaved.getMessage());
    }

    /**
     * The course can be crossed with the profile's speeds and keys alone. An autopilot plays it as a person would: on each platform
     * it runs to the edge nearest the next one and takes off with one press of Space, then steers in the air over the next one.
     */
    @Test
    void shouldCrossTheFloatingStonesToTheGoalWithKeysAlone() throws IOException {
        start();
        GroundWalkMover mover = jumper.getComponent(GroundWalkMover.class);
        FallRespawner respawner = jumper.getComponent(FallRespawner.class);
        List<Platform> course = COURSE.stream().map(this::platform).toList();
        int on = 0;
        int frames = 0;
        float highestRise = 0f;

        while (on < course.size() - 1 && frames < 1200) {
            Platform here = course.get(on);
            Platform next = course.get(on + 1);
            held.remove(Keys.SPACE);
            Vector3 p = position();
            if (next.holds(p) && scene.getPhysics3D().isCharacterGrounded(jumper)) {
                on++;
                continue;
            }
            if (scene.getPhysics3D().isCharacterGrounded(jumper) && here.holds(p)) {
                // Run to the point of this platform nearest the next one, then take off
                float edgeX = clamp(next.x(), here.x() - here.halfX() + EDGE_MARGIN, here.x() + here.halfX() - EDGE_MARGIN);
                float edgeZ = clamp(next.z(), here.z() - here.halfZ() + EDGE_MARGIN, here.z() + here.halfZ() - EDGE_MARGIN);
                steer(edgeX, edgeZ);
                if (Math.abs(p.x - edgeX) <= CLOSE && Math.abs(p.z - edgeZ) <= CLOSE) {
                    held.add(Keys.SPACE);
                }
            } else {
                steer(next.x(), next.z());
                highestRise = Math.max(highestRise, p.y - STANDING - here.top());
            }
            run(1);
            frames++;
        }
        held.clear();
        run(30);

        assertEquals(0, respawner.getRespawns(), "never fell off");
        assertEquals(course.size() - 1, on, "reached the goal in " + frames + " frames, last at " + COURSE.get(on) + " " + position());
        assertTrue(course.get(course.size() - 1).holds(position()), "stands on the goal: " + position());
        assertEquals(course.size() - 1, mover.getJumps(), "one jump per gap");
        assertTrue(highestRise > 200f, "jumps rise more than 2 m: " + highestRise);
        assertEquals(List.of(), session.getRuntimeErrors());
    }

    @Test
    void shouldPutTheJumperBackOnTheStartPlatformAfterAFall() throws IOException {
        start();
        FallRespawner respawner = jumper.getComponent(FallRespawner.class);
        Vector3 begin = respawner.getStart();
        Platform start = platform("Start Platform");

        held.add(Keys.A); // off the start platform's side, into nothing
        float lowest = position().y;
        int frames = 0;
        while (respawner.getRespawns() == 0 && frames < 600) {
            lowest = Math.min(lowest, position().y);
            run(1);
            frames++;
        }
        held.clear();

        assertEquals(1, respawner.getRespawns(), "put back once, after " + frames + " frames");
        assertTrue(lowest < begin.y - GameGenres.PLATFORMER_3D_FALL_DEPTH + 40f, "fell nearly the whole depth first: " + lowest);
        assertTrue(lowest > begin.y - GameGenres.PLATFORMER_3D_FALL_DEPTH - 40f, "and not much further: " + lowest);
        run(60);
        assertTrue(start.holds(position()), "back on the start platform: " + position());
        assertEquals(begin.x, position().x, 1f);
        assertEquals(begin.z, position().z, 1f);
        assertTrue(scene.getPhysics3D().isCharacterGrounded(jumper), "and standing");
        float view = camera.getTransform().getWorldPosition().dst(position());
        assertTrue(view < GameGenres.CHASE_DISTANCE + 150f, "the camera followed it back: " + view);
        assertEquals(1, respawner.getRespawns(), "standing on the start does not count again");
        assertEquals(List.of(), session.getRuntimeErrors());
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
