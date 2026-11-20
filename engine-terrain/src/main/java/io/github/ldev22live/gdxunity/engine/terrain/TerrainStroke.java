package io.github.ldev22live.gdxunity.engine.terrain;

import com.badlogic.gdx.utils.IntFloatMap;
import com.badlogic.gdx.utils.IntIntMap;

import java.util.Arrays;
import java.util.Objects;

/**
 * One brush stroke on a {@link Terrain} (TD-082): from {@link #begin} to {@link #end}, the brush is dabbed along the path of the
 * cursor and the heights change as it goes, so the terrain always shows the stroke. {@link #end} returns what changed, for one
 * undo step. Dabs are spaced by distance, not time: dragging twice as fast does the same work per distance, and holding still after
 * the first dab does nothing more.
 */
public final class TerrainStroke {
    /** Distance between dabs, as a fraction of the brush radius. */
    static final float DAB_SPACING = 0.25f;
    /** How far a full-strength dab raises or lowers the centre, as a fraction of the brush radius. */
    static final float HEIGHT_PER_DAB = 0.05f;
    /** Noise features are this many cells wide. */
    static final float NOISE_CELLS = 4f;

    private final Terrain terrain;
    /** Only points this far into the brush (its weight) are cut or filled as holes, so the edge of a hole is round. */
    static final float HOLE_THRESHOLD = 0.25f;
    /**
     * How fast paint builds up: a dab adds {@code strength * falloff * PAINT_RATE} of the way to a full layer, so the default strength
     * (0.5) covers the centre in one dab while the falloff leaves a soft edge (TD-089).
     */
    static final float PAINT_RATE = 2f;
    /** Weights changed this stroke, by index into the weight array, with their first value. */
    private final IntIntMap originalWeights = new IntIntMap();
    /** Hole flags changed this stroke, by point, with their first value. */
    private final IntIntMap originalHoles = new IntIntMap();
    private final int[] pointWeights = new int[Surface.LAYER_COUNT];
    /** Steepest drop, as height per unit of distance, that erosion leaves alone (about 35 degrees). */
    static final float TALUS_SLOPE = 0.7f;
    /** Share of the excess drop a full-strength erosion dab moves down in one pass. */
    static final float EROSION_RATE = 0.5f;
    /** Passes of erosion in one dab, so a single click visibly softens a cliff. */
    static final int EROSION_PASSES = 4;
    /** Share of the ramp's half width, from its edge inwards, over which it blends into the ground beside it. */
    static final float RAMP_EDGE = 0.4f;
    /** Where a ramp stroke began and where the cursor is now. */
    private float rampStartX;
    private float rampStartZ;
    private float rampEndX;
    private float rampEndZ;
    private float rampStartHeight;

    private final IntFloatMap originals = new IntFloatMap();
    private final IntIntMap originalSurfaces = new IntIntMap();
    private TerrainBrush brush;
    private boolean active;
    private float lastX;
    private float lastZ;
    private float flattenHeight;
    private float[] scratch = new float[0];

    public TerrainStroke(Terrain terrain) {
        this.terrain = Objects.requireNonNull(terrain, "terrain cannot be null");
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Starts a stroke with a dab at (x, z) in the terrain's own space. The brush is copied, so later changes to it do not affect this
     * stroke.
     *
     * @throws IllegalStateException    if a stroke is already in progress
     * @throws IllegalArgumentException if the point is not on the terrain
     */
    public void begin(TerrainBrush brush, float x, float z) {
        Objects.requireNonNull(brush, "brush cannot be null");
        if (active) {
            throw new IllegalStateException("A stroke is already in progress");
        }
        float height = terrain.heightAt(x, z);
        if (Float.isNaN(height)) {
            throw new IllegalArgumentException("The stroke must begin on the terrain: " + x + ", " + z);
        }
        this.brush = brush.copy();
        this.flattenHeight = height;
        this.originals.clear();
        this.originalSurfaces.clear();
        this.originalWeights.clear();
        this.originalHoles.clear();
        this.active = true;
        this.lastX = x;
        this.lastZ = z;
        if (this.brush.getType() == TerrainBrush.Type.RAMP) {
            // A ramp is laid when the stroke ends, from here to where it ends
            rampStartX = x;
            rampStartZ = z;
            rampEndX = x;
            rampEndZ = z;
            rampStartHeight = height;
            return;
        }
        dab(x, z);
    }

    /**
     * Carries the stroke to (x, z) with dabs along the way.
     *
     * @throws IllegalStateException if no stroke is in progress
     */
    public void move(float x, float z) {
        if (!active) {
            throw new IllegalStateException("No stroke is in progress");
        }
        if (brush.getType() == TerrainBrush.Type.RAMP) {
            if (!Float.isNaN(terrain.heightAt(x, z))) {
                rampEndX = x;
                rampEndZ = z;
            }
            return;
        }
        float dx = x - lastX;
        float dz = z - lastZ;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float spacing = brush.getRadius() * DAB_SPACING;
        int dabs = (int) (distance / spacing);
        for (int i = 1; i <= dabs; i++) {
            float t = i * spacing / distance;
            dab(lastX + dx * t, lastZ + dz * t);
        }
        if (dabs > 0) {
            float covered = dabs * spacing / distance;
            lastX += dx * covered;
            lastZ += dz * covered;
        }
    }

    /**
     * Finishes the stroke.
     *
     * @return what the stroke changed; empty when nothing moved
     * @throws IllegalStateException if no stroke is in progress
     */
    public TerrainDelta end() {
        if (!active) {
            throw new IllegalStateException("No stroke is in progress");
        }
        active = false;
        if (brush.getType() == TerrainBrush.Type.RAMP) {
            ramp();
        }
        int[] indices = new int[originals.size];
        float[] before = new float[originals.size];
        float[] after = new float[originals.size];
        int count = 0;
        float[] current = terrain.copyHeights();
        for (IntFloatMap.Entry entry : originals) {
            if (current[entry.key] != entry.value) {
                indices[count] = entry.key;
                before[count] = entry.value;
                after[count] = current[entry.key];
                count++;
            }
        }
        originals.clear();
        int[] surfaceIndices = new int[originalSurfaces.size];
        byte[] surfaceBefore = new byte[originalSurfaces.size];
        byte[] surfaceAfter = new byte[originalSurfaces.size];
        int surfaceCount = 0;
        byte[] currentSurfaces = terrain.copySurfaces();
        for (IntIntMap.Entry entry : originalSurfaces) {
            if (currentSurfaces[entry.key] != entry.value) {
                surfaceIndices[surfaceCount] = entry.key;
                surfaceBefore[surfaceCount] = (byte) entry.value;
                surfaceAfter[surfaceCount] = currentSurfaces[entry.key];
                surfaceCount++;
            }
        }
        originalSurfaces.clear();
        byte[] currentWeights = terrain.copyWeights();
        int[] weightIndices = new int[originalWeights.size];
        byte[] weightBefore = new byte[originalWeights.size];
        byte[] weightAfter = new byte[originalWeights.size];
        int weightCount = 0;
        for (IntIntMap.Entry entry : originalWeights) {
            if (currentWeights[entry.key] != (byte) entry.value) {
                weightIndices[weightCount] = entry.key;
                weightBefore[weightCount] = (byte) entry.value;
                weightAfter[weightCount] = currentWeights[entry.key];
                weightCount++;
            }
        }
        originalWeights.clear();
        byte[] currentHoles = terrain.copyHoles();
        int[] holeIndices = new int[originalHoles.size];
        byte[] holeBefore = new byte[originalHoles.size];
        byte[] holeAfter = new byte[originalHoles.size];
        int holeCount = 0;
        for (IntIntMap.Entry entry : originalHoles) {
            if (currentHoles[entry.key] != (byte) entry.value) {
                holeIndices[holeCount] = entry.key;
                holeBefore[holeCount] = (byte) entry.value;
                holeAfter[holeCount] = currentHoles[entry.key];
                holeCount++;
            }
        }
        originalHoles.clear();
        return new TerrainDelta(Arrays.copyOf(indices, count), Arrays.copyOf(before, count), Arrays.copyOf(after, count),
            Arrays.copyOf(surfaceIndices, surfaceCount), Arrays.copyOf(surfaceBefore, surfaceCount),
            Arrays.copyOf(surfaceAfter, surfaceCount), Arrays.copyOf(weightIndices, weightCount), Arrays.copyOf(weightBefore, weightCount),
            Arrays.copyOf(weightAfter, weightCount), Arrays.copyOf(holeIndices, holeCount), Arrays.copyOf(holeBefore, holeCount),
            Arrays.copyOf(holeAfter, holeCount));
    }

    private void dab(float x, float z) {
        float radius = brush.getRadius();
        float cell = terrain.getCellSize();
        float halfX = terrain.getSizeX() / 2f;
        float halfZ = terrain.getSizeZ() / 2f;
        int ixMin = Math.max(0, (int) Math.ceil((x - radius + halfX) / cell));
        int ixMax = Math.min(terrain.getResolutionX() - 1, (int) Math.floor((x + radius + halfX) / cell));
        int izMin = Math.max(0, (int) Math.ceil((z - radius + halfZ) / cell));
        int izMax = Math.min(terrain.getResolutionZ() - 1, (int) Math.floor((z + radius + halfZ) / cell));
        if (ixMin > ixMax || izMin > izMax) {
            return;
        }
        if (brush.getType() == TerrainBrush.Type.PAINT) {
            paintDab(x, z, ixMin, ixMax, izMin, izMax);
            return;
        }
        if (brush.getType() == TerrainBrush.Type.HOLE) {
            holeDab(x, z, ixMin, ixMax, izMin, izMax);
            return;
        }
        if (brush.getType() == TerrainBrush.Type.ERODE) {
            for (int pass = 0; pass < EROSION_PASSES; pass++) {
                erodeDab(x, z, ixMin, ixMax, izMin, izMax);
            }
            return;
        }
        if (brush.getType() == TerrainBrush.Type.SMOOTH) {
            smoothDab(x, z, ixMin, ixMax, izMin, izMax);
            return;
        }
        float strength = brush.getStrength();
        float step = strength * radius * HEIGHT_PER_DAB;
        for (int iz = izMin; iz <= izMax; iz++) {
            for (int ix = ixMin; ix <= ixMax; ix++) {
                float weight = weight(terrain.xAt(ix) - x, terrain.zAt(iz) - z, radius);
                if (weight <= 0f) {
                    continue;
                }
                float height = terrain.getHeight(ix, iz);
                float target;
                switch (brush.getType()) {
                    case RAISE -> target = height + (brush.isInverted() ? -step : step) * weight;
                    case LOWER -> target = height + (brush.isInverted() ? step : -step) * weight;
                    case FLATTEN -> target = height + (flattenHeight - height) * Math.min(1f, strength * weight);
                    case NOISE -> target = height + (brush.isInverted() ? -step : step) * weight * noise(terrain.xAt(ix), terrain.zAt(iz));
                    default -> throw new IllegalStateException("Unhandled brush " + brush.getType());
                }
                write(ix, iz, target);
            }
        }
    }

    /**
     * Adds the brush's surface as a layer weight, most at the centre and fading to nothing at the edge, and makes room for it by
     * scaling the point's other layers down so the weights never sum past full (TD-089). The inverted brush (Ctrl), or painting
     * {@link Surface#AUTO}, wipes every layer towards the base ground instead.
     */
    private void paintDab(float x, float z, int ixMin, int ixMax, int izMin, int izMax) {
        boolean wipe = brush.isInverted() || brush.getSurface() == Surface.AUTO;
        int target = wipe ? -1 : brush.getSurface().layer();
        float radius = brush.getRadius();
        for (int iz = izMin; iz <= izMax; iz++) {
            for (int ix = ixMin; ix <= ixMax; ix++) {
                float falloff = weight(terrain.xAt(ix) - x, terrain.zAt(iz) - z, radius);
                if (falloff <= 0f) {
                    continue;
                }
                float amount = Math.min(1f, brush.getStrength() * falloff * PAINT_RATE);
                for (int layer = 0; layer < Surface.LAYER_COUNT; layer++) {
                    pointWeights[layer] = terrain.getLayerWeight(ix, iz, Surface.ofLayer(layer));
                }
                if (wipe) {
                    for (int layer = 0; layer < Surface.LAYER_COUNT; layer++) {
                        pointWeights[layer] = Math.round(pointWeights[layer] * (1f - amount));
                    }
                } else {
                    int painted = Math.round(pointWeights[target] + (Terrain.FULL_WEIGHT - pointWeights[target]) * amount);
                    pointWeights[target] = painted;
                    int others = 0;
                    for (int layer = 0; layer < Surface.LAYER_COUNT; layer++) {
                        if (layer != target) {
                            others += pointWeights[layer];
                        }
                    }
                    int room = Terrain.FULL_WEIGHT - painted;
                    if (others > room) {
                        for (int layer = 0; layer < Surface.LAYER_COUNT; layer++) {
                            if (layer != target) {
                                pointWeights[layer] = pointWeights[layer] * room / others; // rounds down, so the sum stays within full
                            }
                        }
                    }
                }
                int point = terrain.index(ix, iz);
                for (int layer = 0; layer < Surface.LAYER_COUNT; layer++) {
                    Surface surface = Surface.ofLayer(layer);
                    int old = terrain.getLayerWeight(ix, iz, surface);
                    if (old != pointWeights[layer]) {
                        int index = Terrain.weightIndex(point, layer);
                        if (!originalWeights.containsKey(index)) {
                            originalWeights.put(index, old);
                        }
                        terrain.setLayerWeight(ix, iz, surface, pointWeights[layer]);
                    }
                }
            }
        }
    }

    /** Cuts holes at the points well inside the brush, or fills them when the brush is inverted (Ctrl). */
    private void holeDab(float x, float z, int ixMin, int ixMax, int izMin, int izMax) {
        boolean cut = !brush.isInverted();
        float radius = brush.getRadius();
        for (int iz = izMin; iz <= izMax; iz++) {
            for (int ix = ixMin; ix <= ixMax; ix++) {
                if (weight(terrain.xAt(ix) - x, terrain.zAt(iz) - z, radius) >= HOLE_THRESHOLD && terrain.isHole(ix, iz) != cut) {
                    int index = terrain.index(ix, iz);
                    if (!originalHoles.containsKey(index)) {
                        originalHoles.put(index, terrain.isHole(ix, iz) ? 1 : 0);
                    }
                    terrain.setHole(ix, iz, cut);
                }
            }
        }
    }

    /**
     * One pass of thermal erosion over the brush's area, read from a snapshot so the order of the points does not matter: each point
     * steeper than {@link #TALUS_SLOPE} towards its lowest neighbour gives that neighbour part of the excess, weighted by the brush.
     * What one point loses its neighbour gains, so the ground is moved, not lost.
     */
    private void erodeDab(float x, float z, int ixMin, int ixMax, int izMin, int izMax) {
        int x0 = Math.max(0, ixMin - 1);
        int x1 = Math.min(terrain.getResolutionX() - 1, ixMax + 1);
        int z0 = Math.max(0, izMin - 1);
        int z1 = Math.min(terrain.getResolutionZ() - 1, izMax + 1);
        int width = x1 - x0 + 1;
        int depth = z1 - z0 + 1;
        if (scratch.length < width * depth) {
            scratch = new float[width * depth];
        }
        Arrays.fill(scratch, 0, width * depth, 0f);
        float radius = brush.getRadius();
        float cell = terrain.getCellSize();
        float rate = EROSION_RATE * brush.getStrength();
        for (int iz = izMin; iz <= izMax; iz++) {
            for (int ix = ixMin; ix <= ixMax; ix++) {
                float weight = weight(terrain.xAt(ix) - x, terrain.zAt(iz) - z, radius);
                if (weight <= 0f) {
                    continue;
                }
                float height = terrain.getHeight(ix, iz);
                int lowX = -1;
                int lowZ = -1;
                float steepest = 0f;
                float drop = 0f;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = ix + dx;
                        int nz = iz + dz;
                        if ((dx == 0 && dz == 0) || nx < x0 || nx > x1 || nz < z0 || nz > z1) {
                            continue;
                        }
                        float distance = cell * (float) Math.sqrt(dx * dx + dz * dz);
                        float slope = (height - terrain.getHeight(nx, nz)) / distance;
                        if (slope > steepest) {
                            steepest = slope;
                            lowX = nx;
                            lowZ = nz;
                            drop = distance;
                        }
                    }
                }
                if (lowX < 0 || steepest <= TALUS_SLOPE) {
                    continue;
                }
                // Moving m from here to there lowers the drop by 2m; take part of the excess, never more than levels the pair
                float excess = (steepest - TALUS_SLOPE) * drop;
                float moved = excess * 0.5f * Math.min(1f, rate * weight);
                scratch[(iz - z0) * width + (ix - x0)] -= moved;
                scratch[(lowZ - z0) * width + (lowX - x0)] += moved;
            }
        }
        for (int iz = z0; iz <= z1; iz++) {
            for (int ix = x0; ix <= x1; ix++) {
                float change = scratch[(iz - z0) * width + (ix - x0)];
                if (change != 0f) {
                    write(ix, iz, terrain.getHeight(ix, iz) + change);
                }
            }
        }
    }

    /**
     * Lays the ramp from the stroke's start to its end: every point within half the brush width of the line, between its two ends,
     * moves to the height the line has there, rising evenly from the start's height to the end's, fully in the middle and blending to
     * nothing over the outer {@link #RAMP_EDGE} of each side.
     */
    private void ramp() {
        float dx = rampEndX - rampStartX;
        float dz = rampEndZ - rampStartZ;
        float lengthSquared = dx * dx + dz * dz;
        float cell = terrain.getCellSize();
        if (lengthSquared < cell * cell) {
            return; // a click, not a drag: there is no line to lay a ramp along
        }
        float endHeight = terrain.heightAt(rampEndX, rampEndZ);
        float half = brush.getRadius();
        float halfX = terrain.getSizeX() / 2f;
        float halfZ = terrain.getSizeZ() / 2f;
        int ixMin = Math.max(0, (int) Math.ceil((Math.min(rampStartX, rampEndX) - half + halfX) / cell));
        int ixMax = Math.min(terrain.getResolutionX() - 1, (int) Math.floor((Math.max(rampStartX, rampEndX) + half + halfX) / cell));
        int izMin = Math.max(0, (int) Math.ceil((Math.min(rampStartZ, rampEndZ) - half + halfZ) / cell));
        int izMax = Math.min(terrain.getResolutionZ() - 1, (int) Math.floor((Math.max(rampStartZ, rampEndZ) + half + halfZ) / cell));
        float inner = half * (1f - RAMP_EDGE);
        for (int iz = izMin; iz <= izMax; iz++) {
            for (int ix = ixMin; ix <= ixMax; ix++) {
                float px = terrain.xAt(ix) - rampStartX;
                float pz = terrain.zAt(iz) - rampStartZ;
                float t = (px * dx + pz * dz) / lengthSquared;
                if (t < 0f || t > 1f) {
                    continue;
                }
                float side = (float) Math.sqrt(Math.max(0f, px * px + pz * pz - t * t * lengthSquared));
                if (side >= half) {
                    continue;
                }
                float blend = side <= inner ? 1f : smooth(1f - (side - inner) / (half - inner));
                float target = rampStartHeight + (endHeight - rampStartHeight) * t;
                float height = terrain.getHeight(ix, iz);
                write(ix, iz, height + (target - height) * blend);
            }
        }
    }

    /** Smooths from a snapshot of the area, so the result does not depend on the order the points are visited in. */
    private void smoothDab(float x, float z, int ixMin, int ixMax, int izMin, int izMax) {
        int width = ixMax - ixMin + 1;
        int depth = izMax - izMin + 1;
        if (scratch.length < width * depth) {
            scratch = new float[width * depth];
        }
        float radius = brush.getRadius();
        for (int iz = izMin; iz <= izMax; iz++) {
            for (int ix = ixMin; ix <= ixMax; ix++) {
                float weight = weight(terrain.xAt(ix) - x, terrain.zAt(iz) - z, radius);
                float height = terrain.getHeight(ix, iz);
                float result = height;
                if (weight > 0f) {
                    float sum = 0f;
                    int neighbours = 0;
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = ix + dx;
                            int nz = iz + dz;
                            if ((dx != 0 || dz != 0) && nx >= 0 && nx < terrain.getResolutionX() && nz >= 0 && nz < terrain.getResolutionZ()) {
                                sum += terrain.getHeight(nx, nz);
                                neighbours++;
                            }
                        }
                    }
                    result = height + (sum / neighbours - height) * Math.min(1f, brush.getStrength() * weight);
                }
                scratch[(iz - izMin) * width + (ix - ixMin)] = result;
            }
        }
        for (int iz = izMin; iz <= izMax; iz++) {
            for (int ix = ixMin; ix <= ixMax; ix++) {
                write(ix, iz, scratch[(iz - izMin) * width + (ix - ixMin)]);
            }
        }
    }

    /** Remembers the first height a point had in this stroke, then sets the new one. */
    private void write(int ix, int iz, float height) {
        int index = terrain.index(ix, iz);
        if (!originals.containsKey(index)) {
            originals.put(index, terrain.getHeight(ix, iz));
        }
        terrain.setHeight(ix, iz, height);
    }

    /** 1 at the centre falling smoothly to 0 at the radius. */
    private static float weight(float dx, float dz, float radius) {
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        if (distance >= radius) {
            return 0f;
        }
        float s = 1f - distance / radius;
        return s * s * (3f - 2f * s);
    }

    /** Smooth value noise in -1 to 1 at a point, the same everywhere for the same point. */
    private float noise(float x, float z) {
        float size = terrain.getCellSize() * NOISE_CELLS;
        float fx = x / size;
        float fz = z / size;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        float tx = smooth(fx - x0);
        float tz = smooth(fz - z0);
        float a = lattice(x0, z0);
        float b = lattice(x0 + 1, z0);
        float c = lattice(x0, z0 + 1);
        float d = lattice(x0 + 1, z0 + 1);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    /** A fixed pseudo-random value in -1 to 1 for each lattice point. */
    private static float lattice(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return ((h & 0xFFFF) / 32767.5f) - 1f;
    }
}
