package io.github.ldev22live.gdxunity.editor.visualscript;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSourceEmbed;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphTypeScriptGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

/** Creates, reads and writes {@code .graph} files under the asset root (TD-029). */
public class GraphAssets {
    /** Folder under the asset root where new graphs are created. */
    public static final String GRAPH_FOLDER = "graphs";
    public static final String EXTENSION = ".graph";
    /** Folder under the asset root where scripts generated from graphs are written. */
    public static final String SCRIPT_FOLDER = "scripts/generated";
    /** Folder under the asset root where TypeScript scripts for the web game are written (TD-045); the script compiler ignores it. */
    public static final String WEB_SCRIPT_FOLDER = "scripts/web";
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

    /**
     * Writes the script generated from {@code graph} to {@code scripts/generated/<Class>.java} (or {@code .kt}), where
     * the in-editor script compiler finds it (TD-044). Overwrites the file of an earlier generation.
     *
     * @throws IllegalArgumentException when the graph has problems or loops that code cannot express
     */
    public AssetReference exportScript(Graph graph, NodeRegistry registry, GraphCodeGenerator.Language language) throws IOException {
        String source = new GraphCodeGenerator(registry).generate(graph, language);
        AssetReference asset = new AssetReference(SCRIPT_FOLDER + "/" + GraphCodeGenerator.className(graph) + language.getExtension());
        File file = fileOf(asset.getPath());
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }
        Files.writeString(file.toPath(), source);
        return asset;
    }

    /**
     * Writes the TypeScript script generated from {@code graph} to {@code scripts/web/<Class>.ts}, to copy into the web
     * project (TD-045). Overwrites the file of an earlier generation.
     *
     * @throws IllegalArgumentException when the graph has problems or loops that code cannot express
     */
    public AssetReference exportTypeScript(Graph graph, NodeRegistry registry) throws IOException {
        String source = new GraphTypeScriptGenerator(registry).generate(graph);
        AssetReference asset = new AssetReference(WEB_SCRIPT_FOLDER + "/" + GraphCodeGenerator.className(graph)
            + GraphTypeScriptGenerator.EXTENSION);
        File file = fileOf(asset.getPath());
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }
        Files.writeString(file.toPath(), source);
        return asset;
    }

    /**
     * Reads the graph embedded in a Java, Kotlin or TypeScript script that either editor generated, and saves it as a new
     * graph asset ({@code graphs/<name>.graph}, numbered when taken). Code edits made after generating are not read back.
     *
     * @param script a script file anywhere on disk
     * @throws IllegalArgumentException when the file holds no graph or a damaged one
     */
    public AssetReference importScript(File script) throws IOException {
        Objects.requireNonNull(script, "script cannot be null");
        Graph graph = GraphSourceEmbed.extract(Files.readString(script.toPath()));
        String base = graph.getName().replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (base.isEmpty()) {
            base = "Graph";
        }
        String path = GRAPH_FOLDER + "/" + base + EXTENSION;
        for (int n = 1; fileOf(path).exists(); n++) {
            path = GRAPH_FOLDER + "/" + base + " " + n + EXTENSION;
        }
        AssetReference asset = new AssetReference(path);
        save(asset, graph);
        return asset;
    }

    /** What {@link #importScriptChecked} made, and the first hand edit it found in the code, if any. */
    public record ImportResult(AssetReference asset, GraphSourceEmbed.Edit edit) {}

    /**
     * Like {@link #importScript} and also compares the code with what the embedded graph generates now, so an edit made by hand after
     * generating is reported (with its line) instead of being dropped silently (TD-065, part B). The graph that is saved is the embedded one
     * either way; a file whose code cannot be compared (a graph with nodes this project does not have, or a language that cannot be
     * regenerated) reports no edit.
     */
    public ImportResult importScriptChecked(File script) throws IOException {
        Objects.requireNonNull(script, "script cannot be null");
        String source = Files.readString(script.toPath());
        AssetReference asset = importScript(script);
        GraphSourceEmbed.Edit edit = null;
        try {
            Graph graph = GraphSourceEmbed.extract(source);
            NodeRegistry registry = NodeRegistry.createDefault();
            String name = script.getName().toLowerCase(java.util.Locale.ROOT);
            String generated = name.endsWith(".kt") ? new GraphCodeGenerator(registry).generate(graph, GraphCodeGenerator.Language.KOTLIN)
                : name.endsWith(".ts") ? new GraphTypeScriptGenerator(registry).generate(graph)
                : new GraphCodeGenerator(registry).generate(graph, GraphCodeGenerator.Language.JAVA);
            edit = GraphSourceEmbed.findEdit(source, generated);
        } catch (IllegalArgumentException e) {
            // Not comparable: the graph is imported all the same
        }
        return new ImportResult(asset, edit);
    }

    private File fileOf(String path) {
        return new File(assetRoot, new AssetReference(path).getPath());
    }
}
