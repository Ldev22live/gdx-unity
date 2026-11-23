package io.github.ldev22live.gdxunity.editor.core.workspace;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The nine tool drawers of UI M3: one list for the top bar, the World strip and the Window menu. */
class EditorDrawersTest {
    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @Test
    void shouldNameTheNineDrawersOfTheBriefEachWithItsPlace() {
        // The tab row of the design reference (UI M3b), then Properties, the panel at the right of the map
        assertEquals(List.of("Terrain", "Objects", "Buildings", "Characters", "Assets", "Layers", "Scripts", "AI Assistant",
            "Properties"), EditorDrawers.ALL.stream().map(EditorDrawers.Drawer::label).toList());
        assertEquals(List.of("Assets", "Layers", "Scripts", "AI Assistant"),
            EditorDrawers.tabs().stream().map(EditorDrawers.Drawer::label).toList(), "Properties is not a tab");
        assertEquals(EditorDrawers.Place.LEFT, EditorDrawers.tabs().get(1).place(), "Layers, the scene hierarchy");
        assertEquals(2, EditorDrawers.ALL.stream().filter(d -> d.place() == EditorDrawers.Place.RIGHT).count(), "Properties, Assistant");
        assertEquals(2, EditorDrawers.ALL.stream().filter(d -> d.place() == EditorDrawers.Place.BOTTOM).count(), "Assets and Scripts");
    }

    @Test
    void shouldOpenEveryMapDrawerFromTheWorldStripOfA3DScene() {
        List<WorldStrip.Section> mapSections = EditorDrawers.ALL.stream().filter(d -> d.place() == EditorDrawers.Place.MAP)
            .map(EditorDrawers.Drawer::section).toList();

        assertEquals(WorldStrip.sectionsFor(GameMode.MODE_3D), mapSections, "in the strip's order");
        for (WorldStrip.Section section : mapSections) {
            assertEquals(Optional.empty(), WorldStrip.unavailableReason(section, GameMode.MODE_3D), section + " opens a drawer in 3D");
            assertEquals(Optional.empty(), WorldStrip.unavailableReason(section, GameMode.MODE_2_5D), section + " in 2.5D");
        }
    }

    @Test
    void shouldHaveARegisteredActionForEveryDockedDrawerButTheAssistantWhichTheEditorOwns() {
        EditorActionRegistry registry = new EditorActionRegistry();
        PanelLayout.registerActions(registry, new EditorState(), new PanelLayout(new WorkspaceLayoutSerializer().createDefaultConfig()));

        for (EditorDrawers.Drawer drawer : EditorDrawers.docked()) {
            if (!drawer.actionId().equals(EditorDrawers.TOGGLE_ASSISTANT)) {
                assertTrue(registry.execute(drawer.actionId()), drawer.label() + " opens through " + drawer.actionId());
            }
        }
    }

    @Test
    void shouldRefuseADrawerThatIsNeitherOnTheMapNorDocked() {
        assertThrows(IllegalArgumentException.class, () -> new EditorDrawers.Drawer("Lost", EditorDrawers.Place.MAP, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new EditorDrawers.Drawer("Both", EditorDrawers.Place.LEFT, "x", WorldStrip.Section.TERRAIN));
        assertThrows(IllegalArgumentException.class, () -> new EditorDrawers.Drawer("Silent", EditorDrawers.Place.BOTTOM, null, null));
    }
}
