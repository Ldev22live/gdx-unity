package io.github.ldev22live.gdxunity.editor.audio;

import io.github.ldev22live.gdxunity.engine.audio.SampleData;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sample song in the project assets must keep loading and sounding like music as the engine changes. */
class BundledDemoSongTest {
    private static final Path ASSETS = Path.of("..", "assets").toAbsolutePath().normalize();

    @Test
    void shouldOpenTheDemoSongWithItsRackPatternsAndPlaylist() {
        MusicSession session = new MusicSession(ASSETS);
        assertTrue(session.listSongs().contains("music/demo.song"), session.listSongs().toString());
        assertTrue(session.open("music/demo.song"), session.getLastError());
        var song = session.getDocument().getSong();
        assertEquals(5, song.getChannels().size());
        assertEquals(2, song.getPatterns().size());
        assertEquals(3, song.getClips().size());
        assertEquals(5 * 384, song.getPlaylistLengthTicks());
        assertTrue(session.getPlayer().getErrors().isEmpty(), session.getPlayer().getErrors().toString());
        session.close();
    }

    @Test
    void shouldRenderTheWholeDemoSongToAudibleStereoAudio() throws Exception {
        MusicSession session = new MusicSession(ASSETS);
        session.open("music/demo.song");
        var song = session.getDocument().getSong();
        SampleData audio = io.github.ldev22live.gdxunity.engine.audio.song.SongExporter.render(song,
            new FolderMusicAssets(ASSETS), io.github.ldev22live.gdxunity.engine.audio.song.Schedule.Mode.SONG, 0);
        // 5 bars at 110 bpm is about 10.9 seconds, then the tail of the lead's reverb and delay (the exporter keeps it)
        assertTrue(audio.getDurationSeconds() >= 10.8f && audio.getDurationSeconds() <= 10.9f + 3f,
            "duration " + audio.getDurationSeconds());
        assertTrue(audio.getPeak() > 0.2f && audio.getPeak() <= 1f, "peak " + audio.getPeak());
        session.close();
    }
}
