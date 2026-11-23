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

    /** Renders one scene with a red box and returns the names of the depth related GL calls, in order. */
    private static java.util.List<String> depthCalls(io.github.ldev22live.gdxunity.engine.render.camera.Camera camera, boolean depthComposite, boolean withShape) {
        SceneRenderer2D real = new SceneRenderer2D(camera) {
            @Override
            protected ShapeRenderer createShapeRenderer() {
                return null;
            }

            @Override
            protected SpriteBatch createSpriteBatch() {
                return null;
            }
        };
        real.initialize();
        real.setDepthComposite(depthComposite);
        Scene scene = new Scene("DepthScene");
        if (withShape) {
            scene.createEntity("Box").addComponent(new ShapeRendererComponent(100f, 100f, Color.RED));
        }
        real.setActiveScene(scene);
        java.util.List<String> calls = new java.util.ArrayList<>();
        GL20 previousGl = Gdx.gl;
        Gdx.gl = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[]{GL20.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "glEnable" -> calls.add(args[0].equals(GL20.GL_DEPTH_TEST) ? "enable depth test" : "enable other");
                case "glDisable" -> calls.add(args[0].equals(GL20.GL_DEPTH_TEST) ? "disable depth test" : "disable other");
                case "glDepthMask" -> calls.add("depth mask " + args[0]);
                default -> { }
            }
            return null;
        });
        try {
            real.render(0.016f);
        } finally {
            Gdx.gl = previousGl;
            real.dispose();
        }
        return calls;
    }

    private static io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D perspective() {
        return new io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D() {
            @Override
            public Viewport getViewport() {
                return null;
            }
        };
    }

    private static OrthographicCamera2D orthographic() {
        return new OrthographicCamera2D(640, 480) {
            @Override
            public Viewport getViewport() {
                return null;
            }
        };
    }

    @Test
    void shouldTestSpritesAgainstTheDepthOfThePerspectivePassWithoutWritingDepthAndRestoreTheState() {
        assertEquals(java.util.List.of("enable depth test", "depth mask false", "depth mask true", "disable depth test"),
            depthCalls(perspective(), true, true));
    }

    @Test
    void shouldNotTouchDepthStateWithAnOrthographicCameraWhateverTheZOfTheEntities() {
        assertEquals(java.util.List.of(), depthCalls(orthographic(), true, true));
    }

    @Test
    void shouldNotTouchDepthStateWhenDepthCompositingIsSwitchedOffOrThereIsNothingToDraw() {
        assertEquals(java.util.List.of(), depthCalls(perspective(), false, true));
        assertEquals(java.util.List.of(), depthCalls(perspective(), true, false));
    }
}
