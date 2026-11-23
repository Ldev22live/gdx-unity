package io.github.ldev22live.gdxunity.editor.audio;

import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.audio.Effect;
import io.github.ldev22live.gdxunity.engine.audio.Mixer;
import io.github.ldev22live.gdxunity.engine.audio.song.Channel;
import io.github.ldev22live.gdxunity.engine.audio.song.InstrumentSpec;
import io.github.ldev22live.gdxunity.engine.audio.song.Note;
import io.github.ldev22live.gdxunity.engine.audio.song.Pattern;
import io.github.ldev22live.gdxunity.engine.audio.song.PlaylistClip;
import io.github.ldev22live.gdxunity.engine.audio.song.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Undoable edits of a {@link Song}. Each is one undo step; they hold what they need to restore the song exactly. */
public final class MusicCommands {
    private MusicCommands() { }

    /** Replaces one channel's notes in one pattern; every note edit (draw, move, resize, delete, step) is one of these. */
    public static final class SetNotes implements EditorCommand {
        private final Pattern pattern;
        private final int channelId;
        private final List<Note> before;
        private final List<Note> after;
        private final String description;

        public SetNotes(Pattern pattern, int channelId, List<Note> after, String description) {
            this.pattern = Objects.requireNonNull(pattern, "pattern cannot be null");
            this.channelId = channelId;
            this.before = List.copyOf(pattern.getNotes(channelId));
            this.after = List.copyOf(Objects.requireNonNull(after, "after cannot be null"));
            this.description = Objects.requireNonNull(description, "description cannot be null");
        }

        @Override
        public boolean execute() {
            pattern.setNotes(channelId, after);
            // Compare what the pattern kept (notes past the end are dropped), not what was asked for
            return !before.equals(pattern.getNotes(channelId));
        }

        @Override
        public boolean undo() {
            pattern.setNotes(channelId, before);
            return true;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    /** Replaces the playlist. */
    public static final class SetClips implements EditorCommand {
        private final Song song;
        private final List<PlaylistClip> before;
        private final List<PlaylistClip> after;
        private final String description;

        public SetClips(Song song, List<PlaylistClip> after, String description) {
            this.song = Objects.requireNonNull(song, "song cannot be null");
            this.before = List.copyOf(song.getClips());
            this.after = List.copyOf(Objects.requireNonNull(after, "after cannot be null"));
            this.description = Objects.requireNonNull(description, "description cannot be null");
        }

        @Override
        public boolean execute() {
            if (before.equals(after)) {
                return false;
            }
            song.setClips(after);
            return true;
        }

        @Override
        public boolean undo() {
            song.setClips(before);
            return true;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    /** Adds a channel to the rack. */
    public static final class AddChannel implements EditorCommand {
        private final Song song;
        private final String name;
        private final InstrumentSpec instrument;
        private final int mixerIndex;
        private Channel channel;

        public AddChannel(Song song, String name, InstrumentSpec instrument, int mixerIndex) {
            this.song = Objects.requireNonNull(song, "song cannot be null");
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.instrument = Objects.requireNonNull(instrument, "instrument cannot be null");
            this.mixerIndex = mixerIndex;
        }

        /** The channel after {@link #execute()}. */
        public Channel getChannel() {
            return channel;
        }

        @Override
        public boolean execute() {
            if (channel == null) {
                channel = song.addChannel(name, instrument, mixerIndex);
            } else {
                // Redo: the same channel object comes back, so its notes' ids still match
                song.insertChannel(song.getChannels().size(), channel);
            }
            return true;
        }

        @Override
        public boolean undo() {
            song.removeChannel(channel.getId());
            return true;
        }

        @Override
        public String getDescription() {
            return "Add channel " + name;
        }
    }

    /** Removes a channel and its notes in every pattern; undo puts both back. */
    public static final class RemoveChannel implements EditorCommand {
        private final Song song;
        private final Channel channel;
        private int index = -1;
        private final Map<Pattern, List<Note>> notes = new java.util.LinkedHashMap<>();

        public RemoveChannel(Song song, Channel channel) {
            this.song = Objects.requireNonNull(song, "song cannot be null");
            this.channel = Objects.requireNonNull(channel, "channel cannot be null");
        }

        @Override
        public boolean execute() {
            notes.clear();
            for (Pattern pattern : song.getPatterns()) {
                List<Note> own = pattern.getNotes(channel.getId());
                if (!own.isEmpty()) {
                    notes.put(pattern, List.copyOf(own));
                }
            }
            index = song.removeChannel(channel.getId());
            return index >= 0;
        }

        @Override
        public boolean undo() {
            song.insertChannel(index, channel);
            for (Map.Entry<Pattern, List<Note>> entry : notes.entrySet()) {
                entry.getKey().setNotes(channel.getId(), entry.getValue());
            }
            return true;
        }

        @Override
        public String getDescription() {
            return "Remove channel " + channel.getName();
        }
    }

    /** Swaps the instrument of a channel. */
    public static final class SetInstrument implements EditorCommand {
        private final Channel channel;
        private final InstrumentSpec before;
        private final InstrumentSpec after;

        public SetInstrument(Channel channel, InstrumentSpec after) {
            this.channel = Objects.requireNonNull(channel, "channel cannot be null");
            this.before = channel.getInstrument();
            this.after = Objects.requireNonNull(after, "after cannot be null");
        }

        @Override
        public boolean execute() {
            channel.setInstrument(after);
            return true;
        }

        @Override
        public boolean undo() {
            channel.setInstrument(before);
            return true;
        }

        @Override
        public String getDescription() {
            return "Change instrument of " + channel.getName();
        }
    }

    /** Adds a pattern. */
    public static final class AddPattern implements EditorCommand {
        private final Song song;
        private final String name;
        private final int bars;
        private Pattern pattern;

        public AddPattern(Song song, String name, int bars) {
            this.song = Objects.requireNonNull(song, "song cannot be null");
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.bars = bars;
        }

        public Pattern getPattern() {
            return pattern;
        }

        @Override
        public boolean execute() {
            if (pattern == null) {
                pattern = song.addPattern(name, bars);
            } else {
                song.insertPattern(song.getPatterns().size(), pattern);
            }
            return true;
        }

        @Override
        public boolean undo() {
            song.removePattern(pattern.getId());
            return true;
        }

        @Override
        public String getDescription() {
            return "Add pattern " + name;
        }
    }

    /** Removes a pattern together with the clips that play it; undo restores both. */
    public static final class RemovePattern implements EditorCommand {
        private final Song song;
        private final Pattern pattern;
        private List<PlaylistClip> clipsBefore;
        private int index = -1;

        public RemovePattern(Song song, Pattern pattern) {
            this.song = Objects.requireNonNull(song, "song cannot be null");
            this.pattern = Objects.requireNonNull(pattern, "pattern cannot be null");
        }

        @Override
        public boolean execute() {
            clipsBefore = new ArrayList<>(song.getClips());
            index = song.removePattern(pattern.getId());
            return index >= 0;
        }

        @Override
        public boolean undo() {
            song.insertPattern(index, pattern);
            song.setClips(clipsBefore);
            return true;
        }

        @Override
        public String getDescription() {
            return "Remove pattern " + pattern.getName();
        }
    }

    /** Changes the length of a pattern in bars; notes that no longer fit are restored by undo. */
    public static final class SetPatternBars implements EditorCommand {
        private final Pattern pattern;
        private final int bars;
        private final int beforeTicks;
        private final Map<Integer, List<Note>> notesBefore = new java.util.LinkedHashMap<>();

        public SetPatternBars(Pattern pattern, int bars) {
            this.pattern = Objects.requireNonNull(pattern, "pattern cannot be null");
            this.bars = bars;
            this.beforeTicks = pattern.getLengthTicks();
            for (int channelId : pattern.getChannelsWithNotes()) {
                notesBefore.put(channelId, List.copyOf(pattern.getNotes(channelId)));
            }
        }

        @Override
        public boolean execute() {
            int ticks = bars * Song.TICKS_PER_BAR;
            if (ticks == pattern.getLengthTicks() || bars < 1 || bars > Song.MAX_PATTERN_BARS) {
                return false;
            }
            pattern.setLengthTicks(ticks);
            return true;
        }

        @Override
        public boolean undo() {
            pattern.setLengthTicks(beforeTicks);
            for (Map.Entry<Integer, List<Note>> entry : notesBefore.entrySet()) {
                pattern.setNotes(entry.getKey(), entry.getValue());
            }
            return true;
        }

        @Override
        public String getDescription() {
            return "Set pattern length to " + bars + " bars";
        }
    }

    /**
     * Records a value change that has already been shown live (a knob drag): executing applies {@code after},
     * undoing applies {@code before}. Does nothing if the two are equal.
     */
    public static final class SetValue<T> implements EditorCommand {
        private final String description;
        private final T before;
        private final T after;
        private final Consumer<T> setter;

        public SetValue(String description, T before, T after, Consumer<T> setter) {
            this.description = Objects.requireNonNull(description, "description cannot be null");
            this.before = before;
            this.after = after;
            this.setter = Objects.requireNonNull(setter, "setter cannot be null");
        }

        @Override
        public boolean execute() {
            if (Objects.equals(before, after)) {
                return false;
            }
            setter.accept(after);
            return true;
        }

        @Override
        public boolean undo() {
            setter.accept(before);
            return true;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    /** Adds an effect to a mixer strip. */
    public static final class AddEffect implements EditorCommand {
        private final Mixer.Strip strip;
        private final Effect effect;

        public AddEffect(Mixer.Strip strip, Effect effect) {
            this.strip = Objects.requireNonNull(strip, "strip cannot be null");
            this.effect = Objects.requireNonNull(effect, "effect cannot be null");
        }

        @Override
        public boolean execute() {
            if (strip.getEffects().size() >= Mixer.MAX_EFFECTS) {
                return false;
            }
            strip.addEffect(effect);
            return true;
        }

        @Override
        public boolean undo() {
            return strip.removeEffect(effect);
        }

        @Override
        public String getDescription() {
            return "Add " + effect.getType() + " to " + strip.getName();
        }
    }

    /** Removes an effect from a mixer strip, back to the same place on undo. */
    public static final class RemoveEffect implements EditorCommand {
        private final Mixer.Strip strip;
        private final Effect effect;
        private List<Effect> before;

        public RemoveEffect(Mixer.Strip strip, Effect effect) {
            this.strip = Objects.requireNonNull(strip, "strip cannot be null");
            this.effect = Objects.requireNonNull(effect, "effect cannot be null");
        }

        @Override
        public boolean execute() {
            before = List.copyOf(strip.getEffects());
            return strip.removeEffect(effect);
        }

        @Override
        public boolean undo() {
            // Rebuild the chain in its old order
            for (Effect existing : List.copyOf(strip.getEffects())) {
                strip.removeEffect(existing);
            }
            for (Effect old : before) {
                strip.addEffect(old);
            }
            return true;
        }

        @Override
        public String getDescription() {
            return "Remove " + effect.getType() + " from " + strip.getName();
        }
    }
}
