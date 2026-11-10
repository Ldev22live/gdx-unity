package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoopNodesTest {
    private final Graph graph = new Graph("Repeat");
    private final NodeRegistry registry = NodeRegistry.createDefault();
    private final List<String> lines = new ArrayList<>();
    private final List<GraphRuntimeException> errors = new ArrayList<>();

    @BeforeAll
    static void init() { GdxNativesLoader.load(); }

    private GraphNode node(String type) {
        GraphNode node = new GraphNode(graph.newNodeId(), type, 0f, 0f);
        graph.addNode(node);
        return node;
    }

    private void wire(GraphNode from, String output, GraphNode to, String input) {
        graph.addLink(new GraphLink(from.getId(), output, to.getId(), input));
    }

    private GraphNode repeat(int count) {
        GraphNode node = node(LoopNodes.REPEAT);
        node.setLiteral("count", count);
        return node;
    }

    private GraphNode print(String message) {
        GraphNode node = node(BuiltInNodes.ACTION_PRINT);
        node.setLiteral("message", message);
        return node;
    }

    private GraphInstance start(GraphNode loop) {
        wire(node(BuiltInNodes.EVENT_BEGIN_PLAY), "then", loop, "exec");
        Scene scene = new Scene("Repeat test");
        GraphInstance instance = new GraphInstance(GraphSerializer.fromJson(GraphSerializer.toJson(graph)), registry,
            scene.createEntity("Self"), new GraphHost() {
                @Override public void log(GraphInstance graph, String message) { lines.add(message); }
                @Override public void error(GraphInstance graph, GraphRuntimeException error) { errors.add(error); }
            });
        instance.beginPlay();
        return instance;
    }

    @Test
    void shouldRunTheBodyInIndexOrderBeforeCompleted() {
        GraphNode loop = repeat(3);
        GraphNode text = node(BuiltInNodes.TEXT_FROM_FLOAT);
        GraphNode body = print("");
        wire(loop, "index", text, "value");
        wire(text, "text", body, "message");
        wire(loop, "body", body, "exec");
        wire(loop, "completed", print("done"), "exec");
        start(loop);
        assertEquals(List.of("0", "1", "2", "done"), lines);
        assertEquals(List.of(), errors);
    }

    @Test
    void shouldCompleteWithoutRunningTheBodyWhenCountIsZero() {
        GraphNode loop = repeat(0);
        wire(loop, "body", print("body"), "exec");
        wire(loop, "completed", print("done"), "exec");
        start(loop);
        assertEquals(List.of("done"), lines);
    }

    @Test
    void shouldFinishNestedBodiesBeforeAdvancingTheOuterLoop() {
        GraphNode outer = repeat(2);
        GraphNode inner = repeat(2);
        wire(outer, "body", inner, "exec");
        wire(inner, "body", print("inner"), "exec");
        wire(inner, "completed", print("outer"), "exec");
        wire(outer, "completed", print("done"), "exec");
        start(outer);
        assertEquals(List.of("inner", "inner", "outer", "inner", "inner", "outer", "done"), lines);
    }

    @Test
    void shouldRejectCountsOutsideTheBoundWithoutContinuing() {
        for (int count : new int[] {-1, LoopNodes.MAX_ITERATIONS + 1}) {
            GraphNode loop = repeat(count);
            wire(loop, "completed", print("wrong"), "exec");
            start(loop);
        }
        assertTrue(lines.isEmpty());
        assertTrue(errors.stream().allMatch(error -> error.getMessage().contains("Repeat count")));
        assertFalse(errors.isEmpty());
    }

    @Test
    void shouldApplyTheEventBudgetAcrossNestedLoops() {
        GraphNode outer = repeat(1000);
        GraphNode inner = repeat(1000);
        wire(outer, "body", inner, "exec");
        wire(inner, "body", print("body"), "exec");
        wire(outer, "completed", print("wrong"), "exec");
        start(outer);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("10000"));
        assertFalse(lines.contains("wrong"));
        assertTrue(lines.size() < GraphInstance.MAX_NODES_PER_EVENT);
    }

    @Test
    void shouldRefuseUnsupportedLoopSourceGenerationExplicitly() {
        GraphNode loop = repeat(2);
        wire(node(BuiltInNodes.EVENT_BEGIN_PLAY), "then", loop, "exec");
        for (GraphCodeGenerator.Language language : GraphCodeGenerator.Language.values()) {
            assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new GraphCodeGenerator(registry).generate(graph, language)).getMessage().contains(LoopNodes.REPEAT));
        }
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> new GraphTypeScriptGenerator(registry).generate(graph)).getMessage().contains(LoopNodes.REPEAT));
    }
}
