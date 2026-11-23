package io.github.ldev22live.gdxunity.engine.audio;

import com.badlogic.gdx.utils.JsonReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SfxrTest {
    @Test
    void shouldGenerateAnAudibleFiniteSoundForEveryPreset() {
        for (String name : SfxrParams.presetNames()) {
            SampleData sound = SfxrGenerator.generate(SfxrParams.preset(name));
            assertTrue(sound.getLength() > 1000, name + " is too short: " + sound.getLength());
            assertTrue(sound.getPeak() > 0.05f, name + " is silent");
            assertTrue(sound.getPeak() <= 1f, name + " clips past full scale");
            assertTrue(AudioTestSupport.allFinite(sound.getLeft()), name + " has NaN");
            assertEquals(SfxrGenerator.SAMPLE_RATE, sound.getSampleRate());
            assertTrue(sound.getDurationSeconds() < 4f, name + " lasts " + sound.getDurationSeconds());
        }
    }

    @Test
    void shouldGenerateTheSameSoundFromTheSameParameters() {
        for (String name : SfxrParams.presetNames()) {
            assertArrayEquals(SfxrGenerator.generate(SfxrParams.preset(name)).getLeft(),
                SfxrGenerator.generate(SfxrParams.preset(name)).getLeft(), name);
        }
    }

    @Test
    void shouldMakeASlidingPitchFallForALaser() {
        SfxrParams params = SfxrParams.preset("Laser");
        params.shape = SfxrParams.Shape.SINE;
        float[] data = SfxrGenerator.generate(params).getLeft();
        int third = data.length / 3;
        double early = AudioTestSupport.frequency(data, 0, third);
        double late = AudioTestSupport.frequency(data, 2 * third, data.length);
        assertTrue(early > 0 && late > 0, "no pitch found");
        assertTrue(early > late * 1.2, "early " + early + " late " + late);
    }

    @Test
    void shouldLastLongerWithALongerSustain() {
        SfxrParams shortSound = SfxrParams.preset("Blip");
        SfxrParams longSound = shortSound.copy();
        longSound.sustain = 0.6f;
        assertTrue(SfxrGenerator.generate(longSound).getLength() > SfxrGenerator.generate(shortSound).getLength() * 2);
    }

    @Test
    void shouldChangeTheSoundWithTheWaveShape() {
        SfxrParams square = SfxrParams.preset("Coin");
        SfxrParams noise = square.copy();
        noise.shape = SfxrParams.Shape.NOISE;
        float[] a = SfxrGenerator.generate(square).getLeft();
        float[] b = SfxrGenerator.generate(noise).getLeft();
        assertTrue(java.util.Arrays.compare(a, b) != 0);
    }

    @Test
    void shouldScaleLoudnessWithVolume() {
        SfxrParams loud = SfxrParams.preset("Jump");
        // Low volumes, so the output limiter does not flatten the louder sound
        loud.volume = 0.2f;
        SfxrParams quiet = loud.copy();
        quiet.volume = 0.1f;
        assertEquals(2f, SfxrGenerator.generate(loud).getPeak() / SfxrGenerator.generate(quiet).getPeak(), 0.01f);
    }

    @Test
    void shouldRandomizeDeterministicallyFromASeed() {
        assertEquals(SfxrParams.randomized(42).toJson(), SfxrParams.randomized(42).toJson());
        assertNotEquals(SfxrParams.randomized(42).toJson(), SfxrParams.randomized(43).toJson());
        for (long seed = 0; seed < 25; seed++) {
            SampleData sound = SfxrGenerator.generate(SfxrParams.randomized(seed));
            assertTrue(AudioTestSupport.allFinite(sound.getLeft()), "seed " + seed);
            assertTrue(sound.getPeak() <= 1f, "seed " + seed);
        }
    }

    @Test
    void shouldMutateWithinRangesAndMoreWithBiggerAmounts() {
        SfxrParams base = SfxrParams.preset("Powerup");
        double small = distance(base, base.mutated(7, 0.05f));
        double large = distance(base, base.mutated(7, 0.8f));
        assertTrue(small < large, "small " + small + " large " + large);
        assertEquals(base.toJson(), base.mutated(7, 0f).toJson());
        SfxrParams extreme = base.mutated(3, 1f);
        assertTrue(extreme.baseFreq >= 0f && extreme.baseFreq <= 1f);
        assertTrue(extreme.slide >= -1f && extreme.slide <= 1f);
    }

    private static double distance(SfxrParams a, SfxrParams b) {
        double sum = Math.abs(a.baseFreq - b.baseFreq) + Math.abs(a.slide - b.slide) + Math.abs(a.sustain - b.sustain)
            + Math.abs(a.decay - b.decay) + Math.abs(a.punch - b.punch) + Math.abs(a.vibratoDepth - b.vibratoDepth)
            + Math.abs(a.duty - b.duty) + Math.abs(a.lowPassCutoff - b.lowPassCutoff);
        return sum;
    }

    @Test
    void shouldRoundTripThroughJson() {
        SfxrParams params = SfxrParams.preset("Explosion");
        params.volume = 0.33f;
        SfxrParams read = SfxrParams.fromJson(new JsonReader().parse(params.toJson()));
        assertEquals(params.toJson(), read.toJson());
        assertEquals(SfxrParams.Shape.NOISE, read.shape);
        assertEquals(0.33f, read.volume);
    }

    @Test
    void shouldRejectAJsonValueListOfTheWrongSize() {
        assertThrows(IllegalArgumentException.class,
            () -> SfxrParams.fromJson(new JsonReader().parse("{\"values\":[1,2,3]}")));
    }

    @Test
    void shouldRejectAnUnknownPreset() {
        assertThrows(IllegalArgumentException.class, () -> SfxrParams.preset("Fart"));
    }
}
