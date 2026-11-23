package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponentSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphVariable;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;
import io.github.ldev22live.gdxunity.engine.visualscript.ValueType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class GraphEditingTest {

    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();

    @TempDir
    Path assetRoot;

    private GraphAssets assets;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = new GraphAssets(assetRoot.toFile());
    }

    private GraphDocument newDocument() throws Exception {
        AssetReference asset = assets.create("Door");
        return new GraphDocument(asset, assets.load(asset), REGISTRY);
    }

    private static GraphNode first(Graph graph, String typeId) {
        return graph.getNodes().stream().filter(n -> n.getTypeId().equals(typeId)).findFirst().orElseThrow();
    }

    @Test
    void shouldCreateStarterGraphsWithUniqueNames() throws Exception {
        AssetReference first = assets.create("Door");
        AssetReference second = assets.create("Door");
        assertEquals("graphs/Door.graph", first.getPath());
        assertEquals("graphs/Door 1.graph", second.getPath());
        assertEquals(AssetType.GRAPH, AssetType.fromPath(first.getPath()));
        Graph graph = assets.load(first);
        assertEquals(List.of(BuiltInNodes.EVENT_BEGIN_PLAY, BuiltInNodes.EVENT_TICK),
            graph.getNodes().stream().map(GraphNode::getTypeId).toList());
    }

    @Test
    void shouldEditWithUndoAndRefuseIncompatibleWires() throws Exception {
        GraphDocument document = newDocument();
        Graph graph = document.getGraph();
        GraphNode begin = first(graph, BuiltInNodes.EVENT_BEGIN_PLAY);
        GraphNode print = document.addNode(REGISTRY.get(BuiltInNodes.ACTION_PRINT), 300f, 0f);
        GraphNode add = document.addNode(REGISTRY.get(BuiltInNodes.MATH_ADD), 0f, 200f);
        assertTrue(document.isDirty());

        GraphCommands.Connect wrongType = new GraphCommands.Connect(graph, REGISTRY,
            new GraphLink(add.getId(), "result", print.getId(), "message"));
        assertFalse(document.execute(wrongType));
        assertTrue(wrongType.getRefusal().contains("FLOAT"));

        assertTrue(document.execute(new GraphCommands.Connect(graph, REGISTRY,
            new GraphLink(begin.getId(), PinDef.EXEC_OUT, print.getId(), PinDef.EXEC_IN))));
        GraphNode secondPrint = document.addNode(REGISTRY.get(BuiltInNodes.ACTION_PRINT), 300f, 100f);
        assertTrue(document.execute(new GraphCommands.Connect(graph, REGISTRY,
            new GraphLink(begin.getId(), PinDef.EXEC_OUT, secondPrint.getId(), PinDef.EXEC_IN))));
        assertEquals(1, graph.linksFrom(begin.getId(), PinDef.EXEC_OUT).size(), "An execution output keeps one wire");
        document.undo();
        assertEquals(print.getId(), graph.linksFrom(begin.getId(), PinDef.EXEC_OUT).get(0).toNode(), "Undo restores the replaced wire");

        assertTrue(document.execute(new GraphCommands.SetLiteral(print, "message", "Opening")));
        assertTrue(document.execute(new GraphCommands.MoveNode(print, 300f, 0f, 320f, 40f)));
        assertTrue(document.execute(new GraphCommands.RemoveNodes(graph, List.of(print.getId()))));
        assertNull(graph.getNode(print.getId()));
        assertTrue(graph.linksTouching(print.getId()).isEmpty());
        document.undo();
        assertSame(print, graph.getNode(print.getId()));
        assertEquals(1, graph.linksInto(print.getId(), PinDef.EXEC_IN).size(), "Its wires come back too");
        document.undo();
        assertEquals(300f, print.getX());

        document.save(assets);
        assertFalse(document.isDirty());
        assertEquals("Opening", assets.load(document.getAsset()).getNode(print.getId()).getLiteral("message"));
    }

    @Test
    void shouldDropWiresThatNoLongerFitWhenAVariableNodeChangesVariable() throws Exception {
        GraphDocument document = newDocument();
        Graph graph = document.getGraph();
        assertTrue(document.execute(new GraphCommands.AddVariable(graph, new GraphVariable("speed", ValueType.FLOAT, 1f))));
        assertTrue(document.execute(new GraphCommands.AddVariable(graph, new GraphVariable("open", ValueType.BOOLEAN, false))));
        assertFalse(document.execute(new GraphCommands.AddVariable(graph, new GraphVariable("open", ValueType.FLOAT, 0f))), "Names are unique");
        GraphNode get = document.addNode(REGISTRY.get(BuiltInNodes.VARIABLE_GET), 0f, 0f);
        document.execute(new GraphCommands.SetProperty(graph, REGISTRY, get, BuiltInNodes.PROPERTY_VARIABLE, "speed"));
        GraphNode add = document.addNode(REGISTRY.get(BuiltInNodes.MATH_ADD), 200f, 0f);
        assertTrue(document.execute(new GraphCommands.Connect(graph, REGISTRY, new GraphLink(get.getId(), "value", add.getId(), "a"))));

        document.execute(new GraphCommands.SetProperty(graph, REGISTRY, get, BuiltInNodes.PROPERTY_VARIABLE, "open"));
        assertTrue(graph.linksTouching(get.getId()).isEmpty(), "A BOOLEAN cannot feed Add");
        assertTrue(document.validate().isEmpty());
        document.undo();
        assertEquals(1, graph.linksTouching(get.getId()).size());
    }

    @Test
    void shouldRunGraphsInPlayModeAndRefuseMissingOnes() throws Exception {
        GraphDocument document = newDocument();
        Graph graph = document.getGraph();
        GraphNode print = document.addNode(REGISTRY.get(BuiltInNodes.ACTION_PRINT), 300f, 0f);
        document.execute(new GraphCommands.SetLiteral(print, "message", "Hello from a graph"));
        document.execute(new GraphCommands.Connect(graph, REGISTRY,
            new GraphLink(first(graph, BuiltInNodes.EVENT_BEGIN_PLAY).getId(), PinDef.EXEC_OUT, print.getId(), PinDef.EXEC_IN)));
        document.save(assets);

        EditorState editorState = new EditorState();
        editorState.getSerializer().getRegistry().register(new GraphScriptComponentSerializer());
        EditorLog log = new EditorLog();
        AtomicLong now = new AtomicLong();
        GraphExecutionTrace trace = new GraphExecutionTrace(now::get);
        PlayModeController play = new PlayModeController(editorState);
        play.addExtension(new GraphPlayModeExtension(editorState, assets, REGISTRY, log, trace));
        Entity door = editorState.getActiveScene().createEntity("Door");
        door.addComponent(new GraphScriptComponent(document.getAsset().getPath()));

        assertTrue(play.play(), editorState.getStatusMessage());
        play.update(0.016f);
        assertTrue(log.getEntries(EditorLog.Severity.INFO).stream().anyMatch(e -> e.message().contains("Hello from a graph")), log.getEntries(EditorLog.Severity.INFO).toString());
        assertTrue(trace.ranWithin(document.getAsset().getPath(), print.getId(), 0.5f));
        GraphScriptComponent runtimeGraph = play.getRuntimeScene().findEntityById(door.getId()).getComponent(GraphScriptComponent.class);
        assertNotNull(runtimeGraph.getInstance());
        play.stop();
        assertNull(runtimeGraph.getInstance(), "Stopping unbinds the graph");
        assertNull(door.getComponent(GraphScriptComponent.class).getInstance(), "The authored scene never runs graphs");

        door.addComponent(new GraphScriptComponent("graphs/Missing.graph"));
        assertFalse(play.play());
        assertTrue(editorState.getStatusMessage().contains("Missing.graph"), editorState.getStatusMessage());
        assertTrue(play.isStopped());
    }
}
