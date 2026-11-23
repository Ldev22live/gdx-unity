package io.github.ldev22live.gdxunity.engine.audio;

/** Something that turns note events into audio: a synth, a drum kit, a sampler or a SoundFont. */
public interface Instrument {
    /** Starts a note. {@code pitch} is a MIDI note number (60 = middle C), {@code velocity} 0..1. */
    void noteOn(int pitch, float velocity);

    /** Releases a note started with {@link #noteOn}; the sound may ring on through its release. */
    void noteOff(int pitch);

    /** Releases every sounding note. */
    void allNotesOff();

    /** Adds {@code frames} frames of sound into {@code left} and {@code right} starting at {@code offset}. */
    void render(float[] left, float[] right, int offset, int frames);

    /** Frees anything the instrument holds. Safe to call twice. */
    default void dispose() { }
}
