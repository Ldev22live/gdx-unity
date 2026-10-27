package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PrimitiveRendererTest {

    static class HeadlessPrimitiveRenderer extends PrimitiveRenderer {
        private final AtomicBoolean shapeRendererDisposed = new AtomicBoolean(false);

        public HeadlessPrimitiveRenderer() {
            super(800, 600);
        }

        @Override
        protected ShapeRenderer createShapeRenderer() {
            // Return null or a non-GL dummy in pure unit test environment
            return null;
        }

        @Override
        public void dispose() {
            super.dispose();
            shapeRendererDisposed.set(true);
        }

        public boolean isShapeRendererDisposed() {
            return shapeRendererDisposed.get();
        }
    }

    @Test
    void shouldConfigureVirtualResolutionAndColorsCorrectly() {
        PrimitiveRenderer renderer = new PrimitiveRenderer(1280, 720);

        assertEquals(1280f, renderer.getVirtualWidth());
        assertEquals(720f, renderer.getVirtualHeight());
        assertNotNull(renderer.getClearColor());
        assertNotNull(renderer.getPrimitiveColor());

        Color newClear = new Color(1, 0, 0, 1);
        renderer.setClearColor(newClear);
        assertEquals(newClear, renderer.getClearColor());

        Color newPrim = new Color(0, 1, 0, 1);
        renderer.setPrimitiveColor(newPrim);
        assertEquals(newPrim, renderer.getPrimitiveColor());
    }

    @Test
    void shouldInitializeAndDisposeWithoutRequiringGlContextInSubclass() {
        HeadlessPrimitiveRenderer renderer = new HeadlessPrimitiveRenderer();
        assertFalse(renderer.isInitialized());

        renderer.initialize();
        assertTrue(renderer.isInitialized());
        assertNotNull(renderer.getCamera());
        assertNotNull(renderer.getViewport());

        // Resize
        assertDoesNotThrow(() -> renderer.resize(1024, 768));

        // Render shouldn't throw even without shape renderer
        assertDoesNotThrow(() -> renderer.render(0.016f));

        // Dispose
        renderer.dispose();
        assertFalse(renderer.isInitialized());
        assertTrue(renderer.isShapeRendererDisposed());

        // Repeated dispose is safe
        assertDoesNotThrow(renderer::dispose);
    }
}
