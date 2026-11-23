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
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavAgent3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavMeshBoundsComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator.Language;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M6.4 (TD-092): Java and Kotlin generated from a behaviour graph with the Navigation nodes compile, and the compiled script
 * walks a guard round a wall to its goal, as the interpreter does.
 */
class GraphNavigationCodeTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final float FRAME = 1f / 60f;
    private static final Vector3 GOAL = new Vector3(600f, 0f, 0f);

    private Application previousApp;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void restoreApp() {
        Gdx.app = previousApp;
    }

    /** Begin Play: Move To the goal (print "walking" or "no way"). Tick: print "there" while Has Arrived. */
    private static Graph patrol() {
        Graph graph = new Graph("Patrol");
        GraphNode begin = add(graph, BuiltInNodes.EVENT_BEGIN_PLAY);
        GraphNode move = add(graph, BuiltInNodes.ACTION_MOVE_TO);
        move.setLiteral("destination", new Vector3(GOAL));
        GraphNode walking = add(graph, BuiltInNodes.ACTION_PRINT);
        walking.setLiteral("message", "walking");
        GraphNode noWay = add(graph, BuiltInNodes.ACTION_PRINT);
        noWay.setLiteral("message", "no way");
        GraphNode tick = add(graph, BuiltInNodes.EVENT_TICK);
        GraphNode branch = add(graph, BuiltInNodes.FLOW_BRANCH);
        GraphNode arrived = add(graph, BuiltInNodes.DATA_HAS_ARRIVED);
        GraphNode there = add(graph, BuiltInNodes.ACTION_PRINT);
        there.setLiteral("message", "there");
        graph.addLink(new GraphLink(begin.getId(), PinDef.EXEC_OUT, move.getId(), PinDef.EXEC_IN));
        graph.addLink(new GraphLink(move.getId(), BuiltInNodes.PIN_MOVING, walking.getId(), PinDef.EXEC_IN));
        graph.addLink(new GraphLink(move.getId(), BuiltInNodes.PIN_UNREACHABLE, noWay.getId(), PinDef.EXEC_IN));
        graph.addLink(new GraphLink(tick.getId(), PinDef.EXEC_OUT, branch.getId(), PinDef.EXEC_IN));
        graph.addLink(new GraphLink(arrived.getId(), "arrived", branch.getId(), "condition"));
        graph.addLink(new GraphLink(branch.getId(), "true", there.getId(), PinDef.EXEC_IN));
        return graph;
    }

    private static GraphNode add(Graph graph, String type) {
        GraphNode node = new GraphNode(graph.newNodeId(), type, 0f, 0f);
        graph.addNode(node);
        return node;
    }

    @Test
    void shouldCompileJavaAndKotlinThatWalkTheGuardRoundTheWall(@TempDir Path temp) throws Exception {
        for (Language language : Language.values()) {
            Path dir = temp.resolve(language.name());
            Graph graph = patrol();
            String source = new GraphCodeGenerator(REGISTRY).generate(graph, language);
            Path src = Files.createDirectories(dir.resolve("src"));
            String className = GraphCodeGenerator.className(graph);
            Path file = src.resolve(className + language.getExtension());
            Files.writeString(file, source);
            File out = dir.resolve("out").toFile();
            CompilationResult result = language == Language.JAVA
                ? new JavaScriptCompiler().compile(List.of(file.toFile()), out, null)
                : new KotlinScriptCompiler().compile(List.of(file.toFile()), out,
                    List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
            assertFalse(result.hasErrors(), language + " did not compile: " + result.diagnostics() + "\n" + source);

            List<String> lines = new ArrayList<>();
            previousApp = Gdx.app;
            Gdx.app = (Application) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Application.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("log") && args != null && args.length == 2) {
                        lines.add((String) args[1]);
                    }
                    return null;
                });
            Scene scene = courtyard();
            Entity guard = scene.findEntityByName("Guard");
            try (ScriptClassLoader loader = new ScriptClassLoader(out, getClass().getClassLoader())) {
                ScriptComponent script = (ScriptComponent) loader.loadScriptClass(GraphCodeGenerator.PACKAGE + "." + className)
                    .getDeclaredConstructor().newInstance();
                guard.addComponent(script);
                for (int i = 0; i < 8 * 60; i++) {
                    scene.update(FRAME);
                }
            } finally {
                scene.dispose();
                Gdx.app = previousApp;
            }
            assertTrue(lines.contains("walking"), language + ": " + lines);
            assertTrue(lines.contains("there"), language + ": Has Arrived turned true");
            Vector3 at = guard.getTransform().getWorldPosition();
            assertTrue(Math.hypot(at.x - GOAL.x, at.z - GOAL.z) <= NavAgent3DComponent.DEFAULT_STOPPING_DISTANCE + 1e-3f,
                language + ": at " + at);
        }
    }

    /** Flat ground, a wall between x -600 and x 600, Nav Mesh Bounds, and a guard with a Nav Agent at x -600. */
    private static Scene courtyard() {
        Scene scene = new Scene("Courtyard");
        Entity ground = scene.createEntity("Ground");
        ground.getTransform().setLocalPosition(0f, -10f, 0f);
        ground.addComponent(new BoxCollider3DComponent(new Vector3(2000f, 20f, 2000f)));
        Entity wall = scene.createEntity("Wall");
        wall.getTransform().setLocalPosition(0f, 100f, 0f);
        wall.addComponent(new BoxCollider3DComponent(new Vector3(100f, 200f, 1200f)));
        scene.createEntity("Bounds").addComponent(new NavMeshBoundsComponent());
        Entity guard = scene.createEntity("Guard");
        guard.getTransform().setLocalPosition(-600f, 0f, 0f);
        guard.addComponent(new NavAgent3DComponent());
        return scene;
    }
}
