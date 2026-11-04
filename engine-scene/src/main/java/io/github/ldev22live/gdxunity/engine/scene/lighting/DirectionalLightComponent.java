package io.github.ldev22live.gdxunity.engine.scene.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

/**
 * Directional light component for simulating light from a distant source (e.g., sun).
 * Directional lights have a direction but no position, affecting all objects equally.
 *
 * <p>With {@link #isCastShadows()} the 3D renderer draws this light's shadows. Only one directional light
 * casts shadows at a time: the first enabled one that has it switched on.
 */
public class DirectionalLightComponent extends BaseComponent {
    private final Vector3 direction = new Vector3(0f, -1f, -0.5f).nor();
    private Color color = new Color(1f, 1f, 1f, 1f);
    private float intensity = 1.0f;
    private boolean castShadows = true;

    public DirectionalLightComponent() {
        super();
    }

    /**
     * Gets the light direction vector (normalized).
     */
    public Vector3 getDirection() {
        return new Vector3(direction);
    }

    /**
     * Sets the light direction vector. Will be normalized.
     */
    public void setDirection(Vector3 direction) {
        if (direction != null && !direction.isZero()) {
            this.direction.set(direction).nor();
        }
    }

    /**
     * Sets the light direction by components. Will be normalized.
     */
    public void setDirection(float x, float y, float z) {
        this.direction.set(x, y, z).nor();
    }

    /**
     * Gets the light color.
     */
    public Color getColor() {
        return new Color(color);
    }

    /**
     * Sets the light color.
     */
    public void setColor(Color color) {
        if (color != null) {
            this.color.set(color);
        }
    }

    /**
     * Gets the light intensity multiplier.
     */
    public float getIntensity() {
        return intensity;
    }

    /**
     * Sets the light intensity multiplier.
     */
    public void setIntensity(float intensity) {
        this.intensity = Math.max(0f, intensity);
    }

    /**
     * Whether this light casts shadows in the 3D view. Default true.
     */
    public boolean isCastShadows() {
        return castShadows;
    }

    /**
     * Sets whether this light casts shadows in the 3D view.
     */
    public void setCastShadows(boolean castShadows) {
        this.castShadows = castShadows;
    }
}
