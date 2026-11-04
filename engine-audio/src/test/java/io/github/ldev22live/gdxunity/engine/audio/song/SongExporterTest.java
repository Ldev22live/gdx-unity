package io.github.ldev22live.gdxunity.engine.audio.song;

import io.github.ldev22live.gdxunity.engine.audio.ReverbEffect;
import io.github.ldev22live.gdxunity.engine.audio.SampleData;
import io.github.ldev22live.gdxunity.engine.audio.WavIO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongExporterTest {
    private static final int BAR_FRAMES = 88200;

    private static SampleData exportPattern(Song song) throws IOException {
        return SongExporter.render(song, MusicAssets.NONE, Schedule.Mode.PATTERN, song.getPatterns().get(0).getId());
    }

    @Test
    void shouldRenderThePatternOnceWithoutLooping() throws IOException {
        SampleData audio = exportPattern(SongTestSupport.songWithNote(0, 96));
        assertEquals(BAR_FRAMES, audio.getLength(), 2000, "one bar at 120 bpm, trailing silence trimmed");
        assertTrue(audio.getPeak() > 0.3f);
        assertEquals(44100, audio.getSampleRate());
        assertTrue(!audio.isMono());
    }

    @Test
    void shouldRenderTheSameAudioEveryTime() throws IOException {
        SampleData a = exportPattern(Song.createDefault("s"));
        Song other = Song.createDefault("s");
        SampleData b = exportPattern(other);
        assertArrayEquals(a.getLeft(), b.getLeft());
    }

    @Test
    void shouldRenderTheDefaultSongsDrumPatternAudibly() throws IOException {
        Song song = Song.createDefault("beat");
        Pattern pattern = song.getPatterns().get(0);
        Channel kick = song.getChannels().get(0);
        Channel snare = song.getChannels().get(1);
        for (int step : new int[] {0, 4, 8, 12}) {
            pattern.setNotes(kick.getId(), StepGrid.toggled(pattern, kick, step, 1f));
        }
        pattern.setNotes(snare.getId(), StepGrid.toggled(pattern, snare, 4, 1f));
        SampleData audio = exportPattern(song);
        assertTrue(audio.getPeak() > 0.1f);
        assertTrue(audio.getPeak() <= 2f);
    }

    @Test
    void shouldAddTheEffectTailAfterTheMusicEnds() throws IOException {
        Song dry = SongTestSupport.songWithNote(Song.TICKS_PER_BAR - 96, 96);
        Song wet = SongTestSupport.songWithNote(Song.TICKS_PER_BAR - 96, 96);
        ReverbEffect reverb = new ReverbEffect(44100f);
        reverb.setParameter("mix", 1f);
        reverb.setParameter("room", 0.9f);
        wet.getMixer().getStrip(0).addEffect(reverb);
        assertTrue(exportPattern(wet).getLength() > exportPattern(dry).getLength() + 10000);
    }

    @Test
    void shouldExportThePlaylistLength() throws IOException {
        Song song = SongTestSupport.songWithNote(0, 96);
        song.setClips(List.of(new PlaylistClip(song.getPatterns().get(0).getId(), 0, 0, 3 * Song.TICKS_PER_BAR)));
        SampleData audio = SongExporter.render(song, MusicAssets.NONE, Schedule.Mode.SONG, 0);
        assertEquals(3 * BAR_FRAMES, audio.getLength(), 2000);
        assertEquals(3 * BAR_FRAMES, SongExporter.framesFor(song, Schedule.Mode.SONG, 0));
    }

    @Test
    void shouldProduceAWavThatReadsBack() throws IOException {
        SampleData audio = exportPattern(SongTestSupport.songWithNote(0, 96));
        SampleData back = WavIO.read(WavIO.write(audio));
        assertEquals(audio.getLength(), back.getLength());
        assertArrayEquals(audio.getLeft(), back.getLeft(), 1e-3f);
    }

    @Test
    void shouldRefuseToExportAnEmptyPlaylist() {
        Song song = SongTestSupport.songWithNote(0, 96);
        IOException e = assertThrows(IOException.class,
            () -> SongExporter.render(song, MusicAssets.NONE, Schedule.Mode.SONG, 0));
        assertTrue(e.getMessage().contains("playlist is empty"), e.getMessage());
    }

    @Test
    void shouldRefuseToExportWhenAFileIsMissing() {
        Song song = SongTestSupport.songWithNote(0, 96);
        song.addChannel("Broken", InstrumentSpec.sample("nope.wav"), 1);
        IOException e = assertThrows(IOException.class, () -> exportPattern(song));
        assertTrue(e.getMessage().contains("nope.wav"), e.getMessage());
    }
}
