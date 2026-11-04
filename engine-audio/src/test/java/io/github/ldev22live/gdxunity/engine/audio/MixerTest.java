package io.github.ldev22live.gdxunity.engine.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixerTest {
    private static final int FRAMES = 256;

    /** Puts a constant level into the strips listed. */
    private static Mixer.StripSource constant(float level, int... strips) {
        return (index, left, right, offset, frames) -> {
            for (int s : strips) {
                if (s == index) {
                    for (int i = 0; i < frames; i++) {
                        left[offset + i] += level;
                        right[offset + i] += level;
                    }
                }
            }
        };
    }

    private static float[][] render(Mixer mixer, Mixer.StripSource source) {
        float[] left = new float[FRAMES];
        float[] right = new float[FRAMES];
        mixer.render(source, left, right, 0, FRAMES);
        return new float[][] {left, right};
    }

    private static Mixer plainMixer() {
        Mixer mixer = new Mixer(AudioTestSupport.RATE);
        mixer.getStrip(0).setVolume(1f);
        mixer.getStrip(1).setVolume(1f);
        return mixer;
    }

    @Test
    void shouldPassACentredSignalAtUnityGain() {
        float[][] out = render(plainMixer(), constant(0.5f, 0));
        assertEquals(0.5f, out[0][10], 1e-5f);
        assertEquals(0.5f, out[1][10], 1e-5f);
    }

    @Test
    void shouldPanHardLeftAndHardRight() {
        Mixer mixer = plainMixer();
        mixer.getStrip(0).setPan(-1f);
        float[][] out = render(mixer, constant(0.5f, 0));
        assertEquals(0f, out[1][10], 1e-5f);
        assertTrue(out[0][10] > 0.5f);

        mixer.getStrip(0).setPan(1f);
        out = render(mixer, constant(0.5f, 0));
        assertEquals(0f, out[0][10], 1e-5f);
        assertTrue(out[1][10] > 0.5f);
    }

    @Test
    void shouldKeepLoudnessConstantAcrossThePan() {
        Mixer mixer = plainMixer();
        for (float pan : new float[] {-0.7f, -0.2f, 0.3f, 0.9f}) {
            mixer.getStrip(0).setPan(pan);
            float[][] out = render(mixer, constant(0.5f, 0));
            // Both sides together carry twice a centred signal's power, wherever it is panned
            double power = out[0][10] * out[0][10] + out[1][10] * out[1][10];
            assertEquals(2.0, power / (0.5 * 0.5), 0.02, "pan " + pan);
        }
    }

    @Test
    void shouldScaleWithTheFaderAndSumStrips() {
        Mixer mixer = plainMixer();
        mixer.getStrip(0).setVolume(0.5f);
        float[][] out = render(mixer, constant(0.4f, 0, 1));
        assertEquals(0.4f * 0.5f + 0.4f, out[0][10], 1e-5f);
    }

    @Test
    void shouldSilenceAMutedStripButStillRunItsSource() {
        Mixer mixer = plainMixer();
        mixer.getStrip(0).setMuted(true);
        int[] calls = {0};
        Mixer.StripSource source = (index, l, r, o, f) -> {
            if (index == 0) {
                calls[0]++;
                l[o] += 0.5f;
                r[o] += 0.5f;
            }
        };
        float[][] out = render(mixer, source);
        assertEquals(1, calls[0], "a muted strip's instruments keep running");
        assertEquals(0f, AudioTestSupport.peak(out[0]));
        assertEquals(0f, AudioTestSupport.peak(out[1]));
    }

    @Test
    void shouldPlayOnlySoloedStrips() {
        Mixer mixer = plainMixer();
        mixer.getStrip(1).setSolo(true);
        float[][] out = render(mixer, constant(0.25f, 0, 1));
        assertEquals(0.25f, out[0][10], 1e-5f);
        mixer.getStrip(1).setSolo(false);
        out = render(mixer, constant(0.25f, 0, 1));
        assertEquals(0.5f, out[0][10], 1e-5f);
    }

    @Test
    void shouldApplyTheMasterVolumeAndMute() {
        Mixer mixer = plainMixer();
        mixer.getMaster().setVolume(0.5f);
        assertEquals(0.25f, render(mixer, constant(0.5f, 0))[0][10], 1e-5f);
        mixer.getMaster().setMuted(true);
        assertEquals(0f, AudioTestSupport.peak(render(mixer, constant(0.5f, 0))[0]));
    }

    @Test
    void shouldRunStripEffectsInOrder() {
        Mixer mixer = plainMixer();
        DelayEffect delay = new DelayEffect(AudioTestSupport.RATE);
        delay.setParameter("time", 0.02f);
        delay.setParameter("mix", 1f);
        delay.setParameter("feedback", 0f);
        mixer.getStrip(0).addEffect(delay);
        Mixer.StripSource impulse = (index, l, r, o, f) -> {
            if (index == 0) {
                l[o] += 0.5f;
                r[o] += 0.5f;
            }
        };
        float[] left = new float[2000];
        float[] right = new float[2000];
        mixer.render(impulse, left, right, 0, 2000);
        assertEquals(0.5f, left[0], 1e-5f);
        assertEquals(0.5f, left[882], 1e-5f);
    }

    @Test
    void shouldLimitEffectsPerStrip() {
        Mixer mixer = new Mixer(AudioTestSupport.RATE);
        for (int i = 0; i < Mixer.MAX_EFFECTS; i++) {
            mixer.getStrip(0).addEffect(new FilterEffect(AudioTestSupport.RATE));
        }
        assertThrows(IllegalStateException.class, () -> mixer.getStrip(0).addEffect(new FilterEffect(AudioTestSupport.RATE)));
        Effect first = mixer.getStrip(0).getEffects().get(0);
        assertTrue(mixer.getStrip(0).removeEffect(first));
        assertFalse(mixer.getStrip(0).removeEffect(first));
    }

    @Test
    void shouldMeterThePeakAndLetItFall() {
        Mixer mixer = plainMixer();
        render(mixer, constant(0.8f, 0));
        assertEquals(0.8f, mixer.getStrip(0).getPeakLeft(), 1e-4f);
        assertEquals(0f, mixer.getStrip(1).getPeakLeft(), 1e-6f);
        float[] left = new float[4410];
        float[] right = new float[4410];
        mixer.render((i, l, r, o, f) -> { }, left, right, 0, 4410);
        assertTrue(mixer.getStrip(0).getPeakLeft() < 0.8f);
        assertTrue(mixer.getStrip(0).getPeakLeft() > 0f);
    }

    @Test
    void shouldWriteAtTheGivenOffsetAndOverwriteOldContents() {
        Mixer mixer = plainMixer();
        float[] left = new float[FRAMES * 2];
        float[] right = new float[FRAMES * 2];
        java.util.Arrays.fill(left, 9f);
        mixer.render(constant(0.5f, 0), left, right, FRAMES, FRAMES);
        assertEquals(9f, left[FRAMES - 1]);
        assertEquals(0.5f, left[FRAMES], 1e-5f);
        assertEquals(0.5f, left[FRAMES * 2 - 1], 1e-5f);
    }

    @Test
    void shouldPassQuietLevelsUnchangedAndBendLoudOnesBelowFullScale() {
        for (float x : new float[] {0f, 0.3f, -0.79f, 0.8f}) {
            assertEquals(x, Mixer.softLimit(x), 1e-6f);
        }
        float previous = 0.8f;
        for (float x = 0.85f; x < 2f; x += 0.15f) {
            float limited = Mixer.softLimit(x);
            assertTrue(limited > previous, "limiter must keep rising: " + x);
            assertTrue(limited < 1f, "limiter output " + limited + " for " + x);
            assertEquals(-limited, Mixer.softLimit(-x), 1e-6f);
            previous = limited;
        }
        assertEquals(1f, Mixer.softLimit(1e6f), 1e-6f);
        assertEquals(-1f, Mixer.softLimit(-1e6f), 1e-6f);
    }

    @Test
    void shouldNeverLetTheMixLeaveFullScaleEvenWhenStripsAddUpPastIt() {
        Mixer mixer = plainMixer();
        float[][] out = render(mixer, constant(0.9f, 0, 1));
        assertTrue(AudioTestSupport.peak(out[0]) < 1f);
        assertTrue(mixer.getMaster().getPeakLeft() > 1.5f, "the meter shows the real level, not the limited one");
    }

    @Test
    void shouldRejectBadStripIndexesAndClampKnobs() {
        Mixer mixer = new Mixer(AudioTestSupport.RATE);
        assertThrows(IndexOutOfBoundsException.class, () -> mixer.getStrip(Mixer.DEFAULT_STRIPS));
        mixer.getStrip(0).setVolume(9f);
        mixer.getStrip(0).setPan(-9f);
        assertEquals(Mixer.MAX_VOLUME, mixer.getStrip(0).getVolume());
        assertEquals(-1f, mixer.getStrip(0).getPan());
    }
}
