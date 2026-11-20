package io.github.ldev22live.gdxunity.engine.render;

/**
 * The arithmetic of the post-processing passes (roadmap M6.2, TD-091), kept on the CPU so it is tested without a graphics context.
 * {@link PostProcessor}'s shaders compute the same formulas per pixel; the comments in its shader text name the method each line
 * follows.
 */
public final class PostProcessMath {
    /** Strength of the white balance: a temperature of 1 raises red and lowers blue by this fraction. */
    static final float TEMPERATURE_GAIN = 0.25f;
    /** Strength of the tint: a tint of 1 lowers green by this fraction. */
    static final float TINT_GAIN = 0.2f;
    /** Rec. 709 luminance weights, used for saturation and to keep white balance from changing brightness. */
    static final float LUMA_R = 0.2126f;
    static final float LUMA_G = 0.7152f;
    static final float LUMA_B = 0.0722f;

    private PostProcessMath() {}

    /**
     * The per-channel gains of a white balance, written into {@code out} (r, g, b; gains may pass 1, which a {@link
     * com.badlogic.gdx.graphics.Color} would clamp) and returned. Positive temperatures
     * warm the frame (more red, less blue), positive tints shift it towards magenta (less green). The gains are scaled so a grey
     * keeps its luminance.
     */
    public static float[] whiteBalance(float temperature, float tint, float[] out) {
        float r = 1f + TEMPERATURE_GAIN * temperature + TINT_GAIN * 0.5f * tint;
        float g = 1f - TINT_GAIN * tint;
        float b = 1f - TEMPERATURE_GAIN * temperature + TINT_GAIN * 0.5f * tint;
        float luma = LUMA_R * r + LUMA_G * g + LUMA_B * b;
        out[0] = r / luma;
        out[1] = g / luma;
        out[2] = b / luma;
        return out;
    }

    /**
     * Distance along the view direction, in world units, of a window depth {@code depth} (0 at the near plane, 1 at the far plane)
     * for a perspective or orthographic projection.
     */
    public static float linearDepth(float depth, float near, float far, boolean orthographic) {
        if (orthographic) {
            return near + depth * (far - near);
        }
        return near * far / (far - depth * (far - near));
    }

    /**
     * How blurred a surface {@code distance} world units away is, 0 (sharp) to 1 (fully blurred): sharp within half the focus range
     * of the focus distance, then fading to full blur over one more focus range (at least 1 unit), with a smooth step.
     */
    public static float circleOfConfusion(float distance, float focusDistance, float focusRange) {
        float sharp = focusRange * 0.5f;
        float fade = Math.max(focusRange, 1f);
        float t = Math.min(1f, Math.max(0f, (Math.abs(distance - focusDistance) - sharp) / fade));
        return t * t * (3f - 2f * t);
    }

    /**
     * How much of a pixel of brightness {@code brightness} (its largest channel) passes into the bloom, 0 to 1: nothing well below
     * {@code threshold}, everything above, with a soft knee of {@code knee} either side.
     */
    public static float bloomContribution(float brightness, float threshold, float knee) {
        float soft = Math.min(2f * knee, Math.max(0f, brightness - threshold + knee));
        soft = soft * soft / (4f * knee + 1e-4f);
        return Math.max(soft, brightness - threshold) / Math.max(brightness, 1e-4f);
    }

    /** One channel graded: contrast around mid grey and clamped to 0 to 1 (exposure, white balance and filter come before). */
    public static float contrast(float channel, float contrast) {
        return Math.min(1f, Math.max(0f, (channel - 0.5f) * contrast + 0.5f));
    }

    /** Size of a reduced-resolution pass: half the frame, at least one pixel. */
    public static int half(int size) {
        return Math.max(1, size / 2);
    }
}
