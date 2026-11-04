package io.github.ldev22live.gdxunity.engine.audio;

import com.badlogic.gdx.utils.JsonReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynthesizerTest {
    private static SynthPatch cleanSine() {
        SynthPatch patch = new SynthPatch();
        patch.osc1 = Waveform.SINE;
        patch.osc2Mix = 0f;
        patch.cutoff = 20000f;
        patch.attack = 0.001f;
        patch.sustain = 1f;
        patch.release = 0.05f;
        patch.volume = 1f;
        return patch;
    }

    @Test
    void shouldConvertMidiNotesToFrequencies() {
        assertEquals(440f, Synthesizer.noteToHz(69), 0.01f);
        assertEquals(261.63f, Synthesizer.noteToHz(60), 0.01f);
        assertEquals(880f, Synthesizer.noteToHz(81), 0.01f);
    }

    @Test
    void shouldPlayTheRequestedPitch() {
        Synthesizer synth = new Synthesizer(AudioTestSupport.RATE, cleanSine());
        synth.noteOn(69, 1f);
        float[] data = AudioTestSupport.render(synth, 22050);
        assertEquals(440.0, AudioTestSupport.frequency(data, 2000, 22050), 3.0);
    }

    @Test
    void shouldBeSilentUntilANoteStartsAndAfterItEnds() {
        Synthesizer synth = new Synthesizer(AudioTestSupport.RATE, cleanSine());
        assertEquals(0f, AudioTestSupport.peak(AudioTestSupport.render(synth, 1000)));
        synth.noteOn(60, 1f);
        AudioTestSupport.render(synth, 2000);
        assertEquals(1, synth.getActiveVoiceCount());
        synth.noteOff(60);
        AudioTestSupport.render(synth, 22050);
        assertEquals(0, synth.getActiveVoiceCount());
        assertEquals(0f, AudioTestSupport.peak(AudioTestSupport.render(synth, 1000)));
    }

    @Test
    void shouldScaleLoudnessWithVelocity() {
        Synthesizer loud = new Synthesizer(AudioTestSupport.RATE, cleanSine());
        Synthesizer quiet = new Synthesizer(AudioTestSupport.RATE, cleanSine());
        loud.noteOn(60, 1f);
        quiet.noteOn(60, 0.25f);
        float loudPeak = AudioTestSupport.peak(AudioTestSupport.render(loud, 4000));
        float quietPeak = AudioTestSupport.peak(AudioTestSupport.render(quiet, 4000));
        assertEquals(4f, loudPeak / quietPeak, 0.1f);
    }

    @Test
    void shouldSoundSeveralNotesAtOnce() {
        Synthesizer synth = new Synthesizer(AudioTestSupport.RATE, cleanSine());
        synth.noteOn(60, 1f);
        synth.noteOn(64, 1f);
        synth.noteOn(67, 1f);
        AudioTestSupport.render(synth, 100);
        assertEquals(3, synth.getActiveVoiceCount());
    }

    @Test
    void shouldReuseTheVoiceWhenTheSameNoteIsRetriggered() {
        Synthesizer synth = new Synthesizer(AudioTestSupport.RATE, cleanSine());
        synth.noteOn(60, 1f);
        synth.noteOn(60, 1f);
        assertEquals(1, synth.getActiveVoiceCount());
    }

    @Test
    void shouldStealTheOldestVoiceWhenAllAreBusy() {
        Synthesizer synth = new Synthesizer(AudioTestSupport.RATE, cleanSine());
        for (int i = 0; i < Synthesizer.MAX_VOICES + 4; i++) {
            synth.noteOn(30 + i, 1f);
        }
        assertEquals(Synthesizer.MAX_VOICES, synth.getActiveVoiceCount());
        // The first notes were stolen: releasing one of them changes nothing audible, the newest is still held
        synth.noteOff(30 + Synthesizer.MAX_VOICES + 3);
        AudioTestSupport.render(synth, 22050);
        assertEquals(Synthesizer.MAX_VOICES - 1, synth.getActiveVoiceCount());
    }

    @Test
    void shouldReleaseEveryNoteOnAllNotesOff() {
        Synthesizer synth = new Synthesizer(AudioTestSupport.RATE, cleanSine());
        synth.noteOn(60, 1f);
        synth.noteOn(64, 1f);
        synth.allNotesOff();
        AudioTestSupport.render(synth, 22050);
        assertEquals(0, synth.getActiveVoiceCount());
    }

    @Test
    void shouldAddToWhatIsAlreadyInTheBuffer() {
        Synthesizer synth = new Synthesizer(AudioTestSupport.RATE, cleanSine());
        synth.noteOn(60, 1f);
        float[] left = new float[500];
        float[] right = new float[500];
        java.util.Arrays.fill(left, 0.25f);
        synth.render(left, right, 0, 500);
        assertTrue(AudioTestSupport.peak(left) > 0.25f);
        assertEquals(0f, right[0], 1e-6f);
    }

    @Test
    void shouldCloseTheFilterWhenTheCutoffIsLow() {
        SynthPatch bright = cleanSine();
        bright.osc1 = Waveform.SAW;
        SynthPatch dull = bright.copy();
        dull.cutoff = 200f;
        Synthesizer a = new Synthesizer(AudioTestSupport.RATE, bright);
        Synthesizer b = new Synthesizer(AudioTestSupport.RATE, dull);
        a.noteOn(69, 1f);
        b.noteOn(69, 1f);
        double brightRms = AudioTestSupport.rms(AudioTestSupport.render(a, 8000), 2000, 8000);
        double dullRms = AudioTestSupport.rms(AudioTestSupport.render(b, 8000), 2000, 8000);
        assertTrue(dullRms < brightRms * 0.5, "dull " + dullRms + " bright " + brightRms);
    }

    @Test
    void shouldDetuneTheSecondOscillator() {
        SynthPatch patch = cleanSine();
        patch.osc2 = Waveform.SINE;
        patch.osc2Mix = 0.5f;
        patch.osc2Octave = 1;
        Synthesizer synth = new Synthesizer(AudioTestSupport.RATE, patch);
        synth.noteOn(57, 1f);
        float[] data = AudioTestSupport.render(synth, 22050);
        // 220 Hz plus 440 Hz: a zero-crossing count would be wrong, so check the energy is not just the fundamental
        SynthPatch single = cleanSine();
        Synthesizer reference = new Synthesizer(AudioTestSupport.RATE, single);
        reference.noteOn(57, 1f);
        float[] pure = AudioTestSupport.render(reference, 22050);
        double difference = 0.0;
        for (int i = 4000; i < 22050; i++) {
            difference += Math.abs(data[i] / 0.5 - pure[i]);
        }
        assertTrue(difference > 100.0);
    }

    @Test
    void shouldMakeEveryFactoryPresetAudible() {
        for (String name : SynthPatch.presetNames()) {
            Synthesizer synth = new Synthesizer(AudioTestSupport.RATE, SynthPatch.preset(name));
            synth.noteOn(60, 1f);
            float[] data = AudioTestSupport.render(synth, 22050);
            assertTrue(AudioTestSupport.peak(data) > 0.01f, name + " is silent");
            assertTrue(AudioTestSupport.allFinite(data), name + " produced NaN");
            assertTrue(AudioTestSupport.peak(data) < 1.5f, name + " is far too loud");
        }
    }

    @Test
    void shouldRoundTripAPatchThroughJson() {
        SynthPatch patch = SynthPatch.preset("Soft Pad");
        patch.osc2Octave = -1;
        SynthPatch read = SynthPatch.fromJson(new JsonReader().parse(patch.toJson()));
        assertEquals(patch.toJson(), read.toJson());
        assertEquals(Waveform.TRIANGLE, read.osc2);
        assertEquals(-1, read.osc2Octave);
        assertEquals(SynthPatch.LfoTarget.CUTOFF, read.lfoTarget);
    }

    @Test
    void shouldKeepDefaultsForMissingJsonFields() {
        SynthPatch read = SynthPatch.fromJson(new JsonReader().parse("{\"cutoff\":123}"));
        assertEquals(123f, read.cutoff);
        assertEquals(new SynthPatch().attack, read.attack);
    }

    @Test
    void shouldCopyWithoutSharingState() {
        SynthPatch original = SynthPatch.preset("Pluck");
        SynthPatch copy = original.copy();
        copy.cutoff = 1f;
        assertNotEquals(original.cutoff, copy.cutoff);
    }

    @Test
    void shouldRejectAnUnknownPreset() {
        assertThrows(IllegalArgumentException.class, () -> SynthPatch.preset("Nope"));
    }
}
