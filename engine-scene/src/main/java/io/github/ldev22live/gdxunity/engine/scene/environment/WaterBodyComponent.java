package io.github.ldev22live.gdxunity.engine.scene.environment;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * A body of water (roadmap M3.9, TD-090): a rectangular surface centred on the entity, level with its position and
 * turned with it, {@link #getWidth()} across its X axis and {@link #getLength()} along its Z axis (the entity's scale
 * applies too). Waves move the surface up and down by up to {@link #getWaveHeight()}.
 *
 * <p>It is drawn only: it does not collide or float anything. The colour is sRGB, and its alpha is how opaque the
 * water is. {@link WaterPreset} fills every setting at once.
 */
public class WaterBodyComponent extends BaseComponent {
    /** Largest width or length, in world units (10 km at 100 units per metre). */
    public static final float MAX_SIZE = 1_000_000f;

    private float width;
    private float length;
    private final Color color = new Color();
    private float roughness;
    private float waveHeight;
    private float waveLength;
    private float waveSpeed;

    /** A calm lake 40 m square ({@link WaterPreset#LAKE}). */
    public WaterBodyComponent() {
        setSize(DEFAULT_SIZE, DEFAULT_SIZE);
        WaterPreset.LAKE.applyTo(this);
    }

    /** Width and length of a new water body: 40 m at 100 units per metre. */
    public static final float DEFAULT_SIZE = 4000f;

    /** Size across the entity's X axis, in world units. */
    public float getWidth() {
        return width;
    }

    /** Size along the entity's Z axis, in world units. */
    public float getLength() {
        return length;
    }

    /**
     * Sets the size of the surface.
     *
     * @throws IllegalArgumentException if either is not more than 0 or past {@link #MAX_SIZE}
     */
    public void setSize(float width, float length) {
        if (!(width > 0f && width <= MAX_SIZE) || !(length > 0f && length <= MAX_SIZE)) {
            throw new IllegalArgumentException("Water size must be within 0 and " + MAX_SIZE + ": " + width + " by " + length);
        }
        this.width = width;
        this.length = length;
    }

    /** Colour of the water (a copy); alpha is how opaque it is. */
    public Color getColor() {
        return new Color(color);
    }

    /** Writes the colour into {@code out} and returns it; for per-frame use without allocating. */
    public Color getColor(Color out) {
        return out.set(color);
    }

    /** Sets the colour; alpha is how opaque the water is. */
    public void setColor(Color color) {
        this.color.set(Objects.requireNonNull(color, "color cannot be null"));
    }

    /** 0 is a mirror, 1 is matte. */
    public float getRoughness() {
        return roughness;
    }

    /**
     * Sets how rough the surface is.
     *
     * @throws IllegalArgumentException if {@code roughness} is outside 0 to 1
     */
    public void setRoughness(float roughness) {
        if (!(roughness >= 0f && roughness <= 1f)) {
            throw new IllegalArgumentException("Roughness must be within 0 and 1: " + roughness);
        }
        this.roughness = roughness;
    }

    /** How far, in world units, waves raise or lower the surface; 0 is still water. */
    public float getWaveHeight() {
        return waveHeight;
    }

    /** Distance, in world units, from one wave crest to the next. */
    public float getWaveLength() {
        return waveLength;
    }

    /** How fast waves travel, in world units per second. */
    public float getWaveSpeed() {
        return waveSpeed;
    }

    /**
     * Sets the waves.
     *
     * @throws IllegalArgumentException if {@code height} or {@code speed} is negative, {@code length} is not more
     *                                  than 0, or any is not finite
     */
    public void setWaves(float height, float length, float speed) {
        if (!(height >= 0f) || !Float.isFinite(height) || !(length > 0f) || !Float.isFinite(length)
            || !(speed >= 0f) || !Float.isFinite(speed)) {
            throw new IllegalArgumentException("Waves need height >= 0, length > 0 and speed >= 0: "
                + height + ", " + length + ", " + speed);
        }
        this.waveHeight = height;
        this.waveLength = length;
        this.waveSpeed = speed;
    }

    /** A new component with the same settings; enabled like this one. */
    public WaterBodyComponent copy() {
        WaterBodyComponent copy = new WaterBodyComponent();
        copy.set(this);
        return copy;
    }

    /** Takes every setting of {@code other}, size and enabled state included. */
    public void set(WaterBodyComponent other) {
        Objects.requireNonNull(other, "other cannot be null");
        width = other.width;
        length = other.length;
        color.set(other.color);
        roughness = other.roughness;
        waveHeight = other.waveHeight;
        waveLength = other.waveLength;
        waveSpeed = other.waveSpeed;
        setEnabled(other.isEnabled());
    }
}
