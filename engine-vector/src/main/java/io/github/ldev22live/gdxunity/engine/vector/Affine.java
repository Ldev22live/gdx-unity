package io.github.ldev22live.gdxunity.engine.vector;

/**
 * A 2D affine transform in SVG's order: a point (x, y) maps to (a·x + c·y + e, b·x + d·y + f).
 */
public record Affine(float a, float b, float c, float d, float e, float f) {
    public static final Affine IDENTITY = new Affine(1f, 0f, 0f, 1f, 0f, 0f);

    public Affine {
        for (float value : new float[] {a, b, c, d, e, f}) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Transform values must be finite: " + a + ", " + b + ", " + c + ", " + d + ", " + e
                    + ", " + f);
            }
        }
    }

    public static Affine translate(float x, float y) {
        return new Affine(1f, 0f, 0f, 1f, x, y);
    }

    public static Affine scale(float x, float y) {
        return new Affine(x, 0f, 0f, y, 0f, 0f);
    }

    /** Rotation by {@code degrees} (clockwise on screen, as SVG with y down). */
    public static Affine rotate(float degrees) {
        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new Affine(cos, sin, -sin, cos, 0f, 0f);
    }

    public static Affine skewX(float degrees) {
        return new Affine(1f, 0f, (float) Math.tan(Math.toRadians(degrees)), 1f, 0f, 0f);
    }

    public static Affine skewY(float degrees) {
        return new Affine(1f, (float) Math.tan(Math.toRadians(degrees)), 0f, 1f, 0f, 0f);
    }

    /** This transform applied after {@code inner}: {@code this.then...} reads outer · inner, as SVG nests transforms. */
    public Affine times(Affine inner) {
        return new Affine(
            a * inner.a + c * inner.b, b * inner.a + d * inner.b,
            a * inner.c + c * inner.d, b * inner.c + d * inner.d,
            a * inner.e + c * inner.f + e, b * inner.e + d * inner.f + f);
    }

    public float x(float px, float py) {
        return a * px + c * py + e;
    }

    public float y(float px, float py) {
        return b * px + d * py + f;
    }

    public boolean isIdentity() {
        return equals(IDENTITY);
    }

    /** How much the transform scales lengths on average (for stroke widths). */
    public float meanScale() {
        return (float) Math.sqrt(Math.abs(a * d - b * c));
    }
}
