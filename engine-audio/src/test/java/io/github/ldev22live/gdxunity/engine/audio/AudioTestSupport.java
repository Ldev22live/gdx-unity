package io.github.ldev22live.gdxunity.engine.audio;

/** Helpers for measuring rendered audio. */
final class AudioTestSupport {
    static final float RATE = 44100f;

    private AudioTestSupport() { }

    /** Renders {@code frames} frames of an instrument into a mono array (left side). */
    static float[] render(Instrument instrument, int frames) {
        float[] left = new float[frames];
        float[] right = new float[frames];
        instrument.render(left, right, 0, frames);
        return left;
    }

    static float peak(float[] data) {
        return peak(data, 0, data.length);
    }

    static float peak(float[] data, int from, int to) {
        float peak = 0f;
        for (int i = from; i < to; i++) {
            peak = Math.max(peak, Math.abs(data[i]));
        }
        return peak;
    }

    static double rms(float[] data, int from, int to) {
        double sum = 0.0;
        for (int i = from; i < to; i++) {
            sum += (double) data[i] * data[i];
        }
        return Math.sqrt(sum / Math.max(to - from, 1));
    }

    /** Frequency estimated from upward zero crossings between {@code from} and {@code to}. */
    static double frequency(float[] data, int from, int to) {
        int crossings = 0;
        int first = -1;
        int last = -1;
        for (int i = from + 1; i < to; i++) {
            if (data[i - 1] < 0f && data[i] >= 0f) {
                crossings++;
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        if (crossings < 2) {
            return 0.0;
        }
        return (crossings - 1) * RATE / (last - first);
    }

    /**
     * The lag, in samples, at which the signal best matches itself shifted, searched within 20% of the lag expected for {@code hz}
     * over the window {@code from..to}. Unlike zero crossings it reads the pitch of a wave that crosses zero more than once a cycle.
     */
    static double period(float[] data, int from, int to, double hz) {
        double expected = RATE / hz;
        int bestLag = 0;
        double best = -2.0;
        for (int lag = (int) (expected * 0.8); lag <= (int) (expected * 1.2) + 1; lag++) {
            double sum = 0.0;
            double energyA = 0.0;
            double energyB = 0.0;
            for (int i = from; i < to; i++) {
                sum += (double) data[i] * data[i + lag];
                energyA += (double) data[i] * data[i];
                energyB += (double) data[i + lag] * data[i + lag];
            }
            double score = sum / Math.sqrt(energyA * energyB + 1e-12);
            if (score > best) {
                best = score;
                bestLag = lag;
            }
        }
        return bestLag;
    }

    /** A pure sine of {@code hz} lasting {@code frames}. */
    static float[] sine(double hz, int frames) {
        float[] data = new float[frames];
        for (int i = 0; i < frames; i++) {
            data[i] = (float) Math.sin(2.0 * Math.PI * hz * i / RATE);
        }
        return data;
    }

    static boolean allFinite(float[] data) {
        for (float value : data) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                return false;
            }
        }
        return true;
    }
}
