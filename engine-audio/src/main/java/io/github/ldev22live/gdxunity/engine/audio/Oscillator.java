package io.github.ldev22live.gdxunity.engine.audio;

import java.util.Objects;

/**
 * One band-limited oscillator. Saw and square use PolyBLEP to cut aliasing; noise is a deterministic xorshift
 * generator so renders are repeatable.
 */
public final class Oscillator {
    private static final long NOISE_SEED = 0x2545F4914F6CDD1DL;
    private double phase;
    private long noiseState = NOISE_SEED;

    /** Starts the cycle at {@code cycles} (0..1), so voices can start in phase or spread. */
    public void setPhase(double cycles) {
        phase = cycles - Math.floor(cycles);
    }

    /**
     * Next sample in -1..1.
     *
     * @param waveform   shape
     * @param phaseStep  cycles per sample (frequency / sample rate), 0..0.5
     * @param pulseWidth share of the cycle a square wave is high, clamped to 0.05..0.95
     */
    public float next(Waveform waveform, double phaseStep, float pulseWidth) {
        Objects.requireNonNull(waveform, "waveform cannot be null");
        double dt = Math.min(Math.max(phaseStep, 0.0), 0.5);
        double t = phase;
        double value;
        switch (waveform) {
            case SINE -> value = Math.sin(2.0 * Math.PI * t);
            case SAW -> value = 2.0 * t - 1.0 - polyBlep(t, dt);
            case SQUARE -> {
                double width = Math.min(Math.max(pulseWidth, 0.05f), 0.95f);
                value = t < width ? 1.0 : -1.0;
                value += polyBlep(t, dt);
                value -= polyBlep((t - width + 1.0) % 1.0, dt);
            }
            case TRIANGLE -> value = 4.0 * Math.abs(t - 0.5) - 1.0;
            case NOISE -> value = nextNoise();
            default -> throw new IllegalArgumentException("Unknown waveform " + waveform);
        }
        phase += dt;
        if (phase >= 1.0) {
            phase -= 1.0;
        }
        return (float) value;
    }

    private double nextNoise() {
        noiseState ^= noiseState << 13;
        noiseState ^= noiseState >>> 7;
        noiseState ^= noiseState << 17;
        return (noiseState >>> 11) * (2.0 / (1L << 53)) - 1.0;
    }

    private static double polyBlep(double t, double dt) {
        if (dt <= 0.0) {
            return 0.0;
        }
        if (t < dt) {
            double x = t / dt;
            return x + x - x * x - 1.0;
        }
        if (t > 1.0 - dt) {
            double x = (t - 1.0) / dt;
            return x * x + x + x + 1.0;
        }
        return 0.0;
    }
}
