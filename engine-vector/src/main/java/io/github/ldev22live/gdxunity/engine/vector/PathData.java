package io.github.ldev22live.gdxunity.engine.vector;

import io.github.ldev22live.gdxunity.engine.vector.VectorPath.Close;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath.CubicTo;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath.LineTo;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath.MoveTo;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath.QuadTo;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath.Segment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SVG path data ({@code d}) to and from {@link VectorPath}. Reading takes every SVG 1.1 command, absolute and relative: M, L, H, V,
 * C, S, Q, T, A (elliptical arcs become cubic segments) and Z. Writing uses absolute M, L, Q, C and Z only.
 */
public final class PathData {
    /** Most cubic pieces per quarter turn of an arc. */
    private static final double MAX_ARC_PIECE_RADIANS = Math.PI / 2;

    private PathData() { }

    /**
     * Parses path data.
     *
     * @throws IllegalArgumentException with the position of the problem when the data is malformed
     */
    public static VectorPath parse(String d) {
        return new Reader(d == null ? "" : d).read();
    }

    /** Writes a path as absolute commands, numbers in shortest round-trippable form. */
    public static String format(VectorPath path) {
        StringBuilder out = new StringBuilder();
        for (Segment segment : path.segments()) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(switch (segment) {
                case MoveTo m -> "M";
                case LineTo l -> "L";
                case QuadTo q -> "Q";
                case CubicTo c -> "C";
                case Close c -> "Z";
            });
            for (float value : segment.coordinates()) {
                out.append(' ').append(number(value));
            }
        }
        return out.toString();
    }

    /** A float written without a trailing ".0" and without exponent where possible. */
    static String number(float value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e7f) {
            return Integer.toString((int) value);
        }
        String text = Float.toString(value);
        return text.contains("E") ? String.format(Locale.ROOT, "%.9f", value).replaceAll("0+$", "") : text;
    }

    private static final class Reader {
        private final String text;
        private int at;
        private final List<Segment> segments = new ArrayList<>();
        private float x;
        private float y;
        private float startX;
        private float startY;
        /** The last control point, for S and T reflection; NaN when the previous command had none of that kind. */
        private float lastCubicX = Float.NaN;
        private float lastCubicY = Float.NaN;
        private float lastQuadX = Float.NaN;
        private float lastQuadY = Float.NaN;

        Reader(String text) {
            this.text = text;
        }

        VectorPath read() {
            char command = 0;
            skipSeparators();
            while (at < text.length()) {
                char c = text.charAt(at);
                if (Character.isLetter(c)) {
                    command = c;
                    at++;
                } else if (command == 0) {
                    throw error("a path starts with a command");
                } else if (command == 'M') {
                    command = 'L';
                } else if (command == 'm') {
                    command = 'l';
                } else if (command == 'Z' || command == 'z') {
                    throw error("numbers after Z");
                }
                apply(command);
                skipSeparators();
            }
            return new VectorPath(segments);
        }

        private void apply(char command) {
            boolean relative = Character.isLowerCase(command);
            float ox = relative ? x : 0f;
            float oy = relative ? y : 0f;
            switch (Character.toUpperCase(command)) {
                case 'M' -> {
                    x = ox + number();
                    y = oy + number();
                    startX = x;
                    startY = y;
                    segments.add(new MoveTo(x, y));
                    clearControls();
                }
                case 'L' -> line(ox + number(), oy + number());
                case 'H' -> line(ox + number(), y);
                case 'V' -> line(x, oy + number());
                case 'C' -> cubic(ox + number(), oy + number(), ox + number(), oy + number(), ox + number(), oy + number());
                case 'S' -> {
                    float c1x = Float.isNaN(lastCubicX) ? x : 2 * x - lastCubicX;
                    float c1y = Float.isNaN(lastCubicY) ? y : 2 * y - lastCubicY;
                    cubic(c1x, c1y, ox + number(), oy + number(), ox + number(), oy + number());
                }
                case 'Q' -> quad(ox + number(), oy + number(), ox + number(), oy + number());
                case 'T' -> {
                    float cx = Float.isNaN(lastQuadX) ? x : 2 * x - lastQuadX;
                    float cy = Float.isNaN(lastQuadY) ? y : 2 * y - lastQuadY;
                    quad(cx, cy, ox + number(), oy + number());
                }
                case 'A' -> {
                    float rx = Math.abs(number());
                    float ry = Math.abs(number());
                    float rotation = number();
                    boolean large = flag();
                    boolean sweep = flag();
                    arc(rx, ry, rotation, large, sweep, ox + number(), oy + number());
                }
                case 'Z' -> {
                    requireStarted();
                    segments.add(new Close());
                    x = startX;
                    y = startY;
                    clearControls();
                }
                default -> throw error("unknown command '" + command + "'");
            }
        }

        private void line(float nx, float ny) {
            requireStarted();
            segments.add(new LineTo(nx, ny));
            x = nx;
            y = ny;
            clearControls();
        }

        private void cubic(float c1x, float c1y, float c2x, float c2y, float nx, float ny) {
            requireStarted();
            segments.add(new CubicTo(c1x, c1y, c2x, c2y, nx, ny));
            x = nx;
            y = ny;
            lastCubicX = c2x;
            lastCubicY = c2y;
            lastQuadX = Float.NaN;
            lastQuadY = Float.NaN;
        }

        private void quad(float cx, float cy, float nx, float ny) {
            requireStarted();
            segments.add(new QuadTo(cx, cy, nx, ny));
            x = nx;
            y = ny;
            lastQuadX = cx;
            lastQuadY = cy;
            lastCubicX = Float.NaN;
            lastCubicY = Float.NaN;
        }

        /** SVG 1.1 appendix F.6: endpoint to centre parameterization, then cubic pieces of at most a quarter turn. */
        private void arc(float rx, float ry, float rotationDegrees, boolean large, boolean sweep, float nx, float ny) {
            requireStarted();
            if ((nx == x && ny == y)) {
                clearControls();
                return;
            }
            if (rx == 0f || ry == 0f) {
                line(nx, ny);
                return;
            }
            double phi = Math.toRadians(rotationDegrees);
            double cos = Math.cos(phi);
            double sin = Math.sin(phi);
            double dx = (x - nx) / 2.0;
            double dy = (y - ny) / 2.0;
            double x1 = cos * dx + sin * dy;
            double y1 = -sin * dx + cos * dy;
            double rxs = rx;
            double rys = ry;
            double lambda = (x1 * x1) / (rxs * rxs) + (y1 * y1) / (rys * rys);
            if (lambda > 1) {
                rxs *= Math.sqrt(lambda);
                rys *= Math.sqrt(lambda);
            }
            double numerator = rxs * rxs * rys * rys - rxs * rxs * y1 * y1 - rys * rys * x1 * x1;
            double denominator = rxs * rxs * y1 * y1 + rys * rys * x1 * x1;
            double factor = Math.sqrt(Math.max(0, numerator / denominator)) * (large == sweep ? -1 : 1);
            double cx1 = factor * rxs * y1 / rys;
            double cy1 = -factor * rys * x1 / rxs;
            double cx = cos * cx1 - sin * cy1 + (x + nx) / 2.0;
            double cy = sin * cx1 + cos * cy1 + (y + ny) / 2.0;
            double start = Math.atan2((y1 - cy1) / rys, (x1 - cx1) / rxs);
            double end = Math.atan2((-y1 - cy1) / rys, (-x1 - cx1) / rxs);
            double delta = end - start;
            if (sweep && delta < 0) {
                delta += 2 * Math.PI;
            } else if (!sweep && delta > 0) {
                delta -= 2 * Math.PI;
            }
            int pieces = (int) Math.ceil(Math.abs(delta) / MAX_ARC_PIECE_RADIANS - 1e-9);
            double step = delta / pieces;
            double k = 4.0 / 3.0 * Math.tan(step / 4);
            double angle = start;
            for (int i = 0; i < pieces; i++) {
                double a1 = angle;
                double a2 = angle + step;
                double e1x = Math.cos(a1);
                double e1y = Math.sin(a1);
                double e2x = Math.cos(a2);
                double e2y = Math.sin(a2);
                double[] c1 = point(cx, cy, rxs, rys, cos, sin, e1x - k * e1y, e1y + k * e1x);
                double[] c2 = point(cx, cy, rxs, rys, cos, sin, e2x + k * e2y, e2y - k * e2x);
                double[] p = i == pieces - 1 ? new double[] {nx, ny} : point(cx, cy, rxs, rys, cos, sin, e2x, e2y);
                cubic((float) c1[0], (float) c1[1], (float) c2[0], (float) c2[1], (float) p[0], (float) p[1]);
                angle = a2;
            }
            lastCubicX = Float.NaN;
            lastCubicY = Float.NaN;
        }

        private static double[] point(double cx, double cy, double rx, double ry, double cos, double sin, double ux, double uy) {
            double px = rx * ux;
            double py = ry * uy;
            return new double[] {cos * px - sin * py + cx, sin * px + cos * py + cy};
        }

        private void clearControls() {
            lastCubicX = Float.NaN;
            lastCubicY = Float.NaN;
            lastQuadX = Float.NaN;
            lastQuadY = Float.NaN;
        }

        private void requireStarted() {
            if (segments.isEmpty()) {
                throw error("a path starts with M");
            }
        }

        private boolean flag() {
            skipSeparators();
            if (at < text.length() && (text.charAt(at) == '0' || text.charAt(at) == '1')) {
                return text.charAt(at++) == '1';
            }
            throw error("an arc flag (0 or 1)");
        }

        private float number() {
            skipSeparators();
            int begin = at;
            if (at < text.length() && (text.charAt(at) == '+' || text.charAt(at) == '-')) {
                at++;
            }
            boolean digits = false;
            boolean dot = false;
            while (at < text.length()) {
                char c = text.charAt(at);
                if (Character.isDigit(c)) {
                    digits = true;
                } else if (c == '.' && !dot) {
                    dot = true;
                } else {
                    break;
                }
                at++;
            }
            if (digits && at < text.length() && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
                int mark = at++;
                if (at < text.length() && (text.charAt(at) == '+' || text.charAt(at) == '-')) {
                    at++;
                }
                int exponentStart = at;
                while (at < text.length() && Character.isDigit(text.charAt(at))) {
                    at++;
                }
                if (at == exponentStart) {
                    at = mark;
                }
            }
            if (!digits) {
                at = begin;
                throw error("a number");
            }
            float value = Float.parseFloat(text.substring(begin, at));
            if (!Float.isFinite(value)) {
                throw error("a finite number");
            }
            return value;
        }

        private void skipSeparators() {
            while (at < text.length() && (Character.isWhitespace(text.charAt(at)) || text.charAt(at) == ',')) {
                at++;
            }
        }

        private IllegalArgumentException error(String expected) {
            return new IllegalArgumentException("Path data: expected " + expected + " at character " + at + " of \"" + text + "\"");
        }
    }
}
