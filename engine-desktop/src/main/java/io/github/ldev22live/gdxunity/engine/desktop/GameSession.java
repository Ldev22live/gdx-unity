package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.animation.AnimatedProperties;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClipSerializer;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponent;
import io.github.ldev22live.gdxunity.engine.animation.ClipSampler;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceDisposalException;
import io.github.ldev22live.gdxunity.engine.render.camera.GameCameraRig;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.physics.JoltPhysicsBackend3D;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCollisionForwarder;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphContactForwarder;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphHost;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphInstance;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphRuntimeException;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One played scene in the game runtime (TD-035): starts the same systems as the editor's Play button, in the same
 * order, and advances them each frame. Needs no graphics context, so it is tested headless.
 *
 * <ol>
 *   <li>Java/Kotlin scripts: precompiled classes from a {@link ScriptFactory} ({@link GameScripts} on desktop,
 *       a generated {@link ScriptRegistry} on the web)</li>
 *   <li>2D physics: {@link PhysicsSystem2D}, stepped before the scene update</li>
 *   <li>Visual scripts: each Graph Script's {@code .graph} file, with 2D collisions forwarded</li>
 *   <li>Animators: each Animator's {@code .anim} clip</li>
 *   <li>Sound: each Audio Source's effect, played through {@link GameAudio} (TD-043)</li>
 * </ol>
 * 3D physics: {@link PhysicsSystem3D} on Jolt (TD-028), created only for scenes with 3D colliders, stepped after 2D physics.
 *
 * <p>Project files are read through an {@link AssetSource}, never {@link File} directly, so the same session runs in
 * the browser (TD-036).
 */
public final class GameSession implements Disposable {
    /** Longest frame step, as in editor Play: a long stall does not make physics or scripts jump. */
    public static final float MAX_FRAME_DELTA = 0.1f;
    /** Runtime errors kept for the smoke test and the exit report; later ones are only logged. */
    static final int MAX_RECORDED_ERRORS = 100;

    /** Where the session reports script output and errors. */
    public interface Log {
        void info(String message);

        void error(String message);
    }

    /** Writes to the LibGDX application log, or to the console when there is no application. */
    public static final Log CONSOLE = new Log() {
        private static final String TAG = "Game";

        @Override
        public void info(String message) {
            if (Gdx.app != null) {
                Gdx.app.log(TAG, message);
            } else {
                System.out.println("[" + TAG + "] " + message);
            }
        }

        @Override
        public void error(String message) {
            if (Gdx.app != null) {
                Gdx.app.error(TAG, message);
            } else {
                System.err.println("[" + TAG + "] " + message);
            }
        }
    };

    private final AssetSource project;
    private final Scene scene;
    private final ScriptFactory scripts;
    private final Log log;
    private final NodeRegistry nodeRegistry = NodeRegistry.createDefault();
    private final AnimatedProperties animatedProperties = AnimatedProperties.createDefault();
    private final GraphCollisionForwarder collisionForwarder = new GraphCollisionForwarder();
    private final GraphContactForwarder contactForwarder = new GraphContactForwarder();
    private final List<String> runtimeErrors = new ArrayList<>();
    private PhysicsSystem2D physics;
    private PhysicsSystem3D physics3D;
    private GameAudio audio;
    private boolean started;
    private boolean disposed;

    private final GraphHost graphHost = new GraphHost() {
        @Override
        public void log(GraphInstance instance, String message) {
            log.info(label(instance) + ": " + message);
        }

        @Override
        public void error(GraphInstance instance, GraphRuntimeException error) {
            reportError(label(instance) + ", node " + error.getNodeId() + ": " + error.getMessage());
        }
    };

    /**
     * @param projectRoot folder that graph and animation paths in the scene are relative to
     * @param scene       the scene to play; the session owns it and disposes it
     * @param scripts     compiled scripts; the session owns them and closes them
     * @param log         where script output and errors go
     */
    public GameSession(File projectRoot, Scene scene, ScriptFactory scripts, Log log) {
        this(AssetSource.directory(Objects.requireNonNull(projectRoot, "projectRoot cannot be null")), scene, scripts,
            log);
    }

    /**
     * @param project where graph and animation paths in the scene are read from
     * @param scene   the scene to play; the session owns it and disposes it
     * @param scripts creates the scene's scripts; the session owns it and closes it
     * @param log     where script output and errors go
     */
    public GameSession(AssetSource project, Scene scene, ScriptFactory scripts, Log log) {
        this.project = Objects.requireNonNull(project, "project cannot be null");
        this.scene = Objects.requireNonNull(scene, "scene cannot be null");
        this.scripts = Objects.requireNonNull(scripts, "scripts cannot be null");
        this.log = Objects.requireNonNull(log, "log cannot be null");
    }

    /** Reads a scene file with every engine component type registered ({@link GameComponentSerializers}). */
    public static Scene loadScene(File file) throws IOException {
        Objects.requireNonNull(file, "file cannot be null");
        if (!file.isFile()) {
            throw new IOException("Scene not found: " + file);
        }
        return parseScene(Files.readString(file.toPath()), file.toString());
    }

    /**
     * Reads a scene file through an asset source, as the web runtime does.
     *
     * @param project where the scene file is read from
     * @param path    the scene's project-relative path
     * @throws IOException if the file is missing, unreadable or not a valid scene
     */
    public static Scene loadScene(AssetSource project, String path) throws IOException {
        Objects.requireNonNull(project, "project cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
        return parseScene(readText(project, path, "Scene"), path);
    }

    private static Scene parseScene(String json, String name) throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        try {
            return new SceneSerializer(registry).fromJson(json);
        } catch (SceneSerializationException e) {
            throw new IOException("Scene " + name + " could not be read: " + e.getMessage(), e);
        }
    }

    public Scene getScene() {
        return scene;
    }

    /** The scene's 2D physics while started; null before {@link #start()} and after {@link #dispose()}. */
    public PhysicsSystem2D getPhysicsSystem() {
        return physics;
    }

    /** The scene's sound while started; null before {@link #start()}, after {@link #dispose()} and when start failed. */
    /** The scene's 3D physics while started; null for scenes without 3D colliders, and after {@link #dispose()}. */
    public PhysicsSystem3D getPhysicsSystem3D() {
        return physics3D;
    }

    public GameAudio getAudio() {
        return audio;
    }

    public boolean isStarted() {
        return started;
    }

    /** Errors raised while playing (graph node failures, models that cannot load), oldest first, at most {@value #MAX_RECORDED_ERRORS}. */
    public List<String> getRuntimeErrors() {
        return Collections.unmodifiableList(runtimeErrors);
    }

    /**
     * Binds scripts, graphs and animators and creates 2D physics. On failure nothing stays bound and the session
     * cannot be started again; dispose it.
     *
     * @return the reasons the scene cannot be played; empty when it started
     */
    public List<String> start() {
        if (started || disposed) {
            throw new IllegalStateException(disposed ? "GameSession is disposed" : "GameSession already started");
        }
        List<String> problems = new ArrayList<>();
        if (GameCameraRig.findActiveCamera(scene) == null) {
            problems.add("The scene has no enabled Camera component on an active entity, so there is nothing to "
                + "look through. Add one in the editor (Add Component > Game > Camera).");
            return problems;
        }
        problems.addAll(ScriptBinder.bind(scene, scripts));
        if (problems.isEmpty()) {
            physics = new PhysicsSystem2D(scene);
            problems.addAll(startPhysics3D());
        }
        if (problems.isEmpty()) {
            problems.addAll(bindGraphs());
        }
        if (problems.isEmpty()) {
            problems.addAll(bindAnimators());
        }
        if (problems.isEmpty()) {
            problems.addAll(bindAudio());
        }
        if (!problems.isEmpty()) {
            unbindAll();
            return problems;
        }
        physics.addCollisionListener(collisionForwarder);
        if (physics3D != null) {
            physics3D.addContactListener(contactForwarder);
        }
        started = true;
        return problems;
    }

    /** Advances physics, then every component of the scene. Ignored unless started. */
    public void update(float deltaTime) {
        if (!started || disposed) {
            return;
        }
        float step = Float.isFinite(deltaTime) && deltaTime > 0f ? Math.min(deltaTime, MAX_FRAME_DELTA) : 0f;
        physics.update(step);
        if (physics3D != null) {
            physics3D.update(step);
        }
        scene.update(step);
    }

    /** Creates the 3D physics for a scene that has 3D colliders; the native library loads here, never for 2D games. */
    private List<String> startPhysics3D() {
        if (!PhysicsSystem3D.isUsedBy(scene)) {
            return List.of();
        }
        try {
            physics3D = new PhysicsSystem3D(scene, new JoltPhysicsBackend3D());
            return List.of();
        } catch (RuntimeException | LinkageError e) {
            return List.of("3D physics could not start: " + e.getMessage());
        }
    }

    private List<String> bindGraphs() {
        List<String> problems = new ArrayList<>();
        List<GraphScriptComponent> components = new ArrayList<>();
        List<GraphInstance> instances = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            for (GraphScriptComponent component : entity.getComponents(GraphScriptComponent.class)) {
                if (!component.isEnabled()) {
                    continue;
                }
                try {
                    Graph graph = GraphSerializer.fromJson(read(component.getGraphPath(), "Graph"));
                    instances.add(new GraphInstance(graph, nodeRegistry, entity, graphHost));
                    components.add(component);
                } catch (IOException | SceneSerializationException | IllegalArgumentException e) {
                    problems.add(entity.getName() + ": " + component.getGraphPath() + ": " + e.getMessage());
                }
            }
        }
        if (problems.isEmpty()) {
            for (int i = 0; i < components.size(); i++) {
                components.get(i).bind(instances.get(i));
            }
        }
        return problems;
    }

    private List<String> bindAudio() {
        audio = new GameAudio(scene, Gdx.audio != null, GameAudio.assetsOf(project));
        List<String> problems = audio.bind();
        if (problems.isEmpty() && audio.getOutputError() != null) {
            // Not fatal: the game is playable without sound, and a machine without a sound device must still run
            log.info(audio.getOutputError());
        }
        return problems;
    }

    private List<String> bindAnimators() {
        List<String> problems = new ArrayList<>();
        List<AnimatorComponent> animators = new ArrayList<>();
        List<AnimationClip> clips = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            for (AnimatorComponent animator : entity.getComponents(AnimatorComponent.class)) {
                if (!animator.isEnabled()) {
                    continue;
                }
                try {
                    AnimationClip clip = AnimationClipSerializer.fromJson(read(animator.getClipPath(), "Animation"));
                    for (String problem : ClipSampler.problems(clip, entity, animatedProperties)) {
                        log.info("Animation " + clip.getName() + " on " + entity.getName() + ": " + problem);
                    }
                    animators.add(animator);
                    clips.add(clip);
                } catch (IOException | RuntimeException e) {
                    problems.add(entity.getName() + ": " + animator.getClipPath() + ": " + e.getMessage());
                }
            }
        }
        if (problems.isEmpty()) {
            for (int i = 0; i < animators.size(); i++) {
                animators.get(i).bind(clips.get(i), animatedProperties);
            }
        }
        return problems;
    }

    private String read(String path, String kind) throws IOException {
        return readText(project, path, kind);
    }

    private static String readText(AssetSource project, String path, String kind) throws IOException {
        FileHandle file = project.resolve(new AssetReference(path));
        if (!file.exists() || file.isDirectory()) {
            throw new IOException(kind + " not found: " + path);
        }
        try {
            return file.readString("UTF-8");
        } catch (GdxRuntimeException e) {
            throw new IOException(kind + " " + path + " could not be read: " + e.getMessage(), e);
        }
    }

    /** Logs an error and keeps it in {@link #getRuntimeErrors()}; used for graph failures and by the renderer. */
    public void reportError(String message) {
        log.error(message);
        if (runtimeErrors.size() < MAX_RECORDED_ERRORS) {
            runtimeErrors.add(message);
        }
    }

    private static String label(GraphInstance instance) {
        return instance.getGraph().getName() + " on " + instance.getSelf().getName();
    }

    private void unbindAll() {
        for (Entity entity : scene.getAllEntities()) {
            for (GraphScriptComponent component : entity.getComponents(GraphScriptComponent.class)) {
                component.unbind();
            }
            for (AnimatorComponent animator : entity.getComponents(AnimatorComponent.class)) {
                animator.unbind();
            }
        }
        ScriptBinder.unbind(scene);
        if (audio != null) {
            GameAudio owned = audio;
            audio = null;
            owned.dispose();
        }
        if (physics3D != null) {
            PhysicsSystem3D owned3D = physics3D;
            physics3D = null;
            owned3D.dispose();
        }
        if (physics != null) {
            physics.removeCollisionListener(collisionForwarder);
            PhysicsSystem2D owned = physics;
            physics = null;
            owned.dispose();
        }
    }

    /**
     * Unbinds everything, disposes physics and the scene and closes the scripts. Every step runs even if an earlier
     * one fails; failures are reported together afterwards (TD-008). Idempotent.
     *
     * @throws ResourceDisposalException if any step failed
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        started = false;
        List<Throwable> failures = new ArrayList<>();
        run(failures, this::unbindAll);
        run(failures, scene::dispose);
        run(failures, () -> {
            try {
                scripts.close();
            } catch (IOException e) {
                throw new IllegalStateException("Could not close the script class loader", e);
            }
        });
        if (!failures.isEmpty()) {
            ResourceDisposalException error = new ResourceDisposalException(
                "GameSession: " + failures.size() + " disposal step(s) failed");
            failures.forEach(error::addSuppressed);
            throw error;
        }
    }

    private static void run(List<Throwable> failures, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | LinkageError e) {
            failures.add(e);
        }
    }
}
