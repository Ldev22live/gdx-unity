package io.github.ldev22live.gdxunity.editor.audio;

import io.github.ldev22live.gdxunity.engine.audio.song.Note;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Piano roll edits as pure functions: each takes the channel's notes and returns the new list (and, where the notes
 * change, the new selection). Notes are immutable values, so a selection is a set of notes. Applying the result
 * to the song, as one undo step, is {@link MusicDocument}'s job.
 */
public final class PianoRollOps {
    private PianoRollOps() { }

    /** New notes together with the selection that follows the edit. */
    public record Result(List<Note> notes, Set<Note> selection) { }

    /** The note covering the point (tick, pitch), the latest-starting one if several, or null. */
    public static Note noteAt(List<Note> notes, int tick, int pitch) {
        Note found = null;
        for (Note note : notes) {
            if (note.pitch() == pitch && tick >= note.tick() && tick < note.endTick()
                && (found == null || note.tick() > found.tick())) {
                found = note;
            }
        }
        return found;
    }

    /** Notes that touch the rectangle: starting before {@code tickTo}, ending after {@code tickFrom}, pitch within range. */
    public static Set<Note> notesIn(List<Note> notes, int tickFrom, int tickTo, int pitchLow, int pitchHigh) {
        int low = Math.min(pitchLow, pitchHigh);
        int high = Math.max(pitchLow, pitchHigh);
        int from = Math.min(tickFrom, tickTo);
        int to = Math.max(tickFrom, tickTo);
        Set<Note> inside = new LinkedHashSet<>();
        for (Note note : notes) {
            if (note.pitch() >= low && note.pitch() <= high && note.tick() < to && note.endTick() > from) {
                inside.add(note);
            }
        }
        return inside;
    }

    /** Adds a note; a note with the same start and pitch is replaced rather than doubled. */
    public static Result add(List<Note> notes, Note added) {
        Objects.requireNonNull(added, "added cannot be null");
        List<Note> result = new ArrayList<>(notes);
        result.removeIf(n -> n.tick() == added.tick() && n.pitch() == added.pitch());
        result.add(added);
        return new Result(result, Set.of(added));
    }

    /** Removes the selected notes. */
    public static List<Note> delete(List<Note> notes, Set<Note> selection) {
        List<Note> result = new ArrayList<>(notes);
        result.removeAll(selection);
        return result;
    }

    /**
     * Moves the selected notes together. The move is limited so the whole group stays inside tick 0 and pitches
     * 0..127. Moved notes replace unselected notes they land exactly on.
     */
    public static Result move(List<Note> notes, Set<Note> selection, int deltaTicks, int deltaPitch) {
        int dt = deltaTicks;
        int dp = deltaPitch;
        for (Note note : selection) {
            dt = Math.max(dt, -note.tick());
            dp = Math.max(dp, Note.MIN_PITCH - note.pitch());
            dp = Math.min(dp, Note.MAX_PITCH - note.pitch());
        }
        List<Note> result = new ArrayList<>();
        Set<Note> moved = new LinkedHashSet<>();
        for (Note note : selection) {
            moved.add(new Note(note.tick() + dt, note.length(), note.pitch() + dp, note.velocity()));
        }
        for (Note note : notes) {
            if (selection.contains(note)) {
                continue;
            }
            boolean covered = false;
            for (Note m : moved) {
                covered |= m.tick() == note.tick() && m.pitch() == note.pitch();
            }
            if (!covered) {
                result.add(note);
            }
        }
        result.addAll(moved);
        return new Result(result, moved);
    }

    /** Gives one note a new length (at least 1 tick). */
    public static Result resize(List<Note> notes, Note target, int newLength) {
        Note resized = target.withLength(Math.max(newLength, 1));
        List<Note> result = new ArrayList<>(notes);
        int index = result.indexOf(target);
        if (index >= 0) {
            result.set(index, resized);
        }
        return new Result(result, Set.of(resized));
    }

    /** Resizes every selected note by the same amount (each stays at least 1 tick). */
    public static Result resizeAll(List<Note> notes, Set<Note> selection, int deltaTicks) {
        List<Note> result = new ArrayList<>();
        Set<Note> resized = new LinkedHashSet<>();
        for (Note note : notes) {
            if (selection.contains(note)) {
                Note changed = note.withLength(Math.max(note.length() + deltaTicks, 1));
                result.add(changed);
                resized.add(changed);
            } else {
                result.add(note);
            }
        }
        return new Result(result, resized);
    }

    /** Sets the velocity of the selected notes. */
    public static Result setVelocity(List<Note> notes, Set<Note> selection, float velocity) {
        float clamped = Math.min(Math.max(velocity, 0f), 1f);
        List<Note> result = new ArrayList<>();
        Set<Note> changed = new LinkedHashSet<>();
        for (Note note : notes) {
            if (selection.contains(note)) {
                Note updated = note.withVelocity(clamped);
                result.add(updated);
                changed.add(updated);
            } else {
                result.add(note);
            }
        }
        return new Result(result, changed);
    }

    /**
     * Snaps the start of the selected notes to the grid; {@code strength} 1 moves them fully, 0.5 halfway.
     * With nothing selected, every note is quantized.
     */
    public static Result quantize(List<Note> notes, Set<Note> selection, GridSnap grid, float strength) {
        float amount = Math.min(Math.max(strength, 0f), 1f);
        List<Note> result = new ArrayList<>();
        Set<Note> changed = new LinkedHashSet<>();
        for (Note note : notes) {
            if (selection.isEmpty() || selection.contains(note)) {
                int target = grid.snap(note.tick());
                Note moved = note.withTick(note.tick() + Math.round((target - note.tick()) * amount));
                result.add(moved);
                changed.add(moved);
            } else {
                result.add(note);
            }
        }
        return new Result(result, changed);
    }

    /** Copies the selected notes {@code offsetTicks} later; the copies become the selection. */
    public static Result duplicate(List<Note> notes, Set<Note> selection, int offsetTicks) {
        List<Note> result = new ArrayList<>(notes);
        Set<Note> copies = new LinkedHashSet<>();
        for (Note note : selection) {
            Note copy = note.withTick(note.tick() + offsetTicks);
            result.removeIf(n -> n.tick() == copy.tick() && n.pitch() == copy.pitch());
            result.add(copy);
            copies.add(copy);
        }
        return new Result(result, copies);
    }
}
