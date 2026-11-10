package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphHost;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphInstance;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphRuntimeException;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphValidator;
import io.github.ldev22live.gdxunity.engine.visualscript.JavaMethodNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeType;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A method node that is Pure by default can be switched to its Action twin and back (TD-060 follow-up). */
class SwitchMethodKindTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final String GET_NAME = "java:io.github.ldev22live.gdxunity.engine.scene.Entity#getName()";
    private static final String SET_NAME = "java:io.github.ldev22live.gdxunity.engine.scene.Entity#setName(java.lang.String)";

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    /** Begin Play, Print, and a Get Name node wired into the message; returns the graph. */
    private static Graph nameGraph() {
        Graph graph = new Graph("Names");
        graph.addNode(new GraphNode("n1", BuiltInNodes.EVENT_BEGIN_PLAY, 0f, 0f));
        graph.addNode(new GraphNode("n2", BuiltInNodes.ACTION_PRINT, 0f, 0f));
        graph.addNode(new GraphNode("n3", GET_NAME, 0f, 0f));
        graph.addLink(new GraphLink("n1", PinDef.EXEC_OUT, "n2", PinDef.EXEC_IN));
        graph.addLink(new GraphLink("n3", "result", "n2", "message"));
        return graph;
    }

    private static List<String> run(Graph graph) {
        List<String> printed = new ArrayList<>();
        Entity entity = new Scene("Level").createEntity("Original");
        new GraphInstance(graph, REGISTRY, entity, new GraphHost() {
            @Override
            public void log(GraphInstance instance, String message) {
                printed.add(message);
            }

            @Override
            public void error(GraphInstance instance, GraphRuntimeException error) {
                throw error;
            }
        }).beginPlay();
        return printed;
    }

    @Test
    void shouldOfferAnActionTwinOnlyForNodesThatArePureAndKeepItOutOfTheMenu() {
        NodeType twin = REGISTRY.get(GET_NAME + JavaMethodNodes.ACTION_SUFFIX);

        assertNotNull(twin);
        assertEquals(NodeType.Kind.ACTION, twin.getKind());
        assertNull(REGISTRY.get(SET_NAME + JavaMethodNodes.ACTION_SUFFIX), "setName is an Action already");
        assertEquals(GET_NAME + JavaMethodNodes.ACTION_SUFFIX, JavaMethodNodes.counterpartOf(GET_NAME, REGISTRY));
        assertEquals(GET_NAME, JavaMethodNodes.counterpartOf(GET_NAME + JavaMethodNodes.ACTION_SUFFIX, REGISTRY));
        assertNull(JavaMethodNodes.counterpartOf(SET_NAME, REGISTRY));
        assertNull(JavaMethodNodes.counterpartOf(BuiltInNodes.ACTION_PRINT, REGISTRY));
        assertTrue(REGISTRY.search("Entity.getName").stream().noneMatch(type -> JavaMethodNodes.isActionTwin(type.getId())));
    }

    @Test
    void shouldRunTheActionTwinOnceInTheChainWithItsResultKept() {
        Graph graph = nameGraph();
        graph.removeLink(new GraphLink("n3", "result", "n2", "message"));
        graph.removeNode("n3");
        graph.addNode(new GraphNode("n3", GET_NAME + JavaMethodNodes.ACTION_SUFFIX, 0f, 0f));
        graph.removeLink(new GraphLink("n1", PinDef.EXEC_OUT, "n2", PinDef.EXEC_IN));
        graph.addLink(new GraphLink("n1", PinDef.EXEC_OUT, "n3", PinDef.EXEC_IN));
        graph.addLink(new GraphLink("n3", PinDef.EXEC_OUT, "n2", PinDef.EXEC_IN));
        graph.addLink(new GraphLink("n3", "result", "n2", "message"));

        assertEquals(List.of(), new GraphValidator(REGISTRY).validate(graph));
        assertEquals(List.of("Original"), run(graph));
    }

    @Test
    void shouldSwitchToTheTwinKeepTheDataWiresAndSwitchBackWithUndo() {
        Graph graph = nameGraph();
        GraphCommands.SwitchMethodKind command = new GraphCommands.SwitchMethodKind(graph, REGISTRY, "n3");

        assertTrue(GraphCommands.SwitchMethodKind.canSwitch(graph, REGISTRY, "n3"));
        assertFalse(GraphCommands.SwitchMethodKind.canSwitch(graph, REGISTRY, "n2"), "Print has no twin");
        assertTrue(command.execute());

        assertEquals(GET_NAME + JavaMethodNodes.ACTION_SUFFIX, graph.getNode("n3").getTypeId());
        assertEquals(1, graph.linksFrom("n3", "result").size(), "the wire into the message stays");
        assertTrue(command.undo());
        assertEquals(GET_NAME, graph.getNode("n3").getTypeId());
        assertEquals(List.of("Original"), run(graph));
    }

    @Test
    void shouldDropTheExecutionWiresWhenSwitchingBackToPureAndRestoreThemOnUndo() {
        Graph graph = nameGraph();
        new GraphCommands.SwitchMethodKind(graph, REGISTRY, "n3").execute();
        graph.addLink(new GraphLink("n1", PinDef.EXEC_OUT, "n3", PinDef.EXEC_IN));
        graph.addLink(new GraphLink("n3", PinDef.EXEC_OUT, "n2", PinDef.EXEC_IN));
        GraphCommands.SwitchMethodKind back = new GraphCommands.SwitchMethodKind(graph, REGISTRY, "n3");

        assertTrue(back.execute());

        assertEquals(GET_NAME, graph.getNode("n3").getTypeId());
        assertTrue(graph.linksTouching("n3").stream().noneMatch(link -> link.toPin().equals(PinDef.EXEC_IN) || link.fromPin().equals(PinDef.EXEC_OUT)));
        assertTrue(back.undo());
        assertEquals(GET_NAME + JavaMethodNodes.ACTION_SUFFIX, graph.getNode("n3").getTypeId());
        assertTrue(graph.linksTouching("n3").stream().anyMatch(link -> link.toPin().equals(PinDef.EXEC_IN)), "the wire into exec is back");
    }

    @Test
    void shouldRefuseToSwitchANodeThatHasNoTwin() {
        Graph graph = nameGraph();

        assertFalse(new GraphCommands.SwitchMethodKind(graph, REGISTRY, "n2").execute());
        assertFalse(new GraphCommands.SwitchMethodKind(graph, REGISTRY, "missing").execute());
    }
}
