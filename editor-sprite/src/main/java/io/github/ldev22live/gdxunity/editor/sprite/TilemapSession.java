package io.github.ldev22live.gdxunity.editor.sprite;

import io.github.ldev22live.gdxunity.editor.core.command.CommandHistory;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.sprite.AutoTileRule;
import io.github.ldev22live.gdxunity.engine.sprite.TileMapData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Editing of a {@link TileMapData} (TD-066, milestone E): the current tile, layer and tool, painting with optional auto-tile rule, and undo. Pointer
 * events come in cell coordinates ({@link #pointerDown} and friends); a stroke paints as the pointer moves and is one undo step when it ends. Rectangles
 * and fills edit once on release.
 */
public final class TilemapSession {
    /** The tile tools. */
    public enum Tool { PAINT, ERASE, FILL, RECT, PICK }

    private final TileMapData map;
    private final CommandHistory history = new CommandHistory();
    private Tool tool = Tool.PAINT;
    private int tile = 1;
    private int layer;
    private String autoRule;
    private boolean down;
    private int startCol;
    private int startRow;
    private int lastCol;
    private int lastRow;
    private int[] before;
    private List<int[]> preview;
    private long revision;

    public TilemapSession(TileMapData map) {
        this.map = Objects.requireNonNull(map, "map cannot be null");
        history.addListener(h -> {
            layer = Math.min(layer, map.getLayerCount() - 1);
            revision++;
        });
    }

    public TileMapData getMap() {
        return map;
    }

    public CommandHistory getHistory() {
        return history;
    }

    /** Changes whenever the cells, layers or rules changed (an edit, a stroke in progress, undo or redo), so the panel redraws. */
    public long getRevision() {
        return revision;
    }

    public Tool getTool() {
        return tool;
    }

    public void setTool(Tool tool) {
        if (!down) {
            this.tool = Objects.requireNonNull(tool, "tool cannot be null");
        }
    }

    public int getTile() {
        return tile;
    }

    /** The tile painted next; 1 or more. */
    public void setTile(int tile) {
        if (tile < 1) {
            throw new IllegalArgumentException("a tile id to paint is 1 or more: " + tile);
        }
        this.tile = tile;
        revision++;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        if (layer < 0 || layer >= map.getLayerCount()) {
            throw new IndexOutOfBoundsException("layer " + layer + " of " + map.getLayerCount());
        }
        this.layer = layer;
        revision++;
    }

    /** The name of the auto-tile rule painting uses, or null to paint the plain current tile. */
    public String getAutoRule() {
        return autoRule;
    }

    public void setAutoRule(String name) {
        if (name != null && map.getRule(name) == null) {
            throw new IllegalArgumentException("There is no auto-tile rule named \"" + name + "\"");
        }
        autoRule = name;
        revision++;
    }

    /** The cells ({col, row} each) of the rectangle being dragged, or null. */
    public List<int[]> getPreview() {
        return preview;
    }

    public boolean isDown() {
        return down;
    }

    /** The column of the last pointer position of the stroke in progress (or the last one). */
    public int getLastCol() {
        return lastCol;
    }

    /** The row of the last pointer position of the stroke in progress (or the last one). */
    public int getLastRow() {
        return lastRow;
    }

    // ---- one cell ----

    private void applyCell(int col, int row, boolean erase) {
        if (!map.contains(col, row)) {
            return;
        }
        AutoTileRule rule = autoRule == null ? null : map.getRule(autoRule);
        if (rule != null) {
            if (erase) {
                map.eraseAuto(layer, col, row, rule);
            } else {
                map.paintAuto(layer, col, row, rule);
            }
        } else {
            map.set(layer, col, row, erase ? TileMapData.EMPTY : tile);
        }
    }

    // ---- pointer ----

    public void pointerDown(int col, int row) {
        if (down) {
            return;
        }
        down = true;
        startCol = col;
        startRow = row;
        lastCol = col;
        lastRow = row;
        preview = null;
        before = map.copyCells(layer);
        switch (tool) {
            case PAINT -> applyCell(col, row, false);
            case ERASE -> applyCell(col, row, true);
            case PICK -> {
                int picked = map.get(layer, col, row);
                if (picked != TileMapData.EMPTY) {
                    tile = picked;
                }
            }
            case FILL -> fill(col, row);
            default -> { }
        }
        revision++;
    }

    public void pointerDrag(int col, int row) {
        if (!down || (col == lastCol && row == lastRow)) {
            return;
        }
        switch (tool) {
            case PAINT, ERASE -> {
                for (int[] p : PixelOps.line(lastCol, lastRow, col, row)) {
                    applyCell(p[0], p[1], tool == Tool.ERASE);
                }
            }
            case RECT -> preview = PixelOps.rect(startCol, startRow, col, row, true);
            default -> { }
        }
        lastCol = col;
        lastRow = row;
        revision++;
    }

    public void pointerUp(int col, int row) {
        if (!down) {
            return;
        }
        pointerDrag(col, row);
        if (tool == Tool.RECT) {
            for (int[] p : PixelOps.rect(startCol, startRow, col, row, true)) {
                applyCell(p[0], p[1], false);
            }
        }
        int[] after = map.copyCells(layer);
        String description = switch (tool) {
            case PAINT -> "Paint tiles";
            case ERASE -> "Erase tiles";
            case FILL -> "Fill tiles";
            case RECT -> "Tile rectangle";
            case PICK -> "Pick tile";
        };
        EditorCommand command = new SetCells(map, layer, before, after, description);
        if (((SetCells) command).changed() > 0) {
            history.execute(command);
        }
        down = false;
        preview = null;
        before = null;
        revision++;
    }

    /** Fills the connected cells that hold the same tile as (col, row) with the current tile (empty cells included). */
    private void fill(int col, int row) {
        if (!map.contains(col, row)) {
            return;
        }
        int target = map.get(layer, col, row);
        if (autoRule == null && target == tile) {
            return;
        }
        List<int[]> region = PixelOps.floodFill(map.copyCells(layer), map.getCols(), map.getRows(), col, row);
        for (int[] p : region) {
            applyCell(p[0], p[1], false);
        }
    }

    // ---- structure (one undo step each) ----

    /** Runs a change of layers, rules, solid tiles or size as one undo step. */
    public boolean edit(String description, Consumer<TileMapData> change) {
        boolean changed = history.execute(new Snapshot(map, description, change));
        layer = Math.min(layer, map.getLayerCount() - 1);
        revision++;
        return changed;
    }

    public void addLayer(String name) {
        edit("Add tile layer", m -> m.addLayer(name));
        layer = map.getLayerCount() - 1;
    }

    public void removeLayer() {
        int at = layer;
        edit("Remove tile layer", m -> m.removeLayer(at));
    }

    public void setLayerVisible(int index, boolean visible) {
        edit(visible ? "Show tile layer" : "Hide tile layer", m -> m.setLayerVisible(index, visible));
    }

    public void setSolid(int tileId, boolean solid) {
        edit("Tile collision", m -> m.setSolid(tileId, solid));
    }

    public void resize(int cols, int rows) {
        edit("Resize tile map", m -> m.resize(cols, rows));
    }

    public void putRule(AutoTileRule rule) {
        edit("Auto-tile rule", m -> m.putRule(rule));
    }

    /** Clears the current layer. */
    public void clearLayer() {
        int at = layer;
        edit("Clear tile layer", m -> m.setCells(at, new int[m.getCols() * m.getRows()]));
    }

    // ---- commands ----

    /** The cells of one layer changed from {@code before} to {@code after}; stores only the cells that differ. */
    static final class SetCells implements EditorCommand {
        private final TileMapData map;
        private final int layer;
        private final int[] indices;
        private final int[] oldValues;
        private final int[] newValues;
        private final String description;

        SetCells(TileMapData map, int layer, int[] before, int[] after, String description) {
            this.map = map;
            this.layer = layer;
            this.description = description;
            List<Integer> changed = new ArrayList<>();
            for (int i = 0; i < before.length; i++) {
                if (before[i] != after[i]) {
                    changed.add(i);
                }
            }
            indices = changed.stream().mapToInt(Integer::intValue).toArray();
            oldValues = new int[indices.length];
            newValues = new int[indices.length];
            for (int i = 0; i < indices.length; i++) {
                oldValues[i] = before[indices[i]];
                newValues[i] = after[indices[i]];
            }
        }

        int changed() {
            return indices.length;
        }

        private void apply(int[] values) {
            int[] cells = map.copyCells(layer);
            for (int i = 0; i < indices.length; i++) {
                cells[indices[i]] = values[i];
            }
            map.setCells(layer, cells);
        }

        @Override
        public boolean execute() {
            apply(newValues);
            return indices.length > 0;
        }

        @Override
        public boolean undo() {
            apply(oldValues);
            return true;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    /** A structural change, undone by restoring a whole copy of the map. */
    static final class Snapshot implements EditorCommand {
        private final TileMapData map;
        private final Consumer<TileMapData> change;
        private final String description;
        private TileMapData before;
        private TileMapData after;

        Snapshot(TileMapData map, String description, Consumer<TileMapData> change) {
            this.map = map;
            this.description = description;
            this.change = change;
        }

        @Override
        public boolean execute() {
            if (after != null) {
                map.restoreFrom(after);
                return true;
            }
            before = map.copy();
            change.accept(map);
            if (map.contentEquals(before)) {
                before = null;
                return false;
            }
            after = map.copy();
            return true;
        }

        @Override
        public boolean undo() {
            map.restoreFrom(before);
            return true;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }
}
