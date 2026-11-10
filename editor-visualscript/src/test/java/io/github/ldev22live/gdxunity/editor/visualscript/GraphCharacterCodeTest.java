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
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java and Kotlin generated from a graph that uses the character nodes (TD-049) compile, and do what the interpreter does:
 * the same messages, and the same moves reach the physics backend.
 */
class GraphCharacterCodeTest {

    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final float FRAME = 1f / 60f;
    private static final int FRAMES = 3;
    /** Moves the world's setup makes before the graph runs: one step to create the character, one to learn the ground. */
    private static final int SETUP_MOVES = 2;

    private Application previousApp;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void restoreApp() {
        Gdx.app = previousApp;
    }

    /** What a run produced: printed lines and the moves that reached the backend, as text. */
    private static final class Outcome {
        final List<String> lines = new ArrayList<>();
        List<String> moves;
    }

    private static final class Builder {
        final Graph graph = new Graph("character probe");

        String node(String type) {
            GraphNode node = new GraphNode(graph.newNodeId(), type, 0f, 0f);
            graph.addNode(node);
            return node.getId();
        }

        String node(String type, String pin, Object literal) {
            String id = node(type);
            graph.getNode(id).setLiteral(pin, literal);
            return id;
        }

        void wire(String from, String fromPin, String to, String toPin) {
            graph.addLink(new GraphLink(from, fromPin, to, toPin));
        }

        void exec(String from, String to) {
            wire(from, PinDef.EXEC_OUT, to, PinDef.EXEC_IN);
        }
    }

    /** Begin Play: print "grounded" or "air" by Is Grounded. Tick: walk at 3 m/s along x, then jump at 4 m/s. */
    private static Graph probeGraph() {
        Builder b = new Builder();
        String begin = b.node(BuiltInNodes.EVENT_BEGIN_PLAY);
        String branch = b.node(BuiltInNodes.FLOW_BRANCH);
        String grounded = b.node(BuiltInNodes.DATA_IS_GROUNDED);
        b.wire(grounded, "grounded", branch, "condition");
        b.exec(begin, branch);
        b.wire(branch, "true", b.node(BuiltInNodes.ACTION_PRINT, "message", "grounded"), PinDef.EXEC_IN);
        b.wire(branch, "false", b.node(BuiltInNodes.ACTION_PRINT, "message", "air"), PinDef.EXEC_IN);
        String tick = b.node(BuiltInNodes.EVENT_TICK);
        String move = b.node(BuiltInNodes.ACTION_MOVE_CHARACTER, "velocity", new Vector3(300f, 0f, 0f));
        String jump = b.node(BuiltInNodes.ACTION_JUMP_CHARACTER, "speed", 400f);
        b.exec(tick, move);
        b.exec(move, jump);
        return b.graph;
    }

    /** A scene with one character that runs the graph, over a stub backend that says whether it stands on ground. */
    private static final class World {
        final Scene scene = new Scene("World");
        final Entity self = scene.createEntity("Hero");
        final StubPhysicsBackend3D backend = new StubPhysicsBackend3D();
        final PhysicsSystem3D physics;

        World(boolean grounded) {
            self.addComponent(new CharacterController3DComponent());
            physics = new PhysicsSystem3D(scene, backend);
            physics.update(FRAME);
            backend.grounded = grounded;
            physics.update(FRAME);
        }

        List<String> moves() {
            return backend.characterMoves.stream().skip(SETUP_MOVES).map(Vector3::toString).toList();
        }

        void close() {
            physics.dispose();
            scene.dispose();
        }
    }

    private static Outcome interpret(Graph graph, boolean grounded) {
        Outcome outcome = new Outcome();
        World world = new World(grounded);
        try {
            GraphInstance instance = new GraphInstance(graph, REGISTRY, world.self, new GraphHost() {
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
                world.physics.update(FRAME);
            }
            outcome.moves = world.moves();
        } finally {
            world.close();
        }
        return outcome;
    }

    private Outcome runGenerated(Graph graph, Language language, boolean grounded, Path temp) throws Exception {
        String source = new GraphCodeGenerator(REGISTRY).generate(graph, language);
        Path dir = Files.createDirectories(temp.resolve("src"));
        String className = GraphCodeGenerator.className(graph);
        Path file = dir.resolve(className + language.getExtension());
        Files.writeString(file, source);
        File out = temp.resolve("out").toFile();
        CompilationResult result = language == Language.JAVA
            ? new JavaScriptCompiler().compile(List.of(file.toFile()), out, null)
            : new KotlinScriptCompiler().compile(List.of(file.toFile()), out,
                List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
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
        World world = new World(grounded);
        try (ScriptClassLoader loader = new ScriptClassLoader(out, getClass().getClassLoader())) {
            ScriptComponent script = (ScriptComponent) loader.loadScriptClass(GraphCodeGenerator.PACKAGE + "." + className)
                .getDeclaredConstructor().newInstance();
            world.self.addComponent(script);
            script.onStart();
            for (int i = 0; i < FRAMES; i++) {
                script.onUpdate(FRAME);
                world.physics.update(FRAME);
            }
            outcome.moves = world.moves();
        } finally {
            world.close();
        }
        return outcome;
    }

    @Test
    void shouldMakeJavaThatDoesWhatTheInterpreterDoesWithCharacterNodes(@TempDir Path temp) throws Exception {
        Graph graph = probeGraph();
        Outcome expected = interpret(graph, true);

        assertEquals(List.of("grounded"), expected.lines);
        assertEquals(FRAMES, expected.moves.size());
        assertTrue(expected.moves.get(0).startsWith("(3.0,"), "walks at 3 m/s: " + expected.moves);
        Outcome generated = runGenerated(graph, Language.JAVA, true, temp);

        assertEquals(expected.lines, generated.lines, "messages");
        assertEquals(expected.moves, generated.moves, "moves that reached the backend");
    }

    @Test
    void shouldMakeKotlinThatDoesWhatTheInterpreterDoesWithCharacterNodes(@TempDir Path temp) throws Exception {
        Graph graph = probeGraph();
        Outcome expected = interpret(graph, true);

        Outcome generated = runGenerated(graph, Language.KOTLIN, true, temp);

        assertEquals(expected.lines, generated.lines, "messages");
        assertEquals(expected.moves, generated.moves, "moves that reached the backend");
    }

    @Test
    void shouldTakeTheAirBranchInGeneratedCodeToo(@TempDir Path temp) throws Exception {
        Graph graph = probeGraph();
        Outcome expected = interpret(graph, false);

        assertEquals(List.of("air"), expected.lines);
        assertEquals(expected.lines, runGenerated(graph, Language.JAVA, false, temp.resolve("java")).lines);
        assertEquals(expected.lines, runGenerated(graph, Language.KOTLIN, false, temp.resolve("kotlin")).lines);
    }
}
