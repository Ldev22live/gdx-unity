package io.github.ldev22live.gdxunity.engine.visualscript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The pins of method nodes carry the parameters' names, because the engine is compiled with {@code -parameters} (TD-060). */
class MethodNodePinNamesTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();

    @Test
    void shouldLabelTheArgumentPinOfEntitySetNameWithTheParameterName() {
        NodeType type = REGISTRY.get("java:io.github.ldev22live.gdxunity.engine.scene.Entity#setName(java.lang.String)");
        assertNotNull(type);

        PinDef pin = type.pin(new GraphNode("n1", type.getId(), 0f, 0f), new Graph("Check"), "p0");

        assertEquals("name", pin.label(), "not the type name String");
    }

    @Test
    void shouldLabelTheClampPinsWithTheirParameterNames() {
        NodeType type = REGISTRY.get("java:com.badlogic.gdx.math.MathUtils#clamp(float,float,float)");
        GraphNode node = new GraphNode("n1", type.getId(), 0f, 0f);
        Graph graph = new Graph("Check");

        // libGDX itself is not compiled with -parameters, so its names are not available and the types stand in
        assertEquals("float", type.pin(node, graph, "p0").label());
    }
}
