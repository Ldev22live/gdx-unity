package io.github.ldev22live.gdxunity.engine.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdsrTest {
    private static final float RATE = 1000f;

    private static Adsr envelope() {
        Adsr adsr = new Adsr(RATE);
        adsr.set(0.1f, 0.2f, 0.5f, 0.3f);
        return adsr;
    }

    private static void advance(Adsr adsr, int samples) {
        for (int i = 0; i < samples; i++) {
            adsr.next();
        }
    }

    @Test
    void shouldBeSilentAndFinishedBeforeANoteStarts() {
        Adsr adsr = envelope();
        assertTrue(adsr.isFinished());
        assertEquals(0f, adsr.next());
    }

    @Test
    void shouldRiseToFullLevelOverTheAttackTime() {
        Adsr adsr = envelope();
        adsr.noteOn();
        advance(adsr, 50);
        assertEquals(0.5f, adsr.getLevel(), 0.02f);
        advance(adsr, 50);
        assertEquals(1f, adsr.getLevel(), 0.02f);
    }

    @Test
    void shouldFallToTheSustainLevelAndStayThere() {
        Adsr adsr = envelope();
        adsr.noteOn();
        advance(adsr, 100 + 200 + 5);
        assertEquals(Adsr.Stage.SUSTAIN, adsr.getStage());
        advance(adsr, 5000);
        assertEquals(0.5f, adsr.getLevel(), 1e-6f);
    }

    @Test
    void shouldFadeToSilenceAfterNoteOffAndThenReportFinished() {
        Adsr adsr = envelope();
        adsr.noteOn();
        advance(adsr, 400);
        adsr.noteOff();
        advance(adsr, 150);
        float halfway = adsr.getLevel();
        assertTrue(halfway > 0.0001f && halfway < 0.5f, "level halfway through release was " + halfway);
        assertFalse(adsr.isFinished());
        advance(adsr, 400);
        assertTrue(adsr.isFinished());
        assertEquals(0f, adsr.getLevel());
    }

    @Test
    void shouldReleaseFromTheCurrentLevelWhenReleasedDuringTheAttack() {
        Adsr adsr = envelope();
        adsr.noteOn();
        advance(adsr, 50);
        float before = adsr.getLevel();
        adsr.noteOff();
        adsr.next();
        assertTrue(adsr.getLevel() <= before && adsr.getLevel() > before * 0.9f);
    }

    @Test
    void shouldIgnoreNoteOffWhenNothingIsPlaying() {
        Adsr adsr = envelope();
        adsr.noteOff();
        assertEquals(Adsr.Stage.IDLE, adsr.getStage());
    }

    @Test
    void shouldRejectANonPositiveSampleRate() {
        assertThrows(IllegalArgumentException.class, () -> new Adsr(0f));
    }
}
