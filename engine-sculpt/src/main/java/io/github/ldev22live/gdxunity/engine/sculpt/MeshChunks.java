package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.utils.IntArray;

import java.util.Arrays;
import java.util.Objects;

/**
 * A {@link SculptMesh} cut into pieces a graphics card can draw with 16-bit indices (TD-038). LibGDX meshes index at
 * most 65,536 vertices, and a dense sculpt has hundreds of thousands, so the triangles are grouped in runs: each
 * chunk holds a consecutive range of triangles with its own small vertex list. A vertex shared by two chunks
 * appears in both.
 *
 * <p>A chunk has a revision that changes whenever one of its triangles' vertices moved or its normal changed, so a
 * renderer re-uploads only the chunks whose revision it has not seen. Chunk geometry (which triangles, which
 * indices) never changes; only positions and normals do.
 *
 * <p>Not thread-safe.
 */
public final class MeshChunks {
    /** Default chunk size: comfortably below the 65,536 vertices a 16-bit index can address. */
    public static final int DEFAULT_MAX_VERTICES = 60_000;
    /** Floats per vertex in {@link #writeVertices}: position then normal. */
    public static final int FLOATS_PER_VERTEX = 6;

    private final SculptMesh mesh;
    private final int chunkCount;
    /** For each chunk: its first triangle and the number of triangles. */
    private final int[] firstTriangle;
    private final int[] triangleCount;
    /** For each chunk: the global vertex index of each local vertex. */
    private final int[][] globalVertices;
    private final short[][] indices;
    private final int[] chunkOfTriangle;
    private final long[] revisions;
    private final int[] markStamp;
    private int stamp;

    /**
     * Cuts a mesh into chunks of at most {@code maxVertices} vertices.
     *
     * @param maxVertices from 3 to 65,536
     * @throws IllegalArgumentException if {@code maxVertices} is out of range
     */
    public MeshChunks(SculptMesh mesh, int maxVertices) {
        this.mesh = Objects.requireNonNull(mesh, "mesh cannot be null");
        if (maxVertices < 3 || maxVertices > 65_536) {
            throw new IllegalArgumentException("maxVertices must be between 3 and 65536: " + maxVertices);
        }
        int triangleTotal = mesh.getTriangleCount();
        int[] triangles = mesh.getTriangles();
        chunkOfTriangle = new int[triangleTotal];
        int[] localIndex = new int[mesh.getVertexCount()];
        int[] localOwner = new int[mesh.getVertexCount()];
        Arrays.fill(localOwner, -1);

        IntArray firsts = new IntArray();
        IntArray counts = new IntArray();
        int[][] vertexLists = new int[16][];
        short[][] indexLists = new short[16][];
        int chunk = -1;
        IntArray currentVertices = new IntArray();
        IntArray currentIndices = new IntArray();
        int currentFirst = 0;
        for (int t = 0; t < triangleTotal; t++) {
            int newVertices = 0;
            for (int corner = 0; corner < 3; corner++) {
                if (localOwner[triangles[3 * t + corner]] != chunk) {
                    newVertices++;
                }
            }
            // The same vertex twice in a triangle cannot happen (SculptMesh rejects it), so the count is exact
            if (chunk < 0 || currentVertices.size + newVertices > maxVertices) {
                if (chunk >= 0) {
                    firsts.add(currentFirst);
                    counts.add(t - currentFirst);
                    vertexLists = store(vertexLists, chunk, currentVertices.toArray());
                    indexLists = store(indexLists, chunk, toShorts(currentIndices));
                }
                chunk++;
                currentFirst = t;
                currentVertices.clear();
                currentIndices.clear();
            }
            for (int corner = 0; corner < 3; corner++) {
                int v = triangles[3 * t + corner];
                if (localOwner[v] != chunk) {
                    localOwner[v] = chunk;
                    localIndex[v] = currentVertices.size;
                    currentVertices.add(v);
                }
                currentIndices.add(localIndex[v]);
            }
            chunkOfTriangle[t] = chunk;
        }
        firsts.add(currentFirst);
        counts.add(triangleTotal - currentFirst);
        vertexLists = store(vertexLists, chunk, currentVertices.toArray());
        indexLists = store(indexLists, chunk, toShorts(currentIndices));

        chunkCount = chunk + 1;
        firstTriangle = firsts.toArray();
        triangleCount = counts.toArray();
        globalVertices = Arrays.copyOf(vertexLists, chunkCount);
        indices = Arrays.copyOf(indexLists, chunkCount);
        revisions = new long[chunkCount];
        markStamp = new int[chunkCount];
    }

    /** Cuts a mesh into chunks of {@link #DEFAULT_MAX_VERTICES}. */
    public MeshChunks(SculptMesh mesh) {
        this(mesh, DEFAULT_MAX_VERTICES);
    }

    private static int[][] store(int[][] lists, int chunk, int[] value) {
        int[][] target = chunk < lists.length ? lists : Arrays.copyOf(lists, Math.max(chunk + 1, lists.length * 2));
        target[chunk] = value;
        return target;
    }

    private static short[][] store(short[][] lists, int chunk, short[] value) {
        short[][] target = chunk < lists.length ? lists : Arrays.copyOf(lists, Math.max(chunk + 1, lists.length * 2));
        target[chunk] = value;
        return target;
    }

    /** Index values above 32,767 wrap to negative shorts; the graphics API reads them as unsigned. */
    private static short[] toShorts(IntArray values) {
        short[] result = new short[values.size];
        for (int i = 0; i < result.length; i++) {
            result[i] = (short) values.items[i];
        }
        return result;
    }

    /** Number of chunks. */
    public int getChunkCount() {
        return chunkCount;
    }

    /** Number of vertices in a chunk. */
    public int getVertexCount(int chunk) {
        return globalVertices[chunk].length;
    }

    /** Number of indices (three per triangle) in a chunk. */
    public int getIndexCount(int chunk) {
        return indices[chunk].length;
    }

    /** First triangle of the chunk. */
    public int getFirstTriangle(int chunk) {
        return firstTriangle[chunk];
    }

    /** Number of triangles in the chunk. */
    public int getTriangleCount(int chunk) {
        return triangleCount[chunk];
    }

    /** The global vertex index of a chunk's local vertex. */
    public int getGlobalVertex(int chunk, int local) {
        return globalVertices[chunk][local];
    }

    /** The chunk's index list (16-bit, read as unsigned). Never changes; do not modify. */
    public short[] getIndices(int chunk) {
        return indices[chunk];
    }

    /**
     * Writes the chunk's vertices as position (x, y, z) then normal (x, y, z), {@link #FLOATS_PER_VERTEX} floats
     * each, into the start of {@code out}.
     *
     * @throws IllegalArgumentException if {@code out} is too small
     */
    public void writeVertices(int chunk, float[] out) {
        int[] vertices = globalVertices[chunk];
        if (out.length < FLOATS_PER_VERTEX * vertices.length) {
            throw new IllegalArgumentException("out needs " + FLOATS_PER_VERTEX * vertices.length + " floats, has "
                + out.length);
        }
        float[] positions = mesh.getPositions();
        float[] normals = mesh.getNormals();
        for (int i = 0; i < vertices.length; i++) {
            int v = 3 * vertices[i];
            int o = FLOATS_PER_VERTEX * i;
            out[o] = positions[v];
            out[o + 1] = positions[v + 1];
            out[o + 2] = positions[v + 2];
            out[o + 3] = normals[v];
            out[o + 4] = normals[v + 1];
            out[o + 5] = normals[v + 2];
        }
    }

    /** The revision of a chunk: 0 until it first changes; a larger value than before means it must be re-uploaded. */
    public long getRevision(int chunk) {
        return revisions[chunk];
    }

    /**
     * Marks every chunk that has a triangle using one of the given vertices as changed at {@code revision}. Call it
     * with the vertices whose position or normal changed.
     *
     * @param vertices the vertices whose position or normal changed
     * @param revision the new revision, greater than any given before
     */
    void markChanged(IntArray vertices, long revision) {
        if (++stamp == Integer.MAX_VALUE) {
            Arrays.fill(markStamp, 0);
            stamp = 1;
        }
        int[] vertexTriangles = mesh.getVertexTriangles();
        for (int i = 0; i < vertices.size; i++) {
            int v = vertices.items[i];
            for (int k = mesh.getVertexTriangleStart(v); k < mesh.getVertexTriangleEnd(v); k++) {
                int chunk = chunkOfTriangle[vertexTriangles[k]];
                if (markStamp[chunk] != stamp) {
                    markStamp[chunk] = stamp;
                    revisions[chunk] = revision;
                }
            }
        }
    }
}
