package io.github.ldev22live.gdxunity.engine.sculpt;

import java.util.Objects;

/**
 * The effect of one stroke: every vertex it touched with its position before and after. Applying it with
 * {@link SculptSurface#apply} in either direction is exact, which is what undo and redo need. It stores positions,
 * not brush parameters, so it stays valid however the stroke was made.
 */
public final class StrokeDelta {
    private final int[] vertices;
    private final float[] before;
    private final float[] after;

    /**
     * @param vertices touched vertex indices
     * @param before   x, y, z before the stroke for each vertex
     * @param after    x, y, z after the stroke for each vertex
     * @throws IllegalArgumentException if the array lengths do not match
     */
    public StrokeDelta(int[] vertices, float[] before, float[] after) {
        Objects.requireNonNull(vertices, "vertices cannot be null");
        Objects.requireNonNull(before, "before cannot be null");
        Objects.requireNonNull(after, "after cannot be null");
        if (before.length != 3 * vertices.length || after.length != 3 * vertices.length) {
            throw new IllegalArgumentException("before and after need 3 floats per vertex: " + vertices.length
                + " vertices, " + before.length + " and " + after.length + " floats");
        }
        this.vertices = vertices.clone();
        this.before = before.clone();
        this.after = after.clone();
    }

    /** Number of vertices the stroke touched; 0 means the stroke changed nothing. */
    public int size() {
        return vertices.length;
    }

    /** True if the stroke touched no vertex, so there is nothing to undo. */
    public boolean isEmpty() {
        return vertices.length == 0;
    }

    /** The touched vertex indices (do not modify). */
    int[] vertices() {
        return vertices;
    }

    /** Positions before the stroke (do not modify). */
    float[] before() {
        return before;
    }

    /** Positions after the stroke (do not modify). */
    float[] after() {
        return after;
    }

    /** Approximate memory held, in bytes, for undo history limits. */
    public long memoryBytes() {
        return 4L * vertices.length + 4L * before.length + 4L * after.length;
    }
}
