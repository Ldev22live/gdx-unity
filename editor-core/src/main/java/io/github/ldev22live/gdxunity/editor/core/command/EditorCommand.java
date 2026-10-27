package io.github.ldev22live.gdxunity.editor.core.command;

/**
 * Fundamental contract for an undoable/redoable editor action.
 * Commands must not reference editor UI or platform-dependent views.
 */
public interface EditorCommand {
    /**
     * Executes the command.
     *
     * @return true if execution succeeded and state changed, false if execution failed or was a no-op.
     * @throws Exception if an unrecoverable error occurs during execution.
     */
    boolean execute() throws Exception;

    /**
     * Reverses the command and accurately restores the pre-execution state.
     *
     * @return true if undo succeeded, false otherwise.
     * @throws Exception if an unrecoverable error occurs during undo.
     */
    boolean undo() throws Exception;

    /**
     * Human-readable description of the operation for history UI and status displays.
     */
    String getDescription();
}
