package io.github.ldev22live.gdxunity.editor.desktop.ui.profiler;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.ldev22live.gdxunity.editor.desktop.ui.music.ShapeWidget;
import io.github.ldev22live.gdxunity.engine.core.profile.FrameProfiler;

import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The Profiler's frame history (roadmap M9.6): one bar per recorded frame, newest on the right. The solid part is the frame loop's
 * work, the faint part above it the waiting until the next frame; lines mark 60 and 30 frames a second. The scale is fixed, so one
 * slow frame (a scene loading) does not flatten the rest: a bar taller than the graph is cut off at the top and drawn in the warning
 * colour. Clicking a bar picks that frame for the breakdown.
 */
final class FrameHistoryGraph extends ShapeWidget {
    /** The graph's full height: three frames at 60 frames a second, so the 60 and 30 lines sit at a third and two thirds. */
    private static final double SCALE_MILLIS = 50.0;
    /** One frame at 60 frames a second. */
    private static final double FRAME_60_MILLIS = 1000.0 / 60.0;
    /** One frame at 30 frames a second. */
    private static final double FRAME_30_MILLIS = 1000.0 / 30.0;
    private static final float PREF_HEIGHT = 60f;
    private static final float LABEL_WIDTH = 64f;
    private static final float GAP = 1f;
    /** Opacity of the waiting part of a bar. */
    private static final float WAITING_ALPHA = 0.35f;

    private final FrameProfiler profiler;
    private final IntSupplier selected;
    private final IntConsumer onPick;
    /** The faint colour of the waiting part, kept so drawing allocates nothing. */
    private final Color waiting = new Color();

    /**
     * @param selected the frame shown in the breakdown, in frames ago
     * @param onPick   called with the frames-ago index of a clicked bar
     */
    FrameHistoryGraph(Skin skin, FrameProfiler profiler, IntSupplier selected, IntConsumer onPick) {
        super(skin);
        this.profiler = Objects.requireNonNull(profiler, "profiler cannot be null");
        this.selected = Objects.requireNonNull(selected, "selected cannot be null");
        this.onPick = Objects.requireNonNull(onPick, "onPick cannot be null");
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                int framesAgo = frameAt(x);
                if (framesAgo >= 0) {
                    onPick.accept(framesAgo);
                }
                return true;
            }
        });
    }

    @Override
    public float getPrefHeight() {
        return PREF_HEIGHT;
    }

    @Override
    public float getPrefWidth() {
        return 200f;
    }

    private float barWidth() {
        return (getWidth() - LABEL_WIDTH) / profiler.getHistorySize();
    }

    /** The frames-ago index under {@code x}, or -1. */
    private int frameAt(float x) {
        int frames = profiler.getFrameCount();
        if (frames == 0 || x < LABEL_WIDTH) {
            return -1;
        }
        int fromRight = (int) ((getWidth() - x) / barWidth());
        return fromRight < frames ? fromRight : -1;
    }

    /** The height of {@code millis} on the graph, cut off at the top. */
    private float heightOf(double millis) {
        return (float) (Math.min(millis, SCALE_MILLIS) / SCALE_MILLIS * (getHeight() - 2f));
    }

    @Override
    protected void drawFilled(ShapeRenderer shapes) {
        shapes.setColor(color("surface"));
        shapes.rect(0f, 0f, getWidth(), getHeight());
        int frames = profiler.getFrameCount();
        if (frames == 0) {
            return;
        }
        float bar = barWidth();
        int picked = selected.getAsInt();
        Color work = color("accent");
        waiting.set(color("text-muted")).a = WAITING_ALPHA;
        Color slow = color("warning");
        for (int i = 0; i < frames; i++) {
            float x = getWidth() - (i + 1) * bar;
            double workMillis = profiler.getFrameNanos(i) / 1_000_000.0;
            double periodMillis = profiler.getPeriodNanos(i) / 1_000_000.0;
            float workHeight = heightOf(workMillis);
            float w = Math.max(1f, bar - GAP);
            if (i == picked) {
                shapes.setColor(color("surface-elevated"));
                shapes.rect(x, 0f, w, getHeight());
            }
            shapes.setColor(workMillis > FRAME_60_MILLIS || periodMillis > SCALE_MILLIS ? slow : work);
            shapes.rect(x, 1f, w, workHeight);
            if (periodMillis > workMillis) {
                shapes.setColor(waiting);
                shapes.rect(x, 1f + workHeight, w, heightOf(periodMillis) - workHeight);
            }
        }
    }

    @Override
    protected void drawLines(ShapeRenderer shapes) {
        shapes.setColor(color("border"));
        shapes.line(LABEL_WIDTH, 1f + heightOf(FRAME_60_MILLIS), getWidth(), 1f + heightOf(FRAME_60_MILLIS));
        shapes.line(LABEL_WIDTH, 1f + heightOf(FRAME_30_MILLIS), getWidth(), 1f + heightOf(FRAME_30_MILLIS));
        int picked = selected.getAsInt();
        if (picked >= 0 && picked < profiler.getFrameCount()) {
            float x = getWidth() - (picked + 1) * barWidth();
            shapes.setColor(color("text-primary"));
            shapes.rect(x, 0f, Math.max(1f, barWidth() - GAP), getHeight() - 1f);
        }
    }

    @Override
    protected void drawText(Batch batch, BitmapFont font, float baseX, float baseY) {
        Color muted = color("text-muted");
        text(batch, font, "60 fps", muted, baseX, baseY, 4f, 1f + heightOf(FRAME_60_MILLIS) + font.getCapHeight() / 2f);
        text(batch, font, "30 fps", muted, baseX, baseY, 4f, 1f + heightOf(FRAME_30_MILLIS) + font.getCapHeight() / 2f);
        if (profiler.getFrameCount() == 0) {
            text(batch, font, "No frames recorded yet", muted, baseX, baseY, LABEL_WIDTH + 8f, getHeight() / 2f + font.getCapHeight() / 2f);
        }
    }
}
