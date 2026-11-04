package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

/**
 * Screen-space text for scores, timers and messages (TD-034), drawn on top of the scene by
 * {@link io.github.ldev22live.gdxunity.engine.render.HudRenderer}. The text is placed relative to an
 * {@link Anchor} of the view, shifted by {@link #getOffsetX()} / {@link #getOffsetY()} pixels (x to the right, y up).
 * The entity's transform is not used.
 */
public class TextComponent extends BaseComponent {

    /** Where in the view the text is placed; the text is aligned to the same side. */
    public enum Anchor {
        TOP_LEFT(0f, 1f), TOP(0.5f, 1f), TOP_RIGHT(1f, 1f),
        LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), RIGHT(1f, 0.5f),
        BOTTOM_LEFT(0f, 0f), BOTTOM(0.5f, 0f), BOTTOM_RIGHT(1f, 0f);

        /** 0 = left edge, 0.5 = centre, 1 = right edge; for both the view and the text. */
        private final float horizontal;
        /** 0 = bottom edge, 0.5 = middle, 1 = top edge; for both the view and the text. */
        private final float vertical;

        Anchor(float horizontal, float vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        /** Left edge of text {@code textWidth} wide, before the offset. */
        public float textLeft(float viewWidth, float textWidth) {
            return (viewWidth - textWidth) * horizontal;
        }

        /** Top edge of text {@code textHeight} high, before the offset. */
        public float textTop(float viewHeight, float textHeight) {
            return textHeight + (viewHeight - textHeight) * vertical;
        }
    }

    /** Default size multiplier of the built-in 15-pixel font. */
    public static final float DEFAULT_SCALE = 1.5f;
    /** Default distance from the anchored edges, in pixels. */
    public static final float DEFAULT_MARGIN = 16f;

    private String text;
    private final Color color = new Color(Color.WHITE);
    private float scale = DEFAULT_SCALE;
    private Anchor anchor = Anchor.TOP_LEFT;
    private float offsetX = DEFAULT_MARGIN;
    private float offsetY = -DEFAULT_MARGIN;

    public TextComponent() {
        this("Text");
    }

    public TextComponent(String text) {
        setText(text);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        this.text = text;
    }

    /** Returns the live color; prefer {@link #setColor(Color)} to change it. */
    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        if (color == null) {
            throw new IllegalArgumentException("color cannot be null");
        }
        this.color.set(color);
    }

    public float getScale() {
        return scale;
    }

    /** @throws IllegalArgumentException unless the scale is positive and finite */
    public void setScale(float scale) {
        if (!(scale > 0f) || Float.isInfinite(scale)) {
            throw new IllegalArgumentException("Text scale must be > 0: " + scale);
        }
        this.scale = scale;
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public void setAnchor(Anchor anchor) {
        if (anchor == null) {
            throw new IllegalArgumentException("anchor cannot be null");
        }
        this.anchor = anchor;
    }

    public float getOffsetX() {
        return offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    /** Pixels from the anchor: x to the right, y up. */
    public void setOffset(float offsetX, float offsetY) {
        if (!Float.isFinite(offsetX) || !Float.isFinite(offsetY)) {
            throw new IllegalArgumentException("Offset must be finite: " + offsetX + ", " + offsetY);
        }
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }
}
