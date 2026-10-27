package io.github.ldev22live.gdxunity.editor.core.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An atomic composite command that executes a sequence of EditorCommands in forward order
 * and reverses them in reverse order during undo.
 * If any command fails during execution, all previously executed commands are rolled back in reverse order.
 */
public class CompoundCommand implements EditorCommand {
    private final String description;
    private final List<EditorCommand> commands = new ArrayList<>();
    private final List<EditorCommand> executedCommands = new ArrayList<>();

    public CompoundCommand(String description) {
        this.description = (description != null) ? description : "Compound Action";
    }

    public CompoundCommand(String description, List<EditorCommand> commands) {
        this.description = (description != null) ? description : "Compound Action";
        if (commands != null) {
            for (EditorCommand cmd : commands) {
                if (cmd != null) {
                    this.commands.add(cmd);
                }
            }
        }
    }

    public void addCommand(EditorCommand command) {
        if (command != null) {
            commands.add(command);
            executedCommands.add(command);
        }
    }

    public List<EditorCommand> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    public int getCommandCount() {
        return commands.size();
    }

    @Override
    public boolean execute() throws Exception {
        if (commands.isEmpty()) {
            return false;
        }

        executedCommands.clear();
        for (int i = 0; i < commands.size(); i++) {
            EditorCommand cmd = commands.get(i);
            try {
                boolean success = cmd.execute();
                if (!success) {
                    List<Exception> rollbackExceptions = rollbackExecuted();
                    if (!rollbackExceptions.isEmpty()) {
                        throw new Exception("CompoundCommand execution failed at step " + i + " ('" + cmd.getDescription() + "') and rollback encountered " + rollbackExceptions.size() + " exception(s). First rollback error: " + rollbackExceptions.get(0).getMessage());
                    }
                    return false;
                }
                executedCommands.add(cmd);
            } catch (Exception e) {
                List<Exception> rollbackExceptions = rollbackExecuted();
                if (!rollbackExceptions.isEmpty()) {
                    throw new Exception("CompoundCommand execution failed at step " + i + " ('" + cmd.getDescription() + "'): " + e.getMessage() + " | ROLLBACK FAILED with " + rollbackExceptions.size() + " exception(s). First rollback error: " + rollbackExceptions.get(0).getMessage(), e);
                }
                throw new Exception("CompoundCommand execution failed at step " + i + " ('" + cmd.getDescription() + "'): " + e.getMessage(), e);
            }
        }
        return true;
    }

    @Override
    public boolean undo() throws Exception {
        List<Exception> undoExceptions = new ArrayList<>();
        boolean allUndone = true;

        // Undo in reverse order of execution
        for (int i = executedCommands.size() - 1; i >= 0; i--) {
            EditorCommand cmd = executedCommands.get(i);
            try {
                boolean undone = cmd.undo();
                if (!undone) {
                    allUndone = false;
                }
            } catch (Exception e) {
                undoExceptions.add(e);
                allUndone = false;
            }
        }

        if (!undoExceptions.isEmpty()) {
            throw new Exception("CompoundCommand undo encountered " + undoExceptions.size() + " failure(s). First failure: " + undoExceptions.get(0).getMessage(), undoExceptions.get(0));
        }

        return allUndone;
    }

    private List<Exception> rollbackExecuted() {
        List<Exception> exceptions = new ArrayList<>();
        for (int i = executedCommands.size() - 1; i >= 0; i--) {
            try {
                executedCommands.get(i).undo();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        executedCommands.clear();
        return exceptions;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
