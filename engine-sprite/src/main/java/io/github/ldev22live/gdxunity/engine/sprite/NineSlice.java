package io.github.ldev22live.gdxunity.engine.sprite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The nine pieces of a 9-slice drawing (TD-066): a {@link SpriteSlice} with borders, stretched to any size so that the corners keep their pixels, the edges
 * stretch along one direction and the middle in both. Pure geometry, so the drawing code and the tests share it.
 */
public final class NineSlice {
    private NineSlice() { }

    /**
     * One piece: where it is read from in the frame ({@code src}, pixels, y down) and where it is drawn ({@code dst}, scene units from the bottom left corner of the
     * drawing, y up).
     */
    public record Patch(int srcX, int srcY, int srcWidth, int srcHeight, float dstX, float dstY, float dstWidth, float dstHeight) { }

    /**
     * The pieces for drawing {@code slice} at {@code width} x {@code height}. A piece with no area (a zero-width border, or a size too small for the borders) is left out;
     * when the size is smaller than the two borders together, the borders shrink in proportion so they never overlap.
     *
     * @throws IllegalArgumentException when the slice has no borders or the size is not above 0
     */
    public static List<Patch> patches(SpriteSlice slice, float width, float height) {
        Objects.requireNonNull(slice, "slice cannot be null");
        if (!slice.hasNineSlice()) {
            throw new IllegalArgumentException("slice " + slice.name() + " has no 9-slice borders");
        }
        if (!(width > 0f) || !(height > 0f)) {
            throw new IllegalArgumentException("a 9-slice size must be above 0 but was " + width + " x " + height);
        }
        float shrinkX = Math.min(1f, width / Math.max(1, slice.left() + slice.right()));
        float shrinkY = Math.min(1f, height / Math.max(1, slice.top() + slice.bottom()));
        float left = slice.left() * shrinkX;
        float right = slice.right() * shrinkX;
        float top = slice.top() * shrinkY;
        float bottom = slice.bottom() * shrinkY;
        int[] srcX = {slice.x(), slice.x() + slice.left(), slice.x() + slice.width() - slice.right()};
        int[] srcW = {slice.left(), slice.width() - slice.left() - slice.right(), slice.right()};
        int[] srcY = {slice.y(), slice.y() + slice.top(), slice.y() + slice.height() - slice.bottom()};
        int[] srcH = {slice.top(), slice.height() - slice.top() - slice.bottom(), slice.bottom()};
        float[] dstX = {0f, left, width - right};
        float[] dstW = {left, width - left - right, right};
        // Rows from the top of the drawing down; the drawing's y grows upward
        float[] dstTop = {height, height - top, bottom};
        float[] dstH = {top, height - top - bottom, bottom};
        List<Patch> out = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (srcW[col] <= 0 || srcH[row] <= 0 || dstW[col] <= 0f || dstH[row] <= 0f) {
                    continue;
                }
                out.add(new Patch(srcX[col], srcY[row], srcW[col], srcH[row], dstX[col], dstTop[row] - dstH[row], dstW[col], dstH[row]));
            }
        }
        return out;
    }
}
