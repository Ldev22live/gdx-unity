package io.github.ldev22live.gdxunity.engine.terrain;

import java.util.Arrays;
import java.util.Objects;

/**
 * A road, river or cliff along a curve over a {@link Terrain} (roadmap M3.7). The curve is a Catmull-Rom spline through control points
 * given in the terrain's own space (x and z), so it passes through every point. {@link #carve} shapes the ground along it and paints
 * it; {@link #sample} gives evenly spaced points on it, for placing meshes. Pure maths, no scene or graphics.
 *
 * <ul>
 *   <li><b>Road</b>: a level strip whose height is the ground's along the curve, smoothed and with its grade limited to
 *       {@link #MAX_ROAD_GRADE}, so it climbs a hill evenly instead of following every bump.</li>
 *   <li><b>River</b>: a channel {@code depth} below the smoothed ground, its bed never rising along the curve (water runs downhill),
 *       deepest in the middle.</li>
 *   <li><b>Cliff</b>: the ground on the curve's left (seen walking from the first point to the last) raised by {@code depth}, with a
 *       steep face along the curve.</li>
 * </ul>
 * Each blends into the ground beside it over {@link #EDGE_SHARE} of its half width. A road's or river's ends are rounded (the ground
 * nearest an end point takes the end's shape).
 */
public final class TerrainPath {
    /** What a path makes of the ground. */
    public enum Kind { ROAD, RIVER, CLIFF }

    /** Steepest a road climbs: height per unit of length. */
    public static final float MAX_ROAD_GRADE = 0.25f;
    /** Share of the half width, outside it, over which a path blends into the ground beside it. */
    public static final float EDGE_SHARE = 0.6f;
    /** Length of the stretch a road or river averages the ground over, in path widths. */
    static final float SMOOTHING_WIDTHS = 3f;
    /** Samples per cell along the curve. */
    static final float SAMPLES_PER_CELL = 2f;
    /** Share of a cliff's width taken by its face. */
    static final float CLIFF_FACE_SHARE = 0.2f;

    /** Evenly spaced points on a curve, with the distance along it to each. */
    public record Samples(float[] x, float[] z, float[] distance) {
        public int size() {
            return x.length;
        }

        /** The curve's length. */
        public float length() {
            return distance.length == 0 ? 0f : distance[distance.length - 1];
        }
    }

    private TerrainPath() { }

    /**
     * Points on the Catmull-Rom curve through the control points, about {@code spacing} apart along it (first and last control points
     * included).
     *
     * @throws IllegalArgumentException with fewer than two points, mismatched arrays, or a spacing that is not above 0
     */
    public static Samples sample(float[] xs, float[] zs, float spacing) {
        Objects.requireNonNull(xs, "xs cannot be null");
        Objects.requireNonNull(zs, "zs cannot be null");
        if (xs.length != zs.length || xs.length < 2) {
            throw new IllegalArgumentException("A path needs at least two points, with an x and a z each: " + xs.length + ", " + zs.length);
        }
        if (!(spacing > 0f)) {
            throw new IllegalArgumentException("Spacing must be above 0: " + spacing);
        }
        // Dense points first, then resampled evenly by distance
        int segments = xs.length - 1;
        int dense = 64;
        float[] dx = new float[segments * dense + 1];
        float[] dz = new float[segments * dense + 1];
        float[] dd = new float[segments * dense + 1];
        int n = 0;
        for (int s = 0; s < segments; s++) {
            int p0 = Math.max(0, s - 1);
            int p3 = Math.min(xs.length - 1, s + 2);
            for (int k = 0; k < dense; k++) {
                float t = k / (float) dense;
                dx[n] = catmullRom(xs[p0], xs[s], xs[s + 1], xs[p3], t);
                dz[n] = catmullRom(zs[p0], zs[s], zs[s + 1], zs[p3], t);
                n++;
            }
        }
        dx[n] = xs[xs.length - 1];
        dz[n] = zs[zs.length - 1];
        n++;
        for (int i = 1; i < n; i++) {
            dd[i] = dd[i - 1] + (float) Math.hypot(dx[i] - dx[i - 1], dz[i] - dz[i - 1]);
        }
        float length = dd[n - 1];
        int count = Math.max(2, (int) Math.ceil(length / spacing) + 1);
        float[] x = new float[count];
        float[] z = new float[count];
        float[] d = new float[count];
        int j = 1;
        for (int i = 0; i < count; i++) {
            float target = length * i / (count - 1);
            while (j < n - 1 && dd[j] < target) {
                j++;
            }
            float span = dd[j] - dd[j - 1];
            float t = span <= 0f ? 0f : Math.max(0f, Math.min(1f, (target - dd[j - 1]) / span));
            x[i] = dx[j - 1] + (dx[j] - dx[j - 1]) * t;
            z[i] = dz[j - 1] + (dz[j] - dz[j - 1]) * t;
            d[i] = target;
        }
        return new Samples(x, z, d);
    }

    /**
     * Shapes and paints the terrain along the path, and returns what changed for one undo step; the terrain shows the change already.
     *
     * @param width full width of the road, river or cliff, in world units
     * @param depth how far a river cuts below the ground or a cliff rises above it (unused for a road)
     * @param paint the paint layer laid along it, or {@link Surface#AUTO} for none
     */
    public static TerrainDelta carve(Terrain terrain, float[] xs, float[] zs, Kind kind, float width, float depth, Surface paint) {
        Objects.requireNonNull(terrain, "terrain cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(paint, "paint cannot be null");
        if (!(width > 0f)) {
            throw new IllegalArgumentException("Width must be above 0: " + width);
        }
        if (!(depth >= 0f)) {
            throw new IllegalArgumentException("Depth must be 0 or more: " + depth);
        }
        float cell = terrain.getCellSize();
        Samples line = sample(xs, zs, cell / SAMPLES_PER_CELL);
        float[] profile = profile(terrain, line, kind, width, depth);
        float half = width / 2f;
        float reach = half * (1f + EDGE_SHARE);
        float[] heightsBefore = terrain.copyHeights();
        byte[] weightsBefore = terrain.copyWeights();

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < line.size(); i++) {
            minX = Math.min(minX, line.x()[i]);
            maxX = Math.max(maxX, line.x()[i]);
            minZ = Math.min(minZ, line.z()[i]);
            maxZ = Math.max(maxZ, line.z()[i]);
        }
        float halfX = terrain.getSizeX() / 2f;
        float halfZ = terrain.getSizeZ() / 2f;
        float box = kind == Kind.CLIFF ? width + reach : reach; // a cliff lifts ground up to width + reach to its left
        int ixMin = Math.max(0, (int) Math.ceil((minX - box + halfX) / cell));
        int ixMax = Math.min(terrain.getResolutionX() - 1, (int) Math.floor((maxX + box + halfX) / cell));
        int izMin = Math.max(0, (int) Math.ceil((minZ - box + halfZ) / cell));
        int izMax = Math.min(terrain.getResolutionZ() - 1, (int) Math.floor((maxZ + box + halfZ) / cell));
        for (int iz = izMin; iz <= izMax; iz++) {
            for (int ix = ixMin; ix <= ixMax; ix++) {
                float px = terrain.xAt(ix);
                float pz = terrain.zAt(iz);
                int nearest = nearest(line, px, pz);
                float side = signedSide(line, nearest, px, pz);
                float distance = Math.abs(side);
                if (kind != Kind.CLIFF && distance >= reach) {
                    continue;
                }
                float height = terrain.getHeight(ix, iz);
                float target;
                float blend;
                switch (kind) {
                    case ROAD -> {
                        target = profile[nearest];
                        blend = edgeBlend(distance, half, reach);
                    }
                    case RIVER -> {
                        float across = Math.min(1f, distance / half);
                        target = profile[nearest] + depth * across * across;
                        blend = edgeBlend(distance, half, reach);
                    }
                    default -> {
                        // Cliff: the left side (positive) is lifted; the face is the first CLIFF_FACE_SHARE of the width
                        if (side <= 0f || side >= width + reach) {
                            continue;
                        }
                        float face = width * CLIFF_FACE_SHARE;
                        float rise = side >= face ? 1f : smooth(side / face);
                        float fade = side <= width ? 1f : smooth(1f - (side - width) / reach);
                        target = height + depth * rise * fade;
                        blend = 1f;
                    }
                }
                if (blend > 0f) {
                    terrain.setHeight(ix, iz, height + (target - height) * blend);
                }
                if (paint != Surface.AUTO && kind != Kind.CLIFF) {
                    paintPoint(terrain, ix, iz, paint, edgeBlend(distance, half, reach));
                }
            }
        }
        return diff(terrain, heightsBefore, weightsBefore);
    }

    /** The path's height at each sample: the ground smoothed along it, then shaped for its kind. */
    private static float[] profile(Terrain terrain, Samples line, Kind kind, float width, float depth) {
        int n = line.size();
        float[] ground = new float[n];
        for (int i = 0; i < n; i++) {
            ground[i] = terrain.heightAt(line.x()[i], line.z()[i]);
        }
        float step = n > 1 ? line.distance()[1] - line.distance()[0] : 1f;
        int window = Math.max(1, Math.round(width * SMOOTHING_WIDTHS / step / 2f));
        float[] smooth = new float[n];
        for (int i = 0; i < n; i++) {
            float sum = 0f;
            int count = 0;
            for (int k = Math.max(0, i - window); k <= Math.min(n - 1, i + window); k++) {
                sum += ground[k];
                count++;
            }
            smooth[i] = sum / count;
        }
        switch (kind) {
            case ROAD -> {
                // Keep the ends where the ground is, then hold every step within the grade, both ways
                smooth[0] = ground[0];
                smooth[n - 1] = ground[n - 1];
                float limit = MAX_ROAD_GRADE * step;
                for (int pass = 0; pass < 2; pass++) {
                    for (int i = 1; i < n; i++) {
                        smooth[i] = clamp(smooth[i], smooth[i - 1] - limit, smooth[i - 1] + limit);
                    }
                    for (int i = n - 2; i >= 0; i--) {
                        smooth[i] = clamp(smooth[i], smooth[i + 1] - limit, smooth[i + 1] + limit);
                    }
                }
                return smooth;
            }
            case RIVER -> {
                float[] bed = new float[n];
                bed[0] = smooth[0] - depth;
                for (int i = 1; i < n; i++) {
                    bed[i] = Math.min(bed[i - 1], smooth[i] - depth); // never rising: water runs downhill
                }
                return bed;
            }
            default -> {
                return smooth;
            }
        }
    }

    /** Raises the paint layer's weight at a point to at least {@code amount} of full, scaling the other layers down to make room. */
    private static void paintPoint(Terrain terrain, int ix, int iz, Surface paint, float amount) {
        if (amount <= 0f) {
            return;
        }
        int wanted = Math.round(Terrain.FULL_WEIGHT * amount);
        int current = terrain.getLayerWeight(ix, iz, paint);
        if (wanted <= current) {
            return;
        }
        int others = 0;
        for (int layer = 0; layer < Surface.LAYER_COUNT; layer++) {
            if (layer != paint.layer()) {
                others += terrain.getLayerWeight(ix, iz, Surface.ofLayer(layer));
            }
        }
        int room = Terrain.FULL_WEIGHT - wanted;
        if (others > room) {
            for (int layer = 0; layer < Surface.LAYER_COUNT; layer++) {
                if (layer != paint.layer()) {
                    Surface other = Surface.ofLayer(layer);
                    terrain.setLayerWeight(ix, iz, other, terrain.getLayerWeight(ix, iz, other) * room / others);
                }
            }
        }
        terrain.setLayerWeight(ix, iz, paint, wanted);
    }

    /** 1 inside the half width, falling smoothly to 0 at {@code reach}. */
    private static float edgeBlend(float distance, float half, float reach) {
        if (distance <= half) {
            return 1f;
        }
        if (distance >= reach) {
            return 0f;
        }
        return smooth(1f - (distance - half) / (reach - half));
    }

    private static int nearest(Samples line, float px, float pz) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < line.size(); i++) {
            float dx = line.x()[i] - px;
            float dz = line.z()[i] - pz;
            float d = dx * dx + dz * dz;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    /** Distance from the curve near sample {@code i}, positive on the left of the direction of travel, negative on the right. */
    private static float signedSide(Samples line, int i, float px, float pz) {
        int a = Math.max(0, i - 1);
        int b = Math.min(line.size() - 1, i + 1);
        float tx = line.x()[b] - line.x()[a];
        float tz = line.z()[b] - line.z()[a];
        float length = (float) Math.hypot(tx, tz);
        float dx = px - line.x()[i];
        float dz = pz - line.z()[i];
        if (length <= 0f) {
            return (float) Math.hypot(dx, dz);
        }
        // Left of travel along +x is -z: the cross product's sign picks the side
        return (tx * dz - tz * dx) / length * -1f;
    }

    private static TerrainDelta diff(Terrain terrain, float[] heightsBefore, byte[] weightsBefore) {
        float[] heights = terrain.copyHeights();
        int[] indices = new int[heights.length];
        float[] before = new float[heights.length];
        float[] after = new float[heights.length];
        int count = 0;
        for (int i = 0; i < heights.length; i++) {
            if (heights[i] != heightsBefore[i]) {
                indices[count] = i;
                before[count] = heightsBefore[i];
                after[count] = heights[i];
                count++;
            }
        }
        byte[] weights = terrain.copyWeights();
        int[] weightIndices = new int[weights.length];
        byte[] weightWas = new byte[weights.length];
        byte[] weightNow = new byte[weights.length];
        int weightCount = 0;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] != weightsBefore[i]) {
                weightIndices[weightCount] = i;
                weightWas[weightCount] = weightsBefore[i];
                weightNow[weightCount] = weights[i];
                weightCount++;
            }
        }
        return new TerrainDelta(Arrays.copyOf(indices, count), Arrays.copyOf(before, count), Arrays.copyOf(after, count), new int[0],
            new byte[0], new byte[0], Arrays.copyOf(weightIndices, weightCount), Arrays.copyOf(weightWas, weightCount),
            Arrays.copyOf(weightNow, weightCount), new int[0], new byte[0], new byte[0]);
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    private static float smooth(float t) {
        float c = clamp(t, 0f, 1f);
        return c * c * (3f - 2f * c);
    }
}
