package io.github.ldev22live.gdxunity.engine.terrain;

/**
 * The ground a grid point of a {@link Terrain} is made of (TD-083). It decides the colour the renderer gives the point; the heights
 * decide the shape. The code of each surface is its position here and is what the {@code .terrain} file stores, so new surfaces go at
 * the end and none is reordered or removed.
 */
public enum Surface {
    /** No surface chosen: the colour follows the ground itself, grass on gentle slopes, rock on steep ones and snow on the heights. */
    AUTO("Auto", 0.28f, 0.48f, 0.17f),
    GRASS("Grass", 0.30f, 0.52f, 0.18f),
    SAND("Sand", 0.80f, 0.71f, 0.46f),
    SNOW("Snow", 0.92f, 0.94f, 0.97f),
    GRAVEL("Gravel", 0.50f, 0.48f, 0.45f);

    private final String label;
    private final float red;
    private final float green;
    private final float blue;

    Surface(String label, float red, float green, float blue) {
        this.label = label;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    /** Name shown in the editor. */
    public String getLabel() {
        return label;
    }

    /** One channel (0 red, 1 green, 2 blue) of the surface's colour in sRGB, 0 to 1. */
    public float channel(int channel) {
        return switch (channel) {
            case 0 -> red;
            case 1 -> green;
            case 2 -> blue;
            default -> throw new IllegalArgumentException("A colour has channels 0 to 2: " + channel);
        };
    }

    /** The code stored in a file. */
    public byte code() {
        return (byte) ordinal();
    }

    /**
     * @throws IllegalArgumentException if the code names no surface
     */
    public static Surface fromCode(int code) {
        Surface[] all = values();
        if (code < 0 || code >= all.length) {
            throw new IllegalArgumentException("Unknown surface code " + code + " (0 to " + (all.length - 1) + ")");
        }
        return all[code];
    }
}
