package io.github.ldev22live.gdxunity.engine.audio.song;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The step sequencer's view of a pattern: each channel is a row of sixteenth-note steps, a step being on when the
 * channel has a note starting there. Returns new note lists; applying them is the caller's job (so it can be undone).
 */
public final class StepGrid {
    private StepGrid() { }

    /** Number of steps in a pattern. */
    public static int stepCount(Pattern pattern) {
        return pattern.getLengthTicks() / Song.STEP_TICKS;
    }

    /** The note that starts exactly on {@code step} (on the channel's step pitch), or null. */
    public static Note noteAt(Pattern pattern, Channel channel, int step) {
        int tick = step * Song.STEP_TICKS;
        for (Note note : pattern.getNotes(channel.getId())) {
            if (note.tick() == tick && note.pitch() == channel.getStepPitch()) {
                return note;
            }
        }
        return null;
    }

    public static boolean isStepOn(Pattern pattern, Channel channel, int step) {
        return noteAt(pattern, channel, step) != null;
    }

    /** The channel's notes with {@code step} switched: removed if it was on, added with {@code velocity} if off. */
    public static List<Note> toggled(Pattern pattern, Channel channel, int step, float velocity) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        Objects.requireNonNull(channel, "channel cannot be null");
        if (step < 0 || step >= stepCount(pattern)) {
            throw new IllegalArgumentException("Step " + step + " is outside the pattern (0.." + (stepCount(pattern) - 1) + ")");
        }
        List<Note> notes = new ArrayList<>(pattern.getNotes(channel.getId()));
        Note existing = noteAt(pattern, channel, step);
        if (existing != null) {
            notes.remove(existing);
        } else {
            notes.add(new Note(step * Song.STEP_TICKS, Song.STEP_TICKS, channel.getStepPitch(), velocity));
        }
        return notes;
    }

    /** The channel's notes with the velocity of the note on {@code step} changed; the same list if the step is off. */
    public static List<Note> withVelocity(Pattern pattern, Channel channel, int step, float velocity) {
        List<Note> notes = new ArrayList<>(pattern.getNotes(channel.getId()));
        Note existing = noteAt(pattern, channel, step);
        if (existing != null) {
            notes.set(notes.indexOf(existing), existing.withVelocity(velocity));
        }
        return notes;
    }
}
