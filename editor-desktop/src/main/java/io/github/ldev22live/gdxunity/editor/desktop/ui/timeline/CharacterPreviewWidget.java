package io.github.ldev22live.gdxunity.editor.desktop.ui.timeline;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.BufferUtils;
import io.github.ldev22live.gdxunity.engine.render.ModelPreview;

import java.nio.IntBuffer;
import java.util.Objects;

/**
 * A small 3D view of one model for the character creator (TD-056). The model is drawn by {@link ModelPreview} into a frame buffer
 * of this widget's size, which is then drawn as a picture; dragging turns the model, the mouse wheel zooms.
 *
 * <p>Nothing is drawn or updated while the widget is not on screen, so a hidden creator costs nothing. {@code beforeDraw} runs
 * just before each frame is drawn: the creator uses it to reload the model when its settings have changed.
 */
public class CharacterPreviewWidget extends Widget implements Disposable {
    /** Degrees the model turns per pixel of mouse drag. */
    private static final float DEGREES_PER_PIXEL = 0.6f;
    /** Zoom change per notch of the mouse wheel: below 1 comes nearer. */
    private static final float ZOOM_PER_NOTCH = 1.1f;
    /** Longest frame step the animation takes, so a stall does not make it jump. */
    private static final float MAX_STEP_SECONDS = 0.1f;
    private static final float MIN_WIDTH = 120f;
    private static final float PREFERRED_HEIGHT = 260f;

    private final ModelPreview preview;
    private final Color background;
    private final Runnable beforeDraw;
    /** Receives the GL viewport; LibGDX needs room for 16 integers even for one query. */
    private final IntBuffer viewportBefore = BufferUtils.newIntBuffer(16);
    private FrameBuffer frameBuffer;
    private TextureRegion region;
    private boolean disposed;

    /**
     * @param preview    the model renderer; this widget owns it and disposes it
     * @param background color behind the model
     * @param beforeDraw called before each frame is drawn (while the widget is visible)
     */
    public CharacterPreviewWidget(ModelPreview preview, Color background, Runnable beforeDraw) {
        this.preview = Objects.requireNonNull(preview, "preview cannot be null");
        this.background = Objects.requireNonNull(background, "background cannot be null");
        this.beforeDraw = Objects.requireNonNull(beforeDraw, "beforeDraw cannot be null");
        addListener(new InputListener() {
            private float lastX;
            private float lastY;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                lastX = x;
                lastY = y;
                return pointer == 0;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                // Dragging right turns the model to the right: the camera goes the other way around
                preview.orbit(-(x - lastX) * DEGREES_PER_PIXEL, (y - lastY) * DEGREES_PER_PIXEL);
                lastX = x;
                lastY = y;
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                preview.zoomBy(amountY > 0 ? ZOOM_PER_NOTCH : 1f / ZOOM_PER_NOTCH);
                return true;
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                if (getStage() != null) {
                    getStage().setScrollFocus(CharacterPreviewWidget.this);
                }
            }
        });
    }

    public ModelPreview getPreview() {
        return preview;
    }

    @Override
    public float getPrefWidth() {
        return MIN_WIDTH;
    }

    @Override
    public float getPrefHeight() {
        return PREFERRED_HEIGHT;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (disposed || getWidth() < 1f || getHeight() < 1f) {
            return;
        }
        beforeDraw.run();
        int width = ViewportPixels.of(getWidth(), Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getWidth());
        int height = ViewportPixels.of(getHeight(), Gdx.graphics.getBackBufferHeight(), Gdx.graphics.getHeight());
        ensureFrameBuffer(width, height);
        preview.update(Math.min(Gdx.graphics.getDeltaTime(), MAX_STEP_SECONDS));

        // The stage's batch is mid-frame, and its clipping (scissor) must not cut the off-screen picture
        batch.end();
        boolean scissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        viewportBefore.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewportBefore);
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        frameBuffer.begin();
        try {
            preview.render(background, width, height);
        } finally {
            frameBuffer.end();
            // Put back exactly the viewport the stage was drawing with (the frame buffer left it at the back buffer's size)
            Gdx.gl.glViewport(viewportBefore.get(0), viewportBefore.get(1), viewportBefore.get(2), viewportBefore.get(3));
            if (scissor) {
                Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            }
            batch.begin();
        }
        batch.disableBlending();
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(region, getX(), getY(), getWidth(), getHeight());
        batch.enableBlending();
    }

    private void ensureFrameBuffer(int width, int height) {
        if (frameBuffer != null && frameBuffer.getWidth() == width && frameBuffer.getHeight() == height) {
            return;
        }
        if (frameBuffer != null) {
            frameBuffer.dispose();
        }
        frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
        region = new TextureRegion(frameBuffer.getColorBufferTexture());
        // Frame buffer textures are stored bottom-up
        region.flip(false, true);
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (frameBuffer != null) {
            frameBuffer.dispose();
            frameBuffer = null;
            region = null;
        }
        preview.dispose();
    }

    /** Converts a size in UI units to back-buffer pixels, which differ on HiDPI displays. */
    private static final class ViewportPixels {
        private ViewportPixels() {
        }

        static int of(float logicalSize, int backBufferTotal, int logicalTotal) {
            if (logicalTotal <= 0) {
                return Math.max(1, Math.round(logicalSize));
            }
            return Math.max(1, Math.round(logicalSize * (backBufferTotal / (float) logicalTotal)));
        }
    }
}
