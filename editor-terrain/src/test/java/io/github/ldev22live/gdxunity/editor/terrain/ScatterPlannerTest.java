package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a Scatter dab puts objects (roadmap M3.6, TD-097): density, topping up, spacing, slope, height, water, holes and keep-out. */
class ScatterPlannerTest {
    /** A flat 65 by 65 point terrain, 40 units apart: 2560 units across, centred on the origin. */
    private static final int RESOLUTION = 65;
    private static final float CELL = 40f;
    private static final float RADIUS = 500f;
    private static final long SEED = 7L;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    private static Terrain flat() {
        return new Terrain(RESOLUTION, RESOLUTION, CELL);
    }

    /** Ground rising along x at {@code degrees} everywhere. */
    private static Terrain slope(float degrees) {
        Terrain terrain = flat();
        float rise = (float) Math.tan(Math.toRadians(degrees));
        for (int iz = 0; iz < RESOLUTION; iz++) {
            for (int ix = 0; ix < RESOLUTION; ix++) {
                terrain.setHeight(ix, iz, ix * CELL * rise);
            }
        }
        return terrain;
    }

    private static ScatterSettings settings(float density, float spacing) {
        ScatterSettings settings = new ScatterSettings();
        settings.setDensity(density);
        settings.setSpacing(spacing);
        return settings;
    }

    private static ScatterPlanner.Plan plan(Terrain terrain, ScatterSettings settings, List<ScatterPlanner.Obstacle> obstacles) {
        return ScatterPlanner.plan(terrain, 0f, 0f, RADIUS, settings, obstacles, new Random(SEED));
    }

    /** Objects wanted in the brush circle for a density. */
    private static int wanted(float density) {
        return Math.round(density * (float) Math.PI * RADIUS * RADIUS / ScatterSettings.DENSITY_AREA);
    }

    @Test
    void shouldFillTheCircleUpToTheDensityOnFlatGroundTheSameWayForTheSameSeed() {
        ScatterSettings settings = settings(8f, 0f);

        ScatterPlanner.Plan plan = plan(flat(), settings, List.of());

        assertEquals(wanted(8f), plan.placements().size(), "8 per 100 square metres over a 5 m circle");
        for (ScatterPlanner.Placement placement : plan.placements()) {
            assertTrue(placement.x() * placement.x() + placement.z() * placement.z() <= RADIUS * RADIUS, "inside the brush: " + placement);
            assertEquals(0f, placement.y(), 1e-4f, "standing on the ground");
            assertTrue(placement.scale() >= settings.getMinScale() && placement.scale() <= settings.getMaxScale(), "scale in range");
            assertTrue(placement.yawDegrees() >= 0f && placement.yawDegrees() < 360f);
        }
        assertEquals(plan.placements(), plan(flat(), settings, List.of()).placements(), "the same seed gives the same dab");
    }

    @Test
    void shouldTopUpToTheDensityCountingCopiesAlreadyThereButNotOtherObjects() {
        List<ScatterPlanner.Obstacle> obstacles = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            obstacles.add(new ScatterPlanner.Obstacle(-300f + i * 150f, 0f, 0f, true));
        }
        obstacles.add(new ScatterPlanner.Obstacle(0f, 300f, 0f, false));

        int placed = plan(flat(), settings(8f, 0f), obstacles).placements().size();

        assertEquals(wanted(8f) - 4, placed, "four copies are already inside; the other object does not count");
        assertTrue(plan(flat(), settings(8f, 0f), List.of(new ScatterPlanner.Obstacle(0f, 0f, 0f, true),
            new ScatterPlanner.Obstacle(10f, 0f, 0f, true), new ScatterPlanner.Obstacle(20f, 0f, 0f, true),
            new ScatterPlanner.Obstacle(30f, 0f, 0f, true), new ScatterPlanner.Obstacle(40f, 0f, 0f, true),
            new ScatterPlanner.Obstacle(50f, 0f, 0f, true), new ScatterPlanner.Obstacle(60f, 0f, 0f, true))).placements().isEmpty(),
            "a full circle gets nothing more");
    }

    @Test
    void shouldKeepTheSpacingBetweenObjectsAndFromObjectsAlreadyThere() {
        float spacing = 250f;
        ScatterPlanner.Obstacle standing = new ScatterPlanner.Obstacle(0f, 0f, 0f, false);

        ScatterPlanner.Plan plan = plan(flat(), settings(ScatterSettings.MAX_DENSITY, spacing), List.of(standing));

        List<ScatterPlanner.Placement> placed = plan.placements();
        assertFalse(placed.isEmpty());
        assertTrue(placed.size() < wanted(ScatterSettings.MAX_DENSITY), "spacing caps how many fit: " + placed.size());
        assertTrue(plan.refused().getOrDefault(ScatterPlanner.Refusal.CROWDED, 0) > 0);
        for (int i = 0; i < placed.size(); i++) {
            ScatterPlanner.Placement a = placed.get(i);
            assertTrue(Math.hypot(a.x(), a.z()) >= spacing, "away from the object already there: " + a);
            for (int j = i + 1; j < placed.size(); j++) {
                ScatterPlanner.Placement b = placed.get(j);
                assertTrue(Math.hypot(a.x() - b.x(), a.z() - b.z()) >= spacing, "spaced: " + a + " and " + b);
            }
        }
    }

    @Test
    void shouldRefuseGroundSteeperThanTheSlopeLimit() {
        ScatterSettings settings = settings(8f, 0f);
        settings.setMaxSlopeDegrees(35f);

        ScatterPlanner.Plan steep = plan(slope(50f), settings, List.of());
        ScatterPlanner.Plan gentle = plan(slope(25f), settings, List.of());

        assertTrue(steep.placements().isEmpty(), "50 degrees is over the limit");
        assertTrue(steep.refused().getOrDefault(ScatterPlanner.Refusal.SLOPE, 0) > 0);
        assertEquals(wanted(8f), gentle.placements().size(), "25 degrees is within it");
        Terrain terrain = slope(25f);
        for (ScatterPlanner.Placement placement : gentle.placements()) {
            assertEquals(terrain.heightAt(placement.x(), placement.z()), placement.y(), 0.01f, "on the sloping ground");
        }
    }

    @Test
    void shouldKeepToTheHeightRangeAndOffWaterHolesAndTheEdge() {
        Terrain terrain = slope(20f);
        ScatterSettings settings = settings(8f, 0f);
        float middle = terrain.heightAt(0f, 0f);
        settings.setHeightRange(middle, Float.POSITIVE_INFINITY);
        for (ScatterPlanner.Placement placement : plan(terrain, settings, List.of()).placements()) {
            assertTrue(placement.y() >= middle, "above the lowest height: " + placement);
            assertTrue(placement.x() >= -CELL, "so on the upper half of the circle: " + placement);
        }

        Terrain wet = flat();
        wet.setWaterLevel(10f);
        ScatterPlanner.Plan underWater = plan(wet, settings(8f, 0f), List.of());
        assertTrue(underWater.placements().isEmpty());
        assertTrue(underWater.refused().getOrDefault(ScatterPlanner.Refusal.WATER, 0) > 0);

        Terrain holed = flat();
        for (int iz = 0; iz < RESOLUTION; iz++) {
            for (int ix = 0; ix < RESOLUTION; ix++) {
                holed.setHole(ix, iz, true);
            }
        }
        assertEquals(0, plan(holed, settings(8f, 0f), List.of()).placements().size(), "no ground in a hole");

        float edge = RESOLUTION / 2 * CELL;
        ScatterPlanner.Plan atEdge = ScatterPlanner.plan(flat(), edge, 0f, RADIUS, settings(8f, 0f), List.of(), new Random(SEED));
        for (ScatterPlanner.Placement placement : atEdge.placements()) {
            assertTrue(placement.x() <= edge, "on the terrain: " + placement);
        }
        assertTrue(atEdge.refused().getOrDefault(ScatterPlanner.Refusal.OFF_TERRAIN, 0) > 0);
    }

    @Test
    void shouldKeepOutOfABuildingsFootprint() {
        float footprint = 300f;
        ScatterPlanner.Obstacle house = new ScatterPlanner.Obstacle(0f, 0f, footprint, false);

        ScatterPlanner.Plan plan = plan(flat(), settings(ScatterSettings.MAX_DENSITY, 0f), List.of(house));

        assertFalse(plan.placements().isEmpty(), "the ring outside the house is filled");
        for (ScatterPlanner.Placement placement : plan.placements()) {
            assertTrue(Math.hypot(placement.x(), placement.z()) >= footprint, "outside the house: " + placement);
        }
    }

    @Test
    void shouldRefuseImpossibleSettingsAndRadius() {
        ScatterSettings settings = new ScatterSettings();
        assertThrows(IllegalArgumentException.class, () -> settings.setDensity(0f));
        assertThrows(IllegalArgumentException.class, () -> settings.setDensity(ScatterSettings.MAX_DENSITY + 1f));
        assertThrows(IllegalArgumentException.class, () -> settings.setScaleRange(1.5f, 1f));
        assertThrows(IllegalArgumentException.class, () -> settings.setScaleRange(0f, 1f));
        assertThrows(IllegalArgumentException.class, () -> settings.setMaxSlopeDegrees(91f));
        assertThrows(IllegalArgumentException.class, () -> settings.setHeightRange(10f, 5f));
        assertThrows(IllegalArgumentException.class, () -> settings.setSpacing(-1f));
        assertThrows(IllegalArgumentException.class,
            () -> ScatterPlanner.plan(flat(), 0f, 0f, 0f, settings, List.of(), new Random(SEED)));
    }
}
