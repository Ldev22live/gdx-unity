package io.github.ldev22live.gdxunity.engine.audio;

import com.badlogic.gdx.utils.JsonReader;
import io.github.ldev22live.gdxunity.engine.audio.song.InstrumentFactory;
import io.github.ldev22live.gdxunity.engine.audio.song.InstrumentSpec;
import io.github.ldev22live.gdxunity.engine.audio.song.MusicAssets;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The FM synthesizer and its settings (TD-075). */
class FmSynthesizerTest {
    private static final float RATE = AudioTestSupport.RATE;
    private static final int MIDDLE_C = 60;
    /** A sustained, un-enveloped sine: the tone a patch with no modulation must make. */
    private static FmPatch steady() {
        FmPatch p = new FmPatch();
        p.index = 0f;
        p.attack = 0.001f;
        p.decay = 0.001f;
        p.sustain = 1f;
        p.release = 0.05f;
        p.volume = 1f;
        return p;
    }

    private static float[] render(FmPatch patch, int note, int frames) {
        FmSynthesizer synth = new FmSynthesizer(RATE, patch);
        synth.noteOn(note, 1f);
        return AudioTestSupport.render(synth, frames);
    }

    private static int crossings(float[] data, int from, int to) {
        int count = 0;
        for (int i = Math.max(from, 1); i < to; i++) {
            if (data[i - 1] < 0f && data[i] >= 0f) {
                count++;
            }
        }
        return count;
    }

    @Test
    void shouldMakeEveryFactoryPresetAudibleFiniteAndNotClipAcrossTheKeyboard() {
        for (String name : FmPatch.presetNames()) {
            for (int note : new int[] {24, MIDDLE_C, 96}) {
                float[] data = render(FmPatch.preset(name), note, 30000);
                assertTrue(AudioTestSupport.peak(data) > 0.01f, name + " is silent at " + note);
                assertTrue(AudioTestSupport.allFinite(data), name + " produced NaN at " + note);
                assertTrue(AudioTestSupport.peak(data) < 1.5f, name + " is far too loud at " + note);
            }
        }
    }

    @Test
    void shouldMakeAPlainSineAtTheNotesPitchWhenThereIsNoModulation() {
        float[] data = render(steady(), MIDDLE_C, 20000);
        assertEquals(261.63, AudioTestSupport.frequency(data, 2000, 20000), 2.0);
        // And it is a sine: its level hardly moves from cycle to cycle
        assertTrue(AudioTestSupport.peak(data, 4000, 8000) > 0.2f);
        assertEquals(AudioTestSupport.peak(data, 4000, 8000), AudioTestSupport.peak(data, 12000, 16000), 0.02f);
    }

    @Test
    void shouldKeepTheNotesPitchWhenTheRatioIsAWholeNumber() {
        FmPatch patch = steady();
        patch.ratio = 1f;
        patch.index = 2f;
        patch.indexSustain = 1f;
        // The wave crosses zero more than once a cycle, so read the pitch from how far it repeats, not from crossings
        double expected = RATE / 261.63;
        assertEquals(expected, AudioTestSupport.period(render(patch, MIDDLE_C, 20000), 4000, 8000, 261.63), 1.0);
    }

    @Test
    void shouldAddOvertonesInProportionToTheIndex() {
        FmPatch plain = steady();
        FmPatch bent = steady();
        bent.ratio = 3f;
        bent.index = 5f;
        bent.indexSustain = 1f;
        // More crossings in the same time means more high-frequency content than the plain sine
        assertTrue(crossings(render(bent, MIDDLE_C, 20000), 2000, 20000) > 2 * crossings(render(plain, MIDDLE_C, 20000), 2000, 20000));
    }

    @Test
    void shouldLetTheModulationFallSoTheToneStartsBrightAndSettles() {
        FmPatch patch = steady();
        patch.ratio = 3f;
        patch.index = 6f;
        patch.indexSustain = 0f;
        patch.indexDecay = 0.1f;
        float[] data = render(patch, MIDDLE_C, 44100);
        int early = crossings(data, 0, 3000);
        int late = crossings(data, 30000, 33000);
        assertTrue(early > 2 * late, "early " + early + " late " + late);
    }

    @Test
    void shouldFallSilentAfterTheNoteIsReleased() {
        FmSynthesizer synth = new FmSynthesizer(RATE, FmPatch.preset("FM Piano"));
        synth.noteOn(MIDDLE_C, 1f);
        AudioTestSupport.render(synth, 4000);
        synth.noteOff(MIDDLE_C);
        float[] tail = AudioTestSupport.render(synth, 44100);
        assertEquals(0f, AudioTestSupport.peak(tail, tail.length - 1000, tail.length));
        assertEquals(0, synth.getActiveVoiceCount());
    }

    @Test
    void shouldTakeOverTheOldestVoiceBeyondSixteenNotes() {
        FmSynthesizer synth = new FmSynthesizer(RATE, FmPatch.preset("Metal Pad"));
        for (int i = 0; i < FmSynthesizer.MAX_VOICES + 4; i++) {
            synth.noteOn(40 + i, 0.8f);
        }
        assertEquals(FmSynthesizer.MAX_VOICES, synth.getActiveVoiceCount());
    }

    @Test
    void shouldReadItsPatchLiveAndScaleWithVelocity() {
        FmPatch patch = FmPatch.preset("Bell");
        FmSynthesizer loud = new FmSynthesizer(RATE, patch);
        FmSynthesizer soft = new FmSynthesizer(RATE, patch.copy());
        loud.noteOn(MIDDLE_C, 1f);
        soft.noteOn(MIDDLE_C, 0.5f);
        assertEquals(2f, AudioTestSupport.peak(AudioTestSupport.render(loud, 3000)) / AudioTestSupport.peak(AudioTestSupport.render(soft, 3000)), 0.01f);
        patch.volume = 0f;
        loud.noteOn(MIDDLE_C + 12, 1f);
        assertEquals(0f, AudioTestSupport.peak(AudioTestSupport.render(loud, 2000)));
    }

    @Test
    void shouldKeepTheRatioFromFoldingBackOnVeryHighNotes() {
        FmPatch patch = FmPatch.preset("Glass");
        patch.ratio = 16f;
        float[] data = render(patch, 120, 20000);
        assertTrue(AudioTestSupport.allFinite(data));
        assertTrue(AudioTestSupport.peak(data) < 1.5f);
    }

    @Test
    void shouldOfferFiveDistinctPresetsInsideTheEditorSliders() {
        List<String> names = FmPatch.presetNames();
        assertEquals(5, names.size());
        assertEquals(5, names.stream().map(n -> FmPatch.preset(n).toJson()).distinct().count());
        for (String name : names) {
            FmPatch p = FmPatch.preset(name);
            assertTrue(p.ratio >= 0.25f && p.ratio <= 16f, name + " ratio");
            assertTrue(p.index >= 0f && p.index <= 12f, name + " index");
            assertTrue(p.indexSustain >= 0f && p.indexSustain <= 1f, name + " indexSustain");
            assertTrue(p.indexDecay >= 0.01f && p.indexDecay <= 5f, name + " indexDecay");
            assertTrue(p.attack >= 0.001f && p.attack <= 5f, name + " attack");
            assertTrue(p.decay >= 0.001f && p.decay <= 5f, name + " decay");
            assertTrue(p.sustain >= 0f && p.sustain <= 1f, name + " sustain");
            assertTrue(p.release >= 0.001f && p.release <= 5f, name + " release");
            assertTrue(p.volume >= 0f && p.volume <= 1f, name + " volume");
        }
        assertThrows(IllegalArgumentException.class, () -> FmPatch.preset("Nope"));
    }

    @Test
    void shouldRoundTripAPatchThroughJsonAndKeepDefaultsForMissingFields() {
        FmPatch patch = FmPatch.preset("Metal Pad");
        patch.ratio = 7.25f;
        FmPatch read = FmPatch.fromJson(new JsonReader().parse(patch.toJson()));
        assertEquals(patch.toJson(), read.toJson());
        assertEquals(7.25f, read.ratio);
        assertEquals(new FmPatch().attack, FmPatch.fromJson(new JsonReader().parse("{\"ratio\":3}")).attack);
        FmPatch copy = patch.copy();
        copy.index = 9f;
        assertNotEquals(patch.index, copy.index);
    }

    @Test
    void shouldBeBuiltFromAnInstrumentSpecAndShareItsPatch() throws IOException {
        InstrumentSpec spec = InstrumentSpec.fm(FmPatch.preset("Glass"));
        Instrument built = new InstrumentFactory(MusicAssets.NONE, RATE).create(spec);
        FmSynthesizer synth = assertInstanceOf(FmSynthesizer.class, built);
        assertTrue(synth.getPatch() == spec.fm, "knobs edit the spec's patch live");
    }
}
