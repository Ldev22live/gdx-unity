package io.github.ldev22live.gdxunity.engine.animation;

/** How a curve moves from a key to the next one. Set on the earlier key of each pair. */
public enum Interpolation {
    /** Holds the key's value until the next key (stepped). */
    CONSTANT("Stepped"),
    /** Straight line to the next key. */
    LINEAR("Linear"),
    /**
     * Smooth cubic curve. Tangents are automatic: flat at the first and last key (ease in / ease out), and through
     * the neighbouring keys elsewhere, so motion does not jerk at keys.
     */
    BEZIER("Bezier");

    private final String displayName;

    Interpolation(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
