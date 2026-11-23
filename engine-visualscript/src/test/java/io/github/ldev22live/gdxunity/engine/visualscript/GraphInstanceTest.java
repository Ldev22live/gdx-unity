package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphInstanceTest {

    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();

    /** Collects what the graph prints and the errors it reports. */
    static final class RecordingHost implements GraphHost {
        final List<String> lines = new ArrayList<>();
        final List<GraphRuntimeException> errors = new ArrayList<>();
        final List<String> executed = new ArrayList<>();

        @Override
        public void log(GraphInstance instance, String message) {
            lines.add(message);
        }

        @Override
        public void error(GraphInstance instance, GraphRuntimeException error) {
            errors.add(error);
        }

        @Override
        public void nodeExecuted(GraphInstance instance, String nodeId) {
            executed.add(nodeId);
        }
    }

    private Scene scene;
    private Entity self;
    private Graph graph;
    private RecordingHost host;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        scene = new Scene("Graphs");
        self = scene.createEntity("Self");
        graph = new Graph("Test");
        host = new RecordingHost();
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

    private void exec(String from, String to) {
        wire(from, PinDef.EXEC_OUT, to, PinDef.EXEC_IN);
    }

    private GraphInstance start() {
        return new GraphInstance(graph, REGISTRY, self, host);
    }

    @Test
    void shouldRunBeginPlayOnceAndTickEveryFrame() {
        exec(node(EVENT_BEGIN_PLAY), print("hello"));
        String tick = node(EVENT_TICK);
        String text = node(TEXT_FROM_FLOAT);
        String printDelta = node(ACTION_PRINT);
        exec(tick, printDelta);
        wire(tick, PIN_DELTA_TIME, text, "value");
        wire(text, "text", printDelta, "message");

        GraphScriptComponent component = self.addComponent(new GraphScriptComponent("graphs/Test.graph"));
        component.bind(start());
        component.onUpdate(0.5f);
        component.onUpdate(0.25f);
        assertEquals(List.of("hello", "0.5", "0.25"), host.lines);
    }

    @Test
    void shouldComputePureNodesWhenReadAndMoveTheEntity() {
        String add = node(MATH_ADD);
        graph.getNode(add).setLiteral("a", 1f);
        graph.getNode(add).setLiteral("b", 2f);
        String make = node(VECTOR_MAKE);
        wire(add, "result", make, "x");
        String set = node(ACTION_SET_POSITION);
        wire(make, "vector", set, "position");
        exec(node(EVENT_BEGIN_PLAY), set);

        Entity parent = scene.createEntity("Parent");
        parent.getTransform().setLocalPosition(10f, 0f, 0f);
        self.getTransform().setParent(parent.getTransform(), false);
        start().beginPlay();
        assertTrue(new Vector3(3f, 0f, 0f).epsilonEquals(self.getTransform().getWorldPosition(), 1e-4f), "Positions are world positions");
        assertEquals(-7f, self.getTransform().getLocalPosition().x, 1e-4f);
    }

    @Test
    void shouldBranchRunSequencesInOrderAndCountWithVariables() {
        graph.putVariable(new GraphVariable("count", ValueType.FLOAT, 0f));
        String tick = node(EVENT_TICK);
        String set = node(VARIABLE_SET);
        graph.getNode(set).setProperty(PROPERTY_VARIABLE, "count");
        String get = node(VARIABLE_GET);
        graph.getNode(get).setProperty(PROPERTY_VARIABLE, "count");
        String plusOne = node(MATH_ADD);
        graph.getNode(plusOne).setLiteral("b", 1f);
        wire(get, "value", plusOne, "a");
        wire(plusOne, "result", set, "value");
        exec(tick, set);

        String branch = node(FLOW_BRANCH);
        String greater = node(MATH_GREATER);
        graph.getNode(greater).setLiteral("b", 2f);
        wire(get, "value", greater, "a");
        wire(greater, "result", branch, "condition");
        exec(set, branch);
        String sequence = node(FLOW_SEQUENCE);
        wire(branch, "true", sequence, PinDef.EXEC_IN);
        wire(sequence, "then0", print("A"), PinDef.EXEC_IN);
        wire(sequence, "then1", print("B"), PinDef.EXEC_IN);
        wire(sequence, "then2", print("C"), PinDef.EXEC_IN);

        GraphInstance instance = start();
        instance.tick(0f);
        instance.tick(0f);
        assertTrue(host.lines.isEmpty(), "count 1 and 2 are not greater than 2");
        instance.tick(0f);
        assertEquals(3f, instance.getVariable("count"));
        assertEquals(List.of("A", "B", "C"), host.lines);
    }

    @Test
    void shouldContinueAfterADelayAndIgnoreCallsWhileWaiting() {
        String delay = node(FLOW_DELAY);
        graph.getNode(delay).setLiteral("seconds", 1f);
        exec(node(EVENT_TICK), delay);
        wire(delay, "completed", print("done"), PinDef.EXEC_IN);

        GraphInstance instance = start();
        instance.tick(0.4f); // the first tick starts the delay
        instance.tick(0.4f);
        instance.tick(0.4f);
        assertTrue(host.lines.isEmpty(), "0.8 s after the delay started");
        instance.tick(0.4f);
        assertEquals(List.of("done"), host.lines, "Done once at 1.2 s; ticks while waiting did not queue more");
    }

    @Test
    void shouldReportFailingNodeAndKeepHandlingLaterEvents() {
        String divide = node(MATH_DIVIDE);
        String text = node(TEXT_FROM_FLOAT);
        String printResult = node(ACTION_PRINT);
        wire(divide, "result", text, "value");
        wire(text, "text", printResult, "message");
        exec(node(EVENT_BEGIN_PLAY), printResult);
        exec(node(EVENT_TICK), print("still running"));

        GraphInstance instance = start();
        instance.beginPlay();
        assertEquals(1, host.errors.size());
        assertEquals(divide, host.errors.get(0).getNodeId());
        assertTrue(host.errors.get(0).getMessage().contains("Division by zero"));
        instance.tick(0f);
        assertEquals(List.of("still running"), host.lines);
    }

    @Test
    void shouldStopEndlessExecutionLoops() {
        String sequence = node(FLOW_SEQUENCE);
        exec(node(EVENT_BEGIN_PLAY), sequence);
        wire(sequence, "then0", sequence, PinDef.EXEC_IN);

        start().beginPlay();
        assertEquals(1, host.errors.size());
        assertTrue(host.errors.get(0).getMessage().contains("endless loop"));
    }

    @Test
    void shouldDestroyTheOtherEntityOnCollisionAndStopWhenSelfIsDestroyed() {
        String collision = node(EVENT_COLLISION);
        String destroy = node(ACTION_DESTROY);
        exec(collision, destroy);
        wire(collision, PIN_OTHER, destroy, "target");
        String custom = node(EVENT_CUSTOM);
        graph.getNode(custom).setProperty(PROPERTY_NAME, "SelfDestruct");
        exec(custom, node(ACTION_DESTROY));
        exec(node(EVENT_TICK), print("tick"));

        Entity rock = scene.createEntity("Rock");
        GraphInstance instance = start();
        instance.collision(rock);
        assertNull(scene.findEntityById(rock.getId()));
        assertFalse(instance.fireCustomEvent("Unknown"));
        assertTrue(instance.fireCustomEvent("SelfDestruct"));
        assertFalse(instance.isRunning());
        instance.tick(0f);
        assertTrue(host.lines.isEmpty(), "A destroyed entity's graph no longer runs");
    }

    @Test
    void shouldRefuseInvalidGraphs() {
        String add = node(MATH_ADD);
        String not = node(LOGIC_NOT);
        wire(add, "result", not, "value");
        GraphValidator validator = new GraphValidator(REGISTRY);
        assertTrue(validator.validate(graph).get(0).message().contains("Cannot connect FLOAT to BOOLEAN"));
        assertThrows(IllegalArgumentException.class, this::start);

        graph = new Graph("Loop");
        String a = node(MATH_ADD);
        String b = node(MATH_ADD);
        wire(a, "result", b, "a");
        wire(b, "result", a, "a");
        assertTrue(validator.validate(graph).stream().anyMatch(p -> p.message().contains("loop")));

        graph = new Graph("Two wires");
        String x = node(MATH_ADD);
        String y = node(MATH_ADD);
        String z = node(MATH_ADD);
        wire(x, "result", z, "a");
        wire(y, "result", z, "a");
        String unknown = node("nope.unknown");
        String variable = node(VARIABLE_GET);
        graph.getNode(variable).setProperty(PROPERTY_VARIABLE, "missing");
        List<String> messages = validator.validate(graph).stream().map(GraphValidator.Problem::message).toList();
        assertTrue(messages.stream().anyMatch(m -> m.contains("more than one wire")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.contains("Unknown node type")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.contains("Variable missing does not exist")), messages.toString());
        assertNotNull(unknown);
    }

    @Test
    void shouldRoundTripGraphFiles() {
        graph.putVariable(new GraphVariable("speed", ValueType.FLOAT, 2.5f));
        graph.putVariable(new GraphVariable("home", ValueType.VECTOR3, new Vector3(1f, 2f, 3f)));
        graph.putVariable(new GraphVariable("open", ValueType.BOOLEAN, true));
        String printNode = print("hi \"there\"");
        String move = node(ACTION_MOVE_BY);
        graph.getNode(move).setLiteral("offset", new Vector3(0f, 5f, 0f));
        graph.getNode(move).setPosition(120f, -40f);
        String custom = node(EVENT_CUSTOM);
        graph.getNode(custom).setProperty(PROPERTY_NAME, "Open");
        exec(custom, printNode);
        exec(printNode, move);

        Graph read = GraphSerializer.fromJson(GraphSerializer.toJson(graph));
        assertEquals("Test", read.getName());
        assertEquals(2.5f, read.getVariable("speed").initialValue());
        assertEquals(new Vector3(1f, 2f, 3f), read.getVariable("home").initialValue());
        assertEquals(true, read.getVariable("open").initialValue());
        assertEquals("hi \"there\"", read.getNode(printNode).getLiteral("message"));
        assertEquals(new Vector3(0f, 5f, 0f), read.getNode(move).getLiteral("offset"));
        assertEquals(120f, read.getNode(move).getX());
        assertEquals("Open", read.getNode(custom).getProperty(PROPERTY_NAME));
        assertEquals(graph.getLinks(), read.getLinks());
        assertTrue(new GraphValidator(REGISTRY).validate(read).isEmpty());

        assertThrows(SceneSerializationException.class, () -> GraphSerializer.fromJson("{\"schemaVersion\":1,\"kind\":\"scene\"}"));
        assertThrows(SceneSerializationException.class, () -> GraphSerializer.fromJson(
            "{\"schemaVersion\":1,\"kind\":\"graph\",\"variables\":[{\"name\":\"x\",\"type\":\"COLOR\",\"value\":1}]}"));
        assertEquals("3", BuiltInNodes.formatNumber(3f));
        assertEquals("0.333", BuiltInNodes.formatNumber(1f / 3f));
    }
}
