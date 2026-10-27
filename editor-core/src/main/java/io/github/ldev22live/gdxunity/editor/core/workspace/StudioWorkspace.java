package io.github.ldev22live.gdxunity.editor.core.workspace;

/**
 * Fundamental contract representing an independent creative environment (studio)
 * inside GdxUnity. Decoupled from concrete desktop windowing or UI toolkits.
 */
public interface StudioWorkspace {
    /**
     * Unique, stable identifier for this workspace (e.g. "scene-editor", "vector-studio", "audio-studio").
     */
    String getId();

    /**
     * Human-readable display title shown in workspace navigation and window headers.
     */
    String getDisplayName();

    /**
     * Called once when the workspace is registered into the WorkspaceManager.
     */
    default void initialize(WorkspaceContext context) {}

    /**
     * Invoked when this workspace becomes the active, focused workspace.
     */
    default void onActivated() {}

    /**
     * Invoked when navigating away from this workspace to another.
     */
    default void onDeactivated() {}

    /**
     * Per-frame logic update.
     */
    default void update(float deltaTime) {}

    /**
     * Per-frame hardware rendering pass (OpenGL, Viewport, ModelBatch), executed before UI overlays.
     */
    default void render(float deltaTime) {}

    /**
     * Invoked when the main editor window changes dimensions.
     */
    default void resize(int width, int height) {}

    /**
     * Cleanly release all resources owned by this workspace.
     */
    default void dispose() {}
}
