package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
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
import io.github.ldev22live.gdxunity.engine.visualscript.GraphTypeScriptGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;
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
 * Graphs with nodes made from Java methods (TD-060) become Java and Kotlin scripts that compile and do what the interpreter does
 * with the same graph; TypeScript source generation refuses method nodes even when the web interpreter supports a subset.
 */
class GraphMethodNodeCodeTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final String RENAME = "java:io.github.ldev22live.gdxunity.engine.scene.Entity#setName(java.lang.String)";
    private static final String GET_NAME = "java:io.github.ldev22live.gdxunity.engine.scene.Entity#getName()";
    private static final String LENGTH = "java:com.badlogic.gdx.math.Vector3#len()";
    private static final String NORMALIZE = "java:com.badlogic.gdx.math.Vector3#nor()";
    private static final String CLAMP = "java:com.badlogic.gdx.math.MathUtils#clamp(int,int,int)";
    private static final List<String> EXPECTED = List.of("Renamed", "5", "10", "1");

    private Application previousApp;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void restoreApp() {
        Gdx.app = previousApp;
    }

    /**
     * Begin Play, then: rename the entity (an entity target nobody wired is the entity itself), print its name; print the length of
     * the vector (3, 4, 0); print a whole-number clamp; normalize the vector (0, 0, 5), an Action that keeps its result, and print
     * the length of that result.
     */
    private static Graph methodGraph() {
        Graph graph = new Graph("method nodes");
        Builder b = new Builder(graph);
        String begin = b.node(BuiltInNodes.EVENT_BEGIN_PLAY);

        String rename = b.node(RENAME);
        graph.getNode(rename).setLiteral("p0", "Renamed");
        String printName = b.node(BuiltInNodes.ACTION_PRINT);
        b.wire(b.node(GET_NAME), "result", printName, "message");

        String make = b.node(BuiltInNodes.VECTOR_MAKE);
        graph.getNode(make).setLiteral("x", 3f);
        graph.getNode(make).setLiteral("y", 4f);
        String length = b.node(LENGTH);
        b.wire(make, "vector", length, "target");
        String printLength = b.printNumber(length);

        String clamp = b.node(CLAMP);
        graph.getNode(clamp).setLiteral("p0", 42);
        graph.getNode(clamp).setLiteral("p1", 0);
        graph.getNode(clamp).setLiteral("p2", 10);
        String printClamp = b.printNumber(clamp);

        String direction = b.node(BuiltInNodes.VECTOR_MAKE);
        graph.getNode(direction).setLiteral("z", 5f);
        String normalize = b.node(NORMALIZE);
        b.wire(direction, "vector", normalize, "target");
        String unitLength = b.node(LENGTH);
        b.wire(normalize, "result", unitLength, "target");
        String printUnit = b.printNumber(unitLength);

        b.exec(begin, rename);
        b.exec(rename, printName);
        b.exec(printName, printLength);
        b.exec(printLength, printClamp);
        b.exec(printClamp, normalize);
        b.exec(normalize, printUnit);
        return graph;
    }

    private static final class Builder {
        private final Graph graph;

        Builder(Graph graph) {
            this.graph = graph;
        }

        String node(String type) {
            GraphNode node = new GraphNode(graph.newNodeId(), type, 0f, 0f);
            graph.addNode(node);
            return node.getId();
        }

        void wire(String from, String fromPin, String to, String toPin) {
            graph.addLink(new GraphLink(from, fromPin, to, toPin));
        }

        void exec(String from, String to) {
            wire(from, PinDef.EXEC_OUT, to, PinDef.EXEC_IN);
        }

        /** A Print of a method node's number (a whole number goes into the float pin of Number to Text). */
        String printNumber(String methodNode) {
            String text = node(BuiltInNodes.TEXT_FROM_FLOAT);
            wire(methodNode, "result", text, "value");
            String print = node(BuiltInNodes.ACTION_PRINT);
            wire(text, "text", print, "message");
            return print;
        }
    }

    private static final class Outcome {
        final List<String> lines = new ArrayList<>();
        String entityName;
    }

    private static Outcome interpret(Graph graph) {
        Outcome outcome = new Outcome();
        Scene scene = new Scene("Interpreted");
        Entity entity = scene.createEntity("Self");
        new GraphInstance(graph, REGISTRY, entity, new GraphHost() {
            @Override
            public void log(GraphInstance i, String message) {
                outcome.lines.add(message);
            }

            @Override
            public void error(GraphInstance i, GraphRuntimeException error) {
                throw error;
            }
        }).beginPlay();
        outcome.entityName = entity.getName();
        return outcome;
    }

    private Outcome runGenerated(Graph graph, Language language, Path temp) throws Exception {
        String source = new GraphCodeGenerator(REGISTRY).generate(graph, language);
        Path dir = Files.createDirectories(temp.resolve("src"));
        String className = GraphCodeGenerator.className(graph);
        Path file = dir.resolve(className + language.getExtension());
        Files.writeString(file, source);
        File out = temp.resolve("out").toFile();
        CompilationResult result = language == Language.JAVA
            ? new JavaScriptCompiler().compile(List.of(file.toFile()), out, null)
            : new KotlinScriptCompiler().compile(List.of(file.toFile()), out, List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
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
            outcome.entityName = entity.getName();
        }
        return outcome;
    }

    @Test
    void shouldRunTheMethodGraphInTheInterpreter() {
        Outcome outcome = interpret(methodGraph());

        assertEquals(EXPECTED, outcome.lines);
        assertEquals("Renamed", outcome.entityName);
    }

    @Test
    void shouldMakeJavaThatCompilesAndDoesWhatTheInterpreterDoes(@TempDir Path temp) throws Exception {
        Outcome generated = runGenerated(methodGraph(), Language.JAVA, temp);

        assertEquals(EXPECTED, generated.lines);
        assertEquals("Renamed", generated.entityName);
    }

    @Test
    void shouldMakeKotlinThatCompilesAndDoesWhatTheInterpreterDoes(@TempDir Path temp) throws Exception {
        Outcome generated = runGenerated(methodGraph(), Language.KOTLIN, temp);

        assertEquals(EXPECTED, generated.lines);
        assertEquals("Renamed", generated.entityName);
    }

    @Test
    void shouldCallTheMethodsByTheirSourceNamesAndKeepAnActionsResultInAField() {
        String java = new GraphCodeGenerator(REGISTRY).generate(methodGraph(), Language.JAVA);

        assertTrue(java.contains("com.badlogic.gdx.math.MathUtils.clamp(42, 0, 10)"), java);
        assertTrue(java.contains("getEntity().setName(\"Renamed\")"), java);
        assertTrue(java.contains("private Vector3 "), "the result of nor() is kept: " + java);
    }

    @Test
    void shouldExplainTheTypeScriptSourceGenerationLimitForMethodNodes() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new GraphTypeScriptGenerator(REGISTRY).generate(methodGraph()));

        assertTrue(error.getMessage().contains("TypeScript source generation"), error.getMessage());
    }
}
