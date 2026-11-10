package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.scripting.CompilationResult;
import io.github.ldev22live.gdxunity.editor.core.scripting.JavaScriptCompiler;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphHost;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphInstance;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphRuntimeException;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphTypeScriptGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphValidator;
import io.github.ldev22live.gdxunity.engine.visualscript.KeyNames;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The example graphs that mirror the sample games' scripts (TD-060, milestone C): they load, validate, generate code and behave as stated. */
class GraphExamplesTest {
    private static final File ASSETS = new File("../assets");
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final float FRAME = 1f / 60f;
    /** The sample games' scripts the examples are modelled on. */
    private static final File SCRIPTS = new File(ASSETS, "scripts/samples");
    private static final List<String> GAMES = List.of("platformer", "collector", "hopper", "runner", "shooter", "breakout", "racing",
        "fps", "puzzle", "fighter", "grapple", "ruins", "trio", "sandbox", "walker");

    /** A host with a keyboard we press ourselves and a log of what the graph printed. */
    private static final class Keyboard implements GraphHost {
        final Set<Integer> down = new HashSet<>();
        final List<String> printed = new ArrayList<>();

        void press(String... names) {
            down.clear();
            for (String name : names) {
                down.addAll(KeyNames.parse(name));
            }
        }

        @Override
        public void log(GraphInstance instance, String message) {
            printed.add(message);
        }

        @Override
        public void error(GraphInstance instance, GraphRuntimeException error) {
            throw error;
        }

        @Override
        public boolean isKeyDown(int keycode) {
            return down.contains(keycode);
        }
    }

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static Graph load(String graphPath) throws IOException {
        return GraphSerializer.fromJson(Files.readString(new File(ASSETS, "graphs/" + graphPath).toPath()));
    }

    private static GraphInstance start(String graphPath, Entity entity, Keyboard keys) throws IOException {
        GraphInstance instance = new GraphInstance(load(graphPath), REGISTRY, entity, keys);
        instance.beginPlay();
        return instance;
    }

    private static void run(GraphInstance instance, int frames) {
        for (int i = 0; i < frames; i++) {
            instance.tick(FRAME);
        }
    }

    @Test
    void shouldListAnExampleForEverySampleGameEachSayingWhatItLeavesOut() throws IOException {
        List<GraphExamples.Example> examples = GraphExamples.load(ASSETS);

        assertEquals(GAMES.size(), examples.size());
        for (String game : GAMES) {
            assertTrue(examples.stream().anyMatch(e -> e.mirrors().startsWith(game + "/")), "an example for " + game);
        }
        for (GraphExamples.Example example : examples) {
            assertTrue(new File(ASSETS, "graphs/" + example.graph()).isFile(), example.graph());
            String script = example.mirrors().split(" ")[0];
            assertTrue(new File(SCRIPTS, script).isFile(), "the script it mirrors exists: " + script);
            assertTrue(example.notCovered().length() > 20, example.title() + " says what it does not cover");
        }
    }

    @Test
    void shouldValidateEveryExampleAndTurnItIntoJavaAndKotlin() throws IOException {
        for (GraphExamples.Example example : GraphExamples.load(ASSETS)) {
            Graph graph = load(example.graph().replace("graphs/", ""));
            assertEquals(List.of(), new GraphValidator(REGISTRY).validate(graph), example.title());
            for (GraphCodeGenerator.Language language : GraphCodeGenerator.Language.values()) {
                String source = new GraphCodeGenerator(REGISTRY).generate(graph, language);
                assertTrue(source.contains("class "), example.title() + " " + language);
            }
        }
    }

    @Test
    void shouldCompileTheGeneratedJavaOfEveryExample(@TempDir Path temp) throws IOException {
        List<File> files = new ArrayList<>();
        Path sources = Files.createDirectories(temp.resolve("src"));
        for (GraphExamples.Example example : GraphExamples.load(ASSETS)) {
            Graph graph = load(example.graph().replace("graphs/", ""));
            Path file = sources.resolve(GraphCodeGenerator.className(graph) + ".java");
            Files.writeString(file, new GraphCodeGenerator(REGISTRY).generate(graph, GraphCodeGenerator.Language.JAVA));
            files.add(file.toFile());
        }

        CompilationResult result = new JavaScriptCompiler().compile(files, temp.resolve("out").toFile(), null);

        assertFalse(result.hasErrors(), "the examples' Java did not compile: " + result.diagnostics());
    }

    @Test
    void shouldGenerateTypeScriptForTheKeyboardNodesWithTheBrowsersKeyCodes() throws IOException {
        String typeScript = new GraphTypeScriptGenerator(REGISTRY).generate(load("samples/HopperPlayer.graph"));

        assertTrue(typeScript.contains("this.input.isDown(\"KeyA\", \"ArrowLeft\")"), typeScript);
        assertTrue(typeScript.contains("this.input.isDown(\"KeyW\", \"ArrowUp\")"), typeScript);
    }

    @Test
    void shouldRefuseTypeScriptForAnExampleThatUsesAJavaMethodNodeAndSayWhy() throws IOException {
        Graph graph = load("samples/ShooterShip.graph");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> new GraphTypeScriptGenerator(REGISTRY).generate(graph));

        assertTrue(refused.getMessage().contains("Java method node"), refused.getMessage());
    }

    @Test
    void shouldFlyTheShooterShipAtTheScriptsSpeedAndStopItAtTheEdgeOfTheBox() throws IOException {
        Scene scene = new Scene("Level");
        Entity ship = scene.createEntity("Ship");
        ship.getTransform().setLocalPosition(300f, 100f, 0f);
        Keyboard keys = new Keyboard();
        GraphInstance graph = start("samples/ShooterShip.graph", ship, keys);

        keys.press("D");
        run(graph, 30);
        assertEquals(300f + 340f * 0.5f, ship.getTransform().getWorldPosition().x, 1f, "half a second to the right");

        run(graph, 120);
        assertEquals(610f, ship.getTransform().getWorldPosition().x, 0.01f, "stopped at the right edge");

        keys.press("UP", "LEFT");
        run(graph, 300);
        Vector3 end = ship.getTransform().getWorldPosition();
        assertEquals(30f, end.x, 0.01f);
        assertEquals(260f, end.y, 0.01f);
    }

    @Test
    void shouldSwitchLanesOncePerPressAndSlideToTheNewLane() throws IOException {
        Scene scene = new Scene("Level");
        Entity runner = scene.createEntity("Runner");
        Keyboard keys = new Keyboard();
        GraphInstance graph = start("samples/RunnerPlayer.graph", runner, keys);

        keys.press("D");
        run(graph, 90);
        assertEquals(130f, runner.getTransform().getWorldPosition().x, 0.5f, "one press, one lane to the right, held for a second and a half");

        keys.press();
        run(graph, 2);
        keys.press("A");
        run(graph, 90);
        keys.press();
        run(graph, 2);
        keys.press("A");
        run(graph, 90);
        assertEquals(-130f, runner.getTransform().getWorldPosition().x, 0.5f, "two presses of A: to the middle and then the left lane");

        keys.press();
        keys.press("A");
        run(graph, 90);
        assertEquals(-130f, runner.getTransform().getWorldPosition().x, 0.5f, "there is no fourth lane");
    }

    @Test
    void shouldWalkThePuzzlePlayerOneCellPerPress() throws IOException {
        Scene scene = new Scene("Level");
        Entity player = scene.createEntity("Player");
        Keyboard keys = new Keyboard();
        GraphInstance graph = start("samples/PuzzlePlayer.graph", player, keys);

        keys.press("RIGHT");
        run(graph, 30);
        assertEquals(100f, player.getTransform().getWorldPosition().x, 0.01f, "held for half a second: still one cell");

        keys.press();
        run(graph, 1);
        keys.press("UP");
        run(graph, 1);
        assertEquals(-100f, player.getTransform().getWorldPosition().z, 0.01f, "up is into the screen");
    }

    @Test
    void shouldSlowTheFighterWhileBlocking() throws IOException {
        Scene scene = new Scene("Level");
        Entity fighter = scene.createEntity("Fighter");
        Keyboard keys = new Keyboard();
        GraphInstance graph = start("samples/FighterWalk.graph", fighter, keys);

        keys.press("D");
        run(graph, 60);
        assertEquals(260f, fighter.getTransform().getWorldPosition().x, 1f);

        keys.press("D", "S");
        run(graph, 60);
        assertEquals(260f + 110f, fighter.getTransform().getWorldPosition().x, 1f);
    }

    @Test
    void shouldWalkTheArenaPlayerTheWayItLooks() throws IOException {
        Scene scene = new Scene("Level");
        Entity player = scene.createEntity("Player");
        Keyboard keys = new Keyboard();
        GraphInstance graph = start("samples/ArenaPlayer.graph", player, keys);

        keys.press("W");
        run(graph, 60);
        Vector3 ahead = player.getTransform().getWorldPosition();
        assertEquals(-320f, ahead.z, 1f, "facing -Z: a second of W is 320 units into the screen");
        assertEquals(0f, ahead.x, 0.5f);

        keys.press("LEFT");
        run(graph, 36);
        keys.press("W");
        run(graph, 60);
        Vector3 turned = player.getTransform().getWorldPosition();
        assertEquals(-320f, turned.x, 2f, "turned 90 degrees to the left: now W walks along -X");
        assertEquals(-320f, turned.z, 2f, "and the earlier z is kept");
    }

    @Test
    void shouldDriveTheRaceCarWhereItPoints() throws IOException {
        Scene scene = new Scene("Level");
        Entity car = scene.createEntity("Car");
        Keyboard keys = new Keyboard();
        GraphInstance graph = start("samples/RaceCar.graph", car, keys);

        keys.press("W");
        run(graph, 120);
        Vector3 straight = car.getTransform().getWorldPosition();
        assertTrue(straight.z < -300f, "accelerating along -Z: " + straight);
        assertEquals(0f, straight.x, 0.5f);

        keys.press("W", "A");
        run(graph, 90);
        Vector3 bent = car.getTransform().getWorldPosition();
        assertTrue(bent.x < -50f, "turning left bends the path towards -X: " + bent);
    }

    @Test
    void shouldReportAnUnknownKeyNameAsAnErrorThatNamesTheNode() throws IOException {
        Graph graph = load("samples/ShooterShip.graph");
        graph.getNodes().stream().filter(n -> n.getTypeId().equals("input.axis")).findFirst().orElseThrow()
            .setProperty("positive", "NOT_A_KEY");
        Entity ship = new Scene("Level").createEntity("Ship");
        List<GraphRuntimeException> errors = new ArrayList<>();
        GraphInstance instance = new GraphInstance(graph, REGISTRY, ship, new GraphHost() {
            @Override
            public void log(GraphInstance i, String message) {
            }

            @Override
            public void error(GraphInstance i, GraphRuntimeException error) {
                errors.add(error);
            }
        });
        instance.beginPlay();

        instance.tick(FRAME);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("NOT_A_KEY"), errors.get(0).getMessage());
    }
}
