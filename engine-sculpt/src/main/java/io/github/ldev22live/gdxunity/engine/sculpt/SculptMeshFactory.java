package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Creates {@link SculptMesh} instances: a sphere to start from, and welding of meshes with split vertices. */
public final class SculptMeshFactory {
    /** Largest cube-sphere resolution: 12 * 400 * 400 = 1.92 million triangles. */
    public static final int MAX_CUBE_SPHERE_RESOLUTION = 400;

    private SculptMeshFactory() {
    }

    /**
     * A sphere made from a cube whose six faces are each a grid of {@code resolution x resolution} quads pushed out
     * onto the sphere. Its triangles are about the same size everywhere, unlike a latitude/longitude sphere which
     * bunches up at the poles, so brushes behave the same on all sides.
     *
     * <p>It has {@code 6 * resolution^2 + 2} vertices and {@code 12 * resolution^2} triangles, centred on the origin.
     *
     * @param radius     sphere radius, greater than 0
     * @param resolution quads per cube edge, from 1 to {@link #MAX_CUBE_SPHERE_RESOLUTION}
     */
    public static SculptMesh cubeSphere(float radius, int resolution) {
        if (!(radius > 0f) || !Float.isFinite(radius)) {
            throw new IllegalArgumentException("radius must be a finite number greater than 0: " + radius);
        }
        if (resolution < 1 || resolution > MAX_CUBE_SPHERE_RESOLUTION) {
            throw new IllegalArgumentException("resolution must be between 1 and " + MAX_CUBE_SPHERE_RESOLUTION
                + ": " + resolution);
        }
        int side = resolution + 1;
        // Vertices are keyed by their integer position on the cube so the six faces share their edge vertices exactly
        Map<Integer, Integer> vertexOfLattice = new HashMap<>();
        FloatArray positions = new FloatArray(3 * (6 * resolution * resolution + 2));
        IntArray triangles = new IntArray(36 * resolution * resolution);
        // For each face: the axis that is fixed, its value (0 or resolution), and the two axes that vary
        int[][] faces = {
            {0, resolution, 1, 2}, {0, 0, 1, 2},
            {1, resolution, 0, 2}, {1, 0, 0, 2},
            {2, resolution, 0, 1}, {2, 0, 0, 1},
        };
        int[] lattice = new int[3];
        for (int[] face : faces) {
            for (int a = 0; a < resolution; a++) {
                for (int b = 0; b < resolution; b++) {
                    int[] quad = new int[4];
                    for (int c = 0; c < 4; c++) {
                        lattice[face[0]] = face[1];
                        lattice[face[2]] = a + (c & 1);
                        lattice[face[3]] = b + (c >> 1);
                        int key = (lattice[0] * side + lattice[1]) * side + lattice[2];
                        Integer existing = vertexOfLattice.get(key);
                        if (existing == null) {
                            existing = positions.size / 3;
                            vertexOfLattice.put(key, existing);
                            addSpherePoint(positions, lattice, resolution, radius);
                        }
                        quad[c] = existing;
                    }
                    addOutwardTriangle(positions, triangles, quad[0], quad[1], quad[3]);
                    addOutwardTriangle(positions, triangles, quad[0], quad[3], quad[2]);
                }
            }
        }
        return new SculptMesh(positions.toArray(), triangles.toArray());
    }

    /** Maps a point of the integer cube lattice onto the sphere with the mapping that keeps cells evenly sized. */
    private static void addSpherePoint(FloatArray positions, int[] lattice, int resolution, float radius) {
        float x = 2f * lattice[0] / resolution - 1f;
        float y = 2f * lattice[1] / resolution - 1f;
        float z = 2f * lattice[2] / resolution - 1f;
        float x2 = x * x;
        float y2 = y * y;
        float z2 = z * z;
        positions.add(radius * x * (float) Math.sqrt(1f - y2 / 2f - z2 / 2f + y2 * z2 / 3f));
        positions.add(radius * y * (float) Math.sqrt(1f - z2 / 2f - x2 / 2f + z2 * x2 / 3f));
        positions.add(radius * z * (float) Math.sqrt(1f - x2 / 2f - y2 / 2f + x2 * y2 / 3f));
    }

    /** Adds the triangle with the winding that makes it face away from the origin. */
    private static void addOutwardTriangle(FloatArray positions, IntArray triangles, int a, int b, int c) {
        float[] p = positions.items;
        float e1x = p[3 * b] - p[3 * a];
        float e1y = p[3 * b + 1] - p[3 * a + 1];
        float e1z = p[3 * b + 2] - p[3 * a + 2];
        float e2x = p[3 * c] - p[3 * a];
        float e2y = p[3 * c + 1] - p[3 * a + 1];
        float e2z = p[3 * c + 2] - p[3 * a + 2];
        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;
        float cx = p[3 * a] + p[3 * b] + p[3 * c];
        float cy = p[3 * a + 1] + p[3 * b + 1] + p[3 * c + 1];
        float cz = p[3 * a + 2] + p[3 * b + 2] + p[3 * c + 2];
        boolean outward = nx * cx + ny * cy + nz * cz >= 0f;
        triangles.add(a);
        triangles.add(outward ? b : c);
        triangles.add(outward ? c : b);
    }

    /**
     * Builds a mesh from triangles with split vertices, for example an imported model whose UV seams and hard edges
     * duplicate positions. Vertices at the same position (within {@code tolerance}) become one vertex, and triangles
     * that collapse to a line or a point are dropped, together with vertices only they used.
     *
     * <p>Positions are merged by rounding to a grid of {@code tolerance}, so two points a little closer than that can
     * stay separate if they fall on opposite sides of a grid line. Use it for duplicates, not for fuzzy merging.
     *
     * @param positions x, y, z per vertex
     * @param triangles three vertex indices per triangle
     * @param tolerance grid size, greater than 0, for example {@code 1e-5f} times the model size
     * @throws IllegalArgumentException if an index is out of range or nothing is left after welding
     */
    public static SculptMesh weld(float[] positions, int[] triangles, float tolerance) {
        Objects.requireNonNull(positions, "positions cannot be null");
        Objects.requireNonNull(triangles, "triangles cannot be null");
        if (!(tolerance > 0f) || !Float.isFinite(tolerance)) {
            throw new IllegalArgumentException("tolerance must be a finite number greater than 0: " + tolerance);
        }
        if (positions.length % 3 != 0 || triangles.length % 3 != 0) {
            throw new IllegalArgumentException("positions and triangles must have lengths that are multiples of 3: "
                + positions.length + ", " + triangles.length);
        }
        int vertexCount = positions.length / 3;
        for (int i = 0; i < triangles.length; i++) {
            if (triangles[i] < 0 || triangles[i] >= vertexCount) {
                throw new IllegalArgumentException("Triangle " + (i / 3) + " uses vertex " + triangles[i]
                    + " but there are " + vertexCount + " vertices");
            }
        }
        Map<String, Integer> merged = new HashMap<>();
        int[] remap = new int[vertexCount];
        FloatArray welded = new FloatArray();
        for (int v = 0; v < vertexCount; v++) {
            long qx = Math.round(positions[3 * v] / (double) tolerance);
            long qy = Math.round(positions[3 * v + 1] / (double) tolerance);
            long qz = Math.round(positions[3 * v + 2] / (double) tolerance);
            String key = qx + "," + qy + "," + qz;
            Integer index = merged.get(key);
            if (index == null) {
                index = welded.size / 3;
                merged.put(key, index);
                welded.add(positions[3 * v]);
                welded.add(positions[3 * v + 1]);
                welded.add(positions[3 * v + 2]);
            }
            remap[v] = index;
        }
        IntArray kept = new IntArray();
        for (int t = 0; t < triangles.length / 3; t++) {
            int a = remap[triangles[3 * t]];
            int b = remap[triangles[3 * t + 1]];
            int c = remap[triangles[3 * t + 2]];
            if (a != b && b != c && a != c) {
                kept.add(a);
                kept.add(b);
                kept.add(c);
            }
        }
        // Drop vertices that only belonged to dropped triangles, so the mesh has no unused vertices
        int[] compact = new int[welded.size / 3];
        Arrays.fill(compact, -1);
        FloatArray usedPositions = new FloatArray();
        for (int i = 0; i < kept.size; i++) {
            int v = kept.items[i];
            if (compact[v] < 0) {
                compact[v] = usedPositions.size / 3;
                usedPositions.add(welded.items[3 * v]);
                usedPositions.add(welded.items[3 * v + 1]);
                usedPositions.add(welded.items[3 * v + 2]);
            }
            kept.items[i] = compact[v];
        }
        return new SculptMesh(usedPositions.toArray(), kept.toArray());
    }
}
