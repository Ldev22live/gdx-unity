package io.github.ldev22live.gdxunity.engine.sprite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Which frame of a sprite is showing at a given time, for a tag or the whole sprite (the game side of TD-066 milestone D reuses this). */
public final class SpriteAnimation {
    private SpriteAnimation() { }

    /**
     * The frames a tag plays in order: forward, reverse, or ping-pong (forward then back without repeating the end frames).
     * A null tag plays every frame forward.
     */
    public static List<Integer> sequence(SpriteDocument doc, SpriteTag tag) {
        Objects.requireNonNull(doc, "doc cannot be null");
        int from = tag == null ? 0 : tag.from();
        int to = tag == null ? doc.getFrameCount() - 1 : tag.to();
        if (to >= doc.getFrameCount()) {
            throw new IllegalArgumentException("tag ends at frame " + to + " but there are " + doc.getFrameCount() + " frames");
        }
        SpriteTag.Direction direction = tag == null ? SpriteTag.Direction.FORWARD : tag.direction();
        List<Integer> out = new ArrayList<>();
        if (direction == SpriteTag.Direction.REVERSE) {
            for (int f = to; f >= from; f--) {
                out.add(f);
            }
            return out;
        }
        for (int f = from; f <= to; f++) {
            out.add(f);
        }
        if (direction == SpriteTag.Direction.PING_PONG) {
            for (int f = to - 1; f > from; f--) {
                out.add(f);
            }
        }
        return out;
    }

    /**
     * The frame showing {@code millis} after the start of the animation.
     *
     * @param loop when false the last frame of the sequence stays once the end is reached
     */
    public static int frameAt(SpriteDocument doc, SpriteTag tag, long millis, boolean loop) {
        if (millis < 0) {
            throw new IllegalArgumentException("time cannot be negative: " + millis);
        }
        List<Integer> sequence = sequence(doc, tag);
        long total = 0;
        for (int frame : sequence) {
            total += doc.getFrameDuration(frame);
        }
        long t = loop ? millis % total : Math.min(millis, total - 1);
        for (int frame : sequence) {
            long duration = doc.getFrameDuration(frame);
            if (t < duration) {
                return frame;
            }
            t -= duration;
        }
        return sequence.get(sequence.size() - 1);
    }
}
