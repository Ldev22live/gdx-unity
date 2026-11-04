package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SculptMeshTest {
    private static final float[] TRIANGLE_POSITIONS = {0, 0, 0, 1, 0, 0, 0, 1, 0};
    private static final int[] TRIANGLE = {0, 1, 2};

    private static String messageOf(float[] positions, int[] triangles) {
        return assertThrows(IllegalArgumentException.class, () -> new SculptMesh(positions, triangles)).getMessage();
    }

    @Test
    void shouldRejectInvalidInputAndNameTheProblem() {
        assertTrue(messageOf(new float[] {0, 0}, TRIANGLE).contains("multiple of 3"));
        assertTrue(messageOf(TRIANGLE_POSITIONS, new int[] {0, 1}).contains("multiple of 3"));
        assertTrue(messageOf(new float[0], new int[0]).contains("at least one triangle"));
        assertTrue(messageOf(TRIANGLE_POSITIONS, new int[] {0, 1, 7}).contains("vertex 7"));
        assertTrue(messageOf(TRIANGLE_POSITIONS, new int[] {0, -1, 2}).contains("vertex -1"));
        assertTrue(messageOf(TRIANGLE_POSITIONS, new int[] {0, 1, 1}).contains("repeats a vertex"));
        assertTrue(messageOf(new float[] {0, 0, 0, 1, Float.NaN, 0, 0, 1, 0}, TRIANGLE).contains("Vertex 1"));
        assertThrows(NullPointerException.class, () -> new SculptMesh(null, TRIANGLE));
    }

    @Test
    void shouldCopyTheArraysItIsGiven() {
        float[] positions = TRIANGLE_POSITIONS.clone();
        int[] triangles = TRIANGLE.clone();
        SculptMesh mesh = new SculptMesh(positions, triangles);

        positions[3] = 99f;
        triangles[0] = 2;

        assertEquals(1f, mesh.getPositions()[3]);
        assertEquals(0, mesh.getTriangles()[0]);
    }

    @Test
    void shouldGiveOutwardUnitNormalsOnASphere() {
        SculptMesh mesh = SculptMeshFactory.cubeSphere(2f, 6);

        Vector3 position = new Vector3();
        Vector3 normal = new Vector3();
        for (int v = 0; v < mesh.getVertexCount(); v++) {
            mesh.getPosition(v, position);
            mesh.getNormal(v, normal);
            assertEquals(1f, normal.len(), 1e-5f, "unit length at vertex " + v);
            assertTrue(normal.dot(position.nor()) > 0.99f, "points outwards at vertex " + v);
        }
    }

    @Test
    void shouldGiveFlatTrianglesTheirFaceNormal() {
        SculptMesh mesh = new SculptMesh(TRIANGLE_POSITIONS, TRIANGLE);

        Vector3 normal = mesh.getNormal(0, new Vector3());

        assertEquals(0f, normal.x, 1e-6f);
        assertEquals(0f, normal.y, 1e-6f);
        assertEquals(1f, normal.z, 1e-6f);
    }

    @Test
    void shouldGiveAVertexNoTriangleUsesTheFallbackNormal() {
        SculptMesh mesh = new SculptMesh(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0, 5, 5, 5}, TRIANGLE);

        Vector3 normal = mesh.getNormal(3, new Vector3());

        assertEquals(0f, normal.x);
        assertEquals(1f, normal.y);
        assertEquals(0f, normal.z);
        assertEquals(0, mesh.getNeighborEnd(3) - mesh.getNeighborStart(3));
    }

    @Test
    void shouldBuildConsistentAdjacency() {
        SculptMesh mesh = SculptTestSupport.sphere(5);

        int totalTriangleRefs = 0;
        for (int v = 0; v < mesh.getVertexCount(); v++) {
            Set<Integer> seen = new HashSet<>();
            for (int k = mesh.getNeighborStart(v); k < mesh.getNeighborEnd(v); k++) {
                int u = mesh.getNeighbors()[k];
                assertTrue(u != v, "a vertex is not its own neighbour");
                assertTrue(seen.add(u), "neighbour listed once: " + u + " of " + v);
                boolean symmetric = false;
                for (int j = mesh.getNeighborStart(u); j < mesh.getNeighborEnd(u); j++) {
                    symmetric |= mesh.getNeighbors()[j] == v;
                }
                assertTrue(symmetric, "neighbour relation is symmetric: " + v + " and " + u);
            }
            for (int k = mesh.getVertexTriangleStart(v); k < mesh.getVertexTriangleEnd(v); k++) {
                int t = mesh.getVertexTriangles()[k];
                boolean uses = false;
                for (int corner = 0; corner < 3; corner++) {
                    uses |= mesh.getTriangles()[3 * t + corner] == v;
                }
                assertTrue(uses, "triangle " + t + " is listed for vertex " + v + " and uses it");
                totalTriangleRefs++;
            }
        }
        assertEquals(3 * mesh.getTriangleCount(), totalTriangleRefs);
    }

    @Test
    void shouldComputeBounds() {
        SculptMesh mesh = SculptMeshFactory.cubeSphere(2f, 4);

        BoundingBox bounds = mesh.computeBounds(new BoundingBox());

        assertEquals(-2f, bounds.min.x, 1e-5f);
        assertEquals(2f, bounds.max.y, 1e-5f);
    }
}
