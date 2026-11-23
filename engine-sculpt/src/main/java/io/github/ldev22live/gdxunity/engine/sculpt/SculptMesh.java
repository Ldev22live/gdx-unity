package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.IntArray;

import java.util.Arrays;
import java.util.Objects;

/**
 * An indexed triangle mesh that brushes edit (TD-038). Triangles are fixed when the mesh is created; only vertex
 * positions change while sculpting. The mesh keeps vertex normals up to date and has the adjacency (vertex to
 * triangles, vertex to neighbouring vertices) that brushes and normal updates need.
 *
 * <p>Vertices must be shared between triangles ({@link SculptMeshFactory#weld} merges duplicates): a mesh with
 * split vertices cracks open when one side of a seam is moved. Triangles are counter-clockwise seen from outside.
 *
 * <p>Not thread-safe. The array getters return the live arrays for fast access and GPU upload: read them, and
 * change positions only through {@link SculptSurface} so normals and the {@link TriangleBvh} stay correct.
 */
public final class SculptMesh {
    /** Largest triangle count accepted, so a corrupt file or a mistaken subdivision cannot exhaust memory. */
    public static final int MAX_TRIANGLES = 8_000_000;

    /** Normals shorter than this (isolated or flat-degenerate vertices) fall back to {@link #FALLBACK_NORMAL_Y}. */
    private static final float MIN_NORMAL_LENGTH = 1e-20f;
    /** Y component of the normal given to a vertex that no triangle uses or whose triangles have no area. */
    private static final float FALLBACK_NORMAL_Y = 1f;

    private final float[] positions;
    private final float[] normals;
    private final int[] triangles;
    /** Triangles using vertex v: {@code vertexTriangles[vertexTriangleStart[v] .. vertexTriangleStart[v + 1])}. */
    private final int[] vertexTriangleStart;
    private final int[] vertexTriangles;
    /** Vertices sharing a triangle edge with vertex v, without duplicates. */
    private final int[] neighborStart;
    private final int[] neighbors;

    /**
     * Creates a mesh from copies of the given arrays and computes its normals.
     *
     * @param positions x, y, z per vertex
     * @param triangles three vertex indices per triangle
     * @throws IllegalArgumentException if an array has the wrong length, a coordinate is not finite, an index is out
     *                                  of range, a triangle repeats a vertex, or there are too many triangles
     */
    public SculptMesh(float[] positions, int[] triangles) {
        Objects.requireNonNull(positions, "positions cannot be null");
        Objects.requireNonNull(triangles, "triangles cannot be null");
        if (positions.length % 3 != 0) {
            throw new IllegalArgumentException("positions length must be a multiple of 3: " + positions.length);
        }
        if (triangles.length % 3 != 0) {
            throw new IllegalArgumentException("triangles length must be a multiple of 3: " + triangles.length);
        }
        int vertexCount = positions.length / 3;
        int triangleCount = triangles.length / 3;
        if (vertexCount == 0 || triangleCount == 0) {
            throw new IllegalArgumentException("A sculpt mesh needs at least one triangle");
        }
        if (triangleCount > MAX_TRIANGLES) {
            throw new IllegalArgumentException("A sculpt mesh has at most " + MAX_TRIANGLES + " triangles, got "
                + triangleCount);
        }
        for (int i = 0; i < positions.length; i++) {
            if (!Float.isFinite(positions[i])) {
                throw new IllegalArgumentException("Vertex " + (i / 3) + " has a coordinate that is not finite: "
                    + positions[i]);
            }
        }
        for (int t = 0; t < triangleCount; t++) {
            int a = triangles[3 * t];
            int b = triangles[3 * t + 1];
            int c = triangles[3 * t + 2];
            for (int index : new int[] {a, b, c}) {
                if (index < 0 || index >= vertexCount) {
                    throw new IllegalArgumentException("Triangle " + t + " uses vertex " + index + " but the mesh has "
                        + vertexCount + " vertices");
                }
            }
            if (a == b || b == c || a == c) {
                throw new IllegalArgumentException("Triangle " + t + " repeats a vertex: " + a + ", " + b + ", " + c);
            }
        }
        this.positions = positions.clone();
        this.triangles = triangles.clone();
        this.normals = new float[positions.length];

        vertexTriangleStart = new int[vertexCount + 1];
        for (int corner : this.triangles) {
            vertexTriangleStart[corner + 1]++;
        }
        for (int v = 0; v < vertexCount; v++) {
            vertexTriangleStart[v + 1] += vertexTriangleStart[v];
        }
        vertexTriangles = new int[this.triangles.length];
        int[] fill = Arrays.copyOf(vertexTriangleStart, vertexCount);
        for (int t = 0; t < triangleCount; t++) {
            for (int corner = 0; corner < 3; corner++) {
                vertexTriangles[fill[this.triangles[3 * t + corner]]++] = t;
            }
        }

        // Neighbours: first count the distinct vertices sharing a triangle with v, then fill them in
        int[] mark = new int[vertexCount];
        Arrays.fill(mark, -1);
        neighborStart = new int[vertexCount + 1];
        for (int v = 0; v < vertexCount; v++) {
            int count = 0;
            for (int k = vertexTriangleStart[v]; k < vertexTriangleStart[v + 1]; k++) {
                int t = vertexTriangles[k];
                for (int corner = 0; corner < 3; corner++) {
                    int u = this.triangles[3 * t + corner];
                    if (u != v && mark[u] != v) {
                        mark[u] = v;
                        count++;
                    }
                }
            }
            neighborStart[v + 1] = neighborStart[v] + count;
        }
        neighbors = new int[neighborStart[vertexCount]];
        Arrays.fill(mark, -1);
        for (int v = 0; v < vertexCount; v++) {
            int next = neighborStart[v];
            for (int k = vertexTriangleStart[v]; k < vertexTriangleStart[v + 1]; k++) {
                int t = vertexTriangles[k];
                for (int corner = 0; corner < 3; corner++) {
                    int u = this.triangles[3 * t + corner];
                    if (u != v && mark[u] != v) {
                        mark[u] = v;
                        neighbors[next++] = u;
                    }
                }
            }
        }
        recomputeNormals();
    }

    /** Number of vertices. */
    public int getVertexCount() {
        return positions.length / 3;
    }

    /** Number of triangles. */
    public int getTriangleCount() {
        return triangles.length / 3;
    }

    /** The live position array (x, y, z per vertex). Change it only through {@link SculptSurface}. */
    public float[] getPositions() {
        return positions;
    }

    /** The live vertex normal array (unit length, x, y, z per vertex). */
    public float[] getNormals() {
        return normals;
    }

    /** The live triangle index array (three vertex indices per triangle). Never modified after creation. */
    public int[] getTriangles() {
        return triangles;
    }

    /** First index in {@link #getVertexTriangles()} of the triangles using vertex {@code vertex}. */
    public int getVertexTriangleStart(int vertex) {
        return vertexTriangleStart[vertex];
    }

    /** One past the last index in {@link #getVertexTriangles()} of the triangles using vertex {@code vertex}. */
    public int getVertexTriangleEnd(int vertex) {
        return vertexTriangleStart[vertex + 1];
    }

    /** Triangle indices grouped by vertex; see {@link #getVertexTriangleStart(int)}. */
    public int[] getVertexTriangles() {
        return vertexTriangles;
    }

    /** First index in {@link #getNeighbors()} of the neighbours of vertex {@code vertex}. */
    public int getNeighborStart(int vertex) {
        return neighborStart[vertex];
    }

    /** One past the last index in {@link #getNeighbors()} of the neighbours of vertex {@code vertex}. */
    public int getNeighborEnd(int vertex) {
        return neighborStart[vertex + 1];
    }

    /** Neighbouring vertex indices grouped by vertex; see {@link #getNeighborStart(int)}. */
    public int[] getNeighbors() {
        return neighbors;
    }

    /** Copies the position of a vertex into {@code out} and returns it. */
    public Vector3 getPosition(int vertex, Vector3 out) {
        return out.set(positions[3 * vertex], positions[3 * vertex + 1], positions[3 * vertex + 2]);
    }

    /** Copies the normal of a vertex into {@code out} and returns it. */
    public Vector3 getNormal(int vertex, Vector3 out) {
        return out.set(normals[3 * vertex], normals[3 * vertex + 1], normals[3 * vertex + 2]);
    }

    /** Sets the box to the bounds of all vertices and returns it. */
    public BoundingBox computeBounds(BoundingBox out) {
        out.inf();
        for (int i = 0; i < positions.length; i += 3) {
            out.ext(positions[i], positions[i + 1], positions[i + 2]);
        }
        return out;
    }

    /** Recomputes every vertex normal from the current positions (area-weighted average of the face normals). */
    public void recomputeNormals() {
        for (int v = 0; v < getVertexCount(); v++) {
            computeNormal(v);
        }
    }

    /** Recomputes the normals of the listed vertices only. */
    public void recomputeNormals(IntArray vertices) {
        for (int i = 0; i < vertices.size; i++) {
            computeNormal(vertices.items[i]);
        }
    }

    private void computeNormal(int v) {
        float nx = 0f;
        float ny = 0f;
        float nz = 0f;
        for (int k = vertexTriangleStart[v]; k < vertexTriangleStart[v + 1]; k++) {
            int t = vertexTriangles[k];
            int a = 3 * triangles[3 * t];
            int b = 3 * triangles[3 * t + 1];
            int c = 3 * triangles[3 * t + 2];
            float e1x = positions[b] - positions[a];
            float e1y = positions[b + 1] - positions[a + 1];
            float e1z = positions[b + 2] - positions[a + 2];
            float e2x = positions[c] - positions[a];
            float e2y = positions[c + 1] - positions[a + 1];
            float e2z = positions[c + 2] - positions[a + 2];
            // The cross product's length is twice the triangle area, so larger faces weigh more
            nx += e1y * e2z - e1z * e2y;
            ny += e1z * e2x - e1x * e2z;
            nz += e1x * e2y - e1y * e2x;
        }
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < MIN_NORMAL_LENGTH) {
            normals[3 * v] = 0f;
            normals[3 * v + 1] = FALLBACK_NORMAL_Y;
            normals[3 * v + 2] = 0f;
        } else {
            normals[3 * v] = nx / length;
            normals[3 * v + 1] = ny / length;
            normals[3 * v + 2] = nz / length;
        }
    }
}
