package io.github.ldev22live.gdxunity.editor.core.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompoundCommandTest {

    private static class TrackingCommand implements EditorCommand {
        private final String name;
        private final List<String> eventLog;
        private final boolean succeedOnExecute;
        private final boolean succeedOnUndo;

        TrackingCommand(String name, List<String> eventLog, boolean succeedOnExecute, boolean succeedOnUndo) {
            this.name = name;
            this.eventLog = eventLog;
            this.succeedOnExecute = succeedOnExecute;
            this.succeedOnUndo = succeedOnUndo;
        }

        @Override
        public boolean execute() throws Exception {
            eventLog.add("EXECUTE:" + name);
            if (!succeedOnExecute) {
                throw new RuntimeException("Simulated execute failure for " + name);
            }
            return true;
        }

        @Override
        public boolean undo() throws Exception {
            eventLog.add("UNDO:" + name);
            if (!succeedOnUndo) {
                throw new RuntimeException("Simulated undo failure for " + name);
            }
            return true;
        }

        @Override
        public String getDescription() {
            return name;
        }
    }

    @Test
    void shouldExecuteInForwardOrderAndUndoInReverseOrder() throws Exception {
        List<String> events = new ArrayList<>();
        CompoundCommand compound = new CompoundCommand("Test Forward and Reverse");

        compound.addCommand(new TrackingCommand("Cmd1", events, true, true));
        compound.addCommand(new TrackingCommand("Cmd2", events, true, true));
        compound.addCommand(new TrackingCommand("Cmd3", events, true, true));

        assertEquals(3, compound.getCommandCount());
        assertEquals("Test Forward and Reverse", compound.getDescription());

        // Forward execution
        boolean executed = compound.execute();
        assertTrue(executed);
        assertEquals(List.of("EXECUTE:Cmd1", "EXECUTE:Cmd2", "EXECUTE:Cmd3"), events);

        // Reverse undo
        events.clear();
        boolean undone = compound.undo();
        assertTrue(undone);
        assertEquals(List.of("UNDO:Cmd3", "UNDO:Cmd2", "UNDO:Cmd1"), events);

        // Redo (execute again in forward order)
        events.clear();
        boolean redone = compound.execute();
        assertTrue(redone);
        assertEquals(List.of("EXECUTE:Cmd1", "EXECUTE:Cmd2", "EXECUTE:Cmd3"), events);
    }

    @Test
    void shouldRollbackInReverseOrderWhenMidExecutionFails() {
        List<String> events = new ArrayList<>();
        CompoundCommand compound = new CompoundCommand("Failing Compound");

        compound.addCommand(new TrackingCommand("Cmd1", events, true, true));
        compound.addCommand(new TrackingCommand("Cmd2", events, true, true));
        // Cmd3 will fail on execute
        compound.addCommand(new TrackingCommand("Cmd3", events, false, true));
        compound.addCommand(new TrackingCommand("Cmd4", events, true, true));

        Exception exception = assertThrows(Exception.class, compound::execute);
        assertTrue(exception.getMessage().contains("Cmd3"));

        // Cmd1 and Cmd2 executed, Cmd3 failed, then Cmd2 and Cmd1 rolled back in reverse order!
        assertEquals(List.of("EXECUTE:Cmd1", "EXECUTE:Cmd2", "EXECUTE:Cmd3", "UNDO:Cmd2", "UNDO:Cmd1"), events);
    }

    @Test
    void shouldReportErrorWhenRollbackEncounteredFailure() {
        List<String> events = new ArrayList<>();
        CompoundCommand compound = new CompoundCommand("Failing Rollback Compound");

        // Cmd1 fails on undo
        compound.addCommand(new TrackingCommand("Cmd1", events, true, false));
        // Cmd2 succeeds on execute
        compound.addCommand(new TrackingCommand("Cmd2", events, true, true));
        // Cmd3 fails on execute
        compound.addCommand(new TrackingCommand("Cmd3", events, false, true));

        // Execution should throw, and exception message must clearly state ROLLBACK FAILED
        Exception exception = assertThrows(Exception.class, compound::execute);
        assertTrue(exception.getMessage().contains("ROLLBACK FAILED"), "Exception should distinctly report rollback failure");
        assertTrue(exception.getMessage().contains("Cmd3"), "Exception should report failing step");
        assertEquals(List.of("EXECUTE:Cmd1", "EXECUTE:Cmd2", "EXECUTE:Cmd3", "UNDO:Cmd2", "UNDO:Cmd1"), events);
    }

    @Test
    void shouldFailAtFirstChildWithoutExecutingSubsequentCommands() {
        List<String> events = new ArrayList<>();
        CompoundCommand compound = new CompoundCommand("First Failure Compound");

        compound.addCommand(new TrackingCommand("Cmd1", events, false, true));
        compound.addCommand(new TrackingCommand("Cmd2", events, true, true));
        compound.addCommand(new TrackingCommand("Cmd3", events, true, true));

        Exception exception = assertThrows(Exception.class, compound::execute);
        assertTrue(exception.getMessage().contains("Cmd1"));
        assertEquals(List.of("EXECUTE:Cmd1"), events);
    }

    @Test
    void shouldFailAtFinalChildAndRollbackAllPrecedingCommands() {
        List<String> events = new ArrayList<>();
        CompoundCommand compound = new CompoundCommand("Final Failure Compound");

        compound.addCommand(new TrackingCommand("Cmd1", events, true, true));
        compound.addCommand(new TrackingCommand("Cmd2", events, true, true));
        compound.addCommand(new TrackingCommand("Cmd3", events, false, true));

        Exception exception = assertThrows(Exception.class, compound::execute);
        assertTrue(exception.getMessage().contains("Cmd3"));
        assertEquals(List.of("EXECUTE:Cmd1", "EXECUTE:Cmd2", "EXECUTE:Cmd3", "UNDO:Cmd2", "UNDO:Cmd1"), events);
    }

    @Test
    void shouldHandleEmptyCompoundGracefully() throws Exception {
        CompoundCommand empty = new CompoundCommand("Empty");
        assertEquals(0, empty.getCommandCount());
        assertFalse(empty.execute());
        assertTrue(empty.undo());
    }
}
