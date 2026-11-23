package io.github.ldev22live.gdxunity.engine.audio;

import java.util.Objects;

/** Decoded audio: one float array per channel, values in -1..1. Mono audio uses the same array for both sides. */
public final class SampleData {
    private final float[] left;
    private final float[] right;
    private final int sampleRate;

    /** Mono audio. */
    public SampleData(float[] mono, int sampleRate) {
        this(mono, mono, sampleRate);
    }

    /** Stereo audio; both arrays must have the same length. */
    public SampleData(float[] left, float[] right, int sampleRate) {
        this.left = Objects.requireNonNull(left, "left cannot be null");
        this.right = Objects.requireNonNull(right, "right cannot be null");
        if (left.length != right.length) {
            throw new IllegalArgumentException("Channels differ in length: " + left.length + " and " + right.length);
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        this.sampleRate = sampleRate;
    }

    public float[] getLeft() {
        return left;
    }

    public float[] getRight() {
        return right;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    /** Number of frames. */
    public int getLength() {
        return left.length;
    }

    public boolean isMono() {
        return left == right;
    }

    public float getDurationSeconds() {
        return (float) left.length / sampleRate;
    }

    /** Largest absolute value, 0 for silence. */
    public float getPeak() {
        float peak = 0f;
        for (int i = 0; i < left.length; i++) {
            peak = Math.max(peak, Math.max(Math.abs(left[i]), Math.abs(right[i])));
        }
        return peak;
    }
}
