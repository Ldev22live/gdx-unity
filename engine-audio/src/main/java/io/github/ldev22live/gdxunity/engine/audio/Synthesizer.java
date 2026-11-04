package io.github.ldev22live.gdxunity.engine.audio;

import java.util.Objects;

/**
 * A polyphonic subtractive synthesizer driven by a {@link SynthPatch}. Notes beyond {@link #MAX_VOICES} steal the
 * quietest or oldest voice. Output is mono, written to both sides; panning is the mixer's job.
 */
public final class Synthesizer implements Instrument {
    /** Voices that can sound at once. */
    public static final int MAX_VOICES = 16;
    /** The filter coefficients are refreshed every this many frames (control rate). */
    private static final int CONTROL_INTERVAL = 16;
    private static final float A4_HZ = 440f;
    private static final int A4_NOTE = 69;
    private static final float SEMITONES_PER_OCTAVE = 12f;
    private static final float CENTS_PER_SEMITONE = 100f;
    /** Keeps the sum of voices out of clipping at the default volume. */
    private static final float VOICE_HEADROOM = 0.5f;

    private final float sampleRate;
    private final SynthPatch patch;
    private final Voice[] voices = new Voice[MAX_VOICES];
    private long noteCounter;

    public Synthesizer(float sampleRate, SynthPatch patch) {
        if (sampleRate <= 0f) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        this.sampleRate = sampleRate;
        this.patch = Objects.requireNonNull(patch, "patch cannot be null");
        for (int i = 0; i < MAX_VOICES; i++) {
            voices[i] = new Voice();
        }
    }

    /** The patch being played; editing it changes the sound of notes from now on. */
    public SynthPatch getPatch() {
        return patch;
    }

    /** Frequency in Hz of a MIDI note. */
    public static float noteToHz(float note) {
        return (float) (A4_HZ * Math.pow(2.0, (note - A4_NOTE) / SEMITONES_PER_OCTAVE));
    }

    /** Number of voices that are still making sound. */
    public int getActiveVoiceCount() {
        int count = 0;
        for (Voice voice : voices) {
            if (voice.active) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void noteOn(int pitch, float velocity) {
        Voice voice = chooseVoice(pitch);
        voice.start(pitch, Math.min(Math.max(velocity, 0f), 1f), ++noteCounter);
    }

    @Override
    public void noteOff(int pitch) {
        for (Voice voice : voices) {
            if (voice.active && voice.pitch == pitch && !voice.released) {
                voice.release();
            }
        }
    }

    @Override
    public void allNotesOff() {
        for (Voice voice : voices) {
            if (voice.active && !voice.released) {
                voice.release();
            }
        }
    }

    private Voice chooseVoice(int pitch) {
        Voice oldest = null;
        for (Voice voice : voices) {
            if (!voice.active) {
                return voice;
            }
            // Retriggering a note takes over its own voice
            if (voice.pitch == pitch) {
                return voice;
            }
            if (oldest == null || voice.order < oldest.order) {
                oldest = voice;
            }
        }
        return oldest;
    }

    @Override
    public void render(float[] left, float[] right, int offset, int frames) {
        for (Voice voice : voices) {
            if (voice.active) {
                voice.render(left, right, offset, frames);
            }
        }
    }

    /** One sounding note. */
    private final class Voice {
        final Oscillator osc1 = new Oscillator();
        final Oscillator osc2 = new Oscillator();
        final Oscillator lfo = new Oscillator();
        final Adsr envelope = new Adsr(sampleRate);
        final BiquadFilter filter = new BiquadFilter(sampleRate);
        boolean active;
        boolean released;
        int pitch;
        float velocity;
        long order;

        void start(int note, float vel, long sequence) {
            pitch = note;
            velocity = vel;
            order = sequence;
            active = true;
            released = false;
            osc1.setPhase(0.0);
            osc2.setPhase(0.0);
            lfo.setPhase(0.0);
            filter.reset();
            envelope.reset();
            envelope.set(patch.attack, patch.decay, patch.sustain, patch.release);
            envelope.noteOn();
        }

        void release() {
            released = true;
            envelope.noteOff();
        }

        void render(float[] left, float[] right, int offset, int frames) {
            float semitones1 = pitch;
            float semitones2 = pitch + patch.osc2Octave * SEMITONES_PER_OCTAVE + patch.osc2Detune / CENTS_PER_SEMITONE;
            float hz1 = noteToHz(semitones1);
            float hz2 = noteToHz(semitones2);
            float mix2 = Math.min(Math.max(patch.osc2Mix, 0f), 1f);
            float gain = patch.volume * velocity * VOICE_HEADROOM;
            double lfoStep = patch.lfoRate / sampleRate;
            for (int i = 0; i < frames; i++) {
                float lfoValue = patch.lfoTarget == SynthPatch.LfoTarget.NONE
                    ? 0f : lfo.next(Waveform.SINE, lfoStep, 0.5f) * patch.lfoDepth;
                float pitchBend = patch.lfoTarget == SynthPatch.LfoTarget.PITCH
                    ? (float) Math.pow(2.0, lfoValue / SEMITONES_PER_OCTAVE) : 1f;
                float level = envelope.next();
                if (i % CONTROL_INTERVAL == 0) {
                    float octaves = patch.filterEnvOctaves * level;
                    if (patch.lfoTarget == SynthPatch.LfoTarget.CUTOFF) {
                        octaves += lfoValue;
                    }
                    filter.set(patch.filterType, (float) (patch.cutoff * Math.pow(2.0, octaves)), patch.resonance);
                }
                float a = osc1.next(patch.osc1, hz1 * pitchBend / sampleRate, patch.pulseWidth);
                float b = osc2.next(patch.osc2, hz2 * pitchBend / sampleRate, patch.pulseWidth);
                float sample = filter.process(a * (1f - mix2) + b * mix2) * level * gain;
                if (patch.lfoTarget == SynthPatch.LfoTarget.VOLUME) {
                    sample *= 1f - Math.abs(lfoValue);
                }
                left[offset + i] += sample;
                right[offset + i] += sample;
                if (envelope.isFinished()) {
                    active = false;
                    break;
                }
            }
        }
    }
}
