package io.github.ldev22live.gdxunity.editor.core.play;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.core.profile.FrameProfiler;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the Play/Pause/Stop simulation lifecycle and scene isolation.
 *
 * Invariant: Playing and stopping must NEVER mutate or silently overwrite the authored scene.
 */
public class PlayModeController {

    @FunctionalInterface
    public interface Listener {
        void onPlayModeStateChanged(PlayModeState oldState, PlayModeState newState);
    }

    /**
     * Runtime systems that live outside editor-core (for example visual scripting, TD-029) and must start and stop
     * with play. {@link #onPlay} runs after scripts and 2D physics are ready, before the first update.
     */
    public interface Extension {
        /**
         * Called before the authored scene is copied for play, so temporary editor changes to it (such as a Timeline
         * preview pose) can be undone first and are not carried into play.
         */
        default void beforePlay(Scene authoredScene) {
        }

        /** @return problems that prevent playing; empty to allow play */
        List<String> onPlay(Scene runtimeScene, PlayModeController controller);

        /** Called before the runtime scene is disposed, also after a failed {@link #onPlay}. */
        void onStop(Scene runtimeScene);
    }

    private final EditorState editorState;
    private final SceneSerializer serializer;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<Extension> extensions = new CopyOnWriteArrayList<>();
    /** The genre profile step (TD-088), always the first extension. */
    private final ProfilePlayStep profileStep = new ProfilePlayStep();

    private PlayModeState state = PlayModeState.STOPPED;

    // The untouched authored scene while running in Play/Pause mode
    private Scene authoredScene;
    // The runtime cloned scene being simulated
    private Scene runtimeScene;
    // Runtime 2D physics system (only active during Play mode)
    private io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D physicsSystem;
    // Runtime 3D physics (Jolt, TD-028); null for scenes without 3D colliders, which never load the native library
    private io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D physicsSystem3D;
    // Selection state when entering play mode, used for reconciliation upon stop
    private UUID prePlaySelectedId;
    // The Profiler panel's sections for one Play step (roadmap M9.6); profiler is null when nothing profiles play
    private FrameProfiler profiler;
    private int playSection;
    private int physics2DSection;
    private int physics3DSection;
    private int sceneSection;

    public PlayModeController(EditorState editorState) {
        this(editorState, editorState != null ? editorState.getSerializer() : new SceneSerializer());
    }

    public PlayModeController(EditorState editorState, SceneSerializer serializer) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer cannot be null");
        extensions.add(profileStep);
    }

    /** The genre profile step (TD-088): its last problems and message, and the key source tests replace. */
    public ProfilePlayStep getProfileStep() {
        return profileStep;
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void addExtension(Extension extension) {
        extensions.add(Objects.requireNonNull(extension, "extension cannot be null"));
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Times each Play step in {@code profiler} (roadmap M9.6): a top-level section {@value #PROFILE_PLAY} with 2D physics, 3D physics
     * and the scene update below it, and below that one section per component type (each script and graph on its own).
     * {@code null} stops profiling.
     */
    public synchronized void setProfiler(FrameProfiler profiler) {
        this.profiler = profiler;
        if (profiler != null) {
            playSection = profiler.section(PROFILE_PLAY, FrameProfiler.ROOT);
            physics2DSection = profiler.section("2D physics", playSection);
            physics3DSection = profiler.section("3D physics", playSection);
            sceneSection = profiler.section(PROFILE_SCENE_UPDATE, playSection);
        }
        if (runtimeScene != null) {
            runtimeScene.setProfiler(profiler, profiler != null ? sceneSection : FrameProfiler.ROOT);
        }
    }

    /** The Profiler's section for one Play step. */
    public static final String PROFILE_PLAY = "Play";
    /** The Profiler's section for the scene's component updates, under {@link #PROFILE_PLAY}. */
    public static final String PROFILE_SCENE_UPDATE = "Scene update";

    public PlayModeState getState() {
        return state;
    }

    public boolean isStopped() {
        return state == PlayModeState.STOPPED;
    }

    public boolean isPlaying() {
        return state == PlayModeState.PLAYING;
    }

    public boolean isPaused() {
        return state == PlayModeState.PAUSED;
    }

    public Scene getAuthoredScene() {
        return authoredScene;
    }

    public Scene getRuntimeScene() {
        return runtimeScene;
    }

    public io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D getPhysicsSystem() {
        return physicsSystem;
    }

    /** The scene's 3D physics while playing; null when the scene has no 3D colliders or is not playing. */
    public io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D getPhysicsSystem3D() {
        return physicsSystem3D;
    }

    /**
     * Starts simulation from the current authored scene.
     * Validates that all entities and components can be safely serialized/cloned.
     *
     * @return true if started successfully, false if startup failed
     */
    public synchronized boolean play() {
        if (state != PlayModeState.STOPPED) {
            editorState.setStatusMessage("Cannot play: Simulation is already running or paused", true);
            return false;
        }

        Scene currentAuthored = editorState.getActiveScene();
        if (currentAuthored == null) {
            editorState.setStatusMessage("Cannot play: No active scene to simulate", true);
            return false;
        }

        // Validate component serializability before cloning
        List<String> unsupported = validateComponentSerializability(currentAuthored);
        if (!unsupported.isEmpty()) {
            String err = "Cannot play: Scene contains unsupported components: " + String.join(", ", unsupported);
            editorState.setStatusMessage(err, true);
            return false;
        }

        for (Extension extension : extensions) {
            extension.beforePlay(currentAuthored);
        }

        // Clone authored scene via versioned serializer
        Scene clonedRuntime;
        try {
            String json = serializer.toJson(currentAuthored);
            clonedRuntime = serializer.fromJson(json);
            clonedRuntime.setName(currentAuthored.getName() + " (Runtime)");
            // The renderer gives the copy these once Play shows it; the Play step's ground check builds bodies before that (TD-088)
            clonedRuntime.setColliderGeometry(currentAuthored.getColliderGeometry());
            clonedRuntime.setHeightFields(currentAuthored.getHeightFields());
        } catch (Exception e) {
            editorState.setStatusMessage("Cannot play: Failed to clone authored scene: " + e.getMessage(), true);
            return false;
        }

        // Initialize script instances if ScriptManager is present
        if (editorState.getScriptManager() != null) {
            boolean compiled = editorState.getScriptManager().compileAll();
            if (!compiled) {
                editorState.setStatusMessage("Cannot play: Script compilation errors exist", true);
                return false;
            }
            List<String> scriptErrors = editorState.getScriptManager().initializeSceneScripts(clonedRuntime);
            if (!scriptErrors.isEmpty()) {
                editorState.setStatusMessage("Cannot play: " + scriptErrors.get(0), true);
                return false;
            }
        }

        // Initialize 2D physics system on runtime scene
        try {
            this.physicsSystem = new io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D(clonedRuntime);
        } catch (Throwable t) {
            editorState.setStatusMessage("Cannot play: Failed to initialize 2D physics: " + t.getMessage(), true);
            if (editorState.getScriptManager() != null) {
                editorState.getScriptManager().cleanupSceneScripts(clonedRuntime);
            }
            clonedRuntime.dispose();
            return false;
        }

        // 3D physics only for scenes that use it: the native library loads on first use
        if (io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D.isUsedBy(clonedRuntime)) {
            try {
                this.physicsSystem3D = new io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D(clonedRuntime,
                    new io.github.ldev22live.gdxunity.engine.physics.JoltPhysicsBackend3D());
            } catch (Throwable t) {
                editorState.setStatusMessage("Cannot play: Failed to initialize 3D physics: " + t.getMessage(), true);
                physicsSystem.dispose();
                physicsSystem = null;
                if (editorState.getScriptManager() != null) {
                    editorState.getScriptManager().cleanupSceneScripts(clonedRuntime);
                }
                clonedRuntime.dispose();
                return false;
            }
        }

        for (Extension extension : extensions) {
            List<String> problems = extension.onPlay(clonedRuntime, this);
            if (!problems.isEmpty()) {
                editorState.setStatusMessage("Cannot play: " + problems.get(0)
                    + (problems.size() > 1 ? " (+" + (problems.size() - 1) + " more)" : ""), true);
                for (Extension started : extensions) {
                    started.onStop(clonedRuntime);
                }
                physicsSystem.dispose();
                physicsSystem = null;
                if (physicsSystem3D != null) {
                    physicsSystem3D.dispose();
                    physicsSystem3D = null;
                }
                if (editorState.getScriptManager() != null) {
                    editorState.getScriptManager().cleanupSceneScripts(clonedRuntime);
                }
                clonedRuntime.dispose();
                return false;
            }
        }

        this.authoredScene = currentAuthored;
        this.runtimeScene = clonedRuntime;
        if (profiler != null) {
            runtimeScene.setProfiler(profiler, sceneSection);
        }
        this.prePlaySelectedId = editorState.getSelectedEntityId();

        // Switch editor active scene to runtime scene
        editorState.swapSceneForPlay(runtimeScene); // the undo history survives Play (Appendix E3)

        // Reconcile selection to runtime entity if applicable
        if (prePlaySelectedId != null) {
            Entity runtimeSelected = runtimeScene.findEntityById(prePlaySelectedId);
            editorState.setSelectedEntity(runtimeSelected);
        }

        changeState(PlayModeState.PLAYING);
        editorState.setStatusMessage(profileStep.getLastMessage().isEmpty() ? "Play mode started" : profileStep.getLastMessage(), false);
        return true;
    }

    /**
     * Pauses a currently playing simulation.
     */
    public synchronized boolean pause() {
        if (state != PlayModeState.PLAYING) {
            return false;
        }

        changeState(PlayModeState.PAUSED);
        editorState.setStatusMessage("Simulation paused", false);
        return true;
    }

    /**
     * Resumes simulation from paused state.
     */
    public synchronized boolean resume() {
        if (state != PlayModeState.PAUSED) {
            return false;
        }

        changeState(PlayModeState.PLAYING);
        editorState.setStatusMessage("Simulation resumed", false);
        return true;
    }

    /**
     * Stops simulation, disposes the runtime scene, and restores the authored scene.
     * Safe to invoke repeatedly or when already stopped.
     */
    public synchronized void stop() {
        if (state == PlayModeState.STOPPED) {
            return;
        }

        Throwable disposalError = null;

        if (runtimeScene != null) {
            for (Extension extension : extensions) {
                try {
                    extension.onStop(runtimeScene);
                } catch (Throwable t) {
                    if (disposalError == null) disposalError = t;
                }
            }
        }

        // Dispose runtime physics system
        if (physicsSystem != null) {
            try {
                physicsSystem.dispose();
            } catch (Throwable t) {
                if (disposalError == null) disposalError = t;
            } finally {
                physicsSystem = null;
            }
        }

        if (physicsSystem3D != null) {
            try {
                physicsSystem3D.dispose();
            } catch (Throwable t) {
                if (disposalError == null) disposalError = t;
            } finally {
                physicsSystem3D = null;
            }
        }

        // Dispose runtime scene
        if (runtimeScene != null) {
            try {
                if (editorState.getScriptManager() != null) {
                    editorState.getScriptManager().cleanupSceneScripts(runtimeScene);
                }
                runtimeScene.dispose();
            } catch (Throwable t) {
                disposalError = t;
            } finally {
                runtimeScene = null;
            }
        }

        // Restore authored scene
        boolean hadAuthored = authoredScene != null;
        Scene restoredScene = hadAuthored ? authoredScene : new Scene("Restored Scene");
        this.authoredScene = null;

        // Back to the authored scene with its undo history; a stand-in scene has no history to keep
        if (hadAuthored) {
            editorState.swapSceneForPlay(restoredScene);
        } else {
            editorState.setActiveScene(restoredScene);
        }

        // Reconcile selection back to authored entity
        if (prePlaySelectedId != null) {
            Entity authoredSelected = restoredScene.findEntityById(prePlaySelectedId);
            editorState.setSelectedEntity(authoredSelected);
        } else {
            editorState.clearSelection();
        }
        prePlaySelectedId = null;

        changeState(PlayModeState.STOPPED);

        if (disposalError != null) {
            editorState.setStatusMessage("Simulation stopped with disposal warning: " + disposalError.getMessage(), true);
        } else {
            editorState.setStatusMessage("Simulation stopped. Authored scene restored.", false);
        }
    }

    /**
     * Advances simulation if currently in PLAYING state.
     *
     * @param deltaTime frame delta in seconds (sanitized and clamped)
     */
    public synchronized void update(float deltaTime) {
        if (state != PlayModeState.PLAYING || runtimeScene == null) {
            return;
        }

        // Sanitize and clamp delta time
        if (Float.isNaN(deltaTime) || Float.isInfinite(deltaTime) || deltaTime < 0f) {
            deltaTime = 0f;
        }
        if (deltaTime > 0.1f) {
            deltaTime = 0.1f; // clamp to 100ms max to prevent physics or simulation explosion
        }

        FrameProfiler timing = profiler;
        try {
            begin(timing, playSection);
            // Advance 2D physics simulation first, then 3D (the same order as GameSession)
            if (physicsSystem != null) {
                begin(timing, physics2DSection);
                physicsSystem.update(deltaTime);
                end(timing, physics2DSection);
            }
            if (physicsSystem3D != null) {
                begin(timing, physics3DSection);
                physicsSystem3D.update(deltaTime);
                end(timing, physics3DSection);
            }

            begin(timing, sceneSection);
            runtimeScene.update(deltaTime);
            end(timing, sceneSection);
            end(timing, playSection);
        } catch (RuntimeException e) {
            // A scene that is set up wrongly (a joint with no collider, a script that throws) fails on its first step, after Play has
            // started. Left alone the exception ends the editor and loses unsaved work, so Play stops and the reason is shown.
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            stop();
            editorState.setStatusMessage("Play stopped: " + reason, true);
        }
    }

    private static void begin(FrameProfiler timing, int section) {
        if (timing != null) {
            timing.begin(section);
        }
    }

    private static void end(FrameProfiler timing, int section) {
        if (timing != null) {
            timing.end(section);
        }
    }

    /**
     * Validates that every component attached to every entity in the scene has a registered serializer.
     */
    public List<String> validateComponentSerializability(Scene scene) {
        List<String> unsupported = new ArrayList<>();
        if (scene == null) {
            return unsupported;
        }

        for (Entity entity : scene.getAllEntities()) {
            for (Component component : entity.getAllComponents()) {
                ComponentSerializer<?> ser = serializer.getRegistry().getByClass(component.getClass());
                if (ser == null) {
                    String name = component.getClass().getSimpleName();
                    if (!unsupported.contains(name)) {
                        unsupported.add(name);
                    }
                }
            }
        }
        return unsupported;
    }

    private void changeState(PlayModeState newState) {
        if (this.state != newState) {
            PlayModeState old = this.state;
            this.state = newState;
            for (Listener l : listeners) {
                l.onPlayModeStateChanged(old, newState);
            }
        }
    }
}
