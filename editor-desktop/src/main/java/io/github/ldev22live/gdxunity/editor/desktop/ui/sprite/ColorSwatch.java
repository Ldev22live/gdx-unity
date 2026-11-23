package io.github.ldev22live.gdxunity.editor.desktop.ui.sprite;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;

import java.util.Objects;

/** A small square filled with one color ({@code 0xRRGGBBAA}), with an outline when marked; used for palette entries and the current color. */
final class ColorSwatch extends Widget {
    private static final float OUTLINE = 2f;

    private final Texture white;
    private final Color color = new Color();
    private boolean marked;
    private final float size;

    ColorSwatch(Texture white, int rgba, float size) {
        this.white = Objects.requireNonNull(white, "white cannot be null");
        this.size = size;
        setColorRgba(rgba);
    }

    void setColorRgba(int rgba) {
        color.set(rgba);
    }

    void setMarked(boolean marked) {
        this.marked = marked;
    }

    @Override
    public float getPrefWidth() {
        return size;
    }

    @Override
    public float getPrefHeight() {
        return size;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (marked) {
            batch.setColor(1f, 1f, 1f, parentAlpha);
            batch.draw(white, getX() - OUTLINE, getY() - OUTLINE, getWidth() + 2 * OUTLINE, getHeight() + 2 * OUTLINE);
            batch.setColor(0f, 0f, 0f, parentAlpha);
            batch.draw(white, getX(), getY(), getWidth(), getHeight());
        }
        // Transparent colors show as a dark cell so they can still be told apart from the background
        batch.setColor(0.2f, 0.2f, 0.2f, parentAlpha);
        batch.draw(white, getX(), getY(), getWidth(), getHeight());
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
        batch.draw(white, getX(), getY(), getWidth(), getHeight());
        batch.setColor(Color.WHITE);
    }
}
