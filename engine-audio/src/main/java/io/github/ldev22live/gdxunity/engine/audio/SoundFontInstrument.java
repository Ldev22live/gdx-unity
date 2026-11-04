package io.github.ldev22live.gdxunity.engine.audio;

import java.util.List;
import java.util.Objects;

/** Plays one preset of a {@link SoundFont}: each note starts a voice for every region that covers its key. */
public final class SoundFontInstrument implements Instrument {
    /** Voices that can sound at once. */
    public static final int MAX_VOICES = 48;
    private static final int MAX_MIDI_VELOCITY = 127;
    private static final double CENTS_PER_OCTAVE = 1200.0;
    private static final float OUTPUT_GAIN = 0.7f;

    private final float sampleRate;
    private final SoundFont soundFont;
    private final SoundFont.Preset preset;
    private final Voice[] voices = new Voice[MAX_VOICES];
    private long noteCounter;

    public SoundFontInstrument(float sampleRate, SoundFont soundFont, SoundFont.Preset preset) {
        if (sampleRate <= 0f) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        this.sampleRate = sampleRate;
        this.soundFont = Objects.requireNonNull(soundFont, "soundFont cannot be null");
        this.preset = Objects.requireNonNull(preset, "preset cannot be null");
        for (int i = 0; i < MAX_VOICES; i++) {
            voices[i] = new Voice();
        }
    }

    public SoundFont.Preset getPreset() {
        return preset;
    }

    /** Number of voices still sounding. */
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
        int midiVelocity = Math.max(1, Math.min(Math.round(velocity * MAX_MIDI_VELOCITY), MAX_MIDI_VELOCITY));
        List<SoundFont.Region> regions = preset.regionsFor(pitch, midiVelocity);
        long order = ++noteCounter;
        for (SoundFont.Region region : regions) {
            Voice voice = chooseVoice();
            voice.start(region, pitch, midiVelocity, order);
        }
    }

    private Voice chooseVoice() {
        Voice oldest = voices[0];
        for (Voice voice : voices) {
            if (!voice.active) {
                return voice;
            }
            if (voice.order < oldest.order) {
                oldest = voice;
            }
        }
        return oldest;
    }

    @Override
    public void noteOff(int pitch) {
        for (Voice voice : voices) {
            if (voice.active && voice.key == pitch && !voice.released) {
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

    @Override
    public void render(float[] left, float[] right, int offset, int frames) {
        for (Voice voice : voices) {
            if (voice.active) {
                voice.render(left, right, offset, frames);
            }
        }
    }

    private final class Voice {
        final Adsr envelope = new Adsr(sampleRate);
        SoundFont.Region region;
        boolean active;
        boolean released;
        int key;
        long order;
        double position;
        double step;
        float gainLeft;
        float gainRight;

        void start(SoundFont.Region r, int note, int velocity, long sequence) {
            region = r;
            key = note;
            order = sequence;
            active = true;
            released = false;
            position = r.sampleStart;
            double cents = (note - r.rootKey) * r.scaleTuning * 100.0 + r.tuneCents;
            step = Math.pow(2.0, cents / CENTS_PER_OCTAVE) * r.sampleRate / sampleRate;
            double velocityGain = (double) velocity * velocity / (MAX_MIDI_VELOCITY * MAX_MIDI_VELOCITY);
            double attenuation = Math.pow(10.0, -r.attenuationCentibels / 200.0);
            float gain = (float) (velocityGain * attenuation) * OUTPUT_GAIN;
            double angle = (r.pan + 1.0) * Math.PI / 4.0;
            gainLeft = gain * (float) (Math.cos(angle) * Math.sqrt(2.0));
            gainRight = gain * (float) (Math.sin(angle) * Math.sqrt(2.0));
            envelope.reset();
            envelope.set(r.attack, r.decay, r.sustainLevel, r.release);
            envelope.noteOn();
        }

        void release() {
            released = true;
            envelope.noteOff();
        }

        void render(float[] left, float[] right, int offset, int frames) {
            short[] pcm = soundFont.getSamples();
            SoundFont.Region r = region;
            for (int i = 0; i < frames; i++) {
                boolean looping = r.loops && !(r.loopsUntilRelease && released);
                if (looping) {
                    while (position >= r.loopEnd) {
                        position -= r.loopEnd - r.loopStart;
                    }
                }
                int index = (int) position;
                // A looping sample never reaches its end; the point after the loop end is only read to interpolate
                if ((!looping && index + 1 >= r.sampleEnd) || index + 1 >= pcm.length) {
                    active = false;
                    return;
                }
                float frac = (float) (position - index);
                float sample = (pcm[index] + (pcm[index + 1] - pcm[index]) * frac) / 32768f;
                float level = envelope.next();
                left[offset + i] += sample * level * gainLeft;
                right[offset + i] += sample * level * gainRight;
                position += step;
                if (envelope.isFinished()) {
                    active = false;
                    return;
                }
            }
        }
    }
}
