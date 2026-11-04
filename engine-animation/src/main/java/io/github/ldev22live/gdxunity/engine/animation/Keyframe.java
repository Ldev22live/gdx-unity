package io.github.ldev22live.gdxunity.engine.animation;

import java.util.Objects;

/**
 * A value at a time on an {@link AnimationCurve}. Immutable: edits replace the key.
 *
 * @param time          seconds from the start of the clip, not negative
 * @param value         the property's value at {@code time}
 * @param interpolation how the curve continues to the next key
 */
public record Keyframe(float time, float value, Interpolation interpolation) {
    public Keyframe {
        if (!Float.isFinite(time) || time < 0f) {
            throw new IllegalArgumentException("Key time must be finite and not negative, got: " + time);
        }
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Key value must be finite, got: " + value);
        }
        Objects.requireNonNull(interpolation, "interpolation cannot be null");
    }

    public Keyframe withTime(float newTime) {
        return new Keyframe(newTime, value, interpolation);
    }

    public Keyframe withValue(float newValue) {
        return new Keyframe(time, newValue, interpolation);
    }

    public Keyframe withInterpolation(Interpolation newInterpolation) {
        return new Keyframe(time, value, newInterpolation);
    }
}
