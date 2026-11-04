package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.utils.IntArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SculptSurfaceTest {
    @Test
    void shouldUpdateTheNormalsAroundAMovedVertex() {
        SculptMesh mesh = SculptTestSupport.sphere(8);
        SculptSurface surface = new SculptSurface(mesh);
        int vertex = 10;
        float[] before = mesh.getNormals().clone();

        mesh.getPositions()[3 * vertex] += 0.2f;
        mesh.getPositions()[3 * vertex + 1] += 0.1f;
        IntArray moved = new IntArray();
        moved.add(vertex);
        surface.commitMoved(moved);

        SculptTestSupport.assertNormalsMatchFullRecompute(mesh);
        // The area-weighted normal of a vertex depends only on the ring of vertices around it, not on its own
        // position, so moving a vertex leaves its own normal alone and tilts the normals of its neighbours. That is
        // why the update must cover the neighbours.
        float largestNeighbourChange = 0f;
        for (int k = mesh.getNeighborStart(vertex); k < mesh.getNeighborEnd(vertex); k++) {
            int neighbour = mesh.getNeighbors()[k];
            float change = 0f;
            for (int axis = 0; axis < 3; axis++) {
                change += Math.abs(mesh.getNormals()[3 * neighbour + axis] - before[3 * neighbour + axis]);
            }
            largestNeighbourChange = Math.max(largestNeighbourChange, change);
        }
        assertTrue(largestNeighbourChange > 1e-2f, "a neighbour's normal tilted by " + largestNeighbourChange);
    }

    @Test
    void shouldChangeNothingWhenNothingMoved() {
        SculptSurface surface = new SculptSurface(SculptTestSupport.sphere(4));
        long revision = surface.getRevision();

        surface.commitMoved(new IntArray());

        assertEquals(revision, surface.getRevision());
    }

    @Test
    void shouldApplyBeforeAndAfterPositionsFromADelta() {
        SculptMesh mesh = SculptTestSupport.sphere(4);
        SculptSurface surface = new SculptSurface(mesh);
        float[] original = mesh.getPositions().clone();
        StrokeDelta delta = new StrokeDelta(new int[] {3, 5},
            new float[] {original[9], original[10], original[11], original[15], original[16], original[17]},
            new float[] {7, 8, 9, 1, 2, 3});

        surface.apply(delta, true);
        assertEquals(7f, mesh.getPositions()[9]);
        assertEquals(3f, mesh.getPositions()[17]);
        surface.apply(delta, false);

        assertArrayEquals(original, mesh.getPositions());
        SculptTestSupport.assertNormalsMatchFullRecompute(mesh);
    }

    @Test
    void shouldRejectADeltaForAnotherMesh() {
        SculptSurface surface = new SculptSurface(SculptTestSupport.sphere(2));
        StrokeDelta foreign = new StrokeDelta(new int[] {9999}, new float[3], new float[3]);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> surface.apply(foreign, true));

        assertTrue(error.getMessage().contains("9999"), error.getMessage());
    }

    @Test
    void shouldRejectMismatchedDeltaArrays() {
        assertThrows(IllegalArgumentException.class, () -> new StrokeDelta(new int[] {1}, new float[3], new float[2]));
        assertThrows(IllegalArgumentException.class, () -> new StrokeDelta(new int[] {1, 2}, new float[3], new float[6]));
        assertTrue(new StrokeDelta(new int[0], new float[0], new float[0]).isEmpty());
        assertEquals(4L + 12L + 12L, new StrokeDelta(new int[] {1}, new float[3], new float[3]).memoryBytes());
    }
}
