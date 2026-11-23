package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import io.github.ldev22live.gdxunity.editor.core.picking.ViewportPicker;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;

/**
 * Framing math for "Focus Selection" (F): where to look and how far back or how far zoomed out the camera
 * must be so the selected entity fills the view with a margin.
 */
public final class ViewportFocus {

    /** The framed object occupies about 1 / FRAME_MARGIN of the view. */
    public static final float FRAME_MARGIN = 1.4f;
    /** Radius used for entities without renderable bounds (e.g. lights). */
    public static final float DEFAULT_FOCUS_RADIUS = 50f;

    private ViewportFocus() {}

    /** Center of the entity's 3D or 2D bounds, or its world position when it has none. */
    public static Vector3 focusCenter(Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        BoundingBox mesh = ViewportPicker.computeMeshBounds(entity);
        if (mesh != null) {
            return mesh.getCenter(new Vector3());
        }
        Vector3 position = entity.getTransform().getWorldPosition();
        Rectangle flat = ViewportPicker.computeRenderBounds(entity);
        if (flat != null) {
            return new Vector3(flat.x + flat.width / 2f, flat.y + flat.height / 2f, position.z);
        }
        return position;
    }

    /** Radius of a sphere enclosing the entity's bounds, or {@link #DEFAULT_FOCUS_RADIUS}. */
    public static float boundingRadius(Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        BoundingBox mesh = ViewportPicker.computeMeshBounds(entity);
        if (mesh != null) {
            return Math.max(DEFAULT_FOCUS_RADIUS * 0.1f, mesh.getDimensions(new Vector3()).len() / 2f);
        }
        Rectangle flat = ViewportPicker.computeRenderBounds(entity);
        if (flat != null) {
            return Math.max(DEFAULT_FOCUS_RADIUS * 0.1f, (float) Math.hypot(flat.width, flat.height) / 2f);
        }
        return DEFAULT_FOCUS_RADIUS;
    }

    /** Camera distance at which a sphere of {@code radius} fits a perspective view with the given vertical FOV. */
    public static float perspectiveDistance(float radius, float fieldOfViewDegrees) {
        if (radius <= 0f) {
            throw new IllegalArgumentException("radius must be positive, got: " + radius);
        }
        double halfFov = Math.toRadians(fieldOfViewDegrees) / 2.0;
        return (float) (radius * FRAME_MARGIN / Math.sin(halfFov));
    }

    /**
     * Orthographic zoom (world units per pixel) at which an area of {@code width} x {@code height} fits a panel,
     * clamped to the 2D viewport's zoom limits.
     */
    public static float orthographicZoom(float width, float height, float panelWidth, float panelHeight) {
        if (panelWidth <= 0f || panelHeight <= 0f) {
            throw new IllegalArgumentException("Panel size must be positive, got: " + panelWidth + "x" + panelHeight);
        }
        float zoom = Math.max(width / panelWidth, height / panelHeight) * FRAME_MARGIN;
        return Math.max(SceneViewportInteraction2D.MIN_ZOOM, Math.min(SceneViewportInteraction2D.MAX_ZOOM, zoom));
    }
}
