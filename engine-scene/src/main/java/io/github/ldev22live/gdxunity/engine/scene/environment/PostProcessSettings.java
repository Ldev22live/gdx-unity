package io.github.ldev22live.gdxunity.engine.scene.environment;

import com.badlogic.gdx.graphics.Color;

/**
 * The post-processing in effect for one camera position (roadmap M6.2, TD-091): every {@link PostProcessVolumeComponent} that
 * reaches the camera blended into one set of values by {@link PostProcessVolumes}. Starts {@linkplain #reset() neutral}: no bloom,
 * no grading, no ambient occlusion, no depth of field. Mutable and reused every frame.
 *
 * <p>An effect blends from its current value towards a volume's by the volume's weight, so a volume half way faded in gives half
 * its bloom, half its saturation change, and so on. The renderer runs only the effects that are on ({@link #hasBloom()} and its
 * siblings).
 */
public final class PostProcessSettings {
    /** Below this an effect's strength counts as off. */
    public static final float EPSILON = 0.001f;

    private float bloomIntensity;
    private float bloomThreshold;
    private float bloomRadius;
    private float exposure;
    private float contrast;
    private float saturation;
    private float temperature;
    private float tint;
    private final Color colorFilter = new Color();
    private float aoIntensity;
    private float aoRadius;
    private float blur;
    private float focusDistance;
    private float focusRange;
    private final Color scratch = new Color();

    /** Neutral settings. */
    public PostProcessSettings() {
        reset();
    }

    /** Back to neutral: every effect off, its other values at a new volume's defaults. */
    public PostProcessSettings reset() {
        PostProcessVolumeComponent defaults = DEFAULTS;
        bloomIntensity = 0f;
        bloomThreshold = defaults.getBloomThreshold();
        bloomRadius = defaults.getBloomRadius();
        exposure = 0f;
        contrast = 1f;
        saturation = 1f;
        temperature = 0f;
        tint = 0f;
        colorFilter.set(Color.WHITE);
        aoIntensity = 0f;
        aoRadius = defaults.getAoRadius();
        blur = 0f;
        focusDistance = defaults.getFocusDistance();
        focusRange = defaults.getFocusRange();
        return this;
    }

    private static final PostProcessVolumeComponent DEFAULTS = new PostProcessVolumeComponent();

    /**
     * Moves every effect {@code volume} switches on towards the volume's values by {@code amount} (0 changes nothing, 1 takes the
     * volume's values). Effects the volume leaves off keep their values.
     *
     * @throws IllegalArgumentException if {@code amount} is outside 0 to 1
     */
    public PostProcessSettings blend(PostProcessVolumeComponent volume, float amount) {
        if (!(amount >= 0f && amount <= 1f)) {
            throw new IllegalArgumentException("Blend amount must be within 0 and 1: " + amount);
        }
        if (amount == 0f) {
            return this;
        }
        if (volume.isBloom()) {
            bloomIntensity = lerp(bloomIntensity, volume.getBloomIntensity(), amount);
            bloomThreshold = lerp(bloomThreshold, volume.getBloomThreshold(), amount);
            bloomRadius = lerp(bloomRadius, volume.getBloomRadius(), amount);
        }
        if (volume.isGrading()) {
            exposure = lerp(exposure, volume.getExposure(), amount);
            contrast = lerp(contrast, volume.getContrast(), amount);
            saturation = lerp(saturation, volume.getSaturation(), amount);
            temperature = lerp(temperature, volume.getTemperature(), amount);
            tint = lerp(tint, volume.getTint(), amount);
            colorFilter.lerp(volume.getColorFilter(scratch), amount);
        }
        if (volume.isAmbientOcclusion()) {
            aoIntensity = lerp(aoIntensity, volume.getAoIntensity(), amount);
            aoRadius = lerp(aoRadius, volume.getAoRadius(), amount);
        }
        if (volume.isDepthOfField()) {
            blur = lerp(blur, volume.getBlur(), amount);
            focusDistance = lerp(focusDistance, volume.getFocusDistance(), amount);
            focusRange = lerp(focusRange, volume.getFocusRange(), amount);
        }
        return this;
    }

    /** Whether bright parts glow. */
    public boolean hasBloom() {
        return bloomIntensity > EPSILON;
    }

    /** Whether the colours are changed. */
    public boolean hasGrading() {
        return Math.abs(exposure) > EPSILON || Math.abs(contrast - 1f) > EPSILON || Math.abs(saturation - 1f) > EPSILON
            || Math.abs(temperature) > EPSILON || Math.abs(tint) > EPSILON || 1f - colorFilter.r > EPSILON
            || 1f - colorFilter.g > EPSILON || 1f - colorFilter.b > EPSILON;
    }

    /** Whether creases are darkened. */
    public boolean hasAmbientOcclusion() {
        return aoIntensity > EPSILON;
    }

    /** Whether out-of-focus parts are blurred. */
    public boolean hasDepthOfField() {
        return blur > EPSILON;
    }

    /** Whether any effect changes the frame; false means the frame is drawn without post-processing. */
    public boolean isActive() {
        return hasBloom() || hasGrading() || hasAmbientOcclusion() || hasDepthOfField();
    }

    public float getBloomIntensity() {
        return bloomIntensity;
    }

    public float getBloomThreshold() {
        return bloomThreshold;
    }

    public float getBloomRadius() {
        return bloomRadius;
    }

    /** Exposure change in stops. */
    public float getExposure() {
        return exposure;
    }

    public float getContrast() {
        return contrast;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getTemperature() {
        return temperature;
    }

    public float getTint() {
        return tint;
    }

    /** Writes the colour filter into {@code out} and returns it. */
    public Color getColorFilter(Color out) {
        return out.set(colorFilter);
    }

    public float getAoIntensity() {
        return aoIntensity;
    }

    public float getAoRadius() {
        return aoRadius;
    }

    /** Depth-of-field blur, 0 to 1. */
    public float getBlur() {
        return blur;
    }

    public float getFocusDistance() {
        return focusDistance;
    }

    public float getFocusRange() {
        return focusRange;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}
