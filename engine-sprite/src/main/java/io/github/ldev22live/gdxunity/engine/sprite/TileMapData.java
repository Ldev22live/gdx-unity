package io.github.ldev22live.gdxunity.engine.sprite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The cells of a tile map (TD-066, milestone E): layers of tile ids on a grid, which tiles are solid, and auto-tile rules. A tile id is the index of a frame
 * of the tile set (a {@code .sprite}) plus one; 0 is an empty cell. Row 0 is the top row, as in an image. Headless and free of drawing, so editing and
 * collision are tested without a GL context.
 */
public final class TileMapData {
    /** Largest number of cells along a side. */
    public static final int MAX_CELLS = 1024;
    /** An empty cell. */
    public static final int EMPTY = 0;

    /** One layer of cells. */
    public static final class Layer {
        private final String name;
        private boolean visible;
        private final int[] cells;

        Layer(String name, boolean visible, int[] cells) {
            this.name = name;
            this.visible = visible;
            this.cells = cells;
        }

        public String getName() {
            return name;
        }

        public boolean isVisible() {
            return visible;
        }
    }

    /** One box of solid cells, in cell units: {@code col} and {@code row} of its top left cell, {@code cols} and {@code rows} across. */
    public record CellBox(int col, int row, int cols, int rows) { }

    private int cols;
    private int rows;
    private final List<Layer> layers = new ArrayList<>();
    private final Set<Integer> solid = new TreeSet<>();
    private final List<AutoTileRule> rules = new ArrayList<>();

    public TileMapData(int cols, int rows) {
        checkSize(cols, rows);
        this.cols = cols;
        this.rows = rows;
        layers.add(new Layer("Ground", true, new int[cols * rows]));
    }

    private static void checkSize(int cols, int rows) {
        if (cols < 1 || rows < 1 || cols > MAX_CELLS || rows > MAX_CELLS) {
            throw new IllegalArgumentException("a tile map is 1.." + MAX_CELLS + " cells on a side but was " + cols + "x" + rows);
        }
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    public int getLayerCount() {
        return layers.size();
    }

    public Layer getLayer(int index) {
        checkLayer(index);
        return layers.get(index);
    }

    private void checkLayer(int index) {
        if (index < 0 || index >= layers.size()) {
            throw new IndexOutOfBoundsException("layer " + index + " of " + layers.size());
        }
    }

    /** Adds an empty layer on top; returns its index. */
    public int addLayer(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        layers.add(new Layer(name, true, new int[cols * rows]));
        return layers.size() - 1;
    }

    /** Removes a layer; the last one cannot be removed. */
    public void removeLayer(int index) {
        checkLayer(index);
        if (layers.size() == 1) {
            throw new IllegalStateException("a tile map needs at least one layer");
        }
        layers.remove(index);
    }

    public void setLayerVisible(int index, boolean visible) {
        checkLayer(index);
        layers.get(index).visible = visible;
    }

    /** Whether (col, row) lies on the map. */
    public boolean contains(int col, int row) {
        return col >= 0 && row >= 0 && col < cols && row < rows;
    }

    /** The tile id at a cell of a layer; {@link #EMPTY} outside the map. */
    public int get(int layer, int col, int row) {
        checkLayer(layer);
        return contains(col, row) ? layers.get(layer).cells[row * cols + col] : EMPTY;
    }

    /** Sets a cell; throws outside the map. */
    public void set(int layer, int col, int row, int tile) {
        checkLayer(layer);
        if (!contains(col, row)) {
            throw new IndexOutOfBoundsException("cell " + col + "," + row + " outside " + cols + "x" + rows);
        }
        if (tile < 0) {
            throw new IllegalArgumentException("a tile id cannot be negative: " + tile);
        }
        layers.get(layer).cells[row * cols + col] = tile;
    }

    /** A copy of one layer's cells (row by row). */
    public int[] copyCells(int layer) {
        checkLayer(layer);
        return layers.get(layer).cells.clone();
    }

    /** Replaces one layer's cells by a copy of {@code cells} (length cols * rows). */
    public void setCells(int layer, int[] cells) {
        checkLayer(layer);
        Objects.requireNonNull(cells, "cells cannot be null");
        if (cells.length != cols * rows) {
            throw new IllegalArgumentException("a layer needs " + cols * rows + " cells but got " + cells.length);
        }
        System.arraycopy(cells, 0, layers.get(layer).cells, 0, cells.length);
    }

    /** Changes the number of cells; content stays at the top left, cut or padded with empty cells. */
    public void resize(int newCols, int newRows) {
        checkSize(newCols, newRows);
        for (int l = 0; l < layers.size(); l++) {
            Layer old = layers.get(l);
            int[] next = new int[newCols * newRows];
            for (int r = 0; r < Math.min(rows, newRows); r++) {
                System.arraycopy(old.cells, r * cols, next, r * newCols, Math.min(cols, newCols));
            }
            layers.set(l, new Layer(old.name, old.visible, next));
        }
        cols = newCols;
        rows = newRows;
    }

    // ---- solid tiles ----

    /** The tile ids that block movement, ascending. */
    public int[] getSolidTiles() {
        return solid.stream().mapToInt(Integer::intValue).toArray();
    }

    public boolean isSolid(int tile) {
        return solid.contains(tile);
    }

    public void setSolid(int tile, boolean isSolid) {
        if (tile < 1) {
            throw new IllegalArgumentException("only tile ids from 1 can be solid: " + tile);
        }
        if (isSolid) {
            solid.add(tile);
        } else {
            solid.remove(tile);
        }
    }

    /**
     * The solid cells of all visible layers merged into as few boxes as possible: runs along a row, joined with the runs of the same width below.
     * Cells with a solid tile on any visible layer count as solid.
     */
    public List<CellBox> solidBoxes() {
        boolean[] blocked = new boolean[cols * rows];
        for (Layer layer : layers) {
            if (!layer.visible) {
                continue;
            }
            for (int i = 0; i < blocked.length; i++) {
                if (solid.contains(layer.cells[i])) {
                    blocked[i] = true;
                }
            }
        }
        List<CellBox> boxes = new ArrayList<>();
        boolean[] used = new boolean[blocked.length];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!blocked[r * cols + c] || used[r * cols + c]) {
                    continue;
                }
                int width = 1;
                while (c + width < cols && blocked[r * cols + c + width] && !used[r * cols + c + width]) {
                    width++;
                }
                int height = 1;
                while (r + height < rows && rowIsBlocked(blocked, used, r + height, c, width)) {
                    height++;
                }
                for (int rr = r; rr < r + height; rr++) {
                    for (int cc = c; cc < c + width; cc++) {
                        used[rr * cols + cc] = true;
                    }
                }
                boxes.add(new CellBox(c, r, width, height));
            }
        }
        return boxes;
    }

    private boolean rowIsBlocked(boolean[] blocked, boolean[] used, int row, int col, int width) {
        for (int c = col; c < col + width; c++) {
            if (!blocked[row * cols + c] || used[row * cols + c]) {
                return false;
            }
        }
        return true;
    }

    // ---- auto-tile rules ----

    public List<AutoTileRule> getRules() {
        return List.copyOf(rules);
    }

    /** Adds a rule, or replaces the rule of the same name. */
    public void putRule(AutoTileRule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        rules.removeIf(r -> r.name().equals(rule.name()));
        rules.add(rule);
    }

    public void removeRule(String name) {
        rules.removeIf(r -> r.name().equals(name));
    }

    /** The rule with this name, or null. */
    public AutoTileRule getRule(String name) {
        for (AutoTileRule r : rules) {
            if (r.name().equals(name)) {
                return r;
            }
        }
        return null;
    }

    private int maskAt(int layer, int col, int row, AutoTileRule rule) {
        int mask = 0;
        if (rule.contains(get(layer, col, row - 1))) {
            mask |= AutoTileRule.NORTH;
        }
        if (rule.contains(get(layer, col + 1, row))) {
            mask |= AutoTileRule.EAST;
        }
        if (rule.contains(get(layer, col, row + 1))) {
            mask |= AutoTileRule.SOUTH;
        }
        if (rule.contains(get(layer, col - 1, row))) {
            mask |= AutoTileRule.WEST;
        }
        return mask;
    }

    private void refit(int layer, int col, int row, AutoTileRule rule) {
        if (contains(col, row) && rule.contains(get(layer, col, row))) {
            set(layer, col, row, rule.tileFor(maskAt(layer, col, row, rule)));
        }
    }

    /** Paints a cell with the rule and refits it and its four neighbours that belong to the rule, so the edges join up. */
    public void paintAuto(int layer, int col, int row, AutoTileRule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        set(layer, col, row, rule.tileFor(0));
        refitAround(layer, col, row, rule);
    }

    /** Empties a cell and refits its neighbours that belong to the rule. */
    public void eraseAuto(int layer, int col, int row, AutoTileRule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        set(layer, col, row, EMPTY);
        refitAround(layer, col, row, rule);
    }

    private void refitAround(int layer, int col, int row, AutoTileRule rule) {
        refit(layer, col, row, rule);
        refit(layer, col, row - 1, rule);
        refit(layer, col + 1, row, rule);
        refit(layer, col, row + 1, rule);
        refit(layer, col - 1, row, rule);
    }

    // ---- copying ----

    /** A deep copy; the editor uses it as an undo snapshot. */
    public TileMapData copy() {
        TileMapData c = new TileMapData(cols, rows);
        c.restoreFrom(this);
        return c;
    }

    /** Makes this map equal to {@code other} (deep copy). */
    public void restoreFrom(TileMapData other) {
        Objects.requireNonNull(other, "other cannot be null");
        if (other == this) {
            return;
        }
        cols = other.cols;
        rows = other.rows;
        layers.clear();
        for (Layer l : other.layers) {
            layers.add(new Layer(l.name, l.visible, l.cells.clone()));
        }
        solid.clear();
        solid.addAll(other.solid);
        rules.clear();
        rules.addAll(other.rules);
    }

    /** Whether both maps hold the same size, layers, cells, solid tiles and rules. */
    public boolean contentEquals(TileMapData other) {
        if (cols != other.cols || rows != other.rows || layers.size() != other.layers.size() || !solid.equals(other.solid) || !rules.equals(other.rules)) {
            return false;
        }
        for (int i = 0; i < layers.size(); i++) {
            Layer a = layers.get(i);
            Layer b = other.layers.get(i);
            if (!a.name.equals(b.name) || a.visible != b.visible || !Arrays.equals(a.cells, b.cells)) {
                return false;
            }
        }
        return true;
    }

    /** Rebuilds a layer list from stored values (used by the scene file reader). */
    public void loadLayers(List<String> names, List<Boolean> visible, List<int[]> cellsPerLayer) {
        if (names.isEmpty() || names.size() != visible.size() || names.size() != cellsPerLayer.size()) {
            throw new IllegalArgumentException("a tile map needs at least one layer, with a name, visibility and cells each");
        }
        List<Layer> next = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            if (cellsPerLayer.get(i).length != cols * rows) {
                throw new IllegalArgumentException("layer " + names.get(i) + " has " + cellsPerLayer.get(i).length + " cells but the map has " + cols * rows);
            }
            next.add(new Layer(names.get(i), visible.get(i), cellsPerLayer.get(i).clone()));
        }
        layers.clear();
        layers.addAll(next);
    }
}
