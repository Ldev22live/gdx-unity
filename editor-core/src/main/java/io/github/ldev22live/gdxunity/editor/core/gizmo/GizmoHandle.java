package io.github.ldev22live.gdxunity.editor.core.gizmo;

/**
 * Handle identification for transform gizmos.
 */
public enum GizmoHandle {
    NONE,

    // Move Handles
    MOVE_X,
    MOVE_Y,
    MOVE_Z,
    MOVE_CENTER,
    MOVE_PLANE_XY,
    MOVE_PLANE_XZ,
    MOVE_PLANE_YZ,

    // Rotate Handles
    ROTATE_X,
    ROTATE_Y,
    ROTATE_Z,

    // Scale Handles
    SCALE_X,
    SCALE_Y,
    SCALE_Z,
    SCALE_UNIFORM
}
