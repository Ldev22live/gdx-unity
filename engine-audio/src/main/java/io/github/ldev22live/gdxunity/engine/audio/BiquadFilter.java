package io.github.ldev22live.gdxunity.engine.audio;

import java.util.Objects;

/** A resonant two-pole filter (RBJ biquad). One instance filters one channel of audio. */
public final class BiquadFilter {
    /** What the filter lets through. */
    public enum Type { LOW_PASS, HIGH_PASS, BAND_PASS }

    /** Lowest cutoff, Hz. */
    public static final float MIN_CUTOFF = 20f;
    /** Highest cutoff as a share of the sample rate (stays below Nyquist). */
    private static final float MAX_CUTOFF_SHARE = 0.45f;
    /** Lowest resonance; 0.707 is flat. */
    public static final float MIN_Q = 0.5f;
    /** Highest resonance. */
    public static final float MAX_Q = 20f;

    private final float sampleRate;
    private float b0 = 1f, b1, b2, a1, a2;
    private float z1, z2;

    public BiquadFilter(float sampleRate) {
        if (sampleRate <= 0f) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        this.sampleRate = sampleRate;
    }

    /** Recomputes the coefficients; cutoff and Q are clamped to usable ranges. */
    public void set(Type type, float cutoffHz, float q) {
        Objects.requireNonNull(type, "type cannot be null");
        float cutoff = Math.min(Math.max(cutoffHz, MIN_CUTOFF), sampleRate * MAX_CUTOFF_SHARE);
        float quality = Math.min(Math.max(q, MIN_Q), MAX_Q);
        double w0 = 2.0 * Math.PI * cutoff / sampleRate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / (2.0 * quality);
        double a0 = 1.0 + alpha;
        double nb0;
        double nb1;
        double nb2;
        switch (type) {
            case LOW_PASS -> {
                nb0 = (1.0 - cos) / 2.0;
                nb1 = 1.0 - cos;
                nb2 = nb0;
            }
            case HIGH_PASS -> {
                nb0 = (1.0 + cos) / 2.0;
                nb1 = -(1.0 + cos);
                nb2 = nb0;
            }
            case BAND_PASS -> {
                nb0 = alpha;
                nb1 = 0.0;
                nb2 = -alpha;
            }
            default -> throw new IllegalArgumentException("Unknown filter type " + type);
        }
        b0 = (float) (nb0 / a0);
        b1 = (float) (nb1 / a0);
        b2 = (float) (nb2 / a0);
        a1 = (float) (-2.0 * cos / a0);
        a2 = (float) ((1.0 - alpha) / a0);
    }

    /** Filters one sample. */
    public float process(float x) {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }

    /** Clears the memory, so a new note does not start with the last one's tail. */
    public void reset() {
        z1 = 0f;
        z2 = 0f;
    }
}
