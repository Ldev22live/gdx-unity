package io.github.ldev22live.gdxunity.editor.sprite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure pixel geometry for the drawing tools: lines, rectangles, ellipses, flood fill and the pixel-perfect rule. Points are {@code {x, y}}. */
public final class PixelOps {
    private PixelOps() { }

    /** Bresenham line from (x0, y0) to (x1, y1), both ends included, in drawing order. */
    public static List<int[]> line(int x0, int y0, int x1, int y1) {
        List<int[]> out = new ArrayList<>();
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            out.add(new int[] {x, y});
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
        return out;
    }

    /** The rectangle with opposite corners (x0, y0) and (x1, y1), outline or filled. */
    public static List<int[]> rect(int x0, int y0, int x1, int y1, boolean filled) {
        int left = Math.min(x0, x1);
        int right = Math.max(x0, x1);
        int top = Math.min(y0, y1);
        int bottom = Math.max(y0, y1);
        List<int[]> out = new ArrayList<>();
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (filled || x == left || x == right || y == top || y == bottom) {
                    out.add(new int[] {x, y});
                }
            }
        }
        return out;
    }

    /** The ellipse that fits the box with corners (x0, y0) and (x1, y1), outline or filled; each pixel once. */
    public static List<int[]> ellipse(int x0, int y0, int x1, int y1, boolean filled) {
        int left = Math.min(x0, x1);
        int right = Math.max(x0, x1);
        int top = Math.min(y0, y1);
        int bottom = Math.max(y0, y1);
        double cx = (left + right) / 2.0;
        double cy = (top + bottom) / 2.0;
        double rx = (right - left + 1) / 2.0;
        double ry = (bottom - top + 1) / 2.0;
        boolean[][] inside = new boolean[bottom - top + 1][right - left + 1];
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                double nx = (x - cx) / rx;
                double ny = (y - cy) / ry;
                inside[y - top][x - left] = nx * nx + ny * ny <= 1.0;
            }
        }
        List<int[]> out = new ArrayList<>();
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (!inside[y - top][x - left]) {
                    continue;
                }
                if (filled || isEdge(inside, x - left, y - top)) {
                    out.add(new int[] {x, y});
                }
            }
        }
        return out;
    }

    private static boolean isEdge(boolean[][] inside, int x, int y) {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (ny < 0 || nx < 0 || ny >= inside.length || nx >= inside[0].length || !inside[ny][nx]) {
                return true;
            }
        }
        return false;
    }

    /**
     * The pixels reached from (x, y) through 4-connected neighbours that have exactly the color of the start pixel.
     * Empty when (x, y) is outside the image.
     */
    public static List<int[]> floodFill(int[] pixels, int width, int height, int x, int y) {
        Objects.requireNonNull(pixels, "pixels cannot be null");
        List<int[]> out = new ArrayList<>();
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return out;
        }
        int target = pixels[y * width + x];
        boolean[] seen = new boolean[width * height];
        int[] stack = new int[width * height];
        int top = 0;
        stack[top++] = y * width + x;
        seen[y * width + x] = true;
        while (top > 0) {
            int index = stack[--top];
            int px = index % width;
            int py = index / width;
            out.add(new int[] {px, py});
            int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : around) {
                int nx = px + d[0];
                int ny = py + d[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                    continue;
                }
                int next = ny * width + nx;
                if (!seen[next] && pixels[next] == target) {
                    seen[next] = true;
                    stack[top++] = next;
                }
            }
        }
        return out;
    }

    /**
     * The pixel-perfect rule for a freehand stroke: a pixel that only turns a corner (both neighbours in the stroke touch it on a side and touch each
     * other only diagonally) is removed, so a line is one pixel thick everywhere.
     */
    public static List<int[]> pixelPerfect(List<int[]> stroke) {
        Objects.requireNonNull(stroke, "stroke cannot be null");
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < stroke.size(); i++) {
            // Compared with the last pixel kept, so a staircase loses every other pixel
            if (!out.isEmpty() && i < stroke.size() - 1 && isCorner(out.get(out.size() - 1), stroke.get(i), stroke.get(i + 1))) {
                continue;
            }
            out.add(stroke.get(i));
        }
        return out;
    }

    private static boolean isCorner(int[] a, int[] b, int[] c) {
        boolean aSide = Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) == 1;
        boolean cSide = Math.abs(c[0] - b[0]) + Math.abs(c[1] - b[1]) == 1;
        boolean diagonal = Math.abs(a[0] - c[0]) == 1 && Math.abs(a[1] - c[1]) == 1;
        return aSide && cSide && diagonal;
    }
}
