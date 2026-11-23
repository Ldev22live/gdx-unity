package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceDisposalException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicMeshGpuTest {
    /** Three chunks of 4, 5 and 6 vertices whose revisions the test changes by hand. */
    private static final class FakeMesh implements DynamicMesh {
        int[] vertexCounts = {4, 5, 6};
        final long[] revisions = new long[3];
        int writes;

        @Override
        public int getChunkCount() {
            return vertexCounts.length;
        }

        @Override
        public int getChunkVertexCount(int chunk) {
            return vertexCounts[chunk];
        }

        @Override
        public short[] getChunkIndices(int chunk) {
            return new short[] {0, 1, 2};
        }

        @Override
        public void writeChunkVertices(int chunk, float[] out) {
            writes++;
            // Stamp the chunk number and revision into the data, so the test sees what was uploaded
            out[0] = chunk;
            out[1] = revisions[chunk];
        }

        @Override
        public long getChunkRevision(int chunk) {
            return revisions[chunk];
        }

        @Override
        public BoundingBox getBounds(BoundingBox out) {
            return out.set(new Vector3(0, 0, 0), new Vector3(1, 1, 1));
        }
    }

    private static final class FakeTarget implements DynamicMeshGpu.Target {
        final int vertexCount;
        final List<float[]> uploads = new ArrayList<>();
        boolean disposed;
        boolean failOnDispose;

        FakeTarget(int vertexCount) {
            this.vertexCount = vertexCount;
        }

        @Override
        public void upload(float[] vertices, int count) {
            assertEquals(vertexCount, count);
            uploads.add(new float[] {vertices[0], vertices[1]});
        }

        @Override
        public void dispose() {
            disposed = true;
            if (failOnDispose) {
                throw new IllegalStateException("dispose failed");
            }
        }
    }

    private final List<FakeTarget> created = new ArrayList<>();

    private DynamicMeshGpu<FakeTarget> gpuFor(FakeMesh mesh) {
        return new DynamicMeshGpu<>(mesh, (vertexCount, indices) -> {
            FakeTarget target = new FakeTarget(vertexCount);
            created.add(target);
            return target;
        });
    }

    @Test
    void shouldCreateOneTargetPerChunkAndUploadNothingUntilSynced() {
        DynamicMeshGpu<FakeTarget> gpu = gpuFor(new FakeMesh());

        assertEquals(3, gpu.getChunkCount());
        assertEquals(List.of(4, 5, 6), created.stream().map(t -> t.vertexCount).toList());
        assertTrue(created.stream().allMatch(t -> t.uploads.isEmpty()));
    }

    @Test
    void shouldUploadEveryChunkOnTheFirstSyncEvenAtRevisionZero() {
        FakeMesh mesh = new FakeMesh();
        DynamicMeshGpu<FakeTarget> gpu = gpuFor(mesh);

        assertEquals(3, gpu.sync());

        assertTrue(created.stream().allMatch(t -> t.uploads.size() == 1));
    }

    @Test
    void shouldUploadOnlyTheChunksWhoseRevisionChanged() {
        FakeMesh mesh = new FakeMesh();
        DynamicMeshGpu<FakeTarget> gpu = gpuFor(mesh);
        gpu.sync();
        mesh.writes = 0;

        assertEquals(0, gpu.sync(), "nothing changed, nothing is uploaded");
        assertEquals(0, mesh.writes, "and no vertex data is even written");

        mesh.revisions[1] = 7;
        assertEquals(1, gpu.sync());

        assertEquals(1, created.get(0).uploads.size());
        assertEquals(2, created.get(1).uploads.size());
        assertEquals(7f, created.get(1).uploads.get(1)[1], "the new data was uploaded");
        assertEquals(1, created.get(2).uploads.size());
        assertEquals(1, mesh.writes);
        mesh.revisions[0] = 8;
        mesh.revisions[2] = 9;
        assertEquals(2, gpu.sync());
    }

    @Test
    void shouldDisposeEveryTargetAndRefuseFurtherSyncs() {
        DynamicMeshGpu<FakeTarget> gpu = gpuFor(new FakeMesh());

        gpu.dispose();
        gpu.dispose();

        assertTrue(created.stream().allMatch(t -> t.disposed));
        assertThrows(IllegalStateException.class, gpu::sync);
    }

    @Test
    void shouldKeepDisposingAfterOneFailureAndReportAllFailures() {
        DynamicMeshGpu<FakeTarget> gpu = gpuFor(new FakeMesh());
        created.get(0).failOnDispose = true;
        created.get(2).failOnDispose = true;

        ResourceDisposalException error = assertThrows(ResourceDisposalException.class, gpu::dispose);

        assertTrue(created.stream().allMatch(t -> t.disposed), "the target after a failing one is still disposed");
        assertEquals(2, error.getSuppressed().length);
    }

    @Test
    void shouldDisposeTargetsAlreadyCreatedWhenALaterOneFails() {
        FakeMesh mesh = new FakeMesh();

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> new DynamicMeshGpu<FakeTarget>(mesh, (vertexCount, indices) -> {
                if (created.size() == 2) {
                    throw new IllegalStateException("out of graphics memory");
                }
                FakeTarget target = new FakeTarget(vertexCount);
                created.add(target);
                return target;
            }));

        assertEquals("out of graphics memory", error.getMessage());
        assertEquals(2, created.size());
        assertTrue(created.stream().allMatch(t -> t.disposed), "nothing leaks when creation fails half way");
    }

    @Test
    void shouldRememberWhichMeshItFollowsAndRejectAnEmptyOne() {
        FakeMesh mesh = new FakeMesh();
        assertSame(mesh, gpuFor(mesh).getSource());

        FakeMesh empty = new FakeMesh();
        empty.vertexCounts = new int[0];
        assertThrows(IllegalArgumentException.class, () -> gpuFor(empty));
    }
}
