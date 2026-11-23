package io.github.ldev22live.gdxunity.editor.audio;

import io.github.ldev22live.gdxunity.engine.audio.song.Note;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PianoRollOpsTest {
    private static final Note C4 = new Note(0, 96, 60, 1f);
    private static final Note E4 = new Note(96, 48, 64, 0.8f);
    private static final Note G4 = new Note(200, 24, 67, 0.6f);
    private static final List<Note> NOTES = List.of(C4, E4, G4);

    @Test
    void shouldFindTheNoteUnderAPoint() {
        assertEquals(C4, PianoRollOps.noteAt(NOTES, 50, 60));
        assertEquals(E4, PianoRollOps.noteAt(NOTES, 96, 64));
        assertNull(PianoRollOps.noteAt(NOTES, 144, 64), "the end tick is outside the note");
        assertNull(PianoRollOps.noteAt(NOTES, 50, 61), "wrong pitch");
    }

    @Test
    void shouldPickTheLaterNoteWhereTwoOverlap() {
        Note later = new Note(48, 96, 60, 1f);
        assertEquals(later, PianoRollOps.noteAt(List.of(C4, later), 60, 60));
    }

    @Test
    void shouldSelectNotesTouchingABox() {
        assertEquals(Set.of(C4, E4), PianoRollOps.notesIn(NOTES, 50, 150, 60, 64));
        assertEquals(Set.of(C4, E4), PianoRollOps.notesIn(NOTES, 150, 50, 64, 60), "corners in any order");
        assertEquals(Set.of(), PianoRollOps.notesIn(NOTES, 0, 90, 61, 63));
        assertEquals(Set.of(G4), PianoRollOps.notesIn(NOTES, 200, 201, 0, 127));
        assertEquals(Set.of(), PianoRollOps.notesIn(NOTES, 96, 200, 67, 67), "a note starting at the box end is outside");
    }

    @Test
    void shouldAddANoteAndSelectIt() {
        Note added = new Note(300, 24, 72, 1f);
        PianoRollOps.Result result = PianoRollOps.add(NOTES, added);
        assertEquals(4, result.notes().size());
        assertEquals(Set.of(added), result.selection());
    }

    @Test
    void shouldReplaceANoteWithTheSameStartAndPitchInsteadOfDoublingIt() {
        Note replacement = new Note(0, 24, 60, 0.5f);
        PianoRollOps.Result result = PianoRollOps.add(NOTES, replacement);
        assertEquals(3, result.notes().size());
        assertTrue(result.notes().contains(replacement));
        assertTrue(!result.notes().contains(C4));
    }

    @Test
    void shouldDeleteTheSelection() {
        assertEquals(List.of(E4, G4), PianoRollOps.delete(NOTES, Set.of(C4)));
        assertEquals(NOTES, PianoRollOps.delete(NOTES, Set.of()));
    }

    @Test
    void shouldMoveTheSelectionInTimeAndPitch() {
        PianoRollOps.Result result = PianoRollOps.move(NOTES, Set.of(E4, G4), 24, 2);
        assertTrue(result.notes().contains(C4));
        assertEquals(Set.of(new Note(120, 48, 66, 0.8f), new Note(224, 24, 69, 0.6f)), result.selection());
        assertEquals(3, result.notes().size());
    }

    @Test
    void shouldLimitAMoveSoTheWholeGroupStaysInRange() {
        PianoRollOps.Result left = PianoRollOps.move(NOTES, Set.of(E4, G4), -500, 0);
        // E4 starts at 96, so the group can only move 96 ticks left: the gap between the notes is kept
        assertEquals(Set.of(new Note(0, 48, 64, 0.8f), new Note(104, 24, 67, 0.6f)), left.selection());
        PianoRollOps.Result up = PianoRollOps.move(NOTES, Set.of(G4), 0, 100);
        assertEquals(127, up.selection().iterator().next().pitch());
        PianoRollOps.Result down = PianoRollOps.move(NOTES, Set.of(C4, E4), 0, -100);
        assertEquals(Set.of(new Note(0, 96, 0, 1f), new Note(96, 48, 4, 0.8f)), down.selection());
    }

    @Test
    void shouldReplaceUnselectedNotesAMovedNoteLandsOn() {
        PianoRollOps.Result result = PianoRollOps.move(NOTES, Set.of(C4), 96, 4);
        // C4 moved onto E4's place, so E4 is gone and only the moved C4 and G4 remain
        assertEquals(2, result.notes().size());
        assertTrue(!result.notes().contains(E4), "E4 was covered by the moved C4");
        assertTrue(result.notes().contains(G4));
    }

    @Test
    void shouldResizeOneNoteAndKeepAtLeastOneTick() {
        PianoRollOps.Result result = PianoRollOps.resize(NOTES, E4, 120);
        assertTrue(result.notes().contains(new Note(96, 120, 64, 0.8f)));
        assertEquals(1, PianoRollOps.resize(NOTES, E4, -5).selection().iterator().next().length());
    }

    @Test
    void shouldResizeEverySelectedNoteByTheSameAmount() {
        PianoRollOps.Result result = PianoRollOps.resizeAll(NOTES, Set.of(C4, G4), -30);
        assertTrue(result.notes().contains(new Note(0, 66, 60, 1f)));
        assertTrue(result.notes().contains(new Note(200, 1, 67, 0.6f)));
        assertTrue(result.notes().contains(E4));
    }

    @Test
    void shouldSetAndClampVelocity() {
        PianoRollOps.Result result = PianoRollOps.setVelocity(NOTES, Set.of(C4, E4), 5f);
        assertTrue(result.notes().contains(new Note(0, 96, 60, 1f)));
        assertTrue(result.notes().contains(G4));
        assertEquals(0f, PianoRollOps.setVelocity(NOTES, Set.of(G4), -3f).selection().iterator().next().velocity());
    }

    @Test
    void shouldQuantizeToTheGrid() {
        List<Note> loose = List.of(new Note(5, 24, 60, 1f), new Note(30, 24, 62, 1f), new Note(100, 24, 64, 1f));
        PianoRollOps.Result full = PianoRollOps.quantize(loose, Set.of(), GridSnap.SIXTEENTH, 1f);
        assertEquals(List.of(0, 24, 96), full.notes().stream().map(Note::tick).toList());
        PianoRollOps.Result half = PianoRollOps.quantize(loose, Set.of(), GridSnap.SIXTEENTH, 0.5f);
        assertEquals(List.of(3, 27, 98), half.notes().stream().map(Note::tick).toList());
    }

    @Test
    void shouldQuantizeOnlyTheSelectionWhenThereIsOne() {
        List<Note> loose = List.of(new Note(5, 24, 60, 1f), new Note(30, 24, 62, 1f));
        PianoRollOps.Result result = PianoRollOps.quantize(loose, Set.of(loose.get(1)), GridSnap.BEAT, 1f);
        assertEquals(5, result.notes().get(0).tick());
        assertEquals(0, result.notes().get(1).tick());
    }

    @Test
    void shouldDuplicateTheSelectionLater() {
        PianoRollOps.Result result = PianoRollOps.duplicate(NOTES, Set.of(C4), 384);
        assertEquals(4, result.notes().size());
        assertEquals(Set.of(new Note(384, 96, 60, 1f)), result.selection());
    }

    @Test
    void shouldNotChangeTheListItWasGiven() {
        PianoRollOps.move(NOTES, Set.of(C4), 10, 1);
        PianoRollOps.delete(NOTES, Set.of(C4));
        PianoRollOps.add(NOTES, new Note(1, 1, 1, 1f));
        assertEquals(List.of(C4, E4, G4), NOTES);
    }

    @Test
    void shouldSnapToTheNearestGridLine() {
        assertEquals(24, GridSnap.SIXTEENTH.snap(13));
        assertEquals(0, GridSnap.SIXTEENTH.snap(11));
        assertEquals(384, GridSnap.BAR.snap(200));
        assertEquals(0, GridSnap.BAR.snap(191));
        assertEquals(37, GridSnap.NONE.snap(37));
        assertEquals(32, GridSnap.EIGHTH_TRIPLET.snap(30));
        assertEquals(24, GridSnap.SIXTEENTH.floor(47));
        assertEquals(GridSnap.BAR, GridSnap.NONE.next());
        assertEquals(GridSnap.NONE, GridSnap.THIRTY_SECOND.next(), "the menu order wraps around");
    }
}
