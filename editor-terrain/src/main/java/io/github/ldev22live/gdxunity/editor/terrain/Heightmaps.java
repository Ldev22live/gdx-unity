package io.github.ldev22live.gdxunity.editor.terrain;

import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainDelta;

import java.util.Arrays;
import java.util.Objects;

/**
 * Heightmap import and export for a {@link Terrain} (roadmap M3.5), through {@link HeightmapPng}. Export maps the terrain's own height
 * range onto the 16-bit samples and records it, so importing the file back gives the same heights to within one step of that range
 * divided by 65,535. Import resamples the image onto the terrain's grid (bilinear), so an image of any size fits; an image without a
 * recorded range is read as {@value #DEFAULT_RANGE} units from black to white.
 */
public final class Heightmaps {
    /** Height range, in world units, of an imported image that does not say its own: black is 0, white this. */
    public static final float DEFAULT_RANGE = 1000f;

    private Heightmaps() { }

    /** The terrain's heights as a 16-bit PNG with their range. */
    public static byte[] export(Terrain terrain) {
        Objects.requireNonNull(terrain, "terrain cannot be null");
        float[] range = terrain.heightRange();
        float lowest = range[0];
        float highest = range[1] > range[0] ? range[1] : range[0] + 1f; // flat ground: any span, every sample is 0
        float[] heights = terrain.copyHeights();
        int[] samples = new int[heights.length];
        for (int i = 0; i < heights.length; i++) {
            samples[i] = Math.round((heights[i] - lowest) / (highest - lowest) * HeightmapPng.MAX_SAMPLE);
        }
        return HeightmapPng.write(terrain.getResolutionX(), terrain.getResolutionZ(), samples, lowest, highest);
    }

    /**
     * Puts a heightmap's heights on the terrain, resampled to its grid, and returns what changed for one undo step. The terrain shows
     * the new heights already, as after a brush stroke.
     */
    public static TerrainDelta importInto(Terrain terrain, HeightmapPng.Heightmap map) {
        Objects.requireNonNull(terrain, "terrain cannot be null");
        Objects.requireNonNull(map, "map cannot be null");
        float lowest = map.hasRange() ? map.lowest() : 0f;
        float highest = map.hasRange() ? map.highest() : DEFAULT_RANGE;
        float[] before = terrain.copyHeights();
        int columns = terrain.getResolutionX();
        int rows = terrain.getResolutionZ();
        for (int iz = 0; iz < rows; iz++) {
            float v = rows == 1 ? 0f : iz * (map.height() - 1) / (float) (rows - 1);
            for (int ix = 0; ix < columns; ix++) {
                float u = columns == 1 ? 0f : ix * (map.width() - 1) / (float) (columns - 1);
                float sample = bilinear(map, u, v);
                // The top sample is exactly the highest height, so the range comes back unrounded and a re-export is the same file
                terrain.setHeight(ix, iz, sample >= HeightmapPng.MAX_SAMPLE ? highest
                    : lowest + (highest - lowest) * sample / HeightmapPng.MAX_SAMPLE);
            }
        }
        float[] after = terrain.copyHeights();
        int[] indices = new int[after.length];
        float[] was = new float[after.length];
        float[] now = new float[after.length];
        int count = 0;
        for (int i = 0; i < after.length; i++) {
            if (before[i] != after[i]) {
                indices[count] = i;
                was[count] = before[i];
                now[count] = after[i];
                count++;
            }
        }
        return new TerrainDelta(Arrays.copyOf(indices, count), Arrays.copyOf(was, count), Arrays.copyOf(now, count));
    }

    private static float bilinear(HeightmapPng.Heightmap map, float u, float v) {
        int x0 = Math.min((int) u, map.width() - 1);
        int y0 = Math.min((int) v, map.height() - 1);
        int x1 = Math.min(x0 + 1, map.width() - 1);
        int y1 = Math.min(y0 + 1, map.height() - 1);
        float tx = u - x0;
        float ty = v - y0;
        int[] s = map.samples();
        float top = s[y0 * map.width() + x0] * (1 - tx) + s[y0 * map.width() + x1] * tx;
        float bottom = s[y1 * map.width() + x0] * (1 - tx) + s[y1 * map.width() + x1] * tx;
        return top * (1 - ty) + bottom * ty;
    }
}
