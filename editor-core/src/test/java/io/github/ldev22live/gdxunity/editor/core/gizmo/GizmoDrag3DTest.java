package io.github.ldev22live.gdxunity.editor.core.gizmo;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 3D gizmo drag math and {@link GizmoInteractionController} 3D drags. */
class GizmoDrag3DTest {

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private EditorState editorState;
    private GizmoInteractionController controller;
    private Entity entity;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        controller = new GizmoInteractionController(editorState);
        entity = editorState.getActiveScene().createEntity("Target");
        editorState.setSelectedEntity(entity);
    }

    /** Ray pointing down -Z from (x, y, 100). */
    private static Ray downZ(float x, float y) {
        return new Ray(new Vector3(x, y, 100f), new Vector3(0f, 0f, -1f));
    }

    @Test
    void shouldFindClosestPointOnAxisAndRejectParallelRay() {
        Vector3 onX = TransformGizmoMath.closestPointOnAxis(downZ(30f, 5f), new Vector3(), Vector3.X);
        assertNotNull(onX);
        assertEquals(30f, onX.x, 0.001f);
        assertEquals(0f, onX.y, 0.001f);

        Ray alongX = new Ray(new Vector3(0f, 5f, 0f), new Vector3(1f, 0f, 0f));
        assertNull(TransformGizmoMath.closestPointOnAxis(alongX, new Vector3(), Vector3.X));
    }

    @Test
    void shouldScaleGizmoLinearlyWithCameraDistance() {
        // 90 degree FOV at distance 100 shows 200 units; 15% of that is 30 units = half the 60-unit axis
        float near = TransformGizmoMath.perspectiveScaleFactor(new Vector3(0f, 0f, 100f), new Vector3(), 90f);
        float far = TransformGizmoMath.perspectiveScaleFactor(new Vector3(0f, 0f, 200f), new Vector3(), 90f);
        assertEquals(0.5f, near, 0.001f);
        assertEquals(2f * near, far, 0.001f);
    }

    @Test
    void shouldMeasureSignedAngleAroundNormal() {
        assertEquals(90f, TransformGizmoMath.signedAngleDegrees(Vector3.X, Vector3.Y, Vector3.Z), 0.001f);
        assertEquals(-90f, TransformGizmoMath.signedAngleDegrees(Vector3.Y, Vector3.X, Vector3.Z), 0.001f);
    }

    @Test
    void shouldMoveAlongXAxisAndCommitOneUndoStep() {
        editorState.setToolMode(ToolMode.MOVE);
        int undoBefore = editorState.getCommandHistory().getUndoCount();

        GizmoHandle handle = controller.hitTest3D(downZ(30f, 0f), 1f);
        assertEquals(GizmoHandle.MOVE_X, handle);
        assertTrue(controller.startDrag3D(handle, downZ(30f, 0f)));

        // Off-axis pointer movement only contributes its X component
        controller.updateDrag3D(downZ(50f, 5f), 1f);
        Vector3 position = entity.getTransform().getWorldPosition();
        assertEquals(20f, position.x, 0.001f);
        assertEquals(0f, position.y, 0.001f);

        controller.endDrag();
        assertEquals(undoBefore + 1, editorState.getCommandHistory().getUndoCount());
        editorState.undo();
        assertEquals(0f, entity.getTransform().getWorldPosition().x, 0.001f);
    }

    @Test
    void shouldRotateAroundZByAngleSweptOnRing() {
        editorState.setToolMode(ToolMode.ROTATE);
        assertTrue(controller.startDrag3D(GizmoHandle.ROTATE_Z, downZ(50f, 0f)));
        controller.updateDrag3D(downZ(0f, 50f), 1f);

        Vector3 rotatedX = new Vector3(1f, 0f, 0f).mul(entity.getTransform().getWorldRotation());
        assertEquals(0f, rotatedX.x, 0.001f);
        assertEquals(1f, rotatedX.y, 0.001f);
    }

    @Test
    void shouldScaleAlongAxisByDragDistance() {
        editorState.setToolMode(ToolMode.SCALE);
        assertTrue(controller.startDrag3D(GizmoHandle.SCALE_X, downZ(30f, 0f)));
        // 30 units further along a 60-unit axis = 1.5x
        controller.updateDrag3D(downZ(60f, 0f), 1f);
        assertEquals(1.5f, entity.getTransform().getLocalScale().x, 0.001f);
    }

    @Test
    void shouldRestoreTransformWhenDragIsCancelled() {
        editorState.setToolMode(ToolMode.MOVE);
        assertTrue(controller.startDrag3D(GizmoHandle.MOVE_PLANE_XY, downZ(15f, 15f)));
        controller.updateDrag3D(downZ(40f, 35f), 1f);
        assertEquals(25f, entity.getTransform().getWorldPosition().x, 0.001f);

        controller.cancelDrag();
        assertEquals(0f, entity.getTransform().getWorldPosition().x, 0.001f);
        assertEquals(0f, entity.getTransform().getWorldPosition().y, 0.001f);
    }

    @Test
    void shouldNotStartDragWhenRayIsParallelToHandlePlane() {
        editorState.setToolMode(ToolMode.MOVE);
        Ray alongXY = new Ray(new Vector3(-100f, 10f, 0f), new Vector3(1f, 0f, 0f));
        assertFalse(controller.startDrag3D(GizmoHandle.MOVE_PLANE_XY, alongXY));
        assertFalse(controller.isDragging());
    }
}
