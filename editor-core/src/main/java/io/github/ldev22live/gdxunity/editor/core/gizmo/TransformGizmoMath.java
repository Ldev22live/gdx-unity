package io.github.ldev22live.gdxunity.editor.core.gizmo;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

/**
 * Pure mathematical algorithms for Move, Rotate, and Scale gizmo handle hit-testing and manipulation.
 * Isolated from rendering and windowing contexts for 100% deterministic unit testing.
 */
public final class TransformGizmoMath {

    public static final float DEFAULT_AXIS_LENGTH = 60f;
    public static final float DEFAULT_HANDLE_RADIUS = 10f;
    public static final float DEFAULT_CENTER_RADIUS = 12f;
    public static final float DEFAULT_ROTATION_RADIUS = 50f;
    public static final float ROTATION_RING_TOLERANCE = 12f;
    public static final float MIN_SCALE = 0.001f;

    private TransformGizmoMath() {}

    /**
     * Hit-tests gizmo handles at the given world coordinate.
     *
     * @param toolMode     active tool mode (MOVE, ROTATE, SCALE)
     * @param origin       world-space origin of the selected entity
     * @param clickWorldPos point of world click
     * @param scaleFactor  camera zoom/scaling factor (to keep pixel-relative sizes)
     * @return GizmoHandle hit or GizmoHandle.NONE
     */
    public static GizmoHandle hitTest(ToolMode toolMode, Vector3 origin, Vector2 clickWorldPos, float scaleFactor) {
        if (toolMode == null || origin == null || clickWorldPos == null || toolMode == ToolMode.SELECT) {
            return GizmoHandle.NONE;
        }

        float effectiveScale = (scaleFactor <= 0f) ? 1.0f : scaleFactor;
        float axisLen = DEFAULT_AXIS_LENGTH * effectiveScale;
        float handleRadius = DEFAULT_HANDLE_RADIUS * effectiveScale;
        float centerRadius = DEFAULT_CENTER_RADIUS * effectiveScale;
        float rotRadius = DEFAULT_ROTATION_RADIUS * effectiveScale;
        float rotTol = ROTATION_RING_TOLERANCE * effectiveScale;

        float ox = origin.x;
        float oy = origin.y;
        float cx = clickWorldPos.x;
        float cy = clickWorldPos.y;

        float distToOrigin = Vector2.dst(ox, oy, cx, cy);

        switch (toolMode) {
            case MOVE -> {
                // 1. Center handle (plane translation)
                if (distToOrigin <= centerRadius) {
                    return GizmoHandle.MOVE_CENTER;
                }

                // 2. X Axis (from (ox, oy) to (ox + axisLen, oy))
                if (cx >= ox && cx <= ox + axisLen + handleRadius && Math.abs(cy - oy) <= handleRadius) {
                    return GizmoHandle.MOVE_X;
                }

                // 3. Y Axis (from (ox, oy) to (ox, oy + axisLen))
                if (cy >= oy && cy <= oy + axisLen + handleRadius && Math.abs(cx - ox) <= handleRadius) {
                    return GizmoHandle.MOVE_Y;
                }
            }
            case ROTATE -> {
                // Check distance to circular rotation ring
                if (Math.abs(distToOrigin - rotRadius) <= rotTol) {
                    return GizmoHandle.ROTATE_Z;
                }
            }
            case SCALE -> {
                // 1. Center / Uniform Scale handle
                if (distToOrigin <= centerRadius) {
                    return GizmoHandle.SCALE_UNIFORM;
                }

                // 2. X Axis scale handle (box at end of axis or on axis)
                if (cx >= ox && cx <= ox + axisLen + handleRadius && Math.abs(cy - oy) <= handleRadius) {
                    return GizmoHandle.SCALE_X;
                }

                // 3. Y Axis scale handle
                if (cy >= oy && cy <= oy + axisLen + handleRadius && Math.abs(cx - ox) <= handleRadius) {
                    return GizmoHandle.SCALE_Y;
                }
            }
            default -> {}
        }

        return GizmoHandle.NONE;
    }

    /**
     * Calculates the new local position during a MOVE gizmo drag.
     */
    public static Vector3 calculateMovedLocalPosition(
        TransformSnapshot snapshot,
        GizmoHandle handle,
        Vector2 startWorldPos,
        Vector2 currentWorldPos
    ) {
        if (snapshot == null || handle == null || startWorldPos == null || currentWorldPos == null) {
            return snapshot != null ? snapshot.localPosition() : new Vector3();
        }

        float deltaX = currentWorldPos.x - startWorldPos.x;
        float deltaY = currentWorldPos.y - startWorldPos.y;

        Vector3 targetWorldPos = new Vector3(snapshot.worldPosition());

        switch (handle) {
            case MOVE_X -> targetWorldPos.x += deltaX;
            case MOVE_Y -> targetWorldPos.y += deltaY;
            case MOVE_CENTER -> {
                targetWorldPos.x += deltaX;
                targetWorldPos.y += deltaY;
            }
            default -> {
                return snapshot.localPosition();
            }
        }

        // Convert targetWorldPos to local space based on snapshot parent
        return worldToLocalPosition(targetWorldPos, snapshot);
    }

    /**
     * Calculates the new local rotation (Quaternion) during a ROTATE_Z gizmo drag.
     */
    public static Quaternion calculateRotatedLocalRotation(
        TransformSnapshot snapshot,
        GizmoHandle handle,
        Vector2 startWorldPos,
        Vector2 currentWorldPos
    ) {
        if (snapshot == null || handle != GizmoHandle.ROTATE_Z || startWorldPos == null || currentWorldPos == null) {
            return snapshot != null ? snapshot.localRotation() : new Quaternion();
        }

        float ox = snapshot.worldPosition().x;
        float oy = snapshot.worldPosition().y;

        float startAngle = MathUtils.atan2(startWorldPos.y - oy, startWorldPos.x - ox) * MathUtils.radDeg;
        float currentAngle = MathUtils.atan2(currentWorldPos.y - oy, currentWorldPos.x - ox) * MathUtils.radDeg;

        float deltaAngleDeg = currentAngle - startAngle;

        // Apply delta rotation around Z axis to snapshot world rotation
        Quaternion deltaRot = new Quaternion().setFromAxis(0f, 0f, 1f, deltaAngleDeg);
        Quaternion newWorldRot = new Quaternion(deltaRot).mul(snapshot.worldRotation());

        // Convert newWorldRot into local rotation using parent
        return worldToLocalRotation(newWorldRot, snapshot);
    }

    /**
     * Calculates the new local scale during a SCALE gizmo drag.
     */
    public static Vector3 calculateScaledLocalScale(
        TransformSnapshot snapshot,
        GizmoHandle handle,
        Vector2 startWorldPos,
        Vector2 currentWorldPos,
        float scaleFactor
    ) {
        if (snapshot == null || handle == null || startWorldPos == null || currentWorldPos == null) {
            return snapshot != null ? snapshot.localScale() : new Vector3(1f, 1f, 1f);
        }

        float effectiveScale = (scaleFactor <= 0f) ? 1.0f : scaleFactor;
        float axisLen = DEFAULT_AXIS_LENGTH * effectiveScale;
        if (axisLen == 0f) axisLen = 1f;

        Vector3 newLocalScale = new Vector3(snapshot.localScale());

        float deltaX = currentWorldPos.x - startWorldPos.x;
        float deltaY = currentWorldPos.y - startWorldPos.y;

        switch (handle) {
            case SCALE_X -> {
                float factor = 1.0f + (deltaX / axisLen);
                float targetScaleX = snapshot.localScale().x * factor;
                newLocalScale.x = sanitizeScale(targetScaleX);
            }
            case SCALE_Y -> {
                float factor = 1.0f + (deltaY / axisLen);
                float targetScaleY = snapshot.localScale().y * factor;
                newLocalScale.y = sanitizeScale(targetScaleY);
            }
            case SCALE_UNIFORM -> {
                // Average delta along both axes
                float deltaUniform = (deltaX + deltaY) * 0.5f;
                float factor = 1.0f + (deltaUniform / axisLen);
                newLocalScale.x = sanitizeScale(snapshot.localScale().x * factor);
                newLocalScale.y = sanitizeScale(snapshot.localScale().y * factor);
            }
            default -> {}
        }

        return newLocalScale;
    }

    public static float sanitizeScale(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale)) {
            return 1.0f;
        }
        if (scale < MIN_SCALE) {
            return MIN_SCALE;
        }
        return scale;
    }

    // --- Helper transformations between World and Local space based on snapshot parent ---

    public static Vector3 worldToLocalPosition(Vector3 targetWorldPos, TransformSnapshot snapshot) {
        if (snapshot.parentWorldPosition() == null) {
            return new Vector3(targetWorldPos);
        }

        Vector3 diff = new Vector3(targetWorldPos).sub(snapshot.parentWorldPosition());
        Quaternion invParentRot = new Quaternion(snapshot.parentWorldRotation()).conjugate();
        invParentRot.transform(diff);

        Vector3 pScale = snapshot.parentWorldScale();
        if (pScale.x != 0f) diff.x /= pScale.x;
        if (pScale.y != 0f) diff.y /= pScale.y;
        if (pScale.z != 0f) diff.z /= pScale.z;

        return diff;
    }

    public static Quaternion worldToLocalRotation(Quaternion targetWorldRot, TransformSnapshot snapshot) {
        if (snapshot.parentWorldRotation() == null) {
            return new Quaternion(targetWorldRot);
        }
        return new Quaternion(snapshot.parentWorldRotation()).conjugate().mul(targetWorldRot);
    }
}
