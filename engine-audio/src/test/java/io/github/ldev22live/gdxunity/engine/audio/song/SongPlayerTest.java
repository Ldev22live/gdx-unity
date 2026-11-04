package io.github.ldev22live.gdxunity.engine.audio.song;

import io.github.ldev22live.gdxunity.engine.audio.SampleData;
import io.github.ldev22live.gdxunity.engine.audio.WavIO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static io.github.ldev22live.gdxunity.engine.audio.song.SongTestSupport.firstSound;
import static io.github.ldev22live.gdxunity.engine.audio.song.SongTestSupport.peak;
import static io.github.ldev22live.gdxunity.engine.audio.song.SongTestSupport.render;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongPlayerTest {
    /** Frames in one bar at 120 bpm. */
    private static final int BAR_FRAMES = 88200;

    private static SongPlayer playerFor(Song song) {
        return new SongPlayer(song, MusicAssets.NONE);
    }

    @Test
    void shouldStartANoteAtItsFrameNotBeforeAndNotLate() {
        // tick 96 is one beat in: 22050 frames at 120 bpm
        SongPlayer player = playerFor(SongTestSupport.songWithNote(96, 96));
        player.play();
        float[] data = render(player, 44100);
        int onset = firstSound(data, 1e-3f);
        assertTrue(onset >= 22050 && onset <= 22056, "onset at frame " + onset);
        assertEquals(0f, peak(data, 0, 22050));
    }

    @Test
    void shouldRenderTheSameAudioWhateverTheBlockSize() {
        Song song = SongTestSupport.songWithNote(100, 200);
        SongPlayer whole = playerFor(song);
        whole.play();
        float[] expected = render(whole, 60000);

        SongPlayer chunked = playerFor(SongTestSupport.songWithNote(100, 200));
        chunked.play();
        float[] actual = new float[60000];
        int done = 0;
        int[] sizes = {1, 7, 64, 333, 1024, 4096};
        int i = 0;
        while (done < actual.length) {
            int frames = Math.min(sizes[i++ % sizes.length], actual.length - done);
            float[] block = render(chunked, frames);
            System.arraycopy(block, 0, actual, done, frames);
            done += frames;
        }
        assertArrayEquals(expected, actual, 1e-6f);
    }

    @Test
    void shouldReleaseTheNoteWhenItsLengthEnds() {
        SongPlayer player = playerFor(SongTestSupport.songWithNote(0, 96));
        player.play();
        float[] data = render(player, 44100);
        assertTrue(peak(data, 5000, 21000) > 0.3f, "note should sound for its length");
        assertEquals(0f, peak(data, 24000, 44100), 1e-6f, "note should be over after one beat plus release");
    }

    @Test
    void shouldLoopThePatternAndPlayTheNoteAgain() {
        SongPlayer player = playerFor(SongTestSupport.songWithNote(0, 48));
        player.play();
        float[] data = render(player, BAR_FRAMES + 20000);
        assertEquals(0f, peak(data, BAR_FRAMES - 4000, BAR_FRAMES - 100), 1e-6f);
        int again = firstSound(java.util.Arrays.copyOfRange(data, BAR_FRAMES - 100, data.length), 1e-3f);
        assertTrue(again >= 98 && again <= 106, "second onset at +" + again);
    }

    @Test
    void shouldPlayTheNoteAtTheLoopStartOnEveryLoop() {
        // The loop point falls between two frames; a note exactly at the loop start must not be skipped by the carry-over
        Song song = SongTestSupport.songWithNote(0, 48);
        song.setBpm(121f);
        SongPlayer player = playerFor(song);
        player.play();
        double barFrames = 384 * 44100.0 * 60.0 / (121.0 * 96.0);
        int loops = 41;
        float[] data = render(player, (int) (barFrames * loops) + 3000);
        for (int k = 0; k < loops; k++) {
            int expected = (int) Math.round(barFrames * k);
            int from = Math.max(expected - 300, 0);
            int onset = -1;
            for (int i = from; i < expected + 300; i++) {
                if (Math.abs(data[i]) > 1e-3f) {
                    onset = i;
                    break;
                }
            }
            assertTrue(onset >= 0, "no note at the start of loop " + k);
            assertEquals(expected, onset, 6.0, "loop " + k);
        }
    }

    @Test
    void shouldNotDriftOverManyLoopsWhenABarIsAFractionOfAFrameLong() {
        // At 121 bpm a bar is 87477.69 frames; rounding the wrap point each loop would add up to a visible shift
        Song song = SongTestSupport.songWithNote(0, 48);
        song.setBpm(121f);
        SongPlayer player = playerFor(song);
        player.play();
        int loops = 40;
        double barFrames = 384 * 44100.0 * 60.0 / (121.0 * 96.0);
        float[] data = render(player, (int) (barFrames * loops) + 3000);
        int firstOnset = firstSound(data, 1e-3f);
        // The note of the last loop starts after the previous one has died away
        int searchFrom = (int) (barFrames * (loops - 1)) + 20000;
        int lastOnset = searchFrom + firstSound(java.util.Arrays.copyOfRange(data, searchFrom, data.length), 1e-3f);
        assertEquals(barFrames * loops, lastOnset - firstOnset, 1.0,
            "loop " + loops + " should start within a frame of the exact time");
    }

    @Test
    void shouldStopAtTheEndWhenNotLooping() {
        SongPlayer player = playerFor(SongTestSupport.songWithNote(0, 48));
        player.setLoop(false);
        player.play();
        float[] data = render(player, BAR_FRAMES + 20000);
        assertFalse(player.isPlaying());
        assertEquals(0f, peak(data, BAR_FRAMES, data.length), 1e-6f);
        assertEquals(0.0, player.getPositionTicks());
    }

    @Test
    void shouldFollowTheTempo() {
        Song slow = SongTestSupport.songWithNote(96, 48);
        slow.setBpm(60f);
        SongPlayer player = playerFor(slow);
        player.play();
        int onset = firstSound(render(player, 60000), 1e-3f);
        assertTrue(onset >= 44100 && onset <= 44106, "at 60 bpm a beat is 44100 frames, onset " + onset);
    }

    @Test
    void shouldTrackThePositionInTicks() {
        SongPlayer player = playerFor(SongTestSupport.songWithNote(0, 48));
        player.play();
        render(player, 22050);
        assertEquals(96.0, player.getPositionTicks(), 0.5);
    }

    @Test
    void shouldKeepThePositionOnPauseAndGoBackOnStop() {
        SongPlayer player = playerFor(SongTestSupport.songWithNote(0, 48));
        player.play();
        render(player, 22050);
        player.pause();
        double paused = player.getPositionTicks();
        render(player, 22050);
        assertEquals(paused, player.getPositionTicks(), 1e-9);
        assertFalse(player.isPlaying());
        player.stop();
        assertEquals(0.0, player.getPositionTicks());
    }

    @Test
    void shouldReleaseSoundingNotesWhenStopped() {
        SongPlayer player = playerFor(SongTestSupport.songWithNote(0, 380));
        player.play();
        render(player, 10000);
        player.stop();
        float[] data = render(player, 10000);
        assertEquals(0f, peak(data, 3000, 10000), 1e-6f);
    }

    @Test
    void shouldPlayFromASeekedPosition() {
        SongPlayer player = playerFor(SongTestSupport.songWithNote(192, 96));
        player.seek(150);
        player.play();
        float[] data = render(player, 30000);
        int onset = firstSound(data, 1e-3f);
        double expected = (192 - 150) * SongTestSupport.FRAMES_PER_TICK_120;
        assertEquals(expected, onset, 6.0);
    }

    @Test
    void shouldPickUpEditsAfterRefresh() {
        Song song = SongTestSupport.songWithNote(0, 48);
        SongPlayer player = playerFor(song);
        player.play();
        render(player, 1000);
        Channel channel = song.getChannels().get(0);
        song.getPatterns().get(0).setNotes(channel.getId(), List.of(new Note(192, 48, 69, 1f)));
        player.refresh();
        // The old note is released by the change and fades within its 20 ms release
        float[] fade = render(player, 2000);
        assertEquals(0f, peak(fade, 1500, 2000), 1e-6f, "the removed note is gone");
        float[] data = render(player, 60000);
        int onset = firstSound(data, 1e-3f);
        double expected = 192 * SongTestSupport.FRAMES_PER_TICK_120 - 3000;
        assertEquals(expected, onset, 6.0);
    }

    @Test
    void shouldNotPlayMutedChannelsOrChannelsOnMutedStrips() {
        Song song = SongTestSupport.songWithNote(0, 96);
        SongPlayer player = playerFor(song);
        song.getChannels().get(0).setMuted(true);
        player.play();
        assertEquals(0f, peak(render(player, 20000), 0, 20000));
        player.stop();

        song.getChannels().get(0).setMuted(false);
        song.getMixer().getStrip(0).setMuted(true);
        player.play();
        assertEquals(0f, peak(render(player, 20000), 0, 20000));
    }

    @Test
    void shouldRouteEachChannelThroughItsOwnMixerStrip() {
        Song song = SongTestSupport.songWithNote(0, 96);
        song.getChannels().get(0).setMixerIndex(3);
        song.getMixer().getStrip(3).setVolume(0.25f);
        SongPlayer loud = playerFor(SongTestSupport.songWithNote(0, 96));
        SongPlayer quiet = playerFor(song);
        loud.play();
        quiet.play();
        float ratio = peak(render(loud, 8000), 0, 8000) / peak(render(quiet, 8000), 0, 8000);
        assertEquals(4f, ratio, 0.15f);
    }

    @Test
    void shouldPlayThePlaylistInSongMode() {
        Song song = SongTestSupport.songWithNote(0, 48);
        song.setClips(List.of(new PlaylistClip(song.getPatterns().get(0).getId(), 0, Song.TICKS_PER_BAR, Song.TICKS_PER_BAR)));
        SongPlayer player = playerFor(song);
        player.setPlayback(Schedule.Mode.SONG, 0);
        player.setLoop(false);
        player.play();
        float[] data = render(player, 3 * BAR_FRAMES);
        assertEquals(0f, peak(data, 0, BAR_FRAMES - 10), "bar one of the playlist is empty");
        int onset = firstSound(data, 1e-3f);
        assertTrue(onset >= BAR_FRAMES && onset <= BAR_FRAMES + 6, "onset " + onset);
        assertFalse(player.isPlaying());
    }

    @Test
    void shouldPlayPreviewNotesWithoutTransport() {
        Song song = SongTestSupport.songWithNote(0, 48);
        SongPlayer player = playerFor(song);
        int channelId = song.getChannels().get(0).getId();
        assertEquals(0f, peak(render(player, 2000), 0, 2000));
        player.previewNoteOn(channelId, 69, 1f);
        assertTrue(peak(render(player, 4000), 0, 4000) > 0.3f);
        player.previewNoteOff(channelId, 69);
        assertEquals(0f, peak(render(player, 8000), 4000, 8000), 1e-6f);
        assertFalse(player.isPlaying());
    }

    @Test
    void shouldReportAMissingSampleFileAndKeepPlayingTheRest() {
        Song song = SongTestSupport.songWithNote(0, 96);
        song.addChannel("Broken", InstrumentSpec.sample("samples/missing.wav"), 1);
        SongPlayer player = playerFor(song);
        assertEquals(1, player.getErrors().size());
        assertTrue(player.getErrors().get(0).startsWith("Broken: "), player.getErrors().get(0));
        assertTrue(player.getErrors().get(0).contains("samples/missing.wav"));
        player.play();
        assertTrue(peak(render(player, 8000), 0, 8000) > 0.3f);
    }

    @Test
    void shouldLoadSamplesThroughTheAssetSourceOnce() throws IOException {
        float[] tone = new float[8000];
        java.util.Arrays.fill(tone, 0.5f);
        byte[] wav = WavIO.write(new SampleData(tone, 44100));
        int[] reads = {0};
        MusicAssets assets = path -> {
            reads[0]++;
            return wav;
        };
        Song song = new Song("s");
        InstrumentSpec spec = InstrumentSpec.sample("kick.wav");
        spec.oneShot = true;
        Channel channel = song.addChannel("Kick", spec, 0);
        song.addChannel("Kick 2", InstrumentSpec.sample("kick.wav"), 1);
        song.getMixer().getStrip(0).setVolume(1f);
        SongPlayer player = new SongPlayer(song, assets);
        assertEquals(1, reads[0], "the same file is read once");
        assertTrue(player.getErrors().isEmpty());
        player.previewNoteOn(channel.getId(), 60, 1f);
        assertTrue(peak(render(player, 500), 0, 500) > 0.2f);
    }

    @Test
    void shouldKeepTheSameInstrumentWhenOnlyAnotherChannelIsAdded() {
        Song song = SongTestSupport.songWithNote(0, 380);
        SongPlayer player = playerFor(song);
        player.play();
        render(player, 4000);
        song.addChannel("Another", InstrumentSpec.drums(), 2);
        player.refresh();
        // The lead note keeps sounding: its synth was not rebuilt
        assertTrue(peak(render(player, 2000), 0, 2000) > 0.3f);
    }

    @Test
    void shouldDoNothingForAnEmptySong() {
        Song song = new Song("empty");
        SongPlayer player = playerFor(song);
        player.play();
        assertEquals(0f, peak(render(player, 5000), 0, 5000));
    }
}
