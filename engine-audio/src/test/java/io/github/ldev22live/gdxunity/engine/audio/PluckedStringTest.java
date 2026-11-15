package io.github.ldev22live.gdxunity.engine.audio;

import com.badlogic.gdx.utils.JsonReader;
import io.github.ldev22live.gdxunity.engine.audio.song.InstrumentFactory;
import io.github.ldev22live.gdxunity.engine.audio.song.InstrumentSpec;
import io.github.ldev22live.gdxunity.engine.audio.song.MusicAssets;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plucked-string instrument and its settings (TD-075). */
class PluckedStringTest {
    private static final float RATE = AudioTestSupport.RATE;

    private static float[] render(PluckPatch patch, int note, int frames) {
        PluckedString string = new PluckedString(RATE, patch);
        string.noteOn(note, 1f);
        return AudioTestSupport.render(string, frames);
    }

    /** Average step between samples relative to the loudness: higher for a brighter sound. */
    private static double brightness(float[] data, int from, int to) {
        double steps = 0.0;
        for (int i = from + 1; i < to; i++) {
            steps += Math.abs(data[i] - data[i - 1]);
        }
        return (steps / (to - from)) / (AudioTestSupport.rms(data, from, to) + 1e-9);
    }

    @Test
    void shouldMakeEveryFactoryPresetAudibleFiniteAndFallSilentAcrossTheKeyboard() {
        for (String name : PluckPatch.presetNames()) {
            PluckPatch patch = PluckPatch.preset(name);
            for (int note : new int[] {24, 60, 96}) {
                float[] data = render(patch, note, (int) ((patch.decay * 1.5f + 0.2f) * RATE));
                assertTrue(AudioTestSupport.peak(data, 0, 4000) > 0.01f, name + " is silent at " + note);
                assertTrue(AudioTestSupport.allFinite(data), name + " produced NaN at " + note);
                assertTrue(AudioTestSupport.peak(data) < 1.5f, name + " is far too loud at " + note);
                assertEquals(0f, AudioTestSupport.peak(data, data.length - 1000, data.length), name + " never ended at " + note);
            }
        }
    }

    @Test
    void shouldRingAtTheNotesPitch() {
        PluckPatch patch = PluckPatch.preset("Harp");
        for (int note : new int[] {45, 57, 69, 81}) {
            double hz = Synthesizer.noteToHz(note);
            double expected = RATE / hz;
            float[] data = render(patch, note, 12000);
            double measured = AudioTestSupport.period(data, 3000, 7000, hz);
            assertEquals(expected, measured, expected * 0.02 + 1.0, "note " + note + ": expected lag " + expected + ", measured " + measured);
        }
    }

    @Test
    void shouldSoundBrighterWithMoreBrightnessAndLoseTheHighEndFasterWithMoreDamping() {
        PluckPatch soft = new PluckPatch();
        soft.brightness = 0f;
        PluckPatch hard = new PluckPatch();
        hard.brightness = 1f;
        assertTrue(brightness(render(hard, 60, 6000), 0, 3000) > 1.5 * brightness(render(soft, 60, 6000), 0, 3000));

        PluckPatch ringing = new PluckPatch();
        ringing.damping = 0f;
        ringing.brightness = 1f;
        PluckPatch dull = new PluckPatch();
        dull.damping = 1f;
        dull.brightness = 1f;
        assertTrue(brightness(render(ringing, 60, 30000), 20000, 24000) > 1.5 * brightness(render(dull, 60, 30000), 20000, 24000));
    }

    @Test
    void shouldFallBySixtyDecibelsInTheDecayTimeAndNotBeforeTheNoteHasRung() {
        PluckPatch patch = new PluckPatch();
        patch.decay = 2f;
        patch.damping = 0f;
        patch.brightness = 1f;
        float[] data = render(patch, 69, (int) (2.2f * RATE));
        double start = AudioTestSupport.rms(data, 0, 4000);
        double half = AudioTestSupport.rms(data, (int) RATE - 2000, (int) RATE + 2000);
        double end = AudioTestSupport.rms(data, (int) (2f * RATE) - 2000, (int) (2f * RATE) + 2000);
        assertTrue(half > 0.01 * start, "still ringing at one second: " + half / start);
        assertTrue(end < 0.02 * start, "down by 2 s: " + end / start);
    }

    @Test
    void shouldSoundTheSameEveryTimeANoteIsPlayed() {
        PluckPatch patch = PluckPatch.preset("Nylon Guitar");
        assertArrayEquals(render(patch, 52, 8000), render(patch, 52, 8000));
        // But different notes are not just the same noise at another speed
        assertNotEquals(AudioTestSupport.peak(render(patch, 52, 100)), AudioTestSupport.peak(render(patch, 53, 100)));
    }

    @Test
    void shouldFallSilentSoonerAfterReleaseThanTheStringWouldRingOn() {
        PluckPatch patch = new PluckPatch();
        patch.decay = 8f;
        patch.release = 0.05f;
        PluckedString string = new PluckedString(RATE, patch);
        string.noteOn(60, 1f);
        AudioTestSupport.render(string, 4000);
        string.noteOff(60);
        float[] tail = AudioTestSupport.render(string, 22050);
        assertEquals(0f, AudioTestSupport.peak(tail, tail.length - 1000, tail.length));
        assertEquals(0, string.getActiveVoiceCount());
    }

    @Test
    void shouldTakeOverTheOldestStringBeyondSixteenNotes() {
        PluckedString string = new PluckedString(RATE, PluckPatch.preset("Harp"));
        for (int i = 0; i < PluckedString.MAX_VOICES + 4; i++) {
            string.noteOn(40 + i, 0.8f);
        }
        assertEquals(PluckedString.MAX_VOICES, string.getActiveVoiceCount());
    }

    @Test
    void shouldHoldVeryLowAndVeryHighNotesWithinItsDelayLine() {
        for (int note : new int[] {0, 127}) {
            float[] data = render(PluckPatch.preset("Nylon Guitar"), note, 20000);
            assertTrue(AudioTestSupport.allFinite(data), "note " + note);
            assertTrue(AudioTestSupport.peak(data) < 1.5f, "note " + note);
        }
    }

    @Test
    void shouldReadItsPatchLiveAndScaleWithVelocity() {
        PluckPatch patch = PluckPatch.preset("Pizzicato");
        PluckedString loud = new PluckedString(RATE, patch);
        PluckedString soft = new PluckedString(RATE, patch.copy());
        loud.noteOn(60, 1f);
        soft.noteOn(60, 0.5f);
        assertEquals(2f, AudioTestSupport.peak(AudioTestSupport.render(loud, 3000)) / AudioTestSupport.peak(AudioTestSupport.render(soft, 3000)), 0.01f);
        patch.volume = 0f;
        loud.noteOn(72, 1f);
        assertEquals(0f, AudioTestSupport.peak(AudioTestSupport.render(loud, 2000)));
    }

    @Test
    void shouldOfferThreeDistinctPresetsInsideTheEditorSliders() {
        List<String> names = PluckPatch.presetNames();
        assertEquals(3, names.size());
        assertEquals(3, names.stream().map(n -> PluckPatch.preset(n).toJson()).distinct().count());
        for (String name : names) {
            PluckPatch p = PluckPatch.preset(name);
            assertTrue(p.decay >= 0.1f && p.decay <= 10f, name + " decay");
            assertTrue(p.damping >= 0f && p.damping <= 1f, name + " damping");
            assertTrue(p.brightness >= 0f && p.brightness <= 1f, name + " brightness");
            assertTrue(p.release >= 0.01f && p.release <= 2f, name + " release");
            assertTrue(p.volume >= 0f && p.volume <= 1f, name + " volume");
        }
        assertThrows(IllegalArgumentException.class, () -> PluckPatch.preset("Nope"));
    }

    @Test
    void shouldRoundTripAPatchThroughJsonAndKeepDefaultsForMissingFields() {
        PluckPatch patch = PluckPatch.preset("Harp");
        patch.damping = 0.31f;
        PluckPatch read = PluckPatch.fromJson(new JsonReader().parse(patch.toJson()));
        assertEquals(patch.toJson(), read.toJson());
        assertEquals(new PluckPatch().release, PluckPatch.fromJson(new JsonReader().parse("{\"decay\":3}")).release);
        PluckPatch copy = patch.copy();
        copy.decay = 9f;
        assertNotEquals(patch.decay, copy.decay);
    }

    @Test
    void shouldBeBuiltFromAnInstrumentSpecAndShareItsPatch() throws IOException {
        InstrumentSpec spec = InstrumentSpec.pluck(PluckPatch.preset("Harp"));
        Instrument built = new InstrumentFactory(MusicAssets.NONE, RATE).create(spec);
        PluckedString string = assertInstanceOf(PluckedString.class, built);
        assertTrue(string.getPatch() == spec.pluck, "knobs edit the spec's patch live");
    }
}
