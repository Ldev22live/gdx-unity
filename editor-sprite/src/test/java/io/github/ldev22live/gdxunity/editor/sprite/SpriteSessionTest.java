package io.github.ldev22live.gdxunity.editor.sprite;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteSlice;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteSessionTest {
    private static final int RED = 0xFF0000FF;
    private static final int BLUE = 0x0000FFFF;
    private static final int CLEAR = SpriteDocument.TRANSPARENT;

    @TempDir
    File dir;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static SpriteSession session(int w, int h) {
        SpriteSession s = new SpriteSession(new SpriteDocument(w, h));
        s.setColor(RED);
        return s;
    }

    private static int px(SpriteSession s, int x, int y) {
        return s.getDocument().getPixel(s.getLayer(), s.getFrame(), x, y);
    }

    @Test
    void shouldDrawAStrokeAsOneUndoStepAndRestoreItOnUndoAndRedo() {
        SpriteSession s = session(8, 8);
        SpriteSession.Stroke stroke = s.beginPencil();
        stroke.add(0, 0);
        stroke.add(5, 0);

        assertEquals(6, stroke.commit());

        assertEquals(1, s.getHistory().getUndoCount());
        assertEquals(RED, px(s, 3, 0));
        s.getHistory().undo();
        assertEquals(CLEAR, px(s, 3, 0));
        s.getHistory().redo();
        assertEquals(RED, px(s, 3, 0));
    }

    @Test
    void shouldFillTheGapBetweenPointerPositionsWithALine() {
        SpriteSession s = session(8, 8);
        SpriteSession.Stroke stroke = s.beginPencil();
        stroke.add(0, 0);
        stroke.add(7, 0);
        stroke.commit();

        for (int x = 0; x < 8; x++) {
            assertEquals(RED, px(s, x, 0), "x=" + x);
        }
    }

    @Test
    void shouldErasePixelsWithTheEraser() {
        SpriteSession s = session(4, 4);
        s.drawRect(0, 0, 3, 3, true);

        SpriteSession.Stroke eraser = s.beginEraser();
        eraser.add(1, 1);
        eraser.commit();

        assertEquals(CLEAR, px(s, 1, 1));
        assertEquals(RED, px(s, 2, 2));
    }

    @Test
    void shouldApplyPixelPerfectOnlyToPlainFreehandStrokes() {
        SpriteSession s = session(4, 4);
        SpriteSession.Stroke stroke = s.beginPencil();
        stroke.add(0, 0);
        stroke.add(1, 0);
        stroke.add(1, 1);
        stroke.commit();

        assertEquals(CLEAR, px(s, 1, 0), "the corner pixel is dropped");
        assertEquals(RED, px(s, 1, 1));

        SpriteSession off = session(4, 4);
        off.setPixelPerfect(false);
        SpriteSession.Stroke plain = off.beginPencil();
        plain.add(0, 0);
        plain.add(1, 0);
        plain.add(1, 1);
        plain.commit();
        assertEquals(RED, px(off, 1, 0));
    }

    @Test
    void shouldMirrorAboutTheCanvasCenterAndUseTheBrushSize() {
        SpriteSession s = session(8, 8);
        s.setMirrorX(true);
        s.setMirrorY(true);

        s.paint(List.of(new int[] {1, 2}), RED, "Dot");

        assertEquals(RED, px(s, 1, 2));
        assertEquals(RED, px(s, 6, 2));
        assertEquals(RED, px(s, 1, 5));
        assertEquals(RED, px(s, 6, 5));

        SpriteSession big = session(8, 8);
        big.setBrush(3);
        assertEquals(9, big.paint(List.of(new int[] {4, 4}), RED, "Dot"));
        assertEquals(RED, px(big, 3, 3));
        assertEquals(RED, px(big, 5, 5));
    }

    @Test
    void shouldWrapAroundTheEdgesInTileModeAndClipOtherwise() {
        SpriteSession wrap = session(4, 4);
        wrap.setTileMode(true);
        wrap.setBrush(3);

        wrap.paint(List.of(new int[] {0, 0}), RED, "Dot");

        assertEquals(RED, px(wrap, 3, 3), "the brush spilled over the left and top edges");
        SpriteSession clip = session(4, 4);
        clip.setBrush(3);
        assertEquals(4, clip.paint(List.of(new int[] {0, 0}), RED, "Dot"), "only the 2x2 pixels inside the canvas");
    }

    @Test
    void shouldFillAndPickColors() {
        SpriteSession s = session(4, 4);
        s.drawLine(2, 0, 2, 3);
        s.setColor(BLUE);

        assertEquals(8, s.fill(0, 0), "the two columns on the left");
        assertEquals(BLUE, px(s, 1, 3));
        assertEquals(CLEAR, px(s, 3, 3));
        assertEquals(BLUE, s.pick(0, 0));
        assertEquals(CLEAR, s.pick(-1, 0));
        assertEquals(BLUE, s.getColor());
    }

    @Test
    void shouldReportNoChangeAndRecordNoUndoStepWhenNothingChanged() {
        SpriteSession s = session(4, 4);
        s.drawRect(0, 0, 3, 3, true);
        int steps = s.getHistory().getUndoCount();

        assertEquals(0, s.drawRect(0, 0, 3, 3, true));
        assertEquals(steps, s.getHistory().getUndoCount());
    }

    @Test
    void shouldMoveFlipAndRotateASelection() {
        SpriteSession s = session(6, 6);
        s.setColor(RED);
        s.paint(List.of(new int[] {0, 0}), RED, "a");
        s.paint(List.of(new int[] {1, 0}), BLUE, "b");
        s.select(0, 0, 1, 1);

        s.flipSelection(true);
        assertEquals(BLUE, px(s, 0, 0));
        assertEquals(RED, px(s, 1, 0));

        s.rotateSelection();
        assertEquals(BLUE, px(s, 1, 0), "clockwise: top-left goes to top-right");

        s.moveSelection(3, 3);
        assertEquals(CLEAR, px(s, 1, 0));
        assertEquals(BLUE, px(s, 4, 3));
        assertEquals(3, s.getSelection().x());

        s.getHistory().undo();
        assertEquals(BLUE, px(s, 1, 0));
    }

    @Test
    void shouldClipWhatLeavesTheCanvasWhenMovingASelectionAndRefuseToRotateARectangle() {
        SpriteSession s = session(4, 4);
        s.paint(List.of(new int[] {3, 3}), RED, "a");
        s.select(3, 3, 3, 3);
        s.moveSelection(1, 1);
        assertEquals(CLEAR, px(s, 3, 3));

        s.select(0, 0, 2, 1);
        assertThrows(IllegalStateException.class, s::rotateSelection);
        s.clearSelection();
        assertThrows(IllegalStateException.class, s::deleteSelection);
    }

    @Test
    void shouldDeleteTheSelectedPixels() {
        SpriteSession s = session(4, 4);
        s.drawRect(0, 0, 3, 3, true);
        s.select(1, 1, 2, 2);

        assertEquals(4, s.deleteSelection());
        assertEquals(CLEAR, px(s, 1, 1));
        assertEquals(RED, px(s, 0, 0));
    }

    @Test
    void shouldAddRemoveAndReorderLayersWithUndo() {
        SpriteSession s = session(2, 2);
        s.drawRect(0, 0, 1, 1, true);
        s.addLayer("Shadow");

        assertEquals(2, s.getDocument().getLayerCount());
        assertEquals(1, s.getLayer());
        assertEquals(CLEAR, px(s, 0, 0), "the new layer is empty");
        s.setLayerVisible(1, false);
        assertFalse(s.getDocument().getLayer(1).visible());

        s.getHistory().undo();
        s.getHistory().undo();
        assertEquals(1, s.getDocument().getLayerCount());
        assertEquals(0, s.getLayer(), "the position is kept valid after undo");
        assertEquals(RED, px(s, 0, 0));
    }

    @Test
    void shouldRefuseToRemoveTheLastLayerWithoutRecordingAStep() {
        SpriteSession s = session(2, 2);

        s.removeLayer();

        assertEquals(1, s.getDocument().getLayerCount());
        assertEquals(0, s.getHistory().getUndoCount());
    }

    @Test
    void shouldAddDuplicateAndRemoveFramesAndMoveTags() {
        SpriteSession s = session(2, 2);
        s.drawRect(0, 0, 0, 0, true);
        s.duplicateFrame();

        assertEquals(2, s.getDocument().getFrameCount());
        assertEquals(1, s.getFrame());
        assertEquals(RED, px(s, 0, 0), "a copy of frame 0");

        s.addFrame();
        assertEquals(CLEAR, px(s, 0, 0));
        s.addTag("walk", 0, 1, SpriteTag.Direction.FORWARD);
        s.setFrameDuration(2, 400);
        assertEquals(400, s.getDocument().getFrameDuration(2));

        s.removeFrame();
        assertEquals(2, s.getDocument().getFrameCount());
        assertEquals(1, s.getFrame());
        assertEquals(1, s.getDocument().getTags().size());
        s.getHistory().undo();
        assertEquals(3, s.getDocument().getFrameCount());
    }

    @Test
    void shouldAddAndRemoveTagsAndSlices() {
        SpriteSession s = session(8, 8);
        s.addFrame();
        s.addTag("idle", 0, 1, SpriteTag.Direction.PING_PONG);
        s.addSlice(SpriteSlice.of("panel", 0, 0, 4, 4));

        assertEquals("idle", s.getDocument().getTags().get(0).name());
        assertEquals(1, s.getDocument().getSlices().size());

        s.removeTag("idle");
        s.removeSlice("panel");
        assertTrue(s.getDocument().getTags().isEmpty());
        assertTrue(s.getDocument().getSlices().isEmpty());
        s.getHistory().undo();
        assertEquals(1, s.getDocument().getSlices().size());
    }

    @Test
    void shouldRecolorPixelsWhenAPaletteColorChanges() {
        SpriteSession s = session(2, 2);
        s.drawRect(0, 0, 1, 1, true);
        s.addPaletteColor(RED);
        s.addPaletteColor(RED);
        assertEquals(1, s.getDocument().getPalette().length, "no duplicate colors");

        s.setPaletteColor(0, BLUE, true);

        assertEquals(BLUE, px(s, 1, 1));
        assertArrayEquals(new int[] {BLUE}, s.getDocument().getPalette());
        s.getHistory().undo();
        assertEquals(RED, px(s, 1, 1));
        assertArrayEquals(new int[] {RED}, s.getDocument().getPalette());
    }

    @Test
    void shouldResizeTheCanvasAsOneUndoableStep() {
        SpriteSession s = session(4, 4);
        s.drawRect(0, 0, 3, 3, true);

        s.resizeCanvas(2, 2);
        assertEquals(2, s.getDocument().getWidth());
        s.getHistory().undo();
        assertEquals(4, s.getDocument().getWidth());
        assertEquals(RED, px(s, 3, 3));
    }

    @Test
    void shouldShowOnionSkinOnlyWhenOnAndWhenThereIsANeighbour() {
        SpriteSession s = session(2, 2);
        s.drawRect(0, 0, 1, 1, true);
        s.duplicateFrame();

        assertNull(s.onionSkinPixels());
        s.setOnionSkin(true);
        int[] onion = s.onionSkinPixels();
        assertNotNull(onion);
        assertTrue((onion[0] & 0xFF) > 0, "the previous frame shows faintly");
        assertTrue((onion[0] & 0xFF) < 255);

        s.addFrame();
        s.setFrame(0);
        s.getDocument().removeFrame(2);
        s.getDocument().removeFrame(1);
        assertNull(s.onionSkinPixels(), "a single frame has no neighbour");
    }

    @Test
    void shouldTrackUnsavedChangesAndSaveAndReopenAFile() throws IOException {
        SpriteSession s = session(4, 4);
        assertFalse(s.isDirty());
        s.drawRect(0, 0, 1, 1, true);
        s.addFrame();
        assertTrue(s.isDirty());

        File target = new File(dir, "hero.sprite");
        s.saveAs(target);
        assertFalse(s.isDirty());

        SpriteSession back = SpriteSession.open(target);
        assertTrue(s.getDocument().contentEquals(back.getDocument()));
        assertEquals(target, back.getFile());
    }

    @Test
    void shouldRejectBadBrushSizesAndOutOfRangePositions() {
        SpriteSession s = session(2, 2);

        assertThrows(IllegalArgumentException.class, () -> s.setBrush(0));
        assertThrows(IllegalArgumentException.class, () -> s.setBrush(SpriteSession.MAX_BRUSH + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> s.setLayer(3));
        assertThrows(IndexOutOfBoundsException.class, () -> s.setFrame(1));
    }
}
