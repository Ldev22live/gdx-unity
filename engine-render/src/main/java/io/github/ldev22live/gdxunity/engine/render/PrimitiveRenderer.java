package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.ldev22live.gdxunity.engine.core.EngineRenderer;

import java.util.Objects;

/**
 * Standard LibGDX 2D primitive renderer.
 * Renders a clearly visible colored primitive (rectangle) on a contrasting background.
 * Uses an {@link OrthographicCamera} and {@link FitViewport} to prevent distortion during resizing.
 */
public class PrimitiveRenderer implements EngineRenderer {
    private final float virtualWidth;
    private final float virtualHeight;

    private OrthographicCamera camera;
    private Viewport viewport;
    private ShapeRenderer shapeRenderer;

    private Color clearColor = new Color(0.12f, 0.14f, 0.18f, 1.0f); // Dark slate background
    private Color primitiveColor = new Color(0.2f, 0.7f, 0.9f, 1.0f); // Bright cyan/blue primitive
    private boolean initialized = false;

    public PrimitiveRenderer() {
        this(640, 480);
    }

    public PrimitiveRenderer(float virtualWidth, float virtualHeight) {
        this.virtualWidth = virtualWidth;
        this.virtualHeight = virtualHeight;
    }

    @Override
    public void initialize() {
        if (initialized) {
            return;
        }
        this.camera = new OrthographicCamera();
        this.viewport = new FitViewport(virtualWidth, virtualHeight, camera);
        if (Gdx.graphics != null) {
            this.viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        }
        this.shapeRenderer = createShapeRenderer();
        this.initialized = true;
    }

    /**
     * Factory method for creating the ShapeRenderer instance.
     * Can be overridden in unit tests to avoid creating GL resources.
     */
    protected ShapeRenderer createShapeRenderer() {
        return new ShapeRenderer();
    }

    @Override
    public void resize(int width, int height) {
        if (viewport != null && width > 0 && height > 0) {
            if (Gdx.graphics != null) {
                viewport.update(width, height, true);
            } else {
                viewport.setWorldSize(virtualWidth, virtualHeight);
                viewport.setScreenSize(width, height);
            }
        }
    }

    @Override
    public void render(float deltaTime) {
        if (!initialized) {
            return;
        }

        // Clear screen with clearColor
        if (Gdx.gl != null) {
            Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        }

        if (shapeRenderer != null && camera != null) {
            camera.update();
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(primitiveColor);

            // Draw centered primitive rectangle (e.g. 200x200)
            float rectWidth = 200f;
            float rectHeight = 200f;
            float x = (virtualWidth - rectWidth) / 2f;
            float y = (virtualHeight - rectHeight) / 2f;
            shapeRenderer.rect(x, y, rectWidth, rectHeight);

            shapeRenderer.end();
        }
    }

    @Override
    public void dispose() {
        if (!initialized) {
            return;
        }
        if (shapeRenderer != null) {
            try {
                shapeRenderer.dispose();
            } finally {
                shapeRenderer = null;
            }
        }
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Color getClearColor() {
        return clearColor;
    }

    public void setClearColor(Color clearColor) {
        this.clearColor = Objects.requireNonNull(clearColor, "clearColor cannot be null");
    }

    public Color getPrimitiveColor() {
        return primitiveColor;
    }

    public void setPrimitiveColor(Color primitiveColor) {
        this.primitiveColor = Objects.requireNonNull(primitiveColor, "primitiveColor cannot be null");
    }

    public Viewport getViewport() {
        return viewport;
    }

    public OrthographicCamera getCamera() {
        return camera;
    }

    public float getVirtualWidth() {
        return virtualWidth;
    }

    public float getVirtualHeight() {
        return virtualHeight;
    }
}
