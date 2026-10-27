package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoHandle;
import io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoInteractionController;
import io.github.ldev22live.gdxunity.editor.core.gizmo.TransformGizmoMath;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;

/**
 * Renders Move, Rotate, and Scale transform gizmos on top of the scene in the viewport.
 */
public class TransformGizmoRenderer {

    private final EditorState editorState;
    private final GizmoInteractionController controller;
    private final Camera camera;

    // Handle colors (Unity style: X = Red, Y = Green, Center/Uniform = Cyan/Yellow)
    private static final Color COLOR_X = new Color(0.9f, 0.2f, 0.2f, 1.0f);
    private static final Color COLOR_Y = new Color(0.2f, 0.85f, 0.2f, 1.0f);
    private static final Color COLOR_CENTER = new Color(0.2f, 0.8f, 0.9f, 1.0f);
    private static final Color COLOR_ROTATE = new Color(0.3f, 0.6f, 1.0f, 1.0f);
    private static final Color COLOR_HIGHLIGHT = new Color(1.0f, 0.95f, 0.2f, 1.0f);

    public TransformGizmoRenderer(EditorState editorState, GizmoInteractionController controller, Camera camera) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.controller = Objects.requireNonNull(controller, "controller cannot be null");
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
    }

    /**
     * Renders the active gizmo if an entity is selected and tool mode is MOVE, ROTATE, or SCALE.
     *
     * @param shapeRenderer active ShapeRenderer
     * @param scaleFactor   camera zoom factor to keep pixel-relative sizes
     */
    public void render(ShapeRenderer shapeRenderer, float scaleFactor) {
        if (shapeRenderer == null || editorState == null) {
            return;
        }

        Entity selected = editorState.getSelectedEntity();
        ToolMode toolMode = editorState.getToolMode();
        if (selected == null || toolMode == ToolMode.SELECT) {
            return;
        }

        Vector3 origin = selected.getTransform().getWorldPosition();
        float effectiveScale = (scaleFactor <= 0f) ? 1.0f : scaleFactor;
        float axisLen = TransformGizmoMath.DEFAULT_AXIS_LENGTH * effectiveScale;
        float handleRadius = TransformGizmoMath.DEFAULT_HANDLE_RADIUS * effectiveScale;
        float centerRadius = TransformGizmoMath.DEFAULT_CENTER_RADIUS * effectiveScale;
        float rotRadius = TransformGizmoMath.DEFAULT_ROTATION_RADIUS * effectiveScale;

        GizmoHandle activeHandle = controller.isDragging() ? controller.getActiveHandle() : GizmoHandle.NONE;

        // Save original ShapeRenderer transform matrix
        Matrix4 origMatrix = new Matrix4(shapeRenderer.getTransformMatrix());
        shapeRenderer.setTransformMatrix(new Matrix4()); // Draw directly in camera world space

        shapeRenderer.setProjectionMatrix(camera.getCombinedMatrix());

        switch (toolMode) {
            case MOVE -> renderMoveGizmo(shapeRenderer, origin, axisLen, handleRadius, centerRadius, activeHandle);
            case ROTATE -> renderRotateGizmo(shapeRenderer, origin, rotRadius, activeHandle);
            case SCALE -> renderScaleGizmo(shapeRenderer, origin, axisLen, handleRadius, centerRadius, activeHandle);
            default -> {}
        }

        shapeRenderer.setTransformMatrix(origMatrix);
    }

    private void renderMoveGizmo(
        ShapeRenderer sr,
        Vector3 origin,
        float axisLen,
        float handleRadius,
        float centerRadius,
        GizmoHandle activeHandle
    ) {
        float ox = origin.x;
        float oy = origin.y;

        // 1. Center plane handle (box/circle)
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(activeHandle == GizmoHandle.MOVE_CENTER ? COLOR_HIGHLIGHT : COLOR_CENTER);
        sr.rect(ox - centerRadius, oy - centerRadius, centerRadius * 2f, centerRadius * 2f);
        sr.end();

        // 2. Lines for X and Y axes
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(activeHandle == GizmoHandle.MOVE_X ? COLOR_HIGHLIGHT : COLOR_X);
        sr.line(ox, oy, ox + axisLen, oy);

        sr.setColor(activeHandle == GizmoHandle.MOVE_Y ? COLOR_HIGHLIGHT : COLOR_Y);
        sr.line(ox, oy, ox, oy + axisLen);
        sr.end();

        // 3. Arrow heads / tip cones (triangles)
        sr.begin(ShapeRenderer.ShapeType.Filled);
        // X Arrow
        sr.setColor(activeHandle == GizmoHandle.MOVE_X ? COLOR_HIGHLIGHT : COLOR_X);
        sr.triangle(
            ox + axisLen + handleRadius, oy,
            ox + axisLen, oy + handleRadius * 0.6f,
            ox + axisLen, oy - handleRadius * 0.6f
        );

        // Y Arrow
        sr.setColor(activeHandle == GizmoHandle.MOVE_Y ? COLOR_HIGHLIGHT : COLOR_Y);
        sr.triangle(
            ox, oy + axisLen + handleRadius,
            ox - handleRadius * 0.6f, oy + axisLen,
            ox + handleRadius * 0.6f, oy + axisLen
        );
        sr.end();
    }

    private void renderRotateGizmo(
        ShapeRenderer sr,
        Vector3 origin,
        float rotRadius,
        GizmoHandle activeHandle
    ) {
        float ox = origin.x;
        float oy = origin.y;

        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(activeHandle == GizmoHandle.ROTATE_Z ? COLOR_HIGHLIGHT : COLOR_ROTATE);
        sr.circle(ox, oy, rotRadius, 48);
        sr.end();

        // Small indicator circle at (ox + rotRadius, oy)
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(activeHandle == GizmoHandle.ROTATE_Z ? COLOR_HIGHLIGHT : COLOR_ROTATE);
        sr.circle(ox + rotRadius, oy, 6f);
        sr.end();
    }

    private void renderScaleGizmo(
        ShapeRenderer sr,
        Vector3 origin,
        float axisLen,
        float handleRadius,
        float centerRadius,
        GizmoHandle activeHandle
    ) {
        float ox = origin.x;
        float oy = origin.y;

        // 1. Center uniform scale handle
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(activeHandle == GizmoHandle.SCALE_UNIFORM ? COLOR_HIGHLIGHT : COLOR_CENTER);
        sr.rect(ox - centerRadius, oy - centerRadius, centerRadius * 2f, centerRadius * 2f);
        sr.end();

        // 2. Lines
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(activeHandle == GizmoHandle.SCALE_X ? COLOR_HIGHLIGHT : COLOR_X);
        sr.line(ox, oy, ox + axisLen, oy);

        sr.setColor(activeHandle == GizmoHandle.SCALE_Y ? COLOR_HIGHLIGHT : COLOR_Y);
        sr.line(ox, oy, ox, oy + axisLen);
        sr.end();

        // 3. Scale terminal boxes
        sr.begin(ShapeRenderer.ShapeType.Filled);
        // X Box
        sr.setColor(activeHandle == GizmoHandle.SCALE_X ? COLOR_HIGHLIGHT : COLOR_X);
        sr.rect(ox + axisLen, oy - handleRadius * 0.5f, handleRadius, handleRadius);

        // Y Box
        sr.setColor(activeHandle == GizmoHandle.SCALE_Y ? COLOR_HIGHLIGHT : COLOR_Y);
        sr.rect(ox - handleRadius * 0.5f, oy + axisLen, handleRadius, handleRadius);
        sr.end();
    }
}
