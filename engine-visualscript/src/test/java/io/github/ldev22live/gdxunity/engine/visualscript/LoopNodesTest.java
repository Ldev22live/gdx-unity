package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
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
    // ---- For Each Entity (roadmap M1.2) ----

    private static final String GET_SCENE = "java:io.github.ldev22live.gdxunity.engine.scene.Entity#getScene()";
    private static final String ALL_ENTITIES = "java:io.github.ldev22live.gdxunity.engine.scene.Scene#getAllEntities()";

    /** For Each Entity over the scene's entities, from Self's scene; the body prints index and name. */
    private GraphNode forEachOverScene() {
        GraphNode self = node(BuiltInNodes.DATA_SELF);
        GraphNode scene = node(GET_SCENE);
        GraphNode all = node(ALL_ENTITIES);
        wire(self, "entity", scene, "target");
        wire(scene, "result", all, "target");
        GraphNode loop = node(LoopNodes.FOR_EACH_ENTITY);
        wire(all, "result", loop, "list");
        GraphNode index = node(BuiltInNodes.TEXT_FROM_FLOAT);
        wire(loop, "index", index, "value");
        GraphNode name = node(BuiltInNodes.DATA_GET_NAME);
        wire(loop, "entity", name, "target");
        GraphNode line = node(BuiltInNodes.TEXT_APPEND);
        wire(index, "text", line, "a");
        wire(name, "name", line, "b");
        GraphNode body = print("");
        wire(line, "result", body, "message");
        wire(loop, "body", body, "exec");
        wire(loop, "completed", print("done"), "exec");
        return loop;
    }

    @Test
    void shouldRunTheBodyForEachEntityOfAListInOrderThenCompleted() {
        GraphNode loop = forEachOverScene();
        assertEquals(List.of(), new GraphValidator(registry).validate(graph), "a List output wires into For Each Entity");
        Scene scene = new Scene("For each");
        Entity self = scene.createEntity("Self");
        scene.createEntity("Crate");
        scene.createEntity("Ball");
        wire(node(BuiltInNodes.EVENT_BEGIN_PLAY), "then", loop, "exec");
        GraphInstance instance = new GraphInstance(GraphSerializer.fromJson(GraphSerializer.toJson(graph)), registry, self, new GraphHost() {
            @Override public void log(GraphInstance graph, String message) { lines.add(message); }
            @Override public void error(GraphInstance graph, GraphRuntimeException error) { errors.add(error); }
        });
        instance.beginPlay();

        assertEquals(List.of(), errors);
        assertEquals(List.of("0Self", "1Crate", "2Ball", "done"), lines);
    }

    @Test
    void shouldStopWithAnErrorWhenTheListIsMissing() {
        GraphNode loop = node(LoopNodes.FOR_EACH_ENTITY);
        wire(loop, "body", print("body"), "exec");
        start(loop);
        assertEquals(List.of(), lines, "nothing runs on a missing list");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("needs a list"), errors.get(0).getMessage());
    }
}
