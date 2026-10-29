package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoInteractionController;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.editor.core.input.ViewportCameraController3D;
import io.github.ldev22live.gdxunity.editor.core.picking.ViewportPicker;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SceneViewportInteraction3DTest {

    private static final float PANEL_W = 800f;
    private static final float PANEL_H = 600f;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private EditorState editorState;
    private PerspectiveCamera3D camera;
    private ViewportCameraController3D cameraController;
    private GizmoInteractionController gizmoController;
    private SceneViewportInteraction3D interaction;
    private Entity box;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        camera = new PerspectiveCamera3D(60f, PANEL_W, PANEL_H, 1f, 5000f);
        camera.setPosition(0f, 0f, 500f);
        camera.lookAt(0f, 0f, 0f);
        camera.update();
        cameraController = new ViewportCameraController3D(camera);
        cameraController.setTarget(0f, 0f, 0f);
        gizmoController = new GizmoInteractionController(editorState);
        interaction = new SceneViewportInteraction3D(editorState, camera, cameraController, gizmoController);

        box = editorState.getActiveScene().createEntity("Box");
        box.addComponent(new ShapeRendererComponent(40f, 40f, Color.RED, ShapeRendererComponent.ShapeType.FILLED, true));
    }

    /** Panel-local position of a world point (y up from the panel's bottom). */
    private Vector3 toPanel(float x, float y, float z) {
        return camera.getGdxCamera().project(new Vector3(x, y, z), 0f, 0f, PANEL_W, PANEL_H);
    }

    @Test
    void shouldBuildCenterRayAlongViewDirection() {
        Ray ray = ViewportCoordinates3D.panelToRay(PANEL_W / 2f, PANEL_H / 2f, PANEL_W, PANEL_H, camera.getGdxCamera());
        assertEquals(-1f, ray.direction.z, 0.001f);
        assertEquals(0f, ray.origin.x, 0.01f);
        assertEquals(0f, ray.origin.y, 0.01f);
    }

    @Test
    void shouldPickFlatEntityWithRayAndPreferNearestPlane() {
        Entity front = editorState.getActiveScene().createEntity("Front");
        front.getTransform().setLocalPosition(0f, 0f, 100f);
        front.addComponent(new ShapeRendererComponent(40f, 40f, Color.BLUE, ShapeRendererComponent.ShapeType.FILLED, true));

        Ray center = new Ray(new Vector3(0f, 0f, 500f), new Vector3(0f, 0f, -1f));
        assertEquals(front, ViewportPicker.pickEntity3D(editorState.getActiveScene(), center));

        Ray miss = new Ray(new Vector3(300f, 0f, 500f), new Vector3(0f, 0f, -1f));
        assertNull(ViewportPicker.pickEntity3D(editorState.getActiveScene(), miss));
    }

    @Test
    void shouldSelectEntityUnderPointer() {
        Vector3 p = toPanel(0f, 0f, 0f);
        assertTrue(interaction.touchDown(p.x, p.y, PANEL_W, PANEL_H, Input.Buttons.LEFT, 0));
        assertEquals(box, editorState.getSelectedEntity());

        interaction.touchUp(Input.Buttons.LEFT);
        Vector3 empty = toPanel(250f, 150f, 0f);
        interaction.touchDown(empty.x, empty.y, PANEL_W, PANEL_H, Input.Buttons.LEFT, 0);
        assertNull(editorState.getSelectedEntity());
    }

    @Test
    void shouldGrabGizmoHandleBeforePicking() {
        editorState.setSelectedEntity(box);
        editorState.setToolMode(ToolMode.MOVE);
        // A point on the X axis handle, well outside the 40x40 box
        float handleX = 60f * interaction.gizmoScale();
        Vector3 p = toPanel(handleX, 0f, 0f);

        assertTrue(interaction.touchDown(p.x, p.y, PANEL_W, PANEL_H, Input.Buttons.LEFT, 0));
        assertTrue(gizmoController.isDragging());
        assertEquals(box, editorState.getSelectedEntity());
    }

    @Test
    void shouldFlyForwardWithWWhileRightButtonHeld() {
        assertFalse(interaction.keyDown(Input.Keys.W), "W is a tool shortcut unless flying");

        interaction.touchDown(400f, 300f, PANEL_W, PANEL_H, Input.Buttons.RIGHT, 0);
        assertTrue(interaction.keyDown(Input.Keys.W));
        float zBefore = camera.getPosition().z;
        interaction.update(0.5f);
        assertTrue(camera.getPosition().z < zBefore, "Camera should move toward -Z (forward)");

        interaction.touchUp(Input.Buttons.RIGHT);
        assertFalse(interaction.keyDown(Input.Keys.W), "Releasing the right button ends fly mode");
    }

    @Test
    void shouldChangeFlySpeedWithScrollOnlyWhileFlying() {
        cameraController.setFlyMoveSpeed(400f);
        float zBefore = camera.getPosition().z;

        interaction.touchDown(400f, 300f, PANEL_W, PANEL_H, Input.Buttons.RIGHT, 0);
        interaction.scrolled(-1f);
        assertEquals(400f * SceneViewportInteraction3D.FLY_SPEED_STEP, interaction.getFlySpeed(), 0.01f);
        assertEquals(zBefore, camera.getPosition().z, 0.001f, "Scrolling while flying must not dolly");

        for (int i = 0; i < 200; i++) {
            interaction.scrolled(-1f);
        }
        assertEquals(SceneViewportInteraction3D.MAX_FLY_SPEED, interaction.getFlySpeed(), 0.01f);

        interaction.touchUp(Input.Buttons.RIGHT);
        interaction.scrolled(-1f);
        assertTrue(camera.getPosition().z < zBefore, "Without flying, scrolling dollies toward the target");
    }

    @Test
    void shouldPlaceDropsOnTheContentPlaneOrAtTheTarget() {
        Vector3 center = interaction.worldPointAt(PANEL_W / 2f, PANEL_H / 2f, PANEL_W, PANEL_H);
        assertEquals(0f, center.x, 0.01f);
        assertEquals(0f, center.y, 0.01f);
        assertEquals(0f, center.z, 0.01f);

        // Looking away from the z = 0 plane, drops fall back to the orbit target
        camera.setPosition(0f, 0f, 500f);
        camera.lookAt(0f, 0f, 1000f);
        camera.update();
        cameraController.setTarget(5f, 6f, 7f);
        assertEquals(new Vector3(5f, 6f, 7f), interaction.worldPointAt(400f, 300f, PANEL_W, PANEL_H));
    }

    @Test
    void shouldOrbitAroundTargetWithAltLeftDrag() {
        float distance = camera.getPosition().dst(cameraController.getTarget());
        assertTrue(interaction.touchDown(400f, 300f, PANEL_W, PANEL_H, Input.Buttons.LEFT, EditorKeyboardDispatcher.MOD_ALT));
        interaction.touchDragged(460f, 300f, PANEL_W, PANEL_H);
        assertNotEquals(0f, camera.getPosition().x, 0.01f);
        assertEquals(distance, camera.getPosition().dst(cameraController.getTarget()), 0.5f);
        assertNull(editorState.getSelectedEntity(), "Orbiting must not pick");
    }

    @Test
    void shouldRouteInputByViewportModeAndKeepGestureTarget() {
        RecordingInteraction view2D = new RecordingInteraction();
        RecordingInteraction view3D = new RecordingInteraction();
        ModeSwitchingViewportInteraction switching = new ModeSwitchingViewportInteraction(editorState, view2D, view3D);

        switching.touchDown(1f, 1f, PANEL_W, PANEL_H, Input.Buttons.LEFT, 0);
        editorState.setViewportMode(ViewportMode.VIEW_3D);
        switching.touchDragged(2f, 2f, PANEL_W, PANEL_H);
        switching.touchUp(Input.Buttons.LEFT);
        assertEquals(List.of("down", "drag", "up"), view2D.events, "A gesture stays with the view that started it");

        switching.touchDown(1f, 1f, PANEL_W, PANEL_H, Input.Buttons.LEFT, 0);
        switching.update(0.1f);
        assertEquals(List.of("down", "update"), view3D.events);
    }

    @Test
    void shouldToggleViewportModeThroughActionAndNotifyListeners() {
        EditorActionRegistry registry = new EditorActionRegistry();
        DefaultEditorActions.registerDefaults(registry, editorState);
        List<ViewportMode> changes = new ArrayList<>();
        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onViewportModeChanged(ViewportMode oldMode, ViewportMode newMode) {
                changes.add(newMode);
            }
        });

        registry.execute(DefaultEditorActions.VIEW_TOGGLE_2D_3D);
        registry.execute(DefaultEditorActions.VIEW_TOGGLE_2D_3D);
        assertEquals(List.of(ViewportMode.VIEW_3D, ViewportMode.VIEW_2D), changes);
    }

    private static class RecordingInteraction implements ViewportInteraction {
        final List<String> events = new ArrayList<>();

        @Override
        public boolean touchDown(float localX, float localY, float panelWidth, float panelHeight, int button, int modifiers) {
            events.add("down");
            return true;
        }

        @Override
        public boolean touchDragged(float localX, float localY, float panelWidth, float panelHeight) {
            events.add("drag");
            return true;
        }

        @Override
        public boolean touchUp(int button) {
            events.add("up");
            return true;
        }

        @Override
        public boolean scrolled(float amountY) {
            events.add("scroll");
            return true;
        }

        @Override
        public void update(float deltaTime) {
            events.add("update");
        }

        @Override
        public Vector3 worldPointAt(float localX, float localY, float panelWidth, float panelHeight) {
            events.add("point");
            return new Vector3();
        }
    }
}
