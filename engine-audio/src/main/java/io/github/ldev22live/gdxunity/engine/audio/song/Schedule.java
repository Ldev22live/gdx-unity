package io.github.ldev22live.gdxunity.engine.audio.song;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Everything a song plays, flattened into a time-ordered list of note-on and note-off events with swing applied
 * and notes cut at the end of their pattern or clip. The player works from this, so it never touches the editable
 * song while it renders. Immutable.
 */
public final class Schedule {
    /** What to compile: one pattern on repeat, or the whole playlist. */
    public enum Mode { PATTERN, SONG }

    /** One note-on or note-off at a tick. */
    public record Event(int tick, int channelId, int pitch, float velocity, boolean on) { }

    /** The swing shift of an off-beat sixteenth is this share of a step at full swing. */
    private static final float SWING_STEP_SHARE = 1f / 3f;
    private static final Comparator<Event> ORDER = Comparator.comparingInt(Event::tick)
        .thenComparing(Event::on)
        .thenComparingInt(Event::channelId)
        .thenComparingInt(Event::pitch);

    private final List<Event> events;
    private final int lengthTicks;

    private Schedule(List<Event> events, int lengthTicks) {
        this.events = List.copyOf(events);
        this.lengthTicks = lengthTicks;
    }

    public List<Event> getEvents() {
        return events;
    }

    /** Length of the compiled music in ticks; looping wraps here. */
    public int getLengthTicks() {
        return lengthTicks;
    }

    /** An empty schedule of the given length. */
    public static Schedule empty(int lengthTicks) {
        return new Schedule(List.of(), lengthTicks);
    }

    /**
     * Compiles the song. In {@link Mode#PATTERN} {@code patternId} picks the pattern; it is ignored for
     * {@link Mode#SONG}.
     */
    public static Schedule compile(Song song, Mode mode, int patternId) {
        Objects.requireNonNull(song, "song cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");
        List<Event> events = new ArrayList<>();
        int length;
        if (mode == Mode.PATTERN) {
            Pattern pattern = song.getPattern(patternId);
            if (pattern == null) {
                throw new IllegalArgumentException("No pattern with id " + patternId);
            }
            addPattern(events, song, pattern, 0, pattern.getLengthTicks());
            length = pattern.getLengthTicks();
        } else {
            for (PlaylistClip clip : song.getClips()) {
                Pattern pattern = song.getPattern(clip.patternId());
                if (pattern == null) {
                    continue;
                }
                for (int offset = 0; offset < clip.lengthTicks(); offset += pattern.getLengthTicks()) {
                    int windowEnd = Math.min(clip.lengthTicks() - offset, pattern.getLengthTicks());
                    addPattern(events, song, pattern, clip.startTick() + offset, windowEnd);
                }
            }
            length = song.getPlaylistLengthTicks();
        }
        events.sort(ORDER);
        return new Schedule(events, length);
    }

    /** Adds a pattern's notes shifted by {@code origin}, ignoring anything at or after {@code windowEnd}. */
    private static void addPattern(List<Event> events, Song song, Pattern pattern, int origin, int windowEnd) {
        int swingShift = Math.round(song.getSwing() * Song.STEP_TICKS * SWING_STEP_SHARE);
        for (Channel channel : song.getChannels()) {
            if (channel.isMuted()) {
                continue;
            }
            for (Note note : pattern.getNotes(channel.getId())) {
                int start = note.tick();
                if ((start / Song.STEP_TICKS) % 2 == 1) {
                    start += swingShift;
                }
                if (start >= windowEnd) {
                    continue;
                }
                int end = Math.min(start + note.length(), windowEnd);
                events.add(new Event(origin + start, channel.getId(), note.pitch(), note.velocity(), true));
                events.add(new Event(origin + end, channel.getId(), note.pitch(), 0f, false));
            }
        }
    }
}
