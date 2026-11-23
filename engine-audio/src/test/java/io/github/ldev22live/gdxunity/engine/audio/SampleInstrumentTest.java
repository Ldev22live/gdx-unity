package io.github.ldev22live.gdxunity.engine.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleInstrumentTest {
    private static SampleInstrument instrument(float[] data) {
        SampleInstrument instrument = new SampleInstrument(AudioTestSupport.RATE, new SampleData(data, 44100));
        instrument.setVolume(1f);
        return instrument;
    }

    @Test
    void shouldPlayTheSampleUnchangedAtItsRootNote() {
        float[] source = AudioTestSupport.sine(300.0, 4000);
        SampleInstrument instrument = instrument(source);
        instrument.noteOn(60, 1f);
        float[] out = AudioTestSupport.render(instrument, 3000);
        assertArrayEquals(java.util.Arrays.copyOf(source, 3000), out, 1e-4f);
    }

    @Test
    void shouldPlayAnOctaveHigherTwiceAsFast() {
        SampleInstrument instrument = instrument(AudioTestSupport.sine(300.0, 20000));
        instrument.noteOn(72, 1f);
        float[] out = AudioTestSupport.render(instrument, 8000);
        assertEquals(600.0, AudioTestSupport.frequency(out, 0, 8000), 4.0);
    }

    @Test
    void shouldEndWhenTheSampleEnds() {
        SampleInstrument instrument = instrument(AudioTestSupport.sine(300.0, 500));
        instrument.noteOn(60, 1f);
        float[] out = AudioTestSupport.render(instrument, 2000);
        assertEquals(0f, AudioTestSupport.peak(out, 600, 2000));
    }

    @Test
    void shouldFadeOutOnNoteOffUnlessOneShot() {
        float[] source = new float[20000];
        java.util.Arrays.fill(source, 0.5f);
        SampleInstrument held = instrument(source);
        held.noteOn(60, 1f);
        AudioTestSupport.render(held, 500);
        held.noteOff(60);
        float[] out = AudioTestSupport.render(held, 3000);
        assertEquals(0f, AudioTestSupport.peak(out, 2000, 3000));

        SampleInstrument oneShot = instrument(source);
        oneShot.setOneShot(true);
        oneShot.noteOn(60, 1f);
        AudioTestSupport.render(oneShot, 500);
        oneShot.noteOff(60);
        assertTrue(AudioTestSupport.peak(AudioTestSupport.render(oneShot, 3000), 2000, 3000) > 0.4f);
    }

    @Test
    void shouldPlayStereoSamplesOnTheirOwnSides() {
        float[] left = new float[1000];
        float[] right = new float[1000];
        java.util.Arrays.fill(left, 0.5f);
        SampleInstrument instrument = new SampleInstrument(AudioTestSupport.RATE, new SampleData(left, right, 44100));
        instrument.setVolume(1f);
        instrument.noteOn(60, 1f);
        float[] outLeft = new float[200];
        float[] outRight = new float[200];
        instrument.render(outLeft, outRight, 0, 200);
        assertEquals(0.5f, outLeft[10], 1e-4f);
        assertEquals(0f, outRight[10], 1e-6f);
    }

    @Test
    void shouldResampleWhenTheSampleRateDiffers() {
        SampleInstrument instrument = new SampleInstrument(AudioTestSupport.RATE,
            new SampleData(AudioTestSupport.sine(300.0, 40000), 22050));
        instrument.setVolume(1f);
        instrument.noteOn(60, 1f);
        float[] out = AudioTestSupport.render(instrument, 20000);
        // A 22.05 kHz sample holding "300 Hz at 44.1 kHz" plays at half the pitch
        assertEquals(150.0, AudioTestSupport.frequency(out, 0, 20000), 2.0);
    }
}
