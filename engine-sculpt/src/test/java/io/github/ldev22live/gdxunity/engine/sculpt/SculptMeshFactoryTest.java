package io.github.ldev22live.gdxunity.engine.sculpt;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SculptMeshFactoryTest {
    @Test
    void shouldMakeACubeSphereWithTheDocumentedCounts() {
        for (int n : new int[] {1, 3, 8}) {
            SculptMesh mesh = SculptMeshFactory.cubeSphere(1f, n);

            assertEquals(6 * n * n + 2, mesh.getVertexCount(), "vertices for resolution " + n);
            assertEquals(12 * n * n, mesh.getTriangleCount(), "triangles for resolution " + n);
        }
    }

    @Test
    void shouldPlaceEveryVertexOnTheSphere() {
        SculptMesh mesh = SculptMeshFactory.cubeSphere(3f, 7);

        for (int v = 0; v < mesh.getVertexCount(); v++) {
            assertEquals(3f, SculptTestSupport.radius(mesh, v), 1e-4f, "radius of vertex " + v);
        }
    }

    @Test
    void shouldMakeAClosedWatertightSurface() {
        SculptMesh mesh = SculptTestSupport.sphere(6);

        Map<Long, Integer> edgeUses = new HashMap<>();
        int[] t = mesh.getTriangles();
        for (int i = 0; i < t.length; i += 3) {
            for (int corner = 0; corner < 3; corner++) {
                int a = t[i + corner];
                int b = t[i + (corner + 1) % 3];
                edgeUses.merge(((long) Math.min(a, b) << 32) | Math.max(a, b), 1, Integer::sum);
            }
        }
        for (Map.Entry<Long, Integer> edge : edgeUses.entrySet()) {
            assertEquals(2, edge.getValue(), "each edge belongs to exactly two triangles");
        }
        // Euler's formula for a sphere: vertices - edges + faces = 2
        assertEquals(2, mesh.getVertexCount() - edgeUses.size() + mesh.getTriangleCount());
    }

    @Test
    void shouldWindEveryTriangleOutwards() {
        SculptMesh mesh = SculptTestSupport.sphere(5);
        float[] p = mesh.getPositions();
        int[] t = mesh.getTriangles();

        for (int i = 0; i < t.length; i += 3) {
            int a = 3 * t[i];
            int b = 3 * t[i + 1];
            int c = 3 * t[i + 2];
            float e1x = p[b] - p[a];
            float e1y = p[b + 1] - p[a + 1];
            float e1z = p[b + 2] - p[a + 2];
            float e2x = p[c] - p[a];
            float e2y = p[c + 1] - p[a + 1];
            float e2z = p[c + 2] - p[a + 2];
            float facing = (e1y * e2z - e1z * e2y) * (p[a] + p[b] + p[c])
                + (e1z * e2x - e1x * e2z) * (p[a + 1] + p[b + 1] + p[c + 1])
                + (e1x * e2y - e1y * e2x) * (p[a + 2] + p[b + 2] + p[c + 2]);
            assertTrue(facing > 0f, "triangle " + (i / 3) + " faces outwards");
        }
    }

    @Test
    void shouldKeepTriangleSizesFairlyEven() {
        SculptMesh mesh = SculptTestSupport.sphere(10);
        float[] p = mesh.getPositions();
        int[] t = mesh.getTriangles();

        float smallest = Float.MAX_VALUE;
        float largest = 0f;
        for (int i = 0; i < t.length; i += 3) {
            int a = 3 * t[i];
            int b = 3 * t[i + 1];
            int c = 3 * t[i + 2];
            float e1x = p[b] - p[a];
            float e1y = p[b + 1] - p[a + 1];
            float e1z = p[b + 2] - p[a + 2];
            float e2x = p[c] - p[a];
            float e2y = p[c + 1] - p[a + 1];
            float e2z = p[c + 2] - p[a + 2];
            float cx = e1y * e2z - e1z * e2y;
            float cy = e1z * e2x - e1x * e2z;
            float cz = e1x * e2y - e1y * e2x;
            float area = (float) Math.sqrt(cx * cx + cy * cy + cz * cz) / 2f;
            smallest = Math.min(smallest, area);
            largest = Math.max(largest, area);
        }
        // A latitude/longitude sphere would be far worse: its pole triangles are tiny
        assertTrue(largest / smallest < 3f, "largest triangle is " + (largest / smallest) + " times the smallest");
    }

    @Test
    void shouldRejectInvalidCubeSphereArguments() {
        assertThrows(IllegalArgumentException.class, () -> SculptMeshFactory.cubeSphere(0f, 4));
        assertThrows(IllegalArgumentException.class, () -> SculptMeshFactory.cubeSphere(-1f, 4));
        assertThrows(IllegalArgumentException.class, () -> SculptMeshFactory.cubeSphere(Float.NaN, 4));
        assertThrows(IllegalArgumentException.class, () -> SculptMeshFactory.cubeSphere(1f, 0));
        IllegalArgumentException tooBig = assertThrows(IllegalArgumentException.class,
            () -> SculptMeshFactory.cubeSphere(1f, SculptMeshFactory.MAX_CUBE_SPHERE_RESOLUTION + 1));
        assertTrue(tooBig.getMessage().contains("401"), tooBig.getMessage());
    }

    @Test
    void shouldWeldDuplicatedVerticesAndDropCollapsedTriangles() {
        // Two triangles sharing the edge (1,0,0)-(0,1,0), written with their own copies of those vertices,
        // plus a triangle whose vertices all weld to one point
        float[] positions = {
            0, 0, 0, 1, 0, 0, 0, 1, 0,
            1, 0, 0, 1, 1, 0, 0, 1, 0,
            5, 5, 5, 5, 5, 5.000001f, 5, 5, 5,
        };
        int[] triangles = {0, 1, 2, 3, 4, 5, 6, 7, 8};

        SculptMesh mesh = SculptMeshFactory.weld(positions, triangles, 1e-3f);

        assertEquals(4, mesh.getVertexCount());
        assertEquals(2, mesh.getTriangleCount());
    }

    @Test
    void shouldRejectBadWeldInput() {
        float[] positions = {0, 0, 0, 1, 0, 0, 0, 1, 0};
        assertThrows(IllegalArgumentException.class, () -> SculptMeshFactory.weld(positions, new int[] {0, 1, 3}, 1e-3f));
        assertThrows(IllegalArgumentException.class, () -> SculptMeshFactory.weld(positions, new int[] {0, 1, 2}, 0f));
        assertThrows(IllegalArgumentException.class, () -> SculptMeshFactory.weld(positions, new int[] {0, 1}, 1e-3f));
        // Everything collapses: nothing is left to sculpt
        assertThrows(IllegalArgumentException.class,
            () -> SculptMeshFactory.weld(new float[] {0, 0, 0, 0, 0, 0, 0, 0, 0}, new int[] {0, 1, 2}, 1e-3f));
    }
}
