package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.ldev22live.gdxunity.engine.render.camera.OrthographicCamera2D;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

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

    @Test
    void shouldReapplyCameraViewportEveryFrame() {
        int[] applyCount = {0};
        FitViewport countingViewport = new FitViewport(640, 480) {
            @Override
            public void apply(boolean centerCamera) {
                applyCount[0]++;
            }
        };
        OrthographicCamera2D camera = new OrthographicCamera2D(640, 480) {
            @Override
            public Viewport getViewport() {
                return countingViewport;
            }
        };
        HeadlessSceneRenderer2D renderer = new HeadlessSceneRenderer2D(camera);
        renderer.initialize();
        renderer.setActiveScene(new Scene("ViewportScene"));

        // A no-op GL stands in for a live context so the GL-guarded path runs
        GL20 previousGl = Gdx.gl;
        Gdx.gl = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[]{GL20.class}, (proxy, method, args) -> null);
        try {
            renderer.render(0.016f);
            renderer.render(0.016f);
        } finally {
            Gdx.gl = previousGl;
            renderer.dispose();
        }

        // Another pass (the editor UI stage) changes the GL viewport between frames, so each frame must re-apply
        assertEquals(2, applyCount[0]);
    }

    @Test
    void shouldSkipColorClearWhenClearDisabled() {
        HeadlessSceneRenderer2D renderer = new HeadlessSceneRenderer2D(new OrthographicCamera2D(640, 480) {
            @Override
            public Viewport getViewport() {
                return null;
            }
        });
        renderer.initialize();
        renderer.setActiveScene(new Scene("ClearScene"));

        int[] clearCount = {0};
        GL20 previousGl = Gdx.gl;
        Gdx.gl = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[]{GL20.class}, (proxy, method, args) -> {
            if (method.getName().equals("glClear")) {
                clearCount[0]++;
            }
            return null;
        });
        try {
            renderer.render(0.016f);
            assertEquals(1, clearCount[0]);

            // The editor draws a grid first and clears the frame buffer itself
            renderer.setClearEnabled(false);
            renderer.render(0.016f);
            assertEquals(1, clearCount[0]);
        } finally {
            Gdx.gl = previousGl;
            renderer.dispose();
        }
    }
}
