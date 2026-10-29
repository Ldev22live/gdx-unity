package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.editor.core.gizmo.ColliderGizmoController;
import io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoInteractionController;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.engine.render.camera.OrthographicCamera2D;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SceneViewportInteraction2DTest {

    private static final float PANEL_W = 800f;
    private static final float PANEL_H = 600f;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private EditorState editorState;
    private OrthographicCamera2D camera;
    private SceneViewportInteraction2D interaction;
    private Entity box;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        camera = OrthographicCamera2D.createScreenFilling(0f, 0f);
        camera.resize((int) PANEL_W, (int) PANEL_H);
        interaction = new SceneViewportInteraction2D(editorState, camera,
            new GizmoInteractionController(editorState), new ColliderGizmoController(editorState));

        box = editorState.getActiveScene().createEntity("Box");
        box.getTransform().setLocalPosition(100f, 50f, 0f);
        box.addComponent(new ShapeRendererComponent(40f, 40f, Color.RED, ShapeRendererComponent.ShapeType.FILLED, true));
    }

    /** Panel-local position of a world point while the camera is at (0, 0) with zoom 1. */
    private static float localX(float worldX) {
        return PANEL_W / 2f + worldX;
    }

    private static float localY(float worldY) {
        return PANEL_H / 2f + worldY;
    }

    @Test
    void shouldConvertPanelCenterToCameraPositionAndScaleByZoom() {
        Vector3 cam = new Vector3(10f, 20f, 0f);
        assertEquals(new Vector2(10f, 20f), ViewportCoordinates2D.panelToWorld(400f, 300f, PANEL_W, PANEL_H, cam, 1f));
        // Bottom-left corner at zoom 2 is (width/2 * 2, height/2 * 2) away from the camera
        assertEquals(new Vector2(10f - 800f, 20f - 600f), ViewportCoordinates2D.panelToWorld(0f, 0f, PANEL_W, PANEL_H, cam, 2f));
        assertThrows(IllegalArgumentException.class, () -> ViewportCoordinates2D.panelToWorld(0f, 0f, 0f, PANEL_H, cam, 1f));
    }

    @Test
    void shouldSelectEntityUnderPointerAndClearOnEmptyClick() {
        assertTrue(interaction.touchDown(localX(100f), localY(50f), PANEL_W, PANEL_H, Input.Buttons.LEFT, 0));
        assertEquals(box, editorState.getSelectedEntity());

        interaction.touchUp(Input.Buttons.LEFT);
        interaction.touchDown(localX(-300f), localY(-200f), PANEL_W, PANEL_H, Input.Buttons.LEFT, 0);
        assertNull(editorState.getSelectedEntity());
    }

    @Test
    void shouldToggleEntityInSelectionWhenAdditive() {
        Entity other = editorState.getActiveScene().createEntity("Other");
        editorState.setSelectedEntity(other);

        interaction.touchDown(localX(100f), localY(50f), PANEL_W, PANEL_H, Input.Buttons.LEFT, EditorKeyboardDispatcher.MOD_CTRL);
        assertTrue(editorState.isSelected(box.getId()));
        assertTrue(editorState.isSelected(other.getId()));

        interaction.touchDown(localX(100f), localY(50f), PANEL_W, PANEL_H, Input.Buttons.LEFT, EditorKeyboardDispatcher.MOD_CTRL);
        assertFalse(editorState.isSelected(box.getId()));
    }

    @Test
    void shouldDragMoveGizmoBeforePickingAndCommitOneUndoStep() {
        editorState.setSelectedEntity(box);
        editorState.setToolMode(ToolMode.MOVE);
        int undoBefore = editorState.getCommandHistory().getUndoCount();

        // 30 units along the X axis handle from the entity origin
        assertTrue(interaction.touchDown(localX(130f), localY(50f), PANEL_W, PANEL_H, Input.Buttons.LEFT, 0));
        interaction.touchDragged(localX(150f), localY(50f), PANEL_W, PANEL_H);
        assertEquals(120f, box.getTransform().getWorldPosition().x, 0.001f);
        assertEquals(50f, box.getTransform().getWorldPosition().y, 0.001f);

        assertTrue(interaction.touchUp(Input.Buttons.LEFT));
        assertEquals(undoBefore + 1, editorState.getCommandHistory().getUndoCount());
        assertEquals(box, editorState.getSelectedEntity(), "Gizmo drag must not change the selection");

        editorState.undo();
        assertEquals(100f, box.getTransform().getWorldPosition().x, 0.001f);
    }

    @Test
    void shouldPanCameraOppositeToRightDrag() {
        assertTrue(interaction.touchDown(400f, 300f, PANEL_W, PANEL_H, Input.Buttons.RIGHT, 0));
        assertTrue(interaction.isPanning());
        interaction.touchDragged(450f, 320f, PANEL_W, PANEL_H);

        assertEquals(-50f, camera.getPosition().x, 0.001f);
        assertEquals(-20f, camera.getPosition().y, 0.001f);

        assertTrue(interaction.touchUp(Input.Buttons.RIGHT));
        assertFalse(interaction.isPanning());
    }

    @Test
    void shouldZoomInStepsAndClampToLimits() {
        interaction.scrolled(1f);
        assertEquals(1f + SceneViewportInteraction2D.ZOOM_STEP, camera.getZoom(), 0.0001f);

        interaction.scrolled(1000f);
        assertEquals(SceneViewportInteraction2D.MAX_ZOOM, camera.getZoom(), 0.0001f);

        interaction.scrolled(-1000f);
        assertEquals(SceneViewportInteraction2D.MIN_ZOOM, camera.getZoom(), 0.0001f);
    }

    @Test
    void shouldPickAtZoomedAndPannedView() {
        camera.setPosition(100f, 50f, 0f);
        camera.setZoom(2f);
        // Panel center now shows the box center
        interaction.touchDown(PANEL_W / 2f, PANEL_H / 2f, PANEL_W, PANEL_H, Input.Buttons.LEFT, 0);
        assertEquals(box, editorState.getSelectedEntity());
    }
}
