package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator.Language;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphInstanceTest.RecordingHost;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Graph functions, parameterized custom events and their file format (TD-070). */
class GraphFunctionsTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();

    private Entity self;
    private Graph graph;
    private RecordingHost host;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        self = new Scene("Functions").createEntity("Self");
        graph = new Graph("Test");
        host = new RecordingHost();
    }

    private static String node(Graph in, String type) {
        GraphNode node = new GraphNode(in.newNodeId(), type, 0f, 0f);
        in.addNode(node);
        return node.getId();
    }

    private static void exec(Graph in, String from, String to) {
        in.addLink(new GraphLink(from, PinDef.EXEC_OUT, to, PinDef.EXEC_IN));
    }

    private static void wire(Graph in, String from, String fromPin, String to, String toPin) {
        in.addLink(new GraphLink(from, fromPin, to, toPin));
    }

    /** A function {@code name(x: float) -> result: float} whose Return hands back x times {@code factor}. */
    private GraphFunction scaling(String name, float factor) {
        GraphFunction function = new GraphFunction(name);
        function.addInput(new GraphParam("x", ValueType.FLOAT));
        function.addOutput(new GraphParam("result", ValueType.FLOAT));
        Graph body = function.getBody();
        String entry = node(body, FunctionNodes.ENTRY);
        String multiply = node(body, MATH_MULTIPLY);
        body.getNode(multiply).setLiteral("b", factor);
        wire(body, entry, "x", multiply, "a");
        String ret = node(body, FunctionNodes.RETURN);
        wire(body, multiply, "result", ret, "result");
        exec(body, entry, ret);
        graph.putFunction(function);
        return function;
    }

    private GraphNode callNode(String function) {
        String id = node(graph, FunctionNodes.CALL);
        graph.getNode(id).setProperty(FunctionNodes.PROPERTY_FUNCTION, function);
        return graph.getNode(id);
    }

    /** Begin Play -> the call node; returns it, and prints its result afterwards. */
    private GraphNode callOnBeginPlayAndPrint(String function, float argument) {
        String begin = node(graph, EVENT_BEGIN_PLAY);
        GraphNode call = callNode(function);
        call.setLiteral("x", argument);
        exec(graph, begin, call.getId());
        String text = node(graph, TEXT_FROM_FLOAT);
        wire(graph, call.getId(), "result", text, "value");
        String print = node(graph, ACTION_PRINT);
        wire(graph, text, "text", print, "message");
        exec(graph, call.getId(), print);
        return call;
    }

    private GraphInstance start() {
        GraphInstance instance = new GraphInstance(graph, REGISTRY, self, host);
        instance.beginPlay();
        return instance;
    }

    @Test
    void shouldCallAFunctionWithInputsAndGetItsOutputs() {
        scaling("triple", 3f);
        callOnBeginPlayAndPrint("triple", 14f);

        start();

        assertEquals(List.of("42"), host.lines);
        assertTrue(host.errors.isEmpty(), host.errors.toString());
    }

    @Test
    void shouldHandOneFunctionsOutputToAnotherCall() {
        scaling("double", 2f);
        scaling("triple", 3f);
        // Begin Play -> double(5) -> triple(double's result) -> print
        String begin = node(graph, EVENT_BEGIN_PLAY);
        GraphNode first = callNode("double");
        first.setLiteral("x", 5f);
        GraphNode second = callNode("triple");
        wire(graph, first.getId(), "result", second.getId(), "x");
        exec(graph, begin, first.getId());
        exec(graph, first.getId(), second.getId());
        String text = node(graph, TEXT_FROM_FLOAT);
        wire(graph, second.getId(), "result", text, "value");
        String print = node(graph, ACTION_PRINT);
        wire(graph, text, "text", print, "message");
        exec(graph, second.getId(), print);

        start();

        assertEquals(List.of("30"), host.lines);
    }

    @Test
    void shouldLetAFunctionCallAnotherFunction() {
        scaling("double", 2f);
        GraphFunction quadruple = new GraphFunction("quadruple");
        quadruple.addInput(new GraphParam("x", ValueType.FLOAT));
        quadruple.addOutput(new GraphParam("result", ValueType.FLOAT));
        Graph body = quadruple.getBody();
        graph.putFunction(quadruple);
        String entry = node(body, FunctionNodes.ENTRY);
        String once = node(body, FunctionNodes.CALL);
        body.getNode(once).setProperty(FunctionNodes.PROPERTY_FUNCTION, "double");
        wire(body, entry, "x", once, "x");
        String twice = node(body, FunctionNodes.CALL);
        body.getNode(twice).setProperty(FunctionNodes.PROPERTY_FUNCTION, "double");
        wire(body, once, "result", twice, "x");
        String ret = node(body, FunctionNodes.RETURN);
        wire(body, twice, "result", ret, "result");
        exec(body, entry, once);
        exec(body, once, twice);
        exec(body, twice, ret);
        callOnBeginPlayAndPrint("quadruple", 3f);

        start();

        assertEquals(List.of("12"), host.lines);
        assertTrue(host.errors.isEmpty(), host.errors.toString());
    }

    @Test
    void shouldShareTheGraphsVariablesWithFunctions() {
        graph.putVariable(new GraphVariable("score", ValueType.FLOAT, 1f));
        GraphFunction bump = new GraphFunction("bump");
        Graph body = bump.getBody();
        graph.putFunction(bump);
        String entry = node(body, FunctionNodes.ENTRY);
        String get = node(body, VARIABLE_GET);
        body.getNode(get).setProperty(PROPERTY_VARIABLE, "score");
        String add = node(body, MATH_ADD);
        body.getNode(add).setLiteral("b", 10f);
        wire(body, get, "value", add, "a");
        String set = node(body, VARIABLE_SET);
        body.getNode(set).setProperty(PROPERTY_VARIABLE, "score");
        wire(body, add, "result", set, "value");
        exec(body, entry, set);
        String begin = node(graph, EVENT_BEGIN_PLAY);
        GraphNode call = callNode("bump");
        exec(graph, begin, call.getId());

        GraphInstance instance = start();

        assertEquals(11f, instance.getVariable("score"));
        assertTrue(host.errors.isEmpty(), host.errors.toString());
    }

    @Test
    void shouldGiveTheDefaultOfAnOutputThatNoReturnReached() {
        GraphFunction silent = new GraphFunction("silent");
        silent.addOutput(new GraphParam("result", ValueType.FLOAT));
        graph.putFunction(silent);
        node(silent.getBody(), FunctionNodes.ENTRY);
        callOnBeginPlayAndPrint("silent", 0f);

        start();

        assertEquals(List.of("0"), host.lines);
    }

    @Test
    void shouldEndTheFunctionAtTheFirstReturnItReaches() {
        GraphFunction early = new GraphFunction("early");
        early.addOutput(new GraphParam("result", ValueType.FLOAT));
        Graph body = early.getBody();
        graph.putFunction(early);
        String entry = node(body, FunctionNodes.ENTRY);
        String sequence = node(body, FLOW_SEQUENCE);
        exec(body, entry, sequence);
        String ret = node(body, FunctionNodes.RETURN);
        body.getNode(ret).setLiteral("result", 7f);
        body.addLink(new GraphLink(sequence, "then0", ret, PinDef.EXEC_IN));
        String after = node(body, ACTION_PRINT);
        body.getNode(after).setLiteral("message", "never");
        body.addLink(new GraphLink(sequence, "then1", after, PinDef.EXEC_IN));
        callOnBeginPlayAndPrint("early", 0f);

        start();

        // Then 1 of the Sequence would print "never" if the Return did not end the function
        assertEquals(List.of("7"), host.lines);
    }

    @Test
    void shouldStopTheCallersChainWhenTheFunctionBodyFails() {
        GraphFunction broken = new GraphFunction("broken");
        Graph body = broken.getBody();
        graph.putFunction(broken);
        String entry = node(body, FunctionNodes.ENTRY);
        String repeat = node(body, LoopNodes.REPEAT);
        body.getNode(repeat).setLiteral("count", LoopNodes.MAX_ITERATIONS + 1);
        exec(body, entry, repeat);
        String begin = node(graph, EVENT_BEGIN_PLAY);
        GraphNode call = callNode("broken");
        exec(graph, begin, call.getId());
        String print = node(graph, ACTION_PRINT);
        graph.getNode(print).setLiteral("message", "after the call");
        exec(graph, call.getId(), print);

        start();

        assertEquals(1, host.errors.size(), host.errors.toString());
        assertTrue(host.errors.get(0).getMessage().contains("Repeat count"), host.errors.get(0).getMessage());
        assertTrue(host.lines.isEmpty(), host.lines.toString());
    }

    // --- Custom events with parameters ---

    private GraphEventDef declareHit() {
        GraphEventDef hit = new GraphEventDef("hit");
        hit.addParam(new GraphParam("damage", ValueType.FLOAT));
        hit.addParam(new GraphParam("label", ValueType.STRING));
        graph.putEvent(hit);
        return hit;
    }

    @Test
    void shouldFireACustomEventWithItsParametersToEveryNodeOfThatName() {
        declareHit();
        String receiver = node(graph, EVENT_CUSTOM);
        graph.getNode(receiver).setProperty(PROPERTY_NAME, "hit");
        String text = node(graph, TEXT_FROM_FLOAT);
        wire(graph, receiver, "damage", text, "value");
        String appended = node(graph, TEXT_APPEND);
        wire(graph, receiver, "label", appended, "a");
        wire(graph, text, "text", appended, "b");
        String print = node(graph, ACTION_PRINT);
        wire(graph, appended, "result", print, "message");
        exec(graph, receiver, print);
        String begin = node(graph, EVENT_BEGIN_PLAY);
        String fire = node(graph, FunctionNodes.FIRE);
        graph.getNode(fire).setProperty(PROPERTY_NAME, "hit");
        graph.getNode(fire).setLiteral("damage", 4f);
        graph.getNode(fire).setLiteral("label", "dmg ");
        exec(graph, begin, fire);
        String done = node(graph, ACTION_PRINT);
        graph.getNode(done).setLiteral("message", "fired");
        exec(graph, fire, done);

        start();

        // The event's chain runs before the Fire node continues
        assertEquals(List.of("dmg 4", "fired"), host.lines);
    }

    @Test
    void shouldStopAnEventThatFiresItself() {
        GraphEventDef loop = new GraphEventDef("loop");
        graph.putEvent(loop);
        String receiver = node(graph, EVENT_CUSTOM);
        graph.getNode(receiver).setProperty(PROPERTY_NAME, "loop");
        String fire = node(graph, FunctionNodes.FIRE);
        graph.getNode(fire).setProperty(PROPERTY_NAME, "loop");
        exec(graph, receiver, fire);
        String begin = node(graph, EVENT_BEGIN_PLAY);
        String first = node(graph, FunctionNodes.FIRE);
        graph.getNode(first).setProperty(PROPERTY_NAME, "loop");
        exec(graph, begin, first);

        start();

        assertFalse(host.errors.isEmpty());
        assertTrue(host.errors.get(0).getMessage().contains("deep"), host.errors.get(0).getMessage());
    }

    @Test
    void shouldKeepAnUndeclaredCustomEventWorkingWithoutParameters() {
        String receiver = node(graph, EVENT_CUSTOM);
        graph.getNode(receiver).setProperty(PROPERTY_NAME, "ping");
        String print = node(graph, ACTION_PRINT);
        graph.getNode(print).setLiteral("message", "pong");
        exec(graph, receiver, print);

        GraphInstance instance = new GraphInstance(graph, REGISTRY, self, host);

        assertTrue(instance.fireCustomEvent("ping"));
        assertEquals(List.of("pong"), host.lines);
    }

    // --- Validation ---

    private List<String> problems(Graph target) {
        return new GraphValidator(REGISTRY).validate(target).stream().map(GraphValidator.Problem::message).toList();
    }

    @Test
    void shouldRefuseACallOfAFunctionThatDoesNotExist() {
        callNode("missing");

        assertTrue(problems(graph).stream().anyMatch(m -> m.contains("Function missing does not exist")), problems(graph).toString());
        assertThrows(IllegalArgumentException.class, () -> new GraphInstance(graph, REGISTRY, self, host));
    }

    @Test
    void shouldRefuseFunctionsThatCallEachOtherInACircle() {
        GraphFunction a = new GraphFunction("a");
        GraphFunction b = new GraphFunction("b");
        graph.putFunction(a);
        graph.putFunction(b);
        for (GraphFunction[] pair : new GraphFunction[][] {{a, b}, {b, a}}) {
            Graph body = pair[0].getBody();
            String entry = node(body, FunctionNodes.ENTRY);
            String call = node(body, FunctionNodes.CALL);
            body.getNode(call).setProperty(FunctionNodes.PROPERTY_FUNCTION, pair[1].getName());
            exec(body, entry, call);
        }

        List<String> found = problems(graph);

        assertTrue(found.stream().anyMatch(m -> m.contains("circle") && m.contains("a -> b -> a")), found.toString());
    }

    @Test
    void shouldRefuseABodyWithoutOneEntryAndEventsOrDelaysInsideIt() {
        GraphFunction function = new GraphFunction("odd");
        graph.putFunction(function);
        node(function.getBody(), EVENT_TICK);
        node(function.getBody(), FLOW_DELAY);

        List<String> found = problems(graph);

        assertTrue(found.stream().anyMatch(m -> m.contains("exactly one Function Entry")), found.toString());
        assertTrue(found.stream().anyMatch(m -> m.contains("cannot hold Event Tick")), found.toString());
        assertTrue(found.stream().anyMatch(m -> m.contains("cannot hold a Delay")), found.toString());
    }

    @Test
    void shouldRefuseEntryAndReturnOutsideAFunction() {
        node(graph, FunctionNodes.ENTRY);
        node(graph, FunctionNodes.RETURN);

        List<String> found = problems(graph);

        assertEquals(2, found.stream().filter(m -> m.contains("only be used inside a function")).count(), found.toString());
    }

    @Test
    void shouldRefuseToFireAnEventNobodyDeclared() {
        String fire = node(graph, FunctionNodes.FIRE);
        graph.getNode(fire).setProperty(PROPERTY_NAME, "nope");

        assertTrue(problems(graph).stream().anyMatch(m -> m.contains("not declared")), problems(graph).toString());
    }

    @Test
    void shouldRefuseABadNameAndADuplicate() {
        assertThrows(IllegalArgumentException.class, () -> new GraphParam("1st", ValueType.FLOAT));
        assertThrows(IllegalArgumentException.class, () -> new GraphParam(PinDef.EXEC_OUT, ValueType.FLOAT));
        assertThrows(IllegalArgumentException.class, () -> new GraphParam("obj", ValueType.OBJECT));
        assertThrows(IllegalArgumentException.class, () -> new GraphFunction("two words"));
        scaling("f", 1f);
        assertThrows(IllegalArgumentException.class, () -> graph.putFunction(new GraphFunction("f")));
        assertThrows(IllegalArgumentException.class, () -> scalingParamTwice());
    }

    private void scalingParamTwice() {
        GraphFunction function = new GraphFunction("g");
        function.addInput(new GraphParam("x", ValueType.FLOAT));
        function.addOutput(new GraphParam("x", ValueType.FLOAT));
    }

    // --- File format ---

    @Test
    void shouldStillWriteVersionOneForAGraphWithoutFunctionsAndReadOldFiles() {
        node(graph, EVENT_BEGIN_PLAY);

        String json = GraphSerializer.toJson(graph);

        assertTrue(json.contains("\"schemaVersion\": 1"), json);
        assertFalse(json.contains("functions"), json);
        assertEquals(1, GraphSerializer.fromJson(json).getNodes().size());
    }

    @Test
    void shouldRoundTripFunctionsAndEventsAsVersionTwo() {
        scaling("triple", 3f);
        declareHit();
        callOnBeginPlayAndPrint("triple", 14f);

        String json = GraphSerializer.toJson(graph);
        Graph read = GraphSerializer.fromJson(json);

        assertTrue(json.contains("\"schemaVersion\": 2"), json);
        assertEquals(List.of("triple"), read.getFunctions().stream().map(GraphFunction::getName).toList());
        GraphFunction triple = read.getFunction("triple");
        assertEquals(graph.getFunction("triple").getInputs(), triple.getInputs());
        assertEquals(graph.getFunction("triple").getOutputs(), triple.getOutputs());
        assertEquals(graph.getFunction("triple").getBody().getNodes().size(), triple.getBody().getNodes().size());
        assertEquals(graph.getFunction("triple").getBody().getLinks(), triple.getBody().getLinks());
        assertEquals(graph.getEvent("hit").getParams(), read.getEvent("hit").getParams());
        assertEquals(GraphSerializer.toJson(graph), GraphSerializer.toJson(read));
        assertTrue(problems(read).isEmpty(), problems(read).toString());
        new GraphInstance(read, REGISTRY, self, host).beginPlay();
        assertEquals(List.of("42"), host.lines);
    }

    @Test
    void shouldRunTheFileTheWebTestRunsAndRecordTheSameLines() throws Exception {
        // engine-babylon/test/functionNodes.test.ts reads the same file and expects the same lines
        String text;
        try (java.io.InputStream in = getClass().getResourceAsStream("/functions-parity.graph")) {
            text = new String(java.util.Objects.requireNonNull(in, "fixture missing").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Graph read = GraphSerializer.fromJson(text);

        new GraphInstance(read, REGISTRY, self, host).beginPlay();

        assertEquals(List.of("42", "dmg 4", "fired"), host.lines);
        assertTrue(host.errors.isEmpty(), host.errors.toString());
    }

    @Test
    void shouldRefuseAFileOfANewerVersion() {
        assertThrows(SceneSerializationException.class,
            () -> GraphSerializer.fromJson("{\"schemaVersion\":3,\"kind\":\"graph\",\"name\":\"x\"}"));
    }

    @Test
    void shouldRefuseToGenerateSourceForGraphsWithFunctions() {
        scaling("triple", 3f);
        callOnBeginPlayAndPrint("triple", 14f);

        for (Language language : Language.values()) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new GraphCodeGenerator(REGISTRY).generate(graph, language));
            assertTrue(error.getMessage().contains("functions"), error.getMessage());
        }
        IllegalArgumentException ts = assertThrows(IllegalArgumentException.class, () -> new GraphTypeScriptGenerator(REGISTRY).generate(graph));
        assertTrue(ts.getMessage().contains("TypeScript"), ts.getMessage());
    }
}
