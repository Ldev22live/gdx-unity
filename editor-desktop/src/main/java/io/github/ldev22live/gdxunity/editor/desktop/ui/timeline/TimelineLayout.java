package io.github.ldev22live.gdxunity.editor.desktop.ui.timeline;

/**
 * Maps clip time to horizontal position in the Timeline's key area and picks ruler spacing. Pure math, kept apart
 * from drawing so it can be tested without a window.
 */
final class TimelineLayout {

    /** Ruler label spacings tried in order, in seconds. */
    private static final float[] LABEL_STEPS = {0.05f, 0.1f, 0.25f, 0.5f, 1f, 2f, 5f, 10f, 30f, 60f};
    /** Labels closer than this many pixels would overlap. */
    static final float MIN_LABEL_SPACING = 56f;
    /** Frame ticks are drawn only when frames are at least this many pixels apart. */
    static final float MIN_FRAME_TICK_SPACING = 5f;

    private final float left;
    private final float width;
    private final float duration;

    /**
     * @param left     x of time 0
     * @param width    pixels from time 0 to the end of the clip
     * @param duration clip length in seconds
     */
    TimelineLayout(float left, float width, float duration) {
        if (!(width > 0f) || !(duration > 0f)) {
            throw new IllegalArgumentException("width and duration must be positive, got " + width + ", " + duration);
        }
        this.left = left;
        this.width = width;
        this.duration = duration;
    }

    float xOf(float time) {
        return left + time / duration * width;
    }

    /** Time under {@code x}, clamped to the clip. */
    float timeAt(float x) {
        return Math.max(0f, Math.min(duration, (x - left) / width * duration));
    }

    float pixelsPerSecond() {
        return width / duration;
    }

    /** The smallest label step whose labels are at least {@link #MIN_LABEL_SPACING} apart. */
    float labelStep() {
        for (float step : LABEL_STEPS) {
            if (step * pixelsPerSecond() >= MIN_LABEL_SPACING) {
                return step;
            }
        }
        return LABEL_STEPS[LABEL_STEPS.length - 1];
    }

    boolean showFrameTicks(int frameRate) {
        return pixelsPerSecond() / frameRate >= MIN_FRAME_TICK_SPACING;
    }
}
