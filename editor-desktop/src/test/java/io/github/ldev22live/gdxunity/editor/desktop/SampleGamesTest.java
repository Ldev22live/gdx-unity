package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.scripting.ScriptManager;
import io.github.ldev22live.gdxunity.engine.desktop.GameScripts;
import io.github.ldev22live.gdxunity.engine.desktop.GameSession;
import io.github.ldev22live.gdxunity.engine.render.camera.GameCameraRig;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
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
        "grapple-gorge-25d", "racing-3d", "arena-fps-3d", "fighter-25d", "puzzle-3d");

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
    void shouldPlayEverySampleTheSameInTheGameRuntimeAsInEditorPlayMode() throws IOException {
        for (String sample : SAMPLES) {
            File file = new File(PROJECT, "samples/" + sample + ".json");

            // Editor: Play button path (clone, compile, bind, 2D physics)
            EditorState editorState = new EditorState();
            EditorApplication.registerComponentSerializers(editorState.getSerializer().getRegistry());
            editorState.setScriptManager(scripts);
            editorState.setActiveScene(editorState.getSerializer().fromJson(Files.readString(file.toPath())));
            PlayModeController play = new PlayModeController(editorState);
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
