package io.github.ldev22live.gdxunity.engine.core;

/**
 * Pluggable rendering hook that is invoked by the engine lifecycle when in RUNNING state.
 * This decouples engine-core from concrete rendering backends (LibGDX, etc.).
 */
public interface EngineRenderer {
    /**
     * Initializes the renderer. Called during engine initialization after GL context is available.
     */
    void initialize();

    /**
     * Called when the viewport or rendering surface is resized.
     *
     * @param width  new width in pixels
     * @param height new height in pixels
     */
    void resize(int width, int height);

    /**
     * Renders a frame using the supplied frame delta time in seconds.
     *
     * @param deltaTime clamped frame delta time in seconds
     */
    void render(float deltaTime);

    /**
     * Releases all rendering-specific resources.
     */
    void dispose();
}
