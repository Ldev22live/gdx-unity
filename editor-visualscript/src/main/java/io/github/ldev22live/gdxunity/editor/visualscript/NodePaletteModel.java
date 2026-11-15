package io.github.ldev22live.gdxunity.editor.visualscript;

import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the Graph tab's node palette lists (TD-069): the registry's nodes grouped by category, filtered by a search text. A search shows
 * at most {@link #MAX_SEARCH_RESULTS} nodes, because the Java method nodes alone number in the thousands.
 */
public final class NodePaletteModel {
    /** Nodes a search lists; more matches ask for a narrower search. */
    public static final int MAX_SEARCH_RESULTS = 200;

    /** @param categories category name to its nodes, in registry order; @param truncated whether the search had more matches than shown */
    public record Listing(Map<String, List<NodeType>> categories, boolean truncated) { }

    private NodePaletteModel() {
    }

    /** Nodes whose title or category contains {@code text}, grouped by category; no search lists every node (no cap). */
    public static Listing list(NodeRegistry registry, String text) {
        Objects.requireNonNull(registry, "registry cannot be null");
        boolean searching = text != null && !text.isBlank();
        Map<String, List<NodeType>> byCategory = new LinkedHashMap<>();
        int shown = 0;
        boolean truncated = false;
        for (NodeType type : registry.search(text)) {
            if (searching && shown >= MAX_SEARCH_RESULTS) {
                truncated = true;
                break;
            }
            byCategory.computeIfAbsent(type.getCategory(), k -> new ArrayList<>()).add(type);
            shown++;
        }
        return new Listing(byCategory, truncated);
    }
}
