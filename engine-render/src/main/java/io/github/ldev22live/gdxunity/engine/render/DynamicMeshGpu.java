package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceDisposalException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Keeps the graphics-card copy of a {@link DynamicMesh} in step with it (TD-038): one target per chunk, created
 * once, and re-uploaded by {@link #sync()} only when the chunk's revision changed since the last upload. The target
 * does the graphics work, so this logic runs and is tested without a graphics context.
 *
 * @param <T> the kind of per-chunk target
 */
final class DynamicMeshGpu<T extends DynamicMeshGpu.Target> implements Disposable {
    /** The graphics-side copy of one chunk. */
    interface Target extends Disposable {
        /** Replaces the chunk's vertices (position then normal, {@link DynamicMesh#FLOATS_PER_VERTEX} floats each). */
        void upload(float[] vertices, int vertexCount);
    }

    /** Creates the target of a chunk. */
    interface TargetFactory<T extends Target> {
        T create(int vertexCount, short[] indices);
    }

    private final DynamicMesh source;
    private final List<T> targets = new ArrayList<>();
    private final long[] uploadedRevision;
    private float[] scratch = new float[0];
    private boolean disposed;

    /**
     * Creates a target for every chunk of the mesh. Nothing is uploaded until {@link #sync()}.
     *
     * @throws IllegalArgumentException if the mesh has no chunks
     */
    DynamicMeshGpu(DynamicMesh source, TargetFactory<T> factory) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(factory, "factory cannot be null");
        int chunks = source.getChunkCount();
        if (chunks < 1) {
            throw new IllegalArgumentException("A dynamic mesh needs at least one chunk");
        }
        uploadedRevision = new long[chunks];
        Arrays.fill(uploadedRevision, Long.MIN_VALUE);
        try {
            for (int c = 0; c < chunks; c++) {
                targets.add(factory.create(source.getChunkVertexCount(c), source.getChunkIndices(c)));
            }
        } catch (RuntimeException e) {
            disposeTargets(e);
            throw e;
        }
    }

    /** The mesh this copy follows. */
    DynamicMesh getSource() {
        return source;
    }

    /** Number of chunk targets. */
    int getChunkCount() {
        return targets.size();
    }

    /** The target of a chunk. */
    T getTarget(int chunk) {
        return targets.get(chunk);
    }

    /**
     * Uploads every chunk that changed since the last call (all of them on the first call).
     *
     * @return the number of chunks uploaded
     * @throws IllegalStateException if this copy was disposed
     */
    int sync() {
        if (disposed) {
            throw new IllegalStateException("The dynamic mesh copy has been disposed");
        }
        int uploaded = 0;
        for (int c = 0; c < targets.size(); c++) {
            long revision = source.getChunkRevision(c);
            if (revision == uploadedRevision[c]) {
                continue;
            }
            int vertexCount = source.getChunkVertexCount(c);
            int floats = vertexCount * DynamicMesh.FLOATS_PER_VERTEX;
            if (scratch.length < floats) {
                scratch = new float[floats];
            }
            source.writeChunkVertices(c, scratch);
            targets.get(c).upload(scratch, vertexCount);
            uploadedRevision[c] = revision;
            uploaded++;
        }
        return uploaded;
    }

    /** Disposes every chunk target; failures are reported together (TD-008). Idempotent. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        disposeTargets(null);
    }

    private void disposeTargets(RuntimeException cause) {
        List<Throwable> failures = new ArrayList<>();
        for (T target : targets) {
            try {
                target.dispose();
            } catch (RuntimeException e) {
                failures.add(e);
            }
        }
        targets.clear();
        if (cause != null) {
            failures.forEach(cause::addSuppressed);
        } else if (!failures.isEmpty()) {
            ResourceDisposalException error = new ResourceDisposalException(
                "Failed to dispose " + failures.size() + " dynamic mesh chunk(s)");
            failures.forEach(error::addSuppressed);
            throw error;
        }
    }
}
