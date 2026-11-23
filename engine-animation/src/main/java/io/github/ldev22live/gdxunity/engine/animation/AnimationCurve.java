package io.github.ldev22live.gdxunity.engine.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Keys of one animated value, kept sorted by time, at most one per time. Before the first key the curve holds the
 * first value, after the last key the last value.
 */
public class AnimationCurve {

    /** Keys closer together than this (seconds) are the same key; about a hundredth of a frame at 60 fps. */
    public static final float TIME_EPSILON = 1e-4f;

    private final List<Keyframe> keys = new ArrayList<>();

    public AnimationCurve() {
    }

    public AnimationCurve(List<Keyframe> keys) {
        for (Keyframe key : keys) {
            setKey(key);
        }
    }

    /** Sorted keys; read-only view. */
    public List<Keyframe> getKeys() {
        return Collections.unmodifiableList(keys);
    }

    public int size() {
        return keys.size();
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public Keyframe getKey(int index) {
        return keys.get(index);
    }

    /** Index of the key at {@code time} (within {@link #TIME_EPSILON}), or -1. */
    public int indexAt(float time) {
        for (int i = 0; i < keys.size(); i++) {
            if (Math.abs(keys.get(i).time() - time) <= TIME_EPSILON) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Adds {@code key}, replacing a key at the same time.
     *
     * @return the key it replaced, or null
     */
    public Keyframe setKey(Keyframe key) {
        Objects.requireNonNull(key, "key cannot be null");
        int existing = indexAt(key.time());
        if (existing >= 0) {
            return keys.set(existing, key);
        }
        int insertAt = 0;
        while (insertAt < keys.size() && keys.get(insertAt).time() < key.time()) {
            insertAt++;
        }
        keys.add(insertAt, key);
        return null;
    }

    /** Replaces all keys with {@code newKeys} (sorted; later keys at the same time win). */
    public void replaceKeys(List<Keyframe> newKeys) {
        Objects.requireNonNull(newKeys, "newKeys cannot be null");
        keys.clear();
        for (Keyframe key : newKeys) {
            setKey(key);
        }
    }

    /** Removes the key at {@code time}; returns it, or null if there is none. */
    public Keyframe removeKeyAt(float time) {
        int index = indexAt(time);
        return index >= 0 ? keys.remove(index) : null;
    }

    /** Time of the last key, or 0 for an empty curve. */
    public float getEndTime() {
        return keys.isEmpty() ? 0f : keys.get(keys.size() - 1).time();
    }

    /**
     * Value at {@code time}.
     *
     * @throws IllegalStateException if the curve has no keys
     */
    public float evaluate(float time) {
        if (keys.isEmpty()) {
            throw new IllegalStateException("Cannot evaluate a curve without keys");
        }
        Keyframe first = keys.get(0);
        if (time <= first.time()) {
            return first.value();
        }
        int last = keys.size() - 1;
        if (time >= keys.get(last).time()) {
            return keys.get(last).value();
        }
        int i = 0;
        while (keys.get(i + 1).time() < time) {
            i++;
        }
        Keyframe from = keys.get(i);
        Keyframe to = keys.get(i + 1);
        float span = to.time() - from.time();
        float s = (time - from.time()) / span;
        return switch (from.interpolation()) {
            case CONSTANT -> from.value();
            case LINEAR -> from.value() + (to.value() - from.value()) * s;
            case BEZIER -> hermite(from.value(), slopeAt(i) * span, to.value(), slopeAt(i + 1) * span, s);
        };
    }

    /** Automatic tangent at key {@code i}, in value per second: flat at both ends, through the neighbours inside. */
    float slopeAt(int i) {
        if (i <= 0 || i >= keys.size() - 1) {
            return 0f;
        }
        Keyframe before = keys.get(i - 1);
        Keyframe after = keys.get(i + 1);
        return (after.value() - before.value()) / (after.time() - before.time());
    }

    /** Cubic Hermite segment; equivalent to a Bezier with handles at a third of the segment along the tangents. */
    private static float hermite(float p0, float m0, float p1, float m1, float s) {
        float s2 = s * s;
        float s3 = s2 * s;
        return (2f * s3 - 3f * s2 + 1f) * p0
            + (s3 - 2f * s2 + s) * m0
            + (-2f * s3 + 3f * s2) * p1
            + (s3 - s2) * m1;
    }

    public AnimationCurve copy() {
        return new AnimationCurve(keys);
    }
}
