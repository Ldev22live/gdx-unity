package io.github.ldev22live.gdxunity.editor.desktop.ui.music;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.TimeUtils;

import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A fader or slider: a filled bar showing a value between a minimum and a maximum, with its name and value as text.
 * Dragging changes the value live; letting go reports the change once so it becomes one undo step. A double-click
 * returns to the default.
 */
final class ValueBar extends ShapeWidget {
    private static final long DOUBLE_CLICK_MILLIS = 400L;
    private static final float MIN_WIDTH = 120f;
    private static final float MIN_HEIGHT = 18f;
    private static final float VERTICAL_WIDTH = 34f;
    private static final float VERTICAL_HEIGHT = 130f;
    private static final float PADDING = 3f;

    private final String label;
    private final float min;
    private final float max;
    private final float defaultValue;
    private final boolean vertical;
    private final boolean logarithmic;
    private final String format;
    private final Supplier<Float> source;
    private final Consumer<Float> live;
    private final BiConsumer<Float, Float> commit;
    private float value;
    private float dragStart;
    private boolean dragging;
    private long lastClickMillis;

    /**
     * @param source  the current value, asked every frame while not dragging so the bar follows undo and redo
     * @param live    applies the value while dragging
     * @param commit  called once on release with the value before and after the drag
     */
    ValueBar(Skin skin, String label, float min, float max, float defaultValue, boolean vertical, boolean logarithmic,
             String format, Supplier<Float> source, Consumer<Float> live, BiConsumer<Float, Float> commit) {
        super(skin);
        if (!(max > min)) {
            throw new IllegalArgumentException("max must be above min: " + min + ".." + max);
        }
        if (logarithmic && min <= 0f) {
            throw new IllegalArgumentException("A logarithmic bar needs a positive minimum: " + min);
        }
        this.label = Objects.requireNonNull(label, "label cannot be null");
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.vertical = vertical;
        this.logarithmic = logarithmic;
        this.format = Objects.requireNonNull(format, "format cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.live = Objects.requireNonNull(live, "live cannot be null");
        this.commit = Objects.requireNonNull(commit, "commit cannot be null");
        this.value = source.get();
        addListener(new BarInput());
    }

    @Override
    public float getPrefWidth() {
        return vertical ? VERTICAL_WIDTH : MIN_WIDTH;
    }

    @Override
    public float getPrefHeight() {
        return vertical ? VERTICAL_HEIGHT : MIN_HEIGHT;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (!dragging) {
            value = source.get();
        }
    }

    /** Position of a value along the bar, 0..1. */
    private float fraction(float v) {
        float clamped = Math.min(Math.max(v, min), max);
        return logarithmic
            ? (float) (Math.log(clamped / min) / Math.log(max / min))
            : (clamped - min) / (max - min);
    }

    private float valueAt(float fraction) {
        float f = Math.min(Math.max(fraction, 0f), 1f);
        return logarithmic ? (float) (min * Math.pow(max / min, f)) : min + f * (max - min);
    }

    private float fractionAt(float x, float y) {
        return vertical ? (y - PADDING) / (getHeight() - 2 * PADDING) : (x - PADDING) / (getWidth() - 2 * PADDING);
    }

    @Override
    protected void drawFilled(ShapeRenderer s) {
        s.setColor(color("surface-variant"));
        s.rect(0f, 0f, getWidth(), getHeight());
        s.setColor(dragging ? color("accent-active") : color("accent"));
        float f = fraction(value);
        // A bar that spans zero (pan) grows from the centre, so left and right read differently
        float origin = min < 0f && max > 0f ? fraction(0f) : 0f;
        float from = Math.min(origin, f);
        float length = Math.abs(f - origin);
        if (vertical) {
            float span = getHeight() - 2 * PADDING;
            s.rect(PADDING, PADDING + span * from, getWidth() - 2 * PADDING, span * length);
        } else {
            float span = getWidth() - 2 * PADDING;
            s.rect(PADDING + span * from, PADDING, span * length, getHeight() - 2 * PADDING);
        }
    }

    @Override
    protected void drawLines(ShapeRenderer s) {
        s.setColor(color("border"));
        s.rect(0f, 0f, getWidth(), getHeight());
    }

    @Override
    protected void drawText(Batch batch, BitmapFont font, float baseX, float baseY) {
        String shown = vertical ? String.format(Locale.ROOT, format, value)
            : label + "  " + String.format(Locale.ROOT, format, value);
        Color c = color("text-primary");
        if (vertical) {
            text(batch, font, shown, c, baseX, baseY, 2f, getHeight() - 4f);
        } else {
            text(batch, font, shown, c, baseX, baseY, 6f, getHeight() / 2f + 5f);
        }
    }

    private final class BarInput extends InputListener {
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            if (button != Input.Buttons.LEFT) {
                return false;
            }
            long now = TimeUtils.millis();
            boolean doubleClick = now - lastClickMillis <= DOUBLE_CLICK_MILLIS;
            lastClickMillis = doubleClick ? 0L : now;
            dragStart = source.get();
            dragging = true;
            if (doubleClick) {
                value = defaultValue;
                live.accept(value);
                finish();
                return true;
            }
            update(x, y);
            return true;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            if (dragging) {
                update(x, y);
            }
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            if (dragging) {
                finish();
            }
        }

        private void update(float x, float y) {
            value = valueAt(fractionAt(x, y));
            live.accept(value);
        }

        private void finish() {
            dragging = false;
            commit.accept(dragStart, value);
        }
    }
}
