package io.github.ldev22live.gdxunity.editor.core.command;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A change made live and recorded as one undo step (roadmap M3.6, TD-097): a Scatter stroke is recorded this way. */
class SceneSnapshotCommandTest {
    private EditorState state;
    private Scene scene;
    private Entity rock;

    @BeforeEach
    void setUp() {
        state = new EditorState();
        scene = new Scene("Level");
        state.setActiveScene(scene);
        rock = scene.createEntity("Rock");
    }

    @Test
    void shouldRecordALiveChangeAsOneUndoStepThatChangesNothingWhenFirstRun() {
        SceneSnapshotCommand.Recording recording = SceneSnapshotCommand.begin(state);
        Entity first = scene.createEntity("Pine 1");
        Entity second = scene.createEntity("Pine 2");
        scene.removeEntity(rock, true);

        assertTrue(state.executeCommand(recording.finish("Scatter Pine x 2")));

        assertEquals(2, scene.getAllEntities().size(), "executing the finished recording changed nothing");
        assertEquals(1, state.getCommandHistory().getUndoCount());
        assertEquals("Scatter Pine x 2", state.getCommandHistory().getUndoDescription());
        assertTrue(state.undo());
        assertEquals(1, scene.getAllEntities().size());
        assertSame(rock, scene.findEntityByName("Rock"), "the removed entity itself is back");
        assertNull(scene.findEntityByName("Pine 1"));
        assertTrue(state.redo());
        assertSame(first, scene.findEntityByName("Pine 1"));
        assertSame(second, scene.findEntityByName("Pine 2"));
        assertNull(scene.findEntityByName("Rock"));
    }

    @Test
    void shouldPutTheSceneBackWhenARecordingIsCancelled() {
        SceneSnapshotCommand.Recording recording = SceneSnapshotCommand.begin(state);
        scene.createEntity("Pine 1");

        recording.cancel();

        assertEquals(1, scene.getAllEntities().size());
        assertSame(rock, scene.getAllEntities().get(0));
        assertEquals(0, state.getCommandHistory().getUndoCount(), "nothing went on the undo stack");
    }
}
