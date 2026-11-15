package io.github.ldev22live.gdxunity.engine.terrain;

import java.util.Objects;

/**
 * What one stroke changed: for each touched grid point (an index into {@link Terrain}'s height array), its height before and after,
 * and for each point whose {@link Surface} changed, the surface code before and after (TD-083). Immutable; this is what the undo
 * step keeps.
 */
public record TerrainDelta(int[] indices, float[] before, float[] after, int[] surfaceIndices, byte[] surfaceBefore, byte[] surfaceAfter) {
    public TerrainDelta {
        Objects.requireNonNull(indices, "indices cannot be null");
        Objects.requireNonNull(before, "before cannot be null");
        Objects.requireNonNull(after, "after cannot be null");
        Objects.requireNonNull(surfaceIndices, "surfaceIndices cannot be null");
        Objects.requireNonNull(surfaceBefore, "surfaceBefore cannot be null");
        Objects.requireNonNull(surfaceAfter, "surfaceAfter cannot be null");
        if (before.length != indices.length || after.length != indices.length) {
            throw new IllegalArgumentException("A change needs one before and one after height per point: " + indices.length + ", "
                + before.length + ", " + after.length);
        }
        if (surfaceBefore.length != surfaceIndices.length || surfaceAfter.length != surfaceIndices.length) {
            throw new IllegalArgumentException("A change needs one before and one after surface per point: " + surfaceIndices.length + ", "
                + surfaceBefore.length + ", " + surfaceAfter.length);
        }
    }

    /** A change of heights only. */
    public TerrainDelta(int[] indices, float[] before, float[] after) {
        this(indices, before, after, new int[0], new byte[0], new byte[0]);
    }

    /** True when the stroke changed nothing. */
    public boolean isEmpty() {
        return indices.length == 0 && surfaceIndices.length == 0;
    }

    /** Number of grid points whose height changed. */
    public int size() {
        return indices.length;
    }

    /** Approximate memory this change holds, in bytes. */
    public long memoryBytes() {
        return 12L * indices.length + 6L * surfaceIndices.length;
    }
}
