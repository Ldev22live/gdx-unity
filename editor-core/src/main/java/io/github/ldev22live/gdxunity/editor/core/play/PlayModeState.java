package io.github.ldev22live.gdxunity.editor.core.play;

/**
 * State machine states for Play Mode.
 */
public enum PlayModeState {
    /**
     * Editor owns the authored scene. Edits directly mutate the authored scene.
     */
    STOPPED,

    /**
     * Simulation is running. Viewport renders and updates the isolated runtime scene.
     */
    PLAYING,

    /**
     * Simulation is paused. Runtime scene remains intact and visible, but does not advance simulation time.
     */
    PAUSED
}
