package io.github.ldev22live.gdxunity.engine.core;

/**
 * Listener interface for observing engine lifecycle state transitions.
 */
public interface EngineLifecycleListener {
    /**
     * Invoked when the engine transitions between states.
     *
     * @param oldState previous state
     * @param newState new state
     */
    void onStateChanged(EngineState oldState, EngineState newState);
}
