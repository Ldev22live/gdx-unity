package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The example graphs of a project (TD-060, milestone C): {@value #FILE} lists graphs that mirror a part of a sample game's Java
 * script and states, for each, what the graph covers and what it leaves out, so nobody mistakes an example for the whole game.
 * The Graph tab's Examples button lists them and opens one.
 */
public final class GraphExamples {
    /** The index file, relative to the asset root. */
    public static final String FILE = "graphs/samples/examples.json";
    private static final String KIND = "graphExamples";
    private static final int FORMAT_VERSION = 1;

    /**
     * One example.
     *
     * @param graph      asset path of the {@code .graph} file, relative to the asset root
     * @param mirrors    the Java script (and part of it) the graph is modelled on
     * @param covers     what the graph does of it
     * @param notCovered what the graph leaves out or does differently
     */
    public record Example(String graph, String mirrors, String covers, String notCovered) {
        /** The graph's file name without folder and extension. */
        public String title() {
            String name = graph.substring(graph.lastIndexOf('/') + 1);
            return name.endsWith(GraphAssets.EXTENSION) ? name.substring(0, name.length() - GraphAssets.EXTENSION.length()) : name;
        }

        public AssetReference asset() {
            return new AssetReference(graph);
        }
    }

    private GraphExamples() {
    }

    /**
     * Reads the project's example list.
     *
     * @return the examples, in file order; empty when the project has no list
     * @throws IllegalArgumentException when the file is not an example list
     */
    public static List<Example> load(File assetRoot) throws IOException {
        Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        File file = new File(assetRoot, FILE);
        if (!file.isFile()) {
            return List.of();
        }
        JsonValue root;
        try {
            root = new JsonReader().parse(Files.readString(file.toPath()));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not an example list (" + FILE + "): " + e.getMessage(), e);
        }
        if (root == null || !KIND.equals(root.getString("kind", null)) || root.getInt("schemaVersion", -1) != FORMAT_VERSION) {
            throw new IllegalArgumentException("Not an example list (" + FILE + "): kind must be " + KIND + " and schemaVersion " + FORMAT_VERSION);
        }
        JsonValue array = root.get("examples");
        if (array == null || !array.isArray()) {
            throw new IllegalArgumentException("An example list needs an examples array (" + FILE + ")");
        }
        List<Example> examples = new ArrayList<>();
        for (JsonValue item = array.child; item != null; item = item.next) {
            examples.add(new Example(required(item, "graph"), required(item, "mirrors"), required(item, "covers"), required(item, "notCovered")));
        }
        return examples;
    }

    private static String required(JsonValue item, String key) {
        String value = item.getString(key, "");
        if (value.isBlank()) {
            throw new IllegalArgumentException("An example needs a " + key + " (" + FILE + ")");
        }
        return value;
    }
}
