package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.math.MathUtils;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterBodyComponent;

import java.util.Objects;

/**
 * The geometry of a {@link WaterBodyComponent} (roadmap M3.9, TD-090): a flat grid of {@link #SEGMENTS} by
 * {@link #SEGMENTS} cells centred on the entity, raised and lowered by two crossing sine waves. Pure arrays, testable
 * without a graphics context; {@link #fill} writes into a reused array, so animating the waves does not allocate.
 *
 * <p>Waves shorter than {@link #CELLS_PER_WAVE} grid cells cannot be shown and would flicker, so their height is scaled
 * down until they fit: on a very large water body short waves flatten out.
 */
final class WaterSurfaceBuilder {
    /** Grid cells along each side. */
    static final int SEGMENTS = 64;
    /** Vertices along each side. */
    static final int VERTICES_PER_SIDE = SEGMENTS + 1;
    /** Floats per vertex: position and normal. */
    static final int VERTEX_SIZE = 6;
    /** Fewest grid cells a wave needs to look like a wave. */
    static final float CELLS_PER_WAVE = 4f;
    /** Share of the height carried by the second, crossing wave. */
    static final float SECOND_WAVE_SHARE = 0.4f;
    /** The second wave is this much longer than the first and runs along a diagonal. */
    static final float SECOND_WAVE_STRETCH = 1.37f;
    /** 1 / sqrt(2): the second wave runs along the diagonal. */
    private static final float DIAGONAL = 0.70710677f;

    private WaterSurfaceBuilder() {}

    /** Vertex count of the grid. */
    static int vertexCount() {
        return VERTICES_PER_SIDE * VERTICES_PER_SIDE;
    }

    /** Triangle indices of the grid, two per cell. */
    static short[] indices() {
        short[] indices = new short[SEGMENTS * SEGMENTS * 6];
        int at = 0;
        for (int row = 0; row < SEGMENTS; row++) {
            for (int col = 0; col < SEGMENTS; col++) {
                short a = (short) (row * VERTICES_PER_SIDE + col);
                short b = (short) (a + 1);
                short c = (short) (a + VERTICES_PER_SIDE);
                short d = (short) (c + 1);
                indices[at++] = a;
                indices[at++] = c;
                indices[at++] = b;
                indices[at++] = b;
                indices[at++] = c;
                indices[at++] = d;
            }
        }
        return indices;
    }

    /** The wave height actually shown for {@code water}: its own, scaled down when its waves are too short for the grid. */
    static float shownWaveHeight(WaterBodyComponent water) {
        float cell = Math.max(water.getWidth(), water.getLength()) / SEGMENTS;
        float fit = water.getWaveLength() / (cell * CELLS_PER_WAVE);
        return water.getWaveHeight() * MathUtils.clamp(fit, 0f, 1f);
    }

    /** Whether {@code water}'s surface moves, so it must be rebuilt every frame. */
    static boolean isAnimated(WaterBodyComponent water) {
        return shownWaveHeight(water) > 0f && water.getWaveSpeed() > 0f;
    }

    /** Height of the surface at {@code (px, pz)} in the entity's space, {@code seconds} after the scene started. */
    static float heightAt(WaterBodyComponent water, float px, float pz, float seconds) {
        float amplitude = shownWaveHeight(water);
        if (amplitude == 0f) {
            return 0f;
        }
        return amplitude * wave(water, px, pz, seconds);
    }

    /**
     * Writes the grid's positions and normals {@code seconds} after the scene started into {@code vertices}, which must
     * hold {@link #vertexCount()} times {@link #VERTEX_SIZE} floats.
     */
    static void fill(WaterBodyComponent water, float seconds, float[] vertices) {
        Objects.requireNonNull(water, "water cannot be null");
        if (vertices.length < vertexCount() * VERTEX_SIZE) {
            throw new IllegalArgumentException("vertices holds " + vertices.length + " floats; needs " + vertexCount() * VERTEX_SIZE);
        }
        float amplitude = shownWaveHeight(water);
        float width = water.getWidth();
        float length = water.getLength();
        int at = 0;
        for (int row = 0; row < VERTICES_PER_SIDE; row++) {
            float pz = -length * 0.5f + length * row / SEGMENTS;
            for (int col = 0; col < VERTICES_PER_SIDE; col++) {
                float px = -width * 0.5f + width * col / SEGMENTS;
                float h = 0f;
                float nx = 0f;
                float nz = 0f;
                if (amplitude > 0f) {
                    h = amplitude * wave(water, px, pz, seconds);
                    // The slope of the surface: d/dx and d/dz of the two waves
                    nx = -amplitude * slope(water, px, pz, seconds, true);
                    nz = -amplitude * slope(water, px, pz, seconds, false);
                }
                float inverse = 1f / (float) Math.sqrt(nx * nx + 1f + nz * nz);
                vertices[at++] = px;
                vertices[at++] = h;
                vertices[at++] = pz;
                vertices[at++] = nx * inverse;
                vertices[at++] = inverse;
                vertices[at++] = nz * inverse;
            }
        }
    }

    /** The two waves' sum at a point, between -1 and 1. */
    private static float wave(WaterBodyComponent water, float px, float pz, float seconds) {
        float k1 = MathUtils.PI2 / water.getWaveLength();
        float k2 = k1 / SECOND_WAVE_STRETCH;
        float travel = water.getWaveSpeed() * seconds;
        float first = MathUtils.sin(k1 * (px - travel));
        float second = MathUtils.sin(k2 * ((px + pz) * DIAGONAL - travel * SECOND_WAVE_STRETCH));
        return first * (1f - SECOND_WAVE_SHARE) + second * SECOND_WAVE_SHARE;
    }

    /** The waves' slope along x ({@code alongX}) or z, per unit of height. */
    private static float slope(WaterBodyComponent water, float px, float pz, float seconds, boolean alongX) {
        float k1 = MathUtils.PI2 / water.getWaveLength();
        float k2 = k1 / SECOND_WAVE_STRETCH;
        float travel = water.getWaveSpeed() * seconds;
        float first = alongX ? k1 * MathUtils.cos(k1 * (px - travel)) : 0f;
        float diagonal = k2 * MathUtils.cos(k2 * ((px + pz) * DIAGONAL - travel * SECOND_WAVE_STRETCH))
            * DIAGONAL;
        return first * (1f - SECOND_WAVE_SHARE) + diagonal * SECOND_WAVE_SHARE;
    }
}
