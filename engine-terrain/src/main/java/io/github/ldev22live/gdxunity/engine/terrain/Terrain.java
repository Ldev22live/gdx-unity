package io.github.ldev22live.gdxunity.engine.terrain;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

import java.util.Arrays;
import java.util.Objects;

/**
 * A heightmap (TD-082): a grid of {@code resolutionX} by {@code resolutionZ} height values, {@code cellSize} apart, centred on the
 * origin of its own space (x and z run from {@code -size/2} to {@code +size/2}, y is up). Shared by everything that names the
 * {@code .terrain} file.
 *
 * <p>The grid is divided into chunks of {@link #CHUNK_CELLS} cells; every height change counts a revision on each chunk whose mesh it
 * affects (the chunk holding the vertex, and the neighbours that share it or whose normals read it), so a renderer rebuilds only what
 * changed. Not thread safe: the editor and the game loop use it from one thread.
 */
public final class Terrain {
    /** Smallest and largest grid, in vertices per side. 1025 is a 1024 by 1024 cell map. */
    public static final int MIN_RESOLUTION = 2;
    public static final int MAX_RESOLUTION = 1025;
    /** Cells per chunk side; a chunk has (CHUNK_CELLS + 1) squared vertices, which fits 16-bit mesh indices. */
    public static final int CHUNK_CELLS = 32;
    /** Heights are kept within plus or minus this, in world units. */
    public static final float MAX_ABS_HEIGHT = 100_000f;
    /** Range of the distance between grid points, in world units. */
    public static final float MIN_CELL_SIZE = 0.01f;
    public static final float MAX_CELL_SIZE = 10_000f;

    /** Where a ray met the terrain, in the terrain's own space. */
    public static final class Hit {
        public float x;
        public float y;
        public float z;
        public float nx;
        public float ny;
        public float nz;
        /** Distance along the ray. */
        public float distance;
    }

    private String name = "Terrain";
    private final int resolutionX;
    private final int resolutionZ;
    private final float cellSize;
    private final float[] heights;
    /** The {@link Surface} code of every grid point, laid out like the heights: the base ground. */
    private final byte[] surfaces;
    /**
     * Paint layer weights (roadmap M3.4, TD-089): {@link Surface#LAYER_COUNT} unsigned bytes per grid point, 0 to {@value #FULL_WEIGHT},
     * point after point. Their sum never exceeds {@value #FULL_WEIGHT}; what is left over is the base ground.
     */
    private final byte[] weights;
    /** 1 where a grid point is a hole (TD-089): nothing is drawn or collides in the cells around it. */
    private final byte[] holes;
    /** Height of the water surface, or NaN for no water (TD-089). */
    private float waterLevel = Float.NaN;
    private final int chunksX;
    private final int chunksZ;
    private final int[] chunkRevisions;
    private int revision;

    /**
     * A flat terrain at height 0.
     *
     * @throws IllegalArgumentException if a resolution or the cell size is outside its range
     */
    public Terrain(int resolutionX, int resolutionZ, float cellSize) {
        if (resolutionX < MIN_RESOLUTION || resolutionX > MAX_RESOLUTION || resolutionZ < MIN_RESOLUTION || resolutionZ > MAX_RESOLUTION) {
            throw new IllegalArgumentException("Resolution must be " + MIN_RESOLUTION + " to " + MAX_RESOLUTION + ": " + resolutionX + " x " + resolutionZ);
        }
        if (!(cellSize >= MIN_CELL_SIZE && cellSize <= MAX_CELL_SIZE)) {
            throw new IllegalArgumentException("Cell size must be " + MIN_CELL_SIZE + " to " + MAX_CELL_SIZE + ": " + cellSize);
        }
        this.resolutionX = resolutionX;
        this.resolutionZ = resolutionZ;
        this.cellSize = cellSize;
        this.heights = new float[resolutionX * resolutionZ];
        this.surfaces = new byte[resolutionX * resolutionZ];
        this.weights = new byte[resolutionX * resolutionZ * Surface.LAYER_COUNT];
        this.holes = new byte[resolutionX * resolutionZ];
        this.chunksX = chunkCount(resolutionX);
        this.chunksZ = chunkCount(resolutionZ);
        this.chunkRevisions = new int[chunksX * chunksZ];
    }

    private static int chunkCount(int resolution) {
        return (resolution - 1 + CHUNK_CELLS - 1) / CHUNK_CELLS;
    }

    /** Display name, never blank. */
    public String getName() {
        return name;
    }

    /** @throws IllegalArgumentException if {@code name} is null or blank */
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A terrain needs a name");
        }
        this.name = name.trim();
        revision++;
    }

    public int getResolutionX() {
        return resolutionX;
    }

    public int getResolutionZ() {
        return resolutionZ;
    }

    public float getCellSize() {
        return cellSize;
    }

    /** Width along x, in world units. */
    public float getSizeX() {
        return (resolutionX - 1) * cellSize;
    }

    /** Depth along z, in world units. */
    public float getSizeZ() {
        return (resolutionZ - 1) * cellSize;
    }

    /** The x of grid column {@code ix}. */
    public float xAt(int ix) {
        return -getSizeX() / 2f + ix * cellSize;
    }

    /** The z of grid row {@code iz}. */
    public float zAt(int iz) {
        return -getSizeZ() / 2f + iz * cellSize;
    }

    /** @throws IndexOutOfBoundsException if the point is not on the grid */
    public float getHeight(int ix, int iz) {
        return heights[index(ix, iz)];
    }

    /**
     * Sets the height of one grid point. A value beyond {@link #MAX_ABS_HEIGHT} is held at the limit.
     *
     * @throws IllegalArgumentException  if {@code height} is not a finite number
     * @throws IndexOutOfBoundsException if the point is not on the grid
     */
    public void setHeight(int ix, int iz, float height) {
        if (!Float.isFinite(height)) {
            throw new IllegalArgumentException("Height must be a finite number: " + height);
        }
        int i = index(ix, iz);
        float clamped = Math.max(-MAX_ABS_HEIGHT, Math.min(MAX_ABS_HEIGHT, height));
        if (heights[i] != clamped) {
            heights[i] = clamped;
            touch(ix, iz);
        }
    }

    /** @throws IndexOutOfBoundsException if the point is not on the grid */
    public Surface getSurface(int ix, int iz) {
        return Surface.fromCode(surfaces[index(ix, iz)]);
    }

    /**
     * Sets the ground of one grid point.
     *
     * @throws IndexOutOfBoundsException if the point is not on the grid
     */
    public void setSurface(int ix, int iz, Surface surface) {
        Objects.requireNonNull(surface, "surface cannot be null");
        int i = index(ix, iz);
        if (surfaces[i] != surface.code()) {
            surfaces[i] = surface.code();
            touch(ix, iz);
        }
    }

    /** Makes every grid point this surface: the base of a level. */
    public void fillSurface(Surface surface) {
        Objects.requireNonNull(surface, "surface cannot be null");
        for (int iz = 0; iz < resolutionZ; iz++) {
            for (int ix = 0; ix < resolutionX; ix++) {
                setSurface(ix, iz, surface);
            }
        }
    }

    /** The surface codes, row by row along x, as a copy. */
    public byte[] copySurfaces() {
        return surfaces.clone();
    }

    /** True when any point has a surface other than {@link Surface#AUTO}, so a file needs to store them. */
    public boolean hasSurfaces() {
        for (byte code : surfaces) {
            if (code != Surface.AUTO.code()) {
                return true;
            }
        }
        return false;
    }

    // ---- paint layers, holes and water (roadmap M3.4, TD-089) ---------------------------------------------------------

    /** A layer weight meaning "all this layer". */
    public static final int FULL_WEIGHT = 255;

    /** Index into the weight array of layer {@code layer} at grid point {@code pointIndex}. */
    public static int weightIndex(int pointIndex, int layer) {
        return pointIndex * Surface.LAYER_COUNT + layer;
    }

    /** The weight of {@code layer}'s surface at a grid point, 0 to {@value #FULL_WEIGHT}. */
    public int getLayerWeight(int ix, int iz, Surface layer) {
        return weights[weightIndex(index(ix, iz), layer.layer())] & 0xFF;
    }

    /**
     * Sets the weight of one paint layer at a grid point, 0 to {@value #FULL_WEIGHT}. The caller keeps the point's weights summing to
     * at most {@value #FULL_WEIGHT}; {@link TerrainStroke} does.
     *
     * @throws IllegalArgumentException if the weight is outside 0 to {@value #FULL_WEIGHT}
     */
    public void setLayerWeight(int ix, int iz, Surface layer, int weight) {
        if (weight < 0 || weight > FULL_WEIGHT) {
            throw new IllegalArgumentException("A layer weight is 0 to " + FULL_WEIGHT + ": " + weight);
        }
        int i = weightIndex(index(ix, iz), layer.layer());
        if ((weights[i] & 0xFF) != weight) {
            weights[i] = (byte) weight;
            touch(ix, iz);
        }
    }

    /** How much of the base ground still shows at a grid point, 0 to {@value #FULL_WEIGHT}. */
    public int getBaseWeight(int ix, int iz) {
        int point = index(ix, iz);
        int sum = 0;
        for (int layer = 0; layer < Surface.LAYER_COUNT; layer++) {
            sum += weights[weightIndex(point, layer)] & 0xFF;
        }
        return Math.max(0, FULL_WEIGHT - sum);
    }

    /** The ground that shows most at a grid point: the base or the heaviest paint layer (the base wins a tie). */
    public Surface dominantSurface(int ix, int iz) {
        int point = index(ix, iz);
        Surface best = Surface.fromCode(surfaces[point]);
        int bestWeight = getBaseWeight(ix, iz);
        for (int layer = 0; layer < Surface.LAYER_COUNT; layer++) {
            int weight = weights[weightIndex(point, layer)] & 0xFF;
            if (weight > bestWeight) {
                bestWeight = weight;
                best = Surface.ofLayer(layer);
            }
        }
        return best;
    }

    /** The layer weights, point after point, as a copy. */
    public byte[] copyWeights() {
        return weights.clone();
    }

    /** True when any layer is painted anywhere, so a file needs to store the weights. */
    public boolean hasWeights() {
        for (byte weight : weights) {
            if (weight != 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether a grid point is a hole. */
    public boolean isHole(int ix, int iz) {
        return holes[index(ix, iz)] != 0;
    }

    /** Cuts a hole at a grid point, or fills one. */
    public void setHole(int ix, int iz, boolean hole) {
        int i = index(ix, iz);
        byte value = (byte) (hole ? 1 : 0);
        if (holes[i] != value) {
            holes[i] = value;
            touch(ix, iz);
        }
    }

    /** Whether the cell whose lowest corner is (ix, iz) is open: any of its four corners is a hole, as the collision sees it. */
    public boolean isCellOpen(int ix, int iz) {
        return isHole(ix, iz) || isHole(ix + 1, iz) || isHole(ix, iz + 1) || isHole(ix + 1, iz + 1);
    }

    /** Whether a point of the terrain's own space lies in an open cell, so there is no ground there; false off the terrain. */
    public boolean isHoleAt(float x, float z) {
        float fx = (x + getSizeX() / 2f) / cellSize;
        float fz = (z + getSizeZ() / 2f) / cellSize;
        if (!(fx >= 0f && fx <= resolutionX - 1 && fz >= 0f && fz <= resolutionZ - 1)) {
            return false;
        }
        return isCellOpen(Math.min((int) fx, resolutionX - 2), Math.min((int) fz, resolutionZ - 2));
    }

    /** The hole flags, one byte per point, as a copy. */
    public byte[] copyHoles() {
        return holes.clone();
    }

    /** True when any point is a hole. */
    public boolean hasHoles() {
        for (byte hole : holes) {
            if (hole != 0) {
                return true;
            }
        }
        return false;
    }

    /** Height of the water surface in the terrain's own space, or NaN when there is no water. */
    public float getWaterLevel() {
        return waterLevel;
    }

    /** Whether the terrain has water. */
    public boolean hasWater() {
        return !Float.isNaN(waterLevel);
    }

    /**
     * Sets the water surface's height, or removes the water with NaN.
     *
     * @throws IllegalArgumentException if the level is infinite or beyond {@link #MAX_ABS_HEIGHT}
     */
    public void setWaterLevel(float level) {
        if (!Float.isNaN(level) && !(Math.abs(level) <= MAX_ABS_HEIGHT)) {
            throw new IllegalArgumentException("Water level must be within plus or minus " + MAX_ABS_HEIGHT + ", or NaN for none: " + level);
        }
        if (Float.compare(waterLevel, level) != 0) {
            waterLevel = level;
            revision++;
        }
    }

    /** Index of a grid point in the height array, row by row along x. */
    public int index(int ix, int iz) {
        if (ix < 0 || ix >= resolutionX || iz < 0 || iz >= resolutionZ) {
            throw new IndexOutOfBoundsException("Grid point " + ix + ", " + iz + " is outside " + resolutionX + " x " + resolutionZ);
        }
        return iz * resolutionX + ix;
    }

    /** Height at a point of the terrain's own space, interpolated between grid points; NaN outside the terrain. */
    public float heightAt(float x, float z) {
        float fx = (x + getSizeX() / 2f) / cellSize;
        float fz = (z + getSizeZ() / 2f) / cellSize;
        if (!(fx >= 0f && fx <= resolutionX - 1 && fz >= 0f && fz <= resolutionZ - 1)) {
            return Float.NaN;
        }
        int ix = Math.min((int) fx, resolutionX - 2);
        int iz = Math.min((int) fz, resolutionZ - 2);
        float tx = fx - ix;
        float tz = fz - iz;
        float h00 = heights[iz * resolutionX + ix];
        float h10 = heights[iz * resolutionX + ix + 1];
        float h01 = heights[(iz + 1) * resolutionX + ix];
        float h11 = heights[(iz + 1) * resolutionX + ix + 1];
        return (h00 * (1 - tx) + h10 * tx) * (1 - tz) + (h01 * (1 - tx) + h11 * tx) * tz;
    }

    /** The surface normal at a grid point (central differences, so it reads the neighbours), written to {@code out}. */
    public Vector3 normalAt(int ix, int iz, Vector3 out) {
        int x0 = Math.max(ix - 1, 0);
        int x1 = Math.min(ix + 1, resolutionX - 1);
        int z0 = Math.max(iz - 1, 0);
        int z1 = Math.min(iz + 1, resolutionZ - 1);
        float dx = heights[iz * resolutionX + x1] - heights[iz * resolutionX + x0];
        float dz = heights[z1 * resolutionX + ix] - heights[z0 * resolutionX + ix];
        return out.set(-dx / ((x1 - x0) * cellSize), 1f, -dz / ((z1 - z0) * cellSize)).nor();
    }

    /**
     * Finds where a ray, given in the terrain's own space, first meets the surface from above: it marches along the part of the ray
     * over the terrain's footprint in half-cell steps and refines the crossing. A ray that starts below the surface does not hit it.
     *
     * @param maxDistance how far along the ray to look
     * @param hit         receives the point, normal and distance when this returns true
     */
    public boolean raycast(Ray ray, float maxDistance, Hit hit) {
        Objects.requireNonNull(ray, "ray cannot be null");
        Objects.requireNonNull(hit, "hit cannot be null");
        float halfX = getSizeX() / 2f;
        float halfZ = getSizeZ() / 2f;
        if (Math.abs(ray.direction.x) < 1e-9f && Math.abs(ray.direction.z) < 1e-9f) {
            return raycastVertical(ray, maxDistance, hit);
        }
        float start = 0f;
        float end = maxDistance;
        float[] origin = {ray.origin.x, ray.origin.z};
        float[] direction = {ray.direction.x, ray.direction.z};
        float[] half = {halfX, halfZ};
        for (int axis = 0; axis < 2; axis++) {
            if (Math.abs(direction[axis]) < 1e-9f) {
                if (origin[axis] < -half[axis] || origin[axis] > half[axis]) {
                    return false;
                }
            } else {
                float a = (-half[axis] - origin[axis]) / direction[axis];
                float b = (half[axis] - origin[axis]) / direction[axis];
                start = Math.max(start, Math.min(a, b));
                end = Math.min(end, Math.max(a, b));
            }
        }
        if (start > end) {
            return false;
        }
        float step = cellSize * 0.5f;
        float previousT = start;
        float previousAbove = above(ray, previousT);
        if (Float.isNaN(previousAbove) || previousAbove < 0f) {
            return false;
        }
        for (float t = start + step; ; t += step) {
            boolean last = t >= end;
            if (last) {
                t = end;
            }
            float margin = above(ray, t);
            if (!Float.isNaN(margin) && margin < 0f) {
                float low = previousT;
                float high = t;
                for (int i = 0; i < REFINE_STEPS; i++) {
                    float mid = (low + high) / 2f;
                    float midMargin = above(ray, mid);
                    if (!Float.isNaN(midMargin) && midMargin < 0f) {
                        high = mid;
                    } else {
                        low = mid;
                    }
                }
                float distance = (low + high) / 2f;
                fillHit(ray, distance, hit);
                return true;
            }
            if (last) {
                return false;
            }
            previousT = t;
        }
    }

    /** A ray straight up or down never leaves the footprint, so it is answered from the height under it instead of marched. */
    private boolean raycastVertical(Ray ray, float maxDistance, Hit hit) {
        float h = heightAt(ray.origin.x, ray.origin.z);
        if (Float.isNaN(h) || ray.direction.y >= 0f || ray.origin.y < h) {
            return false;
        }
        float distance = (ray.origin.y - h) / -ray.direction.y;
        if (distance > maxDistance) {
            return false;
        }
        fillHit(ray, distance, hit);
        return true;
    }

    /** Bisection steps that pin the crossing down; the error is under the step divided by 2 to this power. */
    private static final int REFINE_STEPS = 12;

    /** How far the ray at distance {@code t} is above the surface (negative below it); NaN off the terrain. */
    private float above(Ray ray, float t) {
        float x = ray.origin.x + ray.direction.x * t;
        float z = ray.origin.z + ray.direction.z * t;
        float y = ray.origin.y + ray.direction.y * t;
        float h = heightAt(x, z);
        return Float.isNaN(h) ? Float.NaN : y - h;
    }

    private void fillHit(Ray ray, float distance, Hit hit) {
        hit.distance = distance;
        hit.x = ray.origin.x + ray.direction.x * distance;
        hit.z = ray.origin.z + ray.direction.z * distance;
        float h = heightAt(hit.x, hit.z);
        hit.y = Float.isNaN(h) ? ray.origin.y + ray.direction.y * distance : h;
        int ix = Math.max(0, Math.min(resolutionX - 1, Math.round((hit.x + getSizeX() / 2f) / cellSize)));
        int iz = Math.max(0, Math.min(resolutionZ - 1, Math.round((hit.z + getSizeZ() / 2f) / cellSize)));
        Vector3 normal = normalAt(ix, iz, new Vector3());
        hit.nx = normal.x;
        hit.ny = normal.y;
        hit.nz = normal.z;
    }

    // ---- chunks -----------------------------------------------------------------------------------------------------------

    /** Number of chunks along x and along z. */
    public int getChunksX() {
        return chunksX;
    }

    public int getChunksZ() {
        return chunksZ;
    }

    /** Changes whenever a height in or next to the chunk changes; a renderer rebuilds the chunk's mesh when its number moved. */
    public int getChunkRevision(int cx, int cz) {
        if (cx < 0 || cx >= chunksX || cz < 0 || cz >= chunksZ) {
            throw new IndexOutOfBoundsException("Chunk " + cx + ", " + cz + " is outside " + chunksX + " x " + chunksZ);
        }
        return chunkRevisions[cz * chunksX + cx];
    }

    /** First grid column or row of a chunk; the chunk covers this and the next {@link #CHUNK_CELLS} points, as far as the grid goes. */
    public static int chunkStart(int chunk) {
        return chunk * CHUNK_CELLS;
    }

    /** Marks every chunk whose mesh uses the height of (ix, iz), or a normal computed from it, as changed. */
    private void touch(int ix, int iz) {
        revision++;
        int cx0 = Math.max(0, Math.floorDiv(ix - CHUNK_CELLS - 1, CHUNK_CELLS));
        int cx1 = Math.min(chunksX - 1, Math.floorDiv(ix + 1, CHUNK_CELLS));
        int cz0 = Math.max(0, Math.floorDiv(iz - CHUNK_CELLS - 1, CHUNK_CELLS));
        int cz1 = Math.min(chunksZ - 1, Math.floorDiv(iz + 1, CHUNK_CELLS));
        for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                // A chunk holds vertices chunkStart .. chunkStart + CHUNK_CELLS; its normals also read one point beyond each side
                int minX = chunkStart(cx) - 1;
                int maxX = chunkStart(cx) + CHUNK_CELLS + 1;
                int minZ = chunkStart(cz) - 1;
                int maxZ = chunkStart(cz) + CHUNK_CELLS + 1;
                if (ix >= minX && ix <= maxX && iz >= minZ && iz <= maxZ) {
                    chunkRevisions[cz * chunksX + cx]++;
                }
            }
        }
    }

    /** Changes with every edit, to any height or the name. */
    public int getRevision() {
        return revision;
    }

    // ---- strokes ----------------------------------------------------------------------------------------------------------

    /** Applies a recorded change: {@code forward} puts the heights after the stroke in place, otherwise the heights before it. */
    public void apply(TerrainDelta delta, boolean forward) {
        Objects.requireNonNull(delta, "delta cannot be null");
        float[] values = forward ? delta.after() : delta.before();
        int[] indices = delta.indices();
        for (int i = 0; i < indices.length; i++) {
            int index = indices[i];
            if (index < 0 || index >= heights.length) {
                throw new IndexOutOfBoundsException("The change names height " + index + " but the grid has " + heights.length);
            }
            if (heights[index] != values[i]) {
                heights[index] = values[i];
                touch(index % resolutionX, index / resolutionX);
            }
        }
        byte[] codes = forward ? delta.surfaceAfter() : delta.surfaceBefore();
        int[] surfaceIndices = delta.surfaceIndices();
        for (int i = 0; i < surfaceIndices.length; i++) {
            int index = surfaceIndices[i];
            if (index < 0 || index >= surfaces.length) {
                throw new IndexOutOfBoundsException("The change names surface " + index + " but the grid has " + surfaces.length);
            }
            if (surfaces[index] != codes[i]) {
                surfaces[index] = codes[i];
                touch(index % resolutionX, index / resolutionX);
            }
        }
        byte[] weightValues = forward ? delta.weightAfter() : delta.weightBefore();
        int[] weightIndices = delta.weightIndices();
        for (int i = 0; i < weightIndices.length; i++) {
            int index = weightIndices[i];
            if (index < 0 || index >= weights.length) {
                throw new IndexOutOfBoundsException("The change names weight " + index + " but the grid has " + weights.length);
            }
            if (weights[index] != weightValues[i]) {
                weights[index] = weightValues[i];
                int point = index / Surface.LAYER_COUNT;
                touch(point % resolutionX, point / resolutionX);
            }
        }
        byte[] holeValues = forward ? delta.holeAfter() : delta.holeBefore();
        int[] holeIndices = delta.holeIndices();
        for (int i = 0; i < holeIndices.length; i++) {
            int index = holeIndices[i];
            if (index < 0 || index >= holes.length) {
                throw new IndexOutOfBoundsException("The change names hole " + index + " but the grid has " + holes.length);
            }
            if (holes[index] != holeValues[i]) {
                holes[index] = holeValues[i];
                touch(index % resolutionX, index / resolutionX);
            }
        }
    }

    /** Takes over the heights, ground, paint, holes, water and name of {@code other}, which must have the same grid, so a shared instance can be reloaded in place. */
    public void copyFrom(Terrain other) {
        Objects.requireNonNull(other, "other cannot be null");
        if (other.resolutionX != resolutionX || other.resolutionZ != resolutionZ || other.cellSize != cellSize) {
            throw new IllegalArgumentException("The grid differs: " + other.resolutionX + " x " + other.resolutionZ + " at " + other.cellSize
                + " against " + resolutionX + " x " + resolutionZ + " at " + cellSize);
        }
        name = other.name;
        System.arraycopy(other.heights, 0, heights, 0, heights.length);
        System.arraycopy(other.surfaces, 0, surfaces, 0, surfaces.length);
        System.arraycopy(other.weights, 0, weights, 0, weights.length);
        System.arraycopy(other.holes, 0, holes, 0, holes.length);
        waterLevel = other.waterLevel;
        Arrays.fill(chunkRevisions, 0);
        for (int i = 0; i < chunkRevisions.length; i++) {
            chunkRevisions[i] = ++revision;
        }
    }

    /** The heights, row by row along x, as a copy. */
    public float[] copyHeights() {
        return heights.clone();
    }

    /** Lowest and highest height, as {@code {min, max}}. */
    public float[] heightRange() {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float h : heights) {
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        return new float[]{min, max};
    }
}
