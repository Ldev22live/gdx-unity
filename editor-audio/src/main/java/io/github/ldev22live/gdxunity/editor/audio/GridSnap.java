package io.github.ldev22live.gdxunity.editor.audio;

import io.github.ldev22live.gdxunity.engine.audio.song.Song;

/** How finely the piano roll and playlist snap positions and lengths. */
public enum GridSnap {
    NONE("Off", 1),
    BAR("Bar", Song.TICKS_PER_BAR),
    BEAT("1/4", Song.TICKS_PER_BEAT),
    EIGHTH("1/8", Song.TICKS_PER_BEAT / 2),
    EIGHTH_TRIPLET("1/8T", Song.TICKS_PER_BEAT / 3),
    SIXTEENTH("1/16", Song.TICKS_PER_BEAT / 4),
    SIXTEENTH_TRIPLET("1/16T", Song.TICKS_PER_BEAT / 6),
    THIRTY_SECOND("1/32", Song.TICKS_PER_BEAT / 8);

    private final String label;
    private final int ticks;

    GridSnap(String label, int ticks) {
        this.label = label;
        this.ticks = ticks;
    }

    public String getLabel() {
        return label;
    }

    /** Size of one grid cell in ticks. */
    public int getTicks() {
        return ticks;
    }

    /** The grid line nearest to {@code tick} (halfway rounds up). */
    public int snap(int tick) {
        return Math.floorDiv(tick + ticks / 2, ticks) * ticks;
    }

    /** The grid line at or before {@code tick}. */
    public int floor(int tick) {
        return Math.floorDiv(tick, ticks) * ticks;
    }

    /** The next snap setting in the menu order, wrapping around. */
    public GridSnap next() {
        GridSnap[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
