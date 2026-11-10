package io.github.ldev22live.gdxunity.editor.sprite;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteToolControllerTest {
    private static final int RED = 0xFF0000FF;
    private static final int BLUE = 0x0000FFFF;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private final SpriteSession session = new SpriteSession(new SpriteDocument(8, 8));
    private final SpriteToolController tools = new SpriteToolController(session);

    private int px(int x, int y) {
        return session.getDocument().getPixel(0, 0, x, y);
    }

    @Test
    void shouldDrawAPencilStrokeAsOneUndoStepFromDownToUp() {
        session.setColor(RED);
        tools.pointerDown(0, 0);
        tools.pointerDrag(3, 0);
        tools.pointerUp(5, 0);

        assertEquals(1, session.getHistory().getUndoCount());
        assertEquals(RED, px(0, 0));
        assertEquals(RED, px(5, 0));
        assertFalse(tools.isDown());
    }

    @Test
    void shouldPaintASinglePixelOnAClickWithoutDragging() {
        session.setColor(BLUE);
        tools.pointerDown(2, 2);
        tools.pointerUp(2, 2);

        assertEquals(BLUE, px(2, 2));
    }

    @Test
    void shouldPreviewAShapeWhileDraggingAndEditOnlyOnRelease() {
        session.setColor(RED);
        tools.setTool(SpriteToolController.Tool.RECT);
        tools.pointerDown(1, 1);
        tools.pointerDrag(4, 3);

        assertNotNull(tools.getPreview());
        assertEquals(10, tools.getPreview().size());
        assertEquals(0, session.getHistory().getUndoCount(), "nothing is drawn yet");
        assertEquals(SpriteDocument.TRANSPARENT, px(1, 1));

        tools.pointerUp(4, 3);
        assertEquals(RED, px(1, 1));
        assertEquals(RED, px(4, 3));
        assertEquals(SpriteDocument.TRANSPARENT, px(2, 2), "an outline");
        assertEquals(1, session.getHistory().getUndoCount());
        assertNull(tools.getPreview());
    }

    @Test
    void shouldFillShapesWhenAskedAndDrawLinesAndEllipses() {
        session.setColor(RED);
        tools.setFilledShapes(true);
        tools.setTool(SpriteToolController.Tool.RECT);
        tools.pointerDown(0, 0);
        tools.pointerUp(2, 2);
        assertEquals(RED, px(1, 1));

        tools.setTool(SpriteToolController.Tool.LINE);
        tools.pointerDown(0, 7);
        tools.pointerUp(7, 7);
        assertEquals(RED, px(4, 7));

        tools.setTool(SpriteToolController.Tool.ELLIPSE);
        tools.pointerDown(3, 3);
        tools.pointerUp(7, 5);
        assertEquals(RED, px(5, 4));
    }

    @Test
    void shouldFillAndPickOnAClick() {
        session.setColor(RED);
        tools.setTool(SpriteToolController.Tool.FILL);
        tools.pointerDown(3, 3);
        tools.pointerUp(3, 3);
        assertEquals(RED, px(7, 7));

        session.setColor(BLUE);
        tools.setTool(SpriteToolController.Tool.PICKER);
        tools.pointerDown(0, 0);
        tools.pointerUp(0, 0);

        assertEquals(RED, session.getColor());
    }

    @Test
    void shouldKeepTheColorWhenPickingATransparentPixel() {
        session.setColor(BLUE);
        tools.setTool(SpriteToolController.Tool.PICKER);

        tools.pointerDown(0, 0);
        tools.pointerUp(0, 0);

        assertEquals(BLUE, session.getColor());
    }

    @Test
    void shouldSelectARectangleAndMoveItsPixelsByDraggingInsideIt() {
        session.setColor(RED);
        tools.pointerDown(1, 1);
        tools.pointerUp(1, 1);
        tools.setTool(SpriteToolController.Tool.SELECT);

        tools.pointerDown(0, 0);
        tools.pointerDrag(2, 2);
        tools.pointerUp(2, 2);
        assertEquals(3, session.getSelection().width());

        tools.pointerDown(1, 1);
        tools.pointerDrag(4, 4);
        assertNotNull(tools.getPreview(), "the moved selection outline");
        tools.pointerUp(4, 4);

        assertEquals(SpriteDocument.TRANSPARENT, px(1, 1));
        assertEquals(RED, px(4, 4));
        assertEquals(3, session.getSelection().x());
    }

    @Test
    void shouldNotChangeToolWhileDraggingAndIgnoreMovesWithoutADown() {
        tools.pointerDrag(3, 3);
        tools.pointerUp(3, 3);
        assertEquals(0, session.getHistory().getUndoCount());

        tools.pointerDown(0, 0);
        tools.setTool(SpriteToolController.Tool.ERASER);
        assertEquals(SpriteToolController.Tool.PENCIL, tools.getTool());
        tools.pointerUp(0, 0);
        tools.setTool(SpriteToolController.Tool.ERASER);
        assertTrue(tools.getTool() == SpriteToolController.Tool.ERASER);
    }
}
