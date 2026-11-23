package io.github.ldev22live.gdxunity.engine.sprite;

import com.badlogic.gdx.utils.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Objects;

/** Sprite-sheet export (all frames flattened into one PNG plus a JSON atlas) and import (a PNG cut into equal cells). */
public final class SpriteSheet {
    private SpriteSheet() { }

    /** An exported sheet: the PNG and the atlas JSON that describes it. */
    public record Export(byte[] png, String atlasJson, int width, int height) { }

    /**
     * Packs the flattened frames in a grid, left to right and top to bottom.
     *
     * @param columns frames per row; 0 puts all frames in one row
     * @param padding transparent pixels between cells (not at the outer edge)
     * @param imageName file name written into the atlas
     */
    public static Export export(SpriteDocument doc, int columns, int padding, String imageName) throws IOException {
        Objects.requireNonNull(doc, "doc cannot be null");
        Objects.requireNonNull(imageName, "imageName cannot be null");
        if (columns < 0 || padding < 0) {
            throw new IllegalArgumentException("columns and padding cannot be negative: " + columns + ", " + padding);
        }
        int frames = doc.getFrameCount();
        int cols = columns == 0 ? frames : Math.min(columns, frames);
        int rows = (frames + cols - 1) / cols;
        int w = doc.getWidth();
        int h = doc.getHeight();
        int sheetW = cols * w + (cols - 1) * padding;
        int sheetH = rows * h + (rows - 1) * padding;
        if (sheetW > SpriteDocument.MAX_SIZE * 4 || sheetH > SpriteDocument.MAX_SIZE * 4) {
            throw new IllegalArgumentException("the sheet would be " + sheetW + "x" + sheetH + "; use more columns");
        }
        int[] sheet = new int[sheetW * sheetH];
        StringWriter text = new StringWriter();
        JsonWriter json = new JsonWriter(text);
        json.setOutputType(JsonWriter.OutputType.json);
        json.object().set("image", imageName).object("size").set("w", sheetW).set("h", sheetH).pop();
        json.array("frames");
        for (int f = 0; f < frames; f++) {
            int ox = (f % cols) * (w + padding);
            int oy = (f / cols) * (h + padding);
            int[] flat = doc.flatten(f);
            for (int y = 0; y < h; y++) {
                System.arraycopy(flat, y * w, sheet, (oy + y) * sheetW + ox, w);
            }
            json.object().set("index", f).set("x", ox).set("y", oy).set("w", w).set("h", h).set("duration", doc.getFrameDuration(f)).pop();
        }
        json.pop();
        json.array("tags");
        for (SpriteTag t : doc.getTags()) {
            json.object().set("name", t.name()).set("from", t.from()).set("to", t.to()).set("direction", t.direction().name()).pop();
        }
        json.pop();
        json.array("slices");
        for (SpriteSlice s : doc.getSlices()) {
            json.object().set("name", s.name()).set("x", s.x()).set("y", s.y()).set("w", s.width()).set("h", s.height())
                .set("left", s.left()).set("right", s.right()).set("top", s.top()).set("bottom", s.bottom())
                .set("pivotX", s.pivotX()).set("pivotY", s.pivotY()).pop();
        }
        json.pop();
        json.pop();
        json.close();
        return new Export(PixelPng.encode(sheetW, sheetH, sheet), text.toString(), sheetW, sheetH);
    }

    /**
     * Cuts an image into cells of {@code cellWidth} x {@code cellHeight} (left to right, top to bottom) and makes each cell a frame of one layer.
     * Cells that are completely transparent at the end of the sheet are dropped (at least one frame stays).
     */
    public static SpriteDocument importSheet(PixelPng.Image image, int cellWidth, int cellHeight, int padding) {
        Objects.requireNonNull(image, "image cannot be null");
        if (cellWidth < 1 || cellHeight < 1 || padding < 0) {
            throw new IllegalArgumentException("cell size must be positive and padding not negative: " + cellWidth + "x" + cellHeight + ", " + padding);
        }
        if (cellWidth > image.width() || cellHeight > image.height()) {
            throw new IllegalArgumentException("a " + cellWidth + "x" + cellHeight + " cell does not fit a " + image.width() + "x" + image.height() + " image");
        }
        int cols = (image.width() + padding) / (cellWidth + padding);
        int rows = (image.height() + padding) / (cellHeight + padding);
        int cells = cols * rows;
        int[][] cut = new int[cells][];
        int used = 0;
        for (int i = 0; i < cells; i++) {
            int ox = (i % cols) * (cellWidth + padding);
            int oy = (i / cols) * (cellHeight + padding);
            int[] pixels = new int[cellWidth * cellHeight];
            boolean any = false;
            for (int y = 0; y < cellHeight; y++) {
                System.arraycopy(image.pixels(), (oy + y) * image.width() + ox, pixels, y * cellWidth, cellWidth);
            }
            for (int p : pixels) {
                if ((p & 0xFF) != 0) {
                    any = true;
                    break;
                }
            }
            cut[i] = pixels;
            if (any) {
                used = i + 1;
            }
        }
        int frames = Math.max(1, used);
        SpriteDocument doc = new SpriteDocument(cellWidth, cellHeight);
        for (int f = 1; f < frames; f++) {
            doc.insertFrame(doc.getFrameCount(), -1);
        }
        for (int f = 0; f < frames; f++) {
            doc.setCel(0, f, cut[f]);
        }
        return doc;
    }
}
