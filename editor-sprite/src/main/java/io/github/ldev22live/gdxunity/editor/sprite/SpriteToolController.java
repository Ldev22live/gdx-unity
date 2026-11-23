package io.github.ldev22live.gdxunity.editor.sprite;

import java.util.List;
import java.util.Objects;

/**
 * Turns pointer events on the canvas (pixel coordinates) into edits of a {@link SpriteSession} for the current {@link Tool}. Shape tools show a
 * preview while the pointer is down and edit once, on release, so a shape is one undo step. The Select tool makes a rectangle selection, and
 * dragging inside an existing selection moves its pixels.
 */
public final class SpriteToolController {
    /** The drawing tools. */
    public enum Tool { PENCIL, ERASER, LINE, RECT, ELLIPSE, FILL, PICKER, SELECT }

    private final SpriteSession session;
    private Tool tool = Tool.PENCIL;
    private boolean filledShapes;
    private boolean down;
    private int startX;
    private int startY;
    private int lastX;
    private int lastY;
    private SpriteSession.Stroke stroke;
    private boolean moving;
    private List<int[]> preview;

    public SpriteToolController(SpriteSession session) {
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    public Tool getTool() {
        return tool;
    }

    /** Changes the tool; ignored while a drag is going on. */
    public void setTool(Tool tool) {
        Objects.requireNonNull(tool, "tool cannot be null");
        if (!down) {
            this.tool = tool;
        }
    }

    public boolean isFilledShapes() {
        return filledShapes;
    }

    /** Whether rectangles and ellipses are filled instead of outlines. */
    public void setFilledShapes(boolean filled) {
        this.filledShapes = filled;
    }

    /**
     * The pixels of the shape being dragged (already with brush and mirror applied), or the outline of the selection being moved; null when no
     * drag shows a preview. The canvas draws it over the image.
     */
    public List<int[]> getPreview() {
        return preview;
    }

    /** Whether a drag is going on. */
    public boolean isDown() {
        return down;
    }

    public void pointerDown(int x, int y) {
        if (down) {
            return;
        }
        down = true;
        startX = x;
        startY = y;
        lastX = x;
        lastY = y;
        preview = null;
        switch (tool) {
            case PENCIL -> {
                stroke = session.beginPencil();
                stroke.add(x, y);
            }
            case ERASER -> {
                stroke = session.beginEraser();
                stroke.add(x, y);
            }
            case FILL -> session.fill(x, y);
            case PICKER -> {
                int picked = session.pick(x, y);
                if ((picked & 0xFF) != 0) {
                    session.setColor(picked);
                }
            }
            case SELECT -> {
                SpriteSession.SelectionRect s = session.getSelection();
                moving = s != null && x >= s.x() && y >= s.y() && x < s.x() + s.width() && y < s.y() + s.height();
                if (!moving) {
                    session.select(x, y, x, y);
                }
            }
            default -> { }
        }
    }

    public void pointerDrag(int x, int y) {
        if (!down || (x == lastX && y == lastY)) {
            return;
        }
        lastX = x;
        lastY = y;
        switch (tool) {
            case PENCIL, ERASER -> stroke.add(x, y);
            case LINE -> preview = session.expand(PixelOps.line(startX, startY, x, y));
            case RECT -> preview = session.expand(PixelOps.rect(startX, startY, x, y, filledShapes));
            case ELLIPSE -> preview = session.expand(PixelOps.ellipse(startX, startY, x, y, filledShapes));
            case SELECT -> {
                if (moving) {
                    SpriteSession.SelectionRect s = session.getSelection();
                    preview = PixelOps.rect(s.x() + x - startX, s.y() + y - startY, s.x() + s.width() - 1 + x - startX, s.y() + s.height() - 1 + y - startY, false);
                } else {
                    session.select(startX, startY, x, y);
                }
            }
            default -> { }
        }
    }

    public void pointerUp(int x, int y) {
        if (!down) {
            return;
        }
        pointerDrag(x, y);
        switch (tool) {
            case PENCIL, ERASER -> {
                stroke.commit();
                stroke = null;
            }
            case LINE -> session.drawLine(startX, startY, x, y);
            case RECT -> session.drawRect(startX, startY, x, y, filledShapes);
            case ELLIPSE -> session.drawEllipse(startX, startY, x, y, filledShapes);
            case SELECT -> {
                if (moving && (x != startX || y != startY)) {
                    session.moveSelection(x - startX, y - startY);
                }
            }
            default -> { }
        }
        down = false;
        moving = false;
        preview = null;
    }
}
