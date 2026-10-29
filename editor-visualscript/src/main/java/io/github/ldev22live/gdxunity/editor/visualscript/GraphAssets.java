package io.github.ldev22live.gdxunity.editor.visualscript;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

/** Creates, reads and writes {@code .graph} files under the asset root (TD-029). */
public class GraphAssets {
    /** Folder under the asset root where new graphs are created. */
    public static final String GRAPH_FOLDER = "graphs";
    public static final String EXTENSION = ".graph";
    /** Horizontal gap between the starter event nodes of a new graph. */
    private static final float STARTER_NODE_SPACING = 260f;

    private final File assetRoot;

    public GraphAssets(File assetRoot) {
        this.assetRoot = Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
    }

    /**
     * Creates {@code graphs/<name>.graph} (or {@code <name> 1.graph}, ... if taken) holding Event Begin Play and
     * Event Tick, ready to wire.
     */
    public AssetReference create(String name) throws IOException {
        String base = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (base.isEmpty()) {
            base = "Graph";
        }
        String path = GRAPH_FOLDER + "/" + base + EXTENSION;
        for (int n = 1; fileOf(path).exists(); n++) {
            path = GRAPH_FOLDER + "/" + base + " " + n + EXTENSION;
        }
        Graph graph = new Graph(base);
        graph.addNode(new GraphNode(graph.newNodeId(), BuiltInNodes.EVENT_BEGIN_PLAY, 0f, 0f));
        graph.addNode(new GraphNode(graph.newNodeId(), BuiltInNodes.EVENT_TICK, STARTER_NODE_SPACING, 0f));
        AssetReference asset = new AssetReference(path);
        save(asset, graph);
        return asset;
    }

    /** Reads the graph fresh from disk. */
    public Graph load(AssetReference asset) throws IOException {
        File file = fileOf(asset.getPath());
        if (!file.isFile()) {
            throw new IOException("Graph not found: " + asset.getPath());
        }
        return GraphSerializer.fromJson(Files.readString(file.toPath()));
    }

    public void save(AssetReference asset, Graph graph) throws IOException {
        File file = fileOf(asset.getPath());
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }
        Files.writeString(file.toPath(), GraphSerializer.toJson(graph));
    }

    private File fileOf(String path) {
        return new File(assetRoot, new AssetReference(path).getPath());
    }
}
