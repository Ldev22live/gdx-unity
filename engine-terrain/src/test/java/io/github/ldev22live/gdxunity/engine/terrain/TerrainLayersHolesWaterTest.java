package io.github.ldev22live.gdxunity.engine.terrain;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap M3.4 (TD-089): blended paint layers with soft edges, holes and a water level, in the model, strokes and the file. */
class TerrainLayersHolesWaterTest {

    private static TerrainDelta stroke(Terrain terrain, TerrainBrush brush, float x, float z) {
        TerrainStroke stroke = new TerrainStroke(terrain);
        stroke.begin(brush, x, z);
        return stroke.end();
    }

    private static TerrainBrush paint(Surface surface, float radius) {
        TerrainBrush brush = new TerrainBrush();
        brush.setType(TerrainBrush.Type.PAINT);
        brush.setSurface(surface);
        brush.setRadius(radius);
        return brush;
    }

    @Test
    void shouldRepaintASlopeWithASoftEdgeThatFadesFromTheCentre() {
        Terrain slope = new Terrain(41, 41, 10f);
        for (int iz = 0; iz < 41; iz++) {
            for (int ix = 0; ix < 41; ix++) {
                slope.setHeight(ix, iz, ix * 8f); // a steady slope rising along x
            }
        }
        slope.fillSurface(Surface.GRASS);

        stroke(slope, paint(Surface.ROCK, 100f), 0f, 0f);

        int previous = Terrain.FULL_WEIGHT + 1;
        int softPoints = 0;
        for (int ix = 20; ix <= 30; ix++) {
            int weight = slope.getLayerWeight(ix, 20, Surface.ROCK);
            assertTrue(weight <= previous, "fades outwards: " + weight + " after " + previous + " at column " + ix);
            if (weight > 0 && weight < Terrain.FULL_WEIGHT) {
                softPoints++;
            }
            previous = weight;
        }
        assertTrue(softPoints >= 3, "several points across the edge are part rock, part grass: " + softPoints);
        assertEquals(Surface.ROCK, slope.dominantSurface(20, 20));
        assertEquals(0, slope.getLayerWeight(31, 20, Surface.ROCK), "beyond the radius nothing changed");
    }

    @Test
    void shouldNeverLetTheWeightsOfAPointSumPastFullWhenLayersOverlap() {
        Terrain terrain = new Terrain(21, 21, 10f);
        stroke(terrain, paint(Surface.SAND, 80f), 0f, 0f);
        stroke(terrain, paint(Surface.SNOW, 80f), 30f, 0f);
        stroke(terrain, paint(Surface.DIRT, 80f), 15f, 20f);

        for (int iz = 0; iz < 21; iz++) {
            for (int ix = 0; ix < 21; ix++) {
                int sum = 0;
                for (int layer = 0; layer < Surface.LAYER_COUNT; layer++) {
                    sum += terrain.getLayerWeight(ix, iz, Surface.ofLayer(layer));
                }
                assertTrue(sum <= Terrain.FULL_WEIGHT, "point " + ix + ", " + iz + " sums to " + sum);
                assertEquals(Terrain.FULL_WEIGHT - sum, terrain.getBaseWeight(ix, iz));
            }
        }
        assertEquals(Surface.DIRT, terrain.dominantSurface(12, 12), "the latest layer covers the earlier ones under its centre");
    }

    @Test
    void shouldCutAHoleThatOpensTheCellsAroundItFillItWithCtrlAndUndoBoth() {
        Terrain terrain = new Terrain(21, 21, 10f);
        TerrainBrush hole = new TerrainBrush();
        hole.setType(TerrainBrush.Type.HOLE);
        hole.setRadius(25f);

        TerrainDelta cut = stroke(terrain, hole, 0f, 0f);

        assertTrue(terrain.isHole(10, 10), "the centre point is a hole");
        assertFalse(terrain.isHole(0, 0));
        assertTrue(terrain.isCellOpen(9, 9) && terrain.isCellOpen(10, 10), "every cell touching it is open");
        assertTrue(terrain.isHoleAt(1f, 1f), "there is no ground at the centre");
        assertFalse(terrain.isHoleAt(-90f, -90f));
        assertTrue(cut.holeIndices().length > 0);
        assertEquals(0, cut.size(), "the heights are untouched");

        terrain.apply(cut, false);
        assertFalse(terrain.hasHoles(), "undo fills it");
        terrain.apply(cut, true);
        assertTrue(terrain.isHole(10, 10), "redo cuts it again");

        hole.setInverted(true);
        stroke(terrain, hole, 0f, 0f);
        assertFalse(terrain.hasHoles(), "Ctrl fills holes");
    }

    @Test
    void shouldKeepAWaterLevelOrNone() {
        Terrain terrain = new Terrain(5, 5, 10f);
        assertFalse(terrain.hasWater());
        int revision = terrain.getRevision();

        terrain.setWaterLevel(-12.5f);

        assertTrue(terrain.hasWater());
        assertEquals(-12.5f, terrain.getWaterLevel());
        assertTrue(terrain.getRevision() > revision, "a renderer sees the change");
        terrain.setWaterLevel(Float.NaN);
        assertFalse(terrain.hasWater());
        assertThrows(IllegalArgumentException.class, () -> terrain.setWaterLevel(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> terrain.setWaterLevel(Terrain.MAX_ABS_HEIGHT * 2f));
    }

    @Test
    void shouldStoreLayersHolesAndWaterInTheFileOnlyWhenUsedAndReadThemBackExactly() {
        Terrain plain = TerrainGenerator.flat("Plain", 9, 10f);
        String old = TerrainIO.toJson(plain);
        assertFalse(old.contains("layers") || old.contains("holes") || old.contains("waterLevel"), "an unchanged terrain writes the old format");

        Terrain terrain = TerrainGenerator.flat("Lake", 9, 10f);
        stroke(terrain, paint(Surface.DIRT, 30f), 0f, 0f);
        terrain.setHole(2, 3, true);
        terrain.setWaterLevel(-4f);
        String json = TerrainIO.toJson(terrain);
        Terrain back = TerrainIO.fromJson(json);

        assertArrayEquals(terrain.copyWeights(), back.copyWeights());
        assertArrayEquals(terrain.copyHoles(), back.copyHoles());
        assertEquals(-4f, back.getWaterLevel());
        assertEquals(json, TerrainIO.toJson(back), "the same terrain gives the same text");
        Terrain copy = TerrainGenerator.flat("Copy", 9, 10f);
        copy.copyFrom(back);
        assertArrayEquals(back.copyWeights(), copy.copyWeights(), "a reload in place keeps the paint");
        assertTrue(copy.isHole(2, 3));
        assertEquals(-4f, copy.getWaterLevel());
    }

    @Test
    void shouldReadAnOlderFileWithFewerLayersAndRefuseBadPaintOrHoles() {
        Terrain terrain = TerrainGenerator.flat("Old", 3, 10f);
        String head = TerrainIO.toJson(terrain).replaceAll("\\n}\\n$", "");
        byte[] two = new byte[9 * 2];
        two[0] = (byte) 200; // grass 200 at point 0
        two[1] = (byte) 55;  // sand 55
        Terrain older = TerrainIO.fromJson(head + ",\n\"layerCount\": 2, \"layers\": \"" + Base64.getEncoder().encodeToString(two) + "\"\n}");
        assertEquals(200, older.getLayerWeight(0, 0, Surface.GRASS), "an older file with fewer layers reads");
        assertEquals(55, older.getLayerWeight(0, 0, Surface.SAND));
        assertEquals(0, older.getLayerWeight(0, 0, Surface.DIRT), "the layers it did not have are empty");

        two[1] = (byte) 56; // 200 + 56 > 255
        String tooMuch = head + ",\n\"layerCount\": 2, \"layers\": \"" + Base64.getEncoder().encodeToString(two) + "\"\n}";
        assertTrue(assertThrows(TerrainFormatException.class, () -> TerrainIO.fromJson(tooMuch)).getMessage().contains("sum to 256"));
        String noCount = head + ",\n\"layers\": \"" + Base64.getEncoder().encodeToString(two) + "\"\n}";
        assertThrows(TerrainFormatException.class, () -> TerrainIO.fromJson(noCount));
        String tooMany = head + ",\n\"layerCount\": 99, \"layers\": \"\"\n}";
        assertTrue(assertThrows(TerrainFormatException.class, () -> TerrainIO.fromJson(tooMany)).getMessage().contains("layerCount"));
        byte[] holes = new byte[9];
        holes[4] = 2;
        String badHole = head + ",\n\"holes\": \"" + Base64.getEncoder().encodeToString(holes) + "\"\n}";
        assertTrue(assertThrows(TerrainFormatException.class, () -> TerrainIO.fromJson(badHole)).getMessage().contains("0 or 1"));
        String shortHoles = head + ",\n\"holes\": \"" + Base64.getEncoder().encodeToString(new byte[4]) + "\"\n}";
        assertThrows(TerrainFormatException.class, () -> TerrainIO.fromJson(shortHoles));
    }
}
