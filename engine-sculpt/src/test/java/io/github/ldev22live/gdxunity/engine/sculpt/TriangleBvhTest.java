package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.IntArray;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriangleBvhTest {
    @Test
    void shouldHitTheSurfaceUnderARayAndReportItsNormal() {
        SculptMesh mesh = SculptTestSupport.sphere(12);
        TriangleBvh bvh = new TriangleBvh(mesh);
        TriangleBvh.Hit hit = new TriangleBvh.Hit();

        boolean found = bvh.raycast(new Ray(new Vector3(0, 0, 5), new Vector3(0, 0, -1)), Float.MAX_VALUE, hit);

        assertTrue(found);
        assertEquals(5f - hit.z, hit.distance, 1e-4f);
        assertEquals(1f, hit.z, 0.03f, "the faceted sphere is just inside the true sphere");
        assertTrue(hit.nz > 0.95f, "the normal faces the ray origin");
        assertEquals(1f, (float) Math.sqrt(hit.nx * hit.nx + hit.ny * hit.ny + hit.nz * hit.nz), 1e-5f);
        assertTrue(hit.triangle >= 0 && hit.triangle < mesh.getTriangleCount());
    }

    @Test
    void shouldHitTheBackOfASurfaceFromInside() {
        TriangleBvh bvh = new TriangleBvh(SculptTestSupport.sphere(8));
        TriangleBvh.Hit hit = new TriangleBvh.Hit();

        assertTrue(bvh.raycast(new Ray(new Vector3(0, 0, 0), new Vector3(0, 0, 1)), Float.MAX_VALUE, hit));

        assertEquals(1f, hit.distance, 0.05f);
        assertTrue(hit.nz < -0.9f, "the normal is turned to face the ray origin, inside the sphere");
    }

    @Test
    void shouldMissWhenTheRayPointsAwayOrIsTooShort() {
        TriangleBvh bvh = new TriangleBvh(SculptTestSupport.sphere(8));
        TriangleBvh.Hit hit = new TriangleBvh.Hit();

        assertFalse(bvh.raycast(new Ray(new Vector3(0, 0, 5), new Vector3(0, 0, 1)), Float.MAX_VALUE, hit));
        assertFalse(bvh.raycast(new Ray(new Vector3(5, 5, 5), new Vector3(0, 0, -1)), Float.MAX_VALUE, hit));
        assertFalse(bvh.raycast(new Ray(new Vector3(0, 0, 5), new Vector3(0, 0, -1)), 3f, hit), "max distance");
    }

    @Test
    void shouldRejectARayWithoutDirection() {
        TriangleBvh bvh = new TriangleBvh(SculptTestSupport.sphere(2));
        Ray ray = new Ray(new Vector3(0, 0, 5), new Vector3(0, 0, -1));
        ray.direction.set(0, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> bvh.raycast(ray, 10f, new TriangleBvh.Hit()));
    }

    @Test
    void shouldAgreeWithTestingEveryTriangle() {
        SculptMesh mesh = SculptTestSupport.sphere(16);
        TriangleBvh bvh = new TriangleBvh(mesh);
        TriangleBvh.Hit hit = new TriangleBvh.Hit();

        int hits = 0;
        for (Ray ray : SculptTestSupport.randomRays(400, 42)) {
            float expected = SculptTestSupport.bruteForceHit(mesh, ray);
            boolean found = bvh.raycast(ray, Float.MAX_VALUE, hit);
            assertEquals(expected >= 0f, found, "hit or miss");
            if (found) {
                hits++;
                assertEquals(expected, hit.distance, 1e-3f);
            }
        }
        assertTrue(hits > 100, "enough of the random rays hit the sphere to make the comparison meaningful: " + hits);
    }

    @Test
    void shouldFindTheSameVerticesAsTestingEveryVertex() {
        SculptMesh mesh = SculptTestSupport.sphere(16);
        TriangleBvh bvh = new TriangleBvh(mesh);
        Random random = new Random(5);
        IntArray found = new IntArray();

        for (int i = 0; i < 100; i++) {
            float x = random.nextFloat() * 2.4f - 1.2f;
            float y = random.nextFloat() * 2.4f - 1.2f;
            float z = random.nextFloat() * 2.4f - 1.2f;
            float radius = 0.05f + random.nextFloat() * 0.8f;

            bvh.collectVertices(x, y, z, radius, found);

            Set<Integer> actual = new HashSet<>();
            for (int k = 0; k < found.size; k++) {
                assertTrue(actual.add(found.items[k]), "vertex listed once");
            }
            Set<Integer> expected = new HashSet<>();
            IntArray reference = SculptTestSupport.bruteForceVertices(mesh, x, y, z, radius);
            for (int k = 0; k < reference.size; k++) {
                expected.add(reference.items[k]);
            }
            assertEquals(expected, actual, "vertices within " + radius + " of " + x + ", " + y + ", " + z);
        }
    }

    @Test
    void shouldFollowMovedVerticesAfterAPartialRefit() {
        SculptMesh mesh = SculptTestSupport.sphere(16);
        TriangleBvh bvh = new TriangleBvh(mesh);
        float[] p = mesh.getPositions();
        IntArray moved = new IntArray();
        for (int v = 0; v < mesh.getVertexCount(); v++) {
            if (p[3 * v + 2] > 0.8f) {
                p[3 * v + 2] += 0.5f;
                moved.add(v);
            }
        }
        IntArray triangles = new IntArray();
        for (int t = 0; t < mesh.getTriangleCount(); t++) {
            for (int corner = 0; corner < 3; corner++) {
                if (p[3 * mesh.getTriangles()[3 * t + corner] + 2] > 1.2f) {
                    triangles.add(t);
                    break;
                }
            }
        }
        TriangleBvh.Hit hit = new TriangleBvh.Hit();
        // The raised cap now reaches z = 1.5, far above the old boxes (which end at z = 1). A ray that only
        // meets the raised walls, at height 1.4, can hit them once the boxes are refitted but not before.
        Ray sideways = new Ray(new Vector3(-5, 0, 1.4f), new Vector3(1, 0, 0));
        assertFalse(bvh.raycast(sideways, Float.MAX_VALUE, hit), "stale boxes do not reach the raised cap");

        bvh.refit(triangles.items, triangles.size);

        assertTrue(bvh.raycast(sideways, Float.MAX_VALUE, hit), "the raised cap is found after refitting");
        assertEquals(1.4f, hit.z, 1e-4f);
        Ray fromAbove = new Ray(new Vector3(0, 0, 5), new Vector3(0, 0, -1));
        assertTrue(bvh.raycast(fromAbove, Float.MAX_VALUE, hit));
        assertEquals(1.5f, hit.z, 0.05f);
        TriangleBvh rebuilt = new TriangleBvh(mesh);
        TriangleBvh.Hit expected = new TriangleBvh.Hit();
        for (Ray ray : SculptTestSupport.randomRays(300, 8)) {
            boolean freshHit = rebuilt.raycast(ray, Float.MAX_VALUE, expected);
            assertEquals(freshHit, bvh.raycast(ray, Float.MAX_VALUE, hit), "hit or miss");
            if (freshHit) {
                assertEquals(expected.distance, hit.distance, 1e-4f);
            }
        }
    }

    @Test
    void shouldWorkOnTinyMeshes() {
        SculptMesh triangle = new SculptMesh(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0}, new int[] {0, 1, 2});
        TriangleBvh bvh = new TriangleBvh(triangle);
        TriangleBvh.Hit hit = new TriangleBvh.Hit();
        IntArray found = new IntArray();

        assertTrue(bvh.raycast(new Ray(new Vector3(0.2f, 0.2f, 3), new Vector3(0, 0, -1)), 10f, hit));
        assertEquals(3f, hit.distance, 1e-5f);
        assertEquals(1, bvh.getNodeCount());
        bvh.collectVertices(0f, 0f, 0f, 0.5f, found);
        assertEquals(1, found.size);
    }
}
