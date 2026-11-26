package io.github.ldev22live.gdxunity.engine.vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * Draws a {@link VectorDocument} into pixels (TD-104), headless. Pixels are {@code 0xRRGGBBAA}, straight alpha, rows top to bottom,
 * the format of a sprite cel, so a drawing can become a sprite layer. Each pixel is sampled 4 × 4 times; a shape covers the samples
 * inside its fill (non-zero or even-odd) or within half its stroke width of its outline, and is blended over what is below in
 * proportion to the samples it covers.
 *
 * <p>Strokes are drawn with round joins and round caps (the distance to the outline), whatever an imported SVG asked for; SVG's
 * default butt caps and miter joins differ from this at sharp corners and open ends by up to half the stroke width.
 */
public final class VectorRasterizer {
    /** Samples per pixel side; 4 × 4 samples per pixel. */
    public static final int SAMPLES = 4;

    private VectorRasterizer() { }

    /** The document at its own canvas size. */
    public static int[] render(VectorDocument document) {
        return render(document, document.width(), document.height());
    }

    /** The document scaled to fill {@code width} × {@code height} pixels. */
    public static int[] render(VectorDocument document, int width, int height) {
        Objects.requireNonNull(document, "document cannot be null");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("A raster needs a positive size: " + width + "x" + height);
        }
        float[] red = new float[width * height];
        float[] green = new float[width * height];
        float[] blue = new float[width * height];
        float[] alpha = new float[width * height];
        Affine canvas = Affine.scale((float) width / document.width(), (float) height / document.height());
        for (VectorShape shape : document.shapes()) {
            Affine toPixels = canvas.times(shape.transform());
            List<VectorPath.Polyline> outline = shape.path().transformed(toPixels).flatten();
            if (shape.fill() != null) {
                blend(fillMask(outline, shape.fillRule(), width, height), shape.fill().withOpacity(shape.opacity()), width, height,
                    red, green, blue, alpha);
            }
            if (shape.stroke() != null && shape.strokeWidth() > 0f) {
                float half = shape.strokeWidth() * toPixels.meanScale() / 2f;
                blend(strokeMask(outline, half, width, height), shape.stroke().withOpacity(shape.opacity()), width, height,
                    red, green, blue, alpha);
            }
        }
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            if (alpha[i] > 0f) {
                pixels[i] = (channel(red[i] / alpha[i]) << 24) | (channel(green[i] / alpha[i]) << 16) | (channel(blue[i] / alpha[i]) << 8)
                    | channel(alpha[i]);
            }
        }
        return pixels;
    }

    /** Blends {@code colour} over the premultiplied accumulators in proportion to each pixel's covered samples. */
    private static void blend(BitSet mask, Rgba colour, int width, int height, float[] red, float[] green, float[] blue, float[] alpha) {
        int columns = width * SAMPLES;
        float perSample = 1f / (SAMPLES * SAMPLES);
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int covered = 0;
                for (int sy = 0; sy < SAMPLES; sy++) {
                    int row = (py * SAMPLES + sy) * columns + px * SAMPLES;
                    for (int sx = 0; sx < SAMPLES; sx++) {
                        if (mask.get(row + sx)) {
                            covered++;
                        }
                    }
                }
                if (covered == 0) {
                    continue;
                }
                float a = colour.a() * covered * perSample;
                int i = py * width + px;
                red[i] = colour.r() * a + red[i] * (1 - a);
                green[i] = colour.g() * a + green[i] * (1 - a);
                blue[i] = colour.b() * a + blue[i] * (1 - a);
                alpha[i] = a + alpha[i] * (1 - a);
            }
        }
    }

    /** Samples inside the filled outline (every subpath closed), by scanline crossings. */
    static BitSet fillMask(List<VectorPath.Polyline> outline, VectorShape.FillRule rule, int width, int height) {
        int columns = width * SAMPLES;
        int rows = height * SAMPLES;
        BitSet mask = new BitSet(columns * rows);
        List<float[]> edges = new ArrayList<>();
        for (VectorPath.Polyline polyline : outline) {
            float[] xy = polyline.xy();
            int n = xy.length / 2;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                if (j == 0 && polyline.closed()) {
                    break; // a closed polyline already ends at its start
                }
                edges.add(new float[] {xy[i * 2], xy[i * 2 + 1], xy[j * 2], xy[j * 2 + 1]});
            }
        }
        float[] crossings = new float[Math.max(2, edges.size())];
        int[] directions = new int[crossings.length];
        Integer[] order = new Integer[crossings.length];
        for (int row = 0; row < rows; row++) {
            float y = (row + 0.5f) / SAMPLES;
            int count = 0;
            for (float[] e : edges) {
                float y0 = e[1];
                float y1 = e[3];
                if ((y0 <= y && y1 > y) || (y1 <= y && y0 > y)) {
                    crossings[count] = e[0] + (y - y0) / (y1 - y0) * (e[2] - e[0]);
                    directions[count] = y1 > y0 ? 1 : -1;
                    count++;
                }
            }
            if (count < 2) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                order[i] = i;
            }
            Arrays.sort(order, 0, count, (a, b) -> Float.compare(crossings[a], crossings[b]));
            int winding = 0;
            for (int k = 0; k < count - 1; k++) {
                winding += directions[order[k]];
                boolean inside = rule == VectorShape.FillRule.EVENODD ? (k % 2 == 0) : winding != 0;
                if (inside) {
                    markSpan(mask, row, columns, crossings[order[k]], crossings[order[k + 1]]);
                }
            }
        }
        return mask;
    }

    private static void markSpan(BitSet mask, int row, int columns, float x0, float x1) {
        int first = Math.max(0, (int) Math.ceil(x0 * SAMPLES - 0.5f));
        int end = Math.min(columns, (int) Math.ceil(x1 * SAMPLES - 0.5f));
        if (end > first) {
            mask.set(row * columns + first, row * columns + end);
        }
    }

    /** Samples within {@code half} of any outline segment (round joins and caps). */
    static BitSet strokeMask(List<VectorPath.Polyline> outline, float half, int width, int height) {
        int columns = width * SAMPLES;
        int rows = height * SAMPLES;
        BitSet mask = new BitSet(columns * rows);
        float half2 = half * half;
        for (VectorPath.Polyline polyline : outline) {
            float[] xy = polyline.xy();
            for (int i = 0; i + 3 < xy.length; i += 2) {
                float ax = xy[i];
                float ay = xy[i + 1];
                float bx = xy[i + 2];
                float by = xy[i + 3];
                int c0 = Math.max(0, (int) Math.floor((Math.min(ax, bx) - half) * SAMPLES));
                int c1 = Math.min(columns - 1, (int) Math.ceil((Math.max(ax, bx) + half) * SAMPLES));
                int r0 = Math.max(0, (int) Math.floor((Math.min(ay, by) - half) * SAMPLES));
                int r1 = Math.min(rows - 1, (int) Math.ceil((Math.max(ay, by) + half) * SAMPLES));
                float dx = bx - ax;
                float dy = by - ay;
                float length2 = dx * dx + dy * dy;
                for (int r = r0; r <= r1; r++) {
                    float py = (r + 0.5f) / SAMPLES;
                    for (int c = c0; c <= c1; c++) {
                        float px = (c + 0.5f) / SAMPLES;
                        float t = length2 == 0f ? 0f : Math.max(0f, Math.min(1f, ((px - ax) * dx + (py - ay) * dy) / length2));
                        float ex = ax + t * dx - px;
                        float ey = ay + t * dy - py;
                        if (ex * ex + ey * ey <= half2) {
                            mask.set(r * columns + c);
                        }
                    }
                }
            }
        }
        return mask;
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255f)));
    }
}
