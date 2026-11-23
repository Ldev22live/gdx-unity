package io.github.ldev22live.gdxunity.engine.audio;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectsTest {
    private static float[][] impulse(int frames) {
        float[] left = new float[frames];
        float[] right = new float[frames];
        left[0] = 1f;
        right[0] = 1f;
        return new float[][] {left, right};
    }

    @Test
    void shouldRepeatAnImpulseAfterTheDelayTimeAtTheMixLevel() {
        DelayEffect delay = new DelayEffect(AudioTestSupport.RATE);
        delay.setParameter("time", 0.1f);
        delay.setParameter("feedback", 0f);
        delay.setParameter("mix", 0.5f);
        float[][] buffers = impulse(10000);
        delay.process(buffers[0], buffers[1], 0, 10000);
        assertEquals(1f, buffers[0][0]);
        assertEquals(0.5f, buffers[0][4410], 1e-6f);
        assertEquals(0f, buffers[0][4000]);
        assertEquals(0f, buffers[0][5000]);
    }

    @Test
    void shouldFeedEchoesBackAndLetThemDieAway() {
        DelayEffect delay = new DelayEffect(AudioTestSupport.RATE);
        delay.setParameter("time", 0.05f);
        delay.setParameter("feedback", 0.5f);
        delay.setParameter("mix", 1f);
        float[][] buffers = impulse(40000);
        delay.process(buffers[0], buffers[1], 0, 40000);
        float first = Math.abs(buffers[0][2205]);
        float second = Math.abs(buffers[0][4410]) + Math.abs(buffers[1][4410]);
        assertTrue(first > 0.9f);
        assertTrue(second > 0.4f, "second echo " + second);
        assertTrue(AudioTestSupport.peak(buffers[0], 30000, 40000) < 0.01f);
    }

    @Test
    void shouldKeepTheDelayStateAcrossCalls() {
        DelayEffect whole = new DelayEffect(AudioTestSupport.RATE);
        DelayEffect split = new DelayEffect(AudioTestSupport.RATE);
        float[][] a = impulse(6000);
        float[][] b = impulse(6000);
        whole.process(a[0], a[1], 0, 6000);
        split.process(b[0], b[1], 0, 1000);
        split.process(b[0], b[1], 1000, 5000);
        assertEquals(0, java.util.Arrays.compare(a[0], b[0]));
    }

    @Test
    void shouldClampDelayParameters() {
        DelayEffect delay = new DelayEffect(AudioTestSupport.RATE);
        delay.setParameter("feedback", 5f);
        delay.setParameter("time", 0f);
        assertEquals(DelayEffect.MAX_FEEDBACK, delay.getParameter("feedback"));
        assertEquals(DelayEffect.MIN_TIME, delay.getParameter("time"));
    }

    @Test
    void shouldMakeAReverbTailThatFadesOut() {
        ReverbEffect reverb = new ReverbEffect(AudioTestSupport.RATE);
        reverb.setParameter("mix", 1f);
        reverb.setParameter("room", 0.5f);
        int frames = 4 * 44100;
        float[][] buffers = impulse(frames);
        reverb.process(buffers[0], buffers[1], 0, frames);
        double early = AudioTestSupport.rms(buffers[0], 2000, 12000);
        double late = AudioTestSupport.rms(buffers[0], frames - 20000, frames);
        assertTrue(early > 1e-4, "no tail: " + early);
        assertTrue(late < early * 0.5, "tail does not fade: early " + early + " late " + late);
        assertTrue(AudioTestSupport.allFinite(buffers[0]));
    }

    @Test
    void shouldGiveABiggerRoomALongerTail() {
        double[] late = new double[2];
        float[] rooms = {0.1f, 0.95f};
        for (int i = 0; i < 2; i++) {
            ReverbEffect reverb = new ReverbEffect(AudioTestSupport.RATE);
            reverb.setParameter("mix", 1f);
            reverb.setParameter("room", rooms[i]);
            float[][] buffers = impulse(3 * 44100);
            reverb.process(buffers[0], buffers[1], 0, buffers[0].length);
            late[i] = AudioTestSupport.rms(buffers[0], 2 * 44100, 3 * 44100);
        }
        assertTrue(late[1] > late[0] * 3, "small " + late[0] + " large " + late[1]);
    }

    @Test
    void shouldLeaveTheDrySignalAloneWhenReverbMixIsZero() {
        ReverbEffect reverb = new ReverbEffect(AudioTestSupport.RATE);
        reverb.setParameter("mix", 0f);
        float[][] buffers = impulse(5000);
        reverb.process(buffers[0], buffers[1], 0, 5000);
        assertEquals(1f, buffers[0][0]);
        assertEquals(0f, AudioTestSupport.peak(buffers[0], 1, 5000));
    }

    @Test
    void shouldFilterWithTheFilterEffect() {
        FilterEffect filter = new FilterEffect(AudioTestSupport.RATE);
        filter.setParameter("mode", 0);
        filter.setParameter("cutoff", 500f);
        float[] left = AudioTestSupport.sine(8000.0, 20000);
        float[] right = left.clone();
        filter.process(left, right, 0, 20000);
        assertTrue(AudioTestSupport.rms(left, 10000, 20000) < 0.05);
        assertTrue(AudioTestSupport.rms(right, 10000, 20000) < 0.05);
    }

    @Test
    void shouldCreateEffectsByTypeWithParameters() {
        Effect effect = Effects.create("delay", Map.of("time", 0.25f, "mix", 0.7f), AudioTestSupport.RATE);
        assertEquals("delay", effect.getType());
        assertEquals(0.25f, effect.getParameter("time"));
        assertEquals(0.7f, effect.getParameter("mix"));
        assertEquals(java.util.List.of("time", "feedback", "mix"), java.util.List.copyOf(effect.getParameters().keySet()));
    }

    @Test
    void shouldRejectUnknownEffectsAndParameters() {
        assertThrows(IllegalArgumentException.class, () -> Effects.create("chorus", AudioTestSupport.RATE));
        Effect reverb = Effects.create("reverb", AudioTestSupport.RATE);
        assertThrows(IllegalArgumentException.class, () -> reverb.setParameter("time", 1f));
        assertThrows(IllegalArgumentException.class, () -> reverb.getParameter("time"));
    }

    @Test
    void shouldClearEffectMemoryOnReset() {
        ReverbEffect reverb = new ReverbEffect(AudioTestSupport.RATE);
        reverb.setParameter("mix", 1f);
        float[][] first = impulse(3000);
        reverb.process(first[0], first[1], 0, 3000);
        reverb.reset();
        float[] left = new float[3000];
        float[] right = new float[3000];
        reverb.process(left, right, 0, 3000);
        assertEquals(0f, AudioTestSupport.peak(left));
    }
}
