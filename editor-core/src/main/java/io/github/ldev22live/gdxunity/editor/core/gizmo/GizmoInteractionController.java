package io.github.ldev22live.gdxunity.editor.core.gizmo;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.Objects;
import java.util.UUID;

/**
 * Controller managing gizmo interaction, drag states, and transform mutation.
 * Cancels cleanly when tools change, selection changes, or drags are interrupted.
 */
public class GizmoInteractionController {

    private final EditorState editorState;

    private boolean isDragging = false;
    private GizmoHandle activeHandle = GizmoHandle.NONE;
    private TransformSnapshot initialSnapshot;
    private final Vector2 dragStartWorldPos = new Vector2();
    private final Vector2 currentWorldPos = new Vector2();

    public GizmoInteractionController(EditorState editorState) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");

        // Cancel dragging when selection changes, tool mode changes, or scene replaces
        this.editorState.addListener(new EditorState.Listener() {
            @Override
            public void onSelectionChanged(UUID previousSelectedId, UUID newSelectedId) {
                cancelDrag();
            }

            @Override
            public void onToolModeChanged(ToolMode oldMode, ToolMode newMode) {
                cancelDrag();
            }

            @Override
            public void onActiveSceneChanged(io.github.ldev22live.gdxunity.engine.scene.Scene oldScene, io.github.ldev22live.gdxunity.engine.scene.Scene newScene) {
                cancelDrag();
            }
        });
    }

    public boolean isDragging() {
        return isDragging;
    }

    public GizmoHandle getActiveHandle() {
        return activeHandle;
    }

    public TransformSnapshot getInitialSnapshot() {
        return initialSnapshot;
    }

    /**
     * Hit-tests gizmo handles for the selected entity.
     *
     * @param clickWorldPos point of click in world space
     * @param scaleFactor   camera zoom factor
     * @return GizmoHandle hit, or GizmoHandle.NONE
     */
    public GizmoHandle hitTest(Vector2 clickWorldPos, float scaleFactor) {
        Entity selected = editorState.getSelectedEntity();
        if (selected == null || editorState.getToolMode() == ToolMode.SELECT) {
            return GizmoHandle.NONE;
        }

        Vector3 origin = selected.getTransform().getWorldPosition();
        return TransformGizmoMath.hitTest(editorState.getToolMode(), origin, clickWorldPos, scaleFactor);
    }

    /**
     * Begins manipulation on the specified handle.
     *
     * @param handle        the handle clicked
     * @param startWorldPos world coordinate where the drag began
     * @return true if drag started, false if invalid
     */
    public boolean startDrag(GizmoHandle handle, Vector2 startWorldPos) {
        if (handle == null || handle == GizmoHandle.NONE || startWorldPos == null) {
            return false;
        }

        Entity selected = editorState.getSelectedEntity();
        if (selected == null) {
            return false;
        }

        // Play Mode check: if play mode is active and we want to prevent or allow manipulation
        if (editorState.isPlayModeActive()) {
            // Keep consistent: allow manipulating runtime scene in Play Mode, but if paused without simulation updates, it's still visible
        }

        this.isDragging = true;
        this.activeHandle = handle;
        this.initialSnapshot = TransformSnapshot.capture(selected.getTransform());
        this.dragStartWorldPos.set(startWorldPos);
        this.currentWorldPos.set(startWorldPos);
        return true;
    }

    /**
     * Updates transform during an active drag.
     *
     * @param newWorldPos updated world pointer position
     * @param scaleFactor camera zoom factor
     */
    public void updateDrag(Vector2 newWorldPos, float scaleFactor) {
        if (!isDragging || initialSnapshot == null || activeHandle == GizmoHandle.NONE || newWorldPos == null) {
            return;
        }

        Entity selected = editorState.getSelectedEntity();
        if (selected == null) {
            cancelDrag();
            return;
        }

        this.currentWorldPos.set(newWorldPos);
        Transform transform = selected.getTransform();
        ToolMode mode = editorState.getToolMode();

        switch (mode) {
            case MOVE -> {
                Vector3 newLocalPos = TransformGizmoMath.calculateMovedLocalPosition(
                    initialSnapshot,
                    activeHandle,
                    dragStartWorldPos,
                    currentWorldPos
                );
                transform.setLocalPosition(newLocalPos);
            }
            case ROTATE -> {
                Quaternion newLocalRot = TransformGizmoMath.calculateRotatedLocalRotation(
                    initialSnapshot,
                    activeHandle,
                    dragStartWorldPos,
                    currentWorldPos
                );
                transform.setLocalRotation(newLocalRot);
            }
            case SCALE -> {
                Vector3 newLocalScale = TransformGizmoMath.calculateScaledLocalScale(
                    initialSnapshot,
                    activeHandle,
                    dragStartWorldPos,
                    currentWorldPos,
                    scaleFactor
                );
                transform.setLocalScale(newLocalScale);
            }
            default -> {}
        }

        editorState.notifyEntityTransformModified(selected);
    }

    /**
     * Successfully completes a drag.
     */
    public void endDrag() {
        if (isDragging) {
            Entity selected = editorState.getSelectedEntity();
            if (selected != null && initialSnapshot != null) {
                TransformSnapshot finalSnapshot = TransformSnapshot.capture(selected.getTransform());
                if (io.github.ldev22live.gdxunity.editor.core.command.ChangeTransformCommand.hasChanged(initialSnapshot, finalSnapshot)) {
                    String toolDesc = editorState.getToolMode().name();
                    editorState.getCommandHistory().execute(new io.github.ldev22live.gdxunity.editor.core.command.ChangeTransformCommand(
                        editorState, selected.getId(), initialSnapshot, finalSnapshot, "Gizmo " + toolDesc
                    ));
                }
            }
            isDragging = false;
            activeHandle = GizmoHandle.NONE;
            initialSnapshot = null;
        }
    }

    /**
     * Cancels an active drag and restores the initial snapshot if currently dragging.
     */
    public void cancelDrag() {
        if (isDragging) {
            if (initialSnapshot != null) {
                Entity selected = editorState.getSelectedEntity();
                if (selected != null) {
                    selected.getTransform().setLocalPosition(initialSnapshot.localPosition());
                    selected.getTransform().setLocalRotation(initialSnapshot.localRotation());
                    selected.getTransform().setLocalScale(initialSnapshot.localScale());
                }
            }
            isDragging = false;
            activeHandle = GizmoHandle.NONE;
            initialSnapshot = null;
        }
    }
}
