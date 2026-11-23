package io.github.ldev22live.gdxunity.engine.sprite;

import java.util.Objects;

/**
 * Layer settings (the pixels live in the document's cels).
 *
 * @param name    layer name
 * @param visible whether the layer is drawn when flattening
 * @param opacity 0 (invisible) to 255 (opaque)
 */
public record SpriteLayer(String name, boolean visible, int opacity) {
    /** Largest opacity value. */
    public static final int OPAQUE = 255;

    public SpriteLayer {
        Objects.requireNonNull(name, "name cannot be null");
        if (opacity < 0 || opacity > OPAQUE) {
            throw new IllegalArgumentException("opacity must be 0..255 but was " + opacity);
        }
    }

    public SpriteLayer withName(String newName) {
        return new SpriteLayer(newName, visible, opacity);
    }

    public SpriteLayer withVisible(boolean newVisible) {
        return new SpriteLayer(name, newVisible, opacity);
    }

    public SpriteLayer withOpacity(int newOpacity) {
        return new SpriteLayer(name, visible, newOpacity);
    }
}
