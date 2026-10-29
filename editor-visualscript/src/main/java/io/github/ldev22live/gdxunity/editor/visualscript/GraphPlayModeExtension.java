package io.github.ldev22live.gdxunity.editor.visualscript;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics.Collision2D;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphHost;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphInstance;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphRuntimeException;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Starts visual scripts with play mode (TD-029): loads each Graph Script's asset, refuses to play when one is
 * missing or invalid, binds a {@link GraphInstance} per component, and forwards 2D collisions and sensor overlaps
 * to Event Collision / Event Trigger Enter. Print output and errors go to the Output Log.
 */
public class GraphPlayModeExtension implements PlayModeController.Extension {
    private static final String LOG_SOURCE = "Graph";

    private final EditorState editorState;
    private final GraphAssets assets;
    private final NodeRegistry registry;
    private final EditorLog log;
    private final GraphExecutionTrace trace;
    private final Map<GraphInstance, String> pathByInstance = new IdentityHashMap<>();
    private PhysicsSystem2D physics;
    private final PhysicsSystem2D.CollisionListener collisionForwarder = this::forwardCollision;

    public GraphPlayModeExtension(EditorState editorState, GraphAssets assets, NodeRegistry registry, EditorLog log,
                                  GraphExecutionTrace trace) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.assets = Objects.requireNonNull(assets, "assets cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.log = Objects.requireNonNull(log, "log cannot be null");
        this.trace = Objects.requireNonNull(trace, "trace cannot be null");
    }

    private final GraphHost host = new GraphHost() {
        @Override
        public void log(GraphInstance instance, String message) {
            log.info(LOG_SOURCE, label(instance) + ": " + message);
        }

        @Override
        public void error(GraphInstance instance, GraphRuntimeException error) {
            String text = label(instance) + ", node " + error.getNodeId() + ": " + error.getMessage();
            log.error(LOG_SOURCE, text);
            editorState.setStatusMessage("Graph error in " + text, true);
        }

        @Override
        public void nodeExecuted(GraphInstance instance, String nodeId) {
            String path = pathByInstance.get(instance);
            if (path != null) {
                trace.record(path, nodeId);
            }
        }
    };

    @Override
    public List<String> onPlay(Scene runtimeScene, PlayModeController controller) {
        List<String> problems = new ArrayList<>();
        List<GraphScriptComponent> toBind = new ArrayList<>();
        List<GraphInstance> instances = new ArrayList<>();
        for (Entity entity : runtimeScene.getAllEntities()) {
            for (GraphScriptComponent component : entity.getComponents(GraphScriptComponent.class)) {
                if (!component.isEnabled()) {
                    continue;
                }
                try {
                    Graph graph = assets.load(new AssetReference(component.getGraphPath()));
                    instances.add(new GraphInstance(graph, registry, entity, host));
                    toBind.add(component);
                } catch (IOException | SceneSerializationException | IllegalArgumentException e) {
                    problems.add(entity.getName() + ": " + component.getGraphPath() + ": " + e.getMessage());
                }
            }
        }
        if (!problems.isEmpty()) {
            return problems;
        }
        trace.clear();
        for (int i = 0; i < toBind.size(); i++) {
            toBind.get(i).bind(instances.get(i));
            pathByInstance.put(instances.get(i), toBind.get(i).getGraphPath());
        }
        physics = controller.getPhysicsSystem();
        if (physics != null) {
            physics.addCollisionListener(collisionForwarder);
        }
        return List.of();
    }

    @Override
    public void onStop(Scene runtimeScene) {
        for (Entity entity : runtimeScene.getAllEntities()) {
            for (GraphScriptComponent component : entity.getComponents(GraphScriptComponent.class)) {
                component.unbind();
            }
        }
        if (physics != null) {
            physics.removeCollisionListener(collisionForwarder);
            physics = null;
        }
        pathByInstance.clear();
    }

    private void forwardCollision(Collision2D event) {
        if (event.entity() == null) {
            return;
        }
        for (GraphScriptComponent component : event.entity().getComponents(GraphScriptComponent.class)) {
            GraphInstance instance = component.getInstance();
            if (instance == null) {
                continue;
            }
            switch (event.type()) {
                case COLLISION_ENTER -> instance.collision(event.otherEntity());
                case SENSOR_ENTER -> instance.triggerEnter(event.otherEntity());
                default -> {
                    // Exit events have no graph node yet
                }
            }
        }
    }

    private String label(GraphInstance instance) {
        return instance.getGraph().getName() + " on " + instance.getSelf().getName();
    }
}
