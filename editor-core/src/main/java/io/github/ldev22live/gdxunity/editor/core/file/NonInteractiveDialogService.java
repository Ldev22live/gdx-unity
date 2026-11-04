package io.github.ldev22live.gdxunity.editor.core.file;

import java.io.File;
import java.util.Optional;

/**
 * Dialogs for unattended runs such as {@code --smoke-test}: nothing waits for a person. File choices are cancelled,
 * unsaved changes are discarded, and errors are not shown because {@link SceneFileController} already reports them
 * in the status bar and Output Log, where the smoke test finds them.
 */
public final class NonInteractiveDialogService implements EditorDialogService {

    @Override
    public Optional<File> chooseOpenFile(String title, File initialDirectory, String filterDescription, String extension) {
        return Optional.empty();
    }

    @Override
    public Optional<File> chooseSaveFile(String title, File suggestedFile, String filterDescription, String extension) {
        return Optional.empty();
    }

    @Override
    public UnsavedChangesChoice askUnsavedChanges(String documentName) {
        return UnsavedChangesChoice.DISCARD;
    }

    /** Intentionally silent: the caller has already logged the error; a modal box would block the run. */
    @Override
    public void showError(String title, String message) {
        // Reported by the caller through EditorState.setStatusMessage
    }
}
