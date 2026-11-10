package io.github.ldev22live.gdxunity.engine.scene;

/**
 * What kind of game a scene is (TD-067): flat 2D, 2.5D (a 3D world played from a fixed side-on view) or full 3D.
 * The editor uses it to choose the scene view, which components and Create entries it offers and how new scenes start.
 */
public enum GameMode {
    /** Orthographic XY view, sprites, tile maps and Box2D physics. */
    MODE_2D("2D", "2d"),
    /** 3D content and physics seen from a fixed side view: the camera pans and zooms but does not orbit. */
    MODE_2_5D("2.5D", "2.5d"),
    /** Free 3D navigation with 3D physics, models and lighting. */
    MODE_3D("3D", "3d");

    private final String label;
    private final String fileValue;

    GameMode(String label, String fileValue) {
        this.label = label;
        this.fileValue = fileValue;
    }

    /** The name shown in menus. */
    public String getLabel() {
        return label;
    }

    /** The value written to scene files. */
    public String getFileValue() {
        return fileValue;
    }

    /** The mode with this file value, or null when it is not one. */
    public static GameMode fromFileValue(String value) {
        for (GameMode mode : values()) {
            if (mode.fileValue.equals(value)) {
                return mode;
            }
        }
        return null;
    }
}
