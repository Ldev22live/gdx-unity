package io.github.ldev22live.gdxunity.editor.sprite;

import io.github.ldev22live.gdxunity.editor.core.command.CommandHistory;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteFile;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteHitbox;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteLayer;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteLayerSource;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteSlice;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteTag;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * One open sprite: the document, its undo history, the current layer, frame and color, and the drawing tools (TD-066, milestone B).
 * Every edit is one {@link CommandHistory} step. Drawing happens on the current layer and frame of the active cel, with optional mirroring about the
 * canvas center, wrap-around (tile mode) and a square brush. Not thread safe; the panel calls it from the UI thread.
 */
public final class SpriteSession {
    /** Largest brush side. */
    public static final int MAX_BRUSH = 64;
    /** Opacity of the previous-frame tint of the onion skin (0..255). */
    private static final int ONION_ALPHA = 90;
    private static final int ONION_PREVIOUS = 0xFF3030FF;
    private static final int ONION_NEXT = 0x3060FFFF;

    private final SpriteDocument doc;
    private final CommandHistory history = new CommandHistory();
    private File file;
    private boolean dirty;
    private int layer;
    private int frame;
    private int color = 0x000000FF;
    private int brush = 1;
    private boolean mirrorX;
    private boolean mirrorY;
    private boolean tileMode;
    private boolean pixelPerfect = true;
    private boolean onionSkin;
    private SelectionRect selection;
    private long revision;

    /** An inclusive-exclusive rectangle: x, y, width, height. */
    public record SelectionRect(int x, int y, int width, int height) { }

    public SpriteSession(SpriteDocument doc) {
        this.doc = Objects.requireNonNull(doc, "doc cannot be null");
        // Undo and redo can remove the current layer or frame; keep the position valid and mark the sprite changed
        history.addListener(h -> {
            layer = Math.min(layer, doc.getLayerCount() - 1);
            frame = Math.min(frame, doc.getFrameCount() - 1);
            revision++;
            dirty = true;
        });
    }

    /** Opens a {@code .sprite} file. */
    public static SpriteSession open(File file) throws IOException {
        SpriteSession s = new SpriteSession(SpriteFile.read(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
        s.file = file;
        return s;
    }

    /** Writes the document to {@code target} (and remembers it as the session's file). */
    public void saveAs(File target) throws IOException {
        Objects.requireNonNull(target, "target cannot be null");
        Files.writeString(target.toPath(), SpriteFile.write(doc), StandardCharsets.UTF_8);
        file = target;
        dirty = false;
    }

    public SpriteDocument getDocument() {
        return doc;
    }

    public CommandHistory getHistory() {
        return history;
    }

    public File getFile() {
        return file;
    }

    /** Whether there are edits since the last save. */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * A number that changes whenever what the canvas shows may have changed: a pixel or structure edit, undo or redo, a new current layer or frame,
     * or the onion skin switch. The panel redraws and rebuilds its lists when it changes.
     */
    public long getRevision() {
        return revision;
    }

    // ---- state ----

    public int getLayer() {
        return layer;
    }

    public int getFrame() {
        return frame;
    }

    public void setLayer(int layer) {
        if (layer < 0 || layer >= doc.getLayerCount()) {
            throw new IndexOutOfBoundsException("layer " + layer + " of " + doc.getLayerCount());
        }
        this.layer = layer;
        revision++;
    }

    public void setFrame(int frame) {
        if (frame < 0 || frame >= doc.getFrameCount()) {
            throw new IndexOutOfBoundsException("frame " + frame + " of " + doc.getFrameCount());
        }
        this.frame = frame;
        revision++;
    }

    public int getColor() {
        return color;
    }

    /** The drawing color, {@code 0xRRGGBBAA}. */
    public void setColor(int rgba) {
        color = rgba;
    }

    public int getBrush() {
        return brush;
    }

    public void setBrush(int size) {
        if (size < 1 || size > MAX_BRUSH) {
            throw new IllegalArgumentException("brush size must be 1.." + MAX_BRUSH + " but was " + size);
        }
        brush = size;
    }

    public boolean isMirrorX() {
        return mirrorX;
    }

    public void setMirrorX(boolean on) {
        mirrorX = on;
    }

    public boolean isMirrorY() {
        return mirrorY;
    }

    public void setMirrorY(boolean on) {
        mirrorY = on;
    }

    public boolean isTileMode() {
        return tileMode;
    }

    /** In tile mode, pixels past an edge reappear on the opposite edge instead of being cut. */
    public void setTileMode(boolean on) {
        tileMode = on;
    }

    public boolean isPixelPerfect() {
        return pixelPerfect;
    }

    public void setPixelPerfect(boolean on) {
        pixelPerfect = on;
    }

    public boolean isOnionSkin() {
        return onionSkin;
    }

    public void setOnionSkin(boolean on) {
        onionSkin = on;
        revision++;
    }

    // ---- drawing ----

    /** Brush, mirror and wrap/clip applied to raw tool points; the result has each canvas pixel once, in order. */
    public List<int[]> expand(List<int[]> points) {
        int w = doc.getWidth();
        int h = doc.getHeight();
        boolean[] seen = new boolean[w * h];
        List<int[]> out = new ArrayList<>();
        for (int[] p : points) {
            List<int[]> sources = new ArrayList<>();
            sources.add(p);
            if (mirrorX) {
                sources.add(new int[] {w - 1 - p[0], p[1]});
            }
            if (mirrorY) {
                sources.add(new int[] {p[0], h - 1 - p[1]});
            }
            if (mirrorX && mirrorY) {
                sources.add(new int[] {w - 1 - p[0], h - 1 - p[1]});
            }
            for (int[] s : sources) {
                int start = -(brush - 1) / 2;
                for (int dy = 0; dy < brush; dy++) {
                    for (int dx = 0; dx < brush; dx++) {
                        int x = s[0] + start + dx;
                        int y = s[1] + start + dy;
                        if (tileMode) {
                            x = Math.floorMod(x, w);
                            y = Math.floorMod(y, h);
                        } else if (x < 0 || y < 0 || x >= w || y >= h) {
                            continue;
                        }
                        if (!seen[y * w + x]) {
                            seen[y * w + x] = true;
                            out.add(new int[] {x, y});
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Paints {@code points} with {@code rgba} as one undo step; returns how many pixels changed. */
    public int paint(List<int[]> points, int rgba, String description) {
        int[] before = doc.copyCel(layer, frame);
        int[] after = before.clone();
        for (int[] p : expand(points)) {
            after[p[1] * doc.getWidth() + p[0]] = rgba;
        }
        return run(new SpriteCommands.SetPixels(doc, layer, frame, before, after, description));
    }

    private int run(SpriteCommands.SetPixels command) {
        int changed = command.changedPixels();
        if (changed > 0 && history.execute(command)) {
            dirty = true;
            revision++;
            return changed;
        }
        return 0;
    }

    /** A freehand stroke (pencil, or eraser when {@code erase}); points are added as the pointer moves, one undo step on {@link #commit()}. */
    public final class Stroke {
        private final boolean erase;
        private final List<int[]> points = new ArrayList<>();
        private boolean done;

        private Stroke(boolean erase) {
            this.erase = erase;
        }

        /** Adds the pointer position; the gap to the previous position is filled with a line. */
        public void add(int x, int y) {
            if (done) {
                throw new IllegalStateException("the stroke is finished");
            }
            if (points.isEmpty()) {
                points.add(new int[] {x, y});
                return;
            }
            int[] last = points.get(points.size() - 1);
            List<int[]> segment = PixelOps.line(last[0], last[1], x, y);
            points.addAll(segment.subList(1, segment.size()));
        }

        /** Ends the stroke and applies it as one undo step; returns the number of pixels changed. */
        public int commit() {
            if (done) {
                throw new IllegalStateException("the stroke is finished");
            }
            done = true;
            List<int[]> use = pixelPerfect && brush == 1 && !mirrorX && !mirrorY ? PixelOps.pixelPerfect(points) : points;
            return paint(use, erase ? SpriteDocument.TRANSPARENT : color, erase ? "Erase" : "Pencil");
        }
    }

    /** Starts a pencil stroke in the current color. */
    public Stroke beginPencil() {
        return new Stroke(false);
    }

    /** Starts an eraser stroke. */
    public Stroke beginEraser() {
        return new Stroke(true);
    }

    public int drawLine(int x0, int y0, int x1, int y1) {
        return paint(PixelOps.line(x0, y0, x1, y1), color, "Line");
    }

    public int drawRect(int x0, int y0, int x1, int y1, boolean filled) {
        return paint(PixelOps.rect(x0, y0, x1, y1, filled), color, "Rectangle");
    }

    public int drawEllipse(int x0, int y0, int x1, int y1, boolean filled) {
        return paint(PixelOps.ellipse(x0, y0, x1, y1, filled), color, "Ellipse");
    }

    /** Fills the connected region of identical pixels at (x, y) of the current cel with the current color (brush and mirror are not used). */
    public int fill(int x, int y) {
        int[] before = doc.copyCel(layer, frame);
        int[] after = before.clone();
        for (int[] p : PixelOps.floodFill(before, doc.getWidth(), doc.getHeight(), x, y)) {
            after[p[1] * doc.getWidth() + p[0]] = color;
        }
        return run(new SpriteCommands.SetPixels(doc, layer, frame, before, after, "Fill"));
    }

    /** The color of the current cel at (x, y); transparent outside the canvas. Does not change the drawing color. */
    public int pick(int x, int y) {
        if (x < 0 || y < 0 || x >= doc.getWidth() || y >= doc.getHeight()) {
            return SpriteDocument.TRANSPARENT;
        }
        return doc.getPixel(layer, frame, x, y);
    }

    // ---- selection ----

    public SelectionRect getSelection() {
        return selection;
    }

    /** Selects a rectangle (clipped to the canvas); an empty result clears the selection. */
    public void select(int x0, int y0, int x1, int y1) {
        int left = Math.max(0, Math.min(x0, x1));
        int top = Math.max(0, Math.min(y0, y1));
        int right = Math.min(doc.getWidth() - 1, Math.max(x0, x1));
        int bottom = Math.min(doc.getHeight() - 1, Math.max(y0, y1));
        selection = right < left || bottom < top ? null : new SelectionRect(left, top, right - left + 1, bottom - top + 1);
    }

    public void clearSelection() {
        selection = null;
    }

    private SelectionRect requireSelection() {
        if (selection == null) {
            throw new IllegalStateException("nothing is selected");
        }
        return selection;
    }

    /** Clears the selected pixels. */
    public int deleteSelection() {
        SelectionRect s = requireSelection();
        int[] before = doc.copyCel(layer, frame);
        int[] after = before.clone();
        for (int y = s.y(); y < s.y() + s.height(); y++) {
            for (int x = s.x(); x < s.x() + s.width(); x++) {
                after[y * doc.getWidth() + x] = SpriteDocument.TRANSPARENT;
            }
        }
        return run(new SpriteCommands.SetPixels(doc, layer, frame, before, after, "Delete selection"));
    }

    /** Moves the selected pixels by (dx, dy), clipping what leaves the canvas; the selection follows. */
    public int moveSelection(int dx, int dy) {
        SelectionRect s = requireSelection();
        int w = doc.getWidth();
        int[] before = doc.copyCel(layer, frame);
        int[] after = before.clone();
        for (int y = s.y(); y < s.y() + s.height(); y++) {
            for (int x = s.x(); x < s.x() + s.width(); x++) {
                after[y * w + x] = SpriteDocument.TRANSPARENT;
            }
        }
        for (int y = s.y(); y < s.y() + s.height(); y++) {
            for (int x = s.x(); x < s.x() + s.width(); x++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < doc.getHeight()) {
                    after[ny * w + nx] = before[y * w + x];
                }
            }
        }
        int changed = run(new SpriteCommands.SetPixels(doc, layer, frame, before, after, "Move selection"));
        select(s.x() + dx, s.y() + dy, s.x() + dx + s.width() - 1, s.y() + dy + s.height() - 1);
        return changed;
    }

    /** Mirrors the selected pixels inside the selection. */
    public int flipSelection(boolean horizontal) {
        SelectionRect s = requireSelection();
        int w = doc.getWidth();
        int[] before = doc.copyCel(layer, frame);
        int[] after = before.clone();
        for (int y = 0; y < s.height(); y++) {
            for (int x = 0; x < s.width(); x++) {
                int sx = horizontal ? s.width() - 1 - x : x;
                int sy = horizontal ? y : s.height() - 1 - y;
                after[(s.y() + y) * w + s.x() + x] = before[(s.y() + sy) * w + s.x() + sx];
            }
        }
        return run(new SpriteCommands.SetPixels(doc, layer, frame, before, after, horizontal ? "Flip horizontally" : "Flip vertically"));
    }

    /** Rotates the selected pixels a quarter turn clockwise; only a square selection can turn in place. */
    public int rotateSelection() {
        SelectionRect s = requireSelection();
        if (s.width() != s.height()) {
            throw new IllegalStateException("only a square selection can be rotated, but it is " + s.width() + "x" + s.height());
        }
        int w = doc.getWidth();
        int n = s.width();
        int[] before = doc.copyCel(layer, frame);
        int[] after = before.clone();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                after[(s.y() + x) * w + s.x() + (n - 1 - y)] = before[(s.y() + y) * w + s.x() + x];
            }
        }
        return run(new SpriteCommands.SetPixels(doc, layer, frame, before, after, "Rotate selection"));
    }

    /**
     * Scales and rotates the selected pixels about the selection's centre (TD-104), nearest neighbour, as one undo step: pixel art
     * keeps hard edges and only existing colours. What lands outside the canvas is clipped; transparent pixels of the selection do
     * not cover what is below. The selection becomes the transformed pixels' bounds.
     *
     * @param scaleX  horizontal scale, not 0 (negative mirrors)
     * @param scaleY  vertical scale, not 0
     * @param degrees rotation, clockwise on screen
     * @return how many pixels changed
     */
    public int transformSelection(float scaleX, float scaleY, float degrees) {
        SelectionRect s = requireSelection();
        if (scaleX == 0f || scaleY == 0f || !Float.isFinite(scaleX) || !Float.isFinite(scaleY) || !Float.isFinite(degrees)) {
            throw new IllegalArgumentException("scales must be finite and not 0, and the angle finite: " + scaleX + ", " + scaleY + ", "
                + degrees);
        }
        int w = doc.getWidth();
        int h = doc.getHeight();
        double cx = s.x() + s.width() / 2.0;
        double cy = s.y() + s.height() / 2.0;
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        // Bounds of the transformed selection
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] corner : new double[][] {{s.x(), s.y()}, {s.x() + s.width(), s.y()}, {s.x(), s.y() + s.height()},
            {s.x() + s.width(), s.y() + s.height()}}) {
            double dx = (corner[0] - cx) * scaleX;
            double dy = (corner[1] - cy) * scaleY;
            double x = cx + dx * cos - dy * sin;
            double y = cy + dx * sin + dy * cos;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        int[] before = doc.copyCel(layer, frame);
        int[] after = before.clone();
        for (int y = s.y(); y < s.y() + s.height(); y++) {
            for (int x = s.x(); x < s.x() + s.width(); x++) {
                after[y * w + x] = SpriteDocument.TRANSPARENT;
            }
        }
        int left = Math.max(0, (int) Math.floor(minX));
        int top = Math.max(0, (int) Math.floor(minY));
        int right = Math.min(w - 1, (int) Math.ceil(maxX) - 1);
        int bottom = Math.min(h - 1, (int) Math.ceil(maxY) - 1);
        int usedLeft = Integer.MAX_VALUE;
        int usedTop = Integer.MAX_VALUE;
        int usedRight = -1;
        int usedBottom = -1;
        for (int py = top; py <= bottom; py++) {
            for (int px = left; px <= right; px++) {
                // Inverse: undo the rotation, then the scale, from the destination pixel's centre
                double dx = px + 0.5 - cx;
                double dy = py + 0.5 - cy;
                double rx = (dx * cos + dy * sin) / scaleX;
                double ry = (-dx * sin + dy * cos) / scaleY;
                int sx = (int) Math.floor(cx + rx);
                int sy = (int) Math.floor(cy + ry);
                if (sx < s.x() || sy < s.y() || sx >= s.x() + s.width() || sy >= s.y() + s.height()) {
                    continue;
                }
                int pixel = before[sy * w + sx];
                if ((pixel & 0xFF) != 0) {
                    after[py * w + px] = pixel;
                    usedLeft = Math.min(usedLeft, px);
                    usedTop = Math.min(usedTop, py);
                    usedRight = Math.max(usedRight, px);
                    usedBottom = Math.max(usedBottom, py);
                }
            }
        }
        int changed = run(new SpriteCommands.SetPixels(doc, layer, frame, before, after, "Transform selection"));
        if (usedRight >= 0) {
            select(usedLeft, usedTop, usedRight, usedBottom);
        } else {
            clearSelection();
        }
        return changed;
    }

    // ---- hitboxes and layer sources (TD-104) ----

    /** Adds a hitbox to the current frame, or replaces the one of the same name, as one undo step. */
    public boolean setHitbox(SpriteHitbox box) {
        Objects.requireNonNull(box, "box cannot be null");
        int target = frame;
        return edit("Hitbox " + box.name(), d -> {
            List<SpriteHitbox> boxes = new ArrayList<>(d.getHitboxes(target));
            boxes.removeIf(b -> b.name().equals(box.name()));
            boxes.add(box);
            d.setHitboxes(target, boxes);
        });
    }

    /** Removes the current frame's hitbox called {@code name}; false when it has none. */
    public boolean removeHitbox(String name) {
        int target = frame;
        if (doc.getHitboxes(target).stream().noneMatch(b -> b.name().equals(name))) {
            return false;
        }
        return edit("Remove hitbox " + name, d -> {
            List<SpriteHitbox> boxes = new ArrayList<>(d.getHitboxes(target));
            boxes.removeIf(b -> b.name().equals(name));
            d.setHitboxes(target, boxes);
        });
    }

    /** Links a layer to the vector drawing it is drawn from, or unlinks it with null, as one undo step. */
    public boolean setLayerSource(int index, SpriteLayerSource source) {
        return edit(source == null ? "Unlink layer" : "Link layer to " + source.vector(), d -> d.setLayerSource(index, source));
    }

    // ---- structure ----

    /** Runs a structural change as one undo step; returns whether anything changed (false too when the change was refused, such as removing the last layer). */
    public boolean edit(String description, Consumer<SpriteDocument> change) {
        boolean changed = history.execute(new SpriteCommands.Snapshot(doc, description, change));
        if (changed) {
            dirty = true;
            layer = Math.min(layer, doc.getLayerCount() - 1);
            frame = Math.min(frame, doc.getFrameCount() - 1);
        }
        return changed;
    }

    /** Adds a layer above the current one and makes it current. */
    public void addLayer(String name) {
        int at = layer + 1;
        edit("Add layer", d -> d.insertLayer(at, name));
        layer = at;
    }

    /** Removes the current layer. */
    public void removeLayer() {
        int at = layer;
        edit("Remove layer", d -> d.removeLayer(at));
    }

    public void setLayerVisible(int index, boolean visible) {
        edit(visible ? "Show layer" : "Hide layer", d -> d.setLayer(index, d.getLayer(index).withVisible(visible)));
    }

    public void setLayerOpacity(int index, int opacity) {
        edit("Layer opacity", d -> d.setLayer(index, d.getLayer(index).withOpacity(opacity)));
    }

    public void renameLayer(int index, String name) {
        edit("Rename layer", d -> d.setLayer(index, d.getLayer(index).withName(name)));
    }

    public void moveLayer(int from, int to) {
        edit("Move layer", d -> d.moveLayer(from, to));
        layer = to;
    }

    /** Adds an empty frame after the current one and makes it current. */
    public void addFrame() {
        int at = frame + 1;
        edit("Add frame", d -> d.insertFrame(at, -1));
        frame = at;
    }

    /** Copies the current frame after itself and makes the copy current. */
    public void duplicateFrame() {
        int source = frame;
        edit("Duplicate frame", d -> d.insertFrame(source + 1, source));
        frame = source + 1;
    }

    public void removeFrame() {
        int at = frame;
        edit("Remove frame", d -> d.removeFrame(at));
    }

    public void moveFrame(int from, int to) {
        edit("Move frame", d -> d.moveFrame(from, to));
        frame = to;
    }

    public void setFrameDuration(int index, int millis) {
        edit("Frame duration", d -> d.setFrameDuration(index, millis));
    }

    /** Adds a tag over frames {@code from..to}. */
    public void addTag(String name, int from, int to, SpriteTag.Direction direction) {
        SpriteTag tag = new SpriteTag(name, from, to, direction);
        edit("Add tag", d -> {
            List<SpriteTag> tags = new ArrayList<>(d.getTags());
            tags.add(tag);
            d.setTags(tags);
        });
    }

    public void removeTag(String name) {
        edit("Remove tag", d -> {
            List<SpriteTag> tags = new ArrayList<>(d.getTags());
            tags.removeIf(t -> t.name().equals(name));
            d.setTags(tags);
        });
    }

    public void addSlice(SpriteSlice slice) {
        edit("Add slice", d -> {
            List<SpriteSlice> slices = new ArrayList<>(d.getSlices());
            slices.add(slice);
            d.setSlices(slices);
        });
    }

    public void removeSlice(String name) {
        edit("Remove slice", d -> {
            List<SpriteSlice> slices = new ArrayList<>(d.getSlices());
            slices.removeIf(s -> s.name().equals(name));
            d.setSlices(slices);
        });
    }

    public void resizeCanvas(int width, int height) {
        edit("Resize canvas", d -> d.resizeCanvas(width, height));
        selection = null;
    }

    // ---- palette ----

    /** Appends a color to the palette unless it is already there. */
    public void addPaletteColor(int rgba) {
        edit("Add palette color", d -> {
            int[] palette = d.getPalette();
            for (int c : palette) {
                if (c == rgba) {
                    return;
                }
            }
            int[] next = java.util.Arrays.copyOf(palette, palette.length + 1);
            next[palette.length] = rgba;
            d.setPalette(next);
        });
    }

    /** Replaces palette entry {@code index} and optionally recolors every pixel that used the old color, in all cels. */
    public void setPaletteColor(int index, int rgba, boolean recolorPixels) {
        edit("Edit palette color", d -> {
            int[] palette = d.getPalette();
            int old = palette[index];
            palette[index] = rgba;
            d.setPalette(palette);
            if (recolorPixels) {
                for (int l = 0; l < d.getLayerCount(); l++) {
                    for (int f = 0; f < d.getFrameCount(); f++) {
                        int[] cel = d.copyCel(l, f);
                        for (int i = 0; i < cel.length; i++) {
                            if (cel[i] == old) {
                                cel[i] = rgba;
                            }
                        }
                        d.setCel(l, f, cel);
                    }
                }
            }
        });
    }

    // ---- views ----

    /** The current frame with all visible layers composited. */
    public int[] flattened() {
        return doc.flatten(frame);
    }

    /**
     * The onion skin of the current frame: the previous frame tinted red and the next one blue, each faint, or null when onion skin is off or there
     * is no neighbour. The editor draws it under the current frame.
     */
    public int[] onionSkinPixels() {
        if (!onionSkin) {
            return null;
        }
        int[] out = new int[doc.getWidth() * doc.getHeight()];
        boolean any = false;
        if (frame > 0) {
            tint(doc.flatten(frame - 1), out, ONION_PREVIOUS);
            any = true;
        }
        if (frame < doc.getFrameCount() - 1) {
            tint(doc.flatten(frame + 1), out, ONION_NEXT);
            any = true;
        }
        return any ? out : null;
    }

    private static void tint(int[] source, int[] out, int tintColor) {
        for (int i = 0; i < source.length; i++) {
            int alpha = (source[i] & 0xFF) * ONION_ALPHA / 255;
            if (alpha > 0) {
                out[i] = SpriteDocument.over(out[i], (tintColor & 0xFFFFFF00) | alpha, SpriteLayer.OPAQUE);
            }
        }
    }
}
