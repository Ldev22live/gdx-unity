package io.github.ldev22live.gdxunity.editor.core.play;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
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

    private final EditorState editorState;
    private final SceneSerializer serializer;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private PlayModeState state = PlayModeState.STOPPED;

    // The untouched authored scene while running in Play/Pause mode
    private Scene authoredScene;
    // The runtime cloned scene being simulated
    private Scene runtimeScene;
    // Runtime 2D physics system (only active during Play mode)
    private io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D physicsSystem;
    // Selection state when entering play mode, used for reconciliation upon stop
    private UUID prePlaySelectedId;

    public PlayModeController(EditorState editorState) {
        this(editorState, editorState != null ? editorState.getSerializer() : new SceneSerializer());
    }

    public PlayModeController(EditorState editorState, SceneSerializer serializer) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer cannot be null");
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

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

        // Clone authored scene via versioned serializer
        Scene clonedRuntime;
        try {
            String json = serializer.toJson(currentAuthored);
            clonedRuntime = serializer.fromJson(json);
            clonedRuntime.setName(currentAuthored.getName() + " (Runtime)");
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

        this.authoredScene = currentAuthored;
        this.runtimeScene = clonedRuntime;
        this.prePlaySelectedId = editorState.getSelectedEntityId();

        // Switch editor active scene to runtime scene
        editorState.setActiveScene(runtimeScene);

        // Reconcile selection to runtime entity if applicable
        if (prePlaySelectedId != null) {
            Entity runtimeSelected = runtimeScene.findEntityById(prePlaySelectedId);
            editorState.setSelectedEntity(runtimeSelected);
        }

        changeState(PlayModeState.PLAYING);
        editorState.setStatusMessage("Play mode started", false);
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
        Scene restoredScene = (authoredScene != null) ? authoredScene : new Scene("Restored Scene");
        this.authoredScene = null;

        // Ensure editor points back to authored scene
        editorState.setActiveScene(restoredScene);

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

        // Advance 2D physics simulation first
        if (physicsSystem != null) {
            physicsSystem.update(deltaTime);
        }

        runtimeScene.update(deltaTime);
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
