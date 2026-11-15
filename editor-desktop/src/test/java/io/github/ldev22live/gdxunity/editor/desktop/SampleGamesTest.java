package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphAssets;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphExecutionTrace;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphPlayModeExtension;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphHost;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphInstance;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphRuntimeException;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptLanguage;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.scripting.ScriptManager;
import io.github.ldev22live.gdxunity.engine.desktop.GameScripts;
import io.github.ldev22live.gdxunity.engine.desktop.GameSession;
import io.github.ldev22live.gdxunity.engine.render.camera.GameCameraRig;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.audio.EngineProfile;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioListenerComponent;
import io.github.ldev22live.gdxunity.engine.scene.audio.EngineSoundComponent;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.badlogic.gdx.utils.JsonReader;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshGeometry3D;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plays every sample game in {@code assets/samples} without a window: the scene file is loaded with the editor's
 * serializers, the sample scripts are compiled and bound as Play mode does, and key presses are simulated.
 * Drawing is not covered here (needs a display; see the {@code --smoke-test --scene ... --play} launcher option).
 */
class SampleGamesTest {

    /** Tests run in the module folder; the project's assets are one level up. */
    private static final File PROJECT = new File("../assets");
    private static final float FRAME = 1f / 60f;
    private static final List<String> SAMPLES = List.of("platformer-2d", "breakout-2d", "shooter-2d",
        "physics-sandbox-2d", "collector-3d", "runner-3d", "hopper-3d", "ruins-runner-25d", "three-heroes-25d",
        "grapple-gorge-25d", "racing-3d", "arena-fps-3d", "fighter-25d", "puzzle-3d", "physics-sandbox-3d", "character-playground-3d", "physics-joints-3d", "blockworld-3d", "obby-3d", "materials-3d", "terrain-3d");

    @TempDir
    static Path compiledScripts;

    private static ScriptManager scripts;
    private static Input previousInput;
    private static final Set<Integer> held = new HashSet<>();
    private static final Set<Integer> justPressed = new HashSet<>();

    @BeforeAll
    static void compileSampleScripts() {
        GdxNativesLoader.load();
        previousInput = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "isKeyPressed" -> held.contains((Integer) args[0]);
                case "isKeyJustPressed" -> justPressed.contains((Integer) args[0]);
                default -> method.getReturnType() == boolean.class ? false
                    : method.getReturnType() == int.class ? 0
                    : method.getReturnType() == float.class ? 0f
                    : method.getReturnType() == long.class ? 0L : null;
            });
        scripts = new ScriptManager(new File(PROJECT, "scripts"), compiledScripts.toFile());
        assertTrue(scripts.compileAll(), () -> "sample scripts must compile: " + scripts.getLatestDiagnostics());
    }

    @AfterAll
    static void restoreInput() {
        Gdx.input = previousInput;
    }

    @BeforeEach
    void releaseKeys() {
        held.clear();
        justPressed.clear();
    }

    private static Scene load(String sample) throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        EditorApplication.registerComponentSerializers(registry);
        String json = Files.readString(new File(PROJECT, "samples/" + sample + ".json").toPath());
        Scene scene = new SceneSerializer(registry).fromJson(json);
        List<String> errors = scripts.initializeSceneScripts(scene);
        assertTrue(errors.isEmpty(), () -> sample + ": " + errors);
        for (Entity entity : scene.getAllEntities()) {
            for (GraphScriptComponent graph : entity.getComponents(GraphScriptComponent.class)) {
                graph.bind(new GraphInstance(GraphSerializer.fromJson(Files.readString(new File(PROJECT,
                    graph.getGraphPath()).toPath())), NodeRegistry.createDefault(), entity, new GraphHost() {
                    @Override
                    public void log(GraphInstance instance, String message) { }

                    @Override
                    public void error(GraphInstance instance, GraphRuntimeException error) {
                        throw error;
                    }
                }));
            }
        }
        return scene;
    }

    /** Runs {@code frames} frames; keys in {@code press} count as just pressed in the first frame. */
    private static void run(Scene scene, int frames, int... press) {
        for (int key : press) {
            justPressed.add(key);
            held.add(key);
        }
        for (int i = 0; i < frames; i++) {
            scene.update(FRAME);
            justPressed.clear();
        }
        for (int key : press) {
            held.remove(key);
        }
    }

    private static Entity entity(Scene scene, String name) {
        Entity entity = scene.findEntityByName(name);
        assertNotNull(entity, name);
        return entity;
    }

    private static String text(Scene scene, String name) {
        return entity(scene, name).getComponent(TextComponent.class).getText();
    }

    private static float x(Scene scene, String name) {
        return entity(scene, name).getTransform().getWorldPosition().x;
    }

    private static float y(Scene scene, String name) {
        return entity(scene, name).getTransform().getWorldPosition().y;
    }

    private static float z(Scene scene, String name) {
        return entity(scene, name).getTransform().getWorldPosition().z;
    }

    /** Moves an entity before the first frame; the 2.5D game scripts read their start position from it. */
    private static void place(Scene scene, String name, float x, float y) {
        entity(scene, name).getTransform().setLocalPosition(x, y, 0f);
    }

    private static boolean active(Scene scene, String name) {
        return entity(scene, name).isActiveSelf();
    }

    @Test
    void shouldShipAtLeastThreePlayable2DAnd3DAnd25DSamplesWithMatchingCameras() throws IOException {
        int twoD = 0;
        int threeD = 0;
        int twoAndAHalfD = 0;
        for (String sample : SAMPLES) {
            Scene scene = load(sample);
            CameraComponent camera = GameCameraRig.findActiveCamera(scene);
            assertNotNull(camera, sample + " needs a camera");
            if (sample.endsWith("-2d")) {
                assertEquals(CameraComponent.Projection.ORTHOGRAPHIC, camera.getProjection(), sample);
                twoD++;
            } else {
                assertEquals(CameraComponent.Projection.PERSPECTIVE, camera.getProjection(), sample);
                if (sample.endsWith("-25d")) {
                    twoAndAHalfD++;
                } else {
                    threeD++;
                }
            }
            run(scene, 10);
            scene.dispose();
        }
        assertTrue(twoD >= 3 && threeD >= 3 && twoAndAHalfD >= 3,
            twoD + " 2D, " + threeD + " 3D and " + twoAndAHalfD + " 2.5D samples");
    }

    @Test
    void shouldRunJumpAndCollectCoinsInThePlatformer() throws IOException {
        Scene scene = load("platformer-2d");
        run(scene, 30);
        assertEquals(60f, y(scene, "Player"), 0.5f, "stands on the ground");

        held.add(Keys.RIGHT);
        while (x(scene, "Player") < 250f) {
            run(scene, 1);
        }
        run(scene, 40, Keys.SPACE);
        held.clear();
        run(scene, 60);

        assertEquals("Coins: 1 / 8", text(scene, "Score Text"));
        assertTrue(y(scene, "Player") > 150f, "landed on the ledge, y = " + y(scene, "Player"));
        run(scene, 1, Keys.R);
        assertEquals("Coins: 0 / 8", text(scene, "Score Text"));
        assertEquals(80f, x(scene, "Player"), 0.01f);
        scene.dispose();
    }

    @Test
    void shouldLaunchTheBallAndBreakBricks() throws IOException {
        Scene scene = load("breakout-2d");
        run(scene, 10);
        assertEquals(x(scene, "Paddle"), x(scene, "Ball"), 0.01f, "ball waits on the paddle");

        run(scene, 600, Keys.SPACE);

        assertNotEquals("Score: 0", text(scene, "Score Text"));
        long bricksLeft = scene.getAllEntities().stream()
            .filter(e -> e.getName().startsWith("Brick") && e.isActiveSelf()).count();
        assertTrue(bricksLeft < 40, bricksLeft + " bricks left");
        run(scene, 1, Keys.R);
        assertEquals("Score: 0", text(scene, "Score Text"));
        assertEquals(40, scene.getAllEntities().stream()
            .filter(e -> e.getName().startsWith("Brick") && e.isActiveSelf()).count());
        scene.dispose();
    }

    @Test
    void shouldFireAtEnemiesInTheShooter() throws IOException {
        Scene scene = load("shooter-2d");
        float startX = x(scene, "Ship");
        held.add(Keys.SPACE);
        held.add(Keys.LEFT);
        run(scene, 20);
        held.remove(Keys.LEFT);

        assertTrue(x(scene, "Ship") < startX, "ship moved left");
        assertTrue(scene.getAllEntities().stream().anyMatch(e -> e.getName().startsWith("Bullet") && e.isActiveSelf()),
            "bullets in flight");
        for (int i = 0; i < 1200 && text(scene, "Score Text").equals("Score: 0"); i++) {
            held.clear();
            held.add(Keys.SPACE);
            // Follow the lowest enemy so shots connect
            scene.getAllEntities().stream().filter(e -> e.getName().startsWith("Enemy") && e.isActiveSelf())
                .min((a, b) -> Float.compare(a.getTransform().getLocalPosition().y, b.getTransform().getLocalPosition().y))
                .ifPresent(enemy -> held.add(enemy.getTransform().getLocalPosition().x < x(scene, "Ship") ? Keys.LEFT : Keys.RIGHT));
            run(scene, 1);
        }
        assertNotEquals("Score: 0", text(scene, "Score Text"));
        scene.dispose();
    }

    @Test
    void shouldDropCratesOntoTheGroundInThePhysicsSandbox() throws IOException {
        Scene scene = load("physics-sandbox-2d");
        PhysicsSystem2D physics = new PhysicsSystem2D(scene);
        float bouncyStart = y(scene, "Bouncy Block 1");
        for (int i = 0; i < 180; i++) {
            physics.update(FRAME);
            scene.update(FRAME);
        }
        assertTrue(y(scene, "Bouncy Block 1") < bouncyStart - 50f, "falls under gravity");
        for (Entity entity : scene.getAllEntities()) {
            if (entity.getName().startsWith("Crate")) {
                assertTrue(y(scene, entity.getName()) > 40f, entity.getName() + " rests above the ground");
            }
        }
        physics.dispose();
        scene.dispose();
    }

    @Test
    void shouldDropBallsAndSettleACratePyramidInThe3DPhysicsSandbox() throws IOException {
        File file = new File(PROJECT, "samples/physics-sandbox-3d.json");
        GameSession session = new GameSession(PROJECT, GameSession.loadScene(file),
            new GameScripts(compiledScripts.toFile(), getClass().getClassLoader()), GameSession.CONSOLE);
        assertEquals(List.of(), session.start());
        assertNotNull(session.getPhysicsSystem3D(), "the sample has 3D colliders");
        Scene scene = session.getScene();
        float highest = y(scene, "Ball 8");
        for (int i = 0; i < 360; i++) {
            session.update(FRAME);
        }

        assertTrue(y(scene, "Ball 1") > 30f && y(scene, "Ball 1") < 60f, "a ball rests on the floor, radius 40: " + y(scene, "Ball 1"));
        assertTrue(y(scene, "Ball 8") < highest - 100f, "the bouncy ball fell from " + highest);
        for (Entity entity : scene.getAllEntities()) {
            if (entity.getName().startsWith("Crate")) {
                assertTrue(y(scene, entity.getName()) > 30f, entity.getName() + " is on or above the floor");
                assertTrue(Math.abs(x(scene, entity.getName())) < 800f, entity.getName() + " stayed inside the walls");
            }
        }
        assertTrue(session.getRuntimeErrors().isEmpty(), () -> session.getRuntimeErrors().toString());
        session.dispose();
    }

    /** Plays the 3D sandbox with its SandboxLauncher script (TD-048) and the camera aimed at {@code target} from {@code distance} units away (it must stay inside the walls). */
    private GameSession aimedSandbox(String target, float distance) throws IOException {
        File file = new File(PROJECT, "samples/physics-sandbox-3d.json");
        GameSession session = new GameSession(PROJECT, GameSession.loadScene(file),
            new GameScripts(compiledScripts.toFile(), getClass().getClassLoader()), GameSession.CONSOLE);
        assertEquals(List.of(), session.start());
        for (int i = 0; i < 360; i++) {
            session.update(FRAME);
        }
        Scene scene = session.getScene();
        Entity camera = scene.findEntityByName("Main Camera");
        camera.getTransform().setLocalPosition(x(scene, target), y(scene, target), scene.findEntityByName(target).getTransform().getWorldPosition().z + distance);
        camera.getTransform().setLocalRotation(new Quaternion());
        return session;
    }

    private void press(GameSession session, int key) {
        justPressed.add(key);
        session.update(FRAME);
        justPressed.clear();
    }

    /** Plays the character playground (TD-049) with the hero put at {@code (x, z)} before the game starts. */
    private GameSession playground(float x, float z) throws IOException {
        File file = new File(PROJECT, "samples/character-playground-3d.json");
        Scene scene = GameSession.loadScene(file);
        scene.findEntityByName("Hero").getTransform().setLocalPosition(x, 75f, z);
        GameSession session = new GameSession(PROJECT, scene, new GameScripts(compiledScripts.toFile(), getClass().getClassLoader()),
            GameSession.CONSOLE);
        assertEquals(List.of(), session.start());
        for (int i = 0; i < 60; i++) {
            session.update(FRAME); // lands
        }
        return session;
    }

    @Test
    void shouldWalkAndJumpTheCharacterInThePlaygroundFromTheKeyboard() throws IOException {
        GameSession session = playground(-700f, 900f);
        Scene scene = session.getScene();
        Entity hero = scene.findEntityByName("Hero");
        assertTrue(session.getPhysicsSystem3D().isCharacterGrounded(hero), "standing after the landing");
        float startX = x(scene, "Hero");

        held.add(Keys.D);
        for (int i = 0; i < 60; i++) {
            session.update(FRAME);
        }
        held.clear();
        assertEquals(300f, x(scene, "Hero") - startX, 15f, "a second of D walks 3 m");

        float restY = y(scene, "Hero");
        press(session, Keys.SPACE);
        float apex = restY;
        for (int i = 0; i < 90; i++) {
            session.update(FRAME);
            apex = Math.max(apex, y(scene, "Hero"));
        }
        assertEquals(450f * 450f / (2f * 981f), apex - restY, 8f, "the jump rises about a meter");
        assertTrue(session.getPhysicsSystem3D().isCharacterGrounded(hero), "and comes down");
        assertTrue(scene.findEntityByName("Help Text").getComponent(TextComponent.class).getText().endsWith("On the ground"));
        assertTrue(session.getRuntimeErrors().isEmpty(), () -> session.getRuntimeErrors().toString());
        session.dispose();
    }

    @Test
    void shouldChaseTheHeroWithTheCameraRigOfThePlayground() throws IOException {
        GameSession session = playground(-700f, 900f);
        Scene scene = session.getScene();
        held.add(Keys.D);
        for (int i = 0; i < 120; i++) {
            session.update(FRAME);
        }
        held.clear();
        for (int i = 0; i < 60; i++) {
            session.update(FRAME);
        }

        Vector3 camera = scene.findEntityByName("Main Camera").getTransform().getWorldPosition();
        Vector3 hero = scene.findEntityByName("Hero").getTransform().getWorldPosition();
        assertTrue(hero.x > -300f, "the hero walked: " + hero);
        assertEquals(hero.x, camera.x, 2f, "the camera caught up after the hero stopped");
        assertEquals(hero.y + 420f, camera.y, 2f);
        assertEquals(hero.z + 560f, camera.z, 2f);
        assertTrue(session.getRuntimeErrors().isEmpty(), () -> session.getRuntimeErrors().toString());
        session.dispose();
    }

    @Test
    void shouldSwingTheDoorPendulumChainAndSliderOfTheJointsSample() throws IOException {
        GameSession session = new GameSession(PROJECT, GameSession.loadScene(new File(PROJECT, "samples/physics-joints-3d.json")),
            new GameScripts(compiledScripts.toFile(), getClass().getClassLoader()), GameSession.CONSOLE);
        assertEquals(List.of(), session.start());
        Scene scene = session.getScene();
        PhysicsSystem3D physics = session.getPhysicsSystem3D();
        float pendulumStart = x(scene, "Pendulum Bob");
        float crateStart = x(scene, "Ride Crate");
        boolean pendulumCrossedTheMiddle = false;

        for (int i = 0; i < 60 * 8; i++) {
            session.update(FRAME);
            pendulumCrossedTheMiddle |= x(scene, "Pendulum Bob") < -550f;
        }

        assertEquals(100f, physics.getJointPosition(entity(scene, "Door")), 3f, "the door's motor opened it to its 100 degree limit");
        assertTrue(pendulumStart > -550f && pendulumCrossedTheMiddle, "the pendulum swung through the middle from " + pendulumStart);
        assertEquals(700f, physics.getJointPosition(entity(scene, "Slider Platform")), 8f, "the platform slid to the end of its rail");
        assertTrue(x(scene, "Ride Crate") - crateStart > 500f, "the crate rode along on the platform: " + x(scene, "Ride Crate"));
        Entity first = entity(scene, "Chain Link 1");
        Vector3 hinge = new Vector3(-40f, 0f, 0f).mul(first.getTransform().getWorldMatrix());
        assertEquals(0f, hinge.dst(new Vector3(62f, 640f, 0f)), 4f, "the chain's first link is still on its post");
        Entity last = entity(scene, "Chain Link 5");
        assertTrue(last.getTransform().getWorldPosition().y < 600f, "the chain fell from its horizontal start: " + at(last));
        assertTrue(session.getRuntimeErrors().isEmpty(), () -> session.getRuntimeErrors().toString());
        session.dispose();
    }

    /**
     * The terrain model's own triangles, read from the glTF file's embedded buffer (a headless test cannot load the model's GL
     * mesh, which the renderer reads in the running game), at the scene's 100 units per metre.
     */
    private static MeshGeometry3D terrainTriangles() throws IOException {
        JsonValue gltf = new JsonReader().parse(Files.readString(new File(PROJECT, "models/physics-mesh-terrain.gltf").toPath()));
        String uri = gltf.get("buffers").get(0).getString("uri");
        ByteBuffer blob = ByteBuffer.wrap(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1))).order(ByteOrder.LITTLE_ENDIAN);
        JsonValue views = gltf.get("bufferViews");
        int positionStart = views.get(0).getInt("byteOffset");
        int positionCount = gltf.get("accessors").get(0).getInt("count") * 3;
        float[] positions = new float[positionCount];
        for (int i = 0; i < positionCount; i++) {
            positions[i] = blob.getFloat(positionStart + i * 4) * 100f;
        }
        int indexStart = views.get(2).getInt("byteOffset");
        int[] indices = new int[gltf.get("accessors").get(2).getInt("count")];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = blob.getInt(indexStart + i * 4);
        }
        return new MeshGeometry3D(positions, indices);
    }

    @Test
    void shouldRollBallsOffTheMeshTerrainIntoTheBowlAndLandBoxesAndHullRocksOnIt() throws IOException {
        GameSession session = new GameSession(PROJECT, GameSession.loadScene(new File(PROJECT, "samples/physics-mesh-3d.json")),
            new GameScripts(compiledScripts.toFile(), getClass().getClassLoader()), GameSession.CONSOLE);
        assertEquals(List.of(), session.start());
        Scene scene = session.getScene();
        MeshGeometry3D terrain = terrainTriangles();
        // The game's renderer reads the model; here the terrain triangles stand in, and the rocks use the real primitive reader
        ColliderGeometry primitives = new ColliderGeometry(null);
        scene.setColliderGeometry(entity -> entity.getName().equals("Terrain") ? terrain : primitives.geometryOf(entity));

        for (int i = 0; i < 60 * 10; i++) {
            session.update(FRAME);
        }

        // The bowl is centered at x 450 and its floor is 100 below the ground: a box or ball at rest there is near y -75 or -80
        for (String name : List.of("Box 1", "Box 2", "Box 3")) {
            Vector3 box = at(entity(scene, name));
            assertTrue(Math.abs(box.x - 450f) < 200f && box.y < -40f && box.y > -110f, name + " rests in the bowl: " + box);
        }
        int ballsInTheBowl = 0;
        for (int i = 1; i <= 4; i++) {
            Vector3 ball = at(entity(scene, "Ball " + i));
            assertTrue(ball.y > -110f, "Ball " + i + " did not fall through the terrain: " + ball);
            assertTrue(ball.x > 0f, "Ball " + i + " rolled off the plateau edge: " + ball);
            if (Math.abs(ball.x - 450f) < 220f && ball.y < 0f) {
                ballsInTheBowl++;
            }
        }
        assertTrue(ballsInTheBowl >= 1, "at least one ball reached the bowl");
        for (int i = 1; i <= 2; i++) {
            Vector3 rock = at(entity(scene, "Hull Rock " + i));
            assertTrue(rock.y > -20f && rock.y < 130f, "Hull Rock " + i + " rests on the ramp, not through it: " + rock);
        }
        assertTrue(session.getRuntimeErrors().isEmpty(), () -> session.getRuntimeErrors().toString());
        session.dispose();
    }

    @Test
    void shouldShowCharactersWhoseModelsCarryTheAnimationTheyPlay() throws IOException {
        Scene scene = load("character-creator-3d");
        int characters = 0;
        for (Entity entity : scene.getAllEntities()) {
            ModelComponent model = entity.getComponent(ModelComponent.class);
            if (model == null) {
                continue;
            }
            characters++;
            File file = new File(PROJECT, model.getModelPath());
            assertTrue(file.isFile(), entity.getName() + ": " + file);
            JsonValue gltf = new JsonReader().parse(Files.readString(file.toPath()));
            List<String> clips = new ArrayList<>();
            for (JsonValue animation = gltf.get("animations").child; animation != null; animation = animation.next) {
                clips.add(animation.getString("name"));
            }
            assertTrue(clips.contains(model.getAnimation()), entity.getName() + " plays " + model.getAnimation() + " but its model has " + clips);
            assertEquals(53, gltf.get("skins").get(0).get("joints").size, entity.getName());
        }
        assertEquals(3, characters);
        scene.dispose();
    }

    private static Vector3 at(Entity entity) {
        return entity.getTransform().getWorldPosition();
    }

    @Test
    void shouldClimbTheStepsAndStopAtTheSteepRampAndTheTallBlock() throws IOException {
        GameSession up = playground(-500f, 600f);
        held.add(Keys.D);
        float highest = 0f;
        for (int i = 0; i < 150; i++) {
            up.update(FRAME);
            highest = Math.max(highest, y(up.getScene(), "Hero"));
        }
        held.clear();
        assertTrue(highest > 75f + 60f, "walked up the four steps (top at 80): " + highest);
        up.dispose();

        GameSession steep = playground(-100f, -300f);
        held.add(Keys.D);
        for (int i = 0; i < 240; i++) {
            steep.update(FRAME);
        }
        held.clear();
        assertTrue(y(steep.getScene(), "Hero") < 75f + 40f, "the 60 degree ramp is not climbed: " + y(steep.getScene(), "Hero"));
        assertTrue(x(steep.getScene(), "Hero") < 500f, "and holds the hero back at its foot (x 500): " + x(steep.getScene(), "Hero"));
        steep.dispose();

        GameSession tall = playground(-1000f, -700f);
        held.add(Keys.D);
        for (int i = 0; i < 120; i++) {
            tall.update(FRAME);
        }
        held.clear();
        assertTrue(x(tall.getScene(), "Hero") < -800f - 10f, "the tall block (150 high) stops the hero at its face: " + x(tall.getScene(), "Hero"));
        assertEquals(70f, y(tall.getScene(), "Hero"), 5f, "still standing on the floor");
        tall.dispose();
    }

    @Test
    void shouldPushACrateAcrossThePlaygroundFloor() throws IOException {
        GameSession session = playground(150f, 900f);
        float crateStart = x(session.getScene(), "Crate 1");
        held.add(Keys.D);
        for (int i = 0; i < 180; i++) {
            session.update(FRAME);
        }
        held.clear();

        assertTrue(x(session.getScene(), "Crate 1") > crateStart + 100f,
            "the crate moved along: " + crateStart + " to " + x(session.getScene(), "Crate 1"));
        assertTrue(session.getRuntimeErrors().isEmpty(), () -> session.getRuntimeErrors().toString());
        session.dispose();
    }

    @Test
    void shouldKickAndStopAnAimedBallInTheSandboxFromTheScript() throws IOException {
        GameSession session = aimedSandbox("Ball 1", 600f);
        Scene scene = session.getScene();
        session.update(FRAME);
        assertTrue(scene.findEntityByName("Help Text").getComponent(TextComponent.class).getText().endsWith("Aiming at: Ball 1"),
            scene.findEntityByName("Help Text").getComponent(TextComponent.class).getText());
        assertEquals(0f, session.getPhysicsSystem3D().getLinearVelocity(scene.findEntityByName("Ball 1")).z, 20f, "resting before the kick");

        press(session, Keys.SPACE);

        assertEquals(-900f, session.getPhysicsSystem3D().getLinearVelocity(scene.findEntityByName("Ball 1")).z, 40f,
            "away from the camera at the kick speed");
        press(session, Keys.T);
        assertTrue(session.getPhysicsSystem3D().getLinearVelocity(scene.findEntityByName("Ball 1")).len() < 50f, "T stops the aimed body");
        assertTrue(session.getRuntimeErrors().isEmpty(), () -> session.getRuntimeErrors().toString());
        session.dispose();
    }

    @Test
    void shouldPopTheCratesAroundTheAimPoint() throws IOException {
        GameSession session = aimedSandbox("Crate 1", 400f);
        Scene scene = session.getScene();

        press(session, Keys.E);

        int popped = 0;
        StringBuilder seen = new StringBuilder(scene.findEntityByName("Help Text").getComponent(TextComponent.class).getText());
        for (Entity entity : scene.getAllEntities()) {
            if (entity.getName().startsWith("Crate")) {
                float vy = session.getPhysicsSystem3D().getLinearVelocity(entity).y;
                seen.append(" | ").append(entity.getName()).append(' ').append(entity.getTransform().getWorldPosition()).append(" vy=").append(vy);
                if (vy > 500f) {
                    popped++;
                }
            }
        }
        assertTrue(popped >= 2, "the aimed crate and its neighbours jumped: " + popped + seen);
        assertTrue(session.getRuntimeErrors().isEmpty(), () -> session.getRuntimeErrors().toString());
        session.dispose();
    }

    @Test
    void shouldPushAnAimedBallForAsLongAsFIsHeld() throws IOException {
        GameSession session = aimedSandbox("Ball 1", 600f);
        Scene scene = session.getScene();
        float before = scene.findEntityByName("Ball 1").getTransform().getWorldPosition().z;

        held.add(Keys.F);
        for (int i = 0; i < 30; i++) {
            session.update(FRAME);
        }
        held.clear();

        assertTrue(scene.findEntityByName("Ball 1").getTransform().getWorldPosition().z < before - 100f,
            "a half second of push moved the ball away from the camera");
        session.dispose();
    }

    @Test
    void shouldRollIntoAGemInTheCollector() throws IOException {
        Scene scene = load("collector-3d");
        held.add(Keys.D);
        held.add(Keys.S);
        run(scene, 90);
        assertEquals("Gems: 1 / 10", text(scene, "Collected Text"));
        assertTrue(text(scene, "Time Text").startsWith("Time: 1."), text(scene, "Time Text"));

        Entity camera = entity(scene, "Main Camera");
        assertTrue(camera.getTransform().getWorldPosition().y > y(scene, "Player"), "camera stays above the player");
        scene.dispose();
    }

    @Test
    void shouldChangeLanesJumpAndCountDistanceInTheRunner() throws IOException {
        Scene scene = load("runner-3d");
        run(scene, 30, Keys.RIGHT);
        assertEquals(130f, x(scene, "Player"), 0.01f);
        run(scene, 10, Keys.SPACE);
        assertTrue(y(scene, "Player") > 60f, "jumping");
        run(scene, 60);
        assertEquals(25f, y(scene, "Player"), 0.01f, "landed");
        assertTrue(scene.getAllEntities().stream().anyMatch(e -> e.getName().startsWith("Block") && e.isActiveSelf()),
            "blocks on the track");
        assertNotEquals("Distance: 0 m", text(scene, "Distance Text"));
        scene.dispose();
    }

    @Test
    void shouldWaitForTheStartCountDownDriveSlowOnGrassAndLetRivalsFinishThreeLapsInTheRacer() throws IOException {
        Scene scene = load("racing-3d");
        float startZ = z(scene, "Player");
        float rivalStartZ = z(scene, "Rival 1");
        run(scene, 120);
        assertEquals("Press W or Up to start", text(scene, "Message Text"));
        assertEquals(rivalStartZ, z(scene, "Rival 1"), 0.01f, "rivals wait on the grid until the player starts");
        run(scene, 70, Keys.W);
        assertEquals(startZ, z(scene, "Player"), 0.01f, "held on the grid during the countdown");
        assertEquals("2", text(scene, "Message Text"));
        run(scene, 120);
        assertEquals("GO!", text(scene, "Message Text"));

        run(scene, 60, Keys.W);
        assertTrue(z(scene, "Rival 1") < rivalStartZ - 100f, "rivals drive off at GO");
        assertTrue(z(scene, "Player") < startZ - 200f, "drives down the start straight");
        assertTrue(Math.abs(x(scene, "Player") + 50f) < 1f, "goes straight without steering");
        held.add(Keys.W);
        run(scene, 30, Keys.A);
        run(scene, 120);
        held.clear();
        assertTrue(x(scene, "Player") < -200f, "steered left off the road");
        int kmh = Integer.parseInt(text(scene, "Speed Text").replace(" km/h", ""));
        assertTrue(kmh <= 68, "grass limits the speed, got " + kmh + " km/h");

        // Rivals drive the circuit by themselves and stop once they have finished three laps (about 40 s)
        run(scene, 60 * 50);
        assertEquals("Position 3 / 3", text(scene, "Position Text"));
        for (String rival : List.of("Rival 1", "Rival 2")) {
            float rivalZ = z(scene, rival);
            run(scene, 30);
            assertEquals(rivalZ, z(scene, rival), 0.5f, rival + " stopped after finishing");
            assertTrue(Math.abs(x(scene, rival)) < 110f && rivalZ < 800f && rivalZ > 0f,
                rival + " stopped on the start straight, past the line: " + x(scene, rival) + ", " + rivalZ);
        }

        run(scene, 1, Keys.R);
        assertEquals(startZ, z(scene, "Player"), 0.01f, "R puts the car back on the grid");
        assertEquals("Press W or Up to start", text(scene, "Message Text"));
        assertEquals("Time 0:00.0", text(scene, "Time Text"));
        scene.dispose();
    }

    /** The last state each engine of the racer was given, by car name: {revs, throttle, gain, pan}. */
    private static java.util.Map<String, float[]> listenToEngines(Scene scene) {
        java.util.Map<String, float[]> heard = new java.util.HashMap<>();
        for (String car : List.of("Player", "Rival 1", "Rival 2")) {
            float[] last = new float[4];
            heard.put(car, last);
            entity(scene, car).getComponent(EngineSoundComponent.class).bind((revs, throttle, gain, pan) -> {
                last[0] = revs;
                last[1] = throttle;
                last[2] = gain;
                last[3] = pan;
            });
        }
        return heard;
    }

    @Test
    void shouldGiveEveryCarOfTheRacerAnEngineThatIdlesOnTheGridRevsAtTheStartAndFollowsItsSpeed() throws IOException {
        Scene scene = load("racing-3d");
        java.util.Map<String, float[]> heard = listenToEngines(scene);
        for (String car : List.of("Player", "Rival 1", "Rival 2")) {
            assertNotNull(entity(scene, car).getComponent(EngineSoundComponent.class), car + " has an engine");
            assertTrue(EngineProfile.isKnown(entity(scene, car).getComponent(EngineSoundComponent.class).getProfile()), car);
        }
        assertTrue(entity(scene, "Rival 1").getComponent(EngineSoundComponent.class).isSpatial(), "rivals are heard in place");
        assertFalse(entity(scene, "Player").getComponent(EngineSoundComponent.class).isSpatial(), "the player's own car is not");

        run(scene, 60);
        assertEquals(0f, heard.get("Player")[0], 0.02f, "idles on the grid");
        assertEquals(0f, heard.get("Player")[1], 0.02f);

        run(scene, 40, Keys.W);
        assertTrue(heard.get("Player")[0] > 0.4f, "the engine revs against the held clutch in the countdown: " + heard.get("Player")[0]);
        assertTrue(heard.get("Player")[1] > 0.9f);
        assertTrue(heard.get("Rival 1")[0] > 0.1f, "the rivals rev too");
        assertEquals(0f, x(scene, "Player") + 50f, 0.01f, "still on the grid");

        // Drive off: revs rise and fall as gears change, and speed and revs stay within their ranges
        run(scene, 200);
        held.add(Keys.W);
        float lowest = 1f;
        float highest = 0f;
        float previousSpeedRevs = -1f;
        int drops = 0;
        for (int frame = 0; frame < 150; frame++) {
            run(scene, 1);
            float revs = heard.get("Player")[0];
            assertTrue(revs >= 0f && revs <= 1f, "revs " + revs);
            lowest = Math.min(lowest, revs);
            highest = Math.max(highest, revs);
            if (previousSpeedRevs > 0.6f && revs < previousSpeedRevs - 0.02f) {
                drops++;
            }
            previousSpeedRevs = revs;
        }
        held.clear();
        assertTrue(highest > 0.85f, "revs the engine out in a gear: " + highest);
        assertTrue(drops >= 2, "revs fall when the gearbox changes up: " + drops);

        // Lifting off: the throttle drops to nothing and the revs fall away
        run(scene, 90);
        assertEquals(0f, heard.get("Player")[1], 0.01f, "no throttle when coasting");
        assertTrue(heard.get("Player")[0] < highest - 0.3f, "revs fall with speed");
        assertTrue(heard.get("Rival 1")[2] >= 0f && heard.get("Rival 1")[2] <= 1f);
        assertTrue(Math.abs(heard.get("Rival 1")[3]) <= 1f);
        scene.dispose();
    }

    @Test
    void shouldPlaceARivalsEngineInTheStereoFieldRelativeToTheChaseCamera() throws IOException {
        Scene scene = load("racing-3d");
        assertNotNull(entity(scene, "Main Camera").getComponent(AudioListenerComponent.class), "the camera is the listener");
        java.util.Map<String, float[]> heard = listenToEngines(scene);

        run(scene, 5);

        float[] player = heard.get("Player");
        assertEquals(entity(scene, "Player").getComponent(EngineSoundComponent.class).getVolume(), player[2], 1e-3f,
            "the player's engine is at its own volume, not faded by distance");
        assertEquals(0f, player[3], 1e-3f, "nor panned");
        // On the grid the rivals stand beside the player (x -50 for the player, rivals to the sides)
        for (String rival : List.of("Rival 1", "Rival 2")) {
            float[] state = heard.get(rival);
            float expectedSide = Math.signum(x(scene, rival) - x(scene, "Main Camera"));
            assertTrue(state[2] > 0f, rival + " is within earshot of the camera on the grid");
            if (Math.abs(x(scene, rival) - x(scene, "Main Camera")) > 20f) {
                assertEquals(expectedSide, Math.signum(state[3]), rival + " pans to the side it stands on");
            }
        }
        scene.dispose();
    }

    private static long activeBlocks(Scene scene) {
        return scene.getAllEntities().stream().filter(e -> e.getName().startsWith("Block ") && e.isActiveSelf()).count();
    }

    private static float[] centreOf(Scene scene, String name) {
        var position = entity(scene, name).getTransform().getWorldPosition();
        return new float[] {position.x, position.y, position.z};
    }

    @Test
    void shouldBuildABlockWorldThatOnlyDrawsBlocksWithAnOpenSideAndLetThePlayerWalkAndJump() throws IOException {
        Scene scene = load("blockworld-3d");
        run(scene, 5);
        long drawn = activeBlocks(scene);
        assertTrue(drawn > 200 && drawn < 1200, "only blocks with an open side are drawn, not all " + 16 * 16 * 12 + ": " + drawn);
        assertEquals("Broken 0   Placed 0   Height 3", text(scene, "Stats Text"));
        assertTrue(text(scene, "Hotbar Text").contains("[1 Grass]"), text(scene, "Hotbar Text"));
        float eyeHeight = y(scene, "Main Camera");
        assertEquals(450f, eyeHeight, 0.5f, "eyes 150 above the ground, three blocks up");

        float startZ = z(scene, "Main Camera");
        run(scene, 30, Keys.W);
        assertEquals(startZ - 190f, z(scene, "Main Camera"), 2f, "walks 380 units per second across the flat start");
        run(scene, 1, Keys.SPACE);
        run(scene, 20);
        assertTrue(y(scene, "Main Camera") > eyeHeight + 80f, "jumping lifts the eyes");
        run(scene, 90);
        assertEquals(eyeHeight, y(scene, "Main Camera"), 0.5f, "lands flush on the ground again");

        run(scene, 1, Keys.NUM_3);
        assertTrue(text(scene, "Hotbar Text").contains("[3 Stone]"), text(scene, "Hotbar Text"));
        scene.dispose();
    }

    @Test
    void shouldStopAtTheEdgeOfTheBlockWorldAndNotWalkThroughBlocks() throws IOException {
        Scene scene = load("blockworld-3d");
        held.add(Keys.W);
        run(scene, 900);
        held.clear();
        float stoppedZ = z(scene, "Main Camera");
        run(scene, 30, Keys.W);
        assertEquals(stoppedZ, z(scene, "Main Camera"), 0.01f, "a wall of blocks or the world edge stops the walk");
        assertTrue(Math.abs(stoppedZ) < 800f && Math.abs(x(scene, "Main Camera")) < 800f, "never leaves the world: " + stoppedZ);
        scene.dispose();
    }

    @Test
    void shouldPlaceABlockInFrontBreakTheBlockUnderfootAndDropOneBlock() throws IOException {
        Scene scene = load("blockworld-3d");
        run(scene, 5);
        long drawn = activeBlocks(scene);

        // Look down at the ground just in front of the feet and place a block there
        held.add(Keys.DOWN);
        run(scene, 30);
        held.clear();
        run(scene, 1, Keys.G);
        assertEquals("Broken 0   Placed 1   Height 3", text(scene, "Stats Text"));
        // Placed in the next column ahead, on top of the ground: block (8, 3, 7), centred at x 50, y 350, z -50
        assertTrue(scene.getAllEntities().stream().anyMatch(e -> e.getName().startsWith("Block ") && e.isActiveSelf()
            && Math.abs(e.getTransform().getWorldPosition().x - 50f) < 0.5f && Math.abs(e.getTransform().getWorldPosition().y - 350f) < 0.5f
            && Math.abs(e.getTransform().getWorldPosition().z + 50f) < 0.5f), "the new block is drawn in front of the player");
        assertTrue(activeBlocks(scene) >= drawn - 1, "the block it covers may disappear, nothing else does");
        assertTrue(entity(scene, "Selection").isActiveSelf(), "the block in the crosshair is outlined");

        // Looking straight down: building would fill the player's own space, so it is refused
        held.add(Keys.DOWN);
        run(scene, 90);
        held.clear();
        run(scene, 1, Keys.G);
        assertEquals("Broken 0   Placed 1   Height 3", text(scene, "Stats Text"));

        // Digging out the block underfoot drops the player one block
        run(scene, 1, Keys.F);
        run(scene, 40);
        assertEquals("Broken 1   Placed 1   Height 2", text(scene, "Stats Text"));
        assertEquals(350f, y(scene, "Main Camera"), 0.5f);

        run(scene, 1, Keys.R);
        assertEquals("Broken 0   Placed 0   Height 3", text(scene, "Stats Text"), "R builds the world again");
        scene.dispose();
    }

    @Test
    void shouldStartTheObbyOnItsStartPadWithTheAvatarAndRunAwayFromTheCamera() throws IOException {
        Scene scene = load("obby-3d");
        run(scene, 60);
        assertEquals("Stage 1 / 6", text(scene, "Stage Text"));
        assertEquals("Coins 0 / 12", text(scene, "Coins Text"));
        assertEquals(78f, y(scene, "Player"), 0.5f, "stands on the start pad");
        for (String limb : List.of("Arm L Pivot", "Arm R Pivot", "Leg L Pivot", "Leg R Pivot", "Head", "Torso")) {
            assertNotNull(entity(scene, limb), limb);
        }
        float startZ = z(scene, "Player");
        run(scene, 20, Keys.W);
        assertTrue(z(scene, "Player") < startZ - 100f, "W runs away from the camera");
        assertTrue(z(scene, "Main Camera") > z(scene, "Player"), "the camera follows behind");
        scene.dispose();
    }

    @Test
    void shouldRunRelativeToTheCameraAndTurnItWithTheArrowKeysInTheObby() throws IOException {
        Scene scene = load("obby-3d");
        run(scene, 30);
        float cameraX = x(scene, "Main Camera");
        held.add(Keys.LEFT);
        run(scene, 49);
        held.clear();
        assertTrue(Math.abs(x(scene, "Main Camera") - cameraX) > 300f, "the camera swings round the character");
        float startX = x(scene, "Player");
        run(scene, 24, Keys.W);
        assertTrue(x(scene, "Player") < startX - 80f, "after turning the camera a quarter, W runs sideways along the course");
        scene.dispose();
    }

    @Test
    void shouldSendTheObbyCharacterBackAfterAFallAndAfterTouchingLavaOrASpinner() throws IOException {
        Scene scene = load("obby-3d");
        place3D(scene, "Player", 1500f, 78f, 0f);
        run(scene, 150);
        assertNotEquals("Deaths 0", text(scene, "Deaths Text"), "falls into the void");
        assertEquals(78f, y(scene, "Player"), 1000f, "and is put back where it started (here over the void again, so it falls again)");
        scene.dispose();

        Scene lava = load("obby-3d");
        float[] lavaAt = centreOf(lava, "Kill Lava 1");
        place3D(lava, "Player", lavaAt[0], lavaAt[1] + 40f, lavaAt[2]);
        run(lava, 3);
        assertNotEquals("Deaths 0", text(lava, "Deaths Text"), "lava hurts");
        lava.dispose();

        Scene spinner = load("obby-3d");
        float[] barAt = centreOf(spinner, "Spinner 1");
        place3D(spinner, "Player", barAt[0], barAt[1] + 20f, barAt[2]);
        run(spinner, 3);
        assertNotEquals("Deaths 0", text(spinner, "Deaths Text"), "a spinner bar hurts");
        spinner.dispose();
    }

    @Test
    void shouldTakeCoinsSetCheckpointsAndComeBackToTheCheckpointInTheObby() throws IOException {
        Scene scene = load("obby-3d");
        float[] coin = centreOf(scene, "Coin 1");
        place3D(scene, "Player", coin[0], coin[1], coin[2]);
        run(scene, 2);
        assertEquals("Coins 1 / 12", text(scene, "Coins Text"));
        assertFalse(active(scene, "Coin 1"), "a taken coin disappears");
        scene.dispose();

        Scene checkpoint = load("obby-3d");
        float[] pad = centreOf(checkpoint, "Checkpoint 1");
        place3D(checkpoint, "Player", pad[0], pad[1] + 20f + 78f + 3f, pad[2]);
        run(checkpoint, 20);
        assertEquals("Stage 2 / 6", text(checkpoint, "Stage Text"), "landing on a checkpoint reaches the next stage");
        // Walk off the end of the pad: back on the checkpoint, not at the very start of the course
        held.add(Keys.W);
        run(checkpoint, 240);
        held.clear();
        run(checkpoint, 10);
        assertNotEquals("Deaths 0", text(checkpoint, "Deaths Text"));
        assertTrue(Math.abs(z(checkpoint, "Player") - pad[2]) < 220f, "respawns on the checkpoint: " + z(checkpoint, "Player"));
        checkpoint.dispose();
    }

    @Test
    void shouldCarryTheCharacterOnAMoverDropAFadingTileAndBounceOffAPadInTheObby() throws IOException {
        Scene scene = load("obby-3d");
        float[] mover = centreOf(scene, "Mover Z 1");
        // The mover starts a quarter of a trip out, towards the pad behind it
        place3D(scene, "Player", mover[0], mover[1] + 20f + 78f + 2f, mover[2] + 260f);
        run(scene, 5);
        float startZ = z(scene, "Player");
        run(scene, 115);
        assertEquals(z(scene, "Mover Z 1"), z(scene, "Player"), 10f, "rides along with the mover");
        assertTrue(Math.abs(z(scene, "Player") - startZ) > 300f, "and the mover took it a long way");
        scene.dispose();

        Scene fade = load("obby-3d");
        float[] tile = centreOf(fade, "Fade 1");
        place3D(fade, "Player", tile[0], tile[1] + 20f + 78f + 2f, tile[2]);
        run(fade, 20);
        assertTrue(active(fade, "Fade 1"), "a fading tile holds for a moment after it is stood on");
        run(fade, 40);
        assertFalse(active(fade, "Fade 1"), "and then drops out");
        run(fade, 200);
        assertTrue(active(fade, "Fade 1"), "and comes back later");
        fade.dispose();

        Scene bounce = load("obby-3d");
        float[] pad = centreOf(bounce, "Pad 1");
        place3D(bounce, "Player", pad[0], pad[1] + 20f + 78f + 2f, pad[2]);
        run(bounce, 20);
        assertTrue(y(bounce, "Player") > pad[1] + 20f + 78f + 200f, "a pad launches the character: " + y(bounce, "Player"));
        bounce.dispose();
    }

    @Test
    void shouldFinishTheObbyByLandingOnTheFinishPart() throws IOException {
        Scene scene = load("obby-3d");
        float[] finish = centreOf(scene, "Finish");
        place3D(scene, "Player", finish[0], finish[1] + 20f + 78f + 3f, finish[2]);
        run(scene, 30);
        assertTrue(text(scene, "Message Text").startsWith("Course complete!"), text(scene, "Message Text"));
        run(scene, 1, Keys.R);
        assertEquals("", text(scene, "Message Text"));
        scene.dispose();
    }

    @Test
    void shouldWalkStopAtWallsAndShootDronesThroughWavesInTheArenaShooter() throws IOException {
        Scene scene = load("arena-fps-3d");
        run(scene, 5);
        assertEquals("Press Space to start", text(scene, "Message Text"));
        assertFalse(active(scene, "Enemy 1"), "no drones before the start");

        // Walking and walls work before the start
        float startZ = z(scene, "Player");
        run(scene, 30, Keys.W);
        assertEquals(startZ - 160f, z(scene, "Player"), 1f, "walks forward at 320 units per second");
        run(scene, 240, Keys.A);
        assertEquals(-880f, x(scene, "Player"), 0.5f, "stops at the west wall (inner face -900, radius 20)");

        run(scene, 1, Keys.SPACE);
        assertEquals("Wave 1 of 3", text(scene, "Message Text"));
        run(scene, 125);
        assertEquals("Drones left 3", text(scene, "Enemies Text"));

        // A crate between the player and a drone stops the shots
        place3D(scene, "Player", 0f, 60f, -100f);
        place3D(scene, "Enemy 1", 0f, 50f, -400f);
        run(scene, 40, Keys.SPACE);
        assertTrue(active(scene, "Enemy 1"), "balls hit the crate, not the drone behind it");

        // Shots are balls that fly from the gun, not instant hits
        place3D(scene, "Player", 0f, 60f, 300f);
        run(scene, 120);
        assertTrue(scene.getAllEntities().stream().noneMatch(e -> e.getName().startsWith("Ball") && e.isActiveSelf()),
            "earlier balls have landed");
        run(scene, 1, Keys.SPACE);
        Entity ball = scene.getAllEntities().stream()
            .filter(e -> e.getName().startsWith("Ball") && e.isActiveSelf()).findFirst().orElseThrow();
        float launchZ = ball.getTransform().getWorldPosition().z;
        run(scene, 4);
        assertEquals(launchZ - 100f, ball.getTransform().getWorldPosition().z, 2f, "flies at 1500 units per second");

        // Three hits destroy a drone; destroying all three starts the next wave
        for (String drone : List.of("Enemy 1", "Enemy 2", "Enemy 3")) {
            place3D(scene, "Player", 0f, 60f, 300f);
            place3D(scene, drone, 0f, 50f, 100f);
            run(scene, 40, Keys.SPACE);
            assertFalse(active(scene, drone), drone + " destroyed");
        }
        assertEquals("Wave 2 of 3", text(scene, "Message Text"));

        // Drones that reach the player hurt
        run(scene, 125);
        place3D(scene, "Player", 0f, 60f, 300f);
        place3D(scene, "Enemy 1", 0f, 50f, 250f);
        run(scene, 60);
        assertNotEquals("Health 100", text(scene, "Health Text"), "took damage");

        run(scene, 1, Keys.R);
        assertEquals("Press Space to start", text(scene, "Message Text"));
        assertEquals("Health 100", text(scene, "Health Text"));
        assertEquals(startZ, z(scene, "Player"), 0.01f, "R puts the player back at the start");
        scene.dispose();
    }

    @Test
    void shouldWalkPunchBlockAndLoseARoundByKnockOutInTheFighter() throws IOException {
        Scene scene = load("fighter-25d");
        run(scene, 5);
        assertEquals("Press Space to start", text(scene, "Message Text"));
        run(scene, 1, Keys.SPACE);
        assertEquals("Round 1", text(scene, "Message Text"));
        run(scene, 80);
        assertEquals("FIGHT!", text(scene, "Message Text"));

        float startX = x(scene, "Player");
        run(scene, 30, Keys.D);
        assertTrue(x(scene, "Player") > startX + 100f, "walks towards the CPU");
        for (int i = 0; i < 30 && health(scene, "CPU Health Text") == 100; i++) {
            held.add(Keys.D);
            run(scene, 20, Keys.J);
        }
        held.clear();
        assertTrue(health(scene, "CPU Health Text") < 100, "punches land");
        scene.dispose();

        int lossStanding = 100 - healthAfterLetting(false);
        int lossBlocking = 100 - healthAfterLetting(true);
        assertTrue(lossStanding > 0, "the CPU attacks a player who stands still");
        assertTrue(lossBlocking < lossStanding, "blocking takes less damage: " + lossBlocking + " vs " + lossStanding);

        Scene knockOut = load("fighter-25d");
        run(knockOut, 1, Keys.SPACE);
        for (int i = 0; i < 60 * 60 && !text(knockOut, "Message Text").startsWith("K.O."); i++) {
            run(knockOut, 1);
        }
        assertEquals("K.O.!  The CPU takes round 1", text(knockOut, "Message Text"));
        assertEquals("Rounds 0 - 1", text(knockOut, "Rounds Text"));
        run(knockOut, 140);
        assertEquals("Round 2", text(knockOut, "Message Text"));
        assertEquals(100, health(knockOut, "Player Health Text"), "health is full again for the next round");

        run(knockOut, 1, Keys.R);
        assertEquals("Press Space to start", text(knockOut, "Message Text"));
        assertEquals("Rounds 0 - 0", text(knockOut, "Rounds Text"));
        knockOut.dispose();
    }

    /** Shortest solution of each puzzle level (found by a breadth-first search), as arrow-key moves. */
    private static final List<String> PUZZLE_SOLUTIONS = List.of("LL", "ULDLURRRDRU", "LLULUURDDURRRURDD",
        "ULUUULLDRURDDLDRDRRUUUURRDLULDDULLULLDRURDD");

    @Test
    void shouldBlockAtWallsUndoPushesAndSolveEveryPuzzle() throws IOException {
        Scene scene = load("puzzle-3d");
        run(scene, 1);
        assertEquals("Puzzle 1 / 4", text(scene, "Level Text"));
        float startX = x(scene, "Player");
        float startZ = z(scene, "Player");
        puzzleMoves(scene, "UU");
        assertEquals(startZ - 100f, z(scene, "Player"), 0.01f, "one step up, then the wall stops the second");
        assertEquals("Moves 1", text(scene, "Moves Text"));
        puzzleMoves(scene, "D");
        float crateX = x(scene, "Crate 1");
        puzzleMoves(scene, "LL");
        assertEquals(crateX - 100f, x(scene, "Crate 1"), 0.01f, "walking into the crate pushes it");
        assertEquals("Moves 4", text(scene, "Moves Text"));
        run(scene, 1, Keys.Z);
        run(scene, 1, Keys.U);
        assertEquals(crateX, x(scene, "Crate 1"), 0.01f, "undo puts the crate back");
        assertEquals("Moves 2", text(scene, "Moves Text"), "two undos take back the push and the step before it");
        run(scene, 1, Keys.R);
        assertEquals(startX, x(scene, "Player"), 0.01f, "R restarts the puzzle");
        assertEquals("Moves 0", text(scene, "Moves Text"));

        for (int level = 0; level < PUZZLE_SOLUTIONS.size(); level++) {
            String solution = PUZZLE_SOLUTIONS.get(level);
            assertEquals("Puzzle " + (level + 1) + " / 4", text(scene, "Level Text"));
            puzzleMoves(scene, solution);
            assertTrue(text(scene, "Message Text").startsWith("Solved in " + solution.length() + " moves!"),
                "puzzle " + (level + 1) + ": " + text(scene, "Message Text"));
            run(scene, 1, Keys.SPACE);
        }
        assertEquals("Puzzle 1 / 4", text(scene, "Level Text"), "after the last puzzle it starts again");
        scene.dispose();
    }

    /** Presses arrow keys for U, D, L and R moves, giving each step time to finish animating. */
    private static void puzzleMoves(Scene scene, String moves) {
        for (char move : moves.toCharArray()) {
            int key = switch (move) {
                case 'U' -> Keys.UP;
                case 'D' -> Keys.DOWN;
                case 'L' -> Keys.LEFT;
                case 'R' -> Keys.RIGHT;
                default -> throw new IllegalArgumentException("move must be U, D, L or R: " + move);
            };
            run(scene, 1, key);
            run(scene, 8);
        }
    }

    /** Health left after letting the CPU fight a player who stands still (or blocks) for 8 seconds. */
    private static int healthAfterLetting(boolean block) throws IOException {
        Scene scene = load("fighter-25d");
        run(scene, 1, Keys.SPACE);
        run(scene, 80);
        if (block) {
            held.add(Keys.S);
        }
        run(scene, 8 * 60);
        held.clear();
        int health = health(scene, "Player Health Text");
        scene.dispose();
        return health;
    }

    /** The number at the end of a health text such as "CPU 94". */
    private static int health(Scene scene, String name) {
        String value = text(scene, name);
        return Integer.parseInt(value.substring(value.lastIndexOf(' ') + 1));
    }

    /** Moves an entity in 3D; the arena shooter reads positions from the transforms every frame. */
    private static void place3D(Scene scene, String name, float x, float y, float z) {
        entity(scene, name).getTransform().setLocalPosition(x, y, z);
    }

    @Test
    void shouldJumpAndRespawnAfterFallingInTheHopper() throws IOException {
        Scene scene = load("hopper-3d");
        run(scene, 30);
        assertEquals(30f, y(scene, "Player"), 0.5f, "stands on the first platform");
        run(scene, 15, Keys.SPACE);
        assertTrue(y(scene, "Player") > 100f, "jumping");
        run(scene, 60);
        assertEquals(30f, y(scene, "Player"), 0.5f, "landed");

        held.add(Keys.D);
        run(scene, 40);
        held.clear();
        run(scene, 90);
        assertEquals("Falls: 1", text(scene, "Falls Text"));
        assertEquals(0f, x(scene, "Player"), 0.01f, "back at the start");
        scene.dispose();
    }

    @Test
    void shouldCollectOrbsDieOnSpikesAndMovePlatformsInTheRuinsRunner() throws IOException {
        Scene scene = load("ruins-runner-25d");
        float moverStart = x(scene, "Mover X 1");
        run(scene, 30);
        assertEquals(35f, y(scene, "Player"), 0.5f, "stands on the ground");
        assertNotEquals(moverStart, x(scene, "Mover X 1"), "moving platform moves");

        held.add(Keys.RIGHT);
        while (x(scene, "Player") < 410f) {
            run(scene, 1);
        }
        run(scene, 40, Keys.SPACE);
        held.clear();
        run(scene, 30);
        assertEquals("Orbs: 1 / 8", text(scene, "Collected Text"));
        scene.dispose();

        Scene spikes = load("ruins-runner-25d");
        place(spikes, "Player", 1530f, 35f);
        held.add(Keys.RIGHT);
        for (int i = 0; i < 60 && text(spikes, "Deaths Text").equals("Deaths: 0"); i++) {
            run(spikes, 1);
        }
        held.clear();
        run(spikes, 1);
        assertEquals("Deaths: 1", text(spikes, "Deaths Text"));
        assertEquals(1530f, x(spikes, "Player"), 0.01f, "back at the start");
        spikes.dispose();
    }

    @Test
    void shouldSwitchHeroesAndUseEachAbilityInThreeHeroes() throws IOException {
        Scene scene = load("three-heroes-25d");
        run(scene, 5);
        assertTrue(active(scene, "Hero Mason"));

        // Mason conjures a crate that drops to the ground in front of him
        run(scene, 60, Keys.E);
        assertTrue(active(scene, "Conjured Crate 0"));
        assertEquals(40f, y(scene, "Conjured Crate 0"), 0.5f);
        assertEquals(75f, x(scene, "Conjured Crate 0"), 0.5f);

        // Scout jumps twice as high with the mid-air jump
        run(scene, 1, Keys.NUM_2);
        assertTrue(active(scene, "Hero Scout") && !active(scene, "Hero Mason"));
        assertTrue(text(scene, "Party Text").contains("Scout"));
        place(scene, "Player", -50f, 35f);
        float highest = 0f;
        run(scene, 1, Keys.SPACE);
        for (int i = 0; i < 20; i++) {
            run(scene, 1);
            highest = Math.max(highest, y(scene, "Player"));
        }
        run(scene, 1, Keys.SPACE);
        for (int i = 0; i < 40; i++) {
            run(scene, 1);
            highest = Math.max(highest, y(scene, "Player"));
        }
        assertTrue(highest > 35f + 250f, "double jump reached " + highest);
        scene.dispose();
    }

    @Test
    void shouldLetOnlyTheBrutePushHeavyBlocksAndSmashWalls() throws IOException {
        Scene scene = load("three-heroes-25d");
        place(scene, "Player", 1420f, 255f);
        held.add(Keys.RIGHT);
        run(scene, 30);
        assertEquals(1500f, x(scene, "Heavy Block"), 0.01f, "Mason cannot push it");
        run(scene, 1, Keys.NUM_3);
        held.add(Keys.RIGHT);
        run(scene, 60);
        assertTrue(x(scene, "Heavy Block") > 1550f, "Brute pushed it to " + x(scene, "Heavy Block"));
        scene.dispose();

        Scene wall = load("three-heroes-25d");
        place(wall, "Player", 1150f, 255f);
        run(wall, 5);
        run(wall, 1, Keys.E);
        assertTrue(active(wall, "Cracked Wall"), "Mason cannot smash");
        run(wall, 1, Keys.NUM_3);
        run(wall, 1, Keys.E);
        assertTrue(!active(wall, "Cracked Wall"), "Brute smashed the wall");
        wall.dispose();
    }

    @Test
    void shouldSwingOnTheRopeAndOpenTheGateWithTheCrateInGrappleGorge() throws IOException {
        Scene scene = load("grapple-gorge-25d");
        place(scene, "Player", 690f, 35f);
        run(scene, 2);
        held.add(Keys.RIGHT);
        run(scene, 8, Keys.SPACE);
        held.add(Keys.J);
        run(scene, 30);
        assertTrue(active(scene, "Rope"), "hooked");
        assertTrue(x(scene, "Player") > 800f, "swung out over the gorge: x = " + x(scene, "Player"));
        held.clear();
        run(scene, 1);
        assertTrue(!active(scene, "Rope"), "released");
        scene.dispose();

        Scene gate = load("grapple-gorge-25d");
        place(gate, "Player", 1420f, 35f);
        held.add(Keys.RIGHT);
        while (x(gate, "Crate") < 1790f) {
            run(gate, 1);
        }
        held.clear();
        run(gate, 120);
        assertEquals("Gate: open", text(gate, "Status Text"));
        assertTrue(y(gate, "Gate") > 150f + 200f, "gate raised to " + y(gate, "Gate"));
        gate.dispose();
    }

    /** Key presses for the parity run: frame, key; each key is just pressed on its frame. */
    private static final int[][] PARITY_PRESSES = {
        {0, Keys.SPACE}, {0, Keys.W}, {45, Keys.E}, {60, Keys.SPACE}, {90, Keys.TAB}, {120, Keys.SPACE}, {150, Keys.E},
    };
    private static final int PARITY_FRAMES = 240;
    /** World units; both runs use the same float math, so any difference beyond rounding is a real mismatch. */
    private static final float PARITY_TOLERANCE = 1e-3f;

    /** One frame of the parity input: D and Right held throughout, plus the presses scheduled for this frame. */
    private static void parityInput(int frame) {
        justPressed.clear();
        held.clear();
        held.add(Keys.D);
        held.add(Keys.RIGHT);
        for (int[] press : PARITY_PRESSES) {
            if (press[0] == frame) {
                justPressed.add(press[1]);
                held.add(press[1]);
            }
        }
    }

    @Test
    void shouldMatchTheReferencePaddleScriptAtDifferentWidthsAndFrameTimes() throws IOException {
        File file = new File(PROJECT, "samples/breakout-2d.json");
        for (float width : new float[] {90f, 140f}) {
            Scene graphScene = GameSession.loadScene(file);
            Scene javaScene = GameSession.loadScene(file);
            Entity reference = entity(javaScene, "Paddle");
            assertNotNull(reference.getComponent(GraphScriptComponent.class), "the sample must bind the editable graph");
            reference.removeComponent(reference.getComponent(GraphScriptComponent.class));
            reference.addComponent(new EntityScriptBindingComponent("scripts/samples/breakout/BreakoutPaddle.java",
                "samples.breakout.BreakoutPaddle", ScriptLanguage.JAVA));
            entity(graphScene, "Paddle").getComponent(ShapeRendererComponent.class).setWidth(width);
            reference.getComponent(ShapeRendererComponent.class).setWidth(width);
            GameSession graph = new GameSession(PROJECT, graphScene,
                new GameScripts(compiledScripts.toFile(), getClass().getClassLoader()), GameSession.CONSOLE);
            GameSession java = new GameSession(PROJECT, javaScene,
                new GameScripts(compiledScripts.toFile(), getClass().getClassLoader()), GameSession.CONSOLE);
            assertEquals(List.of(), graph.start());
            assertEquals(List.of(), java.start());
            for (int frame = 0; frame < 480; frame++) {
                held.clear();
                if (frame < 160) held.add(Keys.LEFT);
                else if (frame < 320) held.add(Keys.D);
                else { held.add(Keys.A); held.add(Keys.RIGHT); }
                float delta = frame % 2 == 0 ? FRAME : FRAME / 2f;
                graph.update(delta);
                java.update(delta);
                assertEquals(x(javaScene, "Paddle"), x(graphScene, "Paddle"), 0.001f, "frame " + frame + ", width " + width);
                assertEquals(y(javaScene, "Paddle"), y(graphScene, "Paddle"), 0.001f);
            }
            assertTrue(graph.getRuntimeErrors().isEmpty(), () -> graph.getRuntimeErrors().toString());
            graph.dispose();
            java.dispose();
        }
    }

    /** Hero keys per stretch of frames for the walker parity run: {frames, keys...} held; a key not held the frame before counts as pressed. The first second the hero lands. */
    private static final int[][] WALKER_SCHEDULE = {
        {60}, {40, Keys.D, Keys.W}, {20}, {1, Keys.SPACE}, {30, Keys.SPACE}, {70}, {25, Keys.LEFT, Keys.S},
        {1, Keys.SPACE, Keys.A}, {20, Keys.A}, {1, Keys.SPACE, Keys.A}, {60}, {30, Keys.A, Keys.D, Keys.UP}, {30}};

    /**
     * Roadmap M1.2: the walker graph does what {@code WalkerPlayer.java} does, frame for frame, with real 3D physics: a diagonal walks
     * at the same 300 units a second (the direction is normalised), Space jumps once per press and only on the ground (holding it or
     * pressing it in the air does nothing), and the Help Text says whether the hero stands on the ground.
     */
    @Test
    void shouldWalkJumpAndLabelLikeTheReferenceWalkerScriptFrameForFrame() throws IOException {
        File file = new File(PROJECT, "samples/character-playground-3d.json");
        Scene graphScene = GameSession.loadScene(file);
        Scene javaScene = GameSession.loadScene(file);
        Entity graphHero = entity(graphScene, "Hero");
        Entity javaHero = entity(javaScene, "Hero");
        graphHero.getComponents(EntityScriptBindingComponent.class).forEach(graphHero::removeComponent);
        graphHero.getComponents(GraphScriptComponent.class).forEach(graphHero::removeComponent);
        graphHero.addComponent(new GraphScriptComponent("graphs/samples/WalkerPlayer.graph"));
        javaHero.getComponents(GraphScriptComponent.class).forEach(javaHero::removeComponent);
        javaHero.getComponents(EntityScriptBindingComponent.class).forEach(javaHero::removeComponent);
        javaHero.addComponent(new EntityScriptBindingComponent("scripts/samples/walker/WalkerPlayer.java",
            "samples.walker.WalkerPlayer", ScriptLanguage.JAVA));
        for (Entity hero : List.of(graphHero, javaHero)) {
            hero.getTransform().setLocalPosition(-700f, 75f, 900f);
        }
        GameSession graph = new GameSession(PROJECT, graphScene,
            new GameScripts(compiledScripts.toFile(), getClass().getClassLoader()), GameSession.CONSOLE);
        GameSession java = new GameSession(PROJECT, javaScene,
            new GameScripts(compiledScripts.toFile(), getClass().getClassLoader()), GameSession.CONSOLE);
        assertEquals(List.of(), graph.start());
        assertEquals(List.of(), java.start());
        int frame = 0;
        int jumps = 0;
        int airPresses = 0;
        boolean wasGrounded = true;
        Set<Integer> previous = new HashSet<>();
        for (int[] stretch : WALKER_SCHEDULE) {
            for (int i = 0; i < stretch[0]; i++, frame++) {
                held.clear();
                justPressed.clear();
                for (int k = 1; k < stretch.length; k++) {
                    held.add(stretch[k]);
                    if (!previous.contains(stretch[k])) {
                        justPressed.add(stretch[k]); // went down this frame, as libGDX reports it
                    }
                }
                previous = new HashSet<>(held);
                graph.update(FRAME);
                java.update(FRAME);
                String at = "frame " + frame;
                assertEquals(x(javaScene, "Hero"), x(graphScene, "Hero"), 1e-3f, at);
                assertEquals(y(javaScene, "Hero"), y(graphScene, "Hero"), 1e-3f, at);
                assertEquals(z(javaScene, "Hero"), z(graphScene, "Hero"), 1e-3f, at);
                assertEquals(entity(javaScene, "Help Text").getComponent(TextComponent.class).getText(),
                    entity(graphScene, "Help Text").getComponent(TextComponent.class).getText(), at);
                boolean grounded = graph.getPhysicsSystem3D().isCharacterGrounded(graphHero);
                if (justPressed.contains(Keys.SPACE)) {
                    if (!wasGrounded) {
                        airPresses++;
                    } else if (!grounded) {
                        jumps++;
                    }
                }
                wasGrounded = grounded;
            }
        }
        assertEquals(2, jumps, "both presses on the ground take off; holding Space does not jump again");
        assertEquals(1, airPresses, "the scenario presses Space once in the air, which does nothing (the positions above match)");
        assertTrue(graph.getRuntimeErrors().isEmpty(), () -> graph.getRuntimeErrors().toString());
        assertTrue(java.getRuntimeErrors().isEmpty(), () -> java.getRuntimeErrors().toString());
        graph.dispose();
        java.dispose();
    }

    @Test
    void shouldPlayEverySampleTheSameInTheGameRuntimeAsInEditorPlayMode() throws IOException {
        for (String sample : SAMPLES) {
            File file = new File(PROJECT, "samples/" + sample + ".json");

            // Editor: Play button path (clone, compile, bind, 2D physics)
            EditorState editorState = new EditorState();
            EditorApplication.registerComponentSerializers(editorState.getSerializer().getRegistry());
            editorState.setScriptManager(scripts);
            editorState.setActiveScene(editorState.getSerializer().fromJson(Files.readString(file.toPath())));
            PlayModeController play = new PlayModeController(editorState);
            play.addExtension(new GraphPlayModeExtension(editorState, new GraphAssets(PROJECT), NodeRegistry.createDefault(),
                new EditorLog(), new GraphExecutionTrace()));
            assertTrue(play.play(), () -> sample + ": " + editorState.getStatusMessage());
            for (int frame = 0; frame < PARITY_FRAMES; frame++) {
                parityInput(frame);
                play.update(FRAME);
            }

            // Runtime: scene file, precompiled scripts, GameSession
            GameSession session = new GameSession(PROJECT, GameSession.loadScene(file),
                new GameScripts(compiledScripts.toFile(), getClass().getClassLoader()), GameSession.CONSOLE);
            assertEquals(List.of(), session.start(), sample);
            for (int frame = 0; frame < PARITY_FRAMES; frame++) {
                parityInput(frame);
                session.update(FRAME);
            }

            assertSameState(sample, play.getRuntimeScene(), session.getScene());
            assertTrue(session.getRuntimeErrors().isEmpty(), () -> sample + ": " + session.getRuntimeErrors());
            play.stop();
            session.dispose();
        }
        releaseKeys();
    }

    /**
     * 3D physics (TD-028, Jolt) behaves the same in the game runtime as in editor Play: a floor, balls dropped from different
     * heights and a crate on a moving kinematic platform, run for the same frames through both start-up paths.
     */
    @Test
    void shouldSimulate3DPhysicsTheSameInTheGameRuntimeAsInEditorPlayMode(@org.junit.jupiter.api.io.TempDir Path temp) throws IOException {
        EditorState editorState = new EditorState();
        EditorApplication.registerComponentSerializers(editorState.getSerializer().getRegistry());
        Scene authored = new Scene("Physics 3D parity");
        authored.createEntity("Camera").addComponent(new io.github.ldev22live.gdxunity.engine.render.components.CameraComponent());
        Entity floor = authored.createEntity("Floor");
        floor.getTransform().setLocalPosition(0f, -50f, 0f);
        floor.addComponent(new io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent(
            new com.badlogic.gdx.math.Vector3(2000f, 100f, 2000f)));
        for (int i = 0; i < 3; i++) {
            Entity ball = authored.createEntity("Ball " + i);
            ball.getTransform().setLocalPosition(i * 120f - 120f, 300f + i * 150f, 0f);
            ball.addComponent(new io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent(
                io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent.MotionType.DYNAMIC));
            ball.addComponent(new io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent(50f));
        }
        Entity crate = authored.createEntity("Crate");
        crate.getTransform().setLocalPosition(600f, 400f, 0f);
        crate.addComponent(new io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent(
            io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent.MotionType.DYNAMIC));
        crate.addComponent(new io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent());
        File file = temp.resolve("physics-3d-parity.json").toFile();
        Files.writeString(file.toPath(), editorState.getSerializer().toJson(authored));

        editorState.setScriptManager(scripts);
        editorState.setActiveScene(editorState.getSerializer().fromJson(Files.readString(file.toPath())));
        PlayModeController play = new PlayModeController(editorState);
        assertTrue(play.play(), () -> editorState.getStatusMessage());
        assertNotNull(play.getPhysicsSystem3D());
        for (int frame = 0; frame < PARITY_FRAMES; frame++) {
            play.update(FRAME);
        }

        GameSession session = new GameSession(temp.toFile(), GameSession.loadScene(file),
            new GameScripts(compiledScripts.toFile(), getClass().getClassLoader()), GameSession.CONSOLE);
        assertEquals(List.of(), session.start());
        assertNotNull(session.getPhysicsSystem3D());
        for (int frame = 0; frame < PARITY_FRAMES; frame++) {
            session.update(FRAME);
        }

        assertSameState("3D physics", play.getRuntimeScene(), session.getScene());
        Entity ball = session.getScene().findEntityByName("Ball 0");
        assertTrue(ball.getTransform().getWorldPosition().y < 300f, "the ball fell");
        play.stop();
        session.dispose();
    }

    /** Every entity has the same position, active state and text; entities made while playing are matched by name. */
    private static void assertSameState(String sample, Scene editor, Scene runtime) {
        assertEquals(editor.getAllEntities().size(), runtime.getAllEntities().size(), sample + ": entity count");
        List<String> differences = new ArrayList<>();
        for (Entity expected : editor.getAllEntities()) {
            Entity actual = runtime.findEntityById(expected.getId());
            if (actual == null) {
                actual = runtime.findEntityByName(expected.getName());
            }
            if (actual == null) {
                differences.add(expected.getName() + " is missing");
                continue;
            }
            if (expected.getTransform().getWorldPosition().dst(actual.getTransform().getWorldPosition()) > PARITY_TOLERANCE) {
                differences.add(expected.getName() + " at " + expected.getTransform().getWorldPosition() + " vs "
                    + actual.getTransform().getWorldPosition());
            }
            if (expected.isActiveSelf() != actual.isActiveSelf()) {
                differences.add(expected.getName() + " active " + expected.isActiveSelf() + " vs " + actual.isActiveSelf());
            }
            TextComponent expectedText = expected.getComponent(TextComponent.class);
            if (expectedText != null && !expectedText.getText().equals(actual.getComponent(TextComponent.class).getText())) {
                differences.add(expected.getName() + " says '" + expectedText.getText() + "' vs '"
                    + actual.getComponent(TextComponent.class).getText() + "'");
            }
        }
        assertTrue(differences.isEmpty(), () -> sample + " differs between editor Play and the runtime: " + differences);
    }
}
