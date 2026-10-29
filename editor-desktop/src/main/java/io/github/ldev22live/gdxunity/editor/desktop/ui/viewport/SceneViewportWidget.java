package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;

import java.util.Objects;

/**
 * Scene view panel (TD-024). The scene is rendered off-screen into a {@link FrameBuffer} sized to this
 * widget and drawn as a texture, so it fills exactly the panel area and pointer input arrives in
 * panel-local coordinates that match what is drawn.
 *
 * <p>Clicking the view gives it keyboard focus, so navigation keys (e.g. WASD while flying in 3D) reach the
 * {@link ViewportInteraction} first; keys it does not consume fall through to the global editor shortcuts.
 */
public class SceneViewportWidget extends Widget implements Disposable {

    /** Receives the panel size in UI units whenever it changes, so cameras can match it. */
    public interface ResizeListener {
        void onViewportResized(int width, int height);
    }

    private final ViewportInteraction interaction;
    private final ResizeListener resizeListener;

    private FrameBuffer frameBuffer;
    private TextureRegion frameRegion;
    private int lastWidth;
    private int lastHeight;
    private boolean disposed;

    public SceneViewportWidget(ViewportInteraction interaction, ResizeListener resizeListener) {
        this.interaction = Objects.requireNonNull(interaction, "interaction cannot be null");
        this.resizeListener = Objects.requireNonNull(resizeListener, "resizeListener cannot be null");
        setTouchable(Touchable.enabled);
        addListener(new ViewportInputListener());
    }

    /**
     * Renders the scene into this widget's frame buffer. Must be called outside any active batch, before the
     * UI stage is drawn.
     *
     * @param background clear color behind the scene
     * @param scenePass  draws the scene with the GL viewport set to the frame buffer
     */
    public void renderScene(Color background, Runnable scenePass) {
        Objects.requireNonNull(background, "background cannot be null");
        Objects.requireNonNull(scenePass, "scenePass cannot be null");
        int width = Math.round(getWidth());
        int height = Math.round(getHeight());
        if (disposed || getStage() == null || width <= 0 || height <= 0) {
            return;
        }

        if (width != lastWidth || height != lastHeight) {
            lastWidth = width;
            lastHeight = height;
            resizeListener.onViewportResized(width, height);
        }

        ensureFrameBuffer(toBackBufferPixels(width, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getWidth()),
            toBackBufferPixels(height, Gdx.graphics.getBackBufferHeight(), Gdx.graphics.getHeight()));

        frameBuffer.begin();
        try {
            Gdx.gl.glClearColor(background.r, background.g, background.b, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            scenePass.run();
        } finally {
            frameBuffer.end();
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (frameRegion == null) {
            return;
        }
        // The frame buffer is opaque scene output; blending would let translucent shapes show the UI behind
        batch.disableBlending();
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(frameRegion, getX(), getY(), getWidth(), getHeight());
        batch.enableBlending();
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
            frameRegion = null;
        }
    }

    /** Converts a size in UI (logical) units to back-buffer pixels, which differ on HiDPI displays. */
    static int toBackBufferPixels(int logicalSize, int backBufferTotal, int logicalTotal) {
        if (logicalTotal <= 0) {
            return Math.max(1, logicalSize);
        }
        return Math.max(1, Math.round(logicalSize * (backBufferTotal / (float) logicalTotal)));
    }

    private void ensureFrameBuffer(int pixelWidth, int pixelHeight) {
        if (frameBuffer != null && frameBuffer.getWidth() == pixelWidth && frameBuffer.getHeight() == pixelHeight) {
            return;
        }
        if (frameBuffer != null) {
            frameBuffer.dispose();
        }
        frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, pixelWidth, pixelHeight, true);
        frameRegion = new TextureRegion(frameBuffer.getColorBufferTexture());
        // Frame buffer textures are stored bottom-up
        frameRegion.flip(false, true);
    }

    private static int currentModifiers() {
        int modifiers = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
            modifiers |= EditorKeyboardDispatcher.MOD_CTRL;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
            modifiers |= EditorKeyboardDispatcher.MOD_SHIFT;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT)) {
            modifiers |= EditorKeyboardDispatcher.MOD_ALT;
        }
        return modifiers;
    }

    private class ViewportInputListener extends InputListener {
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            if (pointer != 0) {
                return false;
            }
            Stage stage = getStage();
            if (stage != null) {
                // Leave text fields; navigation keys go to the viewport, other keys to the editor shortcuts
                stage.setKeyboardFocus(SceneViewportWidget.this);
                stage.setScrollFocus(SceneViewportWidget.this);
            }
            return interaction.touchDown(x, y, getWidth(), getHeight(), button, currentModifiers());
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            interaction.touchDragged(x, y, getWidth(), getHeight());
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            interaction.touchUp(button);
        }

        @Override
        public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
            return interaction.scrolled(amountY);
        }

        @Override
        public boolean keyDown(InputEvent event, int keycode) {
            return interaction.keyDown(keycode);
        }

        @Override
        public boolean keyUp(InputEvent event, int keycode) {
            return interaction.keyUp(keycode);
        }

        @Override
        public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
            // pointer == -1 means mouse hover rather than a touch; scroll the viewport under the mouse
            Stage stage = getStage();
            if (pointer == -1 && stage != null) {
                stage.setScrollFocus(SceneViewportWidget.this);
            }
        }
    }
}
