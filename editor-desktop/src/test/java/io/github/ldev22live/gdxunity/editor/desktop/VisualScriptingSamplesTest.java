package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphHost;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphInstance;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphRuntimeException;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphValidator;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sample graphs ({@code graphs/*.graph}) and the scene that uses them ({@code visual-scripting-3d.json}): they load,
 * validate, do what their names say when run, and turn into code in both languages (TD-044).
 */
class VisualScriptingSamplesTest {

    private static final File PROJECT = new File("../assets");
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final float FRAME = 1f / 60f;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static Graph graph(String name) throws IOException {
        return GraphSerializer.fromJson(Files.readString(new File(PROJECT, "graphs/" + name + ".graph").toPath()));
    }

    private static Scene scene() throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        EditorApplication.registerComponentSerializers(registry);
        return new SceneSerializer(registry).fromJson(Files.readString(new File(PROJECT, "samples/visual-scripting-3d.json").toPath()));
    }

    /** Binds the graph of the entity named {@code name}, as Play does, collecting what it prints. */
    private static GraphInstance bind(Scene scene, String name, List<String> printed) throws IOException {
        Entity entity = scene.findEntityByName(name);
        GraphScriptComponent component = entity.getComponent(GraphScriptComponent.class);
        String file = component.getGraphPath().replace("graphs/", "").replace(".graph", "");
        return new GraphInstance(graph(file), REGISTRY, entity, new GraphHost() {
            @Override
            public void log(GraphInstance instance, String message) {
                printed.add(message);
            }

            @Override
            public void error(GraphInstance instance, GraphRuntimeException error) {
                throw error;
            }
        });
    }

    @Test
    void shouldValidateEverySampleGraphAndTurnItIntoCode() throws IOException {
        for (String name : List.of("Spinner", "Hop", "Greeter")) {
            Graph graph = graph(name);
            assertEquals(List.of(), new GraphValidator(REGISTRY).validate(graph), name);
            for (GraphCodeGenerator.Language language : GraphCodeGenerator.Language.values()) {
                String source = new GraphCodeGenerator(REGISTRY).generate(graph, language);
                assertTrue(source.contains("class " + name), name + " " + language);
            }
        }
    }

    @Test
    void shouldBindEveryGraphScriptOfTheScene() throws IOException {
        Scene scene = scene();
        List<String> bound = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            GraphScriptComponent component = entity.getComponent(GraphScriptComponent.class);
            if (component != null) {
                bound.add(component.getGraphPath());
                assertTrue(new File(PROJECT, component.getGraphPath()).isFile(), component.getGraphPath());
            }
        }
        assertEquals(List.of("graphs/Spinner.graph", "graphs/Hop.graph", "graphs/Greeter.graph"), bound);
    }

    @Test
    void shouldTurnTheSpinnerNinetyDegreesEachSecond() throws IOException {
        Scene scene = scene();
        GraphInstance spinner = bind(scene, "Spinner Cube", new ArrayList<>());
        Entity cube = scene.findEntityByName("Spinner Cube");
        float before = cube.getTransform().getLocalEulerAngles().x;

        spinner.beginPlay();
        for (int i = 0; i < 60; i++) {
            spinner.tick(FRAME);
        }

        assertEquals(90f, Math.abs(cube.getTransform().getLocalEulerAngles().x - before), 1f);
    }

    @Test
    void shouldHopUpThenDownAfterTheDelays() throws IOException {
        Scene scene = scene();
        GraphInstance hop = bind(scene, "Hop Cube", new ArrayList<>());
        Entity cube = scene.findEntityByName("Hop Cube");
        float ground = cube.getTransform().getWorldPosition().y;

        hop.beginPlay();
        for (int i = 0; i < 30; i++) {
            hop.tick(FRAME);
        }
        assertEquals(ground, cube.getTransform().getWorldPosition().y, 1e-3f, "still waiting: the first hop comes after 0.8 s");
        for (int i = 0; i < 30; i++) {
            hop.tick(FRAME);
        }
        assertEquals(ground + 120f, cube.getTransform().getWorldPosition().y, 1e-3f, "first hop is up");
        for (int i = 0; i < 48; i++) {
            hop.tick(FRAME);
        }
        assertEquals(ground, cube.getTransform().getWorldPosition().y, 1e-3f, "second hop comes back down");
    }

    @Test
    void shouldGreetByEntityName() throws IOException {
        Scene scene = scene();
        List<String> printed = new ArrayList<>();

        bind(scene, "Greeter Sphere", printed).beginPlay();

        assertEquals(List.of("Hello from a visual script!", "I am Greeter Sphere"), printed);
    }
}
