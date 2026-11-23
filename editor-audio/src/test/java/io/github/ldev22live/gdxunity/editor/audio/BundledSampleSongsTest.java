package io.github.ldev22live.gdxunity.editor.audio;

import io.github.ldev22live.gdxunity.engine.audio.SampleData;
import io.github.ldev22live.gdxunity.engine.audio.song.Schedule;
import io.github.ldev22live.gdxunity.engine.audio.song.Song;
import io.github.ldev22live.gdxunity.engine.audio.song.SongExporter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The five sample songs in the project assets ({@code assets/music}, shared with the web editor) must open without
 * errors, use only instruments both editors can play, and render to audible music of the length their playlist says.
 */
class BundledSampleSongsTest {
    private static final Path ASSETS = Path.of("..", "assets").toAbsolutePath().normalize();
    /** The songs added with the Forgeroom rename, with their tempo and the length of their playlist in bars. */
    private static final String[][] SONGS = {
        {"music/lofi-evening.song", "Lo-fi Evening", "82", "20"},
        {"music/neon-drive.song", "Neon Drive", "128", "20"},
        {"music/pixel-quest.song", "Pixel Quest", "144", "20"},
        {"music/ambient-drift.song", "Ambient Drift", "68", "24"},
        {"music/funk-machine.song", "Funk Machine", "104", "14"},
    };
    /** The longest tail the exporter keeps after the music ends, plus a little. */
    private static final float MAX_TAIL_SECONDS = 9f;

    @Test
    void shouldListAndOpenEverySampleSongWithoutErrors() {
        MusicSession session = new MusicSession(ASSETS);
        for (String[] entry : SONGS) {
            assertTrue(session.listSongs().contains(entry[0]), entry[0] + " is not in " + session.listSongs());
            assertTrue(session.open(entry[0]), entry[0] + ": " + session.getLastError());
            Song song = session.getDocument().getSong();
            assertEquals(entry[1], song.getName());
            assertEquals(Float.parseFloat(entry[2]), song.getBpm(), 0.001f);
            assertEquals(Integer.parseInt(entry[3]) * Song.TICKS_PER_BAR, song.getPlaylistLengthTicks(), entry[0]);
            assertTrue(session.getPlayer().getErrors().isEmpty(), entry[0] + ": " + session.getPlayer().getErrors());
        }
        session.close();
    }

    @Test
    void shouldRenderEverySampleSongToAudibleMusicOfTheRightLength() throws Exception {
        MusicSession session = new MusicSession(ASSETS);
        for (String[] entry : SONGS) {
            session.open(entry[0]);
            Song song = session.getDocument().getSong();
            SampleData audio = SongExporter.render(song, new FolderMusicAssets(ASSETS), Schedule.Mode.SONG, 0);
            float musicSeconds = Integer.parseInt(entry[3]) * 4 * 60f / song.getBpm();
            assertTrue(audio.getDurationSeconds() >= musicSeconds * 0.99f
                && audio.getDurationSeconds() <= musicSeconds + MAX_TAIL_SECONDS,
                entry[0] + ": " + audio.getDurationSeconds() + " s for " + musicSeconds + " s of music");
            assertTrue(audio.getPeak() > 0.2f && audio.getPeak() <= 1f, entry[0] + " peak " + audio.getPeak());
        }
        session.close();
    }
}
