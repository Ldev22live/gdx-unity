package io.github.ldev22live.gdxunity.engine.sculpt;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubdivisionTest {
    @Test
    void shouldSplitATriangleIntoFourAtTheEdgeMidpoints() {
        SculptMesh triangle = new SculptMesh(new float[] {0, 0, 0, 2, 0, 0, 0, 2, 0}, new int[] {0, 1, 2});

        SculptMesh result = Subdivision.midpoint(triangle, 1);

        assertEquals(6, result.getVertexCount());
        assertEquals(4, result.getTriangleCount());
        // Original vertices keep index and position; midpoints are appended
        assertArrayEquals(new float[] {0, 0, 0, 2, 0, 0, 0, 2, 0}, Arrays.copyOf(result.getPositions(), 9));
        float[] p = result.getPositions();
        assertEquals(1f, p[9]);
        assertEquals(0f, p[10]);
        assertEquals(1f, p[12]);
        assertEquals(1f, p[13]);
        assertEquals(0f, p[15]);
        assertEquals(1f, p[16]);
    }

    @Test
    void shouldQuadrupleTriangleCountAndKeepTheSurfaceClosed() {
        SculptMesh sphere = SculptTestSupport.sphere(3);

        SculptMesh result = Subdivision.midpoint(sphere, 2);

        assertEquals(16 * sphere.getTriangleCount(), result.getTriangleCount());
        // Euler's formula still holds for a closed sphere: V - T/2 = 2 (edges = 3T/2)
        assertEquals(2, result.getVertexCount() - result.getTriangleCount() / 2);
    }

    @Test
    void shouldNotChangeTheShape() {
        SculptMesh sphere = SculptTestSupport.sphere(4);
        float[] original = sphere.getPositions().clone();

        SculptMesh result = Subdivision.midpoint(sphere, 1);

        assertArrayEquals(original, Arrays.copyOf(result.getPositions(), original.length));
        for (int v = original.length / 3; v < result.getVertexCount(); v++) {
            // Midpoints of chords of the unit sphere lie inside it, never outside
            assertTrue(SculptTestSupport.radius(result, v) <= 1f + 1e-6f, "midpoint " + v);
        }
    }

    @Test
    void shouldRejectBadLevelsAndResultsThatAreTooLarge() {
        SculptMesh sphere = SculptTestSupport.sphere(2);
        assertThrows(IllegalArgumentException.class, () -> Subdivision.midpoint(sphere, 0));
        assertThrows(IllegalArgumentException.class, () -> Subdivision.midpoint(sphere, Subdivision.MAX_LEVELS + 1));
        SculptMesh big = SculptTestSupport.sphere(100);
        IllegalArgumentException tooMany = assertThrows(IllegalArgumentException.class,
            () -> Subdivision.midpoint(big, 4));
        assertTrue(tooMany.getMessage().contains("limit"), tooMany.getMessage());
    }
}
