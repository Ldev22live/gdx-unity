package io.github.ldev22live.gdxunity.engine.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap M3.7: roads, rivers and cliffs along a curve through control points. */
class TerrainPathTest {
    private static final float CELL = 10f;
    private static final int SIZE = 81;

    /** A round hill 120 high in the middle of flat ground. */
    private static Terrain hill() {
        Terrain terrain = new Terrain(SIZE, SIZE, CELL);
        for (int iz = 0; iz < SIZE; iz++) {
            for (int ix = 0; ix < SIZE; ix++) {
                float d = (float) Math.hypot(terrain.xAt(ix), terrain.zAt(iz));
                terrain.setHeight(ix, iz, d < 300f ? 120f * (float) (0.5 + 0.5 * Math.cos(Math.PI * d / 300f)) : 0f);
            }
        }
        return terrain;
    }

    @Test
    void shouldPassThroughEveryControlPointAndSampleEvenly() {
        float[] xs = {-300f, 0f, 250f, 300f};
        float[] zs = {0f, 120f, -60f, 200f};
        TerrainPath.Samples samples = TerrainPath.sample(xs, zs, 10f);

        assertEquals(-300f, samples.x()[0], 1e-3f);
        assertEquals(300f, samples.x()[samples.size() - 1], 1e-3f);
        assertEquals(200f, samples.z()[samples.size() - 1], 1e-3f);
        boolean passesMiddle = false;
        for (int i = 0; i < samples.size(); i++) {
            passesMiddle |= Math.hypot(samples.x()[i], samples.z()[i] - 120f) < 6f;
        }
        assertTrue(passesMiddle, "the curve passes through the second point");
        for (int i = 1; i < samples.size(); i++) {
            float step = (float) Math.hypot(samples.x()[i] - samples.x()[i - 1], samples.z()[i] - samples.z()[i - 1]);
            assertTrue(step <= 10.5f, "evenly spaced along the curve: " + step);
        }
        assertThrows(IllegalArgumentException.class, () -> TerrainPath.sample(new float[]{0f}, new float[]{0f}, 10f));
    }

    @Test
    void shouldLayARoadThatFollowsACurveAcrossAHillAtAnEvenGradeAndUndoIt() {
        Terrain terrain = hill();
        float[] before = terrain.copyHeights();
        float[] xs = {-380f, -150f, 60f, 380f};
        float[] zs = {-80f, 40f, -30f, 60f};

        TerrainDelta delta = TerrainPath.carve(terrain, xs, zs, TerrainPath.Kind.ROAD, 60f, 0f, Surface.DIRT);

        TerrainPath.Samples road = TerrainPath.sample(xs, zs, CELL);
        float highest = 0f;
        float previous = terrain.heightAt(road.x()[0], road.z()[0]);
        for (int i = 1; i < road.size(); i++) {
            float h = terrain.heightAt(road.x()[i], road.z()[i]);
            highest = Math.max(highest, h);
            float step = road.distance()[i] - road.distance()[i - 1];
            assertTrue(Math.abs(h - previous) <= TerrainPath.MAX_ROAD_GRADE * step + 1.5f,
                "the road climbs evenly: " + (h - previous) + " over " + step + " at sample " + i);
            previous = h;
        }
        assertTrue(highest > 40f, "the road goes up over the hill, not through it: " + highest);
        // Across the road the surface is level (the same height a little to either side of the middle)
        int mid = road.size() / 2;
        float tx = road.x()[mid + 1] - road.x()[mid - 1];
        float tz = road.z()[mid + 1] - road.z()[mid - 1];
        float len = (float) Math.hypot(tx, tz);
        float nx = -tz / len * 20f;
        float nz = tx / len * 20f;
        assertEquals(terrain.heightAt(road.x()[mid] + nx, road.z()[mid] + nz), terrain.heightAt(road.x()[mid] - nx, road.z()[mid] - nz), 2f,
            "level across the road");
        int cx = Math.round((road.x()[mid] + terrain.getSizeX() / 2f) / CELL);
        int cz = Math.round((road.z()[mid] + terrain.getSizeZ() / 2f) / CELL);
        assertEquals(Surface.DIRT, terrain.dominantSurface(cx, cz), "the road is painted");
        assertEquals(before[terrain.index(0, 0)], terrain.getHeight(0, 0), "far ground is untouched");

        terrain.apply(delta, false);
        assertArrayEquals(before, terrain.copyHeights(), "one undo takes the road back");
        assertTrue(!terrain.hasWeights(), "and its paint");
    }

    @Test
    void shouldCutARiverWhoseBedRunsDownhillAndRaiseACliffOnTheLeft() {
        Terrain terrain = new Terrain(SIZE, SIZE, CELL);
        for (int iz = 0; iz < SIZE; iz++) {
            for (int ix = 0; ix < SIZE; ix++) {
                terrain.setHeight(ix, iz, 100f - ix * 1f + 30f * (float) Math.sin(ix * 0.4)); // falling to +x, with bumps
            }
        }
        float[] xs = {-350f, 0f, 350f};
        float[] zs = {0f, 30f, 0f};
        TerrainPath.carve(terrain, xs, zs, TerrainPath.Kind.RIVER, 60f, 20f, Surface.AUTO);
        TerrainPath.Samples river = TerrainPath.sample(xs, zs, CELL);
        float bed = Float.MAX_VALUE;
        for (int i = 0; i < river.size(); i++) {
            float h = terrain.heightAt(river.x()[i], river.z()[i]);
            assertTrue(h <= bed + 1f, "the bed never rises along the river: " + h + " after " + bed);
            bed = Math.min(bed, h);
        }

        Terrain flat = new Terrain(SIZE, SIZE, CELL);
        TerrainPath.carve(flat, new float[]{-300f, 300f}, new float[]{0f, 0f}, TerrainPath.Kind.CLIFF, 80f, 50f, Surface.AUTO);
        assertEquals(50f, flat.heightAt(0f, -60f), 0.5f, "the left of the line (travelling +x, that is -z) is raised");
        assertEquals(0f, flat.heightAt(0f, 60f), "the right is not");
        assertTrue(flat.heightAt(0f, -8f) < 50f, "the face rises steeply from the line");
    }
}
