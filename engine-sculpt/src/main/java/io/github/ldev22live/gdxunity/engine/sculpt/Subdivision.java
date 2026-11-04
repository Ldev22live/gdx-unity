package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Raises the triangle count of a mesh so there is enough geometry to sculpt on. Each triangle is split into four by
 * connecting the midpoints of its edges. The new vertices lie on the old edges, so the shape does not change: a coarse
 * mesh stays faceted until it is sculpted or smoothed.
 */
public final class Subdivision {
    /** Most levels one call may apply (each level quadruples the triangle count). */
    public static final int MAX_LEVELS = 8;

    private Subdivision() {
    }

    /**
     * Returns a new mesh with every triangle split {@code levels} times. The original vertices keep their indices
     * and positions; new vertices are appended.
     *
     * @param levels from 1 to {@link #MAX_LEVELS}
     * @throws IllegalArgumentException if {@code levels} is out of range or the result would have more than
     *                                  {@link SculptMesh#MAX_TRIANGLES} triangles
     */
    public static SculptMesh midpoint(SculptMesh mesh, int levels) {
        Objects.requireNonNull(mesh, "mesh cannot be null");
        if (levels < 1 || levels > MAX_LEVELS) {
            throw new IllegalArgumentException("levels must be between 1 and " + MAX_LEVELS + ": " + levels);
        }
        long resultTriangles = (long) mesh.getTriangleCount() << (2 * levels);
        if (resultTriangles > SculptMesh.MAX_TRIANGLES) {
            throw new IllegalArgumentException("Subdividing " + mesh.getTriangleCount() + " triangles " + levels
                + " time(s) would make " + resultTriangles + " triangles; the limit is " + SculptMesh.MAX_TRIANGLES);
        }
        SculptMesh current = mesh;
        for (int level = 0; level < levels; level++) {
            current = splitOnce(current);
        }
        return current;
    }

    private static SculptMesh splitOnce(SculptMesh mesh) {
        float[] oldPositions = mesh.getPositions();
        int[] oldTriangles = mesh.getTriangles();
        FloatArray positions = new FloatArray(oldPositions.length * 2);
        positions.addAll(oldPositions, 0, oldPositions.length);
        IntArray triangles = new IntArray(oldTriangles.length * 4);
        Map<Long, Integer> midpoints = new HashMap<>();
        for (int t = 0; t < oldTriangles.length; t += 3) {
            int a = oldTriangles[t];
            int b = oldTriangles[t + 1];
            int c = oldTriangles[t + 2];
            int ab = midpoint(positions, midpoints, a, b);
            int bc = midpoint(positions, midpoints, b, c);
            int ca = midpoint(positions, midpoints, c, a);
            add(triangles, a, ab, ca);
            add(triangles, b, bc, ab);
            add(triangles, c, ca, bc);
            add(triangles, ab, bc, ca);
        }
        return new SculptMesh(positions.toArray(), triangles.toArray());
    }

    private static void add(IntArray triangles, int a, int b, int c) {
        triangles.add(a);
        triangles.add(b);
        triangles.add(c);
    }

    /** The vertex halfway along the edge between {@code a} and {@code b}, created on first use. */
    private static int midpoint(FloatArray positions, Map<Long, Integer> midpoints, int a, int b) {
        long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
        Integer existing = midpoints.get(key);
        if (existing != null) {
            return existing;
        }
        int index = positions.size / 3;
        float[] p = positions.items;
        positions.add((p[3 * a] + p[3 * b]) * 0.5f);
        positions.add((p[3 * a + 1] + p[3 * b + 1]) * 0.5f);
        positions.add((p[3 * a + 2] + p[3 * b + 2]) * 0.5f);
        midpoints.put(key, index);
        return index;
    }
}
