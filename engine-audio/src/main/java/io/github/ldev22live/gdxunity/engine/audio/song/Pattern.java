package io.github.ldev22live.gdxunity.engine.audio.song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A block of music: notes per channel over a fixed number of ticks. Patterns are placed on the playlist. */
public final class Pattern {
    private static final Comparator<Note> BY_TIME = Comparator.comparingInt(Note::tick)
        .thenComparingInt(Note::pitch);

    private final Song song;
    private final int id;
    private String name;
    private int lengthTicks;
    private final Map<Integer, List<Note>> notesByChannel = new LinkedHashMap<>();

    Pattern(Song song, int id, String name, int lengthTicks) {
        this.song = Objects.requireNonNull(song, "song cannot be null");
        this.id = id;
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.lengthTicks = checkLength(lengthTicks);
    }

    private static int checkLength(int lengthTicks) {
        if (lengthTicks < Song.TICKS_PER_BAR || lengthTicks % Song.TICKS_PER_BAR != 0) {
            throw new IllegalArgumentException(
                "Pattern length must be a whole number of bars (" + Song.TICKS_PER_BAR + " ticks each): " + lengthTicks);
        }
        return lengthTicks;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    /** Length in ticks, a whole number of bars. */
    public int getLengthTicks() {
        return lengthTicks;
    }

    public int getBars() {
        return lengthTicks / Song.TICKS_PER_BAR;
    }

    /** Changes the length; notes that start at or after the new end are removed, others are cut to fit. */
    public void setLengthTicks(int lengthTicks) {
        this.lengthTicks = checkLength(lengthTicks);
        song.touchSchedule();
        for (Map.Entry<Integer, List<Note>> entry : notesByChannel.entrySet()) {
            entry.setValue(fit(entry.getValue()));
        }
    }

    private List<Note> fit(List<Note> notes) {
        List<Note> kept = new ArrayList<>();
        for (Note note : notes) {
            if (note.tick() < lengthTicks) {
                kept.add(note.endTick() > lengthTicks ? note.withLength(lengthTicks - note.tick()) : note);
            }
        }
        return kept;
    }

    /** Notes of a channel, sorted by start time then pitch. Empty when the channel has none. */
    public List<Note> getNotes(int channelId) {
        List<Note> notes = notesByChannel.get(channelId);
        return notes == null ? List.of() : Collections.unmodifiableList(notes);
    }

    /** Replaces all notes of a channel; notes past the pattern end are cut or dropped like in {@link #setLengthTicks}. */
    public void setNotes(int channelId, List<Note> notes) {
        Objects.requireNonNull(notes, "notes cannot be null");
        List<Note> sorted = fit(notes);
        song.touchSchedule();
        sorted.sort(BY_TIME);
        if (sorted.isEmpty()) {
            notesByChannel.remove(channelId);
        } else {
            notesByChannel.put(channelId, sorted);
        }
    }

    /** Channels that have at least one note here. */
    public List<Integer> getChannelsWithNotes() {
        return List.copyOf(notesByChannel.keySet());
    }

    /** Removes a channel's notes, returning them. */
    List<Note> removeChannel(int channelId) {
        song.touchSchedule();
        List<Note> removed = notesByChannel.remove(channelId);
        return removed == null ? List.of() : removed;
    }

    public int getNoteCount() {
        int count = 0;
        for (List<Note> notes : notesByChannel.values()) {
            count += notes.size();
        }
        return count;
    }
}
