package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.collision.BoundingBox;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * Draws editable triangle geometry, such as a sculpt (TD-038), at the entity's transform. The geometry is found
 * through the renderer's {@link io.github.ldev22live.gdxunity.engine.render.DynamicMeshSource} by {@link #getMeshPath}.
 * One unit of the mesh is one scene unit, scaled by the entity's transform.
 */
public class DynamicMeshComponent extends BaseComponent {
    private String meshPath;
    private final Color color = new Color(0.78f, 0.74f, 0.70f, 1f);

    /** Set by the renderer each frame from the mesh, so picking and framing know its size. */
    private BoundingBox bounds;

    /**
     * @param meshPath project-relative path of the mesh asset, not blank
     * @throws IllegalArgumentException if the path is blank
     */
    public DynamicMeshComponent(String meshPath) {
        setMeshPath(meshPath);
    }

    /** Project-relative path of the mesh asset. */
    public String getMeshPath() {
        return meshPath;
    }

    /**
     * @param meshPath project-relative path, not blank
     * @throws IllegalArgumentException if the path is blank
     */
    public void setMeshPath(String meshPath) {
        Objects.requireNonNull(meshPath, "meshPath cannot be null");
        if (meshPath.isBlank()) {
            throw new IllegalArgumentException("meshPath cannot be blank");
        }
        if (!meshPath.equals(this.meshPath)) {
            this.meshPath = meshPath;
            this.bounds = null;
        }
    }

    /** A copy of the base colour of the surface. */
    public Color getColor() {
        return new Color(color);
    }

    /** Sets the base colour of the surface. */
    public void setColor(Color color) {
        this.color.set(Objects.requireNonNull(color, "color cannot be null"));
    }

    /** Bounds in mesh space as last reported by the renderer; null before the first frame or when there is no mesh. */
    public BoundingBox getBounds() {
        return bounds == null ? null : new BoundingBox(bounds);
    }

    /** Called by the renderer; null forgets the bounds. */
    public void setBounds(BoundingBox bounds) {
        this.bounds = bounds == null ? null : new BoundingBox(bounds);
    }
}
