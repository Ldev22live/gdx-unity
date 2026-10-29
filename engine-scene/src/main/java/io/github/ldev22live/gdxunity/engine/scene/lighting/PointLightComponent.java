package io.github.ldev22live.gdxunity.engine.scene.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

/**
 * Point light component for simulating light from a point source in 3D space.
 * Point lights have a position and affect objects based on distance.
 */
public class PointLightComponent extends BaseComponent {
    private final Vector3 position = new Vector3(0f, 0f, 0f);
    private Color color = new Color(1f, 1f, 1f, 1f);
    private float intensity = 1.0f;
    private float range = 10.0f;

    public PointLightComponent() {
        super();
    }

    /**
     * Gets the light position in world space.
     */
    public Vector3 getPosition() {
        return new Vector3(position);
    }

    /**
     * Sets the light position in world space.
     */
    public void setPosition(Vector3 position) {
        if (position != null) {
            this.position.set(position);
        }
    }

    /**
     * Sets the light position by components.
     */
    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
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
     * Gets the effective range of the light.
     */
    public float getRange() {
        return range;
    }

    /**
     * Sets the effective range of the light.
     */
    public void setRange(float range) {
        this.range = Math.max(0.1f, range);
    }
}
