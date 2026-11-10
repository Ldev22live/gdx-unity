package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** The node types a graph may use, looked up by id; also the searchable list behind the editor's node menu. */
public final class NodeRegistry {
    private final Map<String, NodeType> types = new LinkedHashMap<>();

    /** Registry with {@link BuiltInNodes} and the method nodes of the engine's classes ({@link ClassCatalog#ENGINE_CLASSES}, TD-060). */
    public static NodeRegistry createDefault() {
        NodeRegistry registry = new NodeRegistry();
        BuiltInNodes.registerAll(registry);
        ComponentNodes.registerAll(registry);
        LoopNodes.registerAll(registry);
        for (NodeType type : EngineMethodNodes.TYPES) {
            if (registry.get(type.getId()) == null) registry.register(type);
        }
        return registry;
    }

    /** Registry with only {@link BuiltInNodes}, for tools that do not want the method nodes. */
    public static NodeRegistry createBuiltInOnly() {
        NodeRegistry registry = new NodeRegistry();
        BuiltInNodes.registerAll(registry);
        return registry;
    }

    /** The engine's method nodes, made once: node types do not change, so every registry shares them. */
    private static final class EngineMethodNodes {
        static final List<NodeType> TYPES = build();

        private static List<NodeType> build() {
            NodeRegistry scratch = new NodeRegistry();
            JavaMethodNodes.registerAll(scratch, ClassCatalog.engineDefaults(NodeRegistry.class.getClassLoader()));
            return scratch.getAll();
        }
    }

    public void register(NodeType type) {
        Objects.requireNonNull(type, "type cannot be null");
        if (types.putIfAbsent(type.getId(), type) != null) {
            throw new IllegalArgumentException("Duplicate node type: " + type.getId());
        }
    }

    /**
     * Removes every node made from a method ({@link JavaMethodNodes#isMethodNode}), so a catalog can be read again (TD-060).
     *
     * @return how many nodes were removed
     */
    public int removeMethodNodes() {
        int before = types.size();
        types.keySet().removeIf(JavaMethodNodes::isMethodNode);
        return before - types.size();
    }

    public NodeType get(String id) {
        return types.get(id);
    }

    public List<NodeType> getAll() {
        return List.copyOf(types.values());
    }

    /** Types whose title or category contains {@code text} (any case); all types for blank text. */
    public List<NodeType> search(String text) {
        String needle = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        List<NodeType> result = new ArrayList<>();
        for (NodeType type : types.values()) {
            // The Action twin of a Pure method node is reached by switching the node, not from the menu
            if (JavaMethodNodes.isActionTwin(type.getId())) {
                continue;
            }
            if (needle.isEmpty() || type.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                || type.getCategory().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(type);
            }
        }
        return result;
    }
}
