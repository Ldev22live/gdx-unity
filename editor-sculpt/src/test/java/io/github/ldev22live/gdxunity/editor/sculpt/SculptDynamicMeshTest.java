package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.IntArray;
import io.github.ldev22live.gdxunity.engine.render.DynamicMesh;
import io.github.ldev22live.gdxunity.engine.sculpt.MeshChunks;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptMeshFactory;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptSurface;
import io.github.ldev22live.gdxunity.engine.sculpt.StrokeDelta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SculptDynamicMeshTest {
    @Test
    void shouldPresentTheChunksOfTheSurfaceToTheRenderer() {
        SculptSurface surface = new SculptSurface(SculptMeshFactory.cubeSphere(2f, 12), 400);
        MeshChunks chunks = surface.getChunks();
        DynamicMesh drawable = new SculptDynamicMesh(surface);

        assertEquals(chunks.getChunkCount(), drawable.getChunkCount());
        assertTrue(drawable.getChunkCount() > 1);
        float[] fromChunks = new float[DynamicMesh.FLOATS_PER_VERTEX * 400];
        float[] fromDrawable = new float[fromChunks.length];
        for (int c = 0; c < drawable.getChunkCount(); c++) {
            assertEquals(chunks.getVertexCount(c), drawable.getChunkVertexCount(c));
            assertArrayEquals(chunks.getIndices(c), drawable.getChunkIndices(c));
            chunks.writeVertices(c, fromChunks);
            drawable.writeChunkVertices(c, fromDrawable);
            assertArrayEquals(fromChunks, fromDrawable);
            assertEquals(chunks.getRevision(c), drawable.getChunkRevision(c));
        }
        assertEquals(MeshChunks.FLOATS_PER_VERTEX, DynamicMesh.FLOATS_PER_VERTEX);
    }

    @Test
    void shouldReportBoundsThatFollowTheSculpt() {
        SculptSurface surface = new SculptSurface(SculptMeshFactory.cubeSphere(2f, 6));
        SculptDynamicMesh drawable = new SculptDynamicMesh(surface);
        BoundingBox before = drawable.getBounds(new BoundingBox());
        assertEquals(2f, before.max.y, 1e-4f);

        float[] p = surface.getMesh().getPositions();
        int top = 0;
        for (int v = 0; v < p.length / 3; v++) {
            if (p[3 * v + 1] > p[3 * top + 1]) {
                top = v;
            }
        }
        p[3 * top + 1] += 1.5f;
        IntArray moved = new IntArray();
        moved.add(top);
        surface.commitMoved(moved);

        BoundingBox after = drawable.getBounds(new BoundingBox());
        assertEquals(3.5f, after.max.y, 1e-4f, "bounds are recomputed after the surface changed");
        assertEquals(2f, before.max.y, 1e-4f, "a copy was handed out earlier");
    }

    @Test
    void shouldHandOutACopyOfTheBounds() {
        SculptDynamicMesh drawable = new SculptDynamicMesh(new SculptSurface(SculptMeshFactory.cubeSphere(1f, 3)));
        BoundingBox first = drawable.getBounds(new BoundingBox());
        first.max.set(99f, 99f, 99f);

        assertNotEquals(99f, drawable.getBounds(new BoundingBox()).max.x);
    }

    @Test
    void shouldRefuseNullAndTurnUndoIntoNewChunkRevisions() {
        assertThrows(NullPointerException.class, () -> new SculptDynamicMesh(null));
        SculptSurface surface = new SculptSurface(SculptMeshFactory.cubeSphere(1f, 6), 200);
        SculptDynamicMesh drawable = new SculptDynamicMesh(surface);
        long[] before = new long[drawable.getChunkCount()];
        float[] p = surface.getMesh().getPositions();
        StrokeDelta delta = new StrokeDelta(new int[] {0},
            new float[] {p[0], p[1], p[2]}, new float[] {p[0] + 0.5f, p[1], p[2]});

        surface.apply(delta, true);

        boolean anyChanged = false;
        for (int c = 0; c < before.length; c++) {
            anyChanged |= drawable.getChunkRevision(c) != before[c];
        }
        assertTrue(anyChanged, "the renderer can tell something changed");
    }
}
