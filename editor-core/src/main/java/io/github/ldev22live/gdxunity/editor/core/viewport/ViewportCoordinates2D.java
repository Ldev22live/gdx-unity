package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

import java.util.Objects;

/**
 * Converts panel-local pointer positions into 2D world coordinates for an editor camera that fills its
 * viewport panel at one world unit per pixel when zoom is 1 (TD-024).
 */
public final class ViewportCoordinates2D {

    private ViewportCoordinates2D() {}

    /**
     * @param localX         pointer x from the panel's left edge, in UI units
     * @param localY         pointer y from the panel's bottom edge, in UI units (y up)
     * @param panelWidth     panel width in UI units; must be positive
     * @param panelHeight    panel height in UI units; must be positive
     * @param cameraPosition world position at the panel center
     * @param zoom           camera zoom; world units per UI unit; must be positive
     * @return the world position under the pointer
     */
    public static Vector2 panelToWorld(float localX, float localY, float panelWidth, float panelHeight,
                                       Vector3 cameraPosition, float zoom) {
        Objects.requireNonNull(cameraPosition, "cameraPosition cannot be null");
        if (panelWidth <= 0f || panelHeight <= 0f) {
            throw new IllegalArgumentException("Panel size must be positive, got: " + panelWidth + "x" + panelHeight);
        }
        if (zoom <= 0f) {
            throw new IllegalArgumentException("Zoom must be positive, got: " + zoom);
        }
        float worldX = cameraPosition.x + (localX - panelWidth * 0.5f) * zoom;
        float worldY = cameraPosition.y + (localY - panelHeight * 0.5f) * zoom;
        return new Vector2(worldX, worldY);
    }
}
