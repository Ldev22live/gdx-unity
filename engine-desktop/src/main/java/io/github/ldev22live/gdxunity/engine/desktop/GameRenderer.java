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
        renderer3D.setMaterialLibrary(new io.github.ldev22live.gdxunity.engine.material.MaterialLibrary(project));
        renderer3D.setTerrainLibrary(new io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary(project));
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
        initializeNanos = System.nanoTime();
        renderer3D.initialize();
        renderer2D.initialize();
        if (renderStats) {
            frameTimes = new FrameTimeStats(framesToRender > 0 ? framesToRender : UNBOUNDED_STATS_CAPACITY);
            gpuName = Gdx.gl != null ? Gdx.gl.glGetString(GL20.GL_RENDERER) : "";
        }
        // The session started before the window existed, so before there was an audio backend to open a device on
        session.startAudioOutput();
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

    /** Frames left out of the render statistics: the first frames load files and build every terrain chunk. */
    static final int STATS_WARMUP_FRAMES = 30;
    /** Frame times kept for the percentiles when the game runs until closed rather than for a set number of frames. */
    static final int UNBOUNDED_STATS_CAPACITY = 36_000;
    private boolean renderStats;
    private FrameTimeStats frameTimes;
    private long statsTriangles;
    private long initializeNanos;
    private long firstFrameNanos = -1;
    private long peakHeapBytes;
    private long retainedHeapBytes = -1;
    private String gpuName = "";
    private AudioOutputStats audioStats;

    /**
     * Measures each frame's cost (roadmap M3.8): the time from the start of a frame until the GPU has finished it ({@code glFinish}, so
     * vsync does not hide the cost), and the terrain triangles drawn, after {@value #STATS_WARMUP_FRAMES} warm-up frames. Off by default:
     * waiting for the GPU every frame slows the game. Roadmap M9.5 adds the frame-time percentiles, the Java heap's peak while
     * measuring and what stays after a garbage collection on the last frame, and the sound output's latency and cost.
     * Call before the application starts.
     */
    public void setRenderStats(boolean enabled) {
        this.renderStats = enabled;
    }

    /** Draws far terrain chunks with fewer triangles (on by default); off draws every chunk at full detail (roadmap M3.8). */
    public void setTerrainLod(boolean enabled) {
        renderer3D.setTerrainLod(enabled);
    }

    /** Draws only the objects in view and in the shadow map's area (on by default); off draws every one (roadmap M3.6). */
    public void setObjectCulling(boolean enabled) {
        renderer3D.setObjectCulling(enabled);
    }

    /** The measured frames' average cost in milliseconds, or NaN when none were measured. */
    public double getAverageFrameMillis() {
        return frameTimes == null ? Double.NaN : frameTimes.meanMillis();
    }

    /** The measured frames' average terrain triangles, or 0 when none were measured. */
    public long getAverageTerrainTriangles() {
        return getMeasuredFrames() == 0 ? 0 : statsTriangles / getMeasuredFrames();
    }

    /** Frames that went into the statistics. */
    public int getMeasuredFrames() {
        return frameTimes == null ? 0 : frameTimes.count();
    }

    /** The measured frames' times, or null when statistics are off. */
    public FrameTimeStats getFrameTimes() {
        return frameTimes;
    }

    /**
     * Milliseconds from {@link #initialize()} until the first frame was drawn: creating the renderers plus the first frame, which
     * loads the models, textures and terrain the scene shows. NaN before the first frame.
     */
    public double getFirstFrameMillis() {
        return firstFrameNanos < 0 ? Double.NaN : firstFrameNanos / 1e6;
    }

    /** The largest Java heap use seen at the end of a measured frame, in bytes; 0 when statistics are off. */
    public long getPeakHeapBytes() {
        return peakHeapBytes;
    }

    /** Java heap still in use after a garbage collection on the last frame of a counted run, in bytes; -1 if not measured. */
    public long getRetainedHeapBytes() {
        return retainedHeapBytes;
    }

    /** The OpenGL renderer string (the GPU and driver), or empty when statistics are off. */
    public String getGpuName() {
        return gpuName;
    }

    /** The sound output's latency and cost on the last frame of a counted run, or null when no device was open then. */
    public AudioOutputStats getAudioStats() {
        return audioStats;
    }

    @Override
    public void render(float deltaTime) {
        boolean measure = frameTimes != null && framesRendered >= STATS_WARMUP_FRAMES && Gdx.gl != null;
        long start = measure ? System.nanoTime() : 0L;
        renderFrame(deltaTime);
        if (measure) {
            Gdx.gl.glFinish();
            frameTimes.add(System.nanoTime() - start);
            statsTriangles += renderer3D.getTerrainTriangles();
            Runtime runtime = Runtime.getRuntime();
            peakHeapBytes = Math.max(peakHeapBytes, runtime.totalMemory() - runtime.freeMemory());
        }
        if (framesRendered == 0) {
            firstFrameNanos = System.nanoTime() - initializeNanos;
        }
        framesRendered++;
        if (frameTimes != null && framesToRender > 0 && framesRendered == framesToRender) {
            recordEndOfRun();
        }
        if (framesToRender > 0 && framesRendered == framesToRender && Gdx.app != null) {
            if (screenshotFile != null) {
                saveScreenshot();
            }
            Gdx.app.exit();
        }
    }

    /** The last frame of a counted run: what the sound output did, and the heap that stays once garbage is collected. */
    private void recordEndOfRun() {
        GameAudio audio = session.getAudio();
        audioStats = audio == null ? null : audio.getOutputStats();
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        retainedHeapBytes = runtime.totalMemory() - runtime.freeMemory();
    }

    private void renderFrame(float deltaTime) {
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
