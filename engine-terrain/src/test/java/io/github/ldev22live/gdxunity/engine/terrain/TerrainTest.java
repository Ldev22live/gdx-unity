package io.github.ldev22live.gdxunity.engine.terrain;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainTest {
    @BeforeAll
    static void init() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    @Test
    void shouldCentreTheGridOnTheOriginAndSizeItByCells() {
        Terrain terrain = new Terrain(11, 21, 10f);

        assertEquals(100f, terrain.getSizeX());
        assertEquals(200f, terrain.getSizeZ());
        assertEquals(-50f, terrain.xAt(0));
        assertEquals(50f, terrain.xAt(10));
        assertEquals(-100f, terrain.zAt(0));
    }

    @Test
    void shouldRefuseAGridOrCellSizeOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new Terrain(1, 5, 1f));
        assertThrows(IllegalArgumentException.class, () -> new Terrain(5, Terrain.MAX_RESOLUTION + 1, 1f));
        assertThrows(IllegalArgumentException.class, () -> new Terrain(5, 5, 0f));
        assertThrows(IllegalArgumentException.class, () -> new Terrain(5, 5, Float.NaN));
    }

    @Test
    void shouldRefuseNonFiniteHeightsAndHoldHugeOnesAtTheLimit() {
        Terrain terrain = new Terrain(5, 5, 1f);

        assertThrows(IllegalArgumentException.class, () -> terrain.setHeight(0, 0, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> terrain.setHeight(0, 0, Float.POSITIVE_INFINITY));
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.setHeight(5, 0, 1f));
        terrain.setHeight(1, 1, 1e9f);
        assertEquals(Terrain.MAX_ABS_HEIGHT, terrain.getHeight(1, 1));
    }

    @Test
    void shouldInterpolateTheHeightBetweenGridPointsAndSayNothingOutsideTheTerrain() {
        Terrain terrain = new Terrain(3, 3, 10f);
        terrain.setHeight(1, 1, 8f);

        assertEquals(8f, terrain.heightAt(0f, 0f), 1e-5);
        assertEquals(4f, terrain.heightAt(5f, 0f), 1e-5, "halfway to the neighbour at 0");
        assertEquals(0f, terrain.heightAt(-10f, -10f), 1e-5);
        assertTrue(Float.isNaN(terrain.heightAt(10.5f, 0f)), "off the terrain there is no height");
        assertTrue(Float.isNaN(terrain.heightAt(0f, -10.5f)));
    }

    @Test
    void shouldPointTheNormalUpOnFlatGroundAndAwayFromASlope() {
        Terrain terrain = new Terrain(5, 5, 10f);
        Vector3 normal = new Vector3();
        terrain.normalAt(2, 2, normal);
        assertEquals(1f, normal.y, 1e-5);

        for (int iz = 0; iz < 5; iz++) {
            for (int ix = 0; ix < 5; ix++) {
                terrain.setHeight(ix, iz, ix * 10f);
            }
        }
        terrain.normalAt(2, 2, normal);
        assertTrue(normal.x < 0f, "a surface rising along +x faces -x");
        assertEquals(Math.sqrt(0.5), normal.y, 1e-4);
    }

    @Test
    void shouldFindWhereARayFromAboveMeetsTheGround() {
        Terrain terrain = new Terrain(11, 11, 10f);
        for (int iz = 0; iz < 11; iz++) {
            for (int ix = 0; ix < 11; ix++) {
                terrain.setHeight(ix, iz, 20f);
            }
        }
        Terrain.Hit hit = new Terrain.Hit();

        assertTrue(terrain.raycast(new Ray(new Vector3(12f, 100f, -7f), new Vector3(0, -1, 0)), 1000f, hit));
        assertEquals(12f, hit.x, 1e-3);
        assertEquals(20f, hit.y, 1e-2);
        assertEquals(-7f, hit.z, 1e-3);
        assertEquals(80f, hit.distance, 1e-2);
        assertEquals(1f, hit.ny, 1e-4);

        // Slanted, from outside the footprint
        assertTrue(terrain.raycast(new Ray(new Vector3(-200f, 120f, 0f), new Vector3(2, -1, 0).nor()), 1000f, hit));
        assertEquals(20f, hit.y, 1e-2);
        assertEquals(0f, hit.x, 1e-2, "dropping 100 at one unit down per two across, from x = -200");
    }

    @Test
    void shouldMissWhenTheRayLeavesTheTerrainStartsBelowItOrIsTooShort() {
        Terrain terrain = new Terrain(11, 11, 10f);
        Terrain.Hit hit = new Terrain.Hit();

        assertFalse(terrain.raycast(new Ray(new Vector3(500f, 100f, 0f), new Vector3(0, -1, 0)), 1000f, hit), "beside the terrain");
        assertFalse(terrain.raycast(new Ray(new Vector3(0f, -5f, 0f), new Vector3(0, 1, 0)), 1000f, hit), "from underneath");
        assertFalse(terrain.raycast(new Ray(new Vector3(0f, 100f, 0f), new Vector3(0, -1, 0)), 50f, hit), "not far enough");
        assertFalse(terrain.raycast(new Ray(new Vector3(0f, 100f, 0f), new Vector3(0, 1, 0)), 1000f, hit), "away from the ground");
        assertFalse(terrain.raycast(new Ray(new Vector3(0f, 100f, 0f), new Vector3(0, 1, 0)), Float.MAX_VALUE, hit),
            "straight up with no limit must end, not march forever");
        assertFalse(terrain.raycast(new Ray(new Vector3(0f, 100f, 0f), new Vector3(0, -1, 0)), 50f, hit), "straight down, too short");
        assertTrue(terrain.raycast(new Ray(new Vector3(3f, 100f, 3f), new Vector3(0, -1, 0)), Float.MAX_VALUE, hit), "straight down, no limit");
        assertEquals(100f, hit.distance, 1e-3);
    }

    @Test
    void shouldCountARevisionOnlyOnTheChunksAHeightChangeCanAffect() {
        int resolution = Terrain.CHUNK_CELLS * 3 + 1;
        Terrain terrain = new Terrain(resolution, resolution, 1f);
        assertEquals(3, terrain.getChunksX());

        terrain.setHeight(5, 5, 3f);
        assertEquals(1, terrain.getChunkRevision(0, 0));
        assertEquals(0, terrain.getChunkRevision(1, 0));
        assertEquals(0, terrain.getChunkRevision(0, 1));

        // A point on the border shared by chunks 0 and 1 changes both, and the diagonal chunk reads it for a normal
        int border = Terrain.CHUNK_CELLS;
        terrain.setHeight(border, border, 2f);
        assertTrue(terrain.getChunkRevision(1, 1) > 0);
        assertTrue(terrain.getChunkRevision(0, 0) > 1);
        assertEquals(0, terrain.getChunkRevision(2, 2));

        int before = terrain.getChunkRevision(0, 0);
        terrain.setHeight(5, 5, 3f);
        assertEquals(before, terrain.getChunkRevision(0, 0), "setting the same height changes nothing");
    }

    @Test
    void shouldApplyAChangeForwardAndBackward() {
        Terrain terrain = new Terrain(5, 5, 1f);
        terrain.setHeight(2, 2, 1f);
        TerrainDelta delta = new TerrainDelta(new int[]{terrain.index(2, 2), terrain.index(3, 2)}, new float[]{1f, 0f}, new float[]{5f, 6f});

        terrain.apply(delta, true);
        assertEquals(5f, terrain.getHeight(2, 2));
        assertEquals(6f, terrain.getHeight(3, 2));
        terrain.apply(delta, false);
        assertEquals(1f, terrain.getHeight(2, 2));
        assertEquals(0f, terrain.getHeight(3, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.apply(new TerrainDelta(new int[]{99}, new float[]{0}, new float[]{1}), true));
        assertThrows(IllegalArgumentException.class, () -> new TerrainDelta(new int[]{1}, new float[0], new float[]{1}));
    }

    @Test
    void shouldTakeOverTheHeightsOfAnotherTerrainOfTheSameGridOnly() {
        Terrain terrain = new Terrain(5, 5, 1f);
        Terrain other = new Terrain(5, 5, 1f);
        other.setHeight(1, 1, 7f);
        other.setName("Other");

        terrain.copyFrom(other);

        assertEquals(7f, terrain.getHeight(1, 1));
        assertEquals("Other", terrain.getName());
        assertNotEquals(0, terrain.getChunkRevision(0, 0));
        assertThrows(IllegalArgumentException.class, () -> terrain.copyFrom(new Terrain(6, 5, 1f)));
        assertThrows(IllegalArgumentException.class, () -> terrain.copyFrom(new Terrain(5, 5, 2f)));
    }
}
