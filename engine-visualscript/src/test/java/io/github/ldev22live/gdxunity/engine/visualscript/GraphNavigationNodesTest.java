package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavAgent3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavMeshBoundsComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes.ACTION_MOVE_TO;
import static io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes.ACTION_PRINT;
import static io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes.ACTION_STOP_MOVING;
import static io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes.DATA_HAS_ARRIVED;
import static io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes.EVENT_BEGIN_PLAY;
import static io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes.EVENT_TICK;
import static io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes.FLOW_BRANCH;
import static io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes.PIN_MOVING;
import static io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes.PIN_UNREACHABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour graphs for roadmap M6.4 (TD-092): Move To, Stop Moving and Has Arrived drive a Nav Agent round a wall, in the
 * interpreter and in the generated Java and Kotlin; TypeScript refuses them with the reason.
 */
class GraphNavigationNodesTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final Vector3 GOAL = new Vector3(600f, 0f, 0f);

    private static final class Host implements GraphHost {
        final List<String> lines = new ArrayList<>();
        final List<GraphRuntimeException> errors = new ArrayList<>();

        @Override
        public void log(GraphInstance instance, String message) {
            lines.add(message);
        }

        @Override
        public void error(GraphInstance instance, GraphRuntimeException error) {
            errors.add(error);
        }
    }

    private Scene scene;
    private Entity guard;
    private Graph graph;
    private Host host;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    /** Flat ground with a wall between the guard at x -600 and the goal at x 600. */
    @BeforeEach
    void setUp() {
        scene = new Scene("Courtyard");
        Entity ground = scene.createEntity("Ground");
        ground.getTransform().setLocalPosition(0f, -10f, 0f);
        ground.addComponent(new BoxCollider3DComponent(new Vector3(2000f, 20f, 2000f)));
        Entity wall = scene.createEntity("Wall");
        wall.getTransform().setLocalPosition(0f, 100f, 0f);
        wall.addComponent(new BoxCollider3DComponent(new Vector3(100f, 200f, 1200f)));
        scene.createEntity("Bounds").addComponent(new NavMeshBoundsComponent());
        guard = scene.createEntity("Guard");
        guard.getTransform().setLocalPosition(-600f, 0f, 0f);
        guard.addComponent(new NavAgent3DComponent());
        graph = new Graph("Patrol");
        host = new Host();
    }

    private String node(String type) {
        GraphNode node = new GraphNode(graph.newNodeId(), type, 0f, 0f);
        graph.addNode(node);
        return node.getId();
    }

    private String print(String message) {
        String id = node(ACTION_PRINT);
        graph.getNode(id).setLiteral("message", message);
        return id;
    }

    private void wire(String from, String fromPin, String to, String toPin) {
        graph.addLink(new GraphLink(from, fromPin, to, toPin));
    }

    /** Begin Play: Move To the goal (printing "walking" or "no way"); every tick: print "there" once Has Arrived is true. */
    private String patrol(Vector3 destination) {
        String begin = node(EVENT_BEGIN_PLAY);
        String move = node(ACTION_MOVE_TO);
        graph.getNode(move).setLiteral("destination", destination);
        wire(begin, PinDef.EXEC_OUT, move, PinDef.EXEC_IN);
        wire(move, PIN_MOVING, print("walking"), PinDef.EXEC_IN);
        wire(move, PIN_UNREACHABLE, print("no way"), PinDef.EXEC_IN);
        String tick = node(EVENT_TICK);
        String branch = node(FLOW_BRANCH);
        String arrived = node(DATA_HAS_ARRIVED);
        wire(tick, PinDef.EXEC_OUT, branch, PinDef.EXEC_IN);
        wire(arrived, "arrived", branch, "condition");
        wire(branch, "true", print("there"), PinDef.EXEC_IN);
        return move;
    }

    private void play(GraphInstance instance, float seconds) {
        for (float t = 0f; t < seconds; t += 1f / 60f) {
            scene.update(1f / 60f);
            instance.tick(1f / 60f);
        }
    }

    @Test
    void shouldWalkTheGuardRoundTheWallFromAGraph() {
        patrol(GOAL);
        GraphInstance instance = new GraphInstance(graph, REGISTRY, guard, host);

        instance.beginPlay();
        play(instance, 8f);

        assertTrue(host.errors.isEmpty(), host.errors.toString());
        assertEquals("walking", host.lines.get(0));
        assertTrue(host.lines.contains("there"), "Has Arrived turned true: " + host.lines.size() + " lines");
        Vector3 at = guard.getTransform().getWorldPosition();
        assertTrue(at.dst(GOAL) <= NavAgent3DComponent.DEFAULT_STOPPING_DISTANCE + 1e-3f, "at " + at);
    }

    @Test
    void shouldTakeTheUnreachablePinWhenThereIsNoWay() {
        patrol(new Vector3(600f, 0f, 0f));
        guard.getTransform().setLocalPosition(-600f, 0f, 0f);
        scene.findEntityByName("Wall").getComponent(BoxCollider3DComponent.class).setSize(new Vector3(100f, 200f, 2400f));

        new GraphInstance(graph, REGISTRY, guard, host).beginPlay();

        assertEquals(List.of("no way"), host.lines);
    }

    @Test
    void shouldStopTheGuardAndReportAnEntityWithoutAnAgentOnTheNode() {
        // Begin Play: Move To the goal, and on Moving, Stop Moving at once
        String begin = node(EVENT_BEGIN_PLAY);
        String move = node(ACTION_MOVE_TO);
        graph.getNode(move).setLiteral("destination", GOAL);
        String stop = node(ACTION_STOP_MOVING);
        wire(begin, PinDef.EXEC_OUT, move, PinDef.EXEC_IN);
        wire(move, PIN_MOVING, stop, PinDef.EXEC_IN);
        GraphInstance instance = new GraphInstance(graph, REGISTRY, guard, host);

        instance.beginPlay();
        Vector3 start = guard.getTransform().getWorldPosition();
        play(instance, 1f);

        assertEquals(NavAgent3DComponent.Status.IDLE, guard.getComponent(NavAgent3DComponent.class).getStatus());
        assertEquals(start, guard.getTransform().getWorldPosition(), "stopped before taking a step");
        assertFalse(host.lines.contains("there"));

        Entity rock = scene.createEntity("Rock");
        Host rockHost = new Host();
        new GraphInstance(graph, REGISTRY, rock, rockHost).beginPlay();
        assertEquals(1, rockHost.errors.size());
        assertEquals(move, rockHost.errors.get(0).getNodeId());
        assertTrue(rockHost.errors.get(0).getMessage().contains("Rock has no Nav Agent"), rockHost.errors.get(0).getMessage());
    }

    @Test
    void shouldGenerateJavaAndKotlinThatCallTheAgentAndRefuseTypeScript() {
        patrol(GOAL);
        for (GraphCodeGenerator.Language language : GraphCodeGenerator.Language.values()) {
            String code = new GraphCodeGenerator(REGISTRY).generate(graph, language);
            assertTrue(code.contains(BuiltInNodes.NAV_AGENT_CLASS + ".moveTo("), language + ":\n" + code);
            assertTrue(code.contains(BuiltInNodes.NAV_AGENT_CLASS + ".hasArrived("), language.name());
            assertTrue(code.contains("} else {"), language + ": the unreachable branch");
        }
        IllegalArgumentException web = assertThrows(IllegalArgumentException.class, () -> new GraphTypeScriptGenerator(REGISTRY).generate(graph));
        assertTrue(web.getMessage().contains("needs navigation"), web.getMessage());
    }
}
