package io.github.ldev22live.gdxunity.engine.audio.song;

import io.github.ldev22live.gdxunity.engine.audio.SynthPatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongTest {
    private static InstrumentSpec synth() {
        return InstrumentSpec.synth(new SynthPatch());
    }

    @Test
    void shouldStartWithTheSequencerRowsAndAnEmptyPatternWhenCreatedByDefault() {
        Song song = Song.createDefault("Demo");
        assertEquals(5, song.getChannels().size());
        assertEquals(List.of("Kick", "Snare", "Hat", "Lead", "Bass"),
            song.getChannels().stream().map(Channel::getName).toList());
        assertEquals(36, song.getChannels().get(0).getStepPitch());
        assertEquals(1, song.getPatterns().size());
        assertEquals(Song.STEPS_PER_BAR, StepGrid.stepCount(song.getPatterns().get(0)));
        assertEquals(120f, song.getBpm());
        assertEquals("Kick", song.getMixer().getStrip(0).getName());
    }

    @Test
    void shouldNumberChannelsAndPatternsAndFindThemById() {
        Song song = new Song("s");
        Channel a = song.addChannel("A", synth(), 0);
        Channel b = song.addChannel("B", synth(), 1);
        assertNotNull(song.getChannel(a.getId()));
        assertEquals("B", song.getChannel(b.getId()).getName());
        assertNull(song.getChannel(99));
        Pattern p = song.addPattern("P", 2);
        assertEquals(2 * Song.TICKS_PER_BAR, p.getLengthTicks());
        assertEquals(p, song.getPattern(p.getId()));
    }

    @Test
    void shouldLimitTheNumberOfChannels() {
        Song song = new Song("s");
        for (int i = 0; i < Song.MAX_CHANNELS; i++) {
            song.addChannel("C" + i, synth(), 0);
        }
        assertThrows(IllegalStateException.class, () -> song.addChannel("one too many", synth(), 0));
    }

    @Test
    void shouldRejectAChannelOnAMixerStripThatDoesNotExist() {
        Song song = new Song("s");
        assertThrows(IllegalArgumentException.class, () -> song.addChannel("x", synth(), song.getMixer().getStripCount()));
        assertThrows(IllegalArgumentException.class, () -> song.addChannel("x", synth(), -1));
    }

    @Test
    void shouldRemoveAChannelWithItsNotesAndPutItBackWhenInserted() {
        Song song = new Song("s");
        Channel a = song.addChannel("A", synth(), 0);
        Channel b = song.addChannel("B", synth(), 1);
        Pattern p = song.addPattern("P", 1);
        p.setNotes(a.getId(), List.of(new Note(0, 24, 60, 1f)));
        p.setNotes(b.getId(), List.of(new Note(0, 24, 62, 1f)));
        List<Note> saved = p.getNotes(a.getId());
        assertEquals(0, song.removeChannel(a.getId()));
        assertEquals(1, song.getChannels().size());
        assertTrue(p.getNotes(a.getId()).isEmpty());
        assertEquals(1, p.getNotes(b.getId()).size());
        assertEquals(-1, song.removeChannel(a.getId()));

        song.insertChannel(0, a);
        p.setNotes(a.getId(), saved);
        assertEquals(List.of("A", "B"), song.getChannels().stream().map(Channel::getName).toList());
        assertEquals(1, p.getNotes(a.getId()).size());
        assertThrows(IllegalArgumentException.class, () -> song.insertChannel(0, a));
    }

    @Test
    void shouldValidateTempoAndSwing() {
        Song song = new Song("s");
        song.setBpm(90f);
        assertEquals(90f, song.getBpm());
        assertThrows(IllegalArgumentException.class, () -> song.setBpm(5f));
        assertThrows(IllegalArgumentException.class, () -> song.setBpm(1000f));
        assertThrows(IllegalArgumentException.class, () -> song.setBpm(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> song.setSwing(1.5f));
        assertEquals(90f, song.getBpm());
    }

    @Test
    void shouldCutNotesWhenAPatternIsShortened() {
        Song song = new Song("s");
        Channel c = song.addChannel("A", synth(), 0);
        Pattern p = song.addPattern("P", 2);
        p.setNotes(c.getId(), List.of(
            new Note(0, 96, 60, 1f),
            new Note(Song.TICKS_PER_BAR - 24, 96, 62, 1f),
            new Note(Song.TICKS_PER_BAR + 48, 24, 64, 1f)));
        p.setLengthTicks(Song.TICKS_PER_BAR);
        List<Note> notes = p.getNotes(c.getId());
        assertEquals(2, notes.size());
        assertEquals(24, notes.get(1).length(), "the second note is cut at the pattern end");
    }

    @Test
    void shouldDropNotesPastThePatternEndWhenSettingNotes() {
        Song song = new Song("s");
        Channel c = song.addChannel("A", synth(), 0);
        Pattern p = song.addPattern("P", 1);
        p.setNotes(c.getId(), List.of(new Note(Song.TICKS_PER_BAR, 24, 60, 1f), new Note(0, 24, 60, 1f)));
        assertEquals(1, p.getNotes(c.getId()).size());
    }

    @Test
    void shouldKeepNotesSortedByTimeThenPitch() {
        Song song = new Song("s");
        Channel c = song.addChannel("A", synth(), 0);
        Pattern p = song.addPattern("P", 1);
        p.setNotes(c.getId(), List.of(new Note(48, 24, 60, 1f), new Note(0, 24, 67, 1f), new Note(0, 24, 60, 1f)));
        List<Note> notes = p.getNotes(c.getId());
        assertEquals(60, notes.get(0).pitch());
        assertEquals(67, notes.get(1).pitch());
        assertEquals(48, notes.get(2).tick());
    }

    @Test
    void shouldOnlyAcceptWholeBarPatternLengths() {
        Song song = new Song("s");
        Pattern p = song.addPattern("P", 1);
        assertThrows(IllegalArgumentException.class, () -> p.setLengthTicks(100));
        assertThrows(IllegalArgumentException.class, () -> song.addPattern("zero", 0));
        assertThrows(IllegalArgumentException.class, () -> song.addPattern("huge", Song.MAX_PATTERN_BARS + 1));
    }

    @Test
    void shouldNotAllowClipsToOverlapOnATrack() {
        Song song = new Song("s");
        Pattern p = song.addPattern("P", 1);
        PlaylistClip first = new PlaylistClip(p.getId(), 0, 0, Song.TICKS_PER_BAR);
        song.setClips(List.of(first));
        assertFalse(song.canPlaceClip(new PlaylistClip(p.getId(), 0, 100, Song.TICKS_PER_BAR), -1));
        assertTrue(song.canPlaceClip(new PlaylistClip(p.getId(), 1, 100, Song.TICKS_PER_BAR), -1));
        assertTrue(song.canPlaceClip(new PlaylistClip(p.getId(), 0, Song.TICKS_PER_BAR, Song.TICKS_PER_BAR), -1));
        assertTrue(song.canPlaceClip(first.withStartTick(50), 0), "a clip may overlap its own old place");
        assertFalse(song.canPlaceClip(new PlaylistClip(999, 0, 5000, Song.TICKS_PER_BAR), -1), "unknown pattern");
    }

    @Test
    void shouldKeepTheOldPlaylistWhenANewOneIsInvalid() {
        Song song = new Song("s");
        Pattern p = song.addPattern("P", 1);
        PlaylistClip ok = new PlaylistClip(p.getId(), 0, 0, Song.TICKS_PER_BAR);
        song.setClips(List.of(ok));
        PlaylistClip overlapping = new PlaylistClip(p.getId(), 0, 10, Song.TICKS_PER_BAR);
        assertThrows(IllegalArgumentException.class, () -> song.setClips(List.of(ok, overlapping)));
        assertEquals(List.of(ok), song.getClips());
    }

    @Test
    void shouldRemoveClipsOfARemovedPattern() {
        Song song = new Song("s");
        Pattern a = song.addPattern("A", 1);
        Pattern b = song.addPattern("B", 1);
        song.setClips(List.of(new PlaylistClip(a.getId(), 0, 0, Song.TICKS_PER_BAR),
            new PlaylistClip(b.getId(), 1, 0, Song.TICKS_PER_BAR)));
        assertEquals(0, song.removePattern(a.getId()));
        assertEquals(1, song.getClips().size());
        assertEquals(b.getId(), song.getClips().get(0).patternId());
        assertEquals(Song.TICKS_PER_BAR, song.getPlaylistLengthTicks());
    }

    @Test
    void shouldReportRevisionChangesForEditsThatNeedARebuildOnly() {
        Song song = new Song("s");
        Channel c = song.addChannel("A", synth(), 0);
        Pattern p = song.addPattern("P", 1);
        int schedule = song.getScheduleRevision();
        int instruments = song.getInstrumentRevision();

        p.setNotes(c.getId(), List.of(new Note(0, 24, 60, 1f)));
        assertTrue(song.getScheduleRevision() > schedule, "notes change the schedule");
        assertEquals(instruments, song.getInstrumentRevision());

        schedule = song.getScheduleRevision();
        song.getMixer().getStrip(0).setVolume(0.3f);
        c.getInstrument().patch.cutoff = 500f;
        assertEquals(schedule, song.getScheduleRevision(), "mixer and synth knobs are live");
        assertEquals(instruments, song.getInstrumentRevision());

        c.setInstrument(InstrumentSpec.drums());
        assertTrue(song.getInstrumentRevision() > instruments);
        c.setMuted(true);
        assertTrue(song.getScheduleRevision() > schedule);
    }

    @Test
    void shouldNameNotesLikeAPiano() {
        assertEquals("C4", Note.pitchName(60));
        assertEquals("F#3", Note.pitchName(54));
        assertEquals("A4", Note.pitchName(69));
        assertTrue(Note.isBlackKey(61));
        assertFalse(Note.isBlackKey(60));
    }

    @Test
    void shouldRejectInvalidNotesAndClips() {
        assertThrows(IllegalArgumentException.class, () -> new Note(-1, 1, 60, 1f));
        assertThrows(IllegalArgumentException.class, () -> new Note(0, 0, 60, 1f));
        assertThrows(IllegalArgumentException.class, () -> new Note(0, 1, 128, 1f));
        assertThrows(IllegalArgumentException.class, () -> new Note(0, 1, 60, 1.5f));
        assertThrows(IllegalArgumentException.class, () -> new Note(0, 1, 60, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new PlaylistClip(1, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new PlaylistClip(1, -1, 0, Song.TICKS_PER_BAR));
    }
}
