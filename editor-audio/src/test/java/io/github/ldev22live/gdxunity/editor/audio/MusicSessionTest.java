package io.github.ldev22live.gdxunity.editor.audio;

import io.github.ldev22live.gdxunity.engine.audio.SampleData;
import io.github.ldev22live.gdxunity.engine.audio.WavIO;
import io.github.ldev22live.gdxunity.engine.audio.song.Channel;
import io.github.ldev22live.gdxunity.engine.audio.song.InstrumentSpec;
import io.github.ldev22live.gdxunity.engine.audio.song.Note;
import io.github.ldev22live.gdxunity.engine.audio.song.Pattern;
import io.github.ldev22live.gdxunity.engine.audio.song.Song;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicSessionTest {
    @TempDir
    Path root;
    private MusicSession session;

    @BeforeEach
    void setUp() {
        session = new MusicSession(root);
    }

    @Test
    void shouldCreateASongFileWithTheDefaultRackAndOpenIt() {
        assertTrue(session.createSong("music/theme"));
        assertTrue(Files.isRegularFile(root.resolve("music/theme.song")));
        assertEquals("music/theme.song", session.getDocument().getPath());
        assertEquals(5, session.getDocument().getSong().getChannels().size());
        assertEquals("theme", session.getDocument().getSong().getName());
        assertFalse(session.getDocument().isDirty());
        assertNotNull(session.getPlayer());
        assertNotNull(session.getSelectedPattern());
        assertTrue(session.getSelectedChannelId() > 0);
    }

    @Test
    void shouldNotOverwriteAnExistingSongOrEscapeTheProject() throws IOException {
        Files.createDirectories(root.resolve("music"));
        Files.writeString(root.resolve("music/old.song"), "keep me");
        assertFalse(session.createSong("music/old.song"));
        assertTrue(session.getLastError().contains("already exists"), session.getLastError());
        assertEquals("keep me", Files.readString(root.resolve("music/old.song")));
        assertFalse(session.createSong("../outside.song"));
        assertTrue(session.getLastError().contains("outside"), session.getLastError());
        assertFalse(session.hasSong());
    }

    @Test
    void shouldSaveEditsAndReadThemBackOnOpen() {
        session.createSong("a");
        MusicDocument doc = session.getDocument();
        Channel kick = doc.getSong().getChannels().get(0);
        doc.toggleStep(session.getSelectedPattern(), kick, 3, 0.6f);
        doc.setBpm(99f);
        assertTrue(doc.isDirty());
        assertTrue(session.save());
        assertFalse(doc.isDirty());

        MusicSession other = new MusicSession(root);
        assertTrue(other.open("a.song"));
        assertEquals(99f, other.getDocument().getSong().getBpm());
        Pattern pattern = other.getSelectedPattern();
        assertEquals(1, pattern.getNotes(other.getDocument().getSong().getChannels().get(0).getId()).size());
    }

    @Test
    void shouldSaveUnsavedChangesWhenClosing() {
        session.createSong("b");
        session.getDocument().setBpm(77f);
        assertTrue(session.close());
        assertFalse(session.hasSong());
        assertNull(session.getPlayer());
        MusicSession other = new MusicSession(root);
        other.open("b");
        assertEquals(77f, other.getDocument().getSong().getBpm());
    }

    @Test
    void shouldKeepTheSongOpenWhenSavingFails() throws IOException {
        session.createSong("c");
        session.getDocument().setBpm(88f);
        // A directory where the file should be makes the write fail
        Files.delete(root.resolve("c.song"));
        Files.createDirectory(root.resolve("c.song"));
        assertFalse(session.close());
        assertTrue(session.hasSong());
        assertTrue(session.getLastError().contains("Could not save"), session.getLastError());
    }

    @Test
    void shouldReportABrokenSongFileWithoutLosingTheOpenSong() throws IOException {
        session.createSong("good");
        Files.writeString(root.resolve("bad.song"), "{\"version\":1,\"bpm\":9999}");
        assertFalse(session.open("bad.song"));
        assertTrue(session.getLastError().startsWith("Could not open bad.song"), session.getLastError());
        assertEquals("good.song", session.getDocument().getPath());
        assertFalse(session.open("missing.song"));
    }

    @Test
    void shouldListSongsInTheProjectSorted() throws IOException {
        Files.createDirectories(root.resolve("music/sub"));
        Files.writeString(root.resolve("z.song"), "{}");
        Files.writeString(root.resolve("music/a.song"), "{}");
        Files.writeString(root.resolve("music/sub/m.song"), "{}");
        Files.writeString(root.resolve("music/readme.txt"), "x");
        assertEquals(List.of("music/a.song", "music/sub/m.song", "z.song"), session.listSongs());
    }

    @Test
    void shouldSelectPatternsAndChannelsAndClearTheNoteSelection() {
        session.createSong("d");
        MusicDocument doc = session.getDocument();
        Pattern second = doc.addPattern("Two", 2);
        session.update();
        session.setSelectedNotes(Set.of(new Note(0, 24, 60, 1f)));
        session.selectPattern(second.getId());
        assertEquals(second, session.getSelectedPattern());
        assertTrue(session.getSelectedNotes().isEmpty());
        assertEquals(second.getId(), session.getPlayer().getPatternId(), "pattern mode plays the selected pattern");
        Channel lead = doc.getSong().getChannels().get(3);
        session.selectChannel(lead.getId());
        assertEquals(lead.getId(), session.getSelectedChannelId());
        assertThrows(IllegalArgumentException.class, () -> session.selectPattern(999));
        assertThrows(IllegalArgumentException.class, () -> session.selectChannel(999));
    }

    @Test
    void shouldMoveTheSelectionOffAPatternOrChannelThatWasRemoved() {
        session.createSong("e");
        MusicDocument doc = session.getDocument();
        Pattern second = doc.addPattern("Two", 1);
        session.selectPattern(second.getId());
        Channel last = doc.getSong().getChannels().get(4);
        session.selectChannel(last.getId());
        doc.removePattern(second);
        doc.removeChannel(last);
        session.update();
        assertNotNull(session.getSelectedPattern());
        assertNotEquals(second.getId(), session.getSelectedPattern().getId());
        assertNotEquals(last.getId(), session.getSelectedChannelId());
    }

    private static void assertNotEquals(int unexpected, int actual) {
        assertTrue(unexpected != actual, "unexpected " + actual);
    }

    @Test
    void shouldSwitchBetweenPatternAndSongPlayback() {
        session.createSong("f");
        assertFalse(session.isSongMode());
        session.setSongMode(true);
        assertTrue(session.isSongMode());
        session.setSongMode(false);
        assertFalse(session.isSongMode());
    }

    @Test
    void shouldExportThePatternAsAWavInsideTheProject() throws IOException {
        session.createSong("g");
        MusicDocument doc = session.getDocument();
        Channel kick = doc.getSong().getChannels().get(0);
        doc.toggleStep(session.getSelectedPattern(), kick, 0, 1f);
        session.update();
        assertTrue(session.exportWav("audio/out.wav", false), session.getLastError());
        SampleData wav = WavIO.read(Files.readAllBytes(root.resolve("audio/out.wav")));
        assertTrue(wav.getPeak() > 0.05f);
        assertEquals(88200, wav.getLength(), 22050);
    }

    @Test
    void shouldRefuseToExportOutsideTheProjectOrToANonWavName() {
        session.createSong("h");
        assertFalse(session.exportWav("../x.wav", false));
        assertFalse(session.exportWav("x.mp3", false));
        assertFalse(session.exportWav("sub/x.wav", true), "the playlist is empty");
        assertTrue(session.getLastError().contains("playlist is empty"), session.getLastError());
    }

    @Test
    void shouldExportWithAChannelsSampleFromTheProject() throws IOException {
        session.createSong("i");
        float[] tone = new float[4000];
        java.util.Arrays.fill(tone, 0.5f);
        Files.createDirectories(root.resolve("samples"));
        Files.write(root.resolve("samples/tone.wav"), WavIO.write(new SampleData(tone, 44100)));
        MusicDocument doc = session.getDocument();
        InstrumentSpec spec = InstrumentSpec.sample("samples/tone.wav");
        spec.oneShot = true;
        Channel sampler = doc.addChannel("Smp", spec);
        doc.setNotes(session.getSelectedPattern(), sampler, List.of(new Note(0, 96, 60, 1f)), "Draw");
        session.update();
        assertTrue(session.getPlayer().getErrors().isEmpty());
        assertTrue(session.exportWav("out.wav", false), session.getLastError());
    }

    @Test
    void shouldNotLetASongReadFilesOutsideTheProject() {
        session.createSong("j");
        Channel evil = session.getDocument().addChannel("Evil", InstrumentSpec.sample("../secret.wav"));
        session.update();
        assertTrue(session.getPlayer().getErrors().get(0).contains("outside the project"),
            session.getPlayer().getErrors().toString());
        assertNotNull(evil);
    }

    @Test
    void shouldFailClearlyWhenNoSongIsOpen() {
        assertThrows(IllegalStateException.class, () -> session.save());
        assertThrows(IllegalStateException.class, () -> session.setSongMode(true));
        assertNull(session.getSelectedPattern());
        assertTrue(session.close());
        assertTrue(session.saveIfDirty());
        session.update();
    }

    @Test
    void shouldSnapToSixteenthsByDefaultAndKeepAChangedSnap() {
        assertEquals(GridSnap.SIXTEENTH, session.getSnap());
        session.setSnap(GridSnap.BEAT);
        assertEquals(GridSnap.BEAT, session.getSnap());
        assertEquals(Song.TICKS_PER_BEAT, session.getSnap().getTicks());
    }
}
