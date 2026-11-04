package io.github.ldev22live.gdxunity.engine.audio.song;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepGridTest {
    private final Song song = Song.createDefault("s");
    private final Pattern pattern = song.getPatterns().get(0);
    private final Channel kick = song.getChannels().get(0);

    @Test
    void shouldCountSixteenStepsPerBar() {
        assertEquals(16, StepGrid.stepCount(pattern));
        song.addPattern("Two bars", 2);
        assertEquals(32, StepGrid.stepCount(song.getPatterns().get(1)));
    }

    @Test
    void shouldSwitchAStepOnAtTheRightTickWithTheChannelsPitch() {
        pattern.setNotes(kick.getId(), StepGrid.toggled(pattern, kick, 4, 0.8f));
        assertTrue(StepGrid.isStepOn(pattern, kick, 4));
        assertFalse(StepGrid.isStepOn(pattern, kick, 5));
        Note note = pattern.getNotes(kick.getId()).get(0);
        assertEquals(4 * Song.STEP_TICKS, note.tick());
        assertEquals(Song.STEP_TICKS, note.length());
        assertEquals(36, note.pitch());
        assertEquals(0.8f, note.velocity());
    }

    @Test
    void shouldSwitchAStepOffAgain() {
        pattern.setNotes(kick.getId(), StepGrid.toggled(pattern, kick, 0, 1f));
        pattern.setNotes(kick.getId(), StepGrid.toggled(pattern, kick, 0, 1f));
        assertFalse(StepGrid.isStepOn(pattern, kick, 0));
        assertTrue(pattern.getNotes(kick.getId()).isEmpty());
    }

    @Test
    void shouldKeepOtherStepsAndOtherChannelsWhenToggling() {
        Channel snare = song.getChannels().get(1);
        pattern.setNotes(kick.getId(), StepGrid.toggled(pattern, kick, 0, 1f));
        pattern.setNotes(snare.getId(), StepGrid.toggled(pattern, snare, 4, 1f));
        pattern.setNotes(kick.getId(), StepGrid.toggled(pattern, kick, 8, 1f));
        assertTrue(StepGrid.isStepOn(pattern, kick, 0));
        assertTrue(StepGrid.isStepOn(pattern, kick, 8));
        assertTrue(StepGrid.isStepOn(pattern, snare, 4));
        assertFalse(StepGrid.isStepOn(pattern, snare, 0));
    }

    @Test
    void shouldIgnorePianoRollNotesOfAnotherPitchOrOffTheGrid() {
        pattern.setNotes(kick.getId(), List.of(new Note(0, 24, 40, 1f), new Note(30, 24, 36, 1f)));
        assertFalse(StepGrid.isStepOn(pattern, kick, 0), "a different pitch is not this row's step");
        assertFalse(StepGrid.isStepOn(pattern, kick, 1), "a note off the grid does not light a step");
        assertNull(StepGrid.noteAt(pattern, kick, 0));
    }

    @Test
    void shouldChangeTheVelocityOfAStepAndLeaveOffStepsAlone() {
        pattern.setNotes(kick.getId(), StepGrid.toggled(pattern, kick, 2, 1f));
        pattern.setNotes(kick.getId(), StepGrid.withVelocity(pattern, kick, 2, 0.3f));
        assertEquals(0.3f, StepGrid.noteAt(pattern, kick, 2).velocity());
        assertEquals(1, StepGrid.withVelocity(pattern, kick, 3, 0.3f).size());
    }

    @Test
    void shouldRejectStepsOutsideThePattern() {
        assertThrows(IllegalArgumentException.class, () -> StepGrid.toggled(pattern, kick, 16, 1f));
        assertThrows(IllegalArgumentException.class, () -> StepGrid.toggled(pattern, kick, -1, 1f));
    }
}
