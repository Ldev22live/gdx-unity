package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.math.collision.BoundingBox;

/**
 * Triangle geometry whose vertices change while it is displayed, such as a sculpt being edited (TD-038). It is cut
 * into chunks that each fit 16-bit indices; the renderer uploads a chunk again only when its revision changed.
 * Implemented by the code that owns the geometry, so the renderer needs no dependency on it.
 */
public interface DynamicMesh {
    /** Floats written per vertex by {@link #writeChunkVertices}: position (x, y, z) then normal (x, y, z). */
    int FLOATS_PER_VERTEX = 6;

    /** Number of chunks; fixed for the life of this object. */
    int getChunkCount();

    /** Number of vertices in a chunk; fixed. */
    int getChunkVertexCount(int chunk);

    /** The chunk's triangle indices (16-bit, read as unsigned); fixed. The renderer does not modify the array. */
    short[] getChunkIndices(int chunk);

    /**
     * Writes the chunk's current vertices into the start of {@code out}: {@link #FLOATS_PER_VERTEX} floats each, in
     * the order of the chunk's local vertex numbers.
     */
    void writeChunkVertices(int chunk, float[] out);

    /** A number that changes whenever the chunk's vertex data changed. */
    long getChunkRevision(int chunk);

    /** Sets the box to the bounds of the geometry in its own space and returns it. */
    BoundingBox getBounds(BoundingBox out);
}
