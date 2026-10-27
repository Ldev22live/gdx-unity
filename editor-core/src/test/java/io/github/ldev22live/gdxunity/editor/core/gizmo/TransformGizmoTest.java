package io.github.ldev22live.gdxunity.editor.core.gizmo;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransformGizmoTest {

    @BeforeAll
    static void initNatives() {
        EditorTestHelper.initNatives();
    }

    private EditorState editorState;
    private GizmoInteractionController controller;
    private Entity testEntity;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        controller = new GizmoInteractionController(editorState);

        Scene scene = editorState.getActiveScene();
        testEntity = scene.createEntity("TestObject");
        testEntity.getTransform().setLocalPosition(100f, 100f, 0f);
        editorState.setSelectedEntity(testEntity);
    }

    @Test
    void shouldHitTestMoveGizmoHandles() {
        editorState.setToolMode(ToolMode.MOVE);
        Vector3 origin = testEntity.getTransform().getWorldPosition();

        // 1. Center handle
        GizmoHandle centerHit = TransformGizmoMath.hitTest(
            ToolMode.MOVE, origin, new Vector2(origin.x, origin.y), 1.0f
        );
        assertEquals(GizmoHandle.MOVE_CENTER, centerHit);

        // 2. X Axis handle
        GizmoHandle xHit = TransformGizmoMath.hitTest(
            ToolMode.MOVE, origin, new Vector2(origin.x + 30f, origin.y), 1.0f
        );
        assertEquals(GizmoHandle.MOVE_X, xHit);

        // 3. Y Axis handle
        GizmoHandle yHit = TransformGizmoMath.hitTest(
            ToolMode.MOVE, origin, new Vector2(origin.x, origin.y + 30f), 1.0f
        );
        assertEquals(GizmoHandle.MOVE_Y, yHit);

        // 4. Miss
        GizmoHandle missHit = TransformGizmoMath.hitTest(
            ToolMode.MOVE, origin, new Vector2(origin.x + 200f, origin.y + 200f), 1.0f
        );
        assertEquals(GizmoHandle.NONE, missHit);
    }

    @Test
    void shouldHitTestRotateAndScaleHandles() {
        Vector3 origin = testEntity.getTransform().getWorldPosition();

        // Rotate ring hit
        GizmoHandle rotHit = TransformGizmoMath.hitTest(
            ToolMode.ROTATE, origin, new Vector2(origin.x + 50f, origin.y), 1.0f
        );
        assertEquals(GizmoHandle.ROTATE_Z, rotHit);

        // Scale handles
        GizmoHandle scaleCenter = TransformGizmoMath.hitTest(
            ToolMode.SCALE, origin, new Vector2(origin.x, origin.y), 1.0f
        );
        assertEquals(GizmoHandle.SCALE_UNIFORM, scaleCenter);

        GizmoHandle scaleX = TransformGizmoMath.hitTest(
            ToolMode.SCALE, origin, new Vector2(origin.x + 30f, origin.y), 1.0f
        );
        assertEquals(GizmoHandle.SCALE_X, scaleX);

        GizmoHandle scaleY = TransformGizmoMath.hitTest(
            ToolMode.SCALE, origin, new Vector2(origin.x, origin.y + 30f), 1.0f
        );
        assertEquals(GizmoHandle.SCALE_Y, scaleY);
    }

    @Test
    void shouldPerformMoveManipulationAlongAxesAndCenter() {
        editorState.setToolMode(ToolMode.MOVE);

        // Move X only
        assertTrue(controller.startDrag(GizmoHandle.MOVE_X, new Vector2(100f, 100f)));
        assertTrue(controller.isDragging());
        controller.updateDrag(new Vector2(150f, 120f), 1.0f); // moved +50 on X, +20 on Y
        controller.endDrag();

        // X moved by 50, Y unchanged
        assertEquals(150f, testEntity.getTransform().getLocalPosition().x, 0.001f);
        assertEquals(100f, testEntity.getTransform().getLocalPosition().y, 0.001f);
        assertEquals(0f, testEntity.getTransform().getLocalPosition().z, 0.001f);

        // Move Center (plane movement)
        assertTrue(controller.startDrag(GizmoHandle.MOVE_CENTER, new Vector2(150f, 100f)));
        controller.updateDrag(new Vector2(160f, 140f), 1.0f); // moved +10 on X, +40 on Y
        controller.endDrag();

        assertEquals(160f, testEntity.getTransform().getLocalPosition().x, 0.001f);
        assertEquals(140f, testEntity.getTransform().getLocalPosition().y, 0.001f);
    }

    @Test
    void shouldPerformMoveManipulationUnderParentTransform() {
        Scene scene = editorState.getActiveScene();
        Entity parent = scene.createEntity("Parent");
        parent.getTransform().setLocalPosition(200f, 100f, 0f);
        testEntity.getTransform().setParent(parent.getTransform());

        // Initial local pos is (100, 100), world pos is (300, 200)
        assertEquals(300f, testEntity.getTransform().getWorldPosition().x, 0.001f);
        assertEquals(200f, testEntity.getTransform().getWorldPosition().y, 0.001f);

        editorState.setToolMode(ToolMode.MOVE);
        controller.startDrag(GizmoHandle.MOVE_X, new Vector2(300f, 200f));
        controller.updateDrag(new Vector2(350f, 200f), 1.0f); // moved +50 in world X
        controller.endDrag();

        // World X is 350, local X is 150
        assertEquals(350f, testEntity.getTransform().getWorldPosition().x, 0.001f);
        assertEquals(150f, testEntity.getTransform().getLocalPosition().x, 0.001f);
        assertEquals(100f, testEntity.getTransform().getLocalPosition().y, 0.001f);
    }

    @Test
    void shouldPerformRotateManipulation() {
        editorState.setToolMode(ToolMode.ROTATE);

        // Drag 90 degrees around Z axis: from (150, 100) to (100, 150)
        controller.startDrag(GizmoHandle.ROTATE_Z, new Vector2(150f, 100f));
        controller.updateDrag(new Vector2(100f, 150f), 1.0f);
        controller.endDrag();

        Quaternion rot = testEntity.getTransform().getLocalRotation();
        float angleDeg = rot.getAngleAround(0f, 0f, 1f);
        assertEquals(90f, angleDeg, 1.0f);
    }

    @Test
    void shouldPerformScaleManipulationAndPreventNegativeZero() {
        editorState.setToolMode(ToolMode.SCALE);

        // Scale X
        controller.startDrag(GizmoHandle.SCALE_X, new Vector2(100f, 100f));
        // Drag right by 60 units (1 axisLen) -> factor is 2.0x
        controller.updateDrag(new Vector2(160f, 100f), 1.0f);
        controller.endDrag();

        assertEquals(2.0f, testEntity.getTransform().getLocalScale().x, 0.01f);
        assertEquals(1.0f, testEntity.getTransform().getLocalScale().y, 0.01f);

        // Extreme negative drag should be sanitized to MIN_SCALE (0.001f)
        controller.startDrag(GizmoHandle.SCALE_X, new Vector2(160f, 100f));
        controller.updateDrag(new Vector2(-500f, 100f), 1.0f);
        controller.endDrag();

        assertTrue(testEntity.getTransform().getLocalScale().x >= TransformGizmoMath.MIN_SCALE);
        assertFalse(Float.isNaN(testEntity.getTransform().getLocalScale().x));
    }

    @Test
    void shouldCancelDragWhenToolModeOrSelectionChanges() {
        editorState.setToolMode(ToolMode.MOVE);
        controller.startDrag(GizmoHandle.MOVE_X, new Vector2(100f, 100f));
        controller.updateDrag(new Vector2(130f, 100f), 1.0f);

        assertTrue(controller.isDragging());
        assertEquals(130f, testEntity.getTransform().getLocalPosition().x);

        // Changing tool mode cancels drag and restores initial snapshot
        editorState.setToolMode(ToolMode.SCALE);
        assertFalse(controller.isDragging());
        assertEquals(100f, testEntity.getTransform().getLocalPosition().x);

        // Changing selection cancels drag
        controller.startDrag(GizmoHandle.SCALE_X, new Vector2(100f, 100f));
        controller.updateDrag(new Vector2(150f, 100f), 1.0f);
        assertTrue(controller.isDragging());

        editorState.clearSelection();
        assertFalse(controller.isDragging());
    }

    @Test
    void testGizmoDragCreatesUndoableCommand() {
        editorState.setToolMode(ToolMode.MOVE);
        int initialUndoCount = editorState.getCommandHistory().getUndoCount();

        controller.startDrag(GizmoHandle.MOVE_X, new Vector2(100f, 100f));
        controller.updateDrag(new Vector2(150f, 100f), 1.0f);
        controller.endDrag();

        assertEquals(150f, testEntity.getTransform().getLocalPosition().x);
        assertEquals(initialUndoCount + 1, editorState.getCommandHistory().getUndoCount());

        // Undo restores pre-drag transform
        assertTrue(editorState.undo());
        assertEquals(100f, testEntity.getTransform().getLocalPosition().x);

        // Redo reapplies post-drag transform
        assertTrue(editorState.redo());
        assertEquals(150f, testEntity.getTransform().getLocalPosition().x);
    }

    @Test
    void testGizmoUnchangedDragDoesNotRecordCommand() {
        editorState.setToolMode(ToolMode.MOVE);
        int initialUndoCount = editorState.getCommandHistory().getUndoCount();

        // Drag start and end at exact same location
        controller.startDrag(GizmoHandle.MOVE_X, new Vector2(100f, 100f));
        controller.updateDrag(new Vector2(100f, 100f), 1.0f);
        controller.endDrag();

        assertEquals(initialUndoCount, editorState.getCommandHistory().getUndoCount());
    }
}
