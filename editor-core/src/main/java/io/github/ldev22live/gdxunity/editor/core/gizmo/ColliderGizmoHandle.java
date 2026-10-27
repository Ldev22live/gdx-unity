package io.github.ldev22live.gdxunity.editor.core.gizmo;

/**
 * Handle types for direct collider resizing in the scene viewport.
 */
public enum ColliderGizmoHandle {
    NONE,

    // Box collider edge handles
    BOX_LEFT,
    BOX_RIGHT,
    BOX_TOP,
    BOX_BOTTOM,

    // Circle collider radius handle
    CIRCLE_RADIUS
}
