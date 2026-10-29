package io.github.ldev22live.gdxunity.editor.core.file;

import java.io.File;
import java.util.Optional;

/**
 * Modal dialogs the editor needs from the desktop (TD-022). Implemented natively on the desktop and by
 * fakes in tests, so file logic stays headless-testable.
 */
public interface EditorDialogService {

    /** Answer to "save changes before continuing?". */
    enum UnsavedChangesChoice {
        SAVE,
        DISCARD,
        CANCEL
    }

    /**
     * @param initialDirectory directory the dialog starts in; may be null
     * @param extension        file extension without the dot, e.g. {@code "json"}
     * @return the chosen file, or empty if the user cancelled
     */
    Optional<File> chooseOpenFile(String title, File initialDirectory, String filterDescription, String extension);

    /**
     * @param suggestedFile file pre-filled in the dialog; may be null
     * @return the chosen file, or empty if the user cancelled
     */
    Optional<File> chooseSaveFile(String title, File suggestedFile, String filterDescription, String extension);

    UnsavedChangesChoice askUnsavedChanges(String documentName);

    void showError(String title, String message);
}
