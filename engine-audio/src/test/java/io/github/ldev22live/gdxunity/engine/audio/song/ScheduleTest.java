package io.github.ldev22live.gdxunity.engine.audio.song;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleTest {
    private static Schedule pattern(Song song) {
        return Schedule.compile(song, Schedule.Mode.PATTERN, song.getPatterns().get(0).getId());
    }

    @Test
    void shouldTurnEachNoteIntoAnOnAndAnOffEventInTimeOrder() {
        Song song = SongTestSupport.songWithNote(96, 48);
        List<Schedule.Event> events = pattern(song).getEvents();
        assertEquals(2, events.size());
        assertEquals(96, events.get(0).tick());
        assertTrue(events.get(0).on());
        assertEquals(69, events.get(0).pitch());
        assertEquals(144, events.get(1).tick());
        assertFalse(events.get(1).on());
        assertEquals(Song.TICKS_PER_BAR, pattern(song).getLengthTicks());
    }

    @Test
    void shouldPlayAnOffBeforeAnOnAtTheSameTick() {
        Song song = new Song("s");
        Channel c = song.addChannel("A", InstrumentSpec.synth(SongTestSupport.cleanSine()), 0);
        Pattern p = song.addPattern("P", 1);
        p.setNotes(c.getId(), List.of(new Note(0, 96, 60, 1f), new Note(96, 96, 60, 1f)));
        List<Schedule.Event> events = pattern(song).getEvents();
        assertEquals(96, events.get(1).tick());
        assertFalse(events.get(1).on(), "off first");
        assertTrue(events.get(2).on());
    }

    @Test
    void shouldCutNotesAtTheEndOfThePattern() {
        Song song = SongTestSupport.songWithNote(Song.TICKS_PER_BAR - 24, 96);
        Schedule schedule = pattern(song);
        assertEquals(2, schedule.getEvents().size());
        assertEquals(Song.TICKS_PER_BAR, schedule.getEvents().get(1).tick(), "the off never lands after the loop point");
    }

    @Test
    void shouldSkipMutedChannels() {
        Song song = SongTestSupport.songWithNote(0, 48);
        song.getChannels().get(0).setMuted(true);
        assertTrue(pattern(song).getEvents().isEmpty());
    }

    @Test
    void shouldDelayOnlyTheOffBeatSixteenthsBySwing() {
        Song song = new Song("s");
        Channel c = song.addChannel("A", InstrumentSpec.synth(SongTestSupport.cleanSine()), 0);
        Pattern p = song.addPattern("P", 1);
        p.setNotes(c.getId(), List.of(new Note(0, 12, 60, 1f), new Note(Song.STEP_TICKS, 12, 60, 1f),
            new Note(2 * Song.STEP_TICKS, 12, 60, 1f), new Note(3 * Song.STEP_TICKS, 12, 60, 1f)));
        song.setSwing(1f);
        List<Integer> onTicks = pattern(song).getEvents().stream().filter(Schedule.Event::on).map(Schedule.Event::tick).toList();
        // Full swing moves the odd steps a third of a step (8 ticks) later
        assertEquals(List.of(0, 32, 48, 80), onTicks);
        song.setSwing(0f);
        onTicks = pattern(song).getEvents().stream().filter(Schedule.Event::on).map(Schedule.Event::tick).toList();
        assertEquals(List.of(0, 24, 48, 72), onTicks);
    }

    @Test
    void shouldDropANoteSwungPastThePatternEnd() {
        Song song = new Song("s");
        Channel c = song.addChannel("A", InstrumentSpec.synth(SongTestSupport.cleanSine()), 0);
        Pattern p = song.addPattern("P", 1);
        int lastStep = (Song.STEPS_PER_BAR - 1) * Song.STEP_TICKS;
        p.setNotes(c.getId(), List.of(new Note(lastStep + 20, 4, 60, 1f)));
        song.setSwing(1f);
        assertTrue(pattern(song).getEvents().isEmpty());
    }

    @Test
    void shouldRepeatAPatternAcrossALongerClipAndCutTheLastRepeat() {
        Song song = SongTestSupport.songWithNote(0, 48);
        Pattern p = song.getPatterns().get(0);
        song.setClips(List.of(new PlaylistClip(p.getId(), 0, Song.TICKS_PER_BAR, Song.TICKS_PER_BAR * 2 + 96)));
        Schedule schedule = Schedule.compile(song, Schedule.Mode.SONG, 0);
        List<Integer> onTicks = schedule.getEvents().stream().filter(Schedule.Event::on).map(Schedule.Event::tick).toList();
        assertEquals(List.of(384, 768, 1152), onTicks);
        assertEquals(Song.TICKS_PER_BAR * 3 + 96, schedule.getLengthTicks());
    }

    @Test
    void shouldCutNotesAtTheEndOfAClipShorterThanItsPattern() {
        Song song = new Song("s");
        Channel c = song.addChannel("A", InstrumentSpec.synth(SongTestSupport.cleanSine()), 0);
        Pattern p = song.addPattern("P", 2);
        p.setNotes(c.getId(), List.of(new Note(Song.TICKS_PER_BAR - 24, 96, 60, 1f), new Note(Song.TICKS_PER_BAR + 24, 24, 62, 1f)));
        song.setClips(List.of(new PlaylistClip(p.getId(), 0, 0, Song.TICKS_PER_BAR)));
        Schedule schedule = Schedule.compile(song, Schedule.Mode.SONG, 0);
        assertEquals(2, schedule.getEvents().size(), "the note in bar two is outside the clip");
        assertEquals(Song.TICKS_PER_BAR, schedule.getEvents().get(1).tick());
    }

    @Test
    void shouldMixSeveralTracksIntoOneSchedule() {
        Song song = SongTestSupport.songWithNote(0, 48);
        Pattern p = song.getPatterns().get(0);
        song.setClips(List.of(new PlaylistClip(p.getId(), 0, 0, Song.TICKS_PER_BAR),
            new PlaylistClip(p.getId(), 1, Song.TICKS_PER_BAR, Song.TICKS_PER_BAR)));
        Schedule schedule = Schedule.compile(song, Schedule.Mode.SONG, 0);
        assertEquals(4, schedule.getEvents().size());
        assertEquals(2 * Song.TICKS_PER_BAR, schedule.getLengthTicks());
    }

    @Test
    void shouldCompileAnEmptyPlaylistToNothing() {
        Song song = SongTestSupport.songWithNote(0, 48);
        Schedule schedule = Schedule.compile(song, Schedule.Mode.SONG, 0);
        assertTrue(schedule.getEvents().isEmpty());
        assertEquals(0, schedule.getLengthTicks());
    }

    @Test
    void shouldRejectAnUnknownPattern() {
        Song song = new Song("s");
        assertThrows(IllegalArgumentException.class, () -> Schedule.compile(song, Schedule.Mode.PATTERN, 5));
    }
}
