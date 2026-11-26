package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.Renderable;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer2D;
import io.github.ldev22live.gdxunity.engine.render.ViewDepthOrder;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.render.play.PlaneRunMover;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileCameraRig;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 2.5D hybrid sample (roadmap MC.4) on real Jolt through the game runtime's {@link GameSession}: Kenney 3D scenery (CC0) and
 * ForgeRoom's 2D sprites, played as Platformer + 2.5D with a sprite as the player. It checks the side view (the camera looks along
 * the play plane's normal and follows), the physics (the sprite player stands, runs, jumps onto a 3D model's collider and is stopped
 * by a wall, and never leaves the plane) and the depth order of the sprites seen from that camera, which the scene lists the other
 * way round on purpose.
 */
class HybridSideScrollerOnJoltTest {
    private static final float FRAME = 1f / 60f;
    private static final String SCENE = "samples/hybrid-side-scroller-25d.json";
    private static final File ASSETS = new File("../assets");
    /** A default character's centre above what it stands on: half of 80 plus the 30 radius. */
    private static final float STANDING = 70f;
    /** The capsule's radius: how far its centre stays from a wall it walks into. */
    private static final float RADIUS = 30f;
    /** Jolt lets a resting character sink up to about 2 cm into what it stands on, and stop that far from a wall. */
    private static final float SLOP = 5f;
    /** The Kenney floor tile's top and its half width (2 m at 100 units to the metre). */
    private static final float FLOOR_TOP = 120f;
    private static final float FLOOR_HALF = 100f;
    /** The largest sideways or vertical part of the camera's view direction still counted as looking along the plane's normal. */
    private static final float SIDE_VIEW_TOLERANCE = 0.2f;

    private final AssetSource project = AssetSource.directory(ASSETS);
    private final Set<Integer> held = new HashSet<>();
    private GameSession session;
    private Scene scene;
    private Entity hero;

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

    private Vector3 cameraDirection() {
        return scene.findEntityByName("Main Camera").getTransform().getWorldRotation().transform(new Vector3(0f, 0f, -1f));
    }

    @Test
    void shouldPlayASpritePlayerAmongKenneyScenery() throws IOException {
        start();

        assertTrue(session.getProfileMessage().startsWith("Playing as Platformer / Side-scroller + 2.5D."), session.getProfileMessage());
        assertNotNull(hero.getComponent(SpriteAnimatorComponent.class), "the player is a 2D sprite");
        assertNotNull(hero.getComponent(PlaneRunMover.class));
        assertInstanceOf(ProfileCameraRig.class, scene.findEntityByName("Main Camera").getComponent(CameraRigComponent.class));
        for (String scenery : List.of("Floor Platform", "House Wall", "House Window", "House Door", "Pine 1", "Front Column")) {
            String model = scene.findEntityByName(scenery).getComponent(ModelComponent.class).getModelPath();
            assertTrue(model.startsWith("thirdparty/kenney/"), scenery + " is a Kenney model: " + model);
            assertTrue(new File(ASSETS, model).isFile(), model);
        }
        assertTrue(scene.getPhysics3D().isCharacterGrounded(hero), "stands on the ground");
        assertEquals(STANDING, position().y, SLOP);
    }

    @Test
    void shouldLookAlongThePlaneNormalAndFollowTheSpritePlayer() throws IOException {
        start();
        held.add(Keys.D);
        run(120); // stopped against the floor tile: a following camera trails a running player by its speed over the follow rate

        Vector3 direction = cameraDirection();
        assertTrue(direction.z < -(1f - SIDE_VIEW_TOLERANCE), "the side camera looks at the play plane: " + direction);
        assertEquals(0f, direction.x, SIDE_VIEW_TOLERANCE, "and does not turn along it: " + direction);
        Vector3 camera = scene.findEntityByName("Main Camera").getTransform().getWorldPosition();
        assertEquals(GameGenres.SIDE_CAMERA_DISTANCE, camera.z, 1f, "in front of the play plane");
        assertEquals(position().x, camera.x, 20f, "the side camera followed");
    }

    @Test
    void shouldStopAtTheFloorTileJumpOntoItAndStopAtTheEndWallOnThePlane() throws IOException {
        start();
        float worstDepth = 0f;

        held.add(Keys.D);
        for (int frame = 0; frame < 120; frame++) {
            run(1);
            worstDepth = Math.max(worstDepth, Math.abs(position().z));
        }
        float platformX = scene.findEntityByName("Floor Platform").getTransform().getWorldPosition().x;
        assertEquals(platformX - FLOOR_HALF - RADIUS, position().x, SLOP, "the floor tile's collider stops the sprite player");

        held.clear();
        held.add(Keys.A);
        run(20);
        held.clear();
        held.add(Keys.D);
        held.add(Keys.SPACE);
        for (int frame = 0; frame < 120; frame++) {
            if (frame == 35) {
                held.remove(Keys.D); // over the tile by now: stop running, keep holding Space
            }
            run(1);
            worstDepth = Math.max(worstDepth, Math.abs(position().z));
        }
        assertEquals(1, hero.getComponent(PlaneRunMover.class).getJumps(), "one jump per press");
        assertEquals(FLOOR_TOP + STANDING, position().y, SLOP, "landed on the Kenney floor tile");

        held.clear();
        held.add(Keys.D);
        for (int frame = 0; frame < 240; frame++) {
            run(1);
            worstDepth = Math.max(worstDepth, Math.abs(position().z));
        }
        float wallX = scene.findEntityByName("End Wall").getTransform().getWorldPosition().x;
        assertEquals(wallX - 30f - RADIUS, position().x, SLOP, "ran off the tile and was stopped by the end wall");
        assertEquals(STANDING, position().y, SLOP, "back on the ground");
        assertTrue(worstDepth < 0.01f, "the sprite player never left the play plane: " + worstDepth);
        assertEquals(List.of(), session.getRuntimeErrors());
    }

    @Test
    void shouldDrawTheSpritesFarthestFirstSeenFromTheSideCamera() throws IOException {
        start();
        held.add(Keys.D);
        run(20);

        List<Renderable> primitives = new ArrayList<>();
        List<Renderable> sprites = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            SpriteAnimatorComponent sprite = entity.getComponent(SpriteAnimatorComponent.class);
            if (sprite != null && sprite.needsLoad()) {
                sprite.load(new FileHandle(new File(ASSETS, sprite.getAssetReference().getPath())));
            }
        }
        SceneRenderer2D.collectRenderables(scene.getAllEntities(), primitives, sprites);
        assertEquals(List.of("Front Villager", "Hero", "Back Villager"), names(sprites), "the scene lists them nearest first");

        Entity camera = scene.findEntityByName("Main Camera");
        new ViewDepthOrder().sortBackToFront(sprites, camera.getTransform().getWorldPosition(), cameraDirection());

        assertEquals(List.of("Back Villager", "Hero", "Front Villager"), names(sprites),
            "the 2D pass draws the villager behind the plane first and the one in front of it last");
    }

    private static List<String> names(List<Renderable> renderables) {
        return renderables.stream().map(r -> ((Component) r).getEntity().getName()).toList();
    }
}
