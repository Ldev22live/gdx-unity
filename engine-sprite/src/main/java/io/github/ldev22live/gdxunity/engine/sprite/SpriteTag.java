package io.github.ldev22live.gdxunity.engine.sprite;

import java.util.Objects;

/**
 * A named range of frames (an animation such as "walk").
 *
 * @param name      tag name, not blank
 * @param from      first frame index, inclusive
 * @param to        last frame index, inclusive, not before {@code from}
 * @param direction how the range is played
 */
public record SpriteTag(String name, int from, int to, Direction direction) {
    /** How a tag's frames are played. */
    public enum Direction { FORWARD, REVERSE, PING_PONG }

    public SpriteTag {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(direction, "direction cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("tag name cannot be blank");
        }
        if (from < 0 || to < from) {
            throw new IllegalArgumentException("tag range is invalid: " + from + ".." + to);
        }
    }
}
