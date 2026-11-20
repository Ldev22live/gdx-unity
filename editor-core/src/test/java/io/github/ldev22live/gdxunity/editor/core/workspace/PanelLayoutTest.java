package io.github.ldev22live.gdxunity.editor.core.workspace;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.core.workspace.PanelLayout.DetailsMode;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The World layout's panels and strip (roadmap M1b.2 and M1b.4, TD-085). */
class PanelLayoutTest {
    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @Test
    void shouldStartAsTheWorldLayoutWithDetailsFollowingTheSelection() {
        PanelLayout panels = new PanelLayout(new WorkspaceLayoutSerializer().createDefaultConfig());

        assertFalse(panels.isOutlinerShown());
        assertFalse(panels.isBottomPanelShown());
        assertEquals(DetailsMode.WITH_SELECTION, panels.getDetailsMode());
        assertFalse(panels.isDetailsShown(false), "no selection, no inspector");
        assertTrue(panels.isDetailsShown(true));
    }

    @Test
    void shouldOpenEveryPanelThroughItsActionAndSaveTheChoice() {
        WorkspaceLayoutSerializer serializer = new WorkspaceLayoutSerializer();
        WorkspaceLayoutConfig config = serializer.createDefaultConfig();
        PanelLayout panels = new PanelLayout(config);
        EditorState state = new EditorState();
        EditorActionRegistry registry = new EditorActionRegistry();
        PanelLayout.registerActions(registry, state, panels);

        assertTrue(registry.execute(PanelLayout.TOGGLE_OUTLINER));
        assertTrue(registry.execute(PanelLayout.TOGGLE_BOTTOM_PANEL));
        assertTrue(registry.execute(PanelLayout.CYCLE_DETAILS));
        assertEquals("Properties: always", state.getStatusMessage());

        assertTrue(panels.isOutlinerShown() && panels.isBottomPanelShown() && panels.isDetailsShown(false));
        PanelLayout reloaded = new PanelLayout(serializer.deserialize(serializer.serialize(config)));
        assertTrue(reloaded.isOutlinerShown() && reloaded.isBottomPanelShown());
        assertEquals(DetailsMode.ALWAYS, reloaded.getDetailsMode(), "saved with the layout");

        assertEquals(DetailsMode.HIDDEN, panels.cycleDetails());
        assertFalse(panels.isDetailsShown(true));
        assertEquals(DetailsMode.WITH_SELECTION, panels.cycleDetails(), "and round again");
    }

    @Test
    void shouldGiveAnExistingVersionOneLayoutTheWorldPanelsAndKeepItsSizes() {
        WorkspaceLayoutSerializer serializer = new WorkspaceLayoutSerializer();
        String versionOne = "{\"version\": 1, \"activeWorkspaceId\": \"scene-editor\", \"splitRatioMain\": 0.6, \"splitRatioLeft\": 0.3,"
            + " \"splitRatioRight\": 0.2, \"bottomPanelCollapsed\": false}";

        WorkspaceLayoutConfig config = serializer.deserialize(versionOne);

        assertEquals(WorkspaceLayoutSerializer.CURRENT_VERSION, config.getVersion());
        assertEquals(0.6f, config.getSplitRatioMain(), 1e-6f, "sizes kept");
        assertTrue(config.isBottomPanelCollapsed(), "the World layout: Content Browser closed");
        assertFalse(config.isOutlinerShown());
        assertEquals(DetailsMode.WITH_SELECTION.name(), config.getDetailsMode());
        WorkspaceLayoutConfig badMode = serializer.deserialize("{\"version\": 2, \"detailsMode\": \"sideways\"}");
        assertEquals(DetailsMode.WITH_SELECTION.name(), badMode.getDetailsMode(), "an unknown mode falls back to the default");
    }

    /** UI M3: the Asset Library and the Console are separate drawers, each opened and closed on its own and remembered. */
    @Test
    void shouldOpenTheAssetLibraryAndTheConsoleIndependentlyAndSaveBoth() {
        WorkspaceLayoutSerializer serializer = new WorkspaceLayoutSerializer();
        WorkspaceLayoutConfig config = serializer.createDefaultConfig();
        PanelLayout panels = new PanelLayout(config);
        EditorState state = new EditorState();
        EditorActionRegistry registry = new EditorActionRegistry();
        PanelLayout.registerActions(registry, state, panels);
        assertFalse(panels.isAssetLibraryShown() || panels.isConsoleShown(), "the World layout starts with both closed");

        assertTrue(registry.execute(PanelLayout.TOGGLE_CONSOLE));
        assertEquals("Scripts open", state.getStatusMessage());
        assertTrue(panels.isConsoleShown());
        assertFalse(panels.isAssetLibraryShown(), "opening the Console leaves the Asset Library closed");
        assertTrue(panels.isBottomPanelShown());

        assertTrue(registry.execute(PanelLayout.TOGGLE_BOTTOM_PANEL));
        assertEquals("Assets open", state.getStatusMessage());
        assertTrue(panels.isAssetLibraryShown() && panels.isConsoleShown(), "both open, side by side");
        PanelLayout reloaded = new PanelLayout(serializer.deserialize(serializer.serialize(config)));
        assertTrue(reloaded.isAssetLibraryShown() && reloaded.isConsoleShown(), "both saved");

        panels.toggleConsole();
        assertTrue(panels.isAssetLibraryShown() && !panels.isConsoleShown(), "closing the Console leaves the Asset Library open");
    }

    @Test
    void shouldReadALayoutFromBeforeTheDrawersWithTheConsoleClosed() {
        String saved = "{\"version\":2,\"activeWorkspaceId\":\"scene-editor\",\"splitRatioMain\":0.7,\"splitRatioLeft\":0.2,"
            + "\"splitRatioRight\":0.25,\"bottomPanelCollapsed\":false,\"outlinerShown\":true,\"detailsMode\":\"ALWAYS\"}";

        PanelLayout panels = new PanelLayout(new WorkspaceLayoutSerializer().deserialize(saved));

        assertTrue(panels.isAssetLibraryShown(), "the old bottom panel's state is the Asset Library's");
        assertFalse(panels.isConsoleShown());
        assertTrue(panels.isOutlinerShown());
    }

    @Test
    void shouldOfferTheStripFor2DAnd3DScenesAndExplainTheSectionsThatAreNotBuiltYet() {
        assertEquals(List.of(WorldStrip.Section.TERRAIN, WorldStrip.Section.OBJECTS, WorldStrip.Section.BUILDINGS,
            WorldStrip.Section.CHARACTERS), WorldStrip.sectionsFor(GameMode.MODE_3D));
        assertEquals(WorldStrip.sectionsFor(GameMode.MODE_3D), WorldStrip.sectionsFor(GameMode.MODE_2_5D));
        assertEquals(List.of(WorldStrip.Section.TILES, WorldStrip.Section.SPRITES, WorldStrip.Section.OBJECTS,
            WorldStrip.Section.CHARACTERS), WorldStrip.sectionsFor(GameMode.MODE_2D));

        assertEquals(Optional.empty(), WorldStrip.unavailableReason(WorldStrip.Section.TERRAIN, GameMode.MODE_3D));
        assertEquals(Optional.empty(), WorldStrip.unavailableReason(WorldStrip.Section.OBJECTS, GameMode.MODE_3D));
        assertEquals(Optional.empty(), WorldStrip.unavailableReason(WorldStrip.Section.TILES, GameMode.MODE_2D));
        assertEquals(Optional.empty(), WorldStrip.unavailableReason(WorldStrip.Section.BUILDINGS, GameMode.MODE_3D),
            "placing buildings works since UI M3; reshaping one is Customize's (R7a)");
        assertTrue(WorldStrip.unavailableReason(WorldStrip.Section.OBJECTS, GameMode.MODE_2D).isPresent());
        assertTrue(WorldStrip.unavailableReason(WorldStrip.Section.CHARACTERS, GameMode.MODE_2D).isPresent());
        assertThrows(IllegalArgumentException.class, () -> WorldStrip.unavailableReason(WorldStrip.Section.TERRAIN, GameMode.MODE_2D));
    }
}
