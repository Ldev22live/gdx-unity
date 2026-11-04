package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.IntArray;

import java.util.Arrays;
import java.util.Objects;

/**
 * A bounding volume hierarchy over the triangles of a {@link SculptMesh}, for picking the surface under the cursor
 * and for finding the vertices inside a brush sphere without testing every vertex. Vertices move while sculpting,
 * so the boxes are updated with {@link #refit()} or, for a brush dab, {@link #refit(int[], int)}, which only touches
 * the paths from the moved triangles to the root. The tree structure never changes.
 *
 * <p>Not thread-safe: queries use shared scratch space and allocate nothing.
 */
public final class TriangleBvh {
    /** A leaf holds at most this many triangles. */
    static final int LEAF_SIZE = 4;
    /** Deep enough for any tree built here: a balanced split halves the range each level. */
    private static final int STACK_SIZE = 128;
    /** Ray direction components smaller than this are replaced by it, so the slab test never divides by zero. */
    private static final float MIN_DIRECTION_COMPONENT = 1e-12f;
    /** Triangles whose ray determinant is smaller than this are parallel to the ray or have no area. */
    private static final float PARALLEL_EPSILON = 1e-12f;

    /** The nearest surface point a ray found. Reuse one instance across queries. */
    public static final class Hit {
        /** Index of the triangle that was hit, or -1 before any hit. */
        public int triangle = -1;
        /** Distance from the ray origin to the hit, in world units. */
        public float distance;
        /** The hit point. */
        public float x, y, z;
        /** The unit face normal of the triangle, turned to face the ray origin. */
        public float nx, ny, nz;
    }

    private final SculptMesh mesh;
    /** Triangle indices grouped by leaf. */
    private final int[] order;
    /** Six floats per node: min x, y, z then max x, y, z. */
    private final float[] bounds;
    private final int[] left;
    private final int[] right;
    /** For a leaf: the range of {@link #order} it holds. */
    private final int[] first;
    private final int[] count;
    private final int[] parent;
    private final int[] triangleLeaf;
    private int nodeCount;

    private final int[] stack = new int[STACK_SIZE];
    private final int[] vertexStamp;
    private int stamp;
    private final int[] nodeStamp;
    private int nodeStampCounter;
    private final IntArray dirtyNodes = new IntArray();

    /** Builds the hierarchy for the mesh's current positions. */
    public TriangleBvh(SculptMesh mesh) {
        this.mesh = Objects.requireNonNull(mesh, "mesh cannot be null");
        int triangleCount = mesh.getTriangleCount();
        // Leaves hold at least two triangles unless the tree is a single node, so there are fewer nodes than triangles
        int capacity = Math.max(1, triangleCount);
        bounds = new float[6 * capacity];
        left = new int[capacity];
        right = new int[capacity];
        first = new int[capacity];
        count = new int[capacity];
        parent = new int[capacity];
        nodeStamp = new int[capacity];
        order = new int[triangleCount];
        triangleLeaf = new int[triangleCount];
        vertexStamp = new int[mesh.getVertexCount()];
        build();
        refit();
    }

    private void build() {
        int triangleCount = order.length;
        float[] centroids = new float[3 * triangleCount];
        float[] p = mesh.getPositions();
        int[] tri = mesh.getTriangles();
        for (int t = 0; t < triangleCount; t++) {
            order[t] = t;
            for (int axis = 0; axis < 3; axis++) {
                centroids[3 * t + axis] = (p[3 * tri[3 * t] + axis] + p[3 * tri[3 * t + 1] + axis]
                    + p[3 * tri[3 * t + 2] + axis]) / 3f;
            }
        }
        nodeCount = 0;
        int root = newNode(-1, 0, triangleCount);
        IntArray pending = new IntArray();
        pending.add(root);
        while (pending.size > 0) {
            int node = pending.pop();
            int start = first[node];
            int size = count[node];
            if (size <= LEAF_SIZE) {
                continue;
            }
            float[] low = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
            float[] high = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            for (int i = start; i < start + size; i++) {
                for (int axis = 0; axis < 3; axis++) {
                    float c = centroids[3 * order[i] + axis];
                    low[axis] = Math.min(low[axis], c);
                    high[axis] = Math.max(high[axis], c);
                }
            }
            int axis = 0;
            for (int a = 1; a < 3; a++) {
                if (high[a] - low[a] > high[axis] - low[axis]) {
                    axis = a;
                }
            }
            if (high[axis] - low[axis] <= 0f) {
                continue; // all centroids coincide: keep an oversized leaf rather than split without progress
            }
            int half = size / 2;
            select(centroids, start, start + size - 1, start + half, axis);
            int l = newNode(node, start, half);
            int r = newNode(node, start + half, size - half);
            left[node] = l;
            right[node] = r;
            count[node] = 0;
            pending.add(l);
            pending.add(r);
        }
        for (int node = 0; node < nodeCount; node++) {
            if (left[node] < 0) {
                for (int i = first[node]; i < first[node] + count[node]; i++) {
                    triangleLeaf[order[i]] = node;
                }
            }
        }
    }

    private int newNode(int parentNode, int start, int size) {
        int node = nodeCount++;
        parent[node] = parentNode;
        left[node] = -1;
        right[node] = -1;
        first[node] = start;
        count[node] = size;
        return node;
    }

    /** Moves the element of {@link #order} at index {@code k} into sorted place by centroid on {@code axis}. */
    private void select(float[] centroids, int lo, int hi, int k, int axis) {
        while (lo < hi) {
            float pivot = centroids[3 * order[(lo + hi) >>> 1] + axis];
            int i = lo;
            int j = hi;
            while (i <= j) {
                while (centroids[3 * order[i] + axis] < pivot) {
                    i++;
                }
                while (centroids[3 * order[j] + axis] > pivot) {
                    j--;
                }
                if (i <= j) {
                    int swap = order[i];
                    order[i] = order[j];
                    order[j] = swap;
                    i++;
                    j--;
                }
            }
            if (k <= j) {
                hi = j;
            } else if (k >= i) {
                lo = i;
            } else {
                return;
            }
        }
    }

    /** Recomputes every box from the current vertex positions. */
    public void refit() {
        for (int node = nodeCount - 1; node >= 0; node--) {
            recomputeNode(node);
        }
    }

    /**
     * Recomputes the boxes above the given triangles after their vertices moved.
     *
     * @param triangles triangle indices; the first {@code length} entries are used
     */
    public void refit(int[] triangles, int length) {
        if (++nodeStampCounter == Integer.MAX_VALUE) {
            Arrays.fill(nodeStamp, 0);
            nodeStampCounter = 1;
        }
        dirtyNodes.clear();
        for (int i = 0; i < length; i++) {
            int node = triangleLeaf[triangles[i]];
            while (node != -1 && nodeStamp[node] != nodeStampCounter) {
                nodeStamp[node] = nodeStampCounter;
                dirtyNodes.add(node);
                node = parent[node];
            }
        }
        dirtyNodes.sort();
        // Children always have larger indices than their parent, so walking down the sorted list refits bottom-up
        for (int i = dirtyNodes.size - 1; i >= 0; i--) {
            recomputeNode(dirtyNodes.items[i]);
        }
    }

    private void recomputeNode(int node) {
        int base = 6 * node;
        if (left[node] >= 0) {
            int l = 6 * left[node];
            int r = 6 * right[node];
            for (int axis = 0; axis < 3; axis++) {
                bounds[base + axis] = Math.min(bounds[l + axis], bounds[r + axis]);
                bounds[base + 3 + axis] = Math.max(bounds[l + 3 + axis], bounds[r + 3 + axis]);
            }
            return;
        }
        float[] p = mesh.getPositions();
        int[] tri = mesh.getTriangles();
        for (int axis = 0; axis < 3; axis++) {
            bounds[base + axis] = Float.POSITIVE_INFINITY;
            bounds[base + 3 + axis] = Float.NEGATIVE_INFINITY;
        }
        for (int i = first[node]; i < first[node] + count[node]; i++) {
            int t = order[i];
            for (int corner = 0; corner < 3; corner++) {
                int v = 3 * tri[3 * t + corner];
                for (int axis = 0; axis < 3; axis++) {
                    float c = p[v + axis];
                    bounds[base + axis] = Math.min(bounds[base + axis], c);
                    bounds[base + 3 + axis] = Math.max(bounds[base + 3 + axis], c);
                }
            }
        }
    }

    /**
     * Finds the nearest triangle the ray hits, front or back facing.
     *
     * @param ray         the ray; its direction is normalised internally
     * @param maxDistance only hits closer than this count
     * @param hit         receives the result when this returns true
     * @return whether a triangle was hit
     */
    public boolean raycast(Ray ray, float maxDistance, Hit hit) {
        float ox = ray.origin.x;
        float oy = ray.origin.y;
        float oz = ray.origin.z;
        float dx = ray.direction.x;
        float dy = ray.direction.y;
        float dz = ray.direction.z;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length > 0f)) {
            throw new IllegalArgumentException("The ray has no direction");
        }
        dx /= length;
        dy /= length;
        dz /= length;
        float invX = 1f / safeComponent(dx);
        float invY = 1f / safeComponent(dy);
        float invZ = 1f / safeComponent(dz);
        float[] p = mesh.getPositions();
        int[] tri = mesh.getTriangles();
        float best = maxDistance;
        int bestTriangle = -1;
        int top = 0;
        stack[top++] = 0;
        while (top > 0) {
            int node = stack[--top];
            int b = 6 * node;
            float t1 = (bounds[b] - ox) * invX;
            float t2 = (bounds[b + 3] - ox) * invX;
            float tMin = Math.min(t1, t2);
            float tMax = Math.max(t1, t2);
            t1 = (bounds[b + 1] - oy) * invY;
            t2 = (bounds[b + 4] - oy) * invY;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
            t1 = (bounds[b + 2] - oz) * invZ;
            t2 = (bounds[b + 5] - oz) * invZ;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
            if (tMax < Math.max(tMin, 0f) || tMin > best) {
                continue;
            }
            if (left[node] >= 0) {
                stack[top++] = left[node];
                stack[top++] = right[node];
                continue;
            }
            for (int i = first[node]; i < first[node] + count[node]; i++) {
                int t = order[i];
                float distance = intersectTriangle(p, 3 * tri[3 * t], 3 * tri[3 * t + 1], 3 * tri[3 * t + 2],
                    ox, oy, oz, dx, dy, dz);
                if (distance >= 0f && distance < best) {
                    best = distance;
                    bestTriangle = t;
                }
            }
        }
        if (bestTriangle < 0) {
            return false;
        }
        hit.triangle = bestTriangle;
        hit.distance = best;
        hit.x = ox + dx * best;
        hit.y = oy + dy * best;
        hit.z = oz + dz * best;
        int a = 3 * tri[3 * bestTriangle];
        int c1 = 3 * tri[3 * bestTriangle + 1];
        int c2 = 3 * tri[3 * bestTriangle + 2];
        float e1x = p[c1] - p[a];
        float e1y = p[c1 + 1] - p[a + 1];
        float e1z = p[c1 + 2] - p[a + 2];
        float e2x = p[c2] - p[a];
        float e2y = p[c2 + 1] - p[a + 1];
        float e2z = p[c2 + 2] - p[a + 2];
        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;
        float normalLength = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float flip = nx * dx + ny * dy + nz * dz > 0f ? -1f : 1f;
        hit.nx = flip * nx / normalLength;
        hit.ny = flip * ny / normalLength;
        hit.nz = flip * nz / normalLength;
        return true;
    }

    private static float safeComponent(float value) {
        if (Math.abs(value) >= MIN_DIRECTION_COMPONENT) {
            return value;
        }
        return value < 0f ? -MIN_DIRECTION_COMPONENT : MIN_DIRECTION_COMPONENT;
    }

    /** Moeller-Trumbore, both sides. Returns the distance along the ray, or -1 for a miss. */
    private static float intersectTriangle(float[] p, int a, int b, int c, float ox, float oy, float oz,
                                           float dx, float dy, float dz) {
        float e1x = p[b] - p[a];
        float e1y = p[b + 1] - p[a + 1];
        float e1z = p[b + 2] - p[a + 2];
        float e2x = p[c] - p[a];
        float e2y = p[c + 1] - p[a + 1];
        float e2z = p[c + 2] - p[a + 2];
        float px = dy * e2z - dz * e2y;
        float py = dz * e2x - dx * e2z;
        float pz = dx * e2y - dy * e2x;
        float determinant = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(determinant) < PARALLEL_EPSILON) {
            return -1f;
        }
        float inverse = 1f / determinant;
        float tx = ox - p[a];
        float ty = oy - p[a + 1];
        float tz = oz - p[a + 2];
        float u = (tx * px + ty * py + tz * pz) * inverse;
        if (u < 0f || u > 1f) {
            return -1f;
        }
        float qx = ty * e1z - tz * e1y;
        float qy = tz * e1x - tx * e1z;
        float qz = tx * e1y - ty * e1x;
        float v = (dx * qx + dy * qy + dz * qz) * inverse;
        if (v < 0f || u + v > 1f) {
            return -1f;
        }
        return (e2x * qx + e2y * qy + e2z * qz) * inverse;
    }

    /**
     * Collects the vertices within {@code radius} of a point. The result list is cleared first and holds each vertex
     * at most once.
     */
    public void collectVertices(float cx, float cy, float cz, float radius, IntArray out) {
        out.clear();
        if (++stamp == Integer.MAX_VALUE) {
            Arrays.fill(vertexStamp, 0);
            stamp = 1;
        }
        float r2 = radius * radius;
        float[] p = mesh.getPositions();
        int[] tri = mesh.getTriangles();
        int top = 0;
        stack[top++] = 0;
        while (top > 0) {
            int node = stack[--top];
            int b = 6 * node;
            float gap2 = 0f;
            gap2 += axisGap(cx, bounds[b], bounds[b + 3]);
            gap2 += axisGap(cy, bounds[b + 1], bounds[b + 4]);
            gap2 += axisGap(cz, bounds[b + 2], bounds[b + 5]);
            if (gap2 > r2) {
                continue;
            }
            if (left[node] >= 0) {
                stack[top++] = left[node];
                stack[top++] = right[node];
                continue;
            }
            for (int i = first[node]; i < first[node] + count[node]; i++) {
                int t = order[i];
                for (int corner = 0; corner < 3; corner++) {
                    int v = tri[3 * t + corner];
                    if (vertexStamp[v] == stamp) {
                        continue;
                    }
                    vertexStamp[v] = stamp;
                    float dx = p[3 * v] - cx;
                    float dy = p[3 * v + 1] - cy;
                    float dz = p[3 * v + 2] - cz;
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        out.add(v);
                    }
                }
            }
        }
    }

    /** Squared distance from {@code c} to the interval {@code [low, high]}. */
    private static float axisGap(float c, float low, float high) {
        float gap = c < low ? low - c : (c > high ? c - high : 0f);
        return gap * gap;
    }

    /** Number of nodes in the tree. */
    public int getNodeCount() {
        return nodeCount;
    }
}
