package io.github.ldev22live.gdxunity.engine.sprite;

import java.util.Arrays;
import java.util.Objects;

/**
 * An auto-tile rule (TD-066, milestone E): 16 tiles, one for each combination of the four neighbours that belong to the same group, so painting one cell
 * picks the edge or corner piece that fits and keeps its neighbours fitting. The tile is chosen by a bit mask: north 1, east 2, south 4, west 8.
 *
 * @param name  rule name
 * @param tiles the 16 tile ids (frame index + 1 of the tile set), index = neighbour mask; all above 0
 */
public record AutoTileRule(String name, int[] tiles) {
    /** Number of neighbour combinations. */
    public static final int VARIANTS = 16;
    public static final int NORTH = 1;
    public static final int EAST = 2;
    public static final int SOUTH = 4;
    public static final int WEST = 8;

    public AutoTileRule {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(tiles, "tiles cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("auto-tile rule name cannot be blank");
        }
        if (tiles.length != VARIANTS) {
            throw new IllegalArgumentException("an auto-tile rule needs " + VARIANTS + " tiles but got " + tiles.length);
        }
        for (int t : tiles) {
            if (t < 1) {
                throw new IllegalArgumentException("auto-tile tile ids must be 1 or more: " + Arrays.toString(tiles));
            }
        }
        tiles = tiles.clone();
    }

    /** The tile for a neighbour mask (0 to 15). */
    public int tileFor(int mask) {
        return tiles[mask & (VARIANTS - 1)];
    }

    /** Whether {@code tileId} is one of this rule's tiles, so a cell holding it counts as a neighbour of the group. */
    public boolean contains(int tileId) {
        for (int t : tiles) {
            if (t == tileId) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int[] tiles() {
        return tiles.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AutoTileRule r && name.equals(r.name) && Arrays.equals(tiles, r.tiles);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + Arrays.hashCode(tiles);
    }

    @Override
    public String toString() {
        return "AutoTileRule[" + name + ", " + Arrays.toString(tiles) + "]";
    }
}
