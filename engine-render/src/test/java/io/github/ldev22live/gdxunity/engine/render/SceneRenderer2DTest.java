package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.ldev22live.gdxunity.engine.render.camera.OrthographicCamera2D;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SceneRenderer2DTest {

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    static class HeadlessSceneRenderer2D extends SceneRenderer2D {
        public HeadlessSceneRenderer2D(OrthographicCamera2D camera) {
            super(camera);
        }

        @Override
        protected ShapeRenderer createShapeRenderer() {
            // Null or mock in pure headless tests without native OpenGL bindings
            return null;
        }

        @Override
        protected SpriteBatch createSpriteBatch() {
            return null;
        }
    }

    @Test
    void shouldInitializeResizeAndDisposeSafely() {
        OrthographicCamera2D camera = new OrthographicCamera2D(640, 480);
        HeadlessSceneRenderer2D renderer = new HeadlessSceneRenderer2D(camera);

        assertFalse(renderer.isInitialized());
        assertFalse(renderer.isDisposed());

        renderer.initialize();
        assertTrue(renderer.isInitialized());

        Scene scene = new Scene("TestScene");
        Entity entity = scene.createEntity("TestBox");
        entity.addComponent(new ShapeRendererComponent(100f, 100f, Color.RED));
        renderer.setActiveScene(scene);

        // Resize and render calls should execute safely without GL context
        assertDoesNotThrow(() -> renderer.resize(1280, 720));
        assertDoesNotThrow(() -> renderer.render(0.016f));

        // Dispose
        renderer.dispose();
        assertTrue(renderer.isDisposed());
        assertFalse(renderer.isInitialized());

        // Repeated dispose is safe (idempotent)
        assertDoesNotThrow(renderer::dispose);
    }
}
