package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.IntArray;

import java.util.Arrays;
import java.util.Objects;

/**
 * A mesh together with everything that must stay consistent with it while it is sculpted: the vertex normals and the
 * {@link TriangleBvh}. All position changes go through here ({@link #commitMoved} for brushes, {@link #apply} for
 * undo and redo), so a stroke can never leave stale normals or a stale hierarchy behind.
 *
 * <p>{@link #getRevision()} increases with every change; a renderer compares it with the revision it last uploaded.
 * Not thread-safe.
 */
public final class SculptSurface {
    private final SculptMesh mesh;
    private final TriangleBvh bvh;
    private final MeshChunks chunks;
    private long revision;

    private final int[] vertexStamp;
    private final int[] triangleStamp;
    private int stamp;
    private final IntArray normalRegion = new IntArray();
    private final IntArray changedTriangles = new IntArray();

    /**
     * Wraps a mesh, builds its hierarchy and cuts it into render chunks of {@link MeshChunks#DEFAULT_MAX_VERTICES}.
     * The surface owns the mesh from now on.
     */
    public SculptSurface(SculptMesh mesh) {
        this(mesh, MeshChunks.DEFAULT_MAX_VERTICES);
    }

    /** Like {@link #SculptSurface(SculptMesh)} with a chosen chunk size, mostly for tests. */
    public SculptSurface(SculptMesh mesh, int maxChunkVertices) {
        this.mesh = Objects.requireNonNull(mesh, "mesh cannot be null");
        this.bvh = new TriangleBvh(mesh);
        this.chunks = new MeshChunks(mesh, maxChunkVertices);
        this.vertexStamp = new int[mesh.getVertexCount()];
        this.triangleStamp = new int[mesh.getTriangleCount()];
    }

    /** The mesh being sculpted. */
    public SculptMesh getMesh() {
        return mesh;
    }

    /** The hierarchy over the mesh, kept up to date. */
    public TriangleBvh getBvh() {
        return bvh;
    }

    /** The mesh cut into render chunks; their revisions say which chunks need re-uploading. */
    public MeshChunks getChunks() {
        return chunks;
    }

    /** Changes with every modification of the surface. */
    public long getRevision() {
        return revision;
    }

    /** Finds the surface under a ray; see {@link TriangleBvh#raycast}. */
    public boolean raycast(Ray ray, float maxDistance, TriangleBvh.Hit hit) {
        return bvh.raycast(ray, maxDistance, hit);
    }

    /**
     * Updates normals and the hierarchy after the positions of {@code moved} were changed in the mesh's position
     * array. Normals are recomputed for the moved vertices and their neighbours, because moving a vertex tilts the
     * faces around it.
     *
     * @param moved the vertices whose positions changed
     */
    public void commitMoved(IntArray moved) {
        if (moved.size == 0) {
            return;
        }
        if (++stamp == Integer.MAX_VALUE) {
            Arrays.fill(vertexStamp, 0);
            Arrays.fill(triangleStamp, 0);
            stamp = 1;
        }
        normalRegion.clear();
        changedTriangles.clear();
        int[] neighbors = mesh.getNeighbors();
        int[] vertexTriangles = mesh.getVertexTriangles();
        for (int i = 0; i < moved.size; i++) {
            int v = moved.items[i];
            if (vertexStamp[v] != stamp) {
                vertexStamp[v] = stamp;
                normalRegion.add(v);
            }
            for (int k = mesh.getNeighborStart(v); k < mesh.getNeighborEnd(v); k++) {
                int u = neighbors[k];
                if (vertexStamp[u] != stamp) {
                    vertexStamp[u] = stamp;
                    normalRegion.add(u);
                }
            }
            for (int k = mesh.getVertexTriangleStart(v); k < mesh.getVertexTriangleEnd(v); k++) {
                int t = vertexTriangles[k];
                if (triangleStamp[t] != stamp) {
                    triangleStamp[t] = stamp;
                    changedTriangles.add(t);
                }
            }
        }
        mesh.recomputeNormals(normalRegion);
        bvh.refit(changedTriangles.items, changedTriangles.size);
        revision++;
        // The region holds every vertex whose position or normal changed
        chunks.markChanged(normalRegion, revision);
    }

    /**
     * Applies a stroke's effect: the positions after the stroke (redo) or before it (undo).
     *
     * @param delta   a stroke recorded on this surface's mesh
     * @param redo    true to set the "after" positions, false to restore the "before" positions
     * @throws IllegalArgumentException if the delta refers to a vertex this mesh does not have
     */
    public void apply(StrokeDelta delta, boolean redo) {
        Objects.requireNonNull(delta, "delta cannot be null");
        int[] vertices = delta.vertices();
        float[] source = redo ? delta.after() : delta.before();
        float[] positions = mesh.getPositions();
        for (int i = 0; i < vertices.length; i++) {
            if (vertices[i] < 0 || vertices[i] >= mesh.getVertexCount()) {
                throw new IllegalArgumentException("The stroke refers to vertex " + vertices[i]
                    + " but the mesh has " + mesh.getVertexCount() + " vertices");
            }
        }
        IntArray moved = new IntArray(vertices.length);
        for (int i = 0; i < vertices.length; i++) {
            int v = vertices[i];
            positions[3 * v] = source[3 * i];
            positions[3 * v + 1] = source[3 * i + 1];
            positions[3 * v + 2] = source[3 * i + 2];
            moved.add(v);
        }
        commitMoved(moved);
    }
}
