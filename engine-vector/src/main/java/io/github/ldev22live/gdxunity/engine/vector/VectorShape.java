package io.github.ldev22live.gdxunity.engine.vector;

import java.util.Objects;

/**
 * One named shape of a {@link VectorDocument}: a path in its own coordinates, placed by {@code transform}, filled and stroked with
 * solid colours. A null fill or stroke draws nothing for it.
 *
 * @param name        unique within its document; what the editor lists and the slice tests address
 * @param path        the outline, in the shape's coordinates
 * @param transform   shape coordinates to document coordinates (groups of an imported SVG are folded into it)
 * @param fill        fill colour, or null for no fill
 * @param fillRule    how overlapping subpaths fill
 * @param stroke      stroke colour, or null for no stroke
 * @param strokeWidth stroke width in shape coordinates; 0 or more
 * @param opacity     the whole shape's opacity, 0 to 1
 */
public record VectorShape(String name, VectorPath path, Affine transform, Rgba fill, FillRule fillRule, Rgba stroke,
                          float strokeWidth, float opacity) {
    /** Which points are inside a fill where subpaths overlap. */
    public enum FillRule {
        /** Inside where the outline winds round a point a non-zero number of times (SVG's default). */
        NONZERO,
        /** Inside where a ray from the point crosses the outline an odd number of times. */
        EVENODD
    }

    public VectorShape {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A shape needs a name");
        }
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(transform, "transform cannot be null");
        Objects.requireNonNull(fillRule, "fillRule cannot be null");
        if (!(strokeWidth >= 0f) || !Float.isFinite(strokeWidth)) {
            throw new IllegalArgumentException("Stroke width must be 0 or more: " + strokeWidth);
        }
        if (!(opacity >= 0f && opacity <= 1f)) {
            throw new IllegalArgumentException("Opacity must be 0 to 1: " + opacity);
        }
    }

    /** A filled shape without stroke, untransformed and opaque. */
    public static VectorShape filled(String name, VectorPath path, Rgba fill) {
        return new VectorShape(name, path, Affine.IDENTITY, fill, FillRule.NONZERO, null, 0f, 1f);
    }

    public VectorShape withPath(VectorPath newPath) {
        return new VectorShape(name, newPath, transform, fill, fillRule, stroke, strokeWidth, opacity);
    }

    public VectorShape withFill(Rgba newFill) {
        return new VectorShape(name, path, transform, newFill, fillRule, stroke, strokeWidth, opacity);
    }

    public VectorShape withStroke(Rgba newStroke, float newWidth) {
        return new VectorShape(name, path, transform, fill, fillRule, newStroke, newWidth, opacity);
    }

    public VectorShape withTransform(Affine newTransform) {
        return new VectorShape(name, path, newTransform, fill, fillRule, stroke, strokeWidth, opacity);
    }
}
