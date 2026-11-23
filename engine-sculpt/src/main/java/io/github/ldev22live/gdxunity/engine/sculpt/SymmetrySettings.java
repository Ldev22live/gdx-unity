package io.github.ldev22live.gdxunity.engine.sculpt;

/**
 * Mirror planes through the mesh origin (its local space). With an axis enabled, every dab is repeated on the other
 * side of that plane, and with several axes enabled on every combination of sides.
 */
public final class SymmetrySettings {
    private boolean mirrorX;
    private boolean mirrorY;
    private boolean mirrorZ;

    /** Creates settings with no mirroring. */
    public SymmetrySettings() {
    }

    /** Creates settings with the given axes mirrored. */
    public SymmetrySettings(boolean mirrorX, boolean mirrorY, boolean mirrorZ) {
        this.mirrorX = mirrorX;
        this.mirrorY = mirrorY;
        this.mirrorZ = mirrorZ;
    }

    /** Whether the plane x = 0 is a mirror. */
    public boolean isMirrorX() {
        return mirrorX;
    }

    /** Sets whether the plane x = 0 is a mirror. */
    public void setMirrorX(boolean mirrorX) {
        this.mirrorX = mirrorX;
    }

    /** Whether the plane y = 0 is a mirror. */
    public boolean isMirrorY() {
        return mirrorY;
    }

    /** Sets whether the plane y = 0 is a mirror. */
    public void setMirrorY(boolean mirrorY) {
        this.mirrorY = mirrorY;
    }

    /** Whether the plane z = 0 is a mirror. */
    public boolean isMirrorZ() {
        return mirrorZ;
    }

    /** Sets whether the plane z = 0 is a mirror. */
    public void setMirrorZ(boolean mirrorZ) {
        this.mirrorZ = mirrorZ;
    }

    /** The enabled axes as a bit mask: 1 for X, 2 for Y, 4 for Z. */
    int axisMask() {
        return (mirrorX ? 1 : 0) | (mirrorY ? 2 : 0) | (mirrorZ ? 4 : 0);
    }

    /** A copy with the same values. */
    public SymmetrySettings copy() {
        return new SymmetrySettings(mirrorX, mirrorY, mirrorZ);
    }
}
