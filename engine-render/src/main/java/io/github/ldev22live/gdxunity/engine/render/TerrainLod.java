package io.github.ldev22live.gdxunity.engine.render;

/**
 * Level of detail for terrain chunks (roadmap M3.8): a chunk near the camera is drawn with every grid point, one further away with
 * every 2nd, 4th or 8th, so a large map costs far fewer triangles. Pure maths, tested headless.
 *
 * <p>A chunk is at full detail within {@value #FULL_DETAIL_CHUNKS} chunk widths of the camera; each doubling of that distance takes
 * one level coarser, up to level {@value #MAX_LEVEL} (every 8th point). Chunks of different levels meet with skirts
 * ({@link TerrainMeshBuilder#skirtIndices}) so no gap shows between them.
 */
final class TerrainLod {
    /** Coarsest level: every 2^3 = 8th grid point. {@code Terrain.CHUNK_CELLS} (32) is a multiple of 8. */
    static final int MAX_LEVEL = 3;
    /** Distance, in chunk widths, within which a chunk keeps every grid point. */
    static final float FULL_DETAIL_CHUNKS = 2f;

    private TerrainLod() { }

    /**
     * The level for a chunk this far from the camera.
     *
     * @param distance   from the camera to the chunk's centre, in the terrain's own units
     * @param chunkWidth a chunk's width in the same units ({@code CHUNK_CELLS * cellSize})
     */
    static int levelFor(float distance, float chunkWidth) {
        float near = FULL_DETAIL_CHUNKS * chunkWidth;
        if (!(distance > near)) {
            return 0;
        }
        int level = (int) Math.floor(Math.log(distance / near) / Math.log(2)) + 1;
        return Math.min(MAX_LEVEL, level);
    }

    /** Grid points between drawn vertices at {@code level}: 1, 2, 4 or 8. */
    static int step(int level) {
        if (level < 0 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Level must be 0 to " + MAX_LEVEL + ": " + level);
        }
        return 1 << level;
    }
}
