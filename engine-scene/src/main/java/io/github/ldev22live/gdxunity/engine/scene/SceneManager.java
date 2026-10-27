package io.github.ldev22live.gdxunity.engine.scene;

import com.badlogic.gdx.utils.Disposable;

import java.util.Objects;

/**
 * Manages the active {@link Scene} during runtime execution.
 * Coordinates scene switching, lifecycle transitions, updates, and disposal.
 */
public class SceneManager implements Disposable {
    private Scene activeScene;

    public SceneManager() {
    }

    public SceneManager(Scene initialScene) {
        setScene(initialScene);
    }

    public synchronized Scene getActiveScene() {
        return activeScene;
    }

    /**
     * Replaces the currently active scene with a new scene.
     * Deactivates and disposes the outgoing scene if requested.
     *
     * @param newScene       scene to activate
     * @param disposePrevious if true, disposes the outgoing scene
     */
    public synchronized void setScene(Scene newScene, boolean disposePrevious) {
        if (this.activeScene == newScene) {
            return;
        }

        if (this.activeScene != null) {
            this.activeScene.setActive(false);
            if (disposePrevious) {
                this.activeScene.dispose();
            }
        }

        this.activeScene = newScene;
        if (this.activeScene != null) {
            this.activeScene.setActive(true);
        }
    }

    /**
     * Replaces the active scene, disposing the outgoing scene by default.
     */
    public synchronized void setScene(Scene newScene) {
        setScene(newScene, true);
    }

    /**
     * Advances the active scene's update loop using the supplied frame delta time.
     *
     * @param deltaTime frame delta time in seconds
     */
    public synchronized void update(float deltaTime) {
        if (activeScene != null && activeScene.isActive()) {
            activeScene.update(deltaTime);
        }
    }

    @Override
    public synchronized void dispose() {
        if (activeScene != null) {
            try {
                activeScene.dispose();
            } finally {
                activeScene = null;
            }
        }
    }
}
