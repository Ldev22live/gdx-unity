package io.github.ldev22live.gdxunity.engine.audio;

import java.util.Objects;

/**
 * A polyphonic two-operator FM synthesizer driven by an {@link FmPatch} (TD-075). Each voice bends the phase of a sine carrier with a
 * sine modulator; the bend starts at the patch's index and falls towards its sustained share. Notes beyond {@link #MAX_VOICES} take
 * over the oldest voice. Output is mono, written to both sides.
 */
public final class FmSynthesizer implements Instrument {
    /** Voices that can sound at once. */
    public static final int MAX_VOICES = 16;
    /** Keeps the sum of voices out of clipping at the default volume. */
    private static final float VOICE_HEADROOM = 0.5f;
    /** The modulator cannot run faster than half the sample rate; beyond that it would only fold back as noise. */
    private static final double MAX_PHASE_STEP = 0.5;
    /** Shortest modulation fall, so a zero setting cannot divide by zero. */
    private static final float MIN_INDEX_DECAY = 0.001f;
    private static final double TWO_PI = 2.0 * Math.PI;

    private final float sampleRate;
    private final FmPatch patch;
    private final Voice[] voices = new Voice[MAX_VOICES];
    private long noteCounter;

    public FmSynthesizer(float sampleRate, FmPatch patch) {
        if (sampleRate <= 0f) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        this.sampleRate = sampleRate;
        this.patch = Objects.requireNonNull(patch, "patch cannot be null");
        for (int i = 0; i < MAX_VOICES; i++) {
            voices[i] = new Voice();
        }
    }

    /** The patch being played; editing it changes the sound from the next block on. */
    public FmPatch getPatch() {
        return patch;
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
        chooseVoice(pitch).start(pitch, Math.min(Math.max(velocity, 0f), 1f), ++noteCounter);
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
            if (!voice.active || voice.pitch == pitch) {
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
        final Adsr envelope = new Adsr(sampleRate);
        boolean active;
        boolean released;
        int pitch;
        float velocity;
        long order;
        double carrierPhase;
        double modulatorPhase;
        /** 1 at the start of the note, falling towards 0; the modulation depth follows it. */
        float fall;

        void start(int note, float vel, long sequence) {
            pitch = note;
            velocity = vel;
            order = sequence;
            active = true;
            released = false;
            carrierPhase = 0.0;
            modulatorPhase = 0.0;
            fall = 1f;
            envelope.reset();
            envelope.set(patch.attack, patch.decay, patch.sustain, patch.release);
            envelope.noteOn();
        }

        void release() {
            released = true;
            envelope.noteOff();
        }

        void render(float[] left, float[] right, int offset, int frames) {
            double hz = Synthesizer.noteToHz(pitch);
            double carrierStep = Math.min(hz / sampleRate, MAX_PHASE_STEP);
            double modulatorStep = Math.min(hz * patch.ratio / sampleRate, MAX_PHASE_STEP);
            float fallStep = (float) Math.exp(-1.0 / (Math.max(patch.indexDecay, MIN_INDEX_DECAY) * sampleRate));
            float share = Math.min(Math.max(patch.indexSustain, 0f), 1f);
            float gain = patch.volume * velocity * VOICE_HEADROOM;
            for (int i = 0; i < frames; i++) {
                float level = envelope.next();
                fall *= fallStep;
                double depth = patch.index * (share + (1f - share) * fall);
                double bend = depth * Math.sin(TWO_PI * modulatorPhase);
                float sample = (float) Math.sin(TWO_PI * carrierPhase + bend) * level * gain;
                left[offset + i] += sample;
                right[offset + i] += sample;
                carrierPhase += carrierStep;
                if (carrierPhase >= 1.0) {
                    carrierPhase -= 1.0;
                }
                modulatorPhase += modulatorStep;
                if (modulatorPhase >= 1.0) {
                    modulatorPhase -= 1.0;
                }
                if (envelope.isFinished()) {
                    active = false;
                    break;
                }
            }
        }
    }
}
