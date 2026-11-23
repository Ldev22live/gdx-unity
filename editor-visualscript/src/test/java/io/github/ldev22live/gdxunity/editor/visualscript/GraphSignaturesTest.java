package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.FunctionNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphFunction;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphParam;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;
import io.github.ldev22live.gdxunity.engine.visualscript.ValueType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Editing functions and declared custom events with undo, and the document's view of a function body (TD-070). */
class GraphSignaturesTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();

    private Graph graph;
    private GraphDocument document;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        graph = new Graph("Edited");
        document = new GraphDocument(new AssetReference("graphs/Edited.graph"), graph, REGISTRY);
    }

    private GraphNode node(Graph in, String type) {
        GraphNode node = new GraphNode(in.newNodeId(), type, 0f, 0f);
        in.addNode(node);
        return node;
    }

    private void problemFree() {
        assertTrue(document.validate().isEmpty(), document.validate().toString());
    }

    @Test
    void shouldAddAFunctionThatIsValidAtOnceAndUndoIt() {
        assertTrue(document.execute(GraphSignatures.addFunction(graph, "triple")));

        GraphFunction function = graph.getFunction("triple");
        assertNotNull(function);
        assertEquals(2, function.getBody().getNodes().size(), "a Function Entry and a Return node");
        assertEquals(1, function.getBody().getLinks().size(), "wired together");
        problemFree();

        assertTrue(document.undo());
        assertNull(graph.getFunction("triple"));
        assertTrue(document.redo());
        assertNotNull(graph.getFunction("triple"));
    }

    @Test
    void shouldRefuseABadOrTakenFunctionName() {
        document.execute(GraphSignatures.addFunction(graph, "triple"));

        assertThrows(IllegalArgumentException.class, () -> GraphSignatures.addFunction(graph, "triple"));
        assertThrows(IllegalArgumentException.class, () -> GraphSignatures.addFunction(graph, "two words"));
        assertThrows(IllegalArgumentException.class, () -> GraphSignatures.addFunction(graph, ""));
    }

    @Test
    void shouldShowNewParametersAsPinsOfTheEntryReturnAndCallNodes() {
        document.execute(GraphSignatures.addFunction(graph, "triple"));
        document.execute(GraphSignatures.addInput(graph, "triple", new GraphParam("x", ValueType.FLOAT)));
        document.execute(GraphSignatures.addOutput(graph, "triple", new GraphParam("result", ValueType.FLOAT)));
        GraphNode call = node(graph, FunctionNodes.CALL);
        call.setProperty(FunctionNodes.PROPERTY_FUNCTION, "triple");

        Graph body = graph.getFunction("triple").getBody();
        GraphNode entry = body.getNodes().stream().filter(n -> n.getTypeId().equals(FunctionNodes.ENTRY)).findFirst().orElseThrow();
        GraphNode ret = body.getNodes().stream().filter(n -> n.getTypeId().equals(FunctionNodes.RETURN)).findFirst().orElseThrow();
        List<String> entryPins = REGISTRY.get(FunctionNodes.ENTRY).pinsFor(entry, body).stream().map(PinDef::id).toList();
        List<String> returnPins = REGISTRY.get(FunctionNodes.RETURN).pinsFor(ret, body).stream().map(PinDef::id).toList();
        List<String> callPins = REGISTRY.get(FunctionNodes.CALL).pinsFor(call, graph).stream().map(PinDef::id).toList();

        assertEquals(List.of("then", "x"), entryPins);
        assertEquals(List.of("exec", "result"), returnPins);
        assertEquals(List.of("exec", "then", "x", "result"), callPins);
    }

    @Test
    void shouldDropTheWiresOfARemovedParameterEverywhereAndBringThemBackOnUndo() {
        document.execute(GraphSignatures.addFunction(graph, "triple"));
        document.execute(GraphSignatures.addInput(graph, "triple", new GraphParam("x", ValueType.FLOAT)));
        document.execute(GraphSignatures.addInput(graph, "triple", new GraphParam("y", ValueType.FLOAT)));
        document.execute(GraphSignatures.addOutput(graph, "triple", new GraphParam("result", ValueType.FLOAT)));
        Graph body = graph.getFunction("triple").getBody();
        GraphNode entry = body.getNodes().stream().filter(n -> n.getTypeId().equals(FunctionNodes.ENTRY)).findFirst().orElseThrow();
        GraphNode ret = body.getNodes().stream().filter(n -> n.getTypeId().equals(FunctionNodes.RETURN)).findFirst().orElseThrow();
        body.addLink(new GraphLink(entry.getId(), "x", ret.getId(), "result"));
        GraphNode call = node(graph, FunctionNodes.CALL);
        call.setProperty(FunctionNodes.PROPERTY_FUNCTION, "triple");
        GraphNode first = node(graph, BuiltInNodes.MATH_ADD);
        graph.addLink(new GraphLink(first.getId(), "result", call.getId(), "x"));
        problemFree();

        assertTrue(document.execute(GraphSignatures.removeInput(graph, "triple", "x")));

        assertEquals(List.of("y"), graph.getFunction("triple").getInputs().stream().map(GraphParam::name).toList());
        assertTrue(graph.getLinks().isEmpty(), "the wire into the call's x went with it");
        assertEquals(1, body.getLinks().size(), "only the entry-to-return execution wire is left");
        problemFree();

        assertTrue(document.undo());

        assertEquals(List.of("x", "y"), graph.getFunction("triple").getInputs().stream().map(GraphParam::name).toList(), "pin order is kept");
        assertEquals(1, graph.getLinks().size());
        assertEquals(2, body.getLinks().size());
        problemFree();
    }

    @Test
    void shouldRemoveAFunctionAndReportItsCallsUntilUndone() {
        document.execute(GraphSignatures.addFunction(graph, "triple"));
        node(graph, FunctionNodes.CALL).setProperty(FunctionNodes.PROPERTY_FUNCTION, "triple");
        problemFree();

        document.execute(GraphSignatures.removeFunction(graph, "triple"));

        assertTrue(document.validate().stream().anyMatch(p -> p.message().contains("Function triple does not exist")));
        document.undo();
        problemFree();
    }

    @Test
    void shouldDeclareAnEventWithParametersAndDropTheWiresOfAParameterWhenItGoes() {
        document.execute(GraphSignatures.addEvent(graph, "hit"));
        document.execute(GraphSignatures.addEventParam(graph, "hit", new GraphParam("damage", ValueType.FLOAT)));
        GraphNode receiver = node(graph, BuiltInNodes.EVENT_CUSTOM);
        receiver.setProperty(BuiltInNodes.PROPERTY_NAME, "hit");
        GraphNode fire = node(graph, FunctionNodes.FIRE);
        fire.setProperty(BuiltInNodes.PROPERTY_NAME, "hit");
        GraphNode text = node(graph, BuiltInNodes.TEXT_FROM_FLOAT);
        graph.addLink(new GraphLink(receiver.getId(), "damage", text.getId(), "value"));
        GraphNode add = node(graph, BuiltInNodes.MATH_ADD);
        graph.addLink(new GraphLink(add.getId(), "result", fire.getId(), "damage"));
        problemFree();

        document.execute(GraphSignatures.removeEventParam(graph, "hit", "damage"));

        assertTrue(graph.getEvent("hit").getParams().isEmpty());
        assertTrue(graph.getLinks().isEmpty(), "both wires on the parameter's pin are gone");
        problemFree();

        document.undo();

        assertEquals(2, graph.getLinks().size());
        problemFree();
    }

    @Test
    void shouldOpenAFunctionBodyForEditingAndFallBackWhenItIsGone() {
        document.execute(GraphSignatures.addFunction(graph, "triple"));
        assertSame(graph, document.getViewGraph());

        assertTrue(document.openFunction("triple"));
        GraphNode added = document.addNode(REGISTRY.get(BuiltInNodes.MATH_ADD), 5f, 6f);

        assertNotNull(added);
        assertSame(graph.getFunction("triple").getBody(), document.getViewGraph());
        assertNotNull(graph.getFunction("triple").getBody().getNode(added.getId()));
        assertNull(graph.getNode(added.getId()), "the node went into the body, not the graph");
        assertEquals("triple", document.getOpenFunction().getName());
        assertFalse(document.openFunction("nope"));

        document.undo();
        document.undo();
        assertSame(graph, document.getViewGraph(), "undoing the function's creation shows the graph again");
        assertNull(document.getOpenFunction());
    }

    @Test
    void shouldSaveAndReloadFunctionsThroughTheFile() {
        document.execute(GraphSignatures.addFunction(graph, "triple"));
        document.execute(GraphSignatures.addInput(graph, "triple", new GraphParam("x", ValueType.FLOAT)));
        document.execute(GraphSignatures.addEvent(graph, "hit"));

        Graph read = GraphSerializer.fromJson(GraphSerializer.toJson(graph));

        assertEquals(List.of("triple"), read.getFunctions().stream().map(GraphFunction::getName).toList());
        assertEquals(2, read.getFunction("triple").getBody().getNodes().size());
        assertNotNull(read.getEvent("hit"));
        assertTrue(new io.github.ldev22live.gdxunity.engine.visualscript.GraphValidator(REGISTRY).validate(read).isEmpty());
    }
}
