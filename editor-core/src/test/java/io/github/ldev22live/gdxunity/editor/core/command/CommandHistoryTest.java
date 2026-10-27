package io.github.ldev22live.gdxunity.editor.core.command;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CommandHistoryTest {

    static class DummyCommand implements EditorCommand {
        private final String description;
        private final Runnable onExecute;
        private final Runnable onUndo;

        public DummyCommand(String description, Runnable onExecute, Runnable onUndo) {
            this.description = description;
            this.onExecute = onExecute;
            this.onUndo = onUndo;
        }

        @Override
        public boolean execute() {
            if (onExecute != null) onExecute.run();
            return true;
        }

        @Override
        public boolean undo() {
            if (onUndo != null) onUndo.run();
            return true;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    @Test
    void testExecuteUndoRedo() {
        CommandHistory history = new CommandHistory(10);
        AtomicInteger value = new AtomicInteger(0);

        DummyCommand cmd = new DummyCommand("Add 5", () -> value.addAndGet(5), () -> value.addAndGet(-5));

        assertFalse(history.canUndo());
        assertFalse(history.canRedo());

        assertTrue(history.execute(cmd));
        assertEquals(5, value.get());
        assertTrue(history.canUndo());
        assertFalse(history.canRedo());
        assertEquals("Add 5", history.getUndoDescription());

        assertTrue(history.undo());
        assertEquals(0, value.get());
        assertFalse(history.canUndo());
        assertTrue(history.canRedo());
        assertEquals("Add 5", history.getRedoDescription());

        assertTrue(history.redo());
        assertEquals(5, value.get());
        assertTrue(history.canUndo());
        assertFalse(history.canRedo());
    }

    @Test
    void testRedoInvalidationOnNewEdit() {
        CommandHistory history = new CommandHistory(10);
        AtomicInteger value = new AtomicInteger(0);

        history.execute(new DummyCommand("Add 1", () -> value.addAndGet(1), () -> value.addAndGet(-1)));
        history.execute(new DummyCommand("Add 2", () -> value.addAndGet(2), () -> value.addAndGet(-2)));
        assertEquals(3, value.get());

        history.undo();
        assertEquals(1, value.get());
        assertTrue(history.canRedo());

        // Perform a new edit while redo history exists -> redo history must be cleared
        history.execute(new DummyCommand("Add 10", () -> value.addAndGet(10), () -> value.addAndGet(-10)));
        assertEquals(11, value.get());
        assertFalse(history.canRedo());
        assertEquals(0, history.getRedoCount());
        assertEquals(2, history.getUndoCount());
    }

    @Test
    void testBoundedHistoryLimit() {
        CommandHistory history = new CommandHistory(3);
        for (int i = 1; i <= 5; i++) {
            final int step = i;
            history.execute(new DummyCommand("Step " + step, null, null));
        }

        assertEquals(3, history.getUndoCount());
        assertEquals("Step 5", history.getUndoDescription());
    }

    @Test
    void testEmptyUndoAndRedo() {
        CommandHistory history = new CommandHistory(5);
        assertFalse(history.undo());
        assertFalse(history.redo());
        assertNull(history.getUndoDescription());
        assertNull(history.getRedoDescription());
    }

    @Test
    void testFailedCommandNotAdded() {
        CommandHistory history = new CommandHistory(5);
        EditorCommand failingCmd = new EditorCommand() {
            @Override
            public boolean execute() {
                return false; // failed or no-op
            }

            @Override
            public boolean undo() {
                return true;
            }

            @Override
            public String getDescription() {
                return "Fail";
            }
        };

        assertFalse(history.execute(failingCmd));
        assertEquals(0, history.getUndoCount());
        assertFalse(history.canUndo());
    }

    @Test
    void testClearHistory() {
        CommandHistory history = new CommandHistory(5);
        history.execute(new DummyCommand("Cmd1", null, null));
        history.execute(new DummyCommand("Cmd2", null, null));
        assertEquals(2, history.getUndoCount());

        history.clear();
        assertEquals(0, history.getUndoCount());
        assertEquals(0, history.getRedoCount());
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }
}
