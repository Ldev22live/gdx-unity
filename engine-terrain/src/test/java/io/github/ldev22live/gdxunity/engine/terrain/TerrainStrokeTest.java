package io.github.ldev22live.gdxunity.engine.terrain;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainStrokeTest {
    @BeforeAll
    static void init() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    private static Terrain terrain() {
        return new Terrain(65, 65, 10f);
    }

    private static TerrainBrush brush(TerrainBrush.Type type, float radius, float strength) {
        TerrainBrush brush = new TerrainBrush();
        brush.setType(type);
        brush.setRadius(radius);
        brush.setStrength(strength);
        return brush;
    }

    @Test
    void shouldRaiseMostAtTheCentreAndNothingBeyondTheRadius() {
        Terrain terrain = terrain();
        TerrainStroke stroke = new TerrainStroke(terrain);

        stroke.begin(brush(TerrainBrush.Type.RAISE, 100f, 1f), 0f, 0f);
        TerrainDelta delta = stroke.end();

        float centre = terrain.heightAt(0f, 0f);
        assertEquals(100f * TerrainStroke.HEIGHT_PER_DAB, centre, 1e-3, "full strength raises the centre by radius times the rate");
        assertTrue(terrain.heightAt(50f, 0f) > 0f && terrain.heightAt(50f, 0f) < centre, "less towards the edge");
        assertEquals(0f, terrain.heightAt(110f, 0f), "outside the radius nothing changes");
        assertFalse(delta.isEmpty());
        for (float before : delta.before()) {
            assertEquals(0f, before, "the change remembers the flat ground it began on");
        }
    }

    @Test
    void shouldLowerAndInvertRaiseAndLower() {
        Terrain terrain = terrain();
        TerrainStroke stroke = new TerrainStroke(terrain);

        stroke.begin(brush(TerrainBrush.Type.LOWER, 100f, 1f), 0f, 0f);
        stroke.end();
        assertTrue(terrain.heightAt(0f, 0f) < 0f);

        TerrainBrush inverted = brush(TerrainBrush.Type.LOWER, 100f, 1f);
        inverted.setInverted(true);
        stroke.begin(inverted, 0f, 0f);
        stroke.end();
        assertEquals(0f, terrain.heightAt(0f, 0f), 1e-4, "inverted lower raises by as much as it lowered");
    }

    @Test
    void shouldDabAlongTheDragSoALongStrokeRaisesAWholeRidge() {
        Terrain terrain = terrain();
        TerrainStroke stroke = new TerrainStroke(terrain);
        stroke.begin(brush(TerrainBrush.Type.RAISE, 50f, 0.5f), -200f, 0f);
        stroke.move(200f, 0f);
        stroke.end();

        for (float x = -200f; x <= 200f; x += 50f) {
            assertTrue(terrain.heightAt(x, 0f) > 0.5f, "raised at x=" + x);
        }
        assertEquals(0f, terrain.heightAt(0f, 100f), "beside the ridge it stays flat");
    }

    @Test
    void shouldSmoothARoughSurfaceWithoutDependingOnTheOrderPointsAreVisited() {
        Terrain terrain = terrain();
        for (int iz = 0; iz < 65; iz++) {
            for (int ix = 0; ix < 65; ix++) {
                terrain.setHeight(ix, iz, (ix + iz) % 2 == 0 ? 10f : 0f);
            }
        }
        float spreadBefore = Math.abs(terrain.getHeight(32, 32) - terrain.getHeight(33, 32));

        TerrainStroke stroke = new TerrainStroke(terrain);
        stroke.begin(brush(TerrainBrush.Type.SMOOTH, 150f, 1f), 0f, 0f);
        stroke.end();

        float spreadAfter = Math.abs(terrain.getHeight(32, 32) - terrain.getHeight(33, 32));
        assertTrue(spreadAfter < spreadBefore, "neighbouring heights moved together: " + spreadBefore + " to " + spreadAfter);
        assertEquals(terrain.getHeight(10, 10), (10 + 10) % 2 == 0 ? 10f : 0f, "outside the brush nothing moved");
    }

    @Test
    void shouldFlattenTowardsTheHeightWhereTheStrokeBegan() {
        Terrain terrain = terrain();
        for (int iz = 0; iz < 65; iz++) {
            for (int ix = 0; ix < 65; ix++) {
                terrain.setHeight(ix, iz, ix * 2f);
            }
        }
        float target = terrain.heightAt(0f, 0f);
        TerrainStroke stroke = new TerrainStroke(terrain);
        stroke.begin(brush(TerrainBrush.Type.FLATTEN, 100f, 1f), 0f, 0f);
        stroke.move(60f, 0f);
        stroke.end();

        assertEquals(target, terrain.heightAt(0f, 0f), 1e-3);
        assertEquals(target, terrain.heightAt(50f, 0f), 2f, "the path moved to the start height");
        assertEquals(2f * 63f, terrain.getHeight(63, 32), "far away is untouched");
    }

    @Test
    void shouldAddRepeatableNoiseThatCanGoBothWays() {
        Terrain first = terrain();
        Terrain second = terrain();
        for (Terrain terrain : new Terrain[]{first, second}) {
            TerrainStroke stroke = new TerrainStroke(terrain);
            stroke.begin(brush(TerrainBrush.Type.NOISE, 200f, 1f), 0f, 0f);
            stroke.end();
        }

        assertTrue(java.util.Arrays.equals(first.copyHeights(), second.copyHeights()), "the same noise for the same place");
        boolean up = false;
        boolean down = false;
        for (float h : first.copyHeights()) {
            up |= h > 0.01f;
            down |= h < -0.01f;
        }
        assertTrue(up && down, "noise raises and lowers");
    }

    @Test
    void shouldReturnAnEmptyChangeWhenNothingMovedAndRefuseMisuse() {
        Terrain terrain = terrain();
        TerrainStroke stroke = new TerrainStroke(terrain);

        stroke.begin(brush(TerrainBrush.Type.FLATTEN, 100f, 1f), 0f, 0f);
        assertTrue(stroke.end().isEmpty(), "flattening flat ground changes nothing");
        assertThrows(IllegalStateException.class, stroke::end);
        assertThrows(IllegalStateException.class, () -> stroke.move(1f, 1f));
        stroke.begin(brush(TerrainBrush.Type.RAISE, 10f, 1f), 0f, 0f);
        assertThrows(IllegalStateException.class, () -> stroke.begin(brush(TerrainBrush.Type.RAISE, 10f, 1f), 0f, 0f));
        stroke.end();
        assertThrows(IllegalArgumentException.class, () -> stroke.begin(brush(TerrainBrush.Type.RAISE, 10f, 1f), 9999f, 0f));
        assertThrows(IllegalArgumentException.class, () -> brush(TerrainBrush.Type.RAISE, 0f, 1f));
        assertThrows(IllegalArgumentException.class, () -> brush(TerrainBrush.Type.RAISE, 1f, 2f));
    }

    @Test
    void shouldUndoAStrokeExactlyByTheReturnedChange() {
        Terrain terrain = terrain();
        float[] original = terrain.copyHeights();
        TerrainStroke stroke = new TerrainStroke(terrain);
        stroke.begin(brush(TerrainBrush.Type.RAISE, 120f, 0.8f), -100f, 0f);
        stroke.move(100f, 80f);
        TerrainDelta delta = stroke.end();
        float[] changed = terrain.copyHeights();

        terrain.apply(delta, false);
        assertTrue(java.util.Arrays.equals(original, terrain.copyHeights()), "undo restores every height exactly");
        terrain.apply(delta, true);
        assertTrue(java.util.Arrays.equals(changed, terrain.copyHeights()), "redo restores the stroke");
    }
}
