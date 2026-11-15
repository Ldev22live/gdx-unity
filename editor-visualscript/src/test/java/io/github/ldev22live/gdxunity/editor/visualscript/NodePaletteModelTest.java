package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodePaletteModelTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldGroupEveryNodeByCategoryWhenThereIsNoSearch() {
        NodePaletteModel.Listing listing = NodePaletteModel.list(REGISTRY, "");

        int total = listing.categories().values().stream().mapToInt(List::size).sum();
        assertEquals(REGISTRY.search("").size(), total);
        assertFalse(listing.truncated());
        listing.categories().forEach((category, types) -> types.forEach(t -> assertEquals(category, t.getCategory())));
    }

    @Test
    void shouldOnlyListMatchesOfASearch() {
        NodePaletteModel.Listing listing = NodePaletteModel.list(REGISTRY, "print");

        List<NodeType> all = listing.categories().values().stream().flatMap(List::stream).toList();
        assertFalse(all.isEmpty());
        for (NodeType type : all) {
            assertTrue(type.getTitle().toLowerCase(Locale.ROOT).contains("print")
                || type.getCategory().toLowerCase(Locale.ROOT).contains("print"), type.getTitle());
        }
    }

    @Test
    void shouldCapASearchThatMatchesTooMuchAndSayTheListWasCut() {
        // Every Java method node of the engine classes has a category naming its class, so a one-letter search matches thousands
        NodePaletteModel.Listing listing = NodePaletteModel.list(REGISTRY, "e");

        int total = listing.categories().values().stream().mapToInt(List::size).sum();
        assertEquals(NodePaletteModel.MAX_SEARCH_RESULTS, total);
        assertTrue(listing.truncated());
    }

    @Test
    void shouldListNothingForATextNoNodeHas() {
        NodePaletteModel.Listing listing = NodePaletteModel.list(REGISTRY, "zzzqqq");

        assertTrue(listing.categories().isEmpty());
        assertFalse(listing.truncated());
    }
}
