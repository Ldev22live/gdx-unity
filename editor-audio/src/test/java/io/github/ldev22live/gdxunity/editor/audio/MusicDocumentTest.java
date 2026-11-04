package io.github.ldev22live.gdxunity.editor.audio;

import io.github.ldev22live.gdxunity.engine.audio.DelayEffect;
import io.github.ldev22live.gdxunity.engine.audio.Effect;
import io.github.ldev22live.gdxunity.engine.audio.SynthPatch;
import io.github.ldev22live.gdxunity.engine.audio.song.Channel;
import io.github.ldev22live.gdxunity.engine.audio.song.InstrumentSpec;
import io.github.ldev22live.gdxunity.engine.audio.song.Note;
import io.github.ldev22live.gdxunity.engine.audio.song.Pattern;
import io.github.ldev22live.gdxunity.engine.audio.song.PlaylistClip;
import io.github.ldev22live.gdxunity.engine.audio.song.Song;
import io.github.ldev22live.gdxunity.engine.audio.song.SongIO;
import io.github.ldev22live.gdxunity.engine.audio.song.StepGrid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicDocumentTest {
    private MusicDocument doc;
    private Song song;
    private Pattern pattern;
    private Channel kick;
    private Channel lead;

    @BeforeEach
    void setUp() {
        song = Song.createDefault("t");
        doc = new MusicDocument("music/t.song", song);
        pattern = song.getPatterns().get(0);
        kick = song.getChannels().get(0);
        lead = song.getChannels().get(3);
    }

    /** The whole song as text, so undo can be checked against the exact state before. */
    private String snapshot() {
        return SongIO.toJson(song);
    }

    @Test
    void shouldStartCleanAndBecomeDirtyOnTheFirstEdit() {
        assertFalse(doc.isDirty());
        doc.toggleStep(pattern, kick, 0, 1f);
        assertTrue(doc.isDirty());
        doc.markSaved();
        assertFalse(doc.isDirty());
    }

    @Test
    void shouldUndoAndRedoAStepToggle() {
        String before = snapshot();
        assertTrue(doc.toggleStep(pattern, kick, 4, 0.7f));
        assertTrue(StepGrid.isStepOn(pattern, kick, 4));
        String after = snapshot();
        assertTrue(doc.undo());
        assertEquals(before, snapshot());
        assertTrue(doc.redo());
        assertEquals(after, snapshot());
        assertEquals("Set step 5 of Kick", doc.getUndoDescription());
    }

    @Test
    void shouldMakeEachPianoRollEditOneUndoStep() {
        List<Note> notes = List.of(new Note(0, 96, 60, 1f), new Note(96, 96, 64, 1f));
        assertTrue(doc.setNotes(pattern, lead, notes, "Draw notes"));
        String drawn = snapshot();
        PianoRollOps.Result moved = PianoRollOps.move(pattern.getNotes(lead.getId()), java.util.Set.copyOf(notes), 24, 1);
        assertTrue(doc.setNotes(pattern, lead, moved.notes(), "Move notes"));
        assertTrue(doc.undo());
        assertEquals(drawn, snapshot());
        assertEquals("Draw notes", doc.getUndoDescription());
    }

    @Test
    void shouldReportNoChangeWhenNothingChanged() {
        assertFalse(doc.setNotes(pattern, lead, List.of(), "Nothing"));
        assertFalse(doc.canUndo());
        assertFalse(doc.setBpm(song.getBpm()));
        assertFalse(doc.canUndo());
    }

    @Test
    void shouldNotifyListenersOnEditsUndoAndRedo() {
        AtomicInteger calls = new AtomicInteger();
        MusicDocument.Listener listener = d -> calls.incrementAndGet();
        doc.addListener(listener);
        doc.toggleStep(pattern, kick, 0, 1f);
        doc.undo();
        doc.redo();
        assertEquals(3, calls.get());
        doc.removeListener(listener);
        doc.undo();
        assertEquals(3, calls.get());
    }

    @Test
    void shouldChangeTempoAndSwingWithUndo() {
        assertTrue(doc.setBpm(140f));
        assertTrue(doc.setSwing(0.4f));
        assertEquals(140f, song.getBpm());
        assertEquals(0.4f, song.getSwing());
        doc.undo();
        doc.undo();
        assertEquals(120f, song.getBpm());
        assertEquals(0f, song.getSwing());
        assertFalse(doc.setBpm(5f));
        assertFalse(doc.setSwing(2f));
        assertEquals(120f, song.getBpm());
    }

    @Test
    void shouldAddAChannelOnTheNextFreeStripAndUndoIt() {
        String before = snapshot();
        Channel added = doc.addChannel("Pad", InstrumentSpec.synth(SynthPatch.preset("Soft Pad")));
        assertNotNull(added);
        assertEquals(5, added.getMixerIndex(), "strips 0-4 are used by the default rack");
        assertEquals(6, song.getChannels().size());
        doc.undo();
        // Undo takes the channel out again; ids are never reused, so only the id counter differs from before
        assertEquals(before.replace("\"nextChannelId\":6", "\"nextChannelId\":7"), snapshot());
        doc.redo();
        assertSame(added, song.getChannels().get(5));
    }

    @Test
    void shouldRefuseAChannelBeyondTheLimit() {
        while (song.getChannels().size() < Song.MAX_CHANNELS) {
            assertNotNull(doc.addChannel("C", InstrumentSpec.drums()));
        }
        assertNull(doc.addChannel("one more", InstrumentSpec.drums()));
    }

    @Test
    void shouldRestoreARemovedChannelWithItsNotesInEveryPattern() {
        doc.toggleStep(pattern, kick, 0, 1f);
        Pattern second = doc.addPattern("Two", 2);
        doc.setNotes(second, kick, List.of(new Note(10, 20, 36, 0.5f)), "Draw");
        String before = snapshot();
        assertTrue(doc.removeChannel(kick));
        assertNull(song.getChannel(kick.getId()));
        assertTrue(pattern.getNotes(kick.getId()).isEmpty());
        assertTrue(second.getNotes(kick.getId()).isEmpty());
        doc.undo();
        assertEquals(before, snapshot());
        assertEquals(kick, song.getChannels().get(0), "back in its old place");
    }

    @Test
    void shouldSwapAnInstrumentAndBringTheOldOneBack() {
        InstrumentSpec old = lead.getInstrument();
        InstrumentSpec replacement = InstrumentSpec.drums();
        assertTrue(doc.setInstrument(lead, replacement));
        assertSame(replacement, lead.getInstrument());
        doc.undo();
        assertSame(old, lead.getInstrument());
    }

    @Test
    void shouldRenameMuteAndRouteChannels() {
        assertTrue(doc.renameChannel(lead, "Melody"));
        assertTrue(doc.setChannelMuted(lead, true));
        assertTrue(doc.setChannelMixerIndex(lead, 6));
        assertTrue(doc.setStepPitch(kick, 40));
        assertFalse(doc.setChannelMixerIndex(lead, 99));
        assertFalse(doc.setStepPitch(kick, 200));
        assertEquals("Melody", lead.getName());
        assertTrue(lead.isMuted());
        assertEquals(6, lead.getMixerIndex());
        for (int i = 0; i < 4; i++) {
            doc.undo();
        }
        assertEquals("Lead", lead.getName());
        assertFalse(lead.isMuted());
        assertEquals(3, lead.getMixerIndex());
        assertEquals(36, kick.getStepPitch());
    }

    @Test
    void shouldAddAndRemovePatternsButKeepTheLastOne() {
        Pattern second = doc.addPattern("Two", 2);
        assertEquals(2, song.getPatterns().size());
        assertNull(doc.addPattern("Zero", 0));
        song.setClips(List.of(new PlaylistClip(second.getId(), 0, 0, 768)));
        String before = snapshot();
        assertTrue(doc.removePattern(second));
        assertTrue(song.getClips().isEmpty(), "clips of a removed pattern go with it");
        doc.undo();
        assertEquals(before, snapshot());
        assertTrue(doc.removePattern(pattern));
        assertFalse(doc.removePattern(second), "the last pattern stays");
    }

    @Test
    void shouldShortenAPatternAndGetItsNotesBackOnUndo() {
        Pattern two = doc.addPattern("Two", 2);
        doc.setNotes(two, lead, List.of(new Note(0, 24, 60, 1f), new Note(500, 24, 62, 1f)), "Draw");
        String before = snapshot();
        assertTrue(doc.setPatternBars(two, 1));
        assertEquals(1, two.getNotes(lead.getId()).size());
        doc.undo();
        assertEquals(before, snapshot());
        assertFalse(doc.setPatternBars(two, 2), "same length is no change");
        assertFalse(doc.setPatternBars(two, 99));
    }

    @Test
    void shouldPlaceMoveResizeAndDeleteClips() {
        int id = pattern.getId();
        assertTrue(doc.placeClip(id, 0, 0, Song.TICKS_PER_BAR));
        assertFalse(doc.placeClip(id, 0, 100, Song.TICKS_PER_BAR), "overlaps the first clip");
        assertTrue(doc.placeClip(id, 1, 0, Song.TICKS_PER_BAR));
        assertTrue(doc.moveClip(1, Song.TICKS_PER_BAR, 0));
        assertFalse(doc.moveClip(1, 100, 0), "cannot move onto the first clip");
        assertTrue(doc.resizeClip(1, 2 * Song.TICKS_PER_BAR));
        assertEquals(2 * Song.TICKS_PER_BAR, song.getClips().get(1).lengthTicks());
        assertFalse(doc.resizeClip(0, 2 * Song.TICKS_PER_BAR), "growing would overlap the second clip");
        assertTrue(doc.deleteClip(0));
        assertEquals(1, song.getClips().size());
        assertFalse(doc.deleteClip(5));
        assertFalse(doc.placeClip(999, 0, 0, Song.TICKS_PER_BAR), "unknown pattern");
        assertFalse(doc.placeClip(id, 0, 0, 5), "shorter than a bar");
        doc.undo();
        assertEquals(2, song.getClips().size());
    }

    @Test
    void shouldRecordALiveFaderMoveAsOneUndoStep() {
        var strip = song.getMixer().getStrip(2);
        float before = strip.getVolume();
        strip.setVolume(0.2f);
        strip.setVolume(0.35f);
        assertTrue(doc.recordChange("Set fader", before, 0.35f, strip::setVolume));
        doc.undo();
        assertEquals(before, strip.getVolume());
        doc.redo();
        assertEquals(0.35f, strip.getVolume());
        assertFalse(doc.recordChange("Same", 0.35f, 0.35f, strip::setVolume));
    }

    @Test
    void shouldAddAndRemoveEffectsKeepingTheirOrder() {
        Effect delay = doc.addEffect(3, "delay");
        Effect reverb = doc.addEffect(3, "reverb");
        Effect filter = doc.addEffect(3, "filter");
        assertEquals(List.of(delay, reverb, filter), song.getMixer().getStrip(3).getEffects());
        assertTrue(doc.removeEffect(3, reverb));
        assertEquals(List.of(delay, filter), song.getMixer().getStrip(3).getEffects());
        doc.undo();
        assertEquals(List.of(delay, reverb, filter), song.getMixer().getStrip(3).getEffects());
        assertTrue(delay instanceof DelayEffect);
    }

    @Test
    void shouldAddEffectsToTheMasterAndStopAtTheLimit() {
        assertNotNull(doc.addEffect(-1, "reverb"));
        assertEquals(1, song.getMixer().getMaster().getEffects().size());
        for (int i = 0; i < 3; i++) {
            assertNotNull(doc.addEffect(0, "filter"));
        }
        assertNotNull(doc.addEffect(0, "filter"));
        assertNull(doc.addEffect(0, "filter"), "a strip holds four effects");
    }

    @Test
    void shouldKeepUndoAndRedoIndependentFromOtherDocuments() {
        MusicDocument other = new MusicDocument("music/o.song", Song.createDefault("o"));
        doc.toggleStep(pattern, kick, 0, 1f);
        assertFalse(other.canUndo());
        assertTrue(doc.canUndo());
    }
}
