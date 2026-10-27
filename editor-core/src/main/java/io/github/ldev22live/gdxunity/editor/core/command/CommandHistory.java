package io.github.ldev22live.gdxunity.editor.core.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the undo and redo history for editor commands with a bounded history limit.
 * Notifies registered listeners when command stacks change.
 */
public class CommandHistory {

    public interface Listener {
        void onHistoryChanged(CommandHistory history);
    }

    public static final int DEFAULT_MAX_HISTORY = 100;

    private final int maxHistory;
    private final Deque<EditorCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditorCommand> redoStack = new ArrayDeque<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private boolean isExecuting = false;

    public CommandHistory() {
        this(DEFAULT_MAX_HISTORY);
    }

    public CommandHistory(int maxHistory) {
        if (maxHistory <= 0) {
            throw new IllegalArgumentException("maxHistory must be positive: " + maxHistory);
        }
        this.maxHistory = maxHistory;
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyHistoryChanged() {
        for (Listener listener : listeners) {
            listener.onHistoryChanged(this);
        }
    }

    /**
     * Executes a command and, if successful, adds it to the undo stack and clears the redo stack.
     *
     * @param command the command to execute
     * @return true if executed successfully and recorded, false if rejected or failed
     */
    public synchronized boolean execute(EditorCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        if (isExecuting) {
            // Prevent recursive command recording during undo/redo
            return false;
        }

        isExecuting = true;
        try {
            boolean success = command.execute();
            if (success) {
                undoStack.push(command);
                if (undoStack.size() > maxHistory) {
                    // Evict oldest command from bottom of deque
                    undoStack.removeLast();
                }
                redoStack.clear();
                notifyHistoryChanged();
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            isExecuting = false;
        }
    }

    /**
     * Reverses the most recent command on the undo stack and moves it to the redo stack.
     *
     * @return true if a command was undone, false if undo stack was empty or undo failed
     */
    public synchronized boolean undo() {
        if (isExecuting || undoStack.isEmpty()) {
            return false;
        }

        EditorCommand command = undoStack.pop();
        isExecuting = true;
        try {
            boolean success = command.undo();
            if (success) {
                redoStack.push(command);
                notifyHistoryChanged();
                return true;
            } else {
                // If undo failed, do not add to redo stack
                notifyHistoryChanged();
                return false;
            }
        } catch (Exception e) {
            notifyHistoryChanged();
            return false;
        } finally {
            isExecuting = false;
        }
    }

    /**
     * Re-executes the most recently undone command from the redo stack and moves it back to undo stack.
     *
     * @return true if a command was redone, false if redo stack was empty or redo failed
     */
    public synchronized boolean redo() {
        if (isExecuting || redoStack.isEmpty()) {
            return false;
        }

        EditorCommand command = redoStack.pop();
        isExecuting = true;
        try {
            boolean success = command.execute();
            if (success) {
                undoStack.push(command);
                notifyHistoryChanged();
                return true;
            } else {
                notifyHistoryChanged();
                return false;
            }
        } catch (Exception e) {
            notifyHistoryChanged();
            return false;
        } finally {
            isExecuting = false;
        }
    }

    public synchronized boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public synchronized boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public synchronized String getUndoDescription() {
        return undoStack.isEmpty() ? null : undoStack.peek().getDescription();
    }

    public synchronized String getRedoDescription() {
        return redoStack.isEmpty() ? null : redoStack.peek().getDescription();
    }

    public synchronized int getUndoCount() {
        return undoStack.size();
    }

    public synchronized int getRedoCount() {
        return redoStack.size();
    }

    public synchronized int getMaxHistory() {
        return maxHistory;
    }

    /**
     * Clears all undo and redo history. Useful when scenes are replaced or reset.
     */
    public synchronized void clear() {
        undoStack.clear();
        redoStack.clear();
        notifyHistoryChanged();
    }
}
