package io.github.ldev22live.gdxunity.editor.desktop.ui.sprite;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.sprite.SpriteSession;
import io.github.ldev22live.gdxunity.editor.sprite.SpriteToolController;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;

import java.util.List;
import java.util.Objects;

/**
 * The pixel canvas of the sprite editor: draws the current frame (with a checkerboard under transparent pixels and the onion skin under the
 * image) magnified with nearest filtering, a pixel grid when zoomed in, the selection and the shape preview. The left button uses the current tool,
 * the middle or right button pans, the wheel zooms around the pointer.
 */
final class SpriteCanvasWidget extends Widget implements Disposable {
    /** Smallest and largest zoom, in screen pixels per image pixel. */
    private static final int MIN_ZOOM = 1;
    private static final int MAX_ZOOM = 64;
    /** The pixel grid is drawn from this zoom up. */
    private static final int GRID_MIN_ZOOM = 8;
    private static final float GRID_ALPHA = 0.18f;
    private static final float PREVIEW_ALPHA = 0.55f;
    /** Checkerboard greys of the two cell colors. */
    private static final int CHECKER_LIGHT = 0x5A5A5AFF;
    private static final int CHECKER_DARK = 0x484848FF;
    private static final float MARGIN = 24f;
    private static final float SELECTION_LINE = 2f;

    private final SpriteSession session;
    private final SpriteToolController tools;
    private Pixmap pixmap;
    private Texture texture;
    private final Texture white;
    private long drawnRevision = -1;
    private int drawnWidth;
    private int drawnHeight;
    private int zoom = 8;
    private float originX;
    private float originY;
    private boolean placed;
    private boolean panning;
    private float lastPanX;
    private float lastPanY;
    private int hoverX = -1;
    private int hoverY = -1;

    SpriteCanvasWidget(SpriteSession session, SpriteToolController tools) {
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.tools = Objects.requireNonNull(tools, "tools cannot be null");
        Pixmap one = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        one.setColor(Color.WHITE);
        one.fill();
        white = new Texture(one);
        one.dispose();
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button == com.badlogic.gdx.Input.Buttons.LEFT) {
                    int[] p = toPixel(x, y);
                    tools.pointerDown(p[0], p[1]);
                    return true;
                }
                panning = true;
                lastPanX = x;
                lastPanY = y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (panning) {
                    originX += x - lastPanX;
                    originY += y - lastPanY;
                    lastPanX = x;
                    lastPanY = y;
                } else {
                    int[] p = toPixel(x, y);
                    tools.pointerDrag(p[0], p[1]);
                    hoverX = p[0];
                    hoverY = p[1];
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (panning) {
                    panning = false;
                } else {
                    int[] p = toPixel(x, y);
                    tools.pointerUp(p[0], p[1]);
                }
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                int[] p = toPixel(x, y);
                hoverX = p[0];
                hoverY = p[1];
                return false;
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                zoomAround(x, y, amountY < 0 ? zoom * 2 : zoom / 2);
                return true;
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                if (getStage() != null) {
                    getStage().setScrollFocus(SpriteCanvasWidget.this);
                }
            }
        });
    }

    /** The image pixel under a point in widget coordinates (may lie outside the image). */
    private int[] toPixel(float x, float y) {
        int h = session.getDocument().getHeight();
        int px = MathUtils.floor((x - originX) / zoom);
        int py = MathUtils.floor((originY + h * zoom - y) / zoom);
        return new int[] {px, py};
    }

    private void zoomAround(float x, float y, int newZoom) {
        int clamped = MathUtils.clamp(newZoom, MIN_ZOOM, MAX_ZOOM);
        if (clamped == zoom) {
            return;
        }
        // Keep the image point under the pointer fixed
        float imageX = (x - originX) / zoom;
        float imageY = (y - originY) / zoom;
        zoom = clamped;
        originX = x - imageX * zoom;
        originY = y - imageY * zoom;
    }

    /** Zoom that fits the whole image into the widget, and the image centered. */
    void fitToWidget() {
        SpriteDocument doc = session.getDocument();
        float availableW = Math.max(1f, getWidth() - 2 * MARGIN);
        float availableH = Math.max(1f, getHeight() - 2 * MARGIN);
        zoom = MathUtils.clamp((int) Math.min(availableW / doc.getWidth(), availableH / doc.getHeight()), MIN_ZOOM, MAX_ZOOM);
        originX = (getWidth() - doc.getWidth() * zoom) / 2f;
        originY = (getHeight() - doc.getHeight() * zoom) / 2f;
        placed = true;
    }

    int getZoom() {
        return zoom;
    }

    /** The pixel the pointer is over, or null when outside the image. */
    int[] getHover() {
        SpriteDocument doc = session.getDocument();
        return hoverX >= 0 && hoverY >= 0 && hoverX < doc.getWidth() && hoverY < doc.getHeight() ? new int[] {hoverX, hoverY} : null;
    }

    /** Makes the next draw fit the image again (after a new or opened sprite). */
    void requestFit() {
        placed = false;
        drawnRevision = -1;
    }

    private void refreshImage() {
        SpriteDocument doc = session.getDocument();
        if (pixmap == null || doc.getWidth() != drawnWidth || doc.getHeight() != drawnHeight) {
            if (pixmap != null) {
                pixmap.dispose();
                texture.dispose();
            }
            pixmap = new Pixmap(doc.getWidth(), doc.getHeight(), Pixmap.Format.RGBA8888);
            texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            drawnWidth = doc.getWidth();
            drawnHeight = doc.getHeight();
            drawnRevision = -1;
        }
        if (drawnRevision == session.getRevision()) {
            return;
        }
        int[] frame = session.flattened();
        int[] onion = session.onionSkinPixels();
        for (int y = 0; y < drawnHeight; y++) {
            for (int x = 0; x < drawnWidth; x++) {
                int i = y * drawnWidth + x;
                int background = ((x + y) & 1) == 0 ? CHECKER_LIGHT : CHECKER_DARK;
                if (onion != null) {
                    background = SpriteDocument.over(background, onion[i], 255);
                }
                pixmap.drawPixel(x, y, SpriteDocument.over(background, frame[i], 255));
            }
        }
        texture.draw(pixmap, 0, 0);
        drawnRevision = session.getRevision();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        if (!placed && getWidth() > 0) {
            fitToWidget();
        }
        refreshImage();
        batch.flush();
        if (!clipBegin()) {
            return;
        }
        float x = getX() + originX;
        float y = getY() + originY;
        float w = drawnWidth * (float) zoom;
        float h = drawnHeight * (float) zoom;
        batch.setColor(1f, 1f, 1f, parentAlpha);
        batch.draw(texture, x, y, w, h);
        if (zoom >= GRID_MIN_ZOOM) {
            batch.setColor(1f, 1f, 1f, GRID_ALPHA * parentAlpha);
            for (int i = 0; i <= drawnWidth; i++) {
                batch.draw(white, x + i * zoom, y, 1f, h);
            }
            for (int j = 0; j <= drawnHeight; j++) {
                batch.draw(white, x, y + j * zoom, w, 1f);
            }
        }
        List<int[]> preview = tools.getPreview();
        if (preview != null) {
            batch.setColor(1f, 1f, 1f, PREVIEW_ALPHA * parentAlpha);
            for (int[] p : preview) {
                batch.draw(white, x + p[0] * zoom, y + (drawnHeight - 1 - p[1]) * zoom, zoom, zoom);
            }
        }
        SpriteSession.SelectionRect s = session.getSelection();
        if (s != null) {
            drawFrameAround(batch, x + s.x() * zoom, y + (drawnHeight - s.y() - s.height()) * zoom, s.width() * zoom, s.height() * zoom, parentAlpha);
        }
        batch.setColor(Color.WHITE);
        batch.flush();
        clipEnd();
    }

    private void drawFrameAround(Batch batch, float x, float y, float w, float h, float parentAlpha) {
        batch.setColor(1f, 0.85f, 0.2f, parentAlpha);
        batch.draw(white, x, y, w, SELECTION_LINE);
        batch.draw(white, x, y + h - SELECTION_LINE, w, SELECTION_LINE);
        batch.draw(white, x, y, SELECTION_LINE, h);
        batch.draw(white, x + w - SELECTION_LINE, y, SELECTION_LINE, h);
    }

    @Override
    public void dispose() {
        if (pixmap != null) {
            pixmap.dispose();
            texture.dispose();
            pixmap = null;
            texture = null;
        }
        white.dispose();
    }
}
