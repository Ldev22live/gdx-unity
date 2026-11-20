package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;

/**
 * Builds the vertices and triangles of one chunk of a {@link Terrain} (TD-082). Pure arrays, no GL, so it is unit-tested headless.
 *
 * <p>A vertex is position (3), normal (3) and colour (4, linear light) = {@value #FLOATS_PER_VERTEX} floats. The colour comes from the
 * ground itself: grass on gentle ground, rock on steep ground, snow near the top of the height range. A point with a chosen
 * {@link Surface} (TD-083) takes that surface's colour instead, with rock showing through where it is steep. Paint layers (TD-089) are
 * blended over that base by their weights, so a painted patch has a soft edge. Cells touching a hole are left out of the triangles,
 * and a terrain with water gets one flat water quad ({@link #waterVertices}).
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

    /** Colour of the water quad's vertices (white: the water material gives the colour). */
    private static final float[] WATER_VERTEX_COLOR = {1f, 1f, 1f, 1f};

    private final Vector3 normal = new Vector3();
    /** Scratch for blending: the base colour, then each layer's colour. */
    private final float[] baseColor = new float[4];
    private final float[] layerColor = new float[4];

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
        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (int z = 0; z < countZ; z++) {
            int iz = Terrain.chunkStart(cz) + z;
            for (int x = 0; x < countX; x++) {
                int ix = Terrain.chunkStart(cx) + x;
                float height = terrain.getHeight(ix, iz);
                low = Math.min(low, height);
                high = Math.max(high, height);
                terrain.normalAt(ix, iz, normal);
                out[written++] = terrain.xAt(ix);
                out[written++] = height;
                out[written++] = terrain.zAt(iz);
                out[written++] = normal.x;
                out[written++] = normal.y;
                out[written++] = normal.z;
                written = blendedColor(terrain, ix, iz, normal.y, (height - rangeMin) / span, out, written);
            }
        }
        // Skirts (roadmap M3.8): each border vertex again, hanging below it by the chunk's own height range plus a cell, which is
        // more than any gap a coarser neighbour can leave. Only drawn while level of detail is on (skirtIndices).
        float depth = (high - low) + terrain.getCellSize();
        int[][] edges = edgeVertices(countX, countZ);
        for (int[] edge : edges) {
            for (int vertex : edge) {
                System.arraycopy(out, vertex * FLOATS_PER_VERTEX, out, written, FLOATS_PER_VERTEX);
                out[written + 1] -= depth;
                written += FLOATS_PER_VERTEX;
            }
        }
        return written;
    }

    /** Vertices of a chunk with its skirts: the grid, then a copy of each of the four edges. */
    static int vertexCount(int countX, int countZ) {
        return countX * countZ + 2 * countX + 2 * countZ;
    }

    /** The grid vertices along the chunk's four edges, in order: z = 0, z = last, x = 0, x = last. */
    private static int[][] edgeVertices(int countX, int countZ) {
        int[] bottom = new int[countX];
        int[] top = new int[countX];
        int[] left = new int[countZ];
        int[] right = new int[countZ];
        for (int x = 0; x < countX; x++) {
            bottom[x] = x;
            top[x] = (countZ - 1) * countX + x;
        }
        for (int z = 0; z < countZ; z++) {
            left[z] = z * countX;
            right[z] = z * countX + countX - 1;
        }
        return new int[][]{bottom, top, left, right};
    }

    /**
     * The chunk's triangles at a level of detail (roadmap M3.8): one quad per {@code step} by {@code step} block of cells, leaving out
     * a block that holds an open cell (TD-089). Returns how many indices were written. A chunk whose cells do not divide by
     * {@code step} ends with a narrower block.
     */
    static int indices(Terrain terrain, int cx, int cz, short[] out, int step) {
        int countX = vertexCountX(terrain, cx);
        int countZ = vertexCountZ(terrain, cz);
        int i = 0;
        for (int z = 0; z < countZ - 1; z += step) {
            int z1 = Math.min(z + step, countZ - 1);
            for (int x = 0; x < countX - 1; x += step) {
                int x1 = Math.min(x + step, countX - 1);
                if (blockOpen(terrain, Terrain.chunkStart(cx) + x, Terrain.chunkStart(cz) + z, x1 - x, z1 - z)) {
                    continue;
                }
                short a = (short) (z * countX + x);
                short b = (short) (z * countX + x1);
                short c = (short) (z1 * countX + x);
                short d = (short) (z1 * countX + x1);
                out[i++] = a;
                out[i++] = c;
                out[i++] = b;
                out[i++] = b;
                out[i++] = c;
                out[i++] = d;
            }
        }
        return i;
    }

    private static boolean blockOpen(Terrain terrain, int ix, int iz, int width, int depth) {
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                if (terrain.isCellOpen(ix + x, iz + z)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The skirt triangles along the chunk's four edges at {@code step} (roadmap M3.8), written into {@code out} from {@code at}, both
     * windings so they show from either side. A segment touching a hole is left out, so a hole is not walled. Returns the new end.
     */
    static int skirtIndices(Terrain terrain, int cx, int cz, short[] out, int at, int step) {
        int countX = vertexCountX(terrain, cx);
        int countZ = vertexCountZ(terrain, cz);
        int[][] edges = edgeVertices(countX, countZ);
        int skirtBase = countX * countZ;
        int i = at;
        for (int[] edge : edges) {
            for (int k = 0; k < edge.length - 1; k += step) {
                int k1 = Math.min(k + step, edge.length - 1);
                int g0 = edge[k];
                int g1 = edge[k1];
                if (terrain.isHole(Terrain.chunkStart(cx) + g0 % countX, Terrain.chunkStart(cz) + g0 / countX)
                    || terrain.isHole(Terrain.chunkStart(cx) + g1 % countX, Terrain.chunkStart(cz) + g1 / countX)) {
                    continue;
                }
                short a = (short) g0;
                short b = (short) g1;
                short c = (short) (skirtBase + k);
                short d = (short) (skirtBase + k1);
                out[i++] = a;
                out[i++] = c;
                out[i++] = b;
                out[i++] = b;
                out[i++] = c;
                out[i++] = d;
                out[i++] = a;
                out[i++] = b;
                out[i++] = c;
                out[i++] = b;
                out[i++] = d;
                out[i++] = c;
            }
            skirtBase += edge.length;
        }
        return i;
    }

    /** Most indices a chunk's skirts can need. */
    static int maxSkirtIndices() {
        return 4 * Terrain.CHUNK_CELLS * 12;
    }

    /** The base ground's colour mixed with every painted layer by its weight (TD-089), in linear light. */
    private int blendedColor(Terrain terrain, int ix, int iz, float normalY, float heightShare, float[] out, int at) {
        color(terrain.getSurface(ix, iz), normalY, heightShare, baseColor, 0);
        float baseShare = terrain.getBaseWeight(ix, iz) / (float) Terrain.FULL_WEIGHT;
        if (baseShare >= 1f) {
            System.arraycopy(baseColor, 0, out, at, 4);
            return at + 4;
        }
        for (int channel = 0; channel < 3; channel++) {
            out[at + channel] = baseColor[channel] * baseShare;
        }
        for (int layer = 0; layer < Surface.LAYER_COUNT; layer++) {
            Surface surface = Surface.ofLayer(layer);
            int weight = terrain.getLayerWeight(ix, iz, surface);
            if (weight == 0) {
                continue;
            }
            color(surface, normalY, heightShare, layerColor, 0);
            float share = weight / (float) Terrain.FULL_WEIGHT;
            for (int channel = 0; channel < 3; channel++) {
                out[at + channel] += layerColor[channel] * share;
            }
        }
        out[at + 3] = 1f;
        return at + 4;
    }

    /**
     * Writes the triangles of chunk ({@code cx}, {@code cz}) into {@code out}, two per cell, leaving out every cell that touches a hole
     * (TD-089), and returns how many indices were written.
     */
    static int indices(Terrain terrain, int cx, int cz, short[] out) {
        int countX = vertexCountX(terrain, cx);
        int countZ = vertexCountZ(terrain, cz);
        int i = 0;
        for (int z = 0; z < countZ - 1; z++) {
            for (int x = 0; x < countX - 1; x++) {
                if (terrain.isCellOpen(Terrain.chunkStart(cx) + x, Terrain.chunkStart(cz) + z)) {
                    continue;
                }
                short a = (short) (z * countX + x);
                short b = (short) (a + 1);
                short c = (short) (a + countX);
                short d = (short) (c + 1);
                out[i++] = a;
                out[i++] = c;
                out[i++] = b;
                out[i++] = b;
                out[i++] = c;
                out[i++] = d;
            }
        }
        return i;
    }

    /** Vertices of the water quad (TD-089): the terrain's footprint at its water level, facing up; four vertices, as {@link #vertices}. */
    static float[] waterVertices(Terrain terrain) {
        float level = terrain.getWaterLevel();
        float halfX = terrain.getSizeX() / 2f;
        float halfZ = terrain.getSizeZ() / 2f;
        float[] out = new float[4 * FLOATS_PER_VERTEX];
        float[][] corners = {{-halfX, -halfZ}, {halfX, -halfZ}, {-halfX, halfZ}, {halfX, halfZ}};
        int at = 0;
        for (float[] corner : corners) {
            out[at++] = corner[0];
            out[at++] = level;
            out[at++] = corner[1];
            out[at++] = 0f;
            out[at++] = 1f;
            out[at++] = 0f;
            System.arraycopy(WATER_VERTEX_COLOR, 0, out, at, 4);
            at += 4;
        }
        return out;
    }

    /** The water quad's two triangles, wound to face up. */
    static short[] waterIndices() {
        return new short[]{0, 2, 1, 1, 2, 3};
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
