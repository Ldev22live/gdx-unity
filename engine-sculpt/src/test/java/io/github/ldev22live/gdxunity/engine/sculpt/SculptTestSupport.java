package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.IntArray;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Helpers shared by the sculpt tests: test meshes, brute-force reference answers and consistency checks. */
final class SculptTestSupport {
    private SculptTestSupport() {
    }

    /** A unit sphere with {@code 6 * resolution^2 + 2} vertices. */
    static SculptMesh sphere(int resolution) {
        return SculptMeshFactory.cubeSphere(1f, resolution);
    }

    /** Distance of a vertex from the origin. */
    static float radius(SculptMesh mesh, int vertex) {
        float[] p = mesh.getPositions();
        return (float) Math.sqrt(p[3 * vertex] * p[3 * vertex] + p[3 * vertex + 1] * p[3 * vertex + 1]
            + p[3 * vertex + 2] * p[3 * vertex + 2]);
    }

    /** Distance of a vertex from a point. */
    static float distance(float[] positions, int vertex, float x, float y, float z) {
        float dx = positions[3 * vertex] - x;
        float dy = positions[3 * vertex + 1] - y;
        float dz = positions[3 * vertex + 2] - z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** All vertices within {@code radius} of a point, found by testing every vertex. */
    static IntArray bruteForceVertices(SculptMesh mesh, float x, float y, float z, float radius) {
        IntArray found = new IntArray();
        for (int v = 0; v < mesh.getVertexCount(); v++) {
            if (distance(mesh.getPositions(), v, x, y, z) <= radius) {
                found.add(v);
            }
        }
        return found;
    }

    /** Distance to the nearest triangle hit by testing every triangle with libGDX's own intersection code, or -1. */
    static float bruteForceHit(SculptMesh mesh, Ray ray) {
        float[] p = mesh.getPositions();
        int[] t = mesh.getTriangles();
        Vector3 a = new Vector3();
        Vector3 b = new Vector3();
        Vector3 c = new Vector3();
        Vector3 hit = new Vector3();
        float best = -1f;
        for (int i = 0; i < t.length; i += 3) {
            a.set(p[3 * t[i]], p[3 * t[i] + 1], p[3 * t[i] + 2]);
            b.set(p[3 * t[i + 1]], p[3 * t[i + 1] + 1], p[3 * t[i + 1] + 2]);
            c.set(p[3 * t[i + 2]], p[3 * t[i + 2] + 1], p[3 * t[i + 2] + 2]);
            if (Intersector.intersectRayTriangle(ray, a, b, c, hit)) {
                float d = ray.origin.dst(hit);
                if (best < 0f || d < best) {
                    best = d;
                }
            }
        }
        return best;
    }

    /** Rays from far away aimed at random points near the unit sphere. */
    static Ray[] randomRays(int count, long seed) {
        Random random = new Random(seed);
        Ray[] rays = new Ray[count];
        for (int i = 0; i < count; i++) {
            Vector3 origin = new Vector3(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f,
                random.nextFloat() - 0.5f).nor().scl(4f);
            Vector3 target = new Vector3(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f,
                random.nextFloat() - 0.5f).scl(2.4f);
            rays[i] = new Ray(origin, target.sub(origin).nor());
        }
        return rays;
    }

    /** Asserts the incrementally maintained normals equal normals computed from scratch. */
    static void assertNormalsMatchFullRecompute(SculptMesh mesh) {
        float[] incremental = mesh.getNormals().clone();
        mesh.recomputeNormals();
        assertArrayEquals(mesh.getNormals(), incremental, 1e-5f, "normals kept up to date during sculpting");
    }

    /** Asserts the surface's hierarchy gives the same hits as a hierarchy built from scratch on the same mesh. */
    static void assertBvhMatchesFreshBuild(SculptSurface surface) {
        TriangleBvh fresh = new TriangleBvh(surface.getMesh());
        TriangleBvh.Hit expected = new TriangleBvh.Hit();
        TriangleBvh.Hit actual = new TriangleBvh.Hit();
        for (Ray ray : randomRays(200, 99)) {
            boolean freshHit = fresh.raycast(ray, Float.MAX_VALUE, expected);
            boolean liveHit = surface.getBvh().raycast(ray, Float.MAX_VALUE, actual);
            assertEquals(freshHit, liveHit, "hit or miss after sculpting");
            if (freshHit) {
                assertEquals(expected.distance, actual.distance, 1e-4f, "hit distance after sculpting");
            }
        }
    }
}
