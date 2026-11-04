package io.github.ldev22live.gdxunity.engine.audio.song;

import io.github.ldev22live.gdxunity.engine.audio.DelayEffect;
import io.github.ldev22live.gdxunity.engine.audio.SfxrParams;
import io.github.ldev22live.gdxunity.engine.audio.SynthPatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongIOTest {
    private static Song richSong() {
        Song song = Song.createDefault("Tricky \"name\"\n\\ ünï");
        song.setBpm(133.5f);
        song.setSwing(0.25f);
        Pattern pattern = song.getPatterns().get(0);
        Channel kick = song.getChannels().get(0);
        Channel lead = song.getChannels().get(3);
        pattern.setNotes(kick.getId(), StepGrid.toggled(pattern, kick, 0, 0.9f));
        pattern.setNotes(lead.getId(), List.of(new Note(0, 48, 72, 0.5f), new Note(96, 24, 74, 1f)));
        Pattern second = song.addPattern("Bridge", 2);
        second.setNotes(lead.getId(), List.of(new Note(500, 30, 60, 0.7f)));
        song.setClips(List.of(new PlaylistClip(pattern.getId(), 0, 0, Song.TICKS_PER_BAR),
            new PlaylistClip(second.getId(), 1, Song.TICKS_PER_BAR, 2 * Song.TICKS_PER_BAR)));
        song.getChannels().get(1).setMuted(true);
        lead.getInstrument().patch.cutoff = 1234f;
        song.addChannel("Zap", InstrumentSpec.sfxr(SfxrParams.preset("Laser")), 5);
        song.addChannel("Piano", InstrumentSpec.soundFont("sf/piano.sf2", 0, 3), 6);
        InstrumentSpec sample = InstrumentSpec.sample("samples/kick.wav");
        sample.rootNote = 48;
        sample.oneShot = true;
        song.addChannel("Smp", sample, 7);
        DelayEffect delay = new DelayEffect(Song.SAMPLE_RATE);
        delay.setParameter("time", 0.33f);
        song.getMixer().getStrip(3).addEffect(delay);
        song.getMixer().getStrip(3).setPan(-0.4f);
        song.getMixer().getStrip(3).setSolo(true);
        song.getMixer().getMaster().setVolume(0.9f);
        return song;
    }

    @Test
    void shouldRoundTripEverythingInASong() {
        Song song = richSong();
        String json = SongIO.toJson(song);
        Song read = SongIO.fromJson(json);
        assertEquals(json, SongIO.toJson(read));
        assertEquals(song.getName(), read.getName());
        assertEquals(133.5f, read.getBpm());
        assertEquals(0.25f, read.getSwing());
        assertEquals(song.getChannels().size(), read.getChannels().size());
        assertEquals(1234f, read.getChannels().get(3).getInstrument().patch.cutoff);
        assertTrue(read.getChannels().get(1).isMuted());
        assertEquals(InstrumentSpec.Type.SOUNDFONT, read.getChannels().get(6).getInstrument().type);
        assertEquals(3, read.getChannels().get(6).getInstrument().program);
        assertEquals(48, read.getChannels().get(7).getInstrument().rootNote);
        assertEquals(List.of(new Note(0, 48, 72, 0.5f), new Note(96, 24, 74, 1f)),
            read.getPatterns().get(0).getNotes(read.getChannels().get(3).getId()));
        assertEquals(song.getClips(), read.getClips());
        assertEquals("delay", read.getMixer().getStrip(3).getEffects().get(0).getType());
        assertEquals(0.33f, read.getMixer().getStrip(3).getEffects().get(0).getParameter("time"));
        assertEquals(-0.4f, read.getMixer().getStrip(3).getPan());
        assertTrue(read.getMixer().getStrip(3).isSolo());
        assertEquals(0.9f, read.getMixer().getMaster().getVolume());
    }

    @Test
    void shouldKeepIdsStableAndContinueNumberingAfterLoading() {
        Song song = Song.createDefault("s");
        song.removeChannel(song.getChannels().get(2).getId());
        Song read = SongIO.fromJson(SongIO.toJson(song));
        assertEquals(song.getChannels().stream().map(Channel::getId).toList(),
            read.getChannels().stream().map(Channel::getId).toList());
        Channel added = read.addChannel("New", InstrumentSpec.drums(), 0);
        for (Channel existing : read.getChannels()) {
            if (existing != added) {
                assertTrue(existing.getId() != added.getId(), "id " + added.getId() + " is already used");
            }
        }
        assertTrue(added.getId() > 5, "ids of removed channels are not reused");
    }

    @Test
    void shouldLoadASongThatSoundsTheSame() throws Exception {
        Song song = Song.createDefault("s");
        song.setSwing(0.5f);
        Pattern pattern = song.getPatterns().get(0);
        Channel kick = song.getChannels().get(0);
        Channel lead = song.getChannels().get(3);
        pattern.setNotes(kick.getId(), StepGrid.toggled(pattern, kick, 0, 1f));
        pattern.setNotes(lead.getId(), List.of(new Note(24, 48, 64, 0.8f)));
        song.getMixer().getStrip(3).setPan(0.5f);
        Song read = SongIO.fromJson(SongIO.toJson(song));
        int id = pattern.getId();
        org.junit.jupiter.api.Assertions.assertArrayEquals(
            SongExporter.render(song, MusicAssets.NONE, Schedule.Mode.PATTERN, id).getLeft(),
            SongExporter.render(read, MusicAssets.NONE, Schedule.Mode.PATTERN, id).getLeft());
    }

    @Test
    void shouldRejectUnsupportedVersionsAndGarbage() {
        assertThrows(IllegalArgumentException.class, () -> SongIO.fromJson("{\"version\":99}"));
        assertThrows(IllegalArgumentException.class, () -> SongIO.fromJson("{}"));
        assertThrows(IllegalArgumentException.class, () -> SongIO.fromJson("not json at all"));
        assertThrows(IllegalArgumentException.class, () -> SongIO.fromJson("[1,2]"));
        assertThrows(IllegalArgumentException.class, () -> SongIO.fromJson(""));
    }

    @Test
    void shouldRejectInvalidContentWithAClearMessage() {
        String json = SongIO.toJson(Song.createDefault("s")).replace("\"bpm\":120.0", "\"bpm\":9999");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SongIO.fromJson(json));
        assertTrue(e.getMessage().contains("bpm"), e.getMessage());

        String badEffect = SongIO.toJson(richSong()).replace("\"type\":\"delay\"", "\"type\":\"chorus\"");
        IllegalArgumentException effect = assertThrows(IllegalArgumentException.class, () -> SongIO.fromJson(badEffect));
        assertTrue(effect.getMessage().contains("chorus"), effect.getMessage());
    }

    @Test
    void shouldRejectAChannelOnAMissingMixerStrip() {
        String json = SongIO.toJson(Song.createDefault("s")).replaceFirst("\"mixerIndex\":0", "\"mixerIndex\":40");
        assertThrows(IllegalArgumentException.class, () -> SongIO.fromJson(json));
    }

    @Test
    void shouldWriteReadableJsonThatKeepsSynthSettings() {
        String json = SongIO.toJson(Song.createDefault("s"));
        assertTrue(json.contains("\"version\":1"));
        assertTrue(json.contains("\"cutoff\""));
        SynthPatch lead = SynthPatch.preset("Saw Lead");
        assertTrue(json.contains("\"lfoRate\":" + lead.lfoRate));
    }
}
