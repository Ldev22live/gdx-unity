package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Point-cloud helpers for convex hull colliders (TD-052). */
final class ColliderPoints3D {

    /** Points closer than this (meters) count as one. */
    private static final float MERGE_DISTANCE = 1e-4f;

    private ColliderPoints3D() {}

    /**
     * Removes duplicate points and, when more than {@code maxPoints} remain, keeps the {@code maxPoints} that spread out the
     * most (farthest-point sampling, starting from the point farthest from the center), which keeps the extreme corners a hull
     * is made of.
     *
     * @param points x, y, z per point
     * @return x, y, z of the kept points
     */
    static float[] reduce(float[] points, int maxPoints) {
        List<float[]> unique = new ArrayList<>();
        float cell = MERGE_DISTANCE;
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < points.length; i += 3) {
            long key = 31L * (31L * Math.round(points[i] / cell) + Math.round(points[i + 1] / cell)) + Math.round(points[i + 2] / cell);
            if (seen.add(key)) {
                unique.add(new float[] {points[i], points[i + 1], points[i + 2]});
            }
        }
        List<float[]> kept = unique.size() <= maxPoints ? unique : sample(unique, maxPoints);
        float[] result = new float[kept.size() * 3];
        for (int i = 0; i < kept.size(); i++) {
            System.arraycopy(kept.get(i), 0, result, i * 3, 3);
        }
        return result;
    }

    private static List<float[]> sample(List<float[]> points, int count) {
        float cx = 0f;
        float cy = 0f;
        float cz = 0f;
        for (float[] p : points) {
            cx += p[0];
            cy += p[1];
            cz += p[2];
        }
        cx /= points.size();
        cy /= points.size();
        cz /= points.size();
        float[] distance = new float[points.size()];
        int first = 0;
        float best = -1f;
        for (int i = 0; i < points.size(); i++) {
            float d = squared(points.get(i), cx, cy, cz);
            if (d > best) {
                best = d;
                first = i;
            }
        }
        List<float[]> kept = new ArrayList<>(count);
        Arrays.fill(distance, Float.MAX_VALUE);
        int next = first;
        while (kept.size() < count) {
            float[] chosen = points.get(next);
            kept.add(chosen);
            best = -1f;
            for (int i = 0; i < points.size(); i++) {
                distance[i] = Math.min(distance[i], squared(points.get(i), chosen[0], chosen[1], chosen[2]));
                if (distance[i] > best) {
                    best = distance[i];
                    next = i;
                }
            }
        }
        return kept;
    }

    private static float squared(float[] p, float x, float y, float z) {
        float dx = p[0] - x;
        float dy = p[1] - y;
        float dz = p[2] - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
