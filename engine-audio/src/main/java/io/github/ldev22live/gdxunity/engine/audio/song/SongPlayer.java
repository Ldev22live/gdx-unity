package io.github.ldev22live.gdxunity.engine.audio.song;

import io.github.ldev22live.gdxunity.engine.audio.Instrument;
import io.github.ldev22live.gdxunity.engine.audio.Mixer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Plays a {@link Song}: turns its {@link Schedule} into note events at the right frame, runs the channel
 * instruments and the song's {@link Mixer}. Call {@link #render} from the audio thread (or in a loop for an offline
 * render); every other method may be called from the editor thread. They share one lock, so a block is never
 * rendered half-way through a change.
 *
 * <p>Call {@link #refresh()} after editing the song: it rebuilds instruments and the schedule when the song's
 * revisions moved. Notes sounding when the schedule is replaced are released.</p>
 */
public final class SongPlayer implements Mixer.StripSource {
    private static final double SECONDS_PER_MINUTE = 60.0;
    /** An event within half a frame of the current position is played now. */
    private static final double HALF_FRAME = 0.5;

    /** A channel's instrument and the strip it feeds. */
    private static final class Slot {
        final int channelId;
        final int mixerIndex;
        final InstrumentSpec spec;
        final String signature;
        final Instrument instrument;

        Slot(int channelId, int mixerIndex, InstrumentSpec spec, String signature, Instrument instrument) {
            this.channelId = channelId;
            this.mixerIndex = mixerIndex;
            this.spec = spec;
            this.signature = signature;
            this.instrument = instrument;
        }
    }

    private static final class SilentInstrument implements Instrument {
        @Override
        public void noteOn(int pitch, float velocity) { }

        @Override
        public void noteOff(int pitch) { }

        @Override
        public void allNotesOff() { }

        @Override
        public void render(float[] left, float[] right, int offset, int frames) { }
    }

    private final Song song;
    private final InstrumentFactory factory;
    private final Object lock = new Object();
    private List<Slot> slots = new ArrayList<>();
    private List<String> errors = List.of();
    private int syncedInstrumentRevision = -1;
    private int syncedScheduleRevision = -1;
    private Schedule schedule = Schedule.empty(Song.TICKS_PER_BAR);
    private Schedule activeSchedule = schedule;
    private Schedule.Mode mode = Schedule.Mode.PATTERN;
    private int patternId;
    private boolean playing;
    private boolean loop = true;
    private double positionTicks;
    private int eventIndex;

    public SongPlayer(Song song, MusicAssets assets) {
        this.song = Objects.requireNonNull(song, "song cannot be null");
        this.factory = new InstrumentFactory(Objects.requireNonNull(assets, "assets cannot be null"), Song.SAMPLE_RATE);
        if (!song.getPatterns().isEmpty()) {
            patternId = song.getPatterns().get(0).getId();
        }
        refresh();
    }

    public Song getSong() {
        return song;
    }

    /** Problems found while building instruments (missing sample files and so on), one line each. */
    public List<String> getErrors() {
        synchronized (lock) {
            return errors;
        }
    }

    // ---- transport ----

    /** Chooses what plays: one pattern on repeat, or the playlist. Takes effect from {@link #refresh()}. */
    public void setPlayback(Schedule.Mode mode, int patternId) {
        synchronized (lock) {
            this.mode = Objects.requireNonNull(mode, "mode cannot be null");
            this.patternId = patternId;
            syncedScheduleRevision = -1;
        }
        refresh();
    }

    public Schedule.Mode getMode() {
        synchronized (lock) {
            return mode;
        }
    }

    public int getPatternId() {
        synchronized (lock) {
            return patternId;
        }
    }

    public void setLoop(boolean loop) {
        synchronized (lock) {
            this.loop = loop;
        }
    }

    public boolean isLoop() {
        synchronized (lock) {
            return loop;
        }
    }

    public void play() {
        refresh();
        synchronized (lock) {
            playing = true;
        }
    }

    /** Stops moving; the position stays and sounding notes are released. */
    public void pause() {
        synchronized (lock) {
            playing = false;
            releaseAll();
        }
    }

    /** Stops and returns to the start. */
    public void stop() {
        synchronized (lock) {
            playing = false;
            releaseAll();
            positionTicks = 0;
            eventIndex = 0;
        }
    }

    public boolean isPlaying() {
        synchronized (lock) {
            return playing;
        }
    }

    /** Moves the play position; sounding notes are released. */
    public void seek(double ticks) {
        synchronized (lock) {
            positionTicks = Math.max(ticks, 0.0);
            releaseAll();
            eventIndex = firstEventAtOrAfter(positionTicks);
        }
    }

    /** Where playback is, in ticks from the start of the pattern or song. */
    public double getPositionTicks() {
        synchronized (lock) {
            return Math.max(positionTicks, 0.0);
        }
    }

    // ---- live notes ----

    /** Plays a note on a channel's instrument right away (keyboard preview, drawing notes). */
    public void previewNoteOn(int channelId, int pitch, float velocity) {
        synchronized (lock) {
            Slot slot = slotFor(channelId);
            if (slot != null) {
                slot.instrument.noteOn(pitch, velocity);
            }
        }
    }

    public void previewNoteOff(int channelId, int pitch) {
        synchronized (lock) {
            Slot slot = slotFor(channelId);
            if (slot != null) {
                slot.instrument.noteOff(pitch);
            }
        }
    }

    // ---- keeping up with the song ----

    /** Rebuilds instruments and the schedule if the song changed since the last call. */
    public void refresh() {
        synchronized (lock) {
            if (syncedInstrumentRevision != song.getInstrumentRevision()) {
                syncedInstrumentRevision = song.getInstrumentRevision();
                rebuildInstruments();
            }
            if (syncedScheduleRevision != song.getScheduleRevision()) {
                syncedScheduleRevision = song.getScheduleRevision();
                schedule = compileSchedule();
            }
        }
    }

    /** Forgets cached sample files, so the next {@link #refresh()} after a channel change reads them again. */
    public void reloadFiles() {
        synchronized (lock) {
            factory.clearCache();
            syncedInstrumentRevision = -1;
        }
        refresh();
    }

    private Schedule compileSchedule() {
        if (mode == Schedule.Mode.PATTERN && song.getPattern(patternId) == null) {
            patternId = song.getPatterns().isEmpty() ? 0 : song.getPatterns().get(0).getId();
            if (song.getPatterns().isEmpty()) {
                return Schedule.empty(Song.TICKS_PER_BAR);
            }
        }
        return Schedule.compile(song, mode, patternId);
    }

    private void rebuildInstruments() {
        Map<Integer, Slot> old = new HashMap<>();
        for (Slot slot : slots) {
            old.put(slot.channelId, slot);
        }
        List<Slot> rebuilt = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Channel channel : song.getChannels()) {
            InstrumentSpec spec = channel.getInstrument();
            String signature = signature(spec);
            Slot previous = old.remove(channel.getId());
            if (previous != null && previous.spec == spec && previous.signature.equals(signature)
                && previous.mixerIndex == channel.getMixerIndex()) {
                rebuilt.add(previous);
                continue;
            }
            if (previous != null) {
                previous.instrument.dispose();
            }
            Instrument instrument;
            try {
                instrument = factory.create(spec);
            } catch (IOException | RuntimeException e) {
                problems.add(channel.getName() + ": " + e.getMessage());
                instrument = new SilentInstrument();
            }
            rebuilt.add(new Slot(channel.getId(), channel.getMixerIndex(), spec, signature, instrument));
        }
        for (Slot gone : old.values()) {
            gone.instrument.dispose();
        }
        slots = rebuilt;
        errors = List.copyOf(problems);
    }

    /** Everything about a spec that needs a new instrument when it changes (synth knobs do not: they are live). */
    private static String signature(InstrumentSpec spec) {
        return spec.type + "|" + spec.path + "|" + spec.rootNote + "|" + spec.oneShot + "|" + spec.bank + "|"
            + spec.program + "|" + (spec.type == InstrumentSpec.Type.SFXR ? spec.sfxr.toJson() : "");
    }

    private Slot slotFor(int channelId) {
        for (Slot slot : slots) {
            if (slot.channelId == channelId) {
                return slot;
            }
        }
        return null;
    }

    private void releaseAll() {
        for (Slot slot : slots) {
            slot.instrument.allNotesOff();
        }
    }

    private int firstEventAtOrAfter(double tick) {
        List<Schedule.Event> events = activeSchedule.getEvents();
        int low = 0;
        int high = events.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (events.get(mid).tick() < tick) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    // ---- rendering ----

    @Override
    public void renderStrip(int stripIndex, float[] left, float[] right, int offset, int frames) {
        for (Slot slot : slots) {
            if (slot.mixerIndex == stripIndex) {
                slot.instrument.render(left, right, offset, frames);
            }
        }
    }

    /** Renders {@code frames} frames of the whole mix into the two arrays, starting at index 0. */
    public void render(float[] left, float[] right, int frames) {
        synchronized (lock) {
            if (schedule != activeSchedule) {
                activeSchedule = schedule;
                eventIndex = firstEventAtOrAfter(positionTicks);
                if (playing) {
                    releaseAll();
                }
            }
            int done = 0;
            while (done < frames) {
                int chunk = frames - done;
                double framesPerTick = Song.SAMPLE_RATE * SECONDS_PER_MINUTE / (song.getBpm() * Song.TICKS_PER_BEAT);
                if (playing) {
                    chunk = Math.min(chunk, playChunk(framesPerTick));
                    if (chunk <= 0) {
                        continue;
                    }
                }
                song.getMixer().render(this, left, right, done, chunk);
                if (playing) {
                    positionTicks += chunk / framesPerTick;
                }
                done += chunk;
            }
        }
    }

    /**
     * Plays the events that are due and returns how many frames can be rendered before the next event or the end.
     * Returns 0 when playback stopped or wrapped, so the caller asks again.
     */
    private int playChunk(double framesPerTick) {
        List<Schedule.Event> events = activeSchedule.getEvents();
        int chunk = Integer.MAX_VALUE;
        while (eventIndex < events.size()) {
            Schedule.Event event = events.get(eventIndex);
            double framesUntil = (event.tick() - positionTicks) * framesPerTick;
            if (framesUntil <= HALF_FRAME) {
                dispatch(event);
                eventIndex++;
                continue;
            }
            chunk = (int) Math.ceil(framesUntil - HALF_FRAME);
            break;
        }
        int length = activeSchedule.getLengthTicks();
        double framesToEnd = (length - positionTicks) * framesPerTick;
        if (length <= 0 || framesToEnd <= HALF_FRAME) {
            if (loop && length > 0) {
                // Keep the fraction of a frame the loop point fell between, even when it is negative, so loops do not drift
                positionTicks -= length;
                // Everything at the loop start is still to come, even if the fraction carried over is a hair past it
                eventIndex = 0;
            } else {
                playing = false;
                releaseAll();
                positionTicks = 0;
                eventIndex = 0;
            }
            return 0;
        }
        return Math.min(chunk, (int) Math.ceil(framesToEnd - HALF_FRAME));
    }

    private void dispatch(Schedule.Event event) {
        Slot slot = slotFor(event.channelId());
        if (slot == null) {
            return;
        }
        if (event.on()) {
            slot.instrument.noteOn(event.pitch(), event.velocity());
        } else {
            slot.instrument.noteOff(event.pitch());
        }
    }

    /** Frees every instrument. Safe to call twice. */
    public void dispose() {
        synchronized (lock) {
            for (Slot slot : slots) {
                slot.instrument.dispose();
            }
            slots = new ArrayList<>();
            playing = false;
        }
    }
}
