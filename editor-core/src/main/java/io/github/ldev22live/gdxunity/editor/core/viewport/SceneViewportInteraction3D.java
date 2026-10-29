package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.collision.Ray;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoHandle;
import io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoInteractionController;
import io.github.ldev22live.gdxunity.editor.core.gizmo.TransformGizmoMath;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.editor.core.input.ViewportCameraController3D;
import io.github.ldev22live.gdxunity.editor.core.input.ViewportCameraController3D.NavigationMode;
import io.github.ldev22live.gdxunity.editor.core.picking.ViewportPicker;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;

/**
 * Pointer and keyboard handling for the 3D scene view, following common 3D editor conventions:
 * right-drag looks around and enables WASD/QE fly movement, Alt + left-drag orbits the target,
 * middle-drag pans, the wheel dollies. Left click hits gizmo handles first (TD-016), then picks entities.
 */
public class SceneViewportInteraction3D implements ViewportInteraction {

    private final EditorState editorState;
    private final PerspectiveCamera3D camera;
    private final ViewportCameraController3D cameraController;
    private final GizmoInteractionController gizmoController;

    private int navigationButton = -1;

    public SceneViewportInteraction3D(EditorState editorState, PerspectiveCamera3D camera,
                                      ViewportCameraController3D cameraController,
                                      GizmoInteractionController gizmoController) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
        this.cameraController = Objects.requireNonNull(cameraController, "cameraController cannot be null");
        this.gizmoController = Objects.requireNonNull(gizmoController, "gizmoController cannot be null");
    }

    @Override
    public boolean touchDown(float localX, float localY, float panelWidth, float panelHeight, int button, int modifiers) {
        NavigationMode navigation = navigationFor(button, modifiers);
        if (navigation != NavigationMode.NONE) {
            navigationButton = button;
            // The camera controller works in screen coordinates (y down)
            cameraController.startNavigation(navigation, localX, panelHeight - localY);
            return true;
        }
        if (button != Input.Buttons.LEFT) {
            return false;
        }

        camera.update();
        Ray ray = ViewportCoordinates3D.panelToRay(localX, localY, panelWidth, panelHeight, camera.getGdxCamera());

        GizmoHandle handle = gizmoController.hitTest3D(ray, gizmoScale());
        if (handle != GizmoHandle.NONE && gizmoController.startDrag3D(handle, ray)) {
            editorState.setStatusMessage("Manipulating: " + handle.name(), false);
            return true;
        }

        Entity picked = ViewportPicker.pickEntity3D(editorState.getActiveScene(), ray);
        if (picked == null) {
            editorState.clearSelection();
        } else if ((modifiers & (EditorKeyboardDispatcher.MOD_CTRL | EditorKeyboardDispatcher.MOD_SHIFT)) != 0) {
            if (editorState.isSelected(picked.getId())) {
                editorState.removeSelectedEntityId(picked.getId());
            } else {
                editorState.addSelectedEntityId(picked.getId());
            }
        } else {
            editorState.setSelectedEntity(picked);
            editorState.setStatusMessage("Picked entity: " + picked.getName(), false);
        }
        return true;
    }

    @Override
    public boolean touchDragged(float localX, float localY, float panelWidth, float panelHeight) {
        if (cameraController.getCurrentMode() != NavigationMode.NONE) {
            return cameraController.processDrag(localX, panelHeight - localY);
        }
        if (gizmoController.isDragging()) {
            camera.update();
            Ray ray = ViewportCoordinates3D.panelToRay(localX, localY, panelWidth, panelHeight, camera.getGdxCamera());
            gizmoController.updateDrag3D(ray, gizmoScale());
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int button) {
        if (button == navigationButton) {
            cameraController.stopNavigation();
            navigationButton = -1;
            return true;
        }
        if (button == Input.Buttons.LEFT && gizmoController.isDragging()) {
            gizmoController.endDrag();
            editorState.setStatusMessage("Manipulation finished", false);
            return true;
        }
        return false;
    }

    @Override
    public boolean scrolled(float amountY) {
        return cameraController.processScroll(amountY);
    }

    @Override
    public boolean keyDown(int keycode) {
        return cameraController.processKeyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        return cameraController.processKeyUp(keycode);
    }

    @Override
    public void update(float deltaTime) {
        cameraController.update(deltaTime);
    }

    /** Gizmo scale for the selected entity at its current distance from the camera. */
    public float gizmoScale() {
        Entity selected = editorState.getSelectedEntity();
        if (selected == null) {
            return 1f;
        }
        return TransformGizmoMath.perspectiveScaleFactor(camera.getPosition(),
            selected.getTransform().getWorldPosition(), camera.getFieldOfView());
    }

    private static NavigationMode navigationFor(int button, int modifiers) {
        if (button == Input.Buttons.RIGHT) {
            return NavigationMode.FLY;
        }
        if (button == Input.Buttons.MIDDLE) {
            return NavigationMode.PAN;
        }
        if (button == Input.Buttons.LEFT && (modifiers & EditorKeyboardDispatcher.MOD_ALT) != 0) {
            return NavigationMode.ORBIT;
        }
        return NavigationMode.NONE;
    }
}
