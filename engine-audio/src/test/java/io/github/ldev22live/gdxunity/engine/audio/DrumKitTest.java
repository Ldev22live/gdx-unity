package io.github.ldev22live.gdxunity.engine.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrumKitTest {
    @Test
    void shouldSoundEveryDrumOnItsOwnNoteAndThenFallSilent() {
        for (DrumKit.Drum drum : DrumKit.drums()) {
            DrumKit kit = new DrumKit(AudioTestSupport.RATE);
            kit.noteOn(drum.getNote(), 1f);
            float[] data = AudioTestSupport.render(kit, (int) (2.5f * AudioTestSupport.RATE));
            assertTrue(AudioTestSupport.peak(data, 0, 4000) > 0.05f, drum + " is too quiet");
            assertTrue(AudioTestSupport.allFinite(data), drum + " produced NaN");
            assertTrue(AudioTestSupport.peak(data) < 2f, drum + " is far too loud");
            assertEquals(0f, AudioTestSupport.peak(data, data.length - 1000, data.length), drum + " never ended");
        }
    }

    @Test
    void shouldIgnoreNotesThatAreNoDrum() {
        DrumKit kit = new DrumKit(AudioTestSupport.RATE);
        kit.noteOn(61, 1f);
        assertEquals(0f, AudioTestSupport.peak(AudioTestSupport.render(kit, 4000)));
    }

    @Test
    void shouldMakeTheKickLowerThanTheHat() {
        DrumKit kit = new DrumKit(AudioTestSupport.RATE);
        kit.noteOn(36, 1f);
        float[] kick = AudioTestSupport.render(kit, 12000);
        kit.noteOn(42, 1f);
        float[] hat = AudioTestSupport.render(kit, 2000);
        assertTrue(AudioTestSupport.frequency(kick, 3000, 12000) < 120.0);
        // A hat is noise: many more zero crossings per second than the kick
        int hatCrossings = 0;
        for (int i = 1; i < hat.length; i++) {
            if (hat[i - 1] < 0f && hat[i] >= 0f) {
                hatCrossings++;
            }
        }
        assertTrue(hatCrossings > 100, "hat crossings " + hatCrossings);
    }

    @Test
    void shouldScaleHitsWithVelocity() {
        DrumKit loud = new DrumKit(AudioTestSupport.RATE);
        DrumKit soft = new DrumKit(AudioTestSupport.RATE);
        loud.noteOn(38, 1f);
        soft.noteOn(38, 0.5f);
        assertEquals(2f, AudioTestSupport.peak(AudioTestSupport.render(loud, 3000))
            / AudioTestSupport.peak(AudioTestSupport.render(soft, 3000)), 0.01f);
    }

    @Test
    void shouldStopEverythingOnAllNotesOff() {
        DrumKit kit = new DrumKit(AudioTestSupport.RATE);
        kit.noteOn(49, 1f);
        kit.allNotesOff();
        assertEquals(0f, AudioTestSupport.peak(AudioTestSupport.render(kit, 2000)));
    }

    @Test
    void shouldLetTwoHitsOverlap() {
        DrumKit kit = new DrumKit(AudioTestSupport.RATE);
        kit.noteOn(36, 1f);
        float[] single = AudioTestSupport.render(kit, 2000);
        DrumKit both = new DrumKit(AudioTestSupport.RATE);
        both.noteOn(36, 1f);
        both.noteOn(36, 1f);
        float[] doubled = AudioTestSupport.render(both, 2000);
        assertEquals(2f, AudioTestSupport.peak(doubled) / AudioTestSupport.peak(single), 0.01f);
    }

    @Test
    void shouldFindDrumsByNote() {
        assertEquals(DrumKit.Drum.SNARE, DrumKit.drumForNote(38));
        assertNull(DrumKit.drumForNote(0));
    }
}
