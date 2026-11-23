package io.github.ldev22live.gdxunity.engine.audio.song;

import io.github.ldev22live.gdxunity.engine.audio.Mixer;
import io.github.ldev22live.gdxunity.engine.audio.SynthPatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A music project: tempo, the channel rack, patterns, the playlist and the mixer. Time is counted in ticks,
 * {@link #TICKS_PER_BEAT} to a beat, four beats to a bar (4/4 only).
 *
 * <p>Two revision numbers tell the player what to rebuild: {@link #getScheduleRevision()} changes when notes, tempo,
 * patterns, clips or mutes change, {@link #getInstrumentRevision()} when channels or their instruments change.
 * Mixer knobs and synth settings are live and change neither.</p>
 */
public final class Song {
    /** Ticks in one beat. */
    public static final int TICKS_PER_BEAT = 96;
    public static final int BEATS_PER_BAR = 4;
    public static final int TICKS_PER_BAR = TICKS_PER_BEAT * BEATS_PER_BAR;
    /** Ticks in one step of the step sequencer (a sixteenth note). */
    public static final int STEP_TICKS = TICKS_PER_BEAT / 4;
    public static final int STEPS_PER_BAR = TICKS_PER_BAR / STEP_TICKS;
    /** The sample rate every song renders at. */
    public static final float SAMPLE_RATE = 44100f;
    public static final float MIN_BPM = 20f;
    public static final float MAX_BPM = 300f;
    public static final int MAX_CHANNELS = 16;
    public static final int MAX_PATTERN_BARS = 16;
    public static final int MAX_TRACKS = 16;

    private String name;
    private float bpm = 120f;
    private float swing;
    private final List<Channel> channels = new ArrayList<>();
    private final List<Pattern> patterns = new ArrayList<>();
    private final List<PlaylistClip> clips = new ArrayList<>();
    private final Mixer mixer = new Mixer(SAMPLE_RATE);
    private int nextChannelId = 1;
    private int nextPatternId = 1;
    private volatile int scheduleRevision;
    private volatile int instrumentRevision;

    public Song(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    /**
     * A starting point like the usual first screen of a sequencer: kick, snare and hi-hat rows, a lead and a bass,
     * and one empty pattern.
     */
    public static Song createDefault(String name) {
        Song song = new Song(name);
        song.addChannel("Kick", drumSpec(), 0).setStepPitch(36);
        song.addChannel("Snare", drumSpec(), 1).setStepPitch(38);
        song.addChannel("Hat", drumSpec(), 2).setStepPitch(42);
        song.addChannel("Lead", InstrumentSpec.synth(SynthPatch.preset("Saw Lead")), 3);
        song.addChannel("Bass", InstrumentSpec.synth(SynthPatch.preset("Sub Bass")), 4);
        song.addPattern("Pattern 1", 1);
        song.getMixer().getStrip(0).setName("Kick");
        song.getMixer().getStrip(1).setName("Snare");
        song.getMixer().getStrip(2).setName("Hat");
        song.getMixer().getStrip(3).setName("Lead");
        song.getMixer().getStrip(4).setName("Bass");
        return song;
    }

    private static InstrumentSpec drumSpec() {
        return InstrumentSpec.drums();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    public float getBpm() {
        return bpm;
    }

    /** Sets the tempo; values outside {@link #MIN_BPM}..{@link #MAX_BPM} are rejected. */
    public void setBpm(float bpm) {
        if (!(bpm >= MIN_BPM && bpm <= MAX_BPM)) {
            throw new IllegalArgumentException("bpm must be between " + MIN_BPM + " and " + MAX_BPM + ": " + bpm);
        }
        this.bpm = bpm;
        touchSchedule();
    }

    public float getSwing() {
        return swing;
    }

    /** 0 is straight sixteenths, 1 delays every second sixteenth by a third of a step (triplet feel). */
    public void setSwing(float swing) {
        if (!(swing >= 0f && swing <= 1f)) {
            throw new IllegalArgumentException("swing must be 0..1: " + swing);
        }
        this.swing = swing;
        touchSchedule();
    }

    public Mixer getMixer() {
        return mixer;
    }

    // ---- channels ----

    public List<Channel> getChannels() {
        return Collections.unmodifiableList(channels);
    }

    /** The channel with this id, or null. */
    public Channel getChannel(int id) {
        for (Channel channel : channels) {
            if (channel.getId() == id) {
                return channel;
            }
        }
        return null;
    }

    /** Adds a channel at the end of the rack. */
    public Channel addChannel(String channelName, InstrumentSpec instrument, int mixerIndex) {
        if (channels.size() >= MAX_CHANNELS) {
            throw new IllegalStateException("A song has at most " + MAX_CHANNELS + " channels");
        }
        checkMixerIndex(mixerIndex);
        Channel channel = new Channel(this, nextChannelId++, channelName, instrument, mixerIndex);
        channels.add(channel);
        touchInstruments();
        return channel;
    }

    /** Puts a removed channel back at {@code index} (undo of {@link #removeChannel}). */
    public void insertChannel(int index, Channel channel) {
        Objects.requireNonNull(channel, "channel cannot be null");
        if (getChannel(channel.getId()) != null) {
            throw new IllegalArgumentException("Channel " + channel.getId() + " is already in the song");
        }
        if (channels.size() >= MAX_CHANNELS) {
            throw new IllegalStateException("A song has at most " + MAX_CHANNELS + " channels");
        }
        channels.add(Math.min(Math.max(index, 0), channels.size()), channel);
        touchInstruments();
    }

    /** Removes a channel and all its notes; returns its position in the rack, or -1 if it was not there. */
    public int removeChannel(int id) {
        Channel channel = getChannel(id);
        if (channel == null) {
            return -1;
        }
        int index = channels.indexOf(channel);
        channels.remove(index);
        for (Pattern pattern : patterns) {
            pattern.removeChannel(id);
        }
        touchInstruments();
        return index;
    }

    private void checkMixerIndex(int mixerIndex) {
        if (mixerIndex < 0 || mixerIndex >= mixer.getStripCount()) {
            throw new IllegalArgumentException("Mixer strip " + mixerIndex + " does not exist (0.." + (mixer.getStripCount() - 1) + ")");
        }
    }

    // ---- patterns ----

    public List<Pattern> getPatterns() {
        return Collections.unmodifiableList(patterns);
    }

    /** The pattern with this id, or null. */
    public Pattern getPattern(int id) {
        for (Pattern pattern : patterns) {
            if (pattern.getId() == id) {
                return pattern;
            }
        }
        return null;
    }

    public Pattern addPattern(String patternName, int bars) {
        if (bars < 1 || bars > MAX_PATTERN_BARS) {
            throw new IllegalArgumentException("A pattern is 1.." + MAX_PATTERN_BARS + " bars long: " + bars);
        }
        Pattern pattern = new Pattern(this, nextPatternId++, patternName, bars * TICKS_PER_BAR);
        patterns.add(pattern);
        touchSchedule();
        return pattern;
    }

    /** Puts a removed pattern back at {@code index} (undo of {@link #removePattern}). */
    public void insertPattern(int index, Pattern pattern) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        if (getPattern(pattern.getId()) != null) {
            throw new IllegalArgumentException("Pattern " + pattern.getId() + " is already in the song");
        }
        patterns.add(Math.min(Math.max(index, 0), patterns.size()), pattern);
        touchSchedule();
    }

    /** Removes a pattern; clips that played it are removed too. Returns its position, or -1 if absent. */
    public int removePattern(int id) {
        Pattern pattern = getPattern(id);
        if (pattern == null) {
            return -1;
        }
        int index = patterns.indexOf(pattern);
        patterns.remove(index);
        clips.removeIf(clip -> clip.patternId() == id);
        touchSchedule();
        return index;
    }

    // ---- playlist ----

    public List<PlaylistClip> getClips() {
        return Collections.unmodifiableList(clips);
    }

    /** True when {@code clip} fits on its track without overlapping another clip (other than the one at {@code ignore}). */
    public boolean canPlaceClip(PlaylistClip clip, int ignore) {
        if (getPattern(clip.patternId()) == null || clip.track() >= MAX_TRACKS) {
            return false;
        }
        for (int i = 0; i < clips.size(); i++) {
            PlaylistClip other = clips.get(i);
            if (i != ignore && other.track() == clip.track()
                && clip.startTick() < other.endTick() && other.startTick() < clip.endTick()) {
                return false;
            }
        }
        return true;
    }

    /** Replaces the whole playlist; every clip must fit (see {@link #canPlaceClip}). */
    public void setClips(List<PlaylistClip> newClips) {
        Objects.requireNonNull(newClips, "newClips cannot be null");
        List<PlaylistClip> previous = new ArrayList<>(clips);
        clips.clear();
        for (PlaylistClip clip : newClips) {
            if (!canPlaceClip(clip, -1)) {
                clips.clear();
                clips.addAll(previous);
                throw new IllegalArgumentException("Clip does not fit the playlist: " + clip);
            }
            clips.add(clip);
        }
        touchSchedule();
    }

    /** Tick where the last clip ends, 0 for an empty playlist. */
    public int getPlaylistLengthTicks() {
        int end = 0;
        for (PlaylistClip clip : clips) {
            end = Math.max(end, clip.endTick());
        }
        return end;
    }

    // ---- revisions ----

    public int getScheduleRevision() {
        return scheduleRevision;
    }

    public int getInstrumentRevision() {
        return instrumentRevision;
    }

    void touchSchedule() {
        scheduleRevision++;
    }

    void touchInstruments() {
        instrumentRevision++;
        scheduleRevision++;
    }

    /** Marks everything as changed, for code that edited fields directly (loading, bulk edits). */
    public void touchAll() {
        touchInstruments();
    }

    /** Used by {@link SongIO}: continues id numbering after loading. */
    void setNextIds(int channelId, int patternId) {
        nextChannelId = channelId;
        nextPatternId = patternId;
    }

    int getNextChannelId() {
        return nextChannelId;
    }

    int getNextPatternId() {
        return nextPatternId;
    }

    /** Used by {@link SongIO}: adds a channel keeping its saved id. */
    Channel restoreChannel(int id, String channelName, InstrumentSpec instrument, int mixerIndex) {
        checkMixerIndex(mixerIndex);
        Channel channel = new Channel(this, id, channelName, instrument, mixerIndex);
        channels.add(channel);
        touchInstruments();
        return channel;
    }

    /** Used by {@link SongIO}: adds a pattern keeping its saved id. */
    Pattern restorePattern(int id, String patternName, int lengthTicks) {
        Pattern pattern = new Pattern(this, id, patternName, lengthTicks);
        patterns.add(pattern);
        touchSchedule();
        return pattern;
    }
}
