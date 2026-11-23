package io.github.ldev22live.gdxunity.engine.audio;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.github.ldev22live.gdxunity.engine.audio.SoundFontBuilder.ATTACK;
import static io.github.ldev22live.gdxunity.engine.audio.SoundFontBuilder.ATTENUATION;
import static io.github.ldev22live.gdxunity.engine.audio.SoundFontBuilder.KEY_RANGE;
import static io.github.ldev22live.gdxunity.engine.audio.SoundFontBuilder.PAN;
import static io.github.ldev22live.gdxunity.engine.audio.SoundFontBuilder.RELEASE;
import static io.github.ldev22live.gdxunity.engine.audio.SoundFontBuilder.ROOT_KEY_OVERRIDE;
import static io.github.ldev22live.gdxunity.engine.audio.SoundFontBuilder.SAMPLE_MODES;
import static io.github.ldev22live.gdxunity.engine.audio.SoundFontBuilder.keyRange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundFontTest {
    /** Release of -4000 timecents is about 0.1 s. */
    private static final int SHORT_RELEASE = -4000;

    private static SoundFontInstrument play(byte[] file, int key) throws IOException {
        SoundFont font = SoundFont.read(file);
        SoundFontInstrument instrument = new SoundFontInstrument(AudioTestSupport.RATE, font, font.getPresets().get(0));
        instrument.noteOn(key, 1f);
        return instrument;
    }

    @Test
    void shouldReadPresetsWithTheirBankAndProgram() throws IOException {
        SoundFont font = SoundFont.read(new SoundFontBuilder().presetName("Warm Keys", 0, 5).zone().build());
        assertEquals(1, font.getPresets().size());
        assertEquals("Warm Keys", font.getPresets().get(0).getName());
        assertEquals(5, font.getPresets().get(0).getProgram());
        assertNotNull(font.findPreset(0, 5));
        assertNull(font.findPreset(0, 6));
    }

    @Test
    void shouldPlayTheSampleAtItsRootKeyWithItsOwnPitch() throws IOException {
        SoundFontInstrument instrument = play(new SoundFontBuilder().zone(SAMPLE_MODES, 1).build(), 69);
        float[] data = AudioTestSupport.render(instrument, 20000);
        assertEquals(440.0, AudioTestSupport.frequency(data, 1000, 20000), 3.0);
    }

    @Test
    void shouldTransposeOtherKeysByTheRatioOfTheirPitch() throws IOException {
        SoundFontInstrument instrument = play(new SoundFontBuilder().zone(SAMPLE_MODES, 1).build(), 81);
        float[] data = AudioTestSupport.render(instrument, 20000);
        assertEquals(880.0, AudioTestSupport.frequency(data, 1000, 20000), 6.0);
    }

    @Test
    void shouldUseTheOverridingRootKey() throws IOException {
        SoundFontInstrument instrument = play(new SoundFontBuilder().zone(SAMPLE_MODES, 1, ROOT_KEY_OVERRIDE, 57).build(), 69);
        float[] data = AudioTestSupport.render(instrument, 20000);
        assertEquals(880.0, AudioTestSupport.frequency(data, 1000, 20000), 6.0);
    }

    @Test
    void shouldLoopWhileTheNoteIsHeldAndOtherwiseEndWithTheSample() throws IOException {
        byte[] looping = new SoundFontBuilder().zone(SAMPLE_MODES, 1).build();
        byte[] oneShot = new SoundFontBuilder().zone(SAMPLE_MODES, 0).build();
        float[] loop = AudioTestSupport.render(play(looping, 69), 20000);
        float[] once = AudioTestSupport.render(play(oneShot, 69), 20000);
        assertTrue(AudioTestSupport.peak(loop, 15000, 20000) > 0.1f, "loop stopped");
        assertEquals(0f, AudioTestSupport.peak(once, 15000, 20000), "one-shot kept playing");
        assertTrue(AudioTestSupport.peak(once, 0, 4000) > 0.1f);
    }

    @Test
    void shouldChooseTheZoneThatCoversTheKey() throws IOException {
        byte[] file = new SoundFontBuilder()
            .zone(KEY_RANGE, keyRange(0, 59), SAMPLE_MODES, 1)
            .zone(KEY_RANGE, keyRange(60, 127), SAMPLE_MODES, 1, ATTENUATION, 960)
            .build();
        float[] low = AudioTestSupport.render(play(file, 48), 8000);
        float[] high = AudioTestSupport.render(play(file, 72), 8000);
        assertTrue(AudioTestSupport.peak(low) > 0.3f);
        // 960 centibels of attenuation is 48 dB: the high zone is nearly silent
        assertTrue(AudioTestSupport.peak(high) < AudioTestSupport.peak(low) * 0.02f);
    }

    @Test
    void shouldBeSilentForAKeyNoZoneCovers() throws IOException {
        SoundFontInstrument instrument = play(new SoundFontBuilder().zone(KEY_RANGE, keyRange(60, 72)).build(), 30);
        assertEquals(0f, AudioTestSupport.peak(AudioTestSupport.render(instrument, 4000)));
        assertEquals(0, instrument.getActiveVoiceCount());
    }

    @Test
    void shouldApplyPanFromTheZone() throws IOException {
        SoundFontInstrument instrument = play(new SoundFontBuilder().zone(SAMPLE_MODES, 1, PAN, -500).build(), 69);
        float[] left = new float[4000];
        float[] right = new float[4000];
        instrument.render(left, right, 0, 4000);
        assertTrue(AudioTestSupport.peak(left) > 0.1f);
        assertEquals(0f, AudioTestSupport.peak(right), 1e-4f);
    }

    @Test
    void shouldRiseSlowlyWithALongAttack() throws IOException {
        // 1200 timecents is two seconds
        float[] data = AudioTestSupport.render(play(new SoundFontBuilder().zone(SAMPLE_MODES, 1, ATTACK, 1200).build(), 69), 44100);
        assertTrue(AudioTestSupport.peak(data, 0, 2000) < 0.05f);
        assertTrue(AudioTestSupport.peak(data, 40000, 44100) > AudioTestSupport.peak(data, 0, 4000) * 5);
    }

    @Test
    void shouldReleaseAfterNoteOffOverTheReleaseTime() throws IOException {
        SoundFont font = SoundFont.read(new SoundFontBuilder().zone(SAMPLE_MODES, 1, RELEASE, SHORT_RELEASE).build());
        SoundFontInstrument instrument = new SoundFontInstrument(AudioTestSupport.RATE, font, font.getPresets().get(0));
        instrument.noteOn(69, 1f);
        AudioTestSupport.render(instrument, 4000);
        instrument.noteOff(69);
        float[] data = AudioTestSupport.render(instrument, 22050);
        assertTrue(AudioTestSupport.peak(data, 0, 500) > 0.05f);
        assertEquals(0f, AudioTestSupport.peak(data, 15000, 22050));
        assertEquals(0, instrument.getActiveVoiceCount());
    }

    @Test
    void shouldPlayLouderForHigherVelocity() throws IOException {
        SoundFont font = SoundFont.read(new SoundFontBuilder().zone(SAMPLE_MODES, 1).build());
        SoundFontInstrument loud = new SoundFontInstrument(AudioTestSupport.RATE, font, font.getPresets().get(0));
        SoundFontInstrument soft = new SoundFontInstrument(AudioTestSupport.RATE, font, font.getPresets().get(0));
        loud.noteOn(69, 1f);
        soft.noteOn(69, 0.5f);
        float ratio = AudioTestSupport.peak(AudioTestSupport.render(loud, 4000)) / AudioTestSupport.peak(AudioTestSupport.render(soft, 4000));
        assertEquals(4f, ratio, 0.3f);
    }

    @Test
    void shouldRejectFilesThatAreNotSoundFonts() {
        assertThrows(IOException.class, () -> SoundFont.read(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}));
        assertThrows(IOException.class, () -> SoundFont.read("RIFF\0\0\0\0WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    }

    @Test
    void shouldRejectASoundFontWithoutSampleData() {
        byte[] file = new SoundFontBuilder().zone().build();
        // Rename the sample chunk so the reader cannot find it
        for (int i = 0; i + 4 < file.length; i++) {
            if (file[i] == 's' && file[i + 1] == 'm' && file[i + 2] == 'p' && file[i + 3] == 'l') {
                file[i] = 'x';
            }
        }
        IOException e = assertThrows(IOException.class, () -> SoundFont.read(file));
        assertTrue(e.getMessage().contains("sample data"), e.getMessage());
    }
}
