package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoHandle;
import io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoInteractionController;
import io.github.ldev22live.gdxunity.editor.core.gizmo.TransformGizmoMath;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.editor.core.picking.ViewportPicker;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;

/**
 * Mouse handling of an axis view (TD-039): the left button manipulates the selected entity's gizmo or selects the
 * entity under the pointer; the middle or right button, or Alt with the left, pans; the wheel zooms about the
 * pointer. There is no fly or orbit, since the view looks straight along an axis.
 */
public final class OrthographicViewInteraction implements ViewportInteraction {
    /** One wheel notch zooms by this factor. */
    static final float ZOOM_STEP = 1.15f;

    private final EditorState editorState;
    private final OrthographicViewCamera view;
    private final GizmoInteractionController gizmoController;

    private boolean panning;
    private int panButton = -1;
    private float lastX;
    private float lastY;
    private float panelWidth = 1f;
    private float panelHeight = 1f;

    /** Creates the interaction for one axis-view pane. */
    public OrthographicViewInteraction(EditorState editorState, OrthographicViewCamera view,
                                       GizmoInteractionController gizmoController) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.view = Objects.requireNonNull(view, "view cannot be null");
        this.gizmoController = Objects.requireNonNull(gizmoController, "gizmoController cannot be null");
    }

    /** The factor that sizes the gizmo handles in this view, for drawing and for hit testing. */
    public float gizmoScale() {
        return TransformGizmoMath.orthographicScaleFactor(view.getVisibleHeight());
    }

    private Ray rayAt(float x, float y, float width, float height) {
        view.update();
        return ViewportCoordinates3D.panelToRay(x, y, width, height, view.getCamera().getGdxCamera());
    }

    @Override
    public boolean touchDown(float x, float y, float width, float height, int button, int modifiers) {
        panelWidth = width;
        panelHeight = height;
        lastX = x;
        lastY = y;
        boolean panGesture = button == Input.Buttons.MIDDLE || button == Input.Buttons.RIGHT
            || (button == Input.Buttons.LEFT && (modifiers & EditorKeyboardDispatcher.MOD_ALT) != 0);
        if (panGesture) {
            panning = true;
            panButton = button;
            return true;
        }
        if (button != Input.Buttons.LEFT) {
            return false;
        }
        Ray ray = rayAt(x, y, width, height);
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
    public boolean touchDragged(float x, float y, float width, float height) {
        panelWidth = width;
        panelHeight = height;
        if (panning) {
            view.pan(x - lastX, y - lastY);
            lastX = x;
            lastY = y;
            return true;
        }
        if (gizmoController.isDragging()) {
            gizmoController.updateDrag3D(rayAt(x, y, width, height), gizmoScale());
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int button) {
        if (panning && button == panButton) {
            panning = false;
            panButton = -1;
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
        // Scrolling up (negative) zooms in
        view.zoomAt((float) Math.pow(ZOOM_STEP, -amountY), lastX, lastY);
        return true;
    }

    @Override
    public Vector3 worldPointAt(float x, float y, float width, float height) {
        return view.worldPointAt(x, y);
    }
}
