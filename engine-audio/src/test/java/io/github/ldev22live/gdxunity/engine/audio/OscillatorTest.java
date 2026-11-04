package io.github.ldev22live.gdxunity.engine.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OscillatorTest {
    private static float[] run(Waveform waveform, double hz, float pulse, int frames) {
        Oscillator oscillator = new Oscillator();
        float[] data = new float[frames];
        for (int i = 0; i < frames; i++) {
            data[i] = oscillator.next(waveform, hz / AudioTestSupport.RATE, pulse);
        }
        return data;
    }

    @Test
    void shouldProduceTheRequestedFrequencyForEveryPitchedWaveform() {
        for (Waveform waveform : new Waveform[] {Waveform.SINE, Waveform.SAW, Waveform.SQUARE, Waveform.TRIANGLE}) {
            float[] data = run(waveform, 441.0, 0.5f, 44100);
            assertEquals(441.0, AudioTestSupport.frequency(data, 0, data.length), 2.0, waveform.name());
        }
    }

    @Test
    void shouldStayWithinAFewPercentOfFullScale() {
        for (Waveform waveform : Waveform.values()) {
            float[] data = run(waveform, 523.0, 0.5f, 20000);
            assertTrue(AudioTestSupport.peak(data) <= 1.2f, waveform + " peak " + AudioTestSupport.peak(data));
            assertTrue(AudioTestSupport.peak(data) > 0.8f, waveform + " is too quiet");
        }
    }

    @Test
    void shouldSpendTheRequestedShareOfTheCycleHighForASquareWave() {
        float[] data = run(Waveform.SQUARE, 100.0, 0.25f, 44100);
        int high = 0;
        for (float value : data) {
            if (value > 0f) {
                high++;
            }
        }
        assertEquals(0.25, (double) high / data.length, 0.02);
    }

    @Test
    void shouldGiveARepeatableNoiseSequence() {
        assertArrayEquals(run(Waveform.NOISE, 0, 0.5f, 2000), run(Waveform.NOISE, 0, 0.5f, 2000));
    }

    @Test
    void shouldHaveNoMeanOffsetOverWholeCycles() {
        for (Waveform waveform : new Waveform[] {Waveform.SINE, Waveform.SAW, Waveform.TRIANGLE}) {
            float[] data = run(waveform, 441.0, 0.5f, 44100);
            double sum = 0.0;
            for (float value : data) {
                sum += value;
            }
            assertEquals(0.0, sum / data.length, 0.01, waveform.name());
        }
    }

    @Test
    void shouldRoundOffTheJumpOfASawWaveToReduceAliasing() {
        // A naive saw jumps from +1 to -1 in one sample; the band-limited one passes through intermediate values
        float[] data = run(Waveform.SAW, 3000.0, 0.5f, 4000);
        boolean sawIntermediate = false;
        for (int i = 1; i < data.length; i++) {
            if (data[i - 1] - data[i] > 0.5f && data[i - 1] - data[i] < 1.9f) {
                sawIntermediate = true;
            }
        }
        assertTrue(sawIntermediate, "expected a smoothed edge");
    }

    @Test
    void shouldStartAtTheRequestedPhase() {
        Oscillator oscillator = new Oscillator();
        oscillator.setPhase(0.25);
        assertEquals(1.0f, oscillator.next(Waveform.SINE, 0.0, 0.5f), 1e-5f);
    }
}
