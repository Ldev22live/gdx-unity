package io.github.ldev22live.gdxunity.editor.core.gizmo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.Collider2DComponent;

import java.util.Objects;
import java.util.UUID;

/**
 * Controller managing collider gizmo hit testing, dragging, and reversible mutation.
 */
public class ColliderGizmoController {

    public static final float HANDLE_RADIUS = 6.0f; // in screen-scaled pixels

    private final EditorState editorState;

    private boolean isDragging = false;
    private ColliderGizmoHandle activeHandle = ColliderGizmoHandle.NONE;
    private Collider2DComponent activeCollider = null;

    // Snapshot of dimensions prior to drag
    private float initialWidth;
    private float initialHeight;
    private float initialRadius;
    private final Vector2 initialOffset = new Vector2();
    private final Vector2 dragStartWorldPos = new Vector2();

    public ColliderGizmoController(EditorState editorState) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");

        // Cancel dragging when selection or active scene changes
        this.editorState.addListener(new EditorState.Listener() {
            @Override
            public void onSelectionChanged(UUID previousSelectedId, UUID newSelectedId) {
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

    public ColliderGizmoHandle getActiveHandle() {
        return activeHandle;
    }

    public Collider2DComponent getActiveCollider() {
        return activeCollider;
    }

    public static class HitResult {
        public final ColliderGizmoHandle handle;
        public final Collider2DComponent collider;

        public HitResult(ColliderGizmoHandle handle, Collider2DComponent collider) {
            this.handle = handle;
            this.collider = collider;
        }
    }

    /**
     * Hit tests collider handles on the selected entity.
     */
    public HitResult hitTest(Vector2 worldClickPos, float scaleFactor) {
        Entity selected = editorState.getSelectedEntity();
        if (selected == null || worldClickPos == null) {
            return new HitResult(ColliderGizmoHandle.NONE, null);
        }

        float hitRadius = HANDLE_RADIUS * Math.max(0.1f, scaleFactor);
        float hitRadiusSq = hitRadius * hitRadius;

        Vector3 entityPos = selected.getTransform().getWorldPosition();
        Vector3 entityScale = selected.getTransform().getWorldScale();
        float scaleX = Math.abs(entityScale.x);
        float scaleY = Math.abs(entityScale.y);

        // Check Box Colliders
        for (BoxCollider2DComponent box : selected.getComponents(BoxCollider2DComponent.class)) {
            if (!box.isEnabled()) continue;
            Vector2 off = box.getOffset();
            float centerX = entityPos.x + off.x * entityScale.x;
            float centerY = entityPos.y + off.y * entityScale.y;
            float halfW = (box.getWidth() * scaleX) * 0.5f;
            float halfH = (box.getHeight() * scaleY) * 0.5f;

            // Handles: Right, Left, Top, Bottom
            Vector2 rightHandle = new Vector2(centerX + halfW, centerY);
            if (worldClickPos.dst2(rightHandle) <= hitRadiusSq) {
                return new HitResult(ColliderGizmoHandle.BOX_RIGHT, box);
            }

            Vector2 leftHandle = new Vector2(centerX - halfW, centerY);
            if (worldClickPos.dst2(leftHandle) <= hitRadiusSq) {
                return new HitResult(ColliderGizmoHandle.BOX_LEFT, box);
            }

            Vector2 topHandle = new Vector2(centerX, centerY + halfH);
            if (worldClickPos.dst2(topHandle) <= hitRadiusSq) {
                return new HitResult(ColliderGizmoHandle.BOX_TOP, box);
            }

            Vector2 bottomHandle = new Vector2(centerX, centerY - halfH);
            if (worldClickPos.dst2(bottomHandle) <= hitRadiusSq) {
                return new HitResult(ColliderGizmoHandle.BOX_BOTTOM, box);
            }
        }

        // Check Circle Colliders
        for (CircleCollider2DComponent circle : selected.getComponents(CircleCollider2DComponent.class)) {
            if (!circle.isEnabled()) continue;
            Vector2 off = circle.getOffset();
            float centerX = entityPos.x + off.x * entityScale.x;
            float centerY = entityPos.y + off.y * entityScale.y;
            float effectiveR = circle.getRadius() * Math.max(scaleX, scaleY);

            Vector2 radiusHandle = new Vector2(centerX + effectiveR, centerY);
            if (worldClickPos.dst2(radiusHandle) <= hitRadiusSq) {
                return new HitResult(ColliderGizmoHandle.CIRCLE_RADIUS, circle);
            }
        }

        return new HitResult(ColliderGizmoHandle.NONE, null);
    }

    public boolean startDrag(HitResult hit, Vector2 startWorldPos) {
        if (hit == null || hit.handle == ColliderGizmoHandle.NONE || hit.collider == null || startWorldPos == null) {
            return false;
        }

        Entity selected = editorState.getSelectedEntity();
        if (selected == null) {
            return false;
        }

        this.isDragging = true;
        this.activeHandle = hit.handle;
        this.activeCollider = hit.collider;
        this.dragStartWorldPos.set(startWorldPos);

        if (hit.collider instanceof BoxCollider2DComponent box) {
            this.initialWidth = box.getWidth();
            this.initialHeight = box.getHeight();
            this.initialOffset.set(box.getOffset());
        } else if (hit.collider instanceof CircleCollider2DComponent circle) {
            this.initialRadius = circle.getRadius();
            this.initialOffset.set(circle.getOffset());
        }

        return true;
    }

    public void updateDrag(Vector2 currentWorldPos) {
        if (!isDragging || activeCollider == null) return;
        Entity selected = editorState.getSelectedEntity();
        if (selected == null) return;

        Vector3 scale = selected.getTransform().getWorldScale();
        float scaleX = Math.max(0.001f, Math.abs(scale.x));
        float scaleY = Math.max(0.001f, Math.abs(scale.y));

        float dx = (currentWorldPos.x - dragStartWorldPos.x) / scaleX;
        float dy = (currentWorldPos.y - dragStartWorldPos.y) / scaleY;

        if (activeCollider instanceof BoxCollider2DComponent box) {
            switch (activeHandle) {
                case BOX_RIGHT -> {
                    float newW = Math.max(2.0f, initialWidth + dx * 2f);
                    box.setWidth(newW);
                }
                case BOX_LEFT -> {
                    float newW = Math.max(2.0f, initialWidth - dx * 2f);
                    box.setWidth(newW);
                }
                case BOX_TOP -> {
                    float newH = Math.max(2.0f, initialHeight + dy * 2f);
                    box.setHeight(newH);
                }
                case BOX_BOTTOM -> {
                    float newH = Math.max(2.0f, initialHeight - dy * 2f);
                    box.setHeight(newH);
                }
                default -> {}
            }
            editorState.notifyEntityTransformModified(selected);
        } else if (activeCollider instanceof CircleCollider2DComponent circle) {
            if (activeHandle == ColliderGizmoHandle.CIRCLE_RADIUS) {
                float effectiveScale = Math.max(scaleX, scaleY);
                float worldDelta = (currentWorldPos.x - dragStartWorldPos.x) / effectiveScale;
                float newR = Math.max(2.0f, initialRadius + worldDelta);
                circle.setRadius(newR);
                editorState.notifyEntityTransformModified(selected);
            }
        }
    }

    public void endDrag() {
        if (!isDragging || activeCollider == null) {
            cancelDrag();
            return;
        }

        Entity selected = editorState.getSelectedEntity();
        if (selected == null) {
            cancelDrag();
            return;
        }

        if (activeCollider instanceof BoxCollider2DComponent box) {
            float finalW = box.getWidth();
            float finalH = box.getHeight();

            if (finalW != initialWidth) {
                box.setWidth(initialWidth); // reset before executing command for clean undo stack
                editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                    editorState, selected.getId(), box, initialWidth, finalW, box::setWidth, "Resize Box Collider Width"
                ));
            }
            if (finalH != initialHeight) {
                box.setHeight(initialHeight);
                editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                    editorState, selected.getId(), box, initialHeight, finalH, box::setHeight, "Resize Box Collider Height"
                ));
            }
        } else if (activeCollider instanceof CircleCollider2DComponent circle) {
            float finalR = circle.getRadius();
            if (finalR != initialRadius) {
                circle.setRadius(initialRadius);
                editorState.getCommandHistory().execute(new ModifyComponentPropertyCommand<>(
                    editorState, selected.getId(), circle, initialRadius, finalR, circle::setRadius, "Resize Circle Collider Radius"
                ));
            }
        }

        isDragging = false;
        activeHandle = ColliderGizmoHandle.NONE;
        activeCollider = null;
    }

    public void cancelDrag() {
        if (isDragging && activeCollider != null) {
            if (activeCollider instanceof BoxCollider2DComponent box) {
                box.setWidth(initialWidth);
                box.setHeight(initialHeight);
            } else if (activeCollider instanceof CircleCollider2DComponent circle) {
                circle.setRadius(initialRadius);
            }
            Entity selected = editorState.getSelectedEntity();
            if (selected != null) {
                editorState.notifyEntityTransformModified(selected);
            }
        }
        isDragging = false;
        activeHandle = ColliderGizmoHandle.NONE;
        activeCollider = null;
    }
}
