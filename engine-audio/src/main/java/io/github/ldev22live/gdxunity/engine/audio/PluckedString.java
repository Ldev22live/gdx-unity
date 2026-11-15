package io.github.ldev22live.gdxunity.engine.audio;

import java.util.Objects;

/**
 * A polyphonic plucked-string instrument driven by a {@link PluckPatch} (TD-075), made with the Karplus-Strong method. Starting a note
 * fills a delay line, one pitch period long, with a burst of (low-passed) noise; each trip round the loop passes a small averaging filter
 * and a gain, so the sound rings at the note's pitch and loses its high end first. The fraction of a sample that the loop is too long for a
 * whole delay is made up with a first-order all-pass filter, which delays without dulling (linear interpolation would take the high end
 * off on every trip, most for notes whose period falls half-way between two samples). Each note's burst comes from a fixed seed of its pitch,
 * so a note always sounds the same and the web version, which uses the same generator, matches it. Output is mono.
 */
public final class PluckedString implements Instrument {
    /** Strings that can sound at once. */
    public static final int MAX_VOICES = 16;
    /** The longest delay line is for this pitch; lower notes are held at it (they sound a little sharp rather than overrunning). */
    private static final float LOWEST_HZ = 20f;
    /** Spare slots in the delay line so interpolation never reads the slot being written. */
    private static final int BUFFER_MARGIN = 4;
    /** The shortest loop, in samples, so very high notes stay a loop and not a click. */
    private static final float MIN_DELAY_SAMPLES = 2f;
    /** The all-pass is used for a delay between this and one more sample, where it behaves best. */
    private static final float ALLPASS_MIN_DELAY = 0.5f;
    /** The loop's averaging filter delays the signal by half a sample at full damping; the delay line is made that much shorter. */
    private static final float DAMPING_DELAY_SAMPLES = 0.5f;
    /** A fall of 60 dB, as a gain. */
    private static final float SIXTY_DB = 0.001f;
    /** A note is cut off this many decay times after it starts (the string is about 90 dB down by then). */
    private static final float END_AFTER_DECAYS = 1.5f;
    /** The shortest decay and release, so a zero setting cannot divide by zero. */
    private static final float MIN_SECONDS = 0.01f;
    /** Lowest share of the noise a burst keeps passing through its low-pass, so a brightness of 0 still has some attack. */
    private static final float MIN_BRIGHTNESS_COEFFICIENT = 0.05f;
    /** Keeps the sum of voices out of clipping at the default volume. */
    private static final float VOICE_HEADROOM = 0.5f;
    private static final int NOISE_SEED = 0x2545F491;
    private static final int NOISE_PITCH_SPREAD = 0x9E3779B1;
    private static final int LCG_MULTIPLIER = 1664525;
    private static final int LCG_INCREMENT = 1013904223;
    private static final float TWO_TO_24 = 16777216f;

    private final float sampleRate;
    private final PluckPatch patch;
    private final int bufferSize;
    private final Voice[] voices = new Voice[MAX_VOICES];
    private long noteCounter;

    public PluckedString(float sampleRate, PluckPatch patch) {
        if (sampleRate <= 0f) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        this.sampleRate = sampleRate;
        this.patch = Objects.requireNonNull(patch, "patch cannot be null");
        this.bufferSize = (int) Math.ceil(sampleRate / LOWEST_HZ) + BUFFER_MARGIN;
        for (int i = 0; i < MAX_VOICES; i++) {
            voices[i] = new Voice();
        }
    }

    /** The patch being played; editing it changes notes from now on. */
    public PluckPatch getPatch() {
        return patch;
    }

    /** Number of strings that are still sounding. */
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
                voice.released = true;
            }
        }
    }

    @Override
    public void allNotesOff() {
        for (Voice voice : voices) {
            if (voice.active) {
                voice.released = true;
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

    /** One string. Owns its delay line, so starting a note allocates nothing. */
    private final class Voice {
        final float[] line = new float[bufferSize];
        boolean active;
        boolean released;
        int pitch;
        float velocity;
        long order;
        int write;
        int wholeDelay;
        float allpassCoefficient;
        float allpassIn;
        float allpassOut;
        float previous;
        float loopGain;
        float releaseGain;
        long age;
        long lastFrame;
        float damping;

        void start(int note, float vel, long sequence) {
            pitch = note;
            velocity = vel;
            order = sequence;
            active = true;
            released = false;
            age = 0;
            releaseGain = 1f;
            previous = 0f;
            allpassIn = 0f;
            allpassOut = 0f;
            write = 0;
            damping = Math.min(Math.max(patch.damping, 0f), 1f);
            float hz = Math.max(Synthesizer.noteToHz(note), LOWEST_HZ);
            float delay = Math.min(Math.max(sampleRate / hz - DAMPING_DELAY_SAMPLES * damping, MIN_DELAY_SAMPLES), bufferSize - BUFFER_MARGIN - 1);
            wholeDelay = (int) Math.floor(delay - ALLPASS_MIN_DELAY);
            float fraction = delay - wholeDelay;
            allpassCoefficient = (1f - fraction) / (1f + fraction);
            float decaySeconds = Math.max(patch.decay, MIN_SECONDS);
            loopGain = (float) Math.pow(SIXTY_DB, 1.0 / (decaySeconds * hz));
            lastFrame = (long) (decaySeconds * END_AFTER_DECAYS * sampleRate);
            pluck();
        }

        /** Fills the delay line with a burst of noise, low-passed more the lower the brightness. */
        private void pluck() {
            float coefficient = MIN_BRIGHTNESS_COEFFICIENT
                + (1f - MIN_BRIGHTNESS_COEFFICIENT) * Math.min(Math.max(patch.brightness, 0f), 1f);
            int state = NOISE_SEED + pitch * NOISE_PITCH_SPREAD;
            float smooth = 0f;
            for (int i = 0; i < line.length; i++) {
                state = state * LCG_MULTIPLIER + LCG_INCREMENT;
                float noise = (state >>> 8) / TWO_TO_24 * 2f - 1f;
                smooth += coefficient * (noise - smooth);
                line[i] = smooth;
            }
        }

        void render(float[] left, float[] right, int offset, int frames) {
            float gain = patch.volume * velocity * VOICE_HEADROOM;
            float releaseStep = (float) Math.pow(SIXTY_DB, 1.0 / (Math.max(patch.release, MIN_SECONDS) * sampleRate));
            int size = line.length;
            for (int i = 0; i < frames; i++) {
                int readAt = write - wholeDelay;
                if (readAt < 0) {
                    readAt += size;
                }
                float whole = line[readAt];
                float delayed = allpassCoefficient * whole + allpassIn - allpassCoefficient * allpassOut;
                allpassIn = whole;
                allpassOut = delayed;
                float filtered = (1f - damping) * delayed + damping * 0.5f * (delayed + previous);
                previous = delayed;
                line[write] = filtered * loopGain;
                write = write + 1 == size ? 0 : write + 1;

                float sample = delayed * releaseGain * gain;
                left[offset + i] += sample;
                right[offset + i] += sample;
                if (released) {
                    releaseGain *= releaseStep;
                }
                age++;
                if (age >= lastFrame || releaseGain < SIXTY_DB) {
                    active = false;
                    break;
                }
            }
        }
    }
}
