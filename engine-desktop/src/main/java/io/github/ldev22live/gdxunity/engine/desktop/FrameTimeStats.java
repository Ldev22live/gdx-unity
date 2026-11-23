package io.github.ldev22live.gdxunity.engine.desktop;

import java.util.Arrays;

/**
 * Frame times of one run (roadmap M9.5), kept individually so the median and the 95th percentile can be reported, not just
 * the mean: a game that stutters can have a good mean. The samples array is allocated once, so recording a frame allocates
 * nothing. Frames past the capacity still count towards the mean and the maximum, but not the percentiles; {@link #dropped()}
 * says how many.
 */
public final class FrameTimeStats {
    private final long[] samples;
    private int stored;
    private int dropped;
    private long totalNanos;
    private long maxNanos;

    /** @param capacity frames whose times are kept for the percentiles; at least 1 */
    public FrameTimeStats(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1: " + capacity);
        }
        this.samples = new long[capacity];
    }

    /** Records one frame's time. */
    public void add(long nanos) {
        if (nanos < 0) {
            throw new IllegalArgumentException("nanos must not be negative: " + nanos);
        }
        if (stored < samples.length) {
            samples[stored++] = nanos;
        } else {
            dropped++;
        }
        totalNanos += nanos;
        maxNanos = Math.max(maxNanos, nanos);
    }

    /** Frames recorded, including dropped ones. */
    public int count() {
        return stored + dropped;
    }

    /** Frames recorded past the capacity, left out of the percentiles. */
    public int dropped() {
        return dropped;
    }

    /** Mean frame time in milliseconds, or NaN with no frames. */
    public double meanMillis() {
        return count() == 0 ? Double.NaN : totalNanos / 1e6 / count();
    }

    /** The longest frame in milliseconds, or NaN with no frames. */
    public double maxMillis() {
        return count() == 0 ? Double.NaN : maxNanos / 1e6;
    }

    /**
     * The frame time below or at which {@code percent} of the kept frames fall (nearest rank), in milliseconds; NaN with no
     * frames. 50 is the median.
     *
     * @param percent from 0 (exclusive) to 100
     */
    public double percentileMillis(double percent) {
        if (!(percent > 0 && percent <= 100)) {
            throw new IllegalArgumentException("percent must be in (0, 100]: " + percent);
        }
        if (stored == 0) {
            return Double.NaN;
        }
        long[] sorted = Arrays.copyOf(samples, stored);
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(percent / 100.0 * stored);
        return sorted[Math.max(rank, 1) - 1] / 1e6;
    }
}
