package io.github.ldev22live.gdxunity.editor.visualscript;

import io.github.ldev22live.gdxunity.editor.core.command.CommandHistory;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphValidator;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeType;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A graph open in the editor: the asset it came from, its own undo history (separate from the scene's), and
 * whether it has unsaved changes. Every change goes through {@link #execute}.
 */
public class GraphDocument {

    public interface Listener {
        void onDocumentChanged(GraphDocument document);
    }

    private final AssetReference asset;
    private final Graph graph;
    private final NodeRegistry registry;
    private final CommandHistory history = new CommandHistory();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private boolean dirty;

    public GraphDocument(AssetReference asset, Graph graph, NodeRegistry registry) {
        this.asset = Objects.requireNonNull(asset, "asset cannot be null");
        this.graph = Objects.requireNonNull(graph, "graph cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        history.addListener(h -> {
            dirty = true;
            notifyChanged();
        });
    }

    public AssetReference getAsset() {
        return asset;
    }

    public Graph getGraph() {
        return graph;
    }

    public NodeRegistry getRegistry() {
        return registry;
    }

    public CommandHistory getHistory() {
        return history;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean execute(EditorCommand command) {
        return history.execute(command);
    }

    /** Places a new node of {@code type} at the given canvas position as one undo step. */
    public GraphNode addNode(NodeType type, float x, float y) {
        GraphNode node = new GraphNode(graph.newNodeId(), type.getId(), x, y);
        return execute(new GraphCommands.AddNode(graph, node)) ? node : null;
    }

    public boolean undo() {
        return history.undo();
    }

    public boolean redo() {
        return history.redo();
    }

    public List<GraphValidator.Problem> validate() {
        return new GraphValidator(registry).validate(graph);
    }

    /** Writes the graph to its asset file. Graphs with problems are saved too, so work is never lost. */
    public void save(GraphAssets assets) throws IOException {
        assets.save(asset, graph);
        dirty = false;
        notifyChanged();
    }

    private void notifyChanged() {
        for (Listener listener : listeners) {
            listener.onDocumentChanged(this);
        }
    }
}
