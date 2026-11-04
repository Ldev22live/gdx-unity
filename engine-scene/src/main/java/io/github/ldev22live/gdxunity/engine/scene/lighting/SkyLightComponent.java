package io.github.ldev22live.gdxunity.engine.scene.lighting;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * Light from the whole sky, used by the 3D renderer for image-based lighting: surfaces facing up take the sky
 * color, surfaces facing down the ground color, and shiny surfaces reflect both. Replaces the renderer's flat
 * ambient color while it is enabled. The entity's position and rotation are ignored.
 *
 * <p>Only the first enabled sky light of a scene is used.
 */
public class SkyLightComponent extends BaseComponent {
    /** Default color of the upper half of the sky: a light daylight blue. */
    public static final Color DEFAULT_SKY_COLOR = new Color(0.62f, 0.72f, 0.88f, 1f);
    /** Default color below the horizon: a dim warm grey, like lit ground. */
    public static final Color DEFAULT_GROUND_COLOR = new Color(0.32f, 0.29f, 0.26f, 1f);

    private final Color skyColor = new Color(DEFAULT_SKY_COLOR);
    private final Color groundColor = new Color(DEFAULT_GROUND_COLOR);
    private float intensity = 1f;

    /** Color of the sky above the horizon (a copy). */
    public Color getSkyColor() {
        return new Color(skyColor);
    }

    /** Sets the color of the sky above the horizon. */
    public void setSkyColor(Color skyColor) {
        this.skyColor.set(Objects.requireNonNull(skyColor, "skyColor cannot be null"));
    }

    /** Color below the horizon (a copy). */
    public Color getGroundColor() {
        return new Color(groundColor);
    }

    /** Sets the color below the horizon. */
    public void setGroundColor(Color groundColor) {
        this.groundColor.set(Objects.requireNonNull(groundColor, "groundColor cannot be null"));
    }

    /** Brightness multiplier of the sky light. Default 1. */
    public float getIntensity() {
        return intensity;
    }

    /**
     * Sets the brightness multiplier.
     *
     * @throws IllegalArgumentException if {@code intensity} is negative or not a number
     */
    public void setIntensity(float intensity) {
        if (!(intensity >= 0f) || Float.isInfinite(intensity)) {
            throw new IllegalArgumentException("intensity must be a finite value >= 0: " + intensity);
        }
        this.intensity = intensity;
    }
}
