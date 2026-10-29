package io.github.ldev22live.gdxunity.engine.visualscript;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * Runs a graph asset on its entity during play (TD-029). The scene stores only the asset path; whoever starts
 * play loads the graph and {@link #bind binds} a {@link GraphInstance}. Begin Play fires on the first update,
 * Event Tick on every update. Without an instance (editing, or a graph that failed to load) it does nothing.
 */
public class GraphScriptComponent extends BaseComponent {
    private String graphPath;
    private GraphInstance instance;
    private boolean begun;

    public GraphScriptComponent(String graphPath) {
        setGraphPath(graphPath);
    }

    /** Asset path of the {@code .graph} file, relative to the asset root. */
    public String getGraphPath() {
        return graphPath;
    }

    public void setGraphPath(String graphPath) {
        Objects.requireNonNull(graphPath, "graphPath cannot be null");
        if (graphPath.isBlank()) {
            throw new IllegalArgumentException("graphPath cannot be blank");
        }
        this.graphPath = graphPath;
    }

    public void bind(GraphInstance instance) {
        this.instance = Objects.requireNonNull(instance, "instance cannot be null");
        this.begun = false;
    }

    public void unbind() {
        if (instance != null) {
            instance.stop();
        }
        instance = null;
    }

    /** The running graph, or null while not playing. */
    public GraphInstance getInstance() {
        return instance;
    }

    /** For Java and Kotlin scripts: runs the graph's Custom Event nodes named {@code name}. */
    public boolean fireCustomEvent(String name) {
        return instance != null && instance.fireCustomEvent(name);
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (instance == null) {
            return;
        }
        if (!begun) {
            begun = true;
            instance.beginPlay();
        }
        instance.tick(deltaTime);
    }

    @Override
    public void onDetach() {
        unbind();
        super.onDetach();
    }
}
