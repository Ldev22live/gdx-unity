package io.github.ldev22live.gdxunity.engine.core;

/**
 * Standard lifecycle contract for engine systems and runtimes.
 */
public interface EngineLifecycle {
    /**
     * Initializes the engine and its owned resources.
     * Transitions from {@link EngineState#NEW} to {@link EngineState#RUNNING}.
     * Must be idempotent or reject repeated calls if already running.
     */
    void initialize();

    /**
     * Resizes the engine's active presentation surface / viewport.
     *
     * @param width  new surface width in pixels, non-negative
     * @param height new surface height in pixels, non-negative
     */
    void resize(int width, int height);

    /**
     * Advances and renders a single frame.
     * Ignored if the engine is not in {@link EngineState#RUNNING}.
     */
    void render();

    /**
     * Pauses the engine update and presentation loop.
     * Transitions from {@link EngineState#RUNNING} to {@link EngineState#PAUSED}.
     */
    void pause();

    /**
     * Resumes the engine from a paused state.
     * Transitions from {@link EngineState#PAUSED} to {@link EngineState#RUNNING}.
     */
    void resume();

    /**
     * Disposes and releases all owned resources safely.
     * Idempotent: safe to invoke multiple times.
     */
    void dispose();
}
