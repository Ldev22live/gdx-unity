package io.github.ldev22live.gdxunity.editor.desktop.ui.music;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Disposable;

import java.util.Objects;

/**
 * A widget drawn with filled shapes, outlines and text in the editor's colours, like the Timeline and graph canvases.
 * Subclasses draw in widget coordinates (origin at the bottom left) in three passes.
 */
abstract class ShapeWidget extends Widget implements Disposable {
    protected final Skin skin;
    private final ShapeRenderer shapes = new ShapeRenderer();

    protected ShapeWidget(Skin skin) {
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
    }

    /** Filled shapes, drawn first. */
    protected abstract void drawFilled(ShapeRenderer shapes);

    /** Outlines and lines, drawn over the filled shapes. */
    protected void drawLines(ShapeRenderer shapes) { }

    /** Text, drawn last; {@code baseX/baseY} is the widget's position on the stage. */
    protected void drawText(Batch batch, BitmapFont font, float baseX, float baseY) { }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        // Text and shapes the parent drew before this widget are still waiting in the batch; clipping first would
        // cut them to this widget's bounds when they are finally sent
        batch.flush();
        if (!clipBegin()) {
            return;
        }
        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(getStage().getCamera().combined);
        shapes.setTransformMatrix(batch.getTransformMatrix());
        shapes.translate(getX(), getY(), 0f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawFilled(shapes);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawLines(shapes);
        shapes.end();
        batch.begin();
        BitmapFont font = skin.getFont("default");
        drawText(batch, font, getX(), getY());
        font.setColor(Color.WHITE);
        batch.flush();
        clipEnd();
    }

    protected Color color(String name) {
        return skin.getColor(name);
    }

    /** Draws text with its baseline {@code y} above the widget bottom. */
    protected void text(Batch batch, BitmapFont font, String text, Color color, float baseX, float baseY, float x, float y) {
        font.setColor(color);
        font.draw(batch, text, baseX + x, baseY + y);
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
