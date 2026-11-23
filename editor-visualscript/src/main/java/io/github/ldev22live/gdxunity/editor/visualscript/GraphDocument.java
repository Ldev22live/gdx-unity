package io.github.ldev22live.gdxunity.editor.visualscript;

import io.github.ldev22live.gdxunity.editor.core.command.CommandHistory;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphFunction;
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
    /** The graph the canvas shows: {@link #graph}, or the body of one of its functions (TD-070). */
    private Graph view;

    public GraphDocument(AssetReference asset, Graph graph, NodeRegistry registry) {
        this.asset = Objects.requireNonNull(asset, "asset cannot be null");
        this.graph = Objects.requireNonNull(graph, "graph cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.view = graph;
        history.addListener(h -> {
            dirty = true;
            notifyChanged();
        });
    }

    public AssetReference getAsset() {
        return asset;
    }

    /** The graph of the file: what is saved, run and turned into code. */
    public Graph getGraph() {
        return graph;
    }

    /**
     * The graph being edited: {@link #getGraph()}, or a function's body after {@link #openFunction}. Falls back to the graph when the
     * function was removed (an undo can do that).
     */
    public Graph getViewGraph() {
        if (view != graph) {
            GraphFunction owner = view.getOwner();
            if (owner == null || graph.getFunction(owner.getName()) != owner) {
                view = graph;
            }
        }
        return view;
    }

    /** The function whose body is being edited, or null when the graph itself is. */
    public GraphFunction getOpenFunction() {
        return getViewGraph().getOwner();
    }

    /** Shows the body of the function {@code name} for editing. @return false when there is no such function */
    public boolean openFunction(String name) {
        GraphFunction function = graph.getFunction(name);
        if (function == null) {
            return false;
        }
        view = function.getBody();
        notifyChanged();
        return true;
    }

    /** Shows the graph itself again. */
    public void showMainGraph() {
        view = graph;
        notifyChanged();
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
        Graph target = getViewGraph();
        GraphNode node = new GraphNode(target.newNodeId(), type.getId(), x, y);
        return execute(new GraphCommands.AddNode(target, node)) ? node : null;
    }

    public boolean undo() {
        return history.undo();
    }

    public boolean redo() {
        return history.redo();
    }

    public List<GraphValidator.Problem> validate() {
        return new GraphValidator(registry).validate(getViewGraph());
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
