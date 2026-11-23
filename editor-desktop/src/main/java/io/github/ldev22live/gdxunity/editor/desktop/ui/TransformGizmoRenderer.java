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
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;

/**
 * Renders Move, Rotate, and Scale transform gizmos on top of the scene in the viewport.
 *
 * <p>Only handles that the active hit-test can return are drawn. With a {@link PerspectiveCamera3D}
 * the full 3D gizmo is shown (Z axis, X/Y rotation rings and planar move quads, matching
 * {@link TransformGizmoMath#hitTest3D}); with a 2D camera only the X/Y axes, center handle and
 * Z rotation ring are shown, matching {@link TransformGizmoMath#hitTest}.
 */
public class TransformGizmoRenderer {

    private final EditorState editorState;
    private final GizmoInteractionController controller;
    private final Camera camera;
    /** Draw the full three-axis handles even for a non-perspective camera (the axis views, TD-039). */
    private final boolean full3D;

    // Handle colors (Unity style: X = Red, Y = Green, Z = Blue, Center/Uniform = Cyan/Yellow)
    private static final Color COLOR_X = new Color(0.9f, 0.2f, 0.2f, 1.0f);
    private static final Color COLOR_Y = new Color(0.2f, 0.85f, 0.2f, 1.0f);
    private static final Color COLOR_Z = new Color(0.2f, 0.4f, 0.95f, 1.0f);
    private static final Color COLOR_CENTER = new Color(0.2f, 0.8f, 0.9f, 1.0f);
    private static final Color COLOR_HIGHLIGHT = new Color(1.0f, 0.95f, 0.2f, 1.0f);
    private static final Color COLOR_PLANE_XY = new Color(0.9f, 0.9f, 0.2f, 0.4f);
    private static final Color COLOR_PLANE_XZ = new Color(0.9f, 0.2f, 0.9f, 0.4f);
    private static final Color COLOR_PLANE_YZ = new Color(0.2f, 0.9f, 0.9f, 0.4f);

    public TransformGizmoRenderer(EditorState editorState, GizmoInteractionController controller, Camera camera) {
        this(editorState, controller, camera, false);
    }

    /**
     * @param full3D draw the three-axis handles also when the camera is orthographic, as in an axis view; the size is
     *               then the {@code scaleFactor} given to {@link #render}
     */
    public TransformGizmoRenderer(EditorState editorState, GizmoInteractionController controller, Camera camera,
                                  boolean full3D) {
        this.full3D = full3D;
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
        // Perspective gizmos keep a constant on-screen size; the hit-test in SceneViewportInteraction3D uses the same factor
        float effectiveScale = (camera instanceof PerspectiveCamera3D perspectiveCamera)
            ? TransformGizmoMath.perspectiveScaleFactor(camera.getPosition(), origin, perspectiveCamera.getFieldOfView())
            : (scaleFactor <= 0f) ? 1.0f : scaleFactor;
        float axisLen = TransformGizmoMath.DEFAULT_AXIS_LENGTH * effectiveScale;
        float handleRadius = TransformGizmoMath.DEFAULT_HANDLE_RADIUS * effectiveScale;
        float centerRadius = TransformGizmoMath.DEFAULT_CENTER_RADIUS * effectiveScale;
        float rotRadius = TransformGizmoMath.DEFAULT_ROTATION_RADIUS * effectiveScale;

        GizmoHandle activeHandle = controller.isDragging() ? controller.getActiveHandle() : GizmoHandle.NONE;
        boolean perspective = camera instanceof PerspectiveCamera3D || full3D;

        // Save original ShapeRenderer transform matrix
        Matrix4 origMatrix = new Matrix4(shapeRenderer.getTransformMatrix());
        shapeRenderer.setTransformMatrix(new Matrix4()); // Draw directly in camera world space

        shapeRenderer.setProjectionMatrix(camera.getCombinedMatrix());

        switch (toolMode) {
            case MOVE -> renderMoveGizmo(shapeRenderer, origin, axisLen, handleRadius, centerRadius, activeHandle, perspective);
            case ROTATE -> renderRotateGizmo(shapeRenderer, origin, rotRadius, activeHandle, perspective);
            case SCALE -> renderScaleGizmo(shapeRenderer, origin, axisLen, handleRadius, centerRadius, activeHandle, perspective);
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
        GizmoHandle activeHandle,
        boolean perspective
    ) {
        float ox = origin.x;
        float oy = origin.y;
        float oz = origin.z;

        // 1. Center plane handle (box)
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(activeHandle == GizmoHandle.MOVE_CENTER ? COLOR_HIGHLIGHT : COLOR_CENTER);
        sr.box(ox - centerRadius * 0.5f, oy - centerRadius * 0.5f, oz + centerRadius * 0.5f, centerRadius, centerRadius, centerRadius);

        // Planar drag quads (XY, XZ, YZ); only hit-testable in a perspective viewport
        if (perspective) {
            float planeSize = axisLen * TransformGizmoMath.PLANE_HANDLE_SIZE_RATIO;
            sr.setColor(activeHandle == GizmoHandle.MOVE_PLANE_XY ? COLOR_HIGHLIGHT : COLOR_PLANE_XY);
            sr.rect(ox, oy, planeSize, planeSize);

            sr.setColor(activeHandle == GizmoHandle.MOVE_PLANE_XZ ? COLOR_HIGHLIGHT : COLOR_PLANE_XZ);
            sr.box(ox, oy, oz + planeSize, planeSize, 0f, planeSize);

            sr.setColor(activeHandle == GizmoHandle.MOVE_PLANE_YZ ? COLOR_HIGHLIGHT : COLOR_PLANE_YZ);
            sr.box(ox, oy, oz + planeSize, 0f, planeSize, planeSize);
        }
        sr.end();

        // 2. Lines for X, Y, and Z axes
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(activeHandle == GizmoHandle.MOVE_X ? COLOR_HIGHLIGHT : COLOR_X);
        sr.line(ox, oy, oz, ox + axisLen, oy, oz);

        sr.setColor(activeHandle == GizmoHandle.MOVE_Y ? COLOR_HIGHLIGHT : COLOR_Y);
        sr.line(ox, oy, oz, ox, oy + axisLen, oz);

        if (perspective) {
            sr.setColor(activeHandle == GizmoHandle.MOVE_Z ? COLOR_HIGHLIGHT : COLOR_Z);
            sr.line(ox, oy, oz, ox, oy, oz + axisLen);
        }
        sr.end();

        // 3. Arrow heads / tip cones
        sr.begin(ShapeRenderer.ShapeType.Filled);
        // X Arrow
        sr.setColor(activeHandle == GizmoHandle.MOVE_X ? COLOR_HIGHLIGHT : COLOR_X);
        sr.box(ox + axisLen, oy - handleRadius * 0.5f, oz + handleRadius * 0.5f, handleRadius, handleRadius, handleRadius);

        // Y Arrow
        sr.setColor(activeHandle == GizmoHandle.MOVE_Y ? COLOR_HIGHLIGHT : COLOR_Y);
        sr.box(ox - handleRadius * 0.5f, oy + axisLen, oz + handleRadius * 0.5f, handleRadius, handleRadius, handleRadius);

        // Z Arrow
        if (perspective) {
            sr.setColor(activeHandle == GizmoHandle.MOVE_Z ? COLOR_HIGHLIGHT : COLOR_Z);
            sr.box(ox - handleRadius * 0.5f, oy - handleRadius * 0.5f, oz + axisLen + handleRadius, handleRadius, handleRadius, handleRadius);
        }
        sr.end();
    }

    private void renderRotateGizmo(
        ShapeRenderer sr,
        Vector3 origin,
        float rotRadius,
        GizmoHandle activeHandle,
        boolean perspective
    ) {
        float ox = origin.x;
        float oy = origin.y;
        float oz = origin.z;

        sr.begin(ShapeRenderer.ShapeType.Line);
        // Z ring (XY plane)
        sr.setColor(activeHandle == GizmoHandle.ROTATE_Z ? COLOR_HIGHLIGHT : COLOR_Z);
        sr.circle(ox, oy, rotRadius, 48);

        if (!perspective) {
            // X and Y rings are edge-on in a 2D view and cannot be hit-tested there
            sr.end();
            return;
        }

        // X ring (YZ plane)
        sr.setColor(activeHandle == GizmoHandle.ROTATE_X ? COLOR_HIGHLIGHT : COLOR_X);
        int segments = 48;
        float angleStep = 360f / segments;
        for (int i = 0; i < segments; i++) {
            float a1 = (float) Math.toRadians(i * angleStep);
            float a2 = (float) Math.toRadians((i + 1) * angleStep);
            sr.line(ox, oy + (float) Math.cos(a1) * rotRadius, oz + (float) Math.sin(a1) * rotRadius,
                    ox, oy + (float) Math.cos(a2) * rotRadius, oz + (float) Math.sin(a2) * rotRadius);
        }

        // Y ring (XZ plane)
        sr.setColor(activeHandle == GizmoHandle.ROTATE_Y ? COLOR_HIGHLIGHT : COLOR_Y);
        for (int i = 0; i < segments; i++) {
            float a1 = (float) Math.toRadians(i * angleStep);
            float a2 = (float) Math.toRadians((i + 1) * angleStep);
            sr.line(ox + (float) Math.cos(a1) * rotRadius, oy, oz + (float) Math.sin(a1) * rotRadius,
                    ox + (float) Math.cos(a2) * rotRadius, oy, oz + (float) Math.sin(a2) * rotRadius);
        }
        sr.end();
    }

    private void renderScaleGizmo(
        ShapeRenderer sr,
        Vector3 origin,
        float axisLen,
        float handleRadius,
        float centerRadius,
        GizmoHandle activeHandle,
        boolean perspective
    ) {
        float ox = origin.x;
        float oy = origin.y;
        float oz = origin.z;

        // 1. Center uniform scale handle
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(activeHandle == GizmoHandle.SCALE_UNIFORM ? COLOR_HIGHLIGHT : COLOR_CENTER);
        sr.box(ox - centerRadius * 0.5f, oy - centerRadius * 0.5f, oz + centerRadius * 0.5f, centerRadius, centerRadius, centerRadius);
        sr.end();

        // 2. Lines
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(activeHandle == GizmoHandle.SCALE_X ? COLOR_HIGHLIGHT : COLOR_X);
        sr.line(ox, oy, oz, ox + axisLen, oy, oz);

        sr.setColor(activeHandle == GizmoHandle.SCALE_Y ? COLOR_HIGHLIGHT : COLOR_Y);
        sr.line(ox, oy, oz, ox, oy + axisLen, oz);

        if (perspective) {
            sr.setColor(activeHandle == GizmoHandle.SCALE_Z ? COLOR_HIGHLIGHT : COLOR_Z);
            sr.line(ox, oy, oz, ox, oy, oz + axisLen);
        }
        sr.end();

        // 3. Scale terminal boxes
        sr.begin(ShapeRenderer.ShapeType.Filled);
        // X Box
        sr.setColor(activeHandle == GizmoHandle.SCALE_X ? COLOR_HIGHLIGHT : COLOR_X);
        sr.box(ox + axisLen, oy - handleRadius * 0.5f, oz + handleRadius * 0.5f, handleRadius, handleRadius, handleRadius);

        // Y Box
        sr.setColor(activeHandle == GizmoHandle.SCALE_Y ? COLOR_HIGHLIGHT : COLOR_Y);
        sr.box(ox - handleRadius * 0.5f, oy + axisLen, oz + handleRadius * 0.5f, handleRadius, handleRadius, handleRadius);

        // Z Box
        if (perspective) {
            sr.setColor(activeHandle == GizmoHandle.SCALE_Z ? COLOR_HIGHLIGHT : COLOR_Z);
            sr.box(ox - handleRadius * 0.5f, oy - handleRadius * 0.5f, oz + axisLen + handleRadius, handleRadius, handleRadius, handleRadius);
        }
        sr.end();
    }
}
