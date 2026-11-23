package io.github.ldev22live.gdxunity.engine.audio.song;

/**
 * A pattern placed on the playlist. The pattern repeats if the clip is longer than the pattern.
 *
 * @param patternId   the pattern played
 * @param track       playlist row, 0 at the top
 * @param startTick   where the clip starts in the song
 * @param lengthTicks how long the clip lasts, at least one bar
 */
public record PlaylistClip(int patternId, int track, int startTick, int lengthTicks) {
    public PlaylistClip {
        if (track < 0) {
            throw new IllegalArgumentException("track must not be negative: " + track);
        }
        if (startTick < 0) {
            throw new IllegalArgumentException("startTick must not be negative: " + startTick);
        }
        if (lengthTicks < Song.TICKS_PER_BAR) {
            throw new IllegalArgumentException("A clip is at least one bar (" + Song.TICKS_PER_BAR + " ticks): " + lengthTicks);
        }
    }

    public int endTick() {
        return startTick + lengthTicks;
    }

    public PlaylistClip withStartTick(int newStart) {
        return new PlaylistClip(patternId, track, newStart, lengthTicks);
    }

    public PlaylistClip withTrack(int newTrack) {
        return new PlaylistClip(patternId, newTrack, startTick, lengthTicks);
    }

    public PlaylistClip withLengthTicks(int newLength) {
        return new PlaylistClip(patternId, track, startTick, newLength);
    }
}
