package io.github.ldev22live.gdxunity.editor.core;

/**
 * Projection used by the editor scene view.
 */
public enum ViewportMode {
    /** Orthographic top-down view of the XY plane; 2D tools and panning. */
    VIEW_2D,
    /** Perspective view with free 3D navigation; 2D content appears as flat panels in the XY plane. */
    VIEW_3D;

    /** The other mode. */
    public ViewportMode toggled() {
        return this == VIEW_2D ? VIEW_3D : VIEW_2D;
    }
}
