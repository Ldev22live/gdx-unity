package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.math.Vector3;

/**
 * What a pane of the 3D scene view shows (TD-039): the free perspective "world" view, or an orthographic view
 * looking straight down one axis, as in the multi-view layouts of 3D modelling tools.
 */
public enum ViewKind {
    /** The free perspective camera: fly, orbit and pan. */
    PERSPECTIVE("Perspective", null, null),
    /** Looks along the X axis (from +X towards -X): the screen shows Z across and Y up. */
    X_AXIS("X axis", new Vector3(-1f, 0f, 0f), new Vector3(0f, 1f, 0f)),
    /** Looks along the Y axis (from above, +Y towards -Y): the screen shows X across and Z down the screen. */
    Y_AXIS("Y axis", new Vector3(0f, -1f, 0f), new Vector3(0f, 0f, -1f)),
    /** Looks along the Z axis (from +Z towards -Z): the screen shows X across and Y up. */
    Z_AXIS("Z axis", new Vector3(0f, 0f, -1f), new Vector3(0f, 1f, 0f));

    private final String displayName;
    private final Vector3 direction;
    private final Vector3 up;

    ViewKind(String displayName, Vector3 direction, Vector3 up) {
        this.displayName = displayName;
        this.direction = direction;
        this.up = up;
    }

    /** The name shown in a pane's header. */
    public String getDisplayName() {
        return displayName;
    }

    /** True for the three axis views, which use an orthographic camera. */
    public boolean isOrthographic() {
        return direction != null;
    }

    /**
     * The direction an axis view looks in, as a new vector.
     *
     * @throws IllegalStateException for {@link #PERSPECTIVE}, which has no fixed direction
     */
    public Vector3 getDirection() {
        return new Vector3(requireOrthographic(direction));
    }

    /**
     * The screen's up direction in an axis view, as a new vector.
     *
     * @throws IllegalStateException for {@link #PERSPECTIVE}
     */
    public Vector3 getUp() {
        return new Vector3(requireOrthographic(up));
    }

    private Vector3 requireOrthographic(Vector3 value) {
        if (value == null) {
            throw new IllegalStateException(displayName + " has no fixed direction");
        }
        return value;
    }

    /** The next view in the order Perspective, X, Y, Z, for a header that cycles through the views. */
    public ViewKind next() {
        ViewKind[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
