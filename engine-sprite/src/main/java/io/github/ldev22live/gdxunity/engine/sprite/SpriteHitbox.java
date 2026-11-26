package io.github.ldev22live.gdxunity.engine.sprite;

/**
 * A named rectangle of one frame (TD-104), for gameplay: where a frame can hit or be hit. Canvas pixels, y down; it may reach past
 * the canvas (a sword swing wider than the sprite).
 *
 * @param name   what gameplay asks for, such as "hurt" or "attack"; unique within its frame
 * @param x      left edge
 * @param y      top edge
 * @param width  1 or more
 * @param height 1 or more
 */
public record SpriteHitbox(String name, int x, int y, int width, int height) {
    public SpriteHitbox {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A hitbox needs a name");
        }
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Hitbox " + name + " needs a positive size: " + width + "x" + height);
        }
    }
}
