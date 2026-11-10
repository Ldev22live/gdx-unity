package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nodes made from Java methods (TD-060): which methods become which nodes, running them, and how graphs that use them are saved. */
class JavaMethodNodesTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final String VECTOR_LEN = "java:com.badlogic.gdx.math.Vector3#len()";
    private static final String VECTOR_ADD = "java:com.badlogic.gdx.math.Vector3#add(com.badlogic.gdx.math.Vector3)";
    private static final String CLAMP_FLOAT = "java:com.badlogic.gdx.math.MathUtils#clamp(float,float,float)";
    private static final String CLAMP_INT = "java:com.badlogic.gdx.math.MathUtils#clamp(int,int,int)";
    private static final String ENTITY_GET_NAME = "java:io.github.ldev22live.gdxunity.engine.scene.Entity#getName()";
    private static final String ENTITY_SET_NAME = "java:io.github.ldev22live.gdxunity.engine.scene.Entity#setName(java.lang.String)";
    private static final String ENTITY_GET_SCENE = "java:io.github.ldev22live.gdxunity.engine.scene.Entity#getScene()";
    private static final String SET_MOTION_TYPE =
        "java:io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent#setMotionType("
            + "io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent$MotionType)";

    private static final class RecordingHost implements GraphHost {
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

        @Override
        public void nodeExecuted(GraphInstance instance, String nodeId) {
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
        scene = new Scene("Methods");
        self = scene.createEntity("Self");
        graph = new Graph("Methods test");
        host = new RecordingHost();
    }

    private String node(String type) {
        GraphNode node = new GraphNode(graph.newNodeId(), type, 0f, 0f);
        graph.addNode(node);
        return node.getId();
    }

    private void wire(String from, String fromPin, String to, String toPin) {
        graph.addLink(new GraphLink(from, fromPin, to, toPin));
    }

    private void run() {
        GraphInstance instance = new GraphInstance(graph, REGISTRY, self, host);
        instance.beginPlay();
    }

    /** Begin Play, then the given action nodes one after the other. */
    private void chain(String... actions) {
        String previous = node(BuiltInNodes.EVENT_BEGIN_PLAY);
        for (String action : actions) {
            wire(previous, PinDef.EXEC_OUT, action, PinDef.EXEC_IN);
            previous = action;
        }
    }

    /** A Print of a node's text: a method node's result, or the text of a Number to Text node. */
    private String print(String textNode) {
        String print = node(BuiltInNodes.ACTION_PRINT);
        wire(textNode, JavaMethodNodes.isMethodNode(graph.getNode(textNode).getTypeId()) ? "result" : "text", print, "message");
        return print;
    }

    // --- Which methods become which nodes ---

    @Test
    void shouldMakeANodeIdThatNamesTheClassTheMethodAndItsParameterTypes() throws Exception {
        assertEquals(VECTOR_ADD, JavaMethodNodes.nodeId(Vector3.class.getMethod("add", Vector3.class)));
        assertEquals("java:com.badlogic.gdx.math.Vector3#<init>(float,float,float)",
            JavaMethodNodes.nodeId(Vector3.class.getConstructor(float.class, float.class, float.class)));
        assertTrue(JavaMethodNodes.isMethodNode(VECTOR_LEN));
        assertFalse(JavaMethodNodes.isMethodNode(BuiltInNodes.ACTION_PRINT));
    }

    @Test
    void shouldOfferTheEngineClassesMethodsInTheDefaultRegistryUnderOneCategoryPerClass() {
        for (String id : List.of(VECTOR_LEN, VECTOR_ADD, CLAMP_FLOAT, ENTITY_GET_NAME, ENTITY_SET_NAME, ENTITY_GET_SCENE)) {
            assertNotNull(REGISTRY.get(id), id);
        }
        assertEquals("Java: Vector3", REGISTRY.get(VECTOR_LEN).getCategory());
        assertEquals("Java: MathUtils", REGISTRY.get(CLAMP_FLOAT).getCategory());
        assertEquals("Vector3.len", REGISTRY.get(VECTOR_LEN).getTitle());
        assertTrue(REGISTRY.getAll().size() > 300, "built-in nodes plus the engine classes: " + REGISTRY.getAll().size());
        assertFalse(NodeRegistry.createBuiltInOnly().getAll().stream().anyMatch(type -> JavaMethodNodes.isMethodNode(type.getId())));
        assertEquals(List.of(REGISTRY.get(VECTOR_LEN)), REGISTRY.search("Vector3.len").stream().filter(t -> t.getId().equals(VECTOR_LEN)).toList());
    }

    @Test
    void shouldMakeGettersAndNumberFunctionsPureAndOtherMethodsActions() {
        assertEquals(NodeType.Kind.PURE, REGISTRY.get(ENTITY_GET_NAME).getKind());
        assertEquals(NodeType.Kind.PURE, REGISTRY.get(VECTOR_LEN).getKind(), "returns a number and takes nothing");
        assertEquals(NodeType.Kind.PURE, REGISTRY.get(CLAMP_FLOAT).getKind(), "a static number function");
        assertEquals(NodeType.Kind.ACTION, REGISTRY.get(ENTITY_SET_NAME).getKind(), "returns nothing");
        assertEquals(NodeType.Kind.ACTION, REGISTRY.get(VECTOR_ADD).getKind(), "changes the vector and returns it");
    }

    @Test
    void shouldGiveAnInstanceMethodATargetPinAndEveryParameterAndTheResultAPinOfTheRightType() {
        List<PinDef> add = REGISTRY.get(VECTOR_ADD).pinsFor(null, null);
        assertEquals(ValueType.VECTOR3, pin(add, "target").type());
        assertEquals(ValueType.VECTOR3, pin(add, "p0").type());
        assertEquals(ValueType.VECTOR3, pin(add, "result").type());

        List<PinDef> clamp = REGISTRY.get(CLAMP_FLOAT).pinsFor(null, null);
        assertNull(findPin(clamp, "target"), "a static method has no target");
        assertEquals(ValueType.FLOAT, pin(clamp, "p2").type());
        assertEquals(ValueType.INTEGER, pin(REGISTRY.get(CLAMP_INT).pinsFor(null, null), "p0").type());

        List<PinDef> scene = REGISTRY.get(ENTITY_GET_SCENE).pinsFor(null, null);
        assertEquals(ValueType.ENTITY, pin(scene, "target").type());
        assertEquals(ValueType.OBJECT, pin(scene, "result").type());
        assertEquals(Scene.class, pin(scene, "result").objectClass());

        // An enum is given by the name of its constant
        assertEquals(ValueType.STRING, pin(REGISTRY.get(SET_MOTION_TYPE).pinsFor(null, null), "p0").type());
    }

    @Test
    void shouldLeaveOutMethodsWithArraysAndTheMethodsOfObject() {
        List<NodeType> vector = JavaMethodNodes.typesFor(Vector3.class);
        List<String> arrays = vector.stream().map(NodeType::getId).filter(id -> id.contains("[]")).toList();
        assertEquals(List.of(), arrays, "no array parameters");
        List<String> ofObject = vector.stream().map(NodeType::getId).filter(id -> id.startsWith("java:java.lang.Object#")).toList();
        assertEquals(List.of(), ofObject, "nothing declared by Object itself");
        assertEquals(List.of(), vector.stream().map(NodeType::getId).filter(id -> id.contains("#getClass")).toList());
        assertTrue(vector.stream().anyMatch(type -> type.getId().contains("#<init>(")), "constructors become nodes");
    }

    // --- Running them ---

    @Test
    void shouldCallAMethodOnAWiredTargetAndGiveItsResultToTheNextNode() {
        String make = node(BuiltInNodes.VECTOR_MAKE);
        graph.getNode(make).setLiteral("x", 3f);
        graph.getNode(make).setLiteral("y", 4f);
        String length = node(VECTOR_LEN);
        wire(make, "vector", length, "target");
        String text = node(BuiltInNodes.TEXT_FROM_FLOAT);
        wire(length, "result", text, "value");
        chain(print(text));

        run();

        assertEquals(List.of("5"), host.lines);
        assertEquals(List.of(), host.errors);
    }

    @Test
    void shouldTakeTheGraphsOwnEntityWhenAnEntityTargetIsNotWired() {
        String rename = node(ENTITY_SET_NAME);
        graph.getNode(rename).setLiteral("p0", "Renamed");
        chain(rename);

        run();

        assertEquals("Renamed", self.getName());
        assertEquals(List.of(), host.errors);
    }

    @Test
    void shouldCallStaticFunctionsWithLiteralsAndWholeNumbersWhereFloatsAreWanted() {
        String clamp = node(CLAMP_FLOAT);
        graph.getNode(clamp).setLiteral("p0", 15f);
        graph.getNode(clamp).setLiteral("p1", 0f);
        graph.getNode(clamp).setLiteral("p2", 10f);
        String text = node(BuiltInNodes.TEXT_FROM_FLOAT);
        wire(clamp, "result", text, "value");
        String clampWhole = node(CLAMP_INT);
        graph.getNode(clampWhole).setLiteral("p0", -4);
        graph.getNode(clampWhole).setLiteral("p1", 0);
        graph.getNode(clampWhole).setLiteral("p2", 9);
        String wholeText = node(BuiltInNodes.TEXT_FROM_FLOAT);
        // A whole number goes into a float pin
        wire(clampWhole, "result", wholeText, "value");
        chain(print(text), print(wholeText));

        run();

        assertEquals(List.of("10", "0"), host.lines);
        assertEquals(List.of(), host.errors);
    }

    @Test
    void shouldChainObjectsFromOneMethodIntoTheTargetOfAnother() {
        // Self -> its scene -> the scene's name, printed
        String getScene = node(ENTITY_GET_SCENE);
        String getName = node("java:io.github.ldev22live.gdxunity.engine.scene.Scene#getName()");
        wire(getScene, "result", getName, "target");
        chain(print(getName));

        run();

        assertEquals(List.of("Methods"), host.lines);
        assertEquals(List.of(), host.errors);
    }

    @Test
    void shouldReadAnEnumParameterFromTheNameOfItsConstantAndGiveAnEnumResultAsItsName() {
        NodeRegistry registry = registryWithHelper();
        String describe = node(JavaMethodNodes.nodeId(helperMethod("describe")));
        graph.getNode(describe).setLiteral("p0", "KINEMATIC");
        String motion = node(JavaMethodNodes.nodeId(helperMethod("motion")));
        String describeMotion = node(JavaMethodNodes.nodeId(helperMethod("describe")));
        wire(motion, "result", describeMotion, "p0");
        chain(printText(describe), printText(describeMotion));

        new GraphInstance(graph, registry, self, host).beginPlay();

        assertEquals(List.of("motion KINEMATIC", "motion DYNAMIC"), host.lines);
        assertEquals(List.of(), host.errors);
    }

    @Test
    void shouldSayWhichConstantsAreAllowedWhenTheNameIsNotOne() {
        NodeRegistry registry = registryWithHelper();
        String describe = node(JavaMethodNodes.nodeId(helperMethod("describe")));
        graph.getNode(describe).setLiteral("p0", "FLYING");
        chain(printText(describe));

        new GraphInstance(graph, registry, self, host).beginPlay();

        assertEquals(1, host.errors.size());
        String message = host.errors.get(0).getMessage();
        assertTrue(message.contains("\"FLYING\" is not a MotionType") && message.contains("STATIC") && message.contains("DYNAMIC"), message);
    }

    @Test
    void shouldStopTheChainWithAnErrorThatNamesTheNodeWhenTheTargetIsNotSet() {
        String getName = node("java:io.github.ldev22live.gdxunity.engine.scene.Scene#getName()");
        chain(print(getName));

        run();

        assertEquals(1, host.errors.size());
        assertTrue(host.errors.get(0).getMessage().contains("Target is not set"), host.errors.get(0).getMessage());
        assertEquals(List.of(), host.lines, "the Print after it did not run");
    }

    @Test
    void shouldReportAnExceptionFromTheMethodAsAnErrorOnItsNodeAndKeepTheGraphAlive() {
        NodeRegistry registry = registryWithHelper();
        String fail = node(JavaMethodNodes.nodeId(helperMethod("fail")));
        chain(fail);

        GraphInstance instance = new GraphInstance(graph, registry, self, host);
        instance.beginPlay();
        instance.beginPlay();

        assertEquals(2, host.errors.size(), "each event is reported, the graph keeps running");
        assertEquals(fail, host.errors.get(0).getNodeId());
        assertTrue(host.errors.get(0).getMessage().contains("Helper.fail failed: IllegalStateException: boom"), host.errors.get(0).getMessage());
    }

    // --- Wires between pins ---

    @Test
    void shouldLetAWholeNumberGoIntoAFloatPinButNotAFloatIntoAWholeNumberPin() {
        PinDef integerOut = PinDef.out("o", "o", ValueType.INTEGER);
        PinDef floatOut = PinDef.out("o", "o", ValueType.FLOAT);
        PinDef floatIn = PinDef.in("i", "i", ValueType.FLOAT);
        PinDef integerIn = PinDef.in("i", "i", ValueType.INTEGER);

        assertNull(GraphValidator.connectionProblem(integerOut, floatIn));
        assertEquals("Cannot connect FLOAT to INTEGER", GraphValidator.connectionProblem(floatOut, integerIn));
    }

    @Test
    void shouldLetAnObjectPinTakeWhatItsClassAcceptsIncludingEntitiesAndText() {
        PinDef entityOut = PinDef.out("o", "o", ValueType.ENTITY);
        PinDef textOut = PinDef.out("o", "o", ValueType.STRING);
        PinDef anyIn = PinDef.inObject("i", "i", Object.class);
        PinDef sceneIn = PinDef.inObject("i", "i", Scene.class);
        PinDef sceneOut = PinDef.outObject("o", "o", Scene.class);
        PinDef entityIn = PinDef.in("i", "i", ValueType.ENTITY);

        assertNull(GraphValidator.connectionProblem(entityOut, anyIn));
        assertNull(GraphValidator.connectionProblem(textOut, anyIn));
        assertEquals("Cannot connect STRING to Scene", GraphValidator.connectionProblem(textOut, sceneIn));
        assertNull(GraphValidator.connectionProblem(sceneOut, sceneIn));
        assertEquals("Cannot connect Scene to ENTITY", GraphValidator.connectionProblem(sceneOut, entityIn));
    }

    @Test
    void shouldRefuseAGraphWhoseWireConnectsTypesThatDoNotFit() {
        String getScene = node(ENTITY_GET_SCENE);
        String print = node(BuiltInNodes.ACTION_PRINT);
        wire(getScene, "result", print, "message");

        List<GraphValidator.Problem> problems = new GraphValidator(REGISTRY).validate(graph);

        assertEquals(1, problems.size());
        assertEquals("Cannot connect Scene to STRING", problems.get(0).message());
    }

    // --- Files ---

    @Test
    void shouldSaveAndLoadAGraphWithMethodNodesAndWholeNumberLiterals() {
        String clamp = node(CLAMP_INT);
        graph.getNode(clamp).setLiteral("p0", 42);
        graph.getNode(clamp).setLiteral("p1", 0);
        graph.getNode(clamp).setLiteral("p2", 10);
        graph.putVariable(new GraphVariable("count", ValueType.INTEGER, 7));

        Graph loaded = GraphSerializer.fromJson(GraphSerializer.toJson(graph));

        assertEquals(CLAMP_INT, loaded.getNode(clamp).getTypeId());
        assertEquals(42, loaded.getNode(clamp).getLiteral("p0"));
        assertEquals(7, loaded.getVariable("count").initialValue());
        assertEquals(List.of(), new GraphValidator(REGISTRY).validate(loaded));
    }

    @Test
    void shouldReportAMethodNodeWhoseClassIsNotInTheRegistryAsAnUnknownNodeType() {
        node("java:com.example.Missing#doIt()");

        List<GraphValidator.Problem> problems = new GraphValidator(REGISTRY).validate(graph);

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).message().startsWith("Unknown node type java:com.example.Missing"));
    }

    // --- The class catalog ---

    @Test
    void shouldReadAndWriteTheCatalogFileAndKeepTheNamesItCouldNotFind() {
        ClassLoader loader = getClass().getClassLoader();
        ClassCatalog catalog = ClassCatalog.fromJson("""
            {"schemaVersion": 1, "kind": "classCatalog", "classes": ["com.badlogic.gdx.math.Vector3", "com.example.Gone", "com.badlogic.gdx.math.Vector3"]}
            """, loader);

        assertEquals(List.of(Vector3.class), catalog.getClasses());
        assertEquals(List.of("com.example.Gone"), catalog.getMissing());
        ClassCatalog again = ClassCatalog.fromJson(catalog.toJson(), loader);
        assertEquals(catalog.getClasses(), again.getClasses());
        assertEquals(catalog.getMissing(), again.getMissing());
        assertThrows(IllegalArgumentException.class, () -> ClassCatalog.fromJson("{\"kind\": \"graph\"}", loader));
        assertThrows(IllegalArgumentException.class, () -> ClassCatalog.fromJson("not json", loader));
    }

    @Test
    void shouldAddTheNodesOfProjectClassesToARegistryWithoutDuplicates() {
        NodeRegistry registry = NodeRegistry.createDefault();
        ClassCatalog catalog = ClassCatalog.of(List.of(MathUtils.class, Helper.class));

        int added = JavaMethodNodes.registerAll(registry, catalog);

        assertTrue(added >= 2, "the helper's two methods are new: " + added);
        assertNotNull(registry.get(JavaMethodNodes.nodeId(helperMethod("twice"))));
        assertEquals(0, JavaMethodNodes.registerAll(registry, catalog), "a second time adds nothing");
    }

    @Test
    void shouldRunAMethodOfAProjectLikeClassAndConvertItsLongAndDoubleValues() throws Exception {
        NodeRegistry registry = NodeRegistry.createDefault();
        JavaMethodNodes.registerAll(registry, ClassCatalog.of(List.of(Helper.class)));
        String twice = JavaMethodNodes.nodeId(helperMethod("twice"));
        String big = JavaMethodNodes.nodeId(helperMethod("huge"));
        assertEquals(ValueType.INTEGER, registry.get(twice).pinsFor(null, null).stream().filter(p -> p.id().equals("p0")).findFirst().orElseThrow().type());

        String first = node(twice);
        graph.getNode(first).setLiteral("p0", 21);
        String text = node(BuiltInNodes.TEXT_FROM_FLOAT);
        wire(first, "result", text, "value");
        String hugeNode = node(big);
        String hugeText = node(BuiltInNodes.TEXT_FROM_FLOAT);
        wire(hugeNode, "result", hugeText, "value");
        chain(print(text), print(hugeText));

        new GraphInstance(graph, registry, self, host).beginPlay();

        assertEquals(List.of("42"), host.lines, "the first print ran; the second one stopped at the long that does not fit");
        assertEquals(1, host.errors.size());
        assertTrue(host.errors.get(0).getMessage().contains("does not fit"), host.errors.get(0).getMessage());
    }

    private NodeRegistry registryWithHelper() {
        NodeRegistry registry = NodeRegistry.createDefault();
        JavaMethodNodes.registerAll(registry, ClassCatalog.of(List.of(Helper.class)));
        return registry;
    }

    /** A Print whose text comes from a method node's result. */
    private String printText(String methodNode) {
        return print(methodNode);
    }

    /** Stands in for a class from the project's scripts. */
    public static final class Helper {
        private Helper() {
        }

        public static int twice(int value) {
            return value * 2;
        }

        public static long huge() {
            return 5_000_000_000L;
        }

        public static double halfOf(double value) {
            return value / 2;
        }

        public static String describe(RigidBody3DComponent.MotionType type) {
            return "motion " + type;
        }

        public static RigidBody3DComponent.MotionType motion() {
            return RigidBody3DComponent.MotionType.DYNAMIC;
        }

        public static void fail() {
            throw new IllegalStateException("boom");
        }
    }

    private static java.lang.reflect.Method helperMethod(String name) {
        for (java.lang.reflect.Method method : Helper.class.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static PinDef findPin(List<PinDef> pins, String id) {
        return pins.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    private static PinDef pin(List<PinDef> pins, String id) {
        PinDef found = findPin(pins, id);
        assertNotNull(found, "pin " + id + " in " + pins);
        return found;
    }
}
