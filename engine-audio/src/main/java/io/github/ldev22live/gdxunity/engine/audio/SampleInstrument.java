package io.github.ldev22live.gdxunity.engine.audio;

import java.util.Objects;

/**
 * Plays a recorded or generated sample at the pitch of the note: the sample sounds as recorded at
 * {@link #getRootNote()} and is sped up or slowed down for other notes (linear interpolation).
 */
public final class SampleInstrument implements Instrument {
    /** Voices that can sound at once. */
    public static final int MAX_VOICES = 16;
    /** Fade applied when a held sample is released, in seconds, so the cut does not click. */
    private static final float RELEASE_SECONDS = 0.02f;
    private static final float SEMITONES_PER_OCTAVE = 12f;
    /** The sample's own pitch when the note equals the root note: middle C. */
    public static final int DEFAULT_ROOT_NOTE = 60;

    private final float sampleRate;
    private final Voice[] voices = new Voice[MAX_VOICES];
    private SampleData sample;
    private int rootNote = DEFAULT_ROOT_NOTE;
    private boolean oneShot;
    private float volume = 0.8f;

    public SampleInstrument(float sampleRate, SampleData sample) {
        if (sampleRate <= 0f) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        this.sampleRate = sampleRate;
        this.sample = Objects.requireNonNull(sample, "sample cannot be null");
        for (int i = 0; i < MAX_VOICES; i++) {
            voices[i] = new Voice();
        }
    }

    public SampleData getSample() {
        return sample;
    }

    /** Replaces the sample; notes already sounding stop. */
    public void setSample(SampleData sample) {
        allNotesOff();
        for (Voice voice : voices) {
            voice.active = false;
        }
        this.sample = Objects.requireNonNull(sample, "sample cannot be null");
    }

    public int getRootNote() {
        return rootNote;
    }

    /** The note at which the sample plays unchanged. */
    public void setRootNote(int rootNote) {
        this.rootNote = rootNote;
    }

    public boolean isOneShot() {
        return oneShot;
    }

    /** A one-shot plays to its end whatever the note length (drum hits); otherwise note-off fades it out. */
    public void setOneShot(boolean oneShot) {
        this.oneShot = oneShot;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = Math.min(Math.max(volume, 0f), 1f);
    }

    @Override
    public void noteOn(int pitch, float velocity) {
        Voice chosen = null;
        for (Voice voice : voices) {
            if (!voice.active) {
                chosen = voice;
                break;
            }
        }
        if (chosen == null) {
            chosen = voices[0];
            for (Voice voice : voices) {
                if (voice.position > chosen.position) {
                    chosen = voice;
                }
            }
        }
        chosen.start(pitch, Math.min(Math.max(velocity, 0f), 1f));
    }

    @Override
    public void noteOff(int pitch) {
        if (oneShot) {
            return;
        }
        for (Voice voice : voices) {
            if (voice.active && voice.pitch == pitch) {
                voice.releasing = true;
            }
        }
    }

    @Override
    public void allNotesOff() {
        for (Voice voice : voices) {
            if (voice.active) {
                voice.releasing = true;
            }
        }
    }

    @Override
    public void render(float[] left, float[] right, int offset, int frames) {
        for (Voice voice : voices) {
            if (voice.active) {
                voice.render(left, right, offset, frames);
            }
        }
    }

    private final class Voice {
        boolean active;
        boolean releasing;
        int pitch;
        float velocity;
        double position;
        double step;
        float fade = 1f;

        void start(int note, float vel) {
            pitch = note;
            velocity = vel;
            position = 0.0;
            fade = 1f;
            releasing = false;
            active = true;
            step = Math.pow(2.0, (note - rootNote) / SEMITONES_PER_OCTAVE) * sample.getSampleRate() / sampleRate;
        }

        void render(float[] left, float[] right, int offset, int frames) {
            float[] sourceLeft = sample.getLeft();
            float[] sourceRight = sample.getRight();
            int last = sourceLeft.length - 1;
            float fadeStep = 1f / (RELEASE_SECONDS * sampleRate);
            float gain = volume * velocity;
            for (int i = 0; i < frames; i++) {
                int index = (int) position;
                if (index >= last) {
                    active = false;
                    return;
                }
                float frac = (float) (position - index);
                float l = sourceLeft[index] + (sourceLeft[index + 1] - sourceLeft[index]) * frac;
                float r = sourceRight[index] + (sourceRight[index + 1] - sourceRight[index]) * frac;
                if (releasing) {
                    fade -= fadeStep;
                    if (fade <= 0f) {
                        active = false;
                        return;
                    }
                }
                left[offset + i] += l * gain * fade;
                right[offset + i] += r * gain * fade;
                position += step;
            }
        }
    }
}
