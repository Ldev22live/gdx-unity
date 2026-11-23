package io.github.ldev22live.gdxunity.editor.sprite;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.sprite.AutoTileRule;
import io.github.ldev22live.gdxunity.engine.sprite.TileMapData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TilemapSessionTest {
    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private final TilemapSession session = new TilemapSession(new TileMapData(8, 6));

    private int cell(int col, int row) {
        return session.getMap().get(session.getLayer(), col, row);
    }

    private static AutoTileRule rule() {
        int[] tiles = new int[16];
        for (int i = 0; i < 16; i++) {
            tiles[i] = 20 + i;
        }
        return new AutoTileRule("wall", tiles);
    }

    @Test
    void shouldPaintAStrokeAsOneUndoStepAndRestoreItOnUndoAndRedo() {
        session.setTile(3);
        session.pointerDown(0, 0);
        session.pointerDrag(4, 0);
        assertEquals(3, cell(2, 0), "painted while dragging");
        session.pointerUp(5, 0);

        assertEquals(1, session.getHistory().getUndoCount());
        assertEquals(3, cell(5, 0));
        session.getHistory().undo();
        assertEquals(TileMapData.EMPTY, cell(2, 0));
        session.getHistory().redo();
        assertEquals(3, cell(5, 0));
    }

    @Test
    void shouldEraseWithTheEraserAndIgnoreCellsOutsideTheMap() {
        session.setTile(2);
        session.setTool(TilemapSession.Tool.RECT);
        session.pointerDown(0, 0);
        session.pointerUp(3, 3);
        session.setTool(TilemapSession.Tool.ERASE);

        session.pointerDown(1, 1);
        session.pointerDrag(20, 1);
        session.pointerUp(20, 1);

        assertEquals(TileMapData.EMPTY, cell(1, 1));
        assertEquals(TileMapData.EMPTY, cell(3, 1));
        assertEquals(2, cell(0, 0));
    }

    @Test
    void shouldPreviewARectangleAndFillItOnReleaseWithOneUndoStep() {
        session.setTile(4);
        session.setTool(TilemapSession.Tool.RECT);
        session.pointerDown(1, 1);
        session.pointerDrag(3, 2);

        assertEquals(6, session.getPreview().size());
        assertEquals(TileMapData.EMPTY, cell(1, 1));
        session.pointerUp(3, 2);

        assertEquals(4, cell(3, 2));
        assertNull(session.getPreview());
        assertEquals(1, session.getHistory().getUndoCount());
    }

    @Test
    void shouldFillTheConnectedRegionAndPickATileFromTheMap() {
        session.setTile(2);
        session.setTool(TilemapSession.Tool.RECT);
        session.pointerDown(3, 0);
        session.pointerUp(3, 5);
        session.setTile(7);
        session.setTool(TilemapSession.Tool.FILL);

        session.pointerDown(0, 0);
        session.pointerUp(0, 0);

        assertEquals(7, cell(2, 5), "the left side");
        assertEquals(2, cell(3, 3), "the wall");
        assertEquals(TileMapData.EMPTY, cell(4, 0), "the other side is a separate region");

        session.setTool(TilemapSession.Tool.PICK);
        session.pointerDown(3, 3);
        session.pointerUp(3, 3);
        assertEquals(2, session.getTile());
        assertEquals(2, session.getHistory().getUndoCount(), "picking is not an edit");
    }

    @Test
    void shouldPaintAndEraseWithAnAutoTileRuleSoEdgesFit() {
        session.putRule(rule());
        session.setAutoRule("wall");
        session.pointerDown(1, 1);
        session.pointerDrag(3, 1);
        session.pointerUp(3, 1);

        assertEquals(rule().tileFor(AutoTileRule.EAST), cell(1, 1));
        assertEquals(rule().tileFor(AutoTileRule.EAST | AutoTileRule.WEST), cell(2, 1));
        assertEquals(rule().tileFor(AutoTileRule.WEST), cell(3, 1));

        session.setTool(TilemapSession.Tool.ERASE);
        session.pointerDown(2, 1);
        session.pointerUp(2, 1);
        assertEquals(rule().tileFor(0), cell(1, 1));
        assertEquals(rule().tileFor(0), cell(3, 1));
        session.getHistory().undo();
        assertEquals(rule().tileFor(AutoTileRule.EAST | AutoTileRule.WEST), cell(2, 1));
    }

    @Test
    void shouldRefuseAnUnknownRuleAndABadTile() {
        assertThrows(IllegalArgumentException.class, () -> session.setAutoRule("nope"));
        assertThrows(IllegalArgumentException.class, () -> session.setTile(0));
        assertThrows(IndexOutOfBoundsException.class, () -> session.setLayer(4));
    }

    @Test
    void shouldAddLayersAndPaintOnTheCurrentOneOnly() {
        session.setTile(5);
        session.addLayer("Decor");
        assertEquals(1, session.getLayer());

        session.pointerDown(0, 0);
        session.pointerUp(0, 0);

        assertEquals(5, session.getMap().get(1, 0, 0));
        assertEquals(TileMapData.EMPTY, session.getMap().get(0, 0, 0));
        session.getHistory().undo();
        session.getHistory().undo();
        assertEquals(1, session.getMap().getLayerCount());
        assertEquals(0, session.getLayer(), "the layer index is kept valid after undo");
    }

    @Test
    void shouldMarkTilesSolidResizeAndClearAsUndoableSteps() {
        session.setTile(3);
        session.pointerDown(7, 5);
        session.pointerUp(7, 5);

        session.setSolid(3, true);
        assertTrue(session.getMap().isSolid(3));
        session.resize(4, 4);
        assertEquals(4, session.getMap().getCols());
        session.getHistory().undo();
        assertEquals(8, session.getMap().getCols());
        assertEquals(3, cell(7, 5));
        session.clearLayer();
        assertEquals(TileMapData.EMPTY, cell(7, 5));
        session.getHistory().undo();
        assertEquals(3, cell(7, 5));
    }

    @Test
    void shouldRecordNothingWhenAPaintDoesNotChangeAnyCell() {
        session.setTile(3);
        session.pointerDown(0, 0);
        session.pointerUp(0, 0);
        int steps = session.getHistory().getUndoCount();

        session.pointerDown(0, 0);
        session.pointerUp(0, 0);

        assertEquals(steps, session.getHistory().getUndoCount());
        assertFalse(session.isDown());
        assertNotNull(session.getMap());
    }

    @Test
    void shouldChangeTheRevisionWhenSomethingVisibleChanges() {
        long start = session.getRevision();

        session.setTile(2);
        session.pointerDown(0, 0);
        session.pointerUp(0, 0);

        assertTrue(session.getRevision() > start);
    }
}
