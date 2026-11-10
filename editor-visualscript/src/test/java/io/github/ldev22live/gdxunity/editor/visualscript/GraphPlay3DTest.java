package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.BoxCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.RigidBody3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.SphereCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponentSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The editor's Play button delivers 3D contacts to graphs, on the real Jolt backend (TD-048). */
class GraphPlay3DTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final float FRAME = 1f / 60f;
    private static final int THREE_SECONDS = 180;

    @TempDir
    Path assetRoot;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldFireEventCollisionWhenABallLandsDuringPlayAndStopWhenPlayStops() throws Exception {
        GraphAssets assets = new GraphAssets(assetRoot.toFile());
        AssetReference asset = assets.create("Toucher");
        GraphDocument document = new GraphDocument(asset, assets.load(asset), REGISTRY);
        Graph graph = document.getGraph();
        GraphNode onCollision = new GraphNode(graph.newNodeId(), BuiltInNodes.EVENT_COLLISION, 0f, 0f);
        GraphNode name = new GraphNode(graph.newNodeId(), BuiltInNodes.DATA_GET_NAME, 0f, 0f);
        GraphNode text = new GraphNode(graph.newNodeId(), BuiltInNodes.TEXT_APPEND, 0f, 0f);
        text.setLiteral("a", "touched ");
        GraphNode print = new GraphNode(graph.newNodeId(), BuiltInNodes.ACTION_PRINT, 0f, 0f);
        for (GraphNode node : List.of(onCollision, name, text, print)) {
            graph.addNode(node);
        }
        graph.addLink(new GraphLink(onCollision.getId(), PinDef.EXEC_OUT, print.getId(), PinDef.EXEC_IN));
        graph.addLink(new GraphLink(onCollision.getId(), BuiltInNodes.PIN_OTHER, name.getId(), "target"));
        graph.addLink(new GraphLink(name.getId(), "name", text.getId(), "b"));
        graph.addLink(new GraphLink(text.getId(), "result", print.getId(), "message"));
        document.save(assets);

        EditorState editorState = new EditorState();
        editorState.getSerializer().getRegistry().register(new GraphScriptComponentSerializer());
        editorState.getSerializer().getRegistry().register(new RigidBody3DComponentSerializer());
        editorState.getSerializer().getRegistry().register(new BoxCollider3DComponentSerializer());
        editorState.getSerializer().getRegistry().register(new SphereCollider3DComponentSerializer());
        EditorLog log = new EditorLog();
        PlayModeController play = new PlayModeController(editorState);
        AtomicLong now = new AtomicLong();
        play.addExtension(new GraphPlayModeExtension(editorState, assets, REGISTRY, log, new GraphExecutionTrace(now::get)));
        Entity ball = editorState.getActiveScene().createEntity("Ball");
        ball.getTransform().setLocalPosition(0f, 300f, 0f);
        ball.addComponent(new RigidBody3DComponent(RigidBody3DComponent.MotionType.DYNAMIC));
        ball.addComponent(new SphereCollider3DComponent(50f));
        ball.addComponent(new GraphScriptComponent(asset.getPath()));
        Entity floor = editorState.getActiveScene().createEntity("Floor");
        floor.getTransform().setLocalPosition(0f, -50f, 0f);
        floor.addComponent(new BoxCollider3DComponent(new Vector3(2000f, 100f, 2000f)));

        assertTrue(play.play(), editorState.getStatusMessage());
        assertNotNull(play.getPhysicsSystem3D());
        for (int i = 0; i < THREE_SECONDS; i++) {
            play.update(FRAME);
        }

        long touches = log.getEntries(EditorLog.Severity.INFO).stream().filter(e -> e.message().contains("touched Floor")).count();
        assertEquals(1, touches, () -> log.getEntries(EditorLog.Severity.INFO).toString());
        assertTrue(log.getEntries(EditorLog.Severity.ERROR).isEmpty(), log.getEntries(EditorLog.Severity.ERROR).toString());
        play.stop();
        assertNull(play.getPhysicsSystem3D());
    }
}
