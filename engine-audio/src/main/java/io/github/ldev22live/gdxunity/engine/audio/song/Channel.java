package io.github.ldev22live.gdxunity.engine.audio.song;

import java.util.Objects;

/** One row of the channel rack: an instrument, the mixer strip it plays through, and a mute switch. */
public final class Channel {
    private final Song song;
    private final int id;
    private String name;
    private InstrumentSpec instrument;
    private int mixerIndex;
    private boolean muted;
    /** Pitch a step in the step sequencer plays (drum channels use the drum's own note). */
    private int stepPitch = 60;

    Channel(Song song, int id, String name, InstrumentSpec instrument, int mixerIndex) {
        this.song = Objects.requireNonNull(song, "song cannot be null");
        this.id = id;
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.instrument = Objects.requireNonNull(instrument, "instrument cannot be null");
        this.mixerIndex = mixerIndex;
    }

    /** Stable number that patterns use to refer to this channel. */
    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    public InstrumentSpec getInstrument() {
        return instrument;
    }

    public void setInstrument(InstrumentSpec instrument) {
        this.instrument = Objects.requireNonNull(instrument, "instrument cannot be null");
        song.touchInstruments();
    }

    /** Index of the mixer strip this channel plays through. */
    public int getMixerIndex() {
        return mixerIndex;
    }

    public void setMixerIndex(int mixerIndex) {
        this.mixerIndex = mixerIndex;
        song.touchInstruments();
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        song.touchSchedule();
    }

    public int getStepPitch() {
        return stepPitch;
    }

    public void setStepPitch(int stepPitch) {
        if (stepPitch < Note.MIN_PITCH || stepPitch > Note.MAX_PITCH) {
            throw new IllegalArgumentException("stepPitch must be 0..127: " + stepPitch);
        }
        this.stepPitch = stepPitch;
    }
}
