package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics.Collision2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A 2D contact reaches the graphs of both entities, like a 3D one (found when the script callbacks of 2D were added). */
class GraphCollisionForwarderTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    /** A graph that prints the name of what it collided with: Event Collision, Get Name of Other, Print. */
    private static Graph collisionPrinter() {
        Graph graph = new Graph("Printer");
        graph.addNode(new GraphNode("n1", BuiltInNodes.EVENT_COLLISION, 0f, 0f));
        graph.addNode(new GraphNode("n2", BuiltInNodes.DATA_GET_NAME, 0f, 0f));
        graph.addNode(new GraphNode("n3", BuiltInNodes.ACTION_PRINT, 0f, 0f));
        graph.addLink(new GraphLink("n1", BuiltInNodes.PIN_OTHER, "n2", "target"));
        graph.addLink(new GraphLink("n2", "name", "n3", "message"));
        graph.addLink(new GraphLink("n1", PinDef.EXEC_OUT, "n3", PinDef.EXEC_IN));
        return graph;
    }

    private static List<String> bind(Entity entity) {
        List<String> printed = new ArrayList<>();
        GraphScriptComponent component = new GraphScriptComponent("graphs/Printer.graph");
        entity.addComponent(component);
        component.bind(new GraphInstance(collisionPrinter(), REGISTRY, entity, new GraphHost() {
            @Override
            public void log(GraphInstance instance, String message) {
                printed.add(message);
            }

            @Override
            public void error(GraphInstance instance, GraphRuntimeException error) {
                throw error;
            }
        }));
        return printed;
    }

    @Test
    void shouldRunTheGraphOfTheSecondEntityOfAContactAsWellAsTheFirst() {
        Scene scene = new Scene("Level");
        Entity first = scene.createEntity("First");
        Entity second = scene.createEntity("Second");
        List<String> onFirst = bind(first);
        List<String> onSecond = bind(second);

        new GraphCollisionForwarder().onCollision(new Collision2D(Collision2D.Type.COLLISION_ENTER, first, second, null, null,
            new Vector2(), new Vector2(), false));

        assertEquals(List.of("Second"), onFirst);
        assertEquals(List.of("First"), onSecond);
    }
}
