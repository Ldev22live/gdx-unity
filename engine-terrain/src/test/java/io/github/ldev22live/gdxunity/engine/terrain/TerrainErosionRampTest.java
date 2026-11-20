package io.github.ldev22live.gdxunity.engine.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap M3.5: the erosion brush softens steep ground without losing any, and the ramp lays an even slope between two points. */
class TerrainErosionRampTest {
    private static final float CELL = 10f;
    private static final int SIZE = 41;

    private static Terrain cliff(float height) {
        Terrain terrain = new Terrain(SIZE, SIZE, CELL);
        for (int iz = 0; iz < SIZE; iz++) {
            for (int ix = SIZE / 2; ix < SIZE; ix++) {
                terrain.setHeight(ix, iz, height); // a wall along z at the middle column
            }
        }
        return terrain;
    }

    private static float steepestDrop(Terrain terrain, int iz) {
        float steepest = 0f;
        for (int ix = 1; ix < SIZE; ix++) {
            steepest = Math.max(steepest, Math.abs(terrain.getHeight(ix, iz) - terrain.getHeight(ix - 1, iz)) / CELL);
        }
        return steepest;
    }

    private static double total(Terrain terrain) {
        double sum = 0;
        for (float h : terrain.copyHeights()) {
            sum += h;
        }
        return sum;
    }

    private static TerrainBrush brush(TerrainBrush.Type type, float radius) {
        TerrainBrush brush = new TerrainBrush();
        brush.setType(type);
        brush.setRadius(radius);
        brush.setStrength(1f);
        return brush;
    }

    @Test
    void shouldWearACliffIntoASlopeWithoutLosingGroundAndUndoExactly() {
        Terrain terrain = cliff(200f);
        float[] before = terrain.copyHeights();
        double ground = total(terrain);
        float wall = steepestDrop(terrain, SIZE / 2);

        TerrainStroke stroke = new TerrainStroke(terrain);
        stroke.begin(brush(TerrainBrush.Type.ERODE, 120f), 0f, 0f);
        stroke.move(0f, 60f);
        TerrainDelta delta = stroke.end();

        float after = steepestDrop(terrain, SIZE / 2);
        assertTrue(after < wall * 0.5f, "the cliff is at most half as steep: " + after + " from " + wall);
        assertTrue(terrain.getHeight(SIZE / 2 - 1, SIZE / 2) > 1f, "ground piled at the foot of the cliff");
        assertTrue(terrain.getHeight(SIZE / 2, SIZE / 2) < 199f, "and the top edge wore down");
        assertEquals(ground, total(terrain), 0.5, "erosion moves ground, it does not remove it");
        assertEquals(steepestDrop(cliff(200f), 0), steepestDrop(terrain, 0), 1e-6f, "far from the brush the cliff is as it was");

        terrain.apply(delta, false);
        assertArrayEquals(before, terrain.copyHeights(), "one undo puts every height back");
    }

    @Test
    void shouldLeaveGroundGentlerThanTheTalusSlopeAlone() {
        Terrain terrain = new Terrain(SIZE, SIZE, CELL);
        for (int iz = 0; iz < SIZE; iz++) {
            for (int ix = 0; ix < SIZE; ix++) {
                terrain.setHeight(ix, iz, ix * CELL * TerrainStroke.TALUS_SLOPE * 0.9f);
            }
        }
        TerrainStroke stroke = new TerrainStroke(terrain);
        stroke.begin(brush(TerrainBrush.Type.ERODE, 150f), 0f, 0f);

        assertTrue(stroke.end().isEmpty(), "a slope gentler than the talus does not erode");
    }

    @Test
    void shouldLayAnEvenRampBetweenTwoPointsBlendingAtItsSidesAndUndoIt() {
        // A low floor on the left and a raised shelf on the right, with a steep step between them
        Terrain terrain = new Terrain(SIZE, SIZE, CELL);
        for (int iz = 0; iz < SIZE; iz++) {
            for (int ix = SIZE / 2 + 5; ix < SIZE; ix++) {
                terrain.setHeight(ix, iz, 100f);
            }
        }
        float[] before = terrain.copyHeights();
        TerrainStroke stroke = new TerrainStroke(terrain);
        stroke.begin(brush(TerrainBrush.Type.RAMP, 50f), -150f, 0f);
        stroke.move(0f, 0f);
        stroke.move(150f, 0f);
        assertArrayEquals(before, terrain.copyHeights(), "nothing changes until the stroke ends");

        TerrainDelta delta = stroke.end();

        for (float x = -140f; x <= 140f; x += 40f) {
            float expected = 100f * (x + 150f) / 300f;
            assertEquals(expected, terrain.heightAt(x, 0f), 1f, "even slope along the line at x " + x);
        }
        // On the low side (the floor is 0 there): near the ramp's side the ground is between the floor and the ramp
        float edge = terrain.heightAt(-50f, 40f);
        float onLine = terrain.heightAt(-50f, 0f);
        assertTrue(edge > 0f && edge < onLine, "near the side it blends into the floor: " + edge + " against " + onLine);
        assertEquals(0f, terrain.heightAt(-50f, 100f), "beside the ramp the floor is untouched");
        assertTrue(delta.size() > 0);

        terrain.apply(delta, false);
        assertArrayEquals(before, terrain.copyHeights(), "one undo takes the ramp back");
    }

    @Test
    void shouldLayNoRampForAClick() {
        Terrain terrain = cliff(100f);
        TerrainStroke stroke = new TerrainStroke(terrain);
        stroke.begin(brush(TerrainBrush.Type.RAMP, 50f), 0f, 0f);

        assertTrue(stroke.end().isEmpty(), "no drag, no line, no ramp");
    }
}
