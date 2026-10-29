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

    /** Registry with {@link BuiltInNodes}. */
    public static NodeRegistry createDefault() {
        NodeRegistry registry = new NodeRegistry();
        BuiltInNodes.registerAll(registry);
        return registry;
    }

    public void register(NodeType type) {
        Objects.requireNonNull(type, "type cannot be null");
        if (types.putIfAbsent(type.getId(), type) != null) {
            throw new IllegalArgumentException("Duplicate node type: " + type.getId());
        }
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
            if (needle.isEmpty() || type.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                || type.getCategory().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(type);
            }
        }
        return result;
    }
}
