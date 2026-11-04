package io.github.ldev22live.gdxunity.engine.audio;

import java.util.List;

/**
 * A drum machine made of generated sounds (no sample files). Each drum answers to one MIDI note, using the General
 * MIDI numbers; other notes are ignored. Hits ring out on their own, so note-off does nothing.
 */
public final class DrumKit implements Instrument {
    /** The drums and the note that plays each. */
    public enum Drum {
        KICK(36, "Kick"), SNARE(38, "Snare"), CLAP(39, "Clap"), CLOSED_HAT(42, "Closed Hat"),
        OPEN_HAT(46, "Open Hat"), TOM(45, "Tom"), CRASH(49, "Crash");

        private final int note;
        private final String displayName;

        Drum(int note, String displayName) {
            this.note = note;
            this.displayName = displayName;
        }

        public int getNote() {
            return note;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /** Hits that can sound at once. */
    public static final int MAX_HITS = 24;
    private static final float KICK_START_HZ = 160f;
    private static final float KICK_END_HZ = 45f;
    private static final float KICK_PITCH_DROP_SECONDS = 0.06f;
    private static final float KICK_SECONDS = 0.45f;
    private static final float SNARE_SECONDS = 0.22f;
    private static final float SNARE_TONE_HZ = 190f;
    private static final float CLAP_SECONDS = 0.25f;
    private static final float CLAP_BURST_SECONDS = 0.012f;
    private static final float CLAP_BURST_GAP_SECONDS = 0.011f;
    private static final float CLOSED_HAT_SECONDS = 0.06f;
    private static final float OPEN_HAT_SECONDS = 0.35f;
    private static final float TOM_START_HZ = 220f;
    private static final float TOM_END_HZ = 110f;
    private static final float TOM_SECONDS = 0.35f;
    private static final float CRASH_SECONDS = 1.2f;
    /** Noise for hats and crash is high-passed here to keep it bright. */
    private static final float HAT_HIGH_PASS_HZ = 7000f;
    private static final float DECAY_FALL = 8f;

    private final float sampleRate;
    private final Hit[] hits = new Hit[MAX_HITS];
    private int nextHit;

    public DrumKit(float sampleRate) {
        if (sampleRate <= 0f) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        this.sampleRate = sampleRate;
        for (int i = 0; i < MAX_HITS; i++) {
            hits[i] = new Hit();
        }
    }

    /** The drum a note plays, or null. */
    public static Drum drumForNote(int note) {
        for (Drum drum : Drum.values()) {
            if (drum.note == note) {
                return drum;
            }
        }
        return null;
    }

    /** The drums, in the order a step sequencer lists them. */
    public static List<Drum> drums() {
        return List.of(Drum.values());
    }

    @Override
    public void noteOn(int pitch, float velocity) {
        Drum drum = drumForNote(pitch);
        if (drum == null) {
            return;
        }
        Hit hit = null;
        for (Hit candidate : hits) {
            if (!candidate.active) {
                hit = candidate;
                break;
            }
        }
        if (hit == null) {
            // All busy: the oldest slot is taken over, round-robin
            hit = hits[nextHit];
            nextHit = (nextHit + 1) % MAX_HITS;
        }
        hit.start(drum, Math.min(Math.max(velocity, 0f), 1f));
    }

    @Override
    public void noteOff(int pitch) {
        // Drums ring out on their own
    }

    @Override
    public void allNotesOff() {
        for (Hit hit : hits) {
            hit.active = false;
        }
    }

    @Override
    public void render(float[] left, float[] right, int offset, int frames) {
        for (Hit hit : hits) {
            if (hit.active) {
                hit.render(left, right, offset, frames);
            }
        }
    }

    private final class Hit {
        final Oscillator noise = new Oscillator();
        final BiquadFilter highPass = new BiquadFilter(sampleRate);
        final BiquadFilter bandPass = new BiquadFilter(sampleRate);
        boolean active;
        Drum drum;
        float velocity;
        int frame;
        int length;
        double phase;

        void start(Drum which, float vel) {
            drum = which;
            velocity = vel;
            frame = 0;
            phase = 0.0;
            active = true;
            noise.setPhase(0.0);
            highPass.reset();
            bandPass.reset();
            highPass.set(BiquadFilter.Type.HIGH_PASS, HAT_HIGH_PASS_HZ, BiquadFilter.MIN_Q);
            bandPass.set(BiquadFilter.Type.BAND_PASS, 1800f, 0.8f);
            length = (int) (seconds(which) * sampleRate);
        }

        void render(float[] left, float[] right, int offset, int frames) {
            for (int i = 0; i < frames; i++) {
                if (frame >= length) {
                    active = false;
                    return;
                }
                float t = frame / sampleRate;
                float progress = (float) frame / length;
                float decay = (float) Math.exp(-DECAY_FALL * progress);
                float sample = switch (drum) {
                    case KICK -> kick(t, decay);
                    case SNARE -> snare(t, decay);
                    case CLAP -> clap(t);
                    case CLOSED_HAT, OPEN_HAT, CRASH -> highPass.process(noise()) * decay * 0.6f;
                    case TOM -> tom(t, progress, decay);
                };
                sample *= velocity;
                left[offset + i] += sample;
                right[offset + i] += sample;
                frame++;
            }
        }

        private float noise() {
            return noise.next(Waveform.NOISE, 0.0, 0.5f);
        }

        private float tone(float hz) {
            phase += hz / sampleRate;
            if (phase >= 1.0) {
                phase -= 1.0;
            }
            return (float) Math.sin(2.0 * Math.PI * phase);
        }

        private float kick(float t, float decay) {
            float drop = (float) Math.exp(-t / KICK_PITCH_DROP_SECONDS);
            return tone(KICK_END_HZ + (KICK_START_HZ - KICK_END_HZ) * drop) * decay;
        }

        private float snare(float t, float decay) {
            return (bandPass.process(noise()) * 1.4f + tone(SNARE_TONE_HZ) * 0.5f) * decay;
        }

        private float clap(float t) {
            // Three short bursts, then a tail
            float period = CLAP_BURST_SECONDS + CLAP_BURST_GAP_SECONDS;
            float envelope;
            if (t < period * 3f) {
                float inBurst = t % period;
                envelope = inBurst < CLAP_BURST_SECONDS ? 1f - inBurst / CLAP_BURST_SECONDS : 0f;
            } else {
                envelope = (float) Math.exp(-(t - period * 3f) * 18f);
            }
            return bandPass.process(noise()) * envelope * 1.5f;
        }

        private float tom(float t, float progress, float decay) {
            float hz = TOM_END_HZ + (TOM_START_HZ - TOM_END_HZ) * (float) Math.exp(-t / 0.08f);
            return tone(hz) * decay;
        }
    }

    private static float seconds(Drum drum) {
        return switch (drum) {
            case KICK -> KICK_SECONDS;
            case SNARE -> SNARE_SECONDS;
            case CLAP -> CLAP_SECONDS;
            case CLOSED_HAT -> CLOSED_HAT_SECONDS;
            case OPEN_HAT -> OPEN_HAT_SECONDS;
            case TOM -> TOM_SECONDS;
            case CRASH -> CRASH_SECONDS;
        };
    }
}
