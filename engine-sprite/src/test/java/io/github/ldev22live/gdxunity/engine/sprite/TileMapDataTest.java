package io.github.ldev22live.gdxunity.engine.sprite;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileMapDataTest {
    /** Tiles 1..16 for neighbour masks 0..15, so the tile id shows which mask was chosen. */
    private static AutoTileRule wall() {
        int[] tiles = new int[16];
        for (int i = 0; i < 16; i++) {
            tiles[i] = 10 + i;
        }
        return new AutoTileRule("wall", tiles);
    }

    @Test
    void shouldStartEmptyWithOneLayerAndRefuseCellsOutsideTheMap() {
        TileMapData map = new TileMapData(4, 3);

        assertEquals(1, map.getLayerCount());
        assertEquals(TileMapData.EMPTY, map.get(0, 3, 2));
        assertEquals(TileMapData.EMPTY, map.get(0, 9, 9), "reading outside gives empty");
        assertThrows(IndexOutOfBoundsException.class, () -> map.set(0, 4, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> map.set(0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new TileMapData(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TileMapData(TileMapData.MAX_CELLS + 1, 1));
    }

    @Test
    void shouldAddAndRemoveLayersButKeepAtLeastOne() {
        TileMapData map = new TileMapData(2, 2);

        assertEquals(1, map.addLayer("Decor"));
        map.set(1, 0, 0, 5);
        map.removeLayer(0);

        assertEquals("Decor", map.getLayer(0).getName());
        assertEquals(5, map.get(0, 0, 0));
        assertThrows(IllegalStateException.class, () -> map.removeLayer(0));
    }

    @Test
    void shouldMergeSolidCellsIntoFewBoxes() {
        TileMapData map = new TileMapData(5, 3);
        map.setSolid(1, true);
        // Row 2 is a full floor; a 2x2 block stands on it at columns 1-2 of rows 0-1
        for (int c = 0; c < 5; c++) {
            map.set(0, c, 2, 1);
        }
        map.set(0, 1, 0, 1);
        map.set(0, 2, 0, 1);
        map.set(0, 1, 1, 1);
        map.set(0, 2, 1, 1);

        List<TileMapData.CellBox> boxes = map.solidBoxes();

        // Greedy from the top left: the block grows down through the floor row at columns 1-2, so the floor is split around it
        assertEquals(Set.of(new TileMapData.CellBox(1, 0, 2, 3), new TileMapData.CellBox(0, 2, 1, 1), new TileMapData.CellBox(3, 2, 2, 1)), Set.copyOf(boxes));
    }

    @Test
    void shouldCoverEverySolidCellExactlyOnce() {
        TileMapData map = new TileMapData(6, 6);
        map.setSolid(2, true);
        int[] pattern = {0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 0, 1, 1, 1, 1, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1, 1, 0, 0, 1};
        for (int i = 0; i < pattern.length; i++) {
            map.set(0, i % 6, i / 6, pattern[i] == 1 ? 2 : 3);
        }

        int[] covered = new int[36];
        for (TileMapData.CellBox b : map.solidBoxes()) {
            for (int r = b.row(); r < b.row() + b.rows(); r++) {
                for (int c = b.col(); c < b.col() + b.cols(); c++) {
                    covered[r * 6 + c]++;
                }
            }
        }
        for (int i = 0; i < 36; i++) {
            assertEquals(pattern[i], covered[i], "cell " + i);
        }
    }

    @Test
    void shouldIgnoreHiddenLayersAndNonSolidTilesWhenBuildingBoxes() {
        TileMapData map = new TileMapData(3, 1);
        map.setSolid(1, true);
        map.set(0, 0, 0, 1);
        map.set(0, 1, 0, 2);
        map.addLayer("Hidden");
        map.set(1, 2, 0, 1);
        map.setLayerVisible(1, false);

        assertEquals(List.of(new TileMapData.CellBox(0, 0, 1, 1)), map.solidBoxes());
        map.setSolid(1, false);
        assertTrue(map.solidBoxes().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> map.setSolid(0, true));
    }

    @Test
    void shouldPickTheTileThatFitsTheNeighboursWhenPaintingWithARule() {
        TileMapData map = new TileMapData(5, 3);
        AutoTileRule rule = wall();

        map.paintAuto(0, 1, 1, rule);
        assertEquals(rule.tileFor(0), map.get(0, 1, 1), "alone");

        map.paintAuto(0, 2, 1, rule);
        assertEquals(rule.tileFor(AutoTileRule.EAST), map.get(0, 1, 1), "the left cell now has a neighbour to its east");
        assertEquals(rule.tileFor(AutoTileRule.WEST), map.get(0, 2, 1));

        map.paintAuto(0, 3, 1, rule);
        assertEquals(rule.tileFor(AutoTileRule.EAST | AutoTileRule.WEST), map.get(0, 2, 1), "the middle of a row");

        map.paintAuto(0, 2, 0, rule);
        assertEquals(rule.tileFor(AutoTileRule.EAST | AutoTileRule.WEST | AutoTileRule.NORTH), map.get(0, 2, 1));
        assertEquals(rule.tileFor(AutoTileRule.SOUTH), map.get(0, 2, 0));
    }

    @Test
    void shouldRefitNeighboursWhenAnAutoTileIsErasedAndNotTouchOtherTiles() {
        TileMapData map = new TileMapData(4, 1);
        AutoTileRule rule = wall();
        map.paintAuto(0, 0, 0, rule);
        map.paintAuto(0, 1, 0, rule);
        map.paintAuto(0, 2, 0, rule);
        map.set(0, 3, 0, 99);

        map.eraseAuto(0, 1, 0, rule);

        assertEquals(TileMapData.EMPTY, map.get(0, 1, 0));
        assertEquals(rule.tileFor(0), map.get(0, 0, 0));
        assertEquals(rule.tileFor(0), map.get(0, 2, 0), "the right piece lost its west neighbour and is alone now");
        assertEquals(99, map.get(0, 3, 0), "a tile of another kind is left alone");
    }

    @Test
    void shouldValidateAutoTileRules() {
        assertThrows(IllegalArgumentException.class, () -> new AutoTileRule("x", new int[15]));
        assertThrows(IllegalArgumentException.class, () -> new AutoTileRule(" ", new int[16]));
        int[] withZero = new int[16];
        java.util.Arrays.fill(withZero, 3);
        withZero[7] = 0;
        assertThrows(IllegalArgumentException.class, () -> new AutoTileRule("x", withZero));
        assertTrue(wall().contains(12));
        assertFalse(wall().contains(5));
    }

    @Test
    void shouldReplaceARuleOfTheSameName() {
        TileMapData map = new TileMapData(2, 2);
        map.putRule(wall());
        int[] other = new int[16];
        java.util.Arrays.fill(other, 4);

        map.putRule(new AutoTileRule("wall", other));

        assertEquals(1, map.getRules().size());
        assertEquals(4, map.getRule("wall").tileFor(3));
        map.removeRule("wall");
        assertNull(map.getRule("wall"));
    }

    @Test
    void shouldResizeKeepingTheTopLeftAndCopyDeeply() {
        TileMapData map = new TileMapData(3, 3);
        map.set(0, 2, 2, 7);
        map.set(0, 0, 0, 4);
        map.setSolid(4, true);

        TileMapData copy = map.copy();
        assertTrue(map.contentEquals(copy));
        map.resize(2, 2);

        assertEquals(4, map.get(0, 0, 0));
        assertEquals(TileMapData.EMPTY, map.get(0, 2, 2));
        assertEquals(3, copy.getCols());
        assertEquals(7, copy.get(0, 2, 2));
        assertFalse(map.contentEquals(copy));
        assertArrayEquals(new int[] {4}, map.getSolidTiles());
    }

    @Test
    void shouldRejectLayerDataThatDoesNotMatchTheMapSize() {
        TileMapData map = new TileMapData(2, 2);

        assertThrows(IllegalArgumentException.class, () -> map.setCells(0, new int[3]));
        assertThrows(IllegalArgumentException.class, () -> map.loadLayers(List.of("A"), List.of(true), List.of(new int[5])));
        map.loadLayers(List.of("A", "B"), List.of(true, false), List.of(new int[] {1, 2, 3, 4}, new int[4]));
        assertEquals(2, map.getLayerCount());
        assertFalse(map.getLayer(1).isVisible());
        assertEquals(List.of("A", "B"), java.util.stream.IntStream.range(0, 2).mapToObj(i -> map.getLayer(i).getName()).collect(Collectors.toList()));
    }
}
