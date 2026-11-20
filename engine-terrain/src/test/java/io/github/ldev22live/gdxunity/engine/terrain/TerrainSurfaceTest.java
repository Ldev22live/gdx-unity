package io.github.ldev22live.gdxunity.engine.terrain;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ground a terrain is made of (TD-083): surfaces per point, painted with the brush, undone and stored in the file. */
class TerrainSurfaceTest {
    @BeforeAll
    static void init() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    @Test
    void shouldStartWithNoChosenSurfaceAndFillAWholeTerrainWithOne() {
        Terrain terrain = new Terrain(5, 5, 10f);
        assertEquals(Surface.AUTO, terrain.getSurface(2, 2));
        assertFalse(terrain.hasSurfaces());

        terrain.fillSurface(Surface.SAND);

        assertTrue(terrain.hasSurfaces());
        assertEquals(Surface.SAND, terrain.getSurface(0, 0));
        assertEquals(Surface.SAND, terrain.getSurface(4, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> terrain.getSurface(5, 0));
    }

    @Test
    void shouldKeepSurfaceCodesStableAndRefuseUnknownOnes() {
        assertEquals(0, Surface.AUTO.code());
        assertEquals(1, Surface.GRASS.code());
        assertEquals(2, Surface.SAND.code());
        assertEquals(3, Surface.SNOW.code());
        assertEquals(4, Surface.GRAVEL.code());
        assertEquals(Surface.GRAVEL, Surface.fromCode(4));
        assertEquals(5, Surface.ROCK.code(), "new layers go at the end (TD-089)");
        assertEquals(6, Surface.DIRT.code());
        assertEquals(6, Surface.LAYER_COUNT, "every surface but Auto is a paint layer");
        assertEquals(Surface.GRASS, Surface.ofLayer(0));
        assertThrows(IllegalStateException.class, Surface.AUTO::layer);
        assertThrows(IllegalArgumentException.class, () -> Surface.fromCode(7));
        assertThrows(IllegalArgumentException.class, () -> Surface.fromCode(-1));
    }

    @Test
    void shouldCountARevisionOnTheChunkWhenASurfaceChangesSoItsColoursAreRebuilt() {
        Terrain terrain = new Terrain(Terrain.CHUNK_CELLS * 2 + 1, Terrain.CHUNK_CELLS + 1, 1f);
        int before = terrain.getChunkRevision(0, 0);

        terrain.setSurface(3, 3, Surface.GRAVEL);

        assertTrue(terrain.getChunkRevision(0, 0) > before);
        assertEquals(0, terrain.getChunkRevision(1, 0));
        int again = terrain.getChunkRevision(0, 0);
        terrain.setSurface(3, 3, Surface.GRAVEL);
        assertEquals(again, terrain.getChunkRevision(0, 0), "the same surface changes nothing");
    }

    @Test
    void shouldPaintOnlyTheSurfaceUnderTheBrushAndLeaveTheHeightsAlone() {
        Terrain terrain = new Terrain(41, 41, 10f);
        terrain.fillSurface(Surface.GRASS);
        float[] heights = terrain.copyHeights();
        TerrainBrush brush = new TerrainBrush();
        brush.setType(TerrainBrush.Type.PAINT);
        brush.setSurface(Surface.SNOW);
        brush.setRadius(60f);
        TerrainStroke stroke = new TerrainStroke(terrain);

        stroke.begin(brush, 0f, 0f);
        TerrainDelta delta = stroke.end();

        // TD-089: paint is a layer weight over the base, full at the centre and fading out to the edge (a soft edge)
        assertEquals(Terrain.FULL_WEIGHT, terrain.getLayerWeight(20, 20, Surface.SNOW), "the centre is all snow");
        assertEquals(Surface.SNOW, terrain.dominantSurface(20, 20));
        int between = terrain.getLayerWeight(20 + 4, 20, Surface.SNOW);
        assertTrue(between > 0 && between < Terrain.FULL_WEIGHT, "part snow, part grass between the centre and the edge: " + between);
        assertEquals(0, terrain.getLayerWeight(20 + 6, 20, Surface.SNOW), "at the radius the edge is not painted");
        assertEquals(Surface.GRASS, terrain.dominantSurface(0, 0), "far away");
        assertEquals(Surface.GRASS, terrain.getSurface(20, 20), "the base ground stays under the paint");
        assertArrayEquals(heights, terrain.copyHeights());
        assertEquals(0, delta.size(), "no heights changed");
        assertEquals(0, delta.surfaceIndices().length, "no base ground changed");
        assertTrue(delta.weightIndices().length > 5 && delta.weightIndices().length < 200, "a round patch: " + delta.weightIndices().length);
        assertFalse(delta.isEmpty());

        terrain.apply(delta, false);
        assertEquals(0, terrain.getLayerWeight(20, 20, Surface.SNOW), "undo");
        assertFalse(terrain.hasWeights());
        terrain.apply(delta, true);
        assertEquals(Terrain.FULL_WEIGHT, terrain.getLayerWeight(20, 20, Surface.SNOW), "redo");
    }

    @Test
    void shouldWipeThePaintWhenThePaintBrushIsInvertedAndRememberTheFirstWeightOfAPoint() {
        Terrain terrain = new Terrain(21, 21, 10f);
        terrain.fillSurface(Surface.SAND);
        TerrainBrush brush = new TerrainBrush();
        brush.setType(TerrainBrush.Type.PAINT);
        brush.setSurface(Surface.GRAVEL);
        brush.setRadius(50f);
        TerrainStroke stroke = new TerrainStroke(terrain);

        stroke.begin(brush, 0f, 0f);
        stroke.move(10f, 0f);
        stroke.move(20f, 0f);
        TerrainDelta delta = stroke.end();

        assertTrue(delta.weightIndices().length > 0);
        for (int i = 0; i < delta.weightIndices().length; i++) {
            assertEquals(0, delta.weightBefore()[i], "before is what the weight was before the whole stroke, not after a dab");
            assertTrue((delta.weightAfter()[i] & 0xFF) > 0);
        }
        assertEquals(Surface.GRAVEL, terrain.dominantSurface(10, 10));
        brush.setInverted(true);
        stroke.begin(brush, 0f, 0f);
        stroke.end();
        assertEquals(0, terrain.getLayerWeight(10, 10, Surface.GRAVEL), "Ctrl wipes the paint at the centre");
        assertEquals(Surface.SAND, terrain.dominantSurface(10, 10), "and the base ground shows again");
    }

    @Test
    void shouldStoreSurfacesInTheFileOnlyWhenThereAreSomeAndReadThemBack() {
        Terrain plain = TerrainGenerator.hills("Plain", 17, 10f, 1L, 50f);
        assertFalse(TerrainIO.toJson(plain).contains("surfaces"), "a terrain with no chosen surface writes the old format");

        Terrain terrain = TerrainGenerator.flat("Level", 17, 10f);
        terrain.fillSurface(Surface.GRASS);
        terrain.setSurface(4, 5, Surface.GRAVEL);
        String json = TerrainIO.toJson(terrain);
        Terrain read = TerrainIO.fromJson(json);

        assertTrue(json.contains("\"surfaces\""));
        assertTrue(Arrays.equals(terrain.copySurfaces(), read.copySurfaces()));
        assertEquals(Surface.GRAVEL, read.getSurface(4, 5));
        assertEquals(json, TerrainIO.toJson(read), "the same terrain gives the same text");
    }

    @Test
    void shouldRefuseSurfacesOfTheWrongLengthOrWithAnUnknownCode() {
        Terrain terrain = new Terrain(3, 3, 1f);
        terrain.fillSurface(Surface.SNOW);
        String json = TerrainIO.toJson(terrain);
        String shortList = json.replaceFirst("\"surfaces\": \"[^\"]*\"", "\"surfaces\": \"AQID\"");
        String badCode = json.replaceFirst("\"surfaces\": \"[^\"]*\"",
            "\"surfaces\": \"" + java.util.Base64.getEncoder().encodeToString(new byte[]{1, 1, 1, 1, 9, 1, 1, 1, 1}) + "\"");

        assertThrows(TerrainFormatException.class, () -> TerrainIO.fromJson(shortList));
        assertThrows(TerrainFormatException.class, () -> TerrainIO.fromJson(badCode));
    }

    @Test
    void shouldTakeOverTheSurfacesOfAnotherTerrainWhenReloaded() {
        Terrain terrain = new Terrain(5, 5, 1f);
        Terrain other = new Terrain(5, 5, 1f);
        other.fillSurface(Surface.SAND);

        terrain.copyFrom(other);

        assertEquals(Surface.SAND, terrain.getSurface(2, 2));
    }
}
