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
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent;
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
 * Java and Kotlin generated from a graph that uses the 3D physics nodes (TD-048) compile, and do what the interpreter does:
 * the same messages, and the same calls reach the physics backend.
 */
class GraphPhysics3DCodeTest {

    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final float FRAME = 1f / 60f;
    private static final int FRAMES = 3;

    private Application previousApp;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void restoreApp() {
        Gdx.app = previousApp;
    }

    /** What a run produced: printed lines and the calls that reached the backend. */
    private static final class Outcome {
        final List<String> lines = new ArrayList<>();
        List<String> calls;
    }

    private static final class Builder {
        final Graph graph = new Graph("physics probe");

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

    /**
     * Begin Play: set a velocity, add an impulse, cast a ray down; on a hit print the entity, the distance and the velocity,
     * else print "miss". Tick: add a force.
     */
    private static Graph probeGraph() {
        Builder b = new Builder();
        String begin = b.node(BuiltInNodes.EVENT_BEGIN_PLAY);
        String velocity = b.node(BuiltInNodes.ACTION_SET_VELOCITY, "velocity", new Vector3(0f, 0f, 300f));
        String impulse = b.node(BuiltInNodes.ACTION_ADD_IMPULSE, "impulse", new Vector3(0f, 500f, 0f));
        String ray = b.node(BuiltInNodes.ACTION_RAYCAST_3D);
        b.graph.getNode(ray).setLiteral("origin", new Vector3(0f, 100f, 0f));
        b.graph.getNode(ray).setLiteral("direction", new Vector3(0f, -1f, 0f));
        b.graph.getNode(ray).setLiteral("distance", 500f);
        b.exec(begin, velocity);
        b.exec(velocity, impulse);
        b.exec(impulse, ray);

        String name = b.node(BuiltInNodes.DATA_GET_NAME);
        b.wire(ray, BuiltInNodes.PIN_HIT_ENTITY, name, "target");
        String hitText = b.node(BuiltInNodes.TEXT_APPEND, "a", "hit ");
        b.wire(name, "name", hitText, "b");
        String printHit = b.node(BuiltInNodes.ACTION_PRINT);
        b.wire(hitText, "result", printHit, "message");
        b.wire(ray, BuiltInNodes.PIN_HIT, printHit, PinDef.EXEC_IN);

        String distance = b.node(BuiltInNodes.TEXT_FROM_FLOAT);
        b.wire(ray, BuiltInNodes.PIN_HIT_DISTANCE, distance, "value");
        String printDistance = b.node(BuiltInNodes.ACTION_PRINT);
        b.wire(distance, "text", printDistance, "message");
        b.exec(printHit, printDistance);

        String get = b.node(BuiltInNodes.DATA_GET_VELOCITY);
        String parts = b.node(BuiltInNodes.VECTOR_BREAK);
        b.wire(get, "velocity", parts, "vector");
        String speed = b.node(BuiltInNodes.TEXT_FROM_FLOAT);
        b.wire(parts, "z", speed, "value");
        String printSpeed = b.node(BuiltInNodes.ACTION_PRINT);
        b.wire(speed, "text", printSpeed, "message");
        b.exec(printDistance, printSpeed);

        String printMiss = b.node(BuiltInNodes.ACTION_PRINT, "message", "miss");
        b.wire(ray, BuiltInNodes.PIN_MISS, printMiss, PinDef.EXEC_IN);

        String tick = b.node(BuiltInNodes.EVENT_TICK);
        b.exec(tick, b.node(BuiltInNodes.ACTION_ADD_FORCE, "force", new Vector3(100f, 0f, 0f)));
        return b.graph;
    }

    /** A scene with the entity that runs the graph (a dynamic sphere) and a wall, and 3D physics over a stub backend. */
    private static final class World {
        final Scene scene = new Scene("World");
        final Entity self = scene.createEntity("Self");
        final StubPhysicsBackend3D backend = new StubPhysicsBackend3D();
        final PhysicsSystem3D physics;

        World(boolean hit) {
            self.addComponent(new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC));
            self.addComponent(new SphereCollider3DComponent());
            scene.createEntity("Wall").addComponent(new BoxCollider3DComponent());
            physics = new PhysicsSystem3D(scene, backend);
            physics.update(0f);
            backend.hitHandle = hit ? backend.handle(1) : null;
        }

        void close() {
            physics.dispose();
            scene.dispose();
        }
    }

    private static Outcome interpret(Graph graph, boolean hit) {
        Outcome outcome = new Outcome();
        World world = new World(hit);
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
            outcome.calls = new ArrayList<>(world.backend.calls);
        } finally {
            world.close();
        }
        return outcome;
    }

    private Outcome runGenerated(Graph graph, Language language, boolean hit, Path temp) throws Exception {
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
        World world = new World(hit);
        try (ScriptClassLoader loader = new ScriptClassLoader(out, getClass().getClassLoader())) {
            ScriptComponent script = (ScriptComponent) loader.loadScriptClass(GraphCodeGenerator.PACKAGE + "." + className)
                .getDeclaredConstructor().newInstance();
            world.self.addComponent(script);
            script.onStart();
            for (int i = 0; i < FRAMES; i++) {
                script.onUpdate(FRAME);
                world.physics.update(FRAME);
            }
            outcome.calls = new ArrayList<>(world.backend.calls);
        } finally {
            world.close();
        }
        return outcome;
    }

    @Test
    void shouldMakeJavaThatDoesWhatTheInterpreterDoesWithPhysicsNodes(@TempDir Path temp) throws Exception {
        Graph graph = probeGraph();
        Outcome expected = interpret(graph, true);

        assertEquals(List.of("hit Wall", "50", "300"), expected.lines, "the interpreter itself does what the graph says");
        assertEquals(2 + FRAMES, expected.calls.size(), "velocity, impulse, then one force step per frame: " + expected.calls);
        Outcome generated = runGenerated(graph, Language.JAVA, true, temp);

        assertEquals(expected.lines, generated.lines, "messages");
        assertEquals(expected.calls, generated.calls, "calls to the backend");
    }

    @Test
    void shouldMakeKotlinThatDoesWhatTheInterpreterDoesWithPhysicsNodes(@TempDir Path temp) throws Exception {
        Graph graph = probeGraph();
        Outcome expected = interpret(graph, true);

        Outcome generated = runGenerated(graph, Language.KOTLIN, true, temp);

        assertEquals(expected.lines, generated.lines, "messages");
        assertEquals(expected.calls, generated.calls, "calls to the backend");
    }

    @Test
    void shouldTakeTheMissBranchInGeneratedCodeToo(@TempDir Path temp) throws Exception {
        Graph graph = probeGraph();
        Outcome expected = interpret(graph, false);

        assertEquals(List.of("miss"), expected.lines);
        assertEquals(expected.lines, runGenerated(graph, Language.JAVA, false, temp.resolve("java")).lines);
        assertEquals(expected.lines, runGenerated(graph, Language.KOTLIN, false, temp.resolve("kotlin")).lines);
    }

    @Test
    void shouldGenerateTheRequireCallSoASceneWithoutPhysicsFailsWithAdvice() {
        Builder b = new Builder();
        b.exec(b.node(BuiltInNodes.EVENT_BEGIN_PLAY), b.node(BuiltInNodes.ACTION_ADD_IMPULSE, "impulse", new Vector3(0f, 1f, 0f)));
        String source = new GraphCodeGenerator(REGISTRY).generate(b.graph, Language.JAVA);

        assertTrue(source.contains("requirePhysics3D().addImpulse(getEntity(), new Vector3(0.0f, 1.0f, 0.0f))"), source);
        assertTrue(source.contains("// forgeroom-graph:begin"), "the graph travels with the script");
    }
}
