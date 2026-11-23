package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import io.github.ldev22live.gdxunity.engine.sprite.TileMapData;
import io.github.ldev22live.gdxunity.engine.visualscript.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.ldev22live.gdxunity.editor.core.scripting.JavaScriptCompiler;
import io.github.ldev22live.gdxunity.editor.core.scripting.KotlinScriptCompiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComponentNodesTest {
    private final NodeRegistry registry = NodeRegistry.createDefault();
    private final List<String> errors = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private final GraphHost host = new GraphHost() {
        @Override public void log(GraphInstance graph, String message) { lines.add(message); }
        @Override public void error(GraphInstance graph, GraphRuntimeException error) { errors.add(error.getMessage()); }
    };

    @BeforeAll
    static void init() { GdxNativesLoader.load(); }

    private GraphNode node(Graph graph, String type) {
        GraphNode node = new GraphNode(graph.newNodeId(), type, 0f, 0f);
        graph.addNode(node);
        return node;
    }

    private Graph operation(String component, String signature, Object... args) {
        Graph graph = new Graph("Component calls");
        GraphNode begin = node(graph, BuiltInNodes.EVENT_BEGIN_PLAY);
        GraphNode get = node(graph, ComponentNodes.GET_PREFIX + component);
        String api = ComponentNodes.API.stream().filter(value -> value.endsWith(component + "#" + signature)).findFirst().orElseThrow();
        GraphNode call = node(graph, JavaMethodNodes.ID_PREFIX + api);
        for (int index = 0; index < args.length; index++) call.setLiteral("p" + index, args[index]);
        graph.addLink(new GraphLink(get.getId(), "component", call.getId(), "target"));
        NodeType type = registry.get(call.getTypeId());
        GraphNode end;
        if (type.getKind() == NodeType.Kind.PURE) {
            ValueType result = type.pin(call, graph, "result").type();
            graph.putVariable(new GraphVariable("result", result, result.defaultValue()));
            end = node(graph, BuiltInNodes.VARIABLE_SET);
            end.setProperty("variable", "result");
            graph.addLink(new GraphLink(call.getId(), "result", end.getId(), "value"));
        } else {
            end = call;
            GraphNode done = node(graph, BuiltInNodes.ACTION_PRINT);
            done.setLiteral("message", "done");
            graph.addLink(new GraphLink(call.getId(), "then", done.getId(), "exec"));
        }
        graph.addLink(new GraphLink(begin.getId(), "then", end.getId(), "exec"));
        return graph;
    }

    @Test
    void shouldMatchTheSharedComponentBehaviorCasesWithRealJavaComponents() throws Exception {
        Scene scene = new Scene("Components");
        Entity entity = scene.createEntity("Self");
        entity.addComponent(new ShapeRendererComponent(90f, 14f, Color.WHITE));
        entity.addComponent(new TextComponent("Initial"));
        entity.addComponent(new CameraComponent());
        SpriteAnimatorComponent sprite = new SpriteAnimatorComponent();
        entity.addComponent(sprite);
        TilemapComponent tiles = new TilemapComponent();
        TileMapData map = new TileMapData(2, 2);
        map.set(0, 0, 1, 1);
        map.setSolid(1, true);
        tiles.setMap(map);
        entity.addComponent(tiles);
        AudioSourceComponent audio = new AudioSourceComponent();
        List<Float> played = new ArrayList<>();
        audio.bind((sound, volume, pitch) -> played.add(volume));
        entity.addComponent(audio);
        JsonValue cases = new JsonReader().parse(Files.readString(Path.of("../engine-babylon/test/fixtures/component-cases.json")));
        for (JsonValue test : cases) {
            Object[] args = new Object[test.get("args").size];
            for (int index = 0; index < args.length; index++) {
                JsonValue value = test.get("args").get(index);
                args[index] = value.isString() ? value.asString() : value.asFloat();
            }
            errors.clear();
            lines.clear();
            Graph graph = operation(test.getString("component"), test.getString("method"), args);
            GraphInstance instance = new GraphInstance(GraphSerializer.fromJson(GraphSerializer.toJson(graph)), registry, entity, host);
            instance.beginPlay();
            if (test.getBoolean("error", false)) {
                assertEquals(1, errors.size(), test.toString());
                assertTrue(lines.isEmpty(), "failed methods cannot continue");
            } else {
                assertEquals(List.of(), errors, test.toString());
                JsonValue expected = test.get("result");
                if (expected != null) {
                    Object value = expected.isString() ? expected.asString() : expected.isBoolean() ? expected.asBoolean() : expected.asFloat();
                    assertEquals(value, instance.getVariable("result"), test.toString());
                } else assertEquals(List.of("done"), lines);
            }
        }
        assertEquals("walk", sprite.getTag());
        assertEquals(List.of(0.25f), played);
        scene.dispose();
    }

    @Test
    void shouldReportMissingComponentsAndRejectIncompatibleObjectWires() {
        Graph graph = operation("TextComponent", "setText(java.lang.String)", "hello");
        Scene scene = new Scene("Missing");
        new GraphInstance(graph, registry, scene.createEntity("Empty"), host).beginPlay();
        assertTrue(errors.get(0).contains("has no TextComponent"));
        assertTrue(lines.isEmpty());
        GraphNode shape = node(graph, ComponentNodes.GET_PREFIX + "ShapeRendererComponent");
        GraphNode text = graph.getNodes().stream().filter(value -> value.getTypeId().contains("#setText")).findFirst().orElseThrow();
        assertNotNull(GraphValidator.connectionProblem(registry.get(shape.getTypeId()).pin(shape, graph, "component"),
            registry.get(text.getTypeId()).pin(text, graph, "target")));
        assertTrue(registry.search("Components").stream().anyMatch(value -> value.getId().contains("#setText")));
        scene.dispose();
    }

    @Test
    void shouldSaveComponentEditsAfterUndoRedoAndRunTheReopenedGraph(@TempDir Path temp) throws Exception {
        Graph graph = operation("TextComponent", "setText(java.lang.String)", "original");
        GraphNode call = graph.getNodes().stream().filter(value -> value.getTypeId().contains("#setText")).findFirst().orElseThrow();
        GraphDocument document = new GraphDocument(new AssetReference("graphs/Component.graph"), graph, registry);
        assertTrue(document.execute(new GraphCommands.SetLiteral(call, "p0", "edited")));
        assertTrue(document.undo());
        assertEquals("original", call.getLiteral("p0"));
        assertTrue(document.redo());
        GraphAssets assets = new GraphAssets(temp.toFile());
        document.save(assets);
        Graph reopened = assets.load(document.getAsset());
        assertEquals(List.of(), new GraphValidator(registry).validate(reopened));
        Scene scene = new Scene("Reopened");
        Entity entity = scene.createEntity("Text");
        TextComponent text = entity.addComponent(new TextComponent("before"));
        new GraphInstance(reopened, registry, entity, host).beginPlay();
        assertEquals("edited", text.getText());
        assertEquals(List.of(), errors);
        scene.dispose();
    }

    @Test
    void shouldCompileComponentLookupsAndCallsAsJavaAndKotlin(@TempDir Path temp) throws Exception {
        Graph graph = operation("TextComponent", "setText(java.lang.String)", "from generated code");
        for (GraphCodeGenerator.Language language : GraphCodeGenerator.Language.values()) {
            Path source = temp.resolve("ComponentCalls" + language.getExtension());
            Files.writeString(source, new GraphCodeGenerator(registry).generate(graph, language));
            var result = language == GraphCodeGenerator.Language.JAVA
                ? new JavaScriptCompiler().compile(List.of(source.toFile()), temp.resolve(language.name()).toFile(), null)
                : new KotlinScriptCompiler().compile(List.of(source.toFile()), temp.resolve(language.name()).toFile(), null);
            assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        }
    }
}
