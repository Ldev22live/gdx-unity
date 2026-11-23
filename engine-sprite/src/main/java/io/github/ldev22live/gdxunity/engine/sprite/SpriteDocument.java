package io.github.ldev22live.gdxunity.engine.sprite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A layered, multi-frame pixel image (TD-066). Pixels are {@code 0xRRGGBBAA}. Every cel (one layer on one frame) covers the whole canvas;
 * an absent cel is fully transparent. Frames have a duration in milliseconds; tags name frame ranges; the palette is a list of colors; slices mark regions.
 * <p>
 * Not thread safe. Structural edits go through the methods here; the editor wraps them in undoable commands using {@link #copy()} and {@link #restoreFrom}.
 */
public final class SpriteDocument {
    /** Largest canvas side; keeps a cel under about 16 MB. */
    public static final int MAX_SIZE = 2048;
    /** Duration of a new frame in milliseconds. */
    public static final int DEFAULT_FRAME_MS = 100;
    /** Transparent black. */
    public static final int TRANSPARENT = 0;

    private int width;
    private int height;
    private final List<SpriteLayer> layers = new ArrayList<>();
    private final List<Integer> frameDurations = new ArrayList<>();
    /** {@code cels.get(layer).get(frame)}, null when empty. */
    private final List<List<int[]>> cels = new ArrayList<>();
    private final List<SpriteTag> tags = new ArrayList<>();
    private final List<SpriteSlice> slices = new ArrayList<>();
    private int[] palette = {};

    /** A new document with one empty layer and one empty frame. */
    public SpriteDocument(int width, int height) {
        checkSize(width, height);
        this.width = width;
        this.height = height;
        frameDurations.add(DEFAULT_FRAME_MS);
        layers.add(new SpriteLayer("Layer 1", true, SpriteLayer.OPAQUE));
        List<int[]> row = new ArrayList<>();
        row.add(null);
        cels.add(row);
    }

    private static void checkSize(int width, int height) {
        if (width < 1 || height < 1 || width > MAX_SIZE || height > MAX_SIZE) {
            throw new IllegalArgumentException("canvas size must be 1.." + MAX_SIZE + " but was " + width + "x" + height);
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLayerCount() {
        return layers.size();
    }

    public int getFrameCount() {
        return frameDurations.size();
    }

    public SpriteLayer getLayer(int index) {
        checkLayer(index);
        return layers.get(index);
    }

    public void setLayer(int index, SpriteLayer layer) {
        checkLayer(index);
        layers.set(index, Objects.requireNonNull(layer, "layer cannot be null"));
    }

    public int getFrameDuration(int frame) {
        checkFrame(frame);
        return frameDurations.get(frame);
    }

    public void setFrameDuration(int frame, int millis) {
        checkFrame(frame);
        if (millis < 1) {
            throw new IllegalArgumentException("frame duration must be at least 1 ms but was " + millis);
        }
        frameDurations.set(frame, millis);
    }

    private void checkLayer(int layer) {
        if (layer < 0 || layer >= layers.size()) {
            throw new IndexOutOfBoundsException("layer " + layer + " of " + layers.size());
        }
    }

    private void checkFrame(int frame) {
        if (frame < 0 || frame >= frameDurations.size()) {
            throw new IndexOutOfBoundsException("frame " + frame + " of " + frameDurations.size());
        }
    }

    // ---- pixels ----

    /** The pixel at (x, y) of a cel; transparent for an empty cel. Throws outside the canvas. */
    public int getPixel(int layer, int frame, int x, int y) {
        checkLayer(layer);
        checkFrame(frame);
        checkPoint(x, y);
        int[] cel = cels.get(layer).get(frame);
        return cel == null ? TRANSPARENT : cel[y * width + x];
    }

    /** Sets a pixel, creating the cel when needed. Throws outside the canvas. */
    public void setPixel(int layer, int frame, int x, int y, int rgba) {
        checkLayer(layer);
        checkFrame(frame);
        checkPoint(x, y);
        int[] cel = cels.get(layer).get(frame);
        if (cel == null) {
            if (rgba == TRANSPARENT) {
                return;
            }
            cel = new int[width * height];
            cels.get(layer).set(frame, cel);
        }
        cel[y * width + x] = rgba;
    }

    private void checkPoint(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException("pixel " + x + "," + y + " outside " + width + "x" + height);
        }
    }

    /** A copy of a cel's pixels (row by row); all zero for an empty cel. */
    public int[] copyCel(int layer, int frame) {
        checkLayer(layer);
        checkFrame(frame);
        int[] cel = cels.get(layer).get(frame);
        return cel == null ? new int[width * height] : cel.clone();
    }

    /** Replaces a cel by a copy of {@code pixels} (length width*height); an all-transparent array makes the cel empty. */
    public void setCel(int layer, int frame, int[] pixels) {
        checkLayer(layer);
        checkFrame(frame);
        Objects.requireNonNull(pixels, "pixels cannot be null");
        if (pixels.length != width * height) {
            throw new IllegalArgumentException("a cel needs " + width * height + " pixels but got " + pixels.length);
        }
        boolean empty = true;
        for (int p : pixels) {
            if (p != TRANSPARENT) {
                empty = false;
                break;
            }
        }
        cels.get(layer).set(frame, empty ? null : pixels.clone());
    }

    /** Whether the cel has no content stored. */
    public boolean isCelEmpty(int layer, int frame) {
        checkLayer(layer);
        checkFrame(frame);
        return cels.get(layer).get(frame) == null;
    }

    // ---- layers ----

    /** Adds an empty layer on top; returns its index. */
    public int addLayer(String name) {
        return insertLayer(layers.size(), name);
    }

    /** Inserts an empty layer at {@code index} (0 is the bottom); returns the index. */
    public int insertLayer(int index, String name) {
        if (index < 0 || index > layers.size()) {
            throw new IndexOutOfBoundsException("layer " + index + " of " + layers.size());
        }
        layers.add(index, new SpriteLayer(name, true, SpriteLayer.OPAQUE));
        List<int[]> row = new ArrayList<>();
        for (int i = 0; i < frameDurations.size(); i++) {
            row.add(null);
        }
        cels.add(index, row);
        return index;
    }

    /** Removes a layer; the last layer cannot be removed. */
    public void removeLayer(int index) {
        checkLayer(index);
        if (layers.size() == 1) {
            throw new IllegalStateException("a sprite needs at least one layer");
        }
        layers.remove(index);
        cels.remove(index);
    }

    /** Moves a layer to a new position in the stack. */
    public void moveLayer(int from, int to) {
        checkLayer(from);
        checkLayer(to);
        layers.add(to, layers.remove(from));
        cels.add(to, cels.remove(from));
    }

    // ---- frames ----

    /** Inserts a frame at {@code index}; {@code copyOf} is a frame whose cels are copied, or -1 for an empty frame. Returns the index. */
    public int insertFrame(int index, int copyOf) {
        if (index < 0 || index > frameDurations.size()) {
            throw new IndexOutOfBoundsException("frame " + index + " of " + frameDurations.size());
        }
        int duration = DEFAULT_FRAME_MS;
        if (copyOf >= 0) {
            checkFrame(copyOf);
            duration = frameDurations.get(copyOf);
        }
        frameDurations.add(index, duration);
        for (List<int[]> row : cels) {
            int[] cel = copyOf >= 0 ? row.get(copyOf) : null; // read before the add shifts the row
            row.add(index, cel == null ? null : cel.clone());
        }
        for (int i = 0; i < tags.size(); i++) {
            SpriteTag t = tags.get(i);
            int from = t.from() >= index ? t.from() + 1 : t.from();
            int to = t.to() >= index ? t.to() + 1 : t.to();
            tags.set(i, new SpriteTag(t.name(), from, to, t.direction()));
        }
        return index;
    }

    /** Removes a frame; the last frame cannot be removed. Tags shrink or disappear with their frames. */
    public void removeFrame(int index) {
        checkFrame(index);
        if (frameDurations.size() == 1) {
            throw new IllegalStateException("a sprite needs at least one frame");
        }
        frameDurations.remove(index);
        for (List<int[]> row : cels) {
            row.remove(index);
        }
        List<SpriteTag> kept = new ArrayList<>();
        for (SpriteTag t : tags) {
            if (t.from() == index && t.to() == index) {
                continue;
            }
            int from = t.from() > index ? t.from() - 1 : t.from();
            int to = t.to() >= index ? t.to() - 1 : t.to();
            kept.add(new SpriteTag(t.name(), from, Math.max(from, to), t.direction()));
        }
        tags.clear();
        tags.addAll(kept);
    }

    /** Moves a frame to a new position. Tags are not moved with it. */
    public void moveFrame(int from, int to) {
        checkFrame(from);
        checkFrame(to);
        frameDurations.add(to, frameDurations.remove(from));
        for (List<int[]> row : cels) {
            row.add(to, row.remove(from));
        }
    }

    // ---- tags, slices, palette ----

    public List<SpriteTag> getTags() {
        return List.copyOf(tags);
    }

    /** Replaces the tags; every range must lie inside the frames. */
    public void setTags(List<SpriteTag> newTags) {
        for (SpriteTag t : Objects.requireNonNull(newTags, "tags cannot be null")) {
            if (t.to() >= frameDurations.size()) {
                throw new IllegalArgumentException("tag " + t.name() + " ends at frame " + t.to() + " but there are " + frameDurations.size() + " frames");
            }
        }
        List<SpriteTag> copy = List.copyOf(newTags);
        tags.clear();
        tags.addAll(copy);
    }

    public List<SpriteSlice> getSlices() {
        return List.copyOf(slices);
    }

    /** Replaces the slices; each must lie inside the canvas. */
    public void setSlices(List<SpriteSlice> newSlices) {
        for (SpriteSlice s : Objects.requireNonNull(newSlices, "slices cannot be null")) {
            if (s.x() < 0 || s.y() < 0 || s.x() + s.width() > width || s.y() + s.height() > height) {
                throw new IllegalArgumentException("slice " + s.name() + " is outside the canvas");
            }
        }
        List<SpriteSlice> copy = List.copyOf(newSlices);
        slices.clear();
        slices.addAll(copy);
    }

    /** The palette colors ({@code 0xRRGGBBAA}), a copy. */
    public int[] getPalette() {
        return palette.clone();
    }

    public void setPalette(int[] colors) {
        palette = Objects.requireNonNull(colors, "colors cannot be null").clone();
    }

    // ---- canvas ----

    /** Changes the canvas size; content stays at the top left, cut or padded with transparent pixels. Slices that no longer fit are dropped. */
    public void resizeCanvas(int newWidth, int newHeight) {
        checkSize(newWidth, newHeight);
        for (List<int[]> row : cels) {
            for (int f = 0; f < row.size(); f++) {
                int[] old = row.get(f);
                if (old == null) {
                    continue;
                }
                int[] next = new int[newWidth * newHeight];
                for (int y = 0; y < Math.min(height, newHeight); y++) {
                    System.arraycopy(old, y * width, next, y * newWidth, Math.min(width, newWidth));
                }
                row.set(f, Arrays.stream(next).anyMatch(p -> p != TRANSPARENT) ? next : null);
            }
        }
        width = newWidth;
        height = newHeight;
        slices.removeIf(s -> s.x() + s.width() > newWidth || s.y() + s.height() > newHeight);
    }

    // ---- compositing ----

    /** The visible layers of a frame composited bottom to top with layer opacity; {@code 0xRRGGBBAA} row by row. */
    public int[] flatten(int frame) {
        checkFrame(frame);
        int[] out = new int[width * height];
        for (int layer = 0; layer < layers.size(); layer++) {
            SpriteLayer settings = layers.get(layer);
            int[] cel = cels.get(layer).get(frame);
            if (!settings.visible() || cel == null || settings.opacity() == 0) {
                continue;
            }
            for (int i = 0; i < out.length; i++) {
                out[i] = over(out[i], cel[i], settings.opacity());
            }
        }
        return out;
    }

    /** Source-over blend of {@code src} (with extra opacity 0..255) on {@code dst}, both {@code 0xRRGGBBAA}, not premultiplied. */
    public static int over(int dst, int src, int opacity) {
        int sa = ((src & 0xFF) * opacity + 127) / 255;
        if (sa == 0) {
            return dst;
        }
        int da = dst & 0xFF;
        if (sa == 255 || da == 0) {
            return (src & 0xFFFFFF00) | sa;
        }
        int outA = sa + da * (255 - sa) / 255;
        int r = blend(src >>> 24, sa, dst >>> 24, da, outA);
        int g = blend((src >>> 16) & 0xFF, sa, (dst >>> 16) & 0xFF, da, outA);
        int b = blend((src >>> 8) & 0xFF, sa, (dst >>> 8) & 0xFF, da, outA);
        return (r << 24) | (g << 16) | (b << 8) | outA;
    }

    private static int blend(int s, int sa, int d, int da, int outA) {
        return Math.min(255, (s * sa * 255 + d * da * (255 - sa) + outA * 127) / (outA * 255));
    }

    // ---- copying ----

    /** A deep copy; used by the editor as an undo snapshot. */
    public SpriteDocument copy() {
        SpriteDocument c = new SpriteDocument(width, height);
        c.restoreFrom(this);
        return c;
    }

    /** Makes this document equal to {@code other} (deep copy of its content). */
    public void restoreFrom(SpriteDocument other) {
        Objects.requireNonNull(other, "other cannot be null");
        if (other == this) {
            return;
        }
        width = other.width;
        height = other.height;
        layers.clear();
        layers.addAll(other.layers);
        frameDurations.clear();
        frameDurations.addAll(other.frameDurations);
        cels.clear();
        for (List<int[]> row : other.cels) {
            List<int[]> copy = new ArrayList<>();
            for (int[] cel : row) {
                copy.add(cel == null ? null : cel.clone());
            }
            cels.add(copy);
        }
        tags.clear();
        tags.addAll(other.tags);
        slices.clear();
        slices.addAll(other.slices);
        palette = other.palette.clone();
    }

    /** Whether both documents hold the same size, layers, frames, pixels, tags, slices and palette. */
    public boolean contentEquals(SpriteDocument other) {
        if (width != other.width || height != other.height || !layers.equals(other.layers) || !frameDurations.equals(other.frameDurations)
            || !tags.equals(other.tags) || !slices.equals(other.slices) || !Arrays.equals(palette, other.palette)) {
            return false;
        }
        for (int l = 0; l < layers.size(); l++) {
            for (int f = 0; f < frameDurations.size(); f++) {
                if (!Arrays.equals(copyCel(l, f), other.copyCel(l, f))) {
                    return false;
                }
            }
        }
        return true;
    }
}
