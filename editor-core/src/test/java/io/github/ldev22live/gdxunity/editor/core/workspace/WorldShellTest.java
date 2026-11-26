package io.github.ldev22live.gdxunity.editor.core.workspace;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The World shell of TD-107 (urGameForge Alpha A02): categories, viewport share, first-run panels and the minimap's mapping. */
class WorldShellTest {
    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private static List<String> labels(GameMode mode) {
        return WorldShell.categories(mode).stream().map(WorldShell.Category::label).toList();
    }

    @Test
    void shouldOfferTheReferenceCategoriesForEachGameMode() {
        assertEquals(List.of("Map", "Terrain", "Objects", "Buildings", "Characters", "Players", "Rules"), labels(GameMode.MODE_3D));
        List<String> twoD = labels(GameMode.MODE_2D);
        assertEquals("Map", twoD.get(0));
        assertEquals(List.of("Players", "Rules"), twoD.subList(twoD.size() - 2, twoD.size()), "the scenario categories come last");
        for (GameMode mode : GameMode.values()) {
            for (WorldShell.Category category : WorldShell.categories(mode)) {
                boolean scenario = category == WorldShell.PLAYERS || category == WorldShell.RULES;
                assertEquals(scenario, category.notBuilt().isPresent(), category.label() + " says plainly whether it works yet");
                assertEquals(!scenario && category != WorldShell.MAP, category.worldSection().isPresent(), category.label());
            }
        }
        assertTrue(WorldShell.PLAYERS.notBuilt().orElseThrow().contains("A04"));
        assertTrue(WorldShell.RULES.notBuilt().orElseThrow().contains("A05"));
    }

    @Test
    void shouldLeaveTheMapAtLeastSeventyPercentAtBothTargetSizes() {
        for (float[] size : new float[][] {{1280f, 720f}, {1920f, 1080f}}) {
            assertTrue(WorldShell.viewportShare(size[0], size[1], false) >= WorldShell.MIN_VIEWPORT_SHARE, "drawers closed");
            assertTrue(WorldShell.viewportShare(size[0], size[1], true) >= WorldShell.MIN_VIEWPORT_SHARE, "even with the palette row");
        }
        assertEquals((720f - 36f - 34f - 40f) / 720f, WorldShell.viewportShare(1280f, 720f, false), 1e-6f);
        assertThrows(IllegalArgumentException.class, () -> WorldShell.viewportShare(0f, 720f, false));
    }

    @Test
    void shouldStartTheWorldStyleWithEveryPanelClosedAndParseTheStyle() {
        PanelLayout panels = new PanelLayout(WorldShell.firstRunLayout());
        assertFalse(panels.isOutlinerShown());
        assertEquals(PanelLayout.DetailsMode.HIDDEN, panels.getDetailsMode());
        assertFalse(panels.isConsoleShown());
        assertFalse(panels.isAssetLibraryShown());
        assertEquals(WorldShell.Style.WORLD, WorldShell.Style.parse(null));
        assertEquals(WorldShell.Style.CLASSIC, WorldShell.Style.parse(" Classic "));
        assertThrows(IllegalArgumentException.class, () -> WorldShell.Style.parse("tabs"));
    }

    @Test
    void shouldMapTheLevelIntoTheMinimapAndAClickBackOntoTheGround() {
        Scene scene = new Scene("Valley");
        scene.setGameMode(GameMode.MODE_3D);
        scene.createEntity("West").getTransform().setLocalPosition(-1000f, 0f, 0f);
        scene.createEntity("East").getTransform().setLocalPosition(1000f, 0f, 0f);
        scene.createEntity("North").getTransform().setLocalPosition(0f, 0f, -500f);
        MinimapMath map = MinimapMath.of(scene);

        assertTrue(map.isGroundPlane());
        assertEquals(2200f, map.extent(), 1e-3f, "the larger side (2000) plus 5% each side");
        Vector2 west = map.toMinimap(new Vector3(-1000f, 0f, 0f), 100f, new Vector2());
        Vector2 east = map.toMinimap(new Vector3(1000f, 0f, 0f), 100f, new Vector2());
        assertTrue(west.x < 10f && east.x > 90f, "west on the left, east on the right: " + west + " " + east);
        Vector2 north = map.toMinimap(new Vector3(0f, 0f, -500f), 100f, new Vector2());
        assertTrue(north.y > west.y, "-z (away from the default camera) is drawn upwards, as seen from above");
        Vector3 back = map.toWorld(east.x, east.y, 100f, 0f, new Vector3());
        assertEquals(1000f, back.x, 1e-2f);
        assertEquals(0f, back.z, 1e-2f);

        Scene flat = new Scene("Platforms");
        flat.setGameMode(GameMode.MODE_2D);
        flat.createEntity("Hero").getTransform().setLocalPosition(10f, 20f, 0f);
        MinimapMath map2D = MinimapMath.of(flat);
        assertFalse(map2D.isGroundPlane());
        assertEquals(MinimapMath.MIN_EXTENT, map2D.extent(), 1e-3f, "one entity still gives a usable scale");
        assertEquals(20f, map2D.toWorld(50f, 50f, 100f, 0f, new Vector3()).y, 1e-2f, "the centre is the hero, on x and y");
    }
}
