package io.github.ldev22live.gdxunity.engine.visualscript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphScriptComponentTest {

    /** Roadmap M9.6: the Profiler lists each graph by its file name, not every graph as one "GraphScriptComponent". */
    @Test
    void shouldNameItsGraphFileForTheProfiler() {
        assertEquals("Graph Hopper.graph", new GraphScriptComponent("graphs/samples/Hopper.graph").getProfilerLabel());
        assertEquals("Graph Door.graph", new GraphScriptComponent("Door.graph").getProfilerLabel());
    }
}
