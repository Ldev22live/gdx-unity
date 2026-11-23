package io.github.ldev22live.gdxunity.engine.terrain;

import java.util.Objects;

/** What a terrain brush does to the heights under it (TD-082). */
public final class TerrainBrush {
    /** The tools of a stroke. */
    public enum Type {
        /** Pushes the ground up (down when inverted). */
        RAISE,
        /** Pushes the ground down (up when inverted). */
        LOWER,
        /** Moves each height towards the average of its neighbours. */
        SMOOTH,
        /** Moves each height towards the height where the stroke began. */
        FLATTEN,
        /** Adds a rough, repeatable bumpiness. */
        NOISE,
        /**
         * Paints the brush's {@link Surface} onto the points under it as a layer weight that fades out towards the brush's edge, so
         * a patch has a soft border (TD-089; it was a hard value per point in TD-083). Inverted, it wipes the paint back to the base
         * ground. The heights stay as they are.
         */
        PAINT,
        /** Cuts a hole in the ground (fills holes when inverted): nothing is drawn or collides there (TD-089). */
        HOLE,
        /**
         * Wears steep ground down (roadmap M3.5): where the ground drops more steeply than {@link TerrainStroke#TALUS_SLOPE} to a
         * neighbour, part of the excess slides down to it, so cliffs soften into slopes and the material piles at their foot. The
         * ground is moved, not removed.
         */
        ERODE,
        /**
         * A ramp between two points (roadmap M3.5): the stroke runs from where the button goes down to where it comes up, and the
         * ground along that line, as wide as the brush, becomes an even slope from the height at the start to the height at the end,
         * blending into the ground beside it.
         */
        RAMP
    }

    /** Largest brush radius, in world units. */
    public static final float MAX_RADIUS = 100_000f;
    public static final float DEFAULT_RADIUS = 300f;
    public static final float DEFAULT_STRENGTH = 0.5f;

    private Type type = Type.RAISE;
    private float radius = DEFAULT_RADIUS;
    private float strength = DEFAULT_STRENGTH;
    private boolean inverted;
    private Surface surface = Surface.GRASS;

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
    }

    /** Distance from the centre at which the brush stops acting, in world units. */
    public float getRadius() {
        return radius;
    }

    /** @throws IllegalArgumentException unless above 0 and at most {@value #MAX_RADIUS} */
    public void setRadius(float radius) {
        if (!(radius > 0f && radius <= MAX_RADIUS)) {
            throw new IllegalArgumentException("Brush radius must be above 0 and at most " + MAX_RADIUS + ": " + radius);
        }
        this.radius = radius;
    }

    /** How much each dab does, 0 to 1. */
    public float getStrength() {
        return strength;
    }

    /** @throws IllegalArgumentException if not within 0 to 1 */
    public void setStrength(float strength) {
        if (!(strength >= 0f && strength <= 1f)) {
            throw new IllegalArgumentException("Brush strength must be within 0 to 1: " + strength);
        }
        this.strength = strength;
    }

    /** Raise acts as lower and the other way round while this is on (the Ctrl key in the viewport). */
    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }

    /** The ground the {@link Type#PAINT} tool lays down. */
    public Surface getSurface() {
        return surface;
    }

    public void setSurface(Surface surface) {
        this.surface = Objects.requireNonNull(surface, "surface cannot be null");
    }

    /** A copy that shares nothing with this brush. */
    public TerrainBrush copy() {
        TerrainBrush copy = new TerrainBrush();
        copy.type = type;
        copy.radius = radius;
        copy.strength = strength;
        copy.inverted = inverted;
        copy.surface = surface;
        return copy;
    }
}
