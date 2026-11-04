package io.github.ldev22live.gdxunity.engine.sculpt;

import java.util.Objects;

/** The adjustable properties of a brush. A stroke copies them when it starts, so changing them mid-stroke is safe. */
public final class BrushSettings {
    /** Default brush radius, in mesh units. */
    public static final float DEFAULT_RADIUS = 0.25f;
    /** Default strength. */
    public static final float DEFAULT_STRENGTH = 0.5f;
    /** Default distance between dabs, as a fraction of the radius. */
    public static final float DEFAULT_SPACING = 0.15f;
    /** Smallest dab spacing, as a fraction of the radius, so a stroke cannot place thousands of dabs per move. */
    public static final float MIN_SPACING = 0.02f;
    /** Largest dab spacing, as a fraction of the radius. */
    public static final float MAX_SPACING = 2f;

    private float radius = DEFAULT_RADIUS;
    private float strength = DEFAULT_STRENGTH;
    private float spacing = DEFAULT_SPACING;
    private Falloff falloff = Falloff.SMOOTH;
    private boolean subtract;

    /** Brush radius in mesh units. */
    public float getRadius() {
        return radius;
    }

    /**
     * @param radius greater than 0 and finite
     * @throws IllegalArgumentException otherwise
     */
    public void setRadius(float radius) {
        if (!(radius > 0f) || !Float.isFinite(radius)) {
            throw new IllegalArgumentException("radius must be a finite number greater than 0: " + radius);
        }
        this.radius = radius;
    }

    /** Strength from 0 to 1: how much a single dab moves the surface. */
    public float getStrength() {
        return strength;
    }

    /**
     * @param strength from 0 to 1
     * @throws IllegalArgumentException otherwise
     */
    public void setStrength(float strength) {
        if (!(strength >= 0f && strength <= 1f)) {
            throw new IllegalArgumentException("strength must be between 0 and 1: " + strength);
        }
        this.strength = strength;
    }

    /** Distance between dabs as a fraction of the radius. */
    public float getSpacing() {
        return spacing;
    }

    /**
     * @param spacing from {@link #MIN_SPACING} to {@link #MAX_SPACING}
     * @throws IllegalArgumentException otherwise
     */
    public void setSpacing(float spacing) {
        if (!(spacing >= MIN_SPACING && spacing <= MAX_SPACING)) {
            throw new IllegalArgumentException("spacing must be between " + MIN_SPACING + " and " + MAX_SPACING
                + ": " + spacing);
        }
        this.spacing = spacing;
    }

    /** How the effect fades towards the edge. */
    public Falloff getFalloff() {
        return falloff;
    }

    /** Sets how the effect fades towards the edge. */
    public void setFalloff(Falloff falloff) {
        this.falloff = Objects.requireNonNull(falloff, "falloff cannot be null");
    }

    /** Whether the brush works in the opposite direction (digs instead of builds, spreads instead of pinches). */
    public boolean isSubtract() {
        return subtract;
    }

    /** Sets whether the brush works in the opposite direction. Smooth, flatten and grab ignore it. */
    public void setSubtract(boolean subtract) {
        this.subtract = subtract;
    }

    /** A copy with the same values. */
    public BrushSettings copy() {
        BrushSettings copy = new BrushSettings();
        copy.radius = radius;
        copy.strength = strength;
        copy.spacing = spacing;
        copy.falloff = falloff;
        copy.subtract = subtract;
        return copy;
    }
}
