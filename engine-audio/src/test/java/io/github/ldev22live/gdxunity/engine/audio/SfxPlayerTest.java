package io.github.ldev22live.gdxunity.engine.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SfxPlayerTest {
    private static final int BLOCK = 4096;

    private static float peak(float[] buffer) {
        float peak = 0f;
        for (float value : buffer) {
            peak = Math.max(peak, Math.abs(value));
        }
        return peak;
    }

    /** Renders blocks until the player is silent; returns the frames rendered. */
    private static int renderUntilDone(SfxPlayer player) {
        float[] left = new float[BLOCK];
        float[] right = new float[BLOCK];
        int frames = 0;
        do {
            player.render(left, right, BLOCK);
            frames += BLOCK;
        } while (player.getActiveVoices() > 0 && frames < 20 * SfxPlayer.SAMPLE_RATE);
        return frames;
    }

    @Test
    void shouldBeSilentUntilASoundPlays() {
        SfxPlayer player = new SfxPlayer();
        float[] left = new float[BLOCK];
        float[] right = new float[BLOCK];

        player.render(left, right, BLOCK);

        assertEquals(0f, peak(left));
        assertEquals(0, player.getActiveVoices());
    }

    @Test
    void shouldMixAPlayedSoundAndFinishIt() {
        SfxPlayer player = new SfxPlayer();
        float[] left = new float[BLOCK];
        float[] right = new float[BLOCK];

        player.play("Coin", 1f, 1f);
        player.render(left, right, BLOCK);

        assertTrue(peak(left) > 0.05f, "a played effect is audible");
        assertEquals(1, player.getActiveVoices());
        renderUntilDone(player);
        assertEquals(0, player.getActiveVoices());
    }

    @Test
    void shouldScaleLevelByVolume() {
        SfxPlayer loud = new SfxPlayer();
        SfxPlayer quiet = new SfxPlayer();
        float[] loudLeft = new float[BLOCK];
        float[] quietLeft = new float[BLOCK];
        float[] ignored = new float[BLOCK];

        loud.play("Laser", 1f, 1f);
        quiet.play("Laser", 0.25f, 1f);
        loud.render(loudLeft, ignored, BLOCK);
        quiet.render(quietLeft, ignored, BLOCK);

        assertEquals(peak(loudLeft) * 0.25f, peak(quietLeft), 1e-4f);
    }

    @Test
    void shouldFinishSoonerWhenPitchedUp() {
        SfxPlayer normal = new SfxPlayer();
        SfxPlayer octaveUp = new SfxPlayer();
        normal.play("Explosion", 1f, 1f);
        octaveUp.play("Explosion", 1f, 2f);

        int normalFrames = renderUntilDone(normal);
        int fasterFrames = renderUntilDone(octaveUp);

        assertTrue(fasterFrames < normalFrames, "twice the pitch plays in about half the time: "
            + fasterFrames + " vs " + normalFrames);
    }

    @Test
    void shouldAddOverlappingSoundsAndCutTheOldestPastTheVoiceLimit() {
        SfxPlayer player = new SfxPlayer();
        float[] left = new float[BLOCK];
        float[] right = new float[BLOCK];

        for (int i = 0; i < SfxPlayer.MAX_VOICES + 4; i++) {
            player.play("Explosion", 0.1f, 1f);
        }
        player.render(left, right, BLOCK);

        assertEquals(SfxPlayer.MAX_VOICES, player.getActiveVoices());
    }

    @Test
    void shouldRejectUnknownSoundAndBadArguments() {
        SfxPlayer player = new SfxPlayer();

        assertFalse(SfxPlayer.isKnown("Kazoo"));
        assertTrue(SfxPlayer.isKnown("Jump"));
        assertThrows(IllegalArgumentException.class, () -> player.play("Kazoo", 1f, 1f));
        assertThrows(IllegalArgumentException.class, () -> player.play("Coin", 1.5f, 1f));
        assertThrows(IllegalArgumentException.class, () -> player.play("Coin", 1f, 0f));
        assertThrows(IllegalArgumentException.class, () -> player.prepare("Kazoo"));
    }

    @Test
    void shouldHaveASoundForEveryFactoryPreset() {
        SfxPlayer player = new SfxPlayer();
        for (String name : SfxrParams.presetNames()) {
            float[] left = new float[BLOCK];
            float[] right = new float[BLOCK];
            player.play(name, 1f, 1f);
            player.render(left, right, BLOCK);
            assertTrue(peak(left) > 0f, name + " makes sound");
            renderUntilDone(player);
        }
    }

    /** A clip of a constant level, so what comes out of a speaker is easy to predict. */
    private static SampleData steady(float level) {
        float[] samples = new float[2000];
        java.util.Arrays.fill(samples, level);
        return new SampleData(samples, SfxPlayer.SAMPLE_RATE);
    }

    private static float[] firstSamples(SfxPlayer player, boolean leftSide) {
        float[] left = new float[64];
        float[] right = new float[64];
        player.render(left, right, 64);
        return leftSide ? left : right;
    }

    @Test
    void shouldPlayARecordedClipByTheNameItWasAddedUnder() {
        SfxPlayer player = new SfxPlayer();
        player.addClip("sounds/hit.wav", steady(0.5f));

        assertTrue(player.isAvailable("sounds/hit.wav"));
        assertFalse(player.isAvailable("sounds/other.wav"));
        player.play("sounds/hit.wav", 1f, 1f);

        assertEquals(0.5f, firstSamples(player, true)[10], 1e-4f);
    }

    @Test
    void shouldNotLetAClipTakeTheNameOfAFactoryEffectOrHaveNoName() {
        SfxPlayer player = new SfxPlayer();

        assertThrows(IllegalArgumentException.class, () -> player.addClip("Coin", steady(0.5f)));
        assertThrows(IllegalArgumentException.class, () -> player.addClip(" ", steady(0.5f)));
        assertTrue(player.isAvailable("Coin"));
    }

    @Test
    void shouldKeepBothSpeakersAtFullVolumeInTheMiddleAndQuietenTheFarOneWhenPanned() {
        SfxPlayer middle = new SfxPlayer();
        middle.addClip("clip.wav", steady(1f));
        middle.play("clip.wav", 0.8f, 1f, 0f);
        float[] left = new float[64];
        float[] right = new float[64];
        middle.render(left, right, 64);
        assertEquals(0.8f, left[5], 1e-4f);
        assertEquals(0.8f, right[5], 1e-4f);

        SfxPlayer toTheRight = new SfxPlayer();
        toTheRight.addClip("clip.wav", steady(1f));
        toTheRight.play("clip.wav", 0.8f, 1f, 0.75f);
        toTheRight.render(left, right, 64);
        assertEquals(0.2f, left[5], 1e-4f, "the left speaker is a quarter as loud");
        assertEquals(0.8f, right[5], 1e-4f);

        SfxPlayer hardLeft = new SfxPlayer();
        hardLeft.addClip("clip.wav", steady(1f));
        hardLeft.play("clip.wav", 1f, 1f, -1f);
        hardLeft.render(left, right, 64);
        assertEquals(1f, left[5], 1e-4f);
        assertEquals(0f, right[5], 1e-4f);
    }

    @Test
    void shouldRefuseAPanOutsideMinusOneToOne() {
        SfxPlayer player = new SfxPlayer();

        assertThrows(IllegalArgumentException.class, () -> player.play("Coin", 1f, 1f, 1.5f));
        assertThrows(IllegalArgumentException.class, () -> player.play("Coin", 1f, 1f, Float.NaN));
    }
}
