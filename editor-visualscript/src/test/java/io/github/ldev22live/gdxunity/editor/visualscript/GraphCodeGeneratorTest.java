package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.scripting.CompilationResult;
import io.github.ldev22live.gdxunity.editor.core.scripting.JavaScriptCompiler;
import io.github.ldev22live.gdxunity.editor.core.scripting.KotlinScriptCompiler;
import io.github.ldev22live.gdxunity.editor.core.scripting.ScriptClassLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator.Language;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphHost;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphInstance;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphRuntimeException;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphVariable;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;
import io.github.ldev22live.gdxunity.engine.visualscript.ValueType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Code generated from a graph (TD-044): it compiles in both languages, and a script made from a graph does what the
 * interpreter does with that graph: same messages, same position and rotation after the same frames.
 */
class GraphCodeGeneratorTest {

    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final float FRAME = 0.1f;
    private static final int FRAMES = 20;
    private static final float TOLERANCE = 1e-3f;
    private static final String MESSAGE = "start \"quoted\" $dollar \\ back";

    private Application previousApp;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void restoreApp() {
        Gdx.app = previousApp;
    }

    private static Graph pulseGraph() {
        Graph graph = new Graph("pulse test");
        graph.putVariable(new GraphVariable("count", ValueType.FLOAT, 0f));
        graph.putVariable(new GraphVariable("message", ValueType.STRING, MESSAGE));
        Builder b = new Builder(graph);

        String begin = b.node(BuiltInNodes.EVENT_BEGIN_PLAY);
        String printStart = b.node(BuiltInNodes.ACTION_PRINT);
        b.wire(b.node(BuiltInNodes.VARIABLE_GET, "variable", "message"), "value", printStart, "message");
        String setCount = b.node(BuiltInNodes.VARIABLE_SET, "variable", "count");
        String getCount = b.node(BuiltInNodes.VARIABLE_GET, "variable", "count");
        String plusOne = b.node(BuiltInNodes.MATH_ADD);
        graph.getNode(plusOne).setLiteral("b", 1f);
        b.wire(getCount, "value", plusOne, "a");
        b.wire(plusOne, "result", setCount, "value");
        String branch = b.node(BuiltInNodes.FLOW_BRANCH);
        String greater = b.node(BuiltInNodes.MATH_GREATER);
        b.wire(b.node(BuiltInNodes.VARIABLE_GET, "variable", "count"), "value", greater, "a");
        b.wire(greater, "result", branch, "condition");
        String setPosition = b.node(BuiltInNodes.ACTION_SET_POSITION);
        String make = b.node(BuiltInNodes.VECTOR_MAKE);
        graph.getNode(make).setLiteral("x", 1f);
        graph.getNode(make).setLiteral("y", 2f);
        graph.getNode(make).setLiteral("z", 3f);
        b.wire(make, "vector", setPosition, "position");
        String never = b.node(BuiltInNodes.ACTION_PRINT);
        graph.getNode(never).setLiteral("message", "never");
        b.exec(begin, printStart);
        b.exec(printStart, setCount);
        b.exec(setCount, branch);
        graph.addLink(new GraphLink(branch, "true", setPosition, PinDef.EXEC_IN));
        graph.addLink(new GraphLink(branch, "false", never, PinDef.EXEC_IN));

        // Every tick moves along x by the frame time
        String tick = b.node(BuiltInNodes.EVENT_TICK);
        String moveBy = b.node(BuiltInNodes.ACTION_MOVE_BY);
        String unit = b.node(BuiltInNodes.VECTOR_MAKE);
        graph.getNode(unit).setLiteral("x", 1f);
        String scale = b.node(BuiltInNodes.VECTOR_SCALE);
        b.wire(unit, "vector", scale, "vector");
        b.wire(tick, BuiltInNodes.PIN_DELTA_TIME, scale, "factor");
        b.wire(scale, "result", moveBy, "offset");
        String both = b.node(BuiltInNodes.FLOW_SEQUENCE);
        b.exec(tick, both);
        graph.addLink(new GraphLink(both, "then0", moveBy, PinDef.EXEC_IN));

        // ... and after each 0.35 s delay turns by 90 degrees and reports the count
        String delay = b.node(BuiltInNodes.FLOW_DELAY);
        graph.getNode(delay).setLiteral("seconds", 0.35f);
        String rotate = b.node(BuiltInNodes.ACTION_ROTATE_BY);
        String turn = b.node(BuiltInNodes.VECTOR_MAKE);
        graph.getNode(turn).setLiteral("y", 90f);
        b.wire(turn, "vector", rotate, "degrees");
        String setAgain = b.node(BuiltInNodes.VARIABLE_SET, "variable", "count");
        String plusOneAgain = b.node(BuiltInNodes.MATH_ADD);
        graph.getNode(plusOneAgain).setLiteral("b", 1f);
        b.wire(b.node(BuiltInNodes.VARIABLE_GET, "variable", "count"), "value", plusOneAgain, "a");
        b.wire(plusOneAgain, "result", setAgain, "value");
        String report = b.node(BuiltInNodes.ACTION_PRINT);
        String append = b.node(BuiltInNodes.TEXT_APPEND);
        graph.getNode(append).setLiteral("a", "count=");
        String asText = b.node(BuiltInNodes.TEXT_FROM_FLOAT);
        b.wire(b.node(BuiltInNodes.VARIABLE_GET, "variable", "count"), "value", asText, "value");
        b.wire(asText, "text", append, "b");
        b.wire(append, "result", report, "message");
        graph.addLink(new GraphLink(both, "then1", delay, PinDef.EXEC_IN));
        graph.addLink(new GraphLink(delay, "completed", rotate, PinDef.EXEC_IN));
        b.exec(rotate, setAgain);
        b.exec(setAgain, report);
        return graph;
    }

    /** Adds nodes and wires with fresh ids. */
    private static final class Builder {
        private final Graph graph;

        Builder(Graph graph) {
            this.graph = graph;
        }

        String node(String type, String... properties) {
            GraphNode node = new GraphNode(graph.newNodeId(), type, 0f, 0f);
            for (int i = 0; i < properties.length; i += 2) {
                node.setProperty(properties[i], properties[i + 1]);
            }
            graph.addNode(node);
            return node.getId();
        }

        void wire(String from, String fromPin, String to, String toPin) {
            graph.addLink(new GraphLink(from, fromPin, to, toPin));
        }

        void exec(String from, String to) {
            wire(from, PinDef.EXEC_OUT, to, PinDef.EXEC_IN);
        }
    }

    /** What running the graph (interpreted or generated) produced. */
    private static final class Outcome {
        final List<String> lines = new ArrayList<>();
        Vector3 position;
        Vector3 euler;
    }

    private static Outcome interpret(Graph graph) {
        Outcome outcome = new Outcome();
        Scene scene = new Scene("Interpreted");
        Entity entity = scene.createEntity("Self");
        GraphInstance instance = new GraphInstance(graph, REGISTRY, entity, new GraphHost() {
            @Override
            public void log(GraphInstance i, String message) {
                outcome.lines.add(message);
            }

            @Override
            public void error(GraphInstance i, GraphRuntimeException error) {
                throw error;
            }
        });
        instance.beginPlay();
        for (int i = 0; i < FRAMES; i++) {
            instance.tick(FRAME);
        }
        outcome.position = entity.getTransform().getWorldPosition();
        outcome.euler = entity.getTransform().getLocalEulerAngles();
        return outcome;
    }

    private static CompilationResult compile(Language language, Path file, File out) {
        return language == Language.JAVA
            ? new JavaScriptCompiler().compile(List.of(file.toFile()), out, null)
            : new KotlinScriptCompiler().compile(List.of(file.toFile()), out,
                List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
    }

    private Outcome runGenerated(Graph graph, Language language, Path temp) throws Exception {
        String source = new GraphCodeGenerator(REGISTRY).generate(graph, language);
        Path dir = Files.createDirectories(temp.resolve("src"));
        String className = GraphCodeGenerator.className(graph);
        Path file = dir.resolve(className + language.getExtension());
        Files.writeString(file, source);
        File out = temp.resolve("out").toFile();
        CompilationResult result = compile(language, file, out);
        assertFalse(result.hasErrors(), language + " did not compile: " + result.diagnostics() + "\n" + source);

        Outcome outcome = new Outcome();
        previousApp = Gdx.app;
        Gdx.app = (Application) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Application.class},
            (proxy, method, args) -> {
                if (method.getName().equals("log") && args != null && args.length == 2) {
                    outcome.lines.add((String) args[1]);
                }
                return null;
            });
        try (ScriptClassLoader loader = new ScriptClassLoader(out, getClass().getClassLoader())) {
            Scene scene = new Scene("Generated");
            Entity entity = scene.createEntity("Self");
            ScriptComponent script = (ScriptComponent) loader.loadScriptClass(GraphCodeGenerator.PACKAGE + "." + className)
                .getDeclaredConstructor().newInstance();
            entity.addComponent(script);
            script.onStart();
            for (int i = 0; i < FRAMES; i++) {
                script.onUpdate(FRAME);
            }
            outcome.position = entity.getTransform().getWorldPosition();
            outcome.euler = entity.getTransform().getLocalEulerAngles();
        }
        return outcome;
    }

    private static void assertSame(Outcome expected, Outcome actual, String what) {
        assertEquals(expected.lines, actual.lines, what + ": messages");
        assertEquals(expected.position.x, actual.position.x, TOLERANCE, what + ": x");
        assertEquals(expected.position.y, actual.position.y, TOLERANCE, what + ": y");
        assertEquals(expected.position.z, actual.position.z, TOLERANCE, what + ": z");
        assertEquals(expected.euler.x, actual.euler.x, TOLERANCE, what + ": yaw");
    }

    @Test
    void shouldMakeJavaThatDoesWhatTheInterpreterDoes(@TempDir Path temp) throws Exception {
        Graph graph = pulseGraph();
        Outcome expected = interpret(graph);

        assertTrue(expected.lines.size() > 3, "the graph prints at start and at every delay: " + expected.lines);
        assertEquals(MESSAGE, expected.lines.get(0));
        assertSame(expected, runGenerated(graph, Language.JAVA, temp), "Java");
    }

    @Test
    void shouldMakeKotlinThatDoesWhatTheInterpreterDoes(@TempDir Path temp) throws Exception {
        Graph graph = pulseGraph();
        assertSame(interpret(graph), runGenerated(graph, Language.KOTLIN, temp), "Kotlin");
    }

    @Test
    void shouldNameTheClassAfterTheGraphAndKeepItInTheGeneratedPackage() {
        Graph graph = pulseGraph();

        assertEquals("PulseTest", GraphCodeGenerator.className(graph));
        String java = new GraphCodeGenerator(REGISTRY).generate(graph, Language.JAVA);
        assertTrue(java.contains("package generated;"));
        assertTrue(java.contains("public class PulseTest extends ScriptComponent"));
        assertTrue(new GraphCodeGenerator(REGISTRY).generate(graph, Language.KOTLIN).contains("class PulseTest : ScriptComponent()"));
        assertEquals("Graph2d", GraphCodeGenerator.className(new Graph("2d")));
    }

    @Test
    void shouldRefuseAGraphThatTheInterpreterWouldRefuse() {
        Graph graph = new Graph("Broken");
        graph.addNode(new GraphNode("n1", "unknown.type", 0f, 0f));

        assertThrows(IllegalArgumentException.class, () -> new GraphCodeGenerator(REGISTRY).generate(graph, Language.JAVA));
    }

    @Test
    void shouldRefuseExecutionWiresThatLoopBack() {
        Graph graph = new Graph("Loop");
        Builder b = new Builder(graph);
        String begin = b.node(BuiltInNodes.EVENT_BEGIN_PLAY);
        String print = b.node(BuiltInNodes.ACTION_PRINT);
        String again = b.node(BuiltInNodes.ACTION_PRINT);
        b.exec(begin, print);
        b.exec(print, again);
        b.exec(again, print);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new GraphCodeGenerator(REGISTRY).generate(graph, Language.JAVA));
        assertTrue(error.getMessage().contains("loop"), error.getMessage());
    }

    @Test
    void shouldMakeCollisionAndCustomEventsCallable(@TempDir Path temp) throws Exception {
        Graph graph = new Graph("Events");
        Builder b = new Builder(graph);
        String collision = b.node(BuiltInNodes.EVENT_COLLISION);
        String printOther = b.node(BuiltInNodes.ACTION_PRINT);
        String name = b.node(BuiltInNodes.DATA_GET_NAME);
        b.wire(collision, BuiltInNodes.PIN_OTHER, name, "target");
        b.wire(name, "name", printOther, "message");
        b.exec(collision, printOther);
        String custom = b.node(BuiltInNodes.EVENT_CUSTOM, BuiltInNodes.PROPERTY_NAME, "open");
        String printOpen = b.node(BuiltInNodes.ACTION_PRINT);
        graph.getNode(printOpen).setLiteral("message", "opened");
        b.exec(custom, printOpen);

        for (Language language : Language.values()) {
            String source = new GraphCodeGenerator(REGISTRY).generate(graph, language);
            assertTrue(source.contains("onCollision"), source);
            assertTrue(source.contains("fireCustomEvent"), source);
            Path dir = Files.createDirectories(temp.resolve(language.name()));
            Path file = dir.resolve("Events" + language.getExtension());
            Files.writeString(file, source);
            CompilationResult result = compile(language, file, dir.resolve("out").toFile());
            assertFalse(result.hasErrors(), language + ": " + result.diagnostics() + "\n" + source);
        }
    }
}
