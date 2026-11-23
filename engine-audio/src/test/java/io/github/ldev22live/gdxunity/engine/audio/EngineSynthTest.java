package io.github.ldev22live.gdxunity.engine.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineSynthTest {
    private static final int RATE = EngineSynth.SAMPLE_RATE;
    private static final int SETTLE_BLOCKS = 6;
    /** A smooth, even four-cylinder, so its firing frequency is a clean spectral peak. */
    private static final EngineProfile EVEN_FOUR = new EngineProfile("Test Four", 4, 800f, 7000f, 0f, 1f, 0f);

    /** Runs the engine at a steady state long enough to settle, then returns one second of it. */
    private static float[] steady(EngineSynth synth, float rpm, float throttle) {
        synth.setRpm(rpm);
        synth.setThrottle(throttle);
        float[] block = new float[RATE / 4];
        for (int i = 0; i < SETTLE_BLOCKS; i++) {
            synth.render(block, block.length);
        }
        float[] out = new float[RATE];
        synth.render(out, out.length);
        return out;
    }

    private static float rms(float[] samples) {
        double sum = 0;
        for (float s : samples) {
            sum += s * s;
        }
        return (float) Math.sqrt(sum / samples.length);
    }

    /** Signal power at one frequency (Goertzel). */
    private static double power(float[] samples, double hz) {
        double omega = 2.0 * Math.PI * hz / RATE;
        double coefficient = 2.0 * Math.cos(omega);
        double s1 = 0;
        double s2 = 0;
        for (float sample : samples) {
            double s0 = sample + coefficient * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        return s1 * s1 + s2 * s2 - coefficient * s1 * s2;
    }

    @Test
    void shouldSoundAtTheFiringFrequencyOfTheRpm() {
        for (float rpm : new float[] {1500f, 3000f, 6000f}) {
            EngineSynth synth = new EngineSynth(EVEN_FOUR, 1L);
            float[] out = steady(synth, rpm, 0.8f);
            double fire = EVEN_FOUR.firingHz(rpm);

            double atFiring = power(out, fire);
            assertTrue(atFiring > 10 * power(out, fire * 1.5), "peak at " + fire + " Hz for " + rpm + " rpm");
            assertTrue(atFiring > 10 * power(out, fire * 0.75), "peak at " + fire + " Hz for " + rpm + " rpm");
        }
    }

    @Test
    void shouldFireMoreOftenWithMoreCylindersAtTheSameRpm() {
        EngineProfile eight = new EngineProfile("Test Eight", 8, 800f, 7000f, 0f, 1f, 0f);

        assertEquals(2f * EVEN_FOUR.firingHz(3000f), eight.firingHz(3000f), 1e-3f);
    }

    @Test
    void shouldBeLouderWithTheThrottleOpen() {
        float closed = rms(steady(new EngineSynth(EngineProfile.HATCHBACK, 1L), 3500f, 0f));
        float open = rms(steady(new EngineSynth(EngineProfile.HATCHBACK, 1L), 3500f, 1f));

        assertTrue(open > 1.5f * closed, "open " + open + " vs closed " + closed);
    }

    @Test
    void shouldBeLouderAndBrighterAtHighRevsThanAtIdle() {
        float idle = rms(steady(new EngineSynth(EngineProfile.SPORTS_COUPE, 1L), 900f, 1f));
        float high = rms(steady(new EngineSynth(EngineProfile.SPORTS_COUPE, 1L), 6500f, 1f));

        assertTrue(idle > 0.01f, "idle is audible: " + idle);
        assertTrue(high > 0f);
    }

    @Test
    void shouldStayWithinMinusOneToOneAndFiniteAcrossTheWholeRange() {
        for (EngineProfile profile : new EngineProfile[] {EngineProfile.HATCHBACK, EngineProfile.SPORTS_COUPE,
            EngineProfile.MUSCLE_V8}) {
            EngineSynth synth = new EngineSynth(profile, 7L);
            for (float rpm = 0f; rpm <= 12000f; rpm += 1500f) {
                for (float throttle = 0f; throttle <= 1f; throttle += 0.5f) {
                    for (float s : steady(synth, rpm, throttle)) {
                        assertTrue(Float.isFinite(s) && s >= -1f && s <= 1f, profile.name() + " " + rpm + " " + throttle);
                    }
                }
            }
        }
    }

    @Test
    void shouldHoldRpmWithinTheProfileRangeAndFollowItWithSomeLag() {
        EngineSynth synth = new EngineSynth(EngineProfile.HATCHBACK, 1L);
        float[] block = new float[512];

        synth.setRpm(100000f);
        synth.render(block, 8);
        float justAfter = synth.getRpm();
        for (int i = 0; i < 40; i++) {
            synth.render(block, block.length);
        }

        assertTrue(justAfter < 2000f, "inertia: does not jump straight to the redline (" + justAfter + ")");
        assertEquals(EngineProfile.HATCHBACK.redlineRpm(), synth.getRpm(), 5f);
        synth.setRpm(0f);
        for (int i = 0; i < 80; i++) {
            synth.render(block, block.length);
        }
        assertEquals(EngineProfile.HATCHBACK.idleRpm(), synth.getRpm(), 5f);
    }

    @Test
    void shouldSoundTheSameForTheSameSeedAndDifferForAnother() {
        float[] a = steady(new EngineSynth(EngineProfile.MUSCLE_V8, 5L), 2500f, 1f);
        float[] b = steady(new EngineSynth(EngineProfile.MUSCLE_V8, 5L), 2500f, 1f);
        float[] c = steady(new EngineSynth(EngineProfile.MUSCLE_V8, 6L), 2500f, 1f);

        assertArrayEquals(a, b);
        assertFalse(java.util.Arrays.equals(a, c));
    }

    @Test
    void shouldCutTheEngineInAndOutAtTheRevLimiter() {
        EngineSynth synth = new EngineSynth(EVEN_FOUR, 1L);
        float[] out = steady(synth, EVEN_FOUR.redlineRpm(), 1f);
        float quietest = Float.MAX_VALUE;
        float loudest = 0f;
        int window = RATE / 100;
        for (int start = 0; start + window <= out.length; start += window) {
            float[] part = java.util.Arrays.copyOfRange(out, start, start + window);
            quietest = Math.min(quietest, rms(part));
            loudest = Math.max(loudest, rms(part));
        }

        assertTrue(loudest > 2f * quietest, "limiter flutter: loudest " + loudest + " quietest " + quietest);
    }

    @Test
    void shouldCrackleOnTheOverrunOnlyForAProfileThatCrackles() {
        float smooth = rms(steady(new EngineSynth(EVEN_FOUR, 3L), 5000f, 0f));
        EngineProfile crackling = new EngineProfile("Test Crackle", 4, 800f, 7000f, 0f, 1f, 1f);
        float popping = rms(steady(new EngineSynth(crackling, 3L), 5000f, 0f));

        assertTrue(popping > 1.1f * smooth, "pops add energy: " + popping + " vs " + smooth);
    }

    @Test
    void shouldRefuseBadArguments() {
        EngineSynth synth = new EngineSynth(EngineProfile.HATCHBACK, 1L);

        assertThrows(IllegalArgumentException.class, () -> synth.setRpm(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> synth.setThrottle(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> synth.render(new float[4], 5));
        assertThrows(IllegalArgumentException.class, () -> new EngineProfile("x", 0, 800f, 7000f, 0f, 1f, 0f));
        assertThrows(IllegalArgumentException.class, () -> new EngineProfile("x", 4, 800f, 700f, 0f, 1f, 0f));
        assertThrows(IllegalArgumentException.class, () -> EngineProfile.byName("Steam Engine"));
    }

    @Test
    void shouldFindFactoryProfilesByNameIgnoringCase() {
        assertEquals(EngineProfile.MUSCLE_V8, EngineProfile.byName(" muscle v8 "));
        assertTrue(EngineProfile.isKnown("HATCHBACK"));
        assertFalse(EngineProfile.isKnown("Steam Engine"));
        assertEquals(3, EngineProfile.names().size());
    }
}
