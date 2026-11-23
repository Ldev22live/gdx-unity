package io.github.ldev22live.gdxunity.engine.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BiquadFilterTest {
    private static double gainAt(BiquadFilter.Type type, float cutoff, double hz) {
        BiquadFilter filter = new BiquadFilter(AudioTestSupport.RATE);
        filter.set(type, cutoff, 0.707f);
        float[] input = AudioTestSupport.sine(hz, 20000);
        float[] output = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = filter.process(input[i]);
        }
        return AudioTestSupport.rms(output, 10000, 20000) / AudioTestSupport.rms(input, 10000, 20000);
    }

    @Test
    void shouldPassLowAndStopHighFrequenciesInALowPass() {
        assertTrue(gainAt(BiquadFilter.Type.LOW_PASS, 1000f, 100.0) > 0.95);
        assertTrue(gainAt(BiquadFilter.Type.LOW_PASS, 1000f, 8000.0) < 0.05);
    }

    @Test
    void shouldPassHighAndStopLowFrequenciesInAHighPass() {
        assertTrue(gainAt(BiquadFilter.Type.HIGH_PASS, 1000f, 8000.0) > 0.95);
        assertTrue(gainAt(BiquadFilter.Type.HIGH_PASS, 1000f, 100.0) < 0.05);
    }

    @Test
    void shouldPassOnlyANarrowBandInABandPass() {
        assertTrue(gainAt(BiquadFilter.Type.BAND_PASS, 1000f, 1000.0) > 0.9);
        assertTrue(gainAt(BiquadFilter.Type.BAND_PASS, 1000f, 100.0) < 0.2);
        assertTrue(gainAt(BiquadFilter.Type.BAND_PASS, 1000f, 10000.0) < 0.2);
    }

    @Test
    void shouldStayStableWithTheCutoffFarAboveNyquistAndHighResonance() {
        BiquadFilter filter = new BiquadFilter(AudioTestSupport.RATE);
        filter.set(BiquadFilter.Type.LOW_PASS, 1_000_000f, 100f);
        float[] input = AudioTestSupport.sine(440.0, 5000);
        float last = 0f;
        for (float sample : input) {
            last = filter.process(sample);
            assertTrue(Math.abs(last) < 10f);
        }
    }

    @Test
    void shouldForgetItsHistoryOnReset() {
        BiquadFilter filter = new BiquadFilter(AudioTestSupport.RATE);
        filter.set(BiquadFilter.Type.LOW_PASS, 500f, 5f);
        for (int i = 0; i < 100; i++) {
            filter.process(1f);
        }
        filter.reset();
        assertTrue(Math.abs(filter.process(0f)) < 1e-9f);
    }
}
