package io.github.ldev22live.gdxunity.engine.terrain;

import java.util.Random;

/** Starting points for a new terrain (TD-082): flat ground, or gentle hills to sculpt from. */
public final class TerrainGenerator {
    private TerrainGenerator() { }

    /** Layers of hills, each half the size and a third the height of the one before. */
    private static final int OCTAVES = 4;

    /** Flat ground at height 0. */
    public static Terrain flat(String name, int resolution, float cellSize) {
        Terrain terrain = new Terrain(resolution, resolution, cellSize);
        terrain.setName(name);
        return terrain;
    }

    /**
     * Rolling hills: smooth value noise in {@value #OCTAVES} layers, the same for the same seed.
     *
     * @param amplitude height of the largest hills, in world units
     */
    public static Terrain hills(String name, int resolution, float cellSize, long seed, float amplitude) {
        Terrain terrain = flat(name, resolution, cellSize);
        Random random = new Random(seed);
        float[][] offsets = new float[OCTAVES][2];
        for (float[] offset : offsets) {
            offset[0] = random.nextFloat() * 1000f;
            offset[1] = random.nextFloat() * 1000f;
        }
        float feature = Math.max(terrain.getSizeX(), terrain.getSizeZ()) / 4f;
        for (int iz = 0; iz < resolution; iz++) {
            for (int ix = 0; ix < resolution; ix++) {
                float height = 0f;
                float size = feature;
                float scale = amplitude;
                for (int octave = 0; octave < OCTAVES; octave++) {
                    height += scale * value((terrain.xAt(ix) + offsets[octave][0]) / size, (terrain.zAt(iz) + offsets[octave][1]) / size, seed + octave);
                    size /= 2f;
                    scale /= 3f;
                }
                terrain.setHeight(ix, iz, height);
            }
        }
        return terrain;
    }

    /** Smooth value noise in 0 to 1. */
    private static float value(float x, float z, long seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        float tx = smooth(x - x0);
        float tz = smooth(z - z0);
        return (lattice(x0, z0, seed) * (1 - tx) + lattice(x0 + 1, z0, seed) * tx) * (1 - tz)
            + (lattice(x0, z0 + 1, seed) * (1 - tx) + lattice(x0 + 1, z0 + 1, seed) * tx) * tz;
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    private static float lattice(int x, int z, long seed) {
        long h = x * 374761393L + z * 668265263L + seed * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }
}
