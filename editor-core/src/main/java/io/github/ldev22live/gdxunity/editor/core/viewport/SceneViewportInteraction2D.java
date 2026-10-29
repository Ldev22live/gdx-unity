package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.gizmo.ColliderGizmoController;
import io.github.ldev22live.gdxunity.editor.core.gizmo.ColliderGizmoHandle;
import io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoHandle;
import io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoInteractionController;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.editor.core.picking.ViewportPicker;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;

/**
 * Pointer interaction for the 2D scene viewport, independent of any UI toolkit.
 *
 * <p>Coordinates are panel-local (origin bottom-left, y up). Left button: collider handles first, then
 * transform gizmo handles (TD-016), then entity picking. Right button drag pans; scrolling zooms.
 */
public class SceneViewportInteraction2D implements ViewportInteraction {

    public static final float MIN_ZOOM = 0.1f;
    public static final float MAX_ZOOM = 10f;
    /** Zoom change per scroll notch. */
    public static final float ZOOM_STEP = 0.1f;

    private final EditorState editorState;
    private final Camera camera;
    private final GizmoInteractionController gizmoController;
    private final ColliderGizmoController colliderController;

    private boolean panning;
    private float lastPanX;
    private float lastPanY;

    public SceneViewportInteraction2D(EditorState editorState, Camera camera,
                                      GizmoInteractionController gizmoController,
                                      ColliderGizmoController colliderController) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
        this.gizmoController = Objects.requireNonNull(gizmoController, "gizmoController cannot be null");
        this.colliderController = Objects.requireNonNull(colliderController, "colliderController cannot be null");
    }

    public boolean isPanning() {
        return panning;
    }

    /**
     * Handles a button press inside the viewport. With Ctrl or Shift held, picking toggles the entity in the
     * selection.
     */
    @Override
    public boolean touchDown(float localX, float localY, float panelWidth, float panelHeight,
                             int button, int modifiers) {
        boolean additiveSelection = (modifiers & (EditorKeyboardDispatcher.MOD_CTRL | EditorKeyboardDispatcher.MOD_SHIFT)) != 0;
        if (button == Input.Buttons.RIGHT) {
            panning = true;
            lastPanX = localX;
            lastPanY = localY;
            return true;
        }
        if (button != Input.Buttons.LEFT) {
            return false;
        }

        Vector2 world = toWorld(localX, localY, panelWidth, panelHeight);
        float zoom = camera.getZoom();

        ColliderGizmoController.HitResult colliderHit = colliderController.hitTest(world, zoom);
        if (colliderHit.handle != ColliderGizmoHandle.NONE) {
            colliderController.startDrag(colliderHit, world);
            editorState.setStatusMessage("Resizing Collider: " + colliderHit.handle.name(), false);
            return true;
        }

        GizmoHandle handle = gizmoController.hitTest(world, zoom);
        if (handle != GizmoHandle.NONE) {
            gizmoController.startDrag(handle, world);
            editorState.setStatusMessage("Manipulating: " + handle.name(), false);
            return true;
        }

        Entity picked = ViewportPicker.pickEntityAt(editorState.getActiveScene(), world.x, world.y);
        if (picked == null) {
            editorState.clearSelection();
        } else if (additiveSelection) {
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

    /** Handles pointer movement while a button is held. */
    @Override
    public boolean touchDragged(float localX, float localY, float panelWidth, float panelHeight) {
        if (panning) {
            float zoom = camera.getZoom();
            camera.translate(-(localX - lastPanX) * zoom, -(localY - lastPanY) * zoom, 0f);
            camera.update();
            lastPanX = localX;
            lastPanY = localY;
            return true;
        }
        if (colliderController.isDragging()) {
            colliderController.updateDrag(toWorld(localX, localY, panelWidth, panelHeight));
            return true;
        }
        if (gizmoController.isDragging()) {
            gizmoController.updateDrag(toWorld(localX, localY, panelWidth, panelHeight), camera.getZoom());
            return true;
        }
        return false;
    }

    /** Handles a button release; completed gizmo drags commit one undoable command (TD-018). */
    @Override
    public boolean touchUp(int button) {
        if (button == Input.Buttons.RIGHT && panning) {
            panning = false;
            return true;
        }
        if (button == Input.Buttons.LEFT && colliderController.isDragging()) {
            colliderController.endDrag();
            editorState.setStatusMessage("Collider resizing finished", false);
            return true;
        }
        if (button == Input.Buttons.LEFT && gizmoController.isDragging()) {
            gizmoController.endDrag();
            editorState.setStatusMessage("Manipulation finished", false);
            return true;
        }
        return false;
    }

    /** Zooms by {@link #ZOOM_STEP} per scroll notch, clamped to [{@link #MIN_ZOOM}, {@link #MAX_ZOOM}]. */
    @Override
    public boolean scrolled(float amountY) {
        float zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, camera.getZoom() + amountY * ZOOM_STEP));
        camera.setZoom(zoom);
        camera.update();
        return true;
    }

    /** The 2D point under the pointer, on the z = 0 plane. */
    @Override
    public Vector3 worldPointAt(float localX, float localY, float panelWidth, float panelHeight) {
        Vector2 world = toWorld(localX, localY, panelWidth, panelHeight);
        return new Vector3(world.x, world.y, 0f);
    }

    private Vector2 toWorld(float localX, float localY, float panelWidth, float panelHeight) {
        return ViewportCoordinates2D.panelToWorld(localX, localY, panelWidth, panelHeight,
            camera.getPosition(), camera.getZoom());
    }
}
