package io.github.ldev22live.gdxunity.engine.visualscript;

import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks what a graph asks of its {@link GraphHost} with a Mockito mock (TD-073): which input method a node calls, how often, and that
 * nothing else is touched. The hand-written hosts in the other tests answer questions; a mock also records them.
 */
class GraphHostMockTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final float FRAME = 1f / 60f;
    private static final int SPACE = KeyNames.parse("SPACE").get(0);

    /** Tick -> Branch(Key Pressed SPACE): True -> Print "pressed". */
    private static Graph pressedGraph() {
        Graph graph = new Graph("pressed");
        GraphNode tick = add(graph, BuiltInNodes.EVENT_TICK);
        GraphNode key = add(graph, BuiltInNodes.INPUT_KEY_PRESSED);
        key.setProperty(BuiltInNodes.PROPERTY_KEY, "SPACE");
        GraphNode branch = add(graph, BuiltInNodes.FLOW_BRANCH);
        GraphNode print = add(graph, BuiltInNodes.ACTION_PRINT);
        print.setLiteral("message", "pressed");
        graph.addLink(new GraphLink(key.getId(), "pressed", branch.getId(), "condition"));
        graph.addLink(new GraphLink(tick.getId(), PinDef.EXEC_OUT, branch.getId(), PinDef.EXEC_IN));
        graph.addLink(new GraphLink(branch.getId(), "true", print.getId(), PinDef.EXEC_IN));
        return graph;
    }

    private static GraphNode add(Graph graph, String type) {
        GraphNode node = new GraphNode(graph.newNodeId(), type, 0f, 0f);
        graph.addNode(node);
        return node;
    }

    private static GraphInstance start(GraphHost host) {
        return new GraphInstance(pressedGraph(), REGISTRY, new Scene("Mocked").createEntity("Self"), host);
    }

    @Test
    void shouldAskTheHostForAPressOncePerTickAndNeverForAHeldKey() {
        GraphHost host = mock(GraphHost.class);
        when(host.isKeyJustPressed(SPACE)).thenReturn(false, true, false);

        GraphInstance instance = start(host);
        instance.tick(FRAME);
        instance.tick(FRAME);
        instance.tick(FRAME);

        verify(host, times(3)).isKeyJustPressed(SPACE);
        verify(host, never()).isKeyDown(anyInt());
        verify(host, never()).error(any(), any());
    }

    @Test
    void shouldPrintOnlyOnTheTickTheHostReportsAPress() {
        GraphHost host = mock(GraphHost.class);
        when(host.isKeyJustPressed(SPACE)).thenReturn(false, true, false);

        GraphInstance instance = start(host);
        instance.tick(FRAME);
        verify(host, never()).log(any(), anyString());
        instance.tick(FRAME);
        verify(host, times(1)).log(eq(instance), eq("pressed"));
        instance.tick(FRAME);

        verify(host, times(1)).log(any(), anyString());
    }

    @Test
    void shouldReportAnUnknownKeyToTheHostInsteadOfThrowing() {
        Graph graph = pressedGraph();
        graph.getNode("n2").setProperty(BuiltInNodes.PROPERTY_KEY, "NOT_A_KEY");
        GraphHost host = mock(GraphHost.class);

        new GraphInstance(graph, REGISTRY, new Scene("Bad").createEntity("Self"), host).tick(FRAME);

        verify(host, times(1)).error(any(), any(GraphRuntimeException.class));
        verify(host, never()).isKeyJustPressed(anyInt());
        verify(host, never()).log(any(), anyString());
    }
}
