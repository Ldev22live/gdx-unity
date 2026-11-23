package io.github.ldev22live.gdxunity.editor.audio;

import io.github.ldev22live.gdxunity.editor.core.command.CommandHistory;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.audio.Effect;
import io.github.ldev22live.gdxunity.engine.audio.Effects;
import io.github.ldev22live.gdxunity.engine.audio.Mixer;
import io.github.ldev22live.gdxunity.engine.audio.song.Channel;
import io.github.ldev22live.gdxunity.engine.audio.song.InstrumentSpec;
import io.github.ldev22live.gdxunity.engine.audio.song.Note;
import io.github.ldev22live.gdxunity.engine.audio.song.Pattern;
import io.github.ldev22live.gdxunity.engine.audio.song.PlaylistClip;
import io.github.ldev22live.gdxunity.engine.audio.song.Song;
import io.github.ldev22live.gdxunity.engine.audio.song.StepGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A song open in the music editor: the {@link Song}, its own undo history (separate from the scene's, like a graph's
 * or a clip's), and whether it has unsaved changes. Every edit method is one undo step and returns false when it
 * changed nothing or was refused; the mixer and synth knobs are changed live and recorded when released with
 * {@link #recordChange}.
 */
public final class MusicDocument {
    /** Told after any edit, undo or redo. */
    public interface Listener {
        void onDocumentChanged(MusicDocument document);
    }

    private final String path;
    private final Song song;
    private final CommandHistory history = new CommandHistory();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private boolean dirty;

    public MusicDocument(String path, Song song) {
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.song = Objects.requireNonNull(song, "song cannot be null");
        history.addListener(h -> {
            dirty = true;
            notifyChanged();
        });
    }

    /** Project-relative path of the .song file. */
    public String getPath() {
        return path;
    }

    public Song getSong() {
        return song;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Called after the song was written to disk. */
    public void markSaved() {
        dirty = false;
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        for (Listener listener : listeners) {
            listener.onDocumentChanged(this);
        }
    }

    public boolean execute(EditorCommand command) {
        return history.execute(command);
    }

    public boolean undo() {
        return history.undo();
    }

    public boolean redo() {
        return history.redo();
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public String getUndoDescription() {
        return history.getUndoDescription();
    }

    public String getRedoDescription() {
        return history.getRedoDescription();
    }

    // ---- notes ----

    /** Replaces a channel's notes in a pattern. */
    public boolean setNotes(Pattern pattern, Channel channel, List<Note> notes, String description) {
        return execute(new MusicCommands.SetNotes(pattern, channel.getId(), notes, description));
    }

    /** Switches a step-sequencer step on or off. */
    public boolean toggleStep(Pattern pattern, Channel channel, int step, float velocity) {
        boolean on = StepGrid.isStepOn(pattern, channel, step);
        return setNotes(pattern, channel, StepGrid.toggled(pattern, channel, step, velocity),
            (on ? "Clear step " : "Set step ") + (step + 1) + " of " + channel.getName());
    }

    /** Sets a step's velocity. */
    public boolean setStepVelocity(Pattern pattern, Channel channel, int step, float velocity) {
        return setNotes(pattern, channel, StepGrid.withVelocity(pattern, channel, step, velocity),
            "Change step velocity of " + channel.getName());
    }

    /** Clears every note of a channel in a pattern. */
    public boolean clearChannel(Pattern pattern, Channel channel) {
        return setNotes(pattern, channel, List.of(), "Clear " + channel.getName() + " in " + pattern.getName());
    }

    // ---- song ----

    public boolean setBpm(float bpm) {
        if (!(bpm >= Song.MIN_BPM && bpm <= Song.MAX_BPM)) {
            return false;
        }
        return execute(new MusicCommands.SetValue<>("Set tempo to " + bpm, song.getBpm(), bpm, song::setBpm));
    }

    public boolean setSwing(float swing) {
        if (!(swing >= 0f && swing <= 1f)) {
            return false;
        }
        return execute(new MusicCommands.SetValue<>("Set swing", song.getSwing(), swing, song::setSwing));
    }

    // ---- channels ----

    /**
     * Adds a channel on the first mixer strip no channel uses yet (or the last strip when all are used).
     *
     * @return the new channel, or null when the song already has the maximum number of channels
     */
    public Channel addChannel(String name, InstrumentSpec instrument) {
        if (song.getChannels().size() >= Song.MAX_CHANNELS) {
            return null;
        }
        int strip = song.getMixer().getStripCount() - 1;
        for (int i = 0; i < song.getMixer().getStripCount(); i++) {
            final int candidate = i;
            if (song.getChannels().stream().noneMatch(c -> c.getMixerIndex() == candidate)) {
                strip = i;
                break;
            }
        }
        MusicCommands.AddChannel command = new MusicCommands.AddChannel(song, name, instrument, strip);
        return execute(command) ? command.getChannel() : null;
    }

    public boolean removeChannel(Channel channel) {
        return execute(new MusicCommands.RemoveChannel(song, channel));
    }

    public boolean setInstrument(Channel channel, InstrumentSpec instrument) {
        return execute(new MusicCommands.SetInstrument(channel, instrument));
    }

    public boolean renameChannel(Channel channel, String name) {
        return execute(new MusicCommands.SetValue<>("Rename channel", channel.getName(), name, channel::setName));
    }

    public boolean setChannelMuted(Channel channel, boolean muted) {
        return execute(new MusicCommands.SetValue<>((muted ? "Mute " : "Unmute ") + channel.getName(),
            channel.isMuted(), muted, channel::setMuted));
    }

    public boolean setStepPitch(Channel channel, int pitch) {
        if (pitch < Note.MIN_PITCH || pitch > Note.MAX_PITCH) {
            return false;
        }
        return execute(new MusicCommands.SetValue<>("Set step note of " + channel.getName(),
            channel.getStepPitch(), pitch, channel::setStepPitch));
    }

    public boolean setChannelMixerIndex(Channel channel, int stripIndex) {
        if (stripIndex < 0 || stripIndex >= song.getMixer().getStripCount()) {
            return false;
        }
        return execute(new MusicCommands.SetValue<>("Route " + channel.getName() + " to insert " + (stripIndex + 1),
            channel.getMixerIndex(), stripIndex, channel::setMixerIndex));
    }

    // ---- patterns and playlist ----

    public Pattern addPattern(String name, int bars) {
        if (bars < 1 || bars > Song.MAX_PATTERN_BARS) {
            return null;
        }
        MusicCommands.AddPattern command = new MusicCommands.AddPattern(song, name, bars);
        return execute(command) ? command.getPattern() : null;
    }

    /** Removes a pattern and the clips that play it; the last pattern cannot be removed. */
    public boolean removePattern(Pattern pattern) {
        if (song.getPatterns().size() <= 1) {
            return false;
        }
        return execute(new MusicCommands.RemovePattern(song, pattern));
    }

    public boolean setPatternBars(Pattern pattern, int bars) {
        return execute(new MusicCommands.SetPatternBars(pattern, bars));
    }

    public boolean renamePattern(Pattern pattern, String name) {
        return execute(new MusicCommands.SetValue<>("Rename pattern", pattern.getName(), name, pattern::setName));
    }

    /** Puts a pattern on the playlist; refused when it would overlap another clip on the track. */
    public boolean placeClip(int patternId, int track, int startTick, int lengthTicks) {
        PlaylistClip clip;
        try {
            clip = new PlaylistClip(patternId, track, startTick, lengthTicks);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!song.canPlaceClip(clip, -1)) {
            return false;
        }
        List<PlaylistClip> clips = new ArrayList<>(song.getClips());
        clips.add(clip);
        return execute(new MusicCommands.SetClips(song, clips, "Place pattern on playlist"));
    }

    /** Moves a clip to a new start and track. */
    public boolean moveClip(int index, int startTick, int track) {
        return replaceClip(index, c -> new PlaylistClip(c.patternId(), track, startTick, c.lengthTicks()), "Move clip");
    }

    /** Changes the length of a clip. */
    public boolean resizeClip(int index, int lengthTicks) {
        return replaceClip(index, c -> new PlaylistClip(c.patternId(), c.track(), c.startTick(), lengthTicks), "Resize clip");
    }

    private boolean replaceClip(int index, java.util.function.Function<PlaylistClip, PlaylistClip> change, String description) {
        if (index < 0 || index >= song.getClips().size()) {
            return false;
        }
        PlaylistClip changed;
        try {
            changed = change.apply(song.getClips().get(index));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!song.canPlaceClip(changed, index)) {
            return false;
        }
        List<PlaylistClip> clips = new ArrayList<>(song.getClips());
        clips.set(index, changed);
        return execute(new MusicCommands.SetClips(song, clips, description));
    }

    public boolean deleteClip(int index) {
        if (index < 0 || index >= song.getClips().size()) {
            return false;
        }
        List<PlaylistClip> clips = new ArrayList<>(song.getClips());
        clips.remove(index);
        return execute(new MusicCommands.SetClips(song, clips, "Delete clip"));
    }

    // ---- mixer ----

    /** The strip at {@code index}, or the master strip for -1. */
    public Mixer.Strip strip(int index) {
        return index < 0 ? song.getMixer().getMaster() : song.getMixer().getStrip(index);
    }

    /** Adds a new effect of the given type to a strip (-1 is the master). */
    public Effect addEffect(int stripIndex, String type) {
        Mixer.Strip strip = strip(stripIndex);
        if (strip.getEffects().size() >= Mixer.MAX_EFFECTS) {
            return null;
        }
        Effect effect = Effects.create(type, Song.SAMPLE_RATE);
        return execute(new MusicCommands.AddEffect(strip, effect)) ? effect : null;
    }

    public boolean removeEffect(int stripIndex, Effect effect) {
        return execute(new MusicCommands.RemoveEffect(strip(stripIndex), effect));
    }

    /**
     * Records a change the user already made live (dragging a fader or knob): one undo step from {@code before} to
     * {@code after}, applied through {@code setter}.
     */
    public <T> boolean recordChange(String description, T before, T after, Consumer<T> setter) {
        return execute(new MusicCommands.SetValue<>(description, before, after, setter));
    }
}
