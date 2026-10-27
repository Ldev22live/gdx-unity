package io.github.ldev22live.gdxunity.editor.core.gizmo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.badlogic.gdx.math.Vector2;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent;

import static org.junit.jupiter.api.Assertions.*;

class ColliderGizmoControllerTest {

    private EditorState editorState;
    private Entity entity;
    private ColliderGizmoController controller;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        editorState.newScene("Test Scene");
        entity = editorState.createEntity("Collider Entity");
        entity.getTransform().setLocalPosition(100f, 100f, 0f);
        editorState.setSelectedEntity(entity);
        controller = new ColliderGizmoController(editorState);
    }

    @Test
    void testBoxColliderHitTestAndResizeDrag() {
        BoxCollider2DComponent box = new BoxCollider2DComponent(100f, 80f);
        entity.addComponent(box);

        // Center is at (100, 100). Right handle is at (100 + 50 = 150, 100)
        Vector2 clickNearRight = new Vector2(151f, 100f);
        ColliderGizmoController.HitResult hit = controller.hitTest(clickNearRight, 1.0f);
        assertEquals(ColliderGizmoHandle.BOX_RIGHT, hit.handle);
        assertSame(box, hit.collider);

        // Start drag
        assertTrue(controller.startDrag(hit, clickNearRight));
        assertTrue(controller.isDragging());

        // Drag 20 units right (delta = 20) -> new width = 100 + 40 = 140
        controller.updateDrag(new Vector2(171f, 100f));
        assertEquals(140f, box.getWidth(), 0.01f);

        // End drag commits to command history
        controller.endDrag();
        assertFalse(controller.isDragging());
        assertEquals(140f, box.getWidth(), 0.01f);

        // Undo restores initial width 100
        assertTrue(editorState.getCommandHistory().canUndo());
        editorState.undo();
        assertEquals(100f, box.getWidth(), 0.01f);

        // Redo restores 140
        editorState.redo();
        assertEquals(140f, box.getWidth(), 0.01f);
    }

    @Test
    void testCircleColliderHitTestAndResizeDrag() {
        CircleCollider2DComponent circle = new CircleCollider2DComponent(50f);
        entity.addComponent(circle);

        // Center is at (100, 100). Radius handle is at (100 + 50 = 150, 100)
        Vector2 clickNearCircumference = new Vector2(150f, 101f);
        ColliderGizmoController.HitResult hit = controller.hitTest(clickNearCircumference, 1.0f);
        assertEquals(ColliderGizmoHandle.CIRCLE_RADIUS, hit.handle);
        assertSame(circle, hit.collider);

        assertTrue(controller.startDrag(hit, clickNearCircumference));
        controller.updateDrag(new Vector2(180f, 101f)); // +30 units
        assertEquals(80f, circle.getRadius(), 0.01f);

        controller.endDrag();
        assertEquals(80f, circle.getRadius(), 0.01f);

        // Undo
        editorState.undo();
        assertEquals(50f, circle.getRadius(), 0.01f);
    }

    @Test
    void testHitTestWithDifferentZoomScales() {
        BoxCollider2DComponent box = new BoxCollider2DComponent(100f, 80f);
        entity.addComponent(box);

        // At zoom 2.0f, hit radius is larger (HANDLE_RADIUS * 2.0 = 12px)
        Vector2 clickZoomed = new Vector2(158f, 100f);
        ColliderGizmoController.HitResult hitZoomed = controller.hitTest(clickZoomed, 2.0f);
        assertEquals(ColliderGizmoHandle.BOX_RIGHT, hitZoomed.handle);

        // At zoom 0.5f, hit radius is smaller (HANDLE_RADIUS * 0.5 = 3px), so 8px away misses
        ColliderGizmoController.HitResult hitSmall = controller.hitTest(clickZoomed, 0.5f);
        assertEquals(ColliderGizmoHandle.NONE, hitSmall.handle);
    }

    @Test
    void testCancelDragRestoresOriginalDimensions() {
        BoxCollider2DComponent box = new BoxCollider2DComponent(100f, 80f);
        entity.addComponent(box);

        int cmdCountBefore = editorState.getCommandHistory().getUndoCount();

        Vector2 clickRight = new Vector2(150f, 100f);
        ColliderGizmoController.HitResult hit = controller.hitTest(clickRight, 1.0f);
        controller.startDrag(hit, clickRight);
        controller.updateDrag(new Vector2(190f, 100f));
        assertEquals(180f, box.getWidth(), 0.01f);

        // Cancel drag without committing any command
        controller.cancelDrag();
        assertFalse(controller.isDragging());
        assertEquals(100f, box.getWidth(), 0.01f);
        assertEquals(cmdCountBefore, editorState.getCommandHistory().getUndoCount(),
            "Cancel drag must not push any new command to history");
    }

    @Test
    void testBoxColliderTopAndBottomHandles() {
        BoxCollider2DComponent box = new BoxCollider2DComponent(100f, 80f);
        entity.addComponent(box);

        // Center is (100, 100). Top handle is at (100, 140)
        Vector2 clickTop = new Vector2(100f, 140f);
        ColliderGizmoController.HitResult hitTop = controller.hitTest(clickTop, 1.0f);
        assertEquals(ColliderGizmoHandle.BOX_TOP, hitTop.handle);

        controller.startDrag(hitTop, clickTop);
        controller.updateDrag(new Vector2(100f, 160f)); // +20 y -> +40 height
        assertEquals(120f, box.getHeight(), 0.01f);
        controller.endDrag();

        assertEquals(120f, box.getHeight(), 0.01f);
        editorState.undo();
        assertEquals(80f, box.getHeight(), 0.01f);
    }
}

