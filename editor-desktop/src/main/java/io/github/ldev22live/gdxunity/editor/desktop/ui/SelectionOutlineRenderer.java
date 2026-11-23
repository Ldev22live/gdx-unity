package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.gizmo.TransformGizmoMath;
import io.github.ldev22live.gdxunity.editor.core.picking.ViewportPicker;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Objects;
import java.util.UUID;

/**
 * Draws an outline around every selected entity, in every tool mode, so selection is always visible.
 * The outline is the entity's pickable area: a box around 3D primitives in the 3D view
 * ({@link ViewportPicker#computeMeshBounds}), otherwise a rectangle ({@link ViewportPicker#computeRenderBounds});
 * entities with no renderer get a small diamond marker at their position.
 */
public class SelectionOutlineRenderer {

    private static final Color PRIMARY_COLOR = new Color(1.0f, 0.62f, 0.12f, 1f);
    private static final Color SECONDARY_COLOR = new Color(1.0f, 0.62f, 0.12f, 0.55f);
    /** Outline gap around the bounds, in screen-relative units (scaled like gizmo handles). */
    private static final float OUTLINE_PADDING = 3f;
    /** Half-size of the marker drawn for entities without a renderer. */
    private static final float MARKER_SIZE = 8f;

    private final EditorState editorState;
    private final Camera camera;
    private final Matrix4 identity = new Matrix4();

    public SelectionOutlineRenderer(EditorState editorState, Camera camera) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
    }

    public void render(ShapeRenderer shapeRenderer) {
        Scene scene = editorState.getActiveScene();
        if (shapeRenderer == null || scene == null || editorState.getSelectedEntityIds().isEmpty()) {
            return;
        }

        Matrix4 previousTransform = new Matrix4(shapeRenderer.getTransformMatrix());
        shapeRenderer.setTransformMatrix(identity);
        shapeRenderer.setProjectionMatrix(camera.getCombinedMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        UUID primaryId = editorState.getSelectedEntityId();
        for (UUID id : editorState.getSelectedEntityIds()) {
            Entity entity = scene.findEntityById(id);
            if (entity == null) {
                continue;
            }
            shapeRenderer.setColor(id.equals(primaryId) ? PRIMARY_COLOR : SECONDARY_COLOR);
            Vector3 position = entity.getTransform().getWorldPosition();
            float scale = scaleAt(position);
            BoundingBox localMeshBounds = (camera instanceof PerspectiveCamera3D) ? ViewportPicker.computeMeshLocalBounds(entity) : null;
            Rectangle bounds = ViewportPicker.computeRenderBounds(entity);
            if (localMeshBounds != null) {
                drawOrientedBox(shapeRenderer, localMeshBounds, entity.getTransform().getWorldMatrix(),
                    entity.getTransform().getWorldScale(), OUTLINE_PADDING * scale);
            } else if (bounds != null) {
                float pad = OUTLINE_PADDING * scale;
                drawRect(shapeRenderer, bounds.x - pad, bounds.y - pad, bounds.width + 2f * pad, bounds.height + 2f * pad, position.z);
            } else {
                drawMarker(shapeRenderer, position, MARKER_SIZE * scale);
            }
        }

        shapeRenderer.end();
        shapeRenderer.setTransformMatrix(previousTransform);
    }

    /** Keeps the padding a constant size on screen: camera zoom in 2D, distance-based in 3D. */
    private float scaleAt(Vector3 position) {
        if (camera instanceof PerspectiveCamera3D perspective) {
            return TransformGizmoMath.perspectiveScaleFactor(camera.getPosition(), position, perspective.getFieldOfView());
        }
        return camera.getZoom() > 0f ? camera.getZoom() : 1f;
    }

    private static void drawRect(ShapeRenderer sr, float x, float y, float w, float h, float z) {
        sr.line(x, y, z, x + w, y, z);
        sr.line(x + w, y, z, x + w, y + h, z);
        sr.line(x + w, y + h, z, x, y + h, z);
        sr.line(x, y + h, z, x, y, z);
    }

    /**
     * The 12 edges of a local-space box placed by {@code worldMatrix} (follows rotation and scale). The world-space
     * padding is converted to local units per axis so the gap looks even under non-uniform scale.
     */
    private static void drawOrientedBox(ShapeRenderer sr, BoundingBox localBox, Matrix4 worldMatrix, Vector3 worldScale, float pad) {
        Vector3 localPad = new Vector3(pad / nonZero(worldScale.x), pad / nonZero(worldScale.y), pad / nonZero(worldScale.z));
        Vector3 min = new Vector3(localBox.min).sub(localPad);
        Vector3 max = new Vector3(localBox.max).add(localPad);
        Vector3[] corners = new Vector3[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = new Vector3((i & 1) == 0 ? min.x : max.x, (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z)
                .mul(worldMatrix);
        }
        // Corners whose indices differ in exactly one bit share an edge
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit < 8; bit <<= 1) {
                int j = i | bit;
                if (j != i) {
                    sr.line(corners[i], corners[j]);
                }
            }
        }
    }

    private static float nonZero(float value) {
        return Math.abs(value) < 1e-6f ? 1f : Math.abs(value);
    }

    private static void drawMarker(ShapeRenderer sr, Vector3 p, float size) {
        sr.line(p.x - size, p.y, p.z, p.x, p.y + size, p.z);
        sr.line(p.x, p.y + size, p.z, p.x + size, p.y, p.z);
        sr.line(p.x + size, p.y, p.z, p.x, p.y - size, p.z);
        sr.line(p.x, p.y - size, p.z, p.x - size, p.y, p.z);
    }
}
