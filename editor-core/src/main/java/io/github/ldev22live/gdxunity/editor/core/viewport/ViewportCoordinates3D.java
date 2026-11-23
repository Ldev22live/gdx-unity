package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

import java.util.Objects;

/**
 * Builds world-space picking rays from panel-local pointer positions for a camera that fills its viewport
 * panel. Pure math on the camera's inverse projection-view matrix; no window or graphics context needed.
 */
public final class ViewportCoordinates3D {

    private ViewportCoordinates3D() {}

    /**
     * @param localX      pointer x from the panel's left edge
     * @param localY      pointer y from the panel's bottom edge (y up)
     * @param panelWidth  panel width; must be positive
     * @param panelHeight panel height; must be positive
     * @param camera      an updated LibGDX camera ({@code invProjectionView} must be current)
     * @return a ray starting on the near plane, pointing into the scene
     */
    public static Ray panelToRay(float localX, float localY, float panelWidth, float panelHeight, Camera camera) {
        Objects.requireNonNull(camera, "camera cannot be null");
        if (panelWidth <= 0f || panelHeight <= 0f) {
            throw new IllegalArgumentException("Panel size must be positive, got: " + panelWidth + "x" + panelHeight);
        }
        float ndcX = 2f * localX / panelWidth - 1f;
        float ndcY = 2f * localY / panelHeight - 1f;
        Vector3 near = new Vector3(ndcX, ndcY, -1f).prj(camera.invProjectionView);
        Vector3 far = new Vector3(ndcX, ndcY, 1f).prj(camera.invProjectionView);
        return new Ray(near, far.sub(near).nor());
    }
}
