package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sine and Cosine take degrees, like Rotate By (TD-060, follow-up to milestone C). */
class TrigNodesTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    /** Builds Begin Play, then prints the sine and the cosine of {@code degrees}. */
    private static Graph trigGraph(float degrees, String eventType) {
        Graph graph = new Graph("Trig");
        GraphNode sin = new GraphNode("n2", BuiltInNodes.MATH_SIN, 0f, 0f);
        GraphNode cos = new GraphNode("n3", BuiltInNodes.MATH_COS, 0f, 0f);
        sin.setLiteral("degrees", degrees);
        cos.setLiteral("degrees", degrees);
        for (GraphNode node : List.of(new GraphNode("n1", eventType, 0f, 0f), sin, cos,
            new GraphNode("n4", BuiltInNodes.ACTION_PRINT, 0f, 0f), new GraphNode("n5", BuiltInNodes.ACTION_PRINT, 0f, 0f),
            new GraphNode("n6", BuiltInNodes.TEXT_FROM_FLOAT, 0f, 0f), new GraphNode("n7", BuiltInNodes.TEXT_FROM_FLOAT, 0f, 0f))) {
            graph.addNode(node);
        }
        graph.addLink(new GraphLink("n2", "result", "n6", "value"));
        graph.addLink(new GraphLink("n3", "result", "n7", "value"));
        graph.addLink(new GraphLink("n6", "text", "n4", "message"));
        graph.addLink(new GraphLink("n7", "text", "n5", "message"));
        graph.addLink(new GraphLink("n1", PinDef.EXEC_OUT, "n4", PinDef.EXEC_IN));
        graph.addLink(new GraphLink("n4", PinDef.EXEC_OUT, "n5", PinDef.EXEC_IN));
        return graph;
    }

    private static List<String> printTrig(float degrees) {
        Graph graph = trigGraph(degrees, BuiltInNodes.EVENT_BEGIN_PLAY);
        assertEquals(List.of(), new GraphValidator(REGISTRY).validate(graph));
        List<String> printed = new ArrayList<>();
        Entity entity = new Scene("Level").createEntity("Self");
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

    /** The cosine of 90 degrees is a tiny number, not exactly zero; below the printed precision it counts as 0. */
    private static String roundedText(String text) {
        return Math.abs(Float.parseFloat(text)) < 1e-3f ? "0" : text;
    }

    @Test
    void shouldComputeSineAndCosineOfDegrees() {
        assertEquals(List.of("0", "1"), printTrig(0f));
        assertEquals(List.of("1", "0"), printTrig(90f).stream().map(TrigNodesTest::roundedText).toList());
        assertEquals(List.of("0.5", "0.866"), printTrig(30f));
        assertEquals(List.of("-1", "0"), printTrig(-90f).stream().map(TrigNodesTest::roundedText).toList());
    }

    @Test
    void shouldGenerateJavaKotlinAndTypeScriptForTheTrigNodes() {
        Graph graph = trigGraph(30f, BuiltInNodes.EVENT_TICK);

        String java = new GraphCodeGenerator(REGISTRY).generate(graph, GraphCodeGenerator.Language.JAVA);
        String kotlin = new GraphCodeGenerator(REGISTRY).generate(graph, GraphCodeGenerator.Language.KOTLIN);
        String typeScript = new GraphTypeScriptGenerator(REGISTRY).generate(graph);

        assertTrue(java.contains("(float) Math.sin(Math.toRadians(30.0f))"), java);
        assertTrue(java.contains("(float) Math.cos(Math.toRadians(30.0f))"), java);
        assertTrue(kotlin.contains("Math.sin(Math.toRadians((30.0f).toDouble())).toFloat()"), kotlin);
        assertTrue(typeScript.contains("Math.sin((30") && typeScript.contains(") * Math.PI / 180)"), typeScript);
    }
}
