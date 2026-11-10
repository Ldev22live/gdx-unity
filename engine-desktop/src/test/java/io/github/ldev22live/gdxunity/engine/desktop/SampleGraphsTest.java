package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sample scenes whose logic is a visual script you can open and edit (TD-068, milestone 1). Only graphs that reproduce the whole Java script are switched:
 * the others leave out rules (see {@code assets/graphs/samples/examples.json}, "notCovered"), so they stay on their Java scripts until the nodes they need exist.
 */
class SampleGraphsTest {
    private static final Path SAMPLES = Path.of("..", "assets", "samples");
    private static final Path ASSETS = Path.of("..", "assets");

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static Scene load(String file) throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        return new SceneSerializer(registry).fromJson(Files.readString(SAMPLES.resolve(file), StandardCharsets.UTF_8));
    }

    @Test
    void shouldRunTheShooterShipFromItsGraphAndNotFromJava() throws IOException {
        Scene scene = load("shooter-2d.json");

        Entity ship = scene.findEntityByName("Ship");
        assertNotNull(ship, "the shooter sample has a Ship");
        GraphScriptComponent graph = ship.getComponent(GraphScriptComponent.class);
        assertNotNull(graph);
        assertEquals("graphs/samples/ShooterShip.graph", graph.getGraphPath());
        assertNull(ship.getComponent(EntityScriptBindingComponent.class), "no Java script on the ship any more");
        assertTrue(Files.isRegularFile(ASSETS.resolve(graph.getGraphPath())), "the graph file exists");
        assertNotNull(scene.findEntityByName("Game").getComponent(EntityScriptBindingComponent.class), "the game rules are still Java");
    }
}
