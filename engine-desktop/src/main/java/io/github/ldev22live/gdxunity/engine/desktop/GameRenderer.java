package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.core.EngineRenderer;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetService;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceManager;
import io.github.ldev22live.gdxunity.engine.render.HudRenderer;
import io.github.ldev22live.gdxunity.engine.render.ModelAssets;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer2D;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.render.camera.GameCameraRig;
import io.github.ldev22live.gdxunity.engine.render.camera.OrthographicCamera2D;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.io.File;
import java.util.zip.Deflater;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Draws a {@link GameSession} the way the editor's game view does while playing (TD-034, TD-035): advances the
 * session, then draws the 3D pass (models, primitives, lights, shadows, sky light, tone mapping) and the 2D pass
 * through the scene's Camera component, then the on-screen text. No grid, gizmos or debug overlays.
 *
 * <p>When the scene's camera is switched off while playing, the frame is cleared and only the text is drawn.
 */
public final class GameRenderer implements EngineRenderer {
    private final GameSession session;
    private final ResourceManager resources = new ResourceManager();
    private final GameCameraRig cameras = new GameCameraRig();
    private final SceneRenderer3D renderer3D;
    private final SceneRenderer2D renderer2D;
    private final HudRenderer hud;
    private int width;
    private int height;
    private int framesToRender;
    private File screenshotFile;
    private int framesRendered;
    /** Models whose load failure was already reported, so each is reported once. */
    private final Set<ModelComponent> reportedModels = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * @param session     a started session; the renderer owns it and disposes it last
     * @param projectRoot folder that model and sprite paths in the scene are relative to
     */
    public GameRenderer(GameSession session, File projectRoot) {
        this(session, AssetSource.directory(Objects.requireNonNull(projectRoot, "projectRoot cannot be null")));
    }

    /**
     * @param session the played scene; the renderer owns it and disposes it last
     * @param project where model and sprite paths in the scene are read from (the web runtime's bundled files)
     */
    public GameRenderer(GameSession session, AssetSource project) {
        this.session = Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(project, "project cannot be null");
        // Disposed in reverse order: the session goes last, after everything that draws it
        resources.register(session);
        ModelAssets models = resources.register(new ModelAssets(project));
        this.renderer3D = resources.register(new Disposing3D(new SceneRenderer3D())).renderer;
        renderer3D.setModelAssets(models);
        // Same fallback sun as the editor when the scene has no directional light, so unlit scenes look the same
        renderer3D.setDefaultLightEnabled(true);
        renderer3D.setActiveScene(session.getScene());
        this.renderer2D = resources.register(new Disposing2D(
            new SceneRenderer2D(new OrthographicCamera2D(), new AssetService(project)))).renderer;
        // The 3D pass clears the frame; 2D sprites and shapes are drawn on top of it
        renderer2D.setClearEnabled(false);
        renderer2D.setActiveScene(session.getScene());
        this.hud = resources.register(new HudRenderer());
    }

    /** Asks the application to exit after {@code frames} rendered frames; 0 runs until the window is closed. */
    public void setExitAfterFrames(int frames) {
        if (frames < 0) {
            throw new IllegalArgumentException("frames must not be negative: " + frames);
        }
        this.framesToRender = frames;
    }

    /**
     * Saves the last rendered frame of this window as a PNG just before the application exits after
     * {@link #setExitAfterFrames}. Only this game's own frame buffer is read, never the screen, so nothing from another application can
     * end up in the file (TD-054).
     */
    public void setScreenshotFile(File file) {
        this.screenshotFile = file;
    }

    private void saveScreenshot() {
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            File folder = screenshotFile.getAbsoluteFile().getParentFile();
            if (folder != null) {
                folder.mkdirs();
            }
            PixmapIO.writePNG(new FileHandle(screenshotFile), pixmap, Deflater.DEFAULT_COMPRESSION, true);
        } finally {
            pixmap.dispose();
        }
    }

    public int getFramesRendered() {
        return framesRendered;
    }

    public GameSession getSession() {
        return session;
    }

    @Override
    public void initialize() {
        renderer3D.initialize();
        renderer2D.initialize();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            // Minimized window: keep the last size
            return;
        }
        this.width = width;
        this.height = height;
        cameras.resize(width, height);
    }

    @Override
    public void render(float deltaTime) {
        if (width == 0 && Gdx.graphics != null) {
            resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }
        session.update(deltaTime);
        CameraComponent camera = GameCameraRig.findActiveCamera(session.getScene());
        if (camera != null && width > 0) {
            Camera view = cameras.apply(camera);
            renderer3D.setCamera(view);
            renderer3D.render(deltaTime);
            renderer2D.setCamera(view);
            renderer2D.render(deltaTime);
        } else if (Gdx.gl != null) {
            Color clear = renderer3D.getClearColor();
            Gdx.gl.glClearColor(clear.r, clear.g, clear.b, clear.a);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        }
        hud.render(session.getScene(), width, height);
        reportModelsThatFailedToLoad();
        framesRendered++;
        if (framesToRender > 0 && framesRendered == framesToRender && Gdx.app != null) {
            if (screenshotFile != null) {
                saveScreenshot();
            }
            Gdx.app.exit();
        }
    }

    /**
     * The 3D renderer skips a model it cannot load (the editor shows why in the Details panel); the runtime has no
     * panel, so the reason becomes a runtime error, which also fails a smoke test.
     */
    private void reportModelsThatFailedToLoad() {
        List<Entity> entities = session.getScene().getAllEntities();
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            // Indexed loops over the live lists: this runs every frame and must not allocate
            List<Component> components = entity.getAllComponents();
            for (int j = 0; j < components.size(); j++) {
                if (components.get(j) instanceof ModelComponent model && model.getModelInfo().error() != null
                    && reportedModels.add(model)) {
                    session.reportError("Model on " + entity.getName() + ": " + model.getModelInfo().error());
                }
            }
        }
    }

    /** Disposes the renderers, models, text batch and finally the session; failures are collected (TD-008). */
    @Override
    public void dispose() {
        resources.dispose();
    }

    /** Lets the resource manager own a 3D renderer, which is not a LibGDX {@code Disposable}. */
    private record Disposing3D(SceneRenderer3D renderer) implements Disposable {
        @Override
        public void dispose() {
            renderer.dispose();
        }
    }

    /** Lets the resource manager own a 2D renderer, which is not a LibGDX {@code Disposable}. */
    private record Disposing2D(SceneRenderer2D renderer) implements Disposable {
        @Override
        public void dispose() {
            renderer.dispose();
        }
    }
}
