package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import java.util.Objects;

/**
 * A grid of heights that a {@link TerrainCollider3DComponent} collides with (TD-087), in one unit throughout: scene units when a
 * {@link HeightFieldSource} returns it, meters once {@link PhysicsSystem3D} has scaled it for a backend. Point
 * ({@code column}, {@code row}) lies at x = {@code originX + column * cellSizeX}, z = {@code originZ + row * cellSizeZ}, height
 * {@code heights[row * columns + column]}, in the entity's local space. A height of {@link #NO_COLLISION} is a hole.
 *
 * @param columns   points along x, at least 2
 * @param rows      points along z, at least 2
 * @param cellSizeX distance between neighbouring points along x, more than 0
 * @param cellSizeZ distance between neighbouring points along z, more than 0
 * @param originX   x of column 0
 * @param originZ   z of row 0
 * @param heights   {@code columns * rows} heights, row by row; finite or {@link #NO_COLLISION}
 */
public record HeightField3D(int columns, int rows, float cellSizeX, float cellSizeZ, float originX, float originZ, float[] heights) {
    /** A point nothing collides with. */
    public static final float NO_COLLISION = Float.NaN;

    public HeightField3D {
        if (columns < 2 || rows < 2) {
            throw new IllegalArgumentException("A height field needs at least 2 by 2 points: " + columns + " by " + rows);
        }
        if (!(cellSizeX > 0f) || !(cellSizeZ > 0f) || Float.isInfinite(cellSizeX) || Float.isInfinite(cellSizeZ)) {
            throw new IllegalArgumentException("Cell sizes must be finite and more than 0: " + cellSizeX + ", " + cellSizeZ);
        }
        if (!Float.isFinite(originX) || !Float.isFinite(originZ)) {
            throw new IllegalArgumentException("The origin must be finite: " + originX + ", " + originZ);
        }
        Objects.requireNonNull(heights, "heights cannot be null");
        if (heights.length != (long) columns * rows) {
            throw new IllegalArgumentException("Expected " + (long) columns * rows + " heights, got " + heights.length);
        }
        heights = heights.clone();
        for (float height : heights) {
            if (Float.isInfinite(height)) {
                throw new IllegalArgumentException("Heights must be finite, or NO_COLLISION for a hole: " + height);
            }
        }
    }

    /** Height of a point, or {@link #NO_COLLISION}. */
    public float heightAt(int column, int row) {
        if (column < 0 || column >= columns || row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException("Point " + column + ", " + row + " is outside " + columns + " by " + rows);
        }
        return heights[row * columns + column];
    }

    /** Whether the point is a hole. */
    public static boolean isHole(float height) {
        return Float.isNaN(height);
    }

    /** The same grid with x, y and z multiplied by these factors (holes stay holes); factors must be more than 0. */
    public HeightField3D scaled(float sx, float sy, float sz) {
        if (!(sx > 0f) || !(sy > 0f) || !(sz > 0f)) {
            throw new IllegalArgumentException("Scale factors must be more than 0: " + sx + ", " + sy + ", " + sz);
        }
        float[] result = new float[heights.length];
        for (int i = 0; i < heights.length; i++) {
            result[i] = isHole(heights[i]) ? NO_COLLISION : heights[i] * sy;
        }
        return new HeightField3D(columns, rows, cellSizeX * sx, cellSizeZ * sz, originX * sx, originZ * sz, result);
    }

    @Override
    public float[] heights() {
        return heights.clone();
    }
}
