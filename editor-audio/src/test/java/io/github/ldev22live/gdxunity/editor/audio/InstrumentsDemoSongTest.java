package io.github.ldev22live.gdxunity.editor.audio;

import io.github.ldev22live.gdxunity.engine.audio.FmPatch;
import io.github.ldev22live.gdxunity.engine.audio.PluckPatch;
import io.github.ldev22live.gdxunity.engine.audio.SampleData;
import io.github.ldev22live.gdxunity.engine.audio.SynthPatch;
import io.github.ldev22live.gdxunity.engine.audio.song.Channel;
import io.github.ldev22live.gdxunity.engine.audio.song.InstrumentSpec;
import io.github.ldev22live.gdxunity.engine.audio.song.Note;
import io.github.ldev22live.gdxunity.engine.audio.song.Pattern;
import io.github.ldev22live.gdxunity.engine.audio.song.Schedule;
import io.github.ldev22live.gdxunity.engine.audio.song.Song;
import io.github.ldev22live.gdxunity.engine.audio.song.SongExporter;
import io.github.ldev22live.gdxunity.engine.audio.song.SongIO;
import io.github.ldev22live.gdxunity.engine.audio.song.StepGrid;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sample song {@code music/instruments-demo.song} (TD-076) has one channel of each instrument kind the Music Studio offers, so a
 * person or a screenshot can open it and see every instrument panel. The file is made by this test (record it with
 * {@code GDXUNITY_RECORD_FIXTURES=1}); otherwise the test requires the file to be exactly what the code makes, and every channel to sound.
 */
class InstrumentsDemoSongTest {
    private static final Path ASSETS = Path.of("..", "assets").toAbsolutePath().normalize();
    private static final Path FILE = ASSETS.resolve("music").resolve("instruments-demo.song");
    private static final int TICKS_PER_BEAT = Song.TICKS_PER_BEAT;

    static Song build() {
        Song song = Song.createDefault("Instruments Demo");
        song.setBpm(100f);
        Pattern pattern = song.getPatterns().get(0);
        Channel kick = song.getChannels().get(0);
        Channel lead = song.getChannels().get(3);
        lead.getInstrument().patch.copyFrom(SynthPatch.preset("Electric Piano"));
        pattern.setNotes(kick.getId(), StepGrid.toggled(pattern, kick, 0, 0.9f));
        pattern.setNotes(lead.getId(), List.of(new Note(0, TICKS_PER_BEAT, 60, 0.8f), new Note(TICKS_PER_BEAT, TICKS_PER_BEAT, 64, 0.8f),
            new Note(2 * TICKS_PER_BEAT, 2 * TICKS_PER_BEAT, 67, 0.8f)));
        Channel bell = song.addChannel("Bell", InstrumentSpec.fm(FmPatch.preset("Bell")), 5);
        Channel harp = song.addChannel("Harp", InstrumentSpec.pluck(PluckPatch.preset("Harp")), 6);
        pattern.setNotes(bell.getId(), List.of(new Note(0, 2 * TICKS_PER_BEAT, 79, 0.8f), new Note(2 * TICKS_PER_BEAT, 2 * TICKS_PER_BEAT, 76, 0.8f)));
        pattern.setNotes(harp.getId(), List.of(new Note(0, TICKS_PER_BEAT, 48, 0.9f), new Note(TICKS_PER_BEAT, TICKS_PER_BEAT, 52, 0.9f),
            new Note(2 * TICKS_PER_BEAT, TICKS_PER_BEAT, 55, 0.9f), new Note(3 * TICKS_PER_BEAT, TICKS_PER_BEAT, 60, 0.9f)));
        return song;
    }

    @Test
    void shouldMatchTheBundledFileAndSoundOnEveryChannel() throws IOException {
        String expected = SongIO.toJson(build());
        if ("1".equals(System.getenv("GDXUNITY_RECORD_FIXTURES"))) {
            Files.writeString(FILE, expected, StandardCharsets.UTF_8);
        }
        assertEquals(expected, Files.readString(FILE, StandardCharsets.UTF_8), "run with GDXUNITY_RECORD_FIXTURES=1 to rewrite " + FILE);

        MusicSession session = new MusicSession(ASSETS);
        assertTrue(session.open("music/instruments-demo.song"), session.getLastError());
        Song song = session.getDocument().getSong();
        assertEquals(List.of(InstrumentSpec.Type.DRUMS, InstrumentSpec.Type.DRUMS, InstrumentSpec.Type.DRUMS, InstrumentSpec.Type.SYNTH,
            InstrumentSpec.Type.SYNTH, InstrumentSpec.Type.FM, InstrumentSpec.Type.PLUCK),
            song.getChannels().stream().map(c -> c.getInstrument().type).toList());
        SampleData audio = SongExporter.render(song, new FolderMusicAssets(ASSETS), Schedule.Mode.PATTERN, song.getPatterns().get(0).getId());
        float peak = 0f;
        for (float v : audio.getLeft()) {
            assertTrue(Float.isFinite(v));
            peak = Math.max(peak, Math.abs(v));
        }
        assertTrue(peak > 0.1f, "the demo song is nearly silent: " + peak);
    }
}
