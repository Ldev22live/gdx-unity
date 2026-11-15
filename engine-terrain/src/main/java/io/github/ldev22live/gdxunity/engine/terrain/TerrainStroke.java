package io.github.ldev22live.gdxunity.engine.terrain;

import com.badlogic.gdx.utils.IntFloatMap;
import com.badlogic.gdx.utils.IntIntMap;

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
    /** Only points this far into the brush (its weight) get the painted surface, so the edge of a patch is round. */
    static final float PAINT_THRESHOLD = 0.25f;

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
        this.active = true;
        this.lastX = x;
        this.lastZ = z;
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
        return new TerrainDelta(java.util.Arrays.copyOf(indices, count), java.util.Arrays.copyOf(before, count),
            java.util.Arrays.copyOf(after, count), java.util.Arrays.copyOf(surfaceIndices, surfaceCount),
            java.util.Arrays.copyOf(surfaceBefore, surfaceCount), java.util.Arrays.copyOf(surfaceAfter, surfaceCount));
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

    /** Lays the brush's surface on the points inside the brush; the inverted brush (Ctrl) lays {@link Surface#AUTO} instead. */
    private void paintDab(float x, float z, int ixMin, int ixMax, int izMin, int izMax) {
        Surface surface = brush.isInverted() ? Surface.AUTO : brush.getSurface();
        float radius = brush.getRadius();
        for (int iz = izMin; iz <= izMax; iz++) {
            for (int ix = ixMin; ix <= ixMax; ix++) {
                if (weight(terrain.xAt(ix) - x, terrain.zAt(iz) - z, radius) >= PAINT_THRESHOLD) {
                    int index = terrain.index(ix, iz);
                    if (!originalSurfaces.containsKey(index)) {
                        originalSurfaces.put(index, terrain.getSurface(ix, iz).code());
                    }
                    terrain.setSurface(ix, iz, surface);
                }
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
