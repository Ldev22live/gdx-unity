package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.math.collision.BoundingBox;
import io.github.ldev22live.gdxunity.engine.render.DynamicMesh;
import io.github.ldev22live.gdxunity.engine.sculpt.MeshChunks;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptSurface;

import java.util.Objects;

/**
 * Presents a {@link SculptSurface} to the renderer as a {@link DynamicMesh}, so a sculpt is drawn as it is edited.
 * The renderer asks for the bounds every frame, so they are computed again only when the surface changed.
 */
public final class SculptDynamicMesh implements DynamicMesh {
    private final SculptSurface surface;
    private final MeshChunks chunks;
    private final BoundingBox bounds = new BoundingBox();
    private long boundsRevision = -1L;

    /** Creates the adapter for a surface. */
    public SculptDynamicMesh(SculptSurface surface) {
        this.surface = Objects.requireNonNull(surface, "surface cannot be null");
        this.chunks = surface.getChunks();
    }

    @Override
    public int getChunkCount() {
        return chunks.getChunkCount();
    }

    @Override
    public int getChunkVertexCount(int chunk) {
        return chunks.getVertexCount(chunk);
    }

    @Override
    public short[] getChunkIndices(int chunk) {
        return chunks.getIndices(chunk);
    }

    @Override
    public void writeChunkVertices(int chunk, float[] out) {
        chunks.writeVertices(chunk, out);
    }

    @Override
    public long getChunkRevision(int chunk) {
        return chunks.getRevision(chunk);
    }

    @Override
    public BoundingBox getBounds(BoundingBox out) {
        if (boundsRevision != surface.getRevision()) {
            surface.getMesh().computeBounds(bounds);
            boundsRevision = surface.getRevision();
        }
        return out.set(bounds.min, bounds.max);
    }
}
