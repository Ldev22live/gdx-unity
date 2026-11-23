package io.github.ldev22live.gdxunity.engine.sprite;

import java.util.Objects;

/**
 * A named rectangle of the canvas: a region for a UI frame (with optional 9-slice borders) and a pivot.
 *
 * @param name   slice name
 * @param x      left edge in pixels
 * @param y      top edge in pixels (y grows downward, as in the image)
 * @param width  width in pixels, at least 1
 * @param height height in pixels, at least 1
 * @param left   9-slice border: pixels of the left column (0 when not used)
 * @param right  9-slice border: pixels of the right column
 * @param top    9-slice border: pixels of the top row
 * @param bottom 9-slice border: pixels of the bottom row
 * @param pivotX pivot x relative to the slice's left edge
 * @param pivotY pivot y relative to the slice's top edge
 */
public record SpriteSlice(String name, int x, int y, int width, int height, int left, int right, int top, int bottom, int pivotX, int pivotY) {
    public SpriteSlice {
        Objects.requireNonNull(name, "name cannot be null");
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("slice size must be positive: " + width + "x" + height);
        }
        if (left < 0 || right < 0 || top < 0 || bottom < 0 || left + right > width || top + bottom > height) {
            throw new IllegalArgumentException("9-slice borders do not fit a " + width + "x" + height + " slice");
        }
    }

    /** A slice without borders, pivot at the top left. */
    public static SpriteSlice of(String name, int x, int y, int width, int height) {
        return new SpriteSlice(name, x, y, width, height, 0, 0, 0, 0, 0, 0);
    }

    /** Whether any 9-slice border is set. */
    public boolean hasNineSlice() {
        return left + right + top + bottom > 0;
    }
}
