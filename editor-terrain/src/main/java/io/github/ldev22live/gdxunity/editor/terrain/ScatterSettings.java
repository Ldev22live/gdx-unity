package io.github.ldev22live.gdxunity.editor.terrain;

/**
 * How the level editor's Scatter tool spreads an object over the ground (roadmap M3.6, TD-097). Lengths and heights are in world units
 * (100 a metre), heights in the terrain's own space. The values are checked when set, so a scatter never runs with an impossible one.
 */
public final class ScatterSettings {
    /** The area density is counted over: 10 m by 10 m, in square world units. */
    public static final float DENSITY_AREA = 1000f * 1000f;
    /** The densest scatter offered, in objects per {@link #DENSITY_AREA}. */
    public static final float MAX_DENSITY = 40f;
    /** The largest scale a scattered copy may get. */
    public static final float MAX_SCALE = 5f;
    /** The widest gap between scattered objects that may be asked for, in world units. */
    public static final float MAX_SPACING = 2000f;

    private float density = 3f;
    private float minScale = 0.8f;
    private float maxScale = 1.2f;
    private float maxSlopeDegrees = 35f;
    private float minHeight = Float.NEGATIVE_INFINITY;
    private float maxHeight = Float.POSITIVE_INFINITY;
    private float spacing = 150f;

    /** Objects per 10 m by 10 m of ground that a stroke fills up to. */
    public float getDensity() {
        return density;
    }

    /** @throws IllegalArgumentException unless {@code 0 < density <= }{@link #MAX_DENSITY} */
    public void setDensity(float density) {
        if (!(density > 0f && density <= MAX_DENSITY)) {
            throw new IllegalArgumentException("Scatter density must be above 0 and at most " + MAX_DENSITY + ": " + density);
        }
        this.density = density;
    }

    public float getMinScale() {
        return minScale;
    }

    public float getMaxScale() {
        return maxScale;
    }

    /**
     * The range each copy's even scale is picked from.
     *
     * @throws IllegalArgumentException unless {@code 0 < min <= max <= }{@link #MAX_SCALE}
     */
    public void setScaleRange(float min, float max) {
        if (!(min > 0f && min <= max && max <= MAX_SCALE)) {
            throw new IllegalArgumentException("Scatter scale range must have 0 < min <= max <= " + MAX_SCALE + ": " + min + " to " + max);
        }
        this.minScale = min;
        this.maxScale = max;
    }

    /** The steepest ground, in degrees from level, that gets an object. */
    public float getMaxSlopeDegrees() {
        return maxSlopeDegrees;
    }

    /** @throws IllegalArgumentException unless {@code 0 <= degrees <= 90} */
    public void setMaxSlopeDegrees(float degrees) {
        if (!(degrees >= 0f && degrees <= 90f)) {
            throw new IllegalArgumentException("Scatter slope limit must be 0 to 90 degrees: " + degrees);
        }
        this.maxSlopeDegrees = degrees;
    }

    /** The lowest ground that gets an object; negative infinity for no limit. */
    public float getMinHeight() {
        return minHeight;
    }

    /** The highest ground that gets an object; positive infinity for no limit. */
    public float getMaxHeight() {
        return maxHeight;
    }

    /**
     * Only ground from {@code min} to {@code max} high gets objects; infinities lift a limit.
     *
     * @throws IllegalArgumentException when either is NaN or {@code min > max}
     */
    public void setHeightRange(float min, float max) {
        if (Float.isNaN(min) || Float.isNaN(max) || min > max) {
            throw new IllegalArgumentException("Scatter height range must have min <= max: " + min + " to " + max);
        }
        this.minHeight = min;
        this.maxHeight = max;
    }

    /** The least distance between two objects, in world units. */
    public float getSpacing() {
        return spacing;
    }

    /** @throws IllegalArgumentException unless {@code 0 <= spacing <= }{@link #MAX_SPACING} */
    public void setSpacing(float spacing) {
        if (!(spacing >= 0f && spacing <= MAX_SPACING)) {
            throw new IllegalArgumentException("Scatter spacing must be 0 to " + MAX_SPACING + ": " + spacing);
        }
        this.spacing = spacing;
    }
}
