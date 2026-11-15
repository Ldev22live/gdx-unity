package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;

/**
 * Builds the vertices and triangles of one chunk of a {@link Terrain} (TD-082). Pure arrays, no GL, so it is unit-tested headless.
 *
 * <p>A vertex is position (3), normal (3) and colour (4, linear light) = {@value #FLOATS_PER_VERTEX} floats. The colour comes from the
 * ground itself, as there are no paint layers yet: grass on gentle ground, rock on steep ground, snow near the top of the height
 * range. A point with a chosen {@link Surface} (TD-083) takes that surface's colour instead, with rock showing through where it is steep.
 */
final class TerrainMeshBuilder {
    static final int FLOATS_PER_VERTEX = 10;
    /** Gamma of the sRGB-to-linear conversion, as in the rest of the renderer. */
    private static final float GAMMA = 2.2f;
    /** Normal y at which grass starts giving way to rock, and at which it is all rock. */
    static final float ROCK_START = 0.85f;
    static final float ROCK_FULL = 0.65f;
    /** Share of the height range above which snow begins, and at which it is all snow. */
    static final float SNOW_START = 0.84f;
    static final float SNOW_FULL = 0.96f;
    private static final float[] GRASS = {0.28f, 0.48f, 0.17f};
    private static final float[] ROCK = {0.46f, 0.43f, 0.40f};
    private static final float[] SNOW = {0.92f, 0.94f, 0.97f};

    private final Vector3 normal = new Vector3();

    /** Vertices along x in chunk {@code cx}: its cells plus one. */
    static int vertexCountX(Terrain terrain, int cx) {
        return Math.min(Terrain.chunkStart(cx) + Terrain.CHUNK_CELLS, terrain.getResolutionX() - 1) - Terrain.chunkStart(cx) + 1;
    }

    /** Vertices along z in chunk {@code cz}. */
    static int vertexCountZ(Terrain terrain, int cz) {
        return Math.min(Terrain.chunkStart(cz) + Terrain.CHUNK_CELLS, terrain.getResolutionZ() - 1) - Terrain.chunkStart(cz) + 1;
    }

    /**
     * Fills {@code out} with the vertices of a chunk, row by row along x, and returns how many floats were written.
     *
     * @param rangeMin lowest height of the whole terrain, for the snow line
     * @param rangeMax highest height of the whole terrain
     */
    int vertices(Terrain terrain, int cx, int cz, float rangeMin, float rangeMax, float[] out) {
        int countX = vertexCountX(terrain, cx);
        int countZ = vertexCountZ(terrain, cz);
        int written = 0;
        float span = Math.max(rangeMax - rangeMin, 1e-3f);
        for (int z = 0; z < countZ; z++) {
            int iz = Terrain.chunkStart(cz) + z;
            for (int x = 0; x < countX; x++) {
                int ix = Terrain.chunkStart(cx) + x;
                float height = terrain.getHeight(ix, iz);
                terrain.normalAt(ix, iz, normal);
                out[written++] = terrain.xAt(ix);
                out[written++] = height;
                out[written++] = terrain.zAt(iz);
                out[written++] = normal.x;
                out[written++] = normal.y;
                out[written++] = normal.z;
                written = color(terrain.getSurface(ix, iz), normal.y, (height - rangeMin) / span, out, written);
            }
        }
        return written;
    }

    /** Writes the linear colour for ground with this steepness (normal y) at this share of the height range. */
    static int color(float normalY, float heightShare, float[] out, int at) {
        return color(Surface.AUTO, normalY, heightShare, out, at);
    }

    /** How much of the steep-ground rock shows through each surface: sand and gravel keep more of their own colour. */
    private static float rockShare(Surface surface) {
        return switch (surface) {
            case SAND -> 0.5f;
            case GRAVEL -> 0.3f;
            default -> 1f;
        };
    }

    /** Writes the linear colour of this surface on ground with this steepness; only {@link Surface#AUTO} turns to snow with height. */
    static int color(Surface surface, float normalY, float heightShare, float[] out, int at) {
        if (surface != Surface.AUTO) {
            float rockAmount = (1f - smoothstep(ROCK_FULL, ROCK_START, normalY)) * rockShare(surface);
            for (int channel = 0; channel < 3; channel++) {
                float srgb = surface.channel(channel) + (ROCK[channel] - surface.channel(channel)) * rockAmount;
                out[at + channel] = (float) Math.pow(srgb, GAMMA);
            }
            out[at + 3] = 1f;
            return at + 4;
        }
        float rock = 1f - smoothstep(ROCK_FULL, ROCK_START, normalY);
        float snow = smoothstep(SNOW_START, SNOW_FULL, heightShare) * (1f - rock * 0.6f);
        for (int channel = 0; channel < 3; channel++) {
            float srgb = GRASS[channel] + (ROCK[channel] - GRASS[channel]) * rock;
            srgb += (SNOW[channel] - srgb) * snow;
            out[at + channel] = (float) Math.pow(srgb, GAMMA);
        }
        out[at + 3] = 1f;
        return at + 4;
    }

    /** Two triangles per cell of a chunk with {@code countX} by {@code countZ} vertices, wound so the top faces up (+y). */
    static short[] indices(int countX, int countZ) {
        short[] indices = new short[(countX - 1) * (countZ - 1) * 6];
        int i = 0;
        for (int z = 0; z < countZ - 1; z++) {
            for (int x = 0; x < countX - 1; x++) {
                short a = (short) (z * countX + x);
                short b = (short) (a + 1);
                short c = (short) (a + countX);
                short d = (short) (c + 1);
                indices[i++] = a;
                indices[i++] = c;
                indices[i++] = b;
                indices[i++] = b;
                indices[i++] = c;
                indices[i++] = d;
            }
        }
        return indices;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }
}
