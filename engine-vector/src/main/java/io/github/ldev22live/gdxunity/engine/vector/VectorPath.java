package io.github.ldev22live.gdxunity.engine.vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * An immutable path: subpaths of lines, quadratic and cubic Bézier segments, in absolute coordinates. Every segment's points are
 * editable {@linkplain #points() points}; moving one gives a new path ({@link #withPoint}).
 */
public record VectorPath(List<Segment> segments) {
    /** Tolerance, in path units, of the straight pieces {@link #flatten} turns curves into. */
    public static final float FLATNESS = 0.05f;
    /** Most pieces one curve is cut into, whatever its size. */
    private static final int MAX_PIECES = 256;

    /** One step of a path. */
    public sealed interface Segment permits MoveTo, LineTo, QuadTo, CubicTo, Close {
        /** The points this segment owns, in order (control points first, end point last); empty for Close. */
        float[] coordinates();
    }

    public record MoveTo(float x, float y) implements Segment {
        public float[] coordinates() { return new float[] {x, y}; }
    }

    public record LineTo(float x, float y) implements Segment {
        public float[] coordinates() { return new float[] {x, y}; }
    }

    public record QuadTo(float cx, float cy, float x, float y) implements Segment {
        public float[] coordinates() { return new float[] {cx, cy, x, y}; }
    }

    public record CubicTo(float c1x, float c1y, float c2x, float c2y, float x, float y) implements Segment {
        public float[] coordinates() { return new float[] {c1x, c1y, c2x, c2y, x, y}; }
    }

    public record Close() implements Segment {
        public float[] coordinates() { return new float[0]; }
    }

    /** Where an editable point is: its segment, and which of the segment's points. */
    public record PointRef(int segment, int point) { }

    public VectorPath {
        segments = List.copyOf(Objects.requireNonNull(segments, "segments cannot be null"));
        if (!segments.isEmpty() && !(segments.getFirst() instanceof MoveTo)) {
            throw new IllegalArgumentException("A path starts with a move: " + segments.getFirst());
        }
        for (Segment segment : segments) {
            for (float value : segment.coordinates()) {
                if (!Float.isFinite(value)) {
                    throw new IllegalArgumentException("Path coordinates must be finite: " + segment);
                }
            }
        }
    }

    /** A closed polygon through {@code xy} pairs. */
    public static VectorPath polygon(float... xy) {
        if (xy.length < 6 || xy.length % 2 != 0) {
            throw new IllegalArgumentException("A polygon needs at least three points, as x, y pairs: " + xy.length + " values");
        }
        List<Segment> result = new ArrayList<>();
        result.add(new MoveTo(xy[0], xy[1]));
        for (int i = 2; i < xy.length; i += 2) {
            result.add(new LineTo(xy[i], xy[i + 1]));
        }
        result.add(new Close());
        return new VectorPath(result);
    }

    /** Every editable point, in path order. */
    public List<PointRef> points() {
        List<PointRef> result = new ArrayList<>();
        for (int s = 0; s < segments.size(); s++) {
            int count = segments.get(s).coordinates().length / 2;
            for (int p = 0; p < count; p++) {
                result.add(new PointRef(s, p));
            }
        }
        return result;
    }

    /** The coordinates of an editable point, as {x, y}. */
    public float[] point(PointRef ref) {
        float[] values = segment(ref).coordinates();
        checkPoint(ref, values);
        return new float[] {values[ref.point() * 2], values[ref.point() * 2 + 1]};
    }

    /** This path with one editable point moved to (x, y). */
    public VectorPath withPoint(PointRef ref, float x, float y) {
        float[] values = segment(ref).coordinates();
        checkPoint(ref, values);
        values[ref.point() * 2] = x;
        values[ref.point() * 2 + 1] = y;
        List<Segment> result = new ArrayList<>(segments);
        result.set(ref.segment(), rebuild(segments.get(ref.segment()), values));
        return new VectorPath(result);
    }

    /** This path with every point mapped through {@code transform}. */
    public VectorPath transformed(Affine transform) {
        if (transform.isIdentity()) {
            return this;
        }
        List<Segment> result = new ArrayList<>();
        for (Segment segment : segments) {
            float[] values = segment.coordinates();
            float[] mapped = new float[values.length];
            for (int i = 0; i < values.length; i += 2) {
                mapped[i] = transform.x(values[i], values[i + 1]);
                mapped[i + 1] = transform.y(values[i], values[i + 1]);
            }
            result.add(rebuild(segment, mapped));
        }
        return new VectorPath(result);
    }

    /**
     * The path as polylines, one per subpath, curves cut into straight pieces within {@link #FLATNESS}. A closed subpath ends where
     * it started; an open one is returned open (filling closes it implicitly, stroking does not).
     *
     * @return per subpath: {x0, y0, x1, y1, ...} and whether it was closed
     */
    public List<Polyline> flatten() {
        List<Polyline> result = new ArrayList<>();
        FloatList current = null;
        float startX = 0f;
        float startY = 0f;
        float x = 0f;
        float y = 0f;
        for (Segment segment : segments) {
            switch (segment) {
                case MoveTo m -> {
                    if (current != null && current.size() >= 4) {
                        result.add(new Polyline(current.toArray(), false));
                    }
                    current = new FloatList();
                    current.add(m.x(), m.y());
                    startX = x = m.x();
                    startY = y = m.y();
                }
                case LineTo l -> {
                    current.add(l.x(), l.y());
                    x = l.x();
                    y = l.y();
                }
                case QuadTo q -> {
                    int pieces = pieces(Math.abs(x - 2 * q.cx() + q.x()) + Math.abs(y - 2 * q.cy() + q.y()));
                    for (int i = 1; i <= pieces; i++) {
                        float t = (float) i / pieces;
                        float u = 1 - t;
                        current.add(u * u * x + 2 * u * t * q.cx() + t * t * q.x(), u * u * y + 2 * u * t * q.cy() + t * t * q.y());
                    }
                    x = q.x();
                    y = q.y();
                }
                case CubicTo c -> {
                    float bend = Math.max(Math.abs(x - 2 * c.c1x() + c.c2x()) + Math.abs(y - 2 * c.c1y() + c.c2y()),
                        Math.abs(c.c1x() - 2 * c.c2x() + c.x()) + Math.abs(c.c1y() - 2 * c.c2y() + c.y()));
                    int pieces = pieces(bend * 1.5f);
                    for (int i = 1; i <= pieces; i++) {
                        float t = (float) i / pieces;
                        float u = 1 - t;
                        float w0 = u * u * u;
                        float w1 = 3 * u * u * t;
                        float w2 = 3 * u * t * t;
                        float w3 = t * t * t;
                        current.add(w0 * x + w1 * c.c1x() + w2 * c.c2x() + w3 * c.x(), w0 * y + w1 * c.c1y() + w2 * c.c2y() + w3 * c.y());
                    }
                    x = c.x();
                    y = c.y();
                }
                case Close ignored -> {
                    if (current != null) {
                        current.add(startX, startY);
                        result.add(new Polyline(current.toArray(), true));
                        current = new FloatList();
                        current.add(startX, startY);
                        x = startX;
                        y = startY;
                    }
                }
            }
        }
        if (current != null && current.size() >= 4) {
            result.add(new Polyline(current.toArray(), false));
        }
        return result;
    }

    /** One flattened subpath. */
    public record Polyline(float[] xy, boolean closed) { }

    private Segment segment(PointRef ref) {
        Objects.requireNonNull(ref, "ref cannot be null");
        if (ref.segment() < 0 || ref.segment() >= segments.size()) {
            throw new IllegalArgumentException("No segment " + ref.segment() + " in a path of " + segments.size());
        }
        return segments.get(ref.segment());
    }

    private static void checkPoint(PointRef ref, float[] values) {
        if (ref.point() < 0 || ref.point() * 2 >= values.length) {
            throw new IllegalArgumentException("No point " + ref.point() + " in segment " + ref.segment());
        }
    }

    private static Segment rebuild(Segment kind, float[] v) {
        return switch (kind) {
            case MoveTo m -> new MoveTo(v[0], v[1]);
            case LineTo l -> new LineTo(v[0], v[1]);
            case QuadTo q -> new QuadTo(v[0], v[1], v[2], v[3]);
            case CubicTo c -> new CubicTo(v[0], v[1], v[2], v[3], v[4], v[5]);
            case Close c -> c;
        };
    }

    /** Pieces for a curve whose control polygon bends by {@code bend}: the error of n pieces falls as bend / n². */
    private static int pieces(float bend) {
        return Math.max(1, Math.min(MAX_PIECES, (int) Math.ceil(Math.sqrt(bend / (8f * FLATNESS)))));
    }

    /** A growable float array for flattening. */
    private static final class FloatList {
        private float[] values = new float[16];
        private int size;

        void add(float x, float y) {
            if (size + 2 > values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = x;
            values[size++] = y;
        }

        int size() {
            return size;
        }

        float[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
