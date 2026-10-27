package io.github.ldev22live.gdxunity.engine.core.time;

import java.util.Objects;

/**
 * Tracks and clamps frame delta-time and accumulated running time.
 * Rejects negative, NaN, and infinite delta values to prevent contaminating engine state.
 */
public final class FrameClock {
    /** Default upper limit for delta time in seconds (1/10th second = 100ms). */
    public static final float DEFAULT_MAX_DELTA_TIME = 0.1f;

    private final TimeProvider timeProvider;
    private float maxDeltaTime;
    private float deltaTime;
    private float totalElapsedTime;
    private long frameCount;

    public FrameClock() {
        this(new LibGdxTimeProvider(), DEFAULT_MAX_DELTA_TIME);
    }

    public FrameClock(TimeProvider timeProvider) {
        this(timeProvider, DEFAULT_MAX_DELTA_TIME);
    }

    public FrameClock(TimeProvider timeProvider, float maxDeltaTime) {
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider cannot be null");
        setMaxDeltaTime(maxDeltaTime);
        this.deltaTime = 0.0f;
        this.totalElapsedTime = 0.0f;
        this.frameCount = 0L;
    }

    /**
     * Advances the clock by reading the current delta from the TimeProvider,
     * sanitizing it, clamping it against maxDeltaTime, and adding it to totalElapsedTime.
     *
     * @return the sanitized, clamped delta time for the current frame
     */
    public float tick() {
        float raw = timeProvider.getRawDeltaTime();
        return tick(raw);
    }

    /**
     * Advances the clock with an explicitly provided raw delta time value.
     *
     * @param rawDeltaTime raw delta time in seconds
     * @return the sanitized, clamped delta time for the current frame
     */
    public float tick(float rawDeltaTime) {
        float sanitized = sanitize(rawDeltaTime);
        float clamped = Math.min(sanitized, maxDeltaTime);
        this.deltaTime = clamped;
        this.totalElapsedTime += clamped;
        this.frameCount++;
        return clamped;
    }

    /**
     * Sanitizes raw delta values: converts negative, NaN, and positive infinity to 0.0f.
     */
    public static float sanitize(float raw) {
        if (Float.isNaN(raw) || Float.isInfinite(raw) || raw < 0.0f) {
            return 0.0f;
        }
        return raw;
    }

    /**
     * Resets the clock accumulators to zero.
     */
    public void reset() {
        this.deltaTime = 0.0f;
        this.totalElapsedTime = 0.0f;
        this.frameCount = 0L;
    }

    public float getDeltaTime() {
        return deltaTime;
    }

    public float getTotalElapsedTime() {
        return totalElapsedTime;
    }

    public long getFrameCount() {
        return frameCount;
    }

    public float getMaxDeltaTime() {
        return maxDeltaTime;
    }

    public void setMaxDeltaTime(float maxDeltaTime) {
        if (Float.isNaN(maxDeltaTime) || Float.isInfinite(maxDeltaTime) || maxDeltaTime <= 0.0f) {
            throw new IllegalArgumentException("maxDeltaTime must be a positive finite number, got: " + maxDeltaTime);
        }
        this.maxDeltaTime = maxDeltaTime;
    }
}
