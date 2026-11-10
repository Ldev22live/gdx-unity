package io.github.ldev22live.gdxunity.engine.scene.audio;

import com.badlogic.gdx.math.Vector3;

import java.util.Objects;

/**
 * How a sound at a place is heard from another (Phase 20-C): the volume falls in a straight line from full at {@code minDistance}
 * to silent at {@code maxDistance}, and the pan follows how far to the listener's right (or left) the sound is, from -1 for
 * straight left to 1 for straight right. Only the left-right axis matters: a sound above, below or behind the listener is not
 * moved between the speakers by height or depth, and nothing is added for the speed of sound or the Doppler effect.
 */
public final class SpatialAudio {
    /** Distances closer than this to the listener count as the same place, so the pan does not jump around at zero. */
    private static final float SAME_PLACE = 1e-4f;

    /**
     * @param gain linear volume multiplier, 0 to 1
     * @param pan  -1 (left) to 1 (right)
     */
    public record Heard(float gain, float pan) {
    }

    private SpatialAudio() {
    }

    /**
     * @param listener    where the ears are
     * @param right       the direction of the listener's right ear (need not be a unit vector)
     * @param source      where the sound is
     * @param minDistance up to this far away the sound is at full volume
     * @param maxDistance this far away and beyond it is silent
     * @throws IllegalArgumentException if the distances are not 0 &le; min &lt; max, or {@code right} is the zero vector
     */
    public static Heard hear(Vector3 listener, Vector3 right, Vector3 source, float minDistance, float maxDistance) {
        Objects.requireNonNull(listener, "listener cannot be null");
        Objects.requireNonNull(right, "right cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        if (!(minDistance >= 0f && maxDistance > minDistance)) {
            throw new IllegalArgumentException("Distances must satisfy 0 <= min < max: " + minDistance + ", " + maxDistance);
        }
        if (right.isZero()) {
            throw new IllegalArgumentException("right cannot be the zero vector");
        }
        Vector3 offset = new Vector3(source).sub(listener);
        float distance = offset.len();
        float gain = distance <= minDistance ? 1f : distance >= maxDistance ? 0f : 1f - (distance - minDistance) / (maxDistance - minDistance);
        float pan = distance < SAME_PLACE ? 0f : offset.dot(new Vector3(right).nor()) / distance;
        return new Heard(gain, Math.max(-1f, Math.min(1f, pan)));
    }
}
