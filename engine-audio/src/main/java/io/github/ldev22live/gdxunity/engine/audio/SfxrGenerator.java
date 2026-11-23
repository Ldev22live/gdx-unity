package io.github.ldev22live.gdxunity.engine.audio;

import java.util.Objects;
import java.util.Random;

/**
 * Renders a retro sound effect from {@link SfxrParams}: a pitch-sliding oscillator with an envelope, low-pass and
 * high-pass filters, vibrato, a pitch jump and a phaser, eight-times oversampled. The result is fully determined
 * by the parameters (the noise uses a fixed seed), so a sound can be regenerated instead of stored.
 */
public final class SfxrGenerator {
    /** Output sample rate, Hz. */
    public static final int SAMPLE_RATE = 44100;
    private static final int OVERSAMPLE = 8;
    private static final int NOISE_SIZE = 32;
    private static final int PHASER_SIZE = 1024;
    private static final float ENVELOPE_SCALE = 100000f;
    private static final long NOISE_SEED = 0x5EEDL;
    /** The sound stops after this many seconds even if the envelope has not ended. */
    private static final float MAX_SECONDS = 10f;
    private static final float OUTPUT_GAIN = 2f;

    private SfxrGenerator() { }

    /** Renders the sound as mono audio. */
    public static SampleData generate(SfxrParams p) {
        Objects.requireNonNull(p, "p cannot be null");
        State s = new State(p);
        int maxFrames = (int) (MAX_SECONDS * SAMPLE_RATE);
        float[] buffer = new float[Math.min(maxFrames, s.totalEnvelopeFrames() + 1)];
        int frames = 0;
        while (frames < buffer.length && s.playing) {
            buffer[frames++] = s.next();
        }
        float[] trimmed = new float[frames];
        System.arraycopy(buffer, 0, trimmed, 0, frames);
        return new SampleData(trimmed, SAMPLE_RATE);
    }

    private static final class State {
        final SfxrParams p;
        final Random noise = new Random(NOISE_SEED);
        final float[] noiseBuffer = new float[NOISE_SIZE];
        final float[] phaserBuffer = new float[PHASER_SIZE];
        final int[] envelopeLength = new int[3];
        final float maxPeriod;
        boolean playing = true;
        double period;
        double slide;
        double deltaSlide;
        double duty;
        double dutySlide;
        double arpMultiplier;
        int arpTime;
        int arpLimit;
        int repeatTime;
        int repeatLimit;
        double vibratoPhase;
        double vibratoSpeed;
        double vibratoDepth;
        int phase;
        int envelopeStage;
        int envelopeTime;
        double lowPass;
        double lowPassDelta;
        double lowPassWidth;
        double lowPassWidthDelta;
        double lowPassDamping;
        boolean lowPassOn;
        double highPass;
        double highPassCoefficient;
        double highPassDelta;
        double phaserOffset;
        double phaserDelta;
        int phaserIndex;
        int phaserPosition;

        State(SfxrParams params) {
            p = params;
            maxPeriod = (float) (100.0 / ((double) p.minFreq * p.minFreq + 0.001));
            envelopeLength[0] = (int) (p.attack * p.attack * ENVELOPE_SCALE);
            envelopeLength[1] = (int) (p.sustain * p.sustain * ENVELOPE_SCALE);
            envelopeLength[2] = (int) (p.decay * p.decay * ENVELOPE_SCALE);
            for (int i = 0; i < NOISE_SIZE; i++) {
                noiseBuffer[i] = noise.nextFloat() * 2f - 1f;
            }
            restart();
            lowPassOn = p.lowPassCutoff != 1f;
            lowPassWidth = Math.pow(p.lowPassCutoff, 3.0) * 0.1;
            lowPassWidthDelta = 1.0 + p.lowPassSweep * 0.0001;
            lowPassDamping = Math.min(0.8, 5.0 / (1.0 + Math.pow(p.lowPassResonance, 2.0) * 20.0) * (0.01 + lowPassWidth));
            highPassCoefficient = Math.pow(p.highPassCutoff, 2.0) * 0.1;
            highPassDelta = 1.0 + p.highPassSweep * 0.0003;
            vibratoSpeed = Math.pow(p.vibratoSpeed, 2.0) * 0.01;
            vibratoDepth = p.vibratoDepth * 0.5;
            phaserOffset = Math.pow(p.phaserOffset, 2.0) * 1020.0 * Math.signum(p.phaserOffset);
            phaserDelta = Math.pow(p.phaserSweep, 2.0) * Math.signum(p.phaserSweep);
            repeatLimit = p.repeatSpeed == 0f ? 0 : (int) (Math.pow(1.0 - p.repeatSpeed, 2.0) * 20000.0 + 32.0);
        }

        /** (Re)starts the pitch parts; a repeat restarts these but not the envelope. */
        void restart() {
            period = 100.0 / (Math.pow(p.baseFreq, 2.0) + 0.001);
            slide = 1.0 - Math.pow(p.slide, 3.0) * 0.01;
            deltaSlide = -Math.pow(p.deltaSlide, 3.0) * 0.000001;
            duty = 0.5 - p.duty * 0.5;
            dutySlide = -p.dutySweep * 0.00005;
            arpMultiplier = p.arpeggioChange >= 0f
                ? 1.0 - Math.pow(p.arpeggioChange, 2.0) * 0.9 : 1.0 + Math.pow(p.arpeggioChange, 2.0) * 10.0;
            arpTime = 0;
            arpLimit = p.arpeggioSpeed >= 1f ? 0 : (int) (Math.pow(1.0 - p.arpeggioSpeed, 2.0) * 20000.0 + 32.0);
        }

        int totalEnvelopeFrames() {
            return envelopeLength[0] + envelopeLength[1] + envelopeLength[2];
        }

        float next() {
            repeatTime++;
            if (repeatLimit != 0 && repeatTime >= repeatLimit) {
                repeatTime = 0;
                restart();
            }
            arpTime++;
            if (arpLimit != 0 && arpTime >= arpLimit) {
                arpLimit = 0;
                period *= arpMultiplier;
            }
            slide += deltaSlide;
            period *= slide;
            if (period > maxPeriod) {
                period = maxPeriod;
                if (p.minFreq > 0f) {
                    playing = false;
                }
            }
            double effectivePeriod = period;
            if (vibratoDepth > 0.0) {
                vibratoPhase += vibratoSpeed;
                effectivePeriod = period * (1.0 + Math.sin(vibratoPhase) * vibratoDepth);
            }
            int periodFrames = Math.max((int) effectivePeriod, 8);
            duty = Math.min(Math.max(duty + dutySlide, 0.0), 0.5);
            envelopeTime++;
            if (envelopeTime > envelopeLength[envelopeStage]) {
                envelopeTime = 0;
                envelopeStage++;
                if (envelopeStage == 3) {
                    playing = false;
                    envelopeStage = 2;
                }
            }
            double envelope = envelopeVolume();
            phaserOffset += phaserDelta;
            phaserIndex = Math.min(Math.abs((int) phaserOffset), PHASER_SIZE - 1);
            if (highPassDelta != 0.0) {
                highPassCoefficient = Math.min(Math.max(highPassCoefficient * highPassDelta, 0.00001), 0.1);
            }
            double mixed = 0.0;
            for (int over = 0; over < OVERSAMPLE; over++) {
                phase++;
                if (phase >= periodFrames) {
                    phase %= periodFrames;
                    if (p.shape == SfxrParams.Shape.NOISE) {
                        for (int i = 0; i < NOISE_SIZE; i++) {
                            noiseBuffer[i] = noise.nextFloat() * 2f - 1f;
                        }
                    }
                }
                double fraction = (double) phase / periodFrames;
                double raw = switch (p.shape) {
                    case SQUARE -> fraction < duty ? 0.5 : -0.5;
                    case SAW -> 1.0 - fraction * 2.0;
                    case SINE -> Math.sin(fraction * 2.0 * Math.PI);
                    case NOISE -> noiseBuffer[phase * NOISE_SIZE / periodFrames];
                };
                double previousLowPass = lowPass;
                lowPassWidth = Math.min(Math.max(lowPassWidth * lowPassWidthDelta, 0.0), 0.1);
                if (lowPassOn) {
                    lowPassDelta += (raw - lowPass) * lowPassWidth;
                    lowPassDelta -= lowPassDelta * lowPassDamping;
                } else {
                    lowPass = raw;
                    lowPassDelta = 0.0;
                }
                lowPass += lowPassDelta;
                highPass += lowPass - previousLowPass;
                highPass -= highPass * highPassCoefficient;
                phaserBuffer[phaserPosition & (PHASER_SIZE - 1)] = (float) highPass;
                double phased = highPass + phaserBuffer[(phaserPosition - phaserIndex + PHASER_SIZE) & (PHASER_SIZE - 1)];
                phaserPosition = (phaserPosition + 1) & (PHASER_SIZE - 1);
                mixed += phased * envelope;
            }
            double out = mixed / OVERSAMPLE * p.volume * OUTPUT_GAIN;
            return (float) Math.min(Math.max(out, -1.0), 1.0);
        }

        double envelopeVolume() {
            int length = Math.max(envelopeLength[envelopeStage], 1);
            double t = (double) envelopeTime / length;
            return switch (envelopeStage) {
                case 0 -> t;
                case 1 -> 1.0 + (1.0 - t) * 2.0 * p.punch;
                default -> 1.0 - t;
            };
        }
    }
}
