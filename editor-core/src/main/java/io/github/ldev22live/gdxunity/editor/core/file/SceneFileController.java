package io.github.ldev22live.gdxunity.editor.core.file;

import io.github.ldev22live.gdxunity.editor.core.EditorState;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Scene document handling: New, Open, Save, Save As, unsaved-changes tracking and the window title.
 *
 * <p>Any change recorded in the {@link io.github.ldev22live.gdxunity.editor.core.command.CommandHistory}
 * marks the document dirty; a successful save, open or new marks it clean. Operations that would discard
 * unsaved changes ask the user first through {@link EditorDialogService}.
 */
public class SceneFileController {

    /** Scene files are versioned JSON (TD-010). */
    public static final String SCENE_EXTENSION = "json";
    public static final String SCENE_FILTER_DESCRIPTION = "GdxUnity Scene (*.json)";
    public static final String APPLICATION_NAME = "GdxUnity Editor";
    static final String UNTITLED_NAME = "Untitled";

    /** Notified when the file, dirty flag or title changes. */
    public interface Listener {
        void onDocumentStateChanged(SceneFileController controller);
    }

    private final EditorState editorState;
    private final EditorDialogService dialogs;
    private final File defaultDirectory;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private File currentFile;
    private boolean dirty;

    /**
     * @param defaultDirectory where dialogs start when no file is open yet; may be null
     */
    public SceneFileController(EditorState editorState, EditorDialogService dialogs, File defaultDirectory) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs cannot be null");
        this.defaultDirectory = defaultDirectory;
        editorState.getCommandHistory().addListener(history -> setDirty(true));
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    public boolean isDirty() {
        return dirty;
    }

    public File getCurrentFile() {
        return currentFile;
    }

    /** File name of the open scene, or {@code "Untitled"} when it has never been saved. */
    public String getDocumentName() {
        return currentFile != null ? currentFile.getName() : UNTITLED_NAME;
    }

    /** E.g. {@code "level1.json* - GdxUnity Editor"}; the asterisk marks unsaved changes. */
    public String getWindowTitle() {
        return getDocumentName() + (dirty ? "*" : "") + " - " + APPLICATION_NAME;
    }

    /** Replaces the scene with an empty one, after offering to save unsaved changes. */
    public boolean newScene() {
        if (rejectDuringPlayMode() || !confirmDiscardChanges()) {
            return false;
        }
        editorState.newScene("Untitled Scene");
        currentFile = null;
        markClean();
        return true;
    }

    /** Asks for a scene file and opens it, after offering to save unsaved changes. */
    public boolean open() {
        if (rejectDuringPlayMode() || !confirmDiscardChanges()) {
            return false;
        }
        Optional<File> chosen = dialogs.chooseOpenFile("Open Scene", dialogDirectory(), SCENE_FILTER_DESCRIPTION, SCENE_EXTENSION);
        return chosen.isPresent() && openFile(chosen.get());
    }

    /** Opens the given scene file without prompting. Reports failures to the user and the status bar. */
    public boolean openFile(File file) {
        Objects.requireNonNull(file, "file cannot be null");
        try {
            editorState.loadSceneFromFile(file);
        } catch (Exception e) {
            reportFailure("Open failed", "Could not open " + file.getName() + ": " + e.getMessage());
            return false;
        }
        currentFile = file;
        markClean();
        return true;
    }

    /** Saves to the current file, or asks for one if the scene has never been saved. */
    public boolean save() {
        if (rejectDuringPlayMode()) {
            return false;
        }
        return currentFile == null ? saveAs() : writeTo(currentFile);
    }

    /** Asks for a file and saves the scene there; adds the {@code .json} extension if missing. */
    public boolean saveAs() {
        if (rejectDuringPlayMode()) {
            return false;
        }
        File suggested = currentFile != null ? currentFile : new File(dialogDirectory(), "scene." + SCENE_EXTENSION);
        Optional<File> chosen = dialogs.chooseSaveFile("Save Scene As", suggested, SCENE_FILTER_DESCRIPTION, SCENE_EXTENSION);
        return chosen.isPresent() && writeTo(withSceneExtension(chosen.get()));
    }

    /**
     * Offers to save unsaved changes before they would be lost.
     *
     * @return true if it is safe to continue (saved, discarded, or nothing to save); false if cancelled
     */
    public boolean confirmDiscardChanges() {
        if (!dirty) {
            return true;
        }
        return switch (dialogs.askUnsavedChanges(getDocumentName())) {
            case SAVE -> save();
            case DISCARD -> true;
            case CANCEL -> false;
        };
    }

    static File withSceneExtension(File file) {
        String suffix = "." + SCENE_EXTENSION;
        return file.getName().toLowerCase().endsWith(suffix) ? file : new File(file.getPath() + suffix);
    }

    private boolean writeTo(File file) {
        try {
            editorState.saveSceneToFile(file);
        } catch (Exception e) {
            reportFailure("Save failed", "Could not save " + file.getName() + ": " + e.getMessage());
            return false;
        }
        currentFile = file;
        markClean();
        return true;
    }

    /** File operations act on the authored scene, which is locked while Play mode runs. */
    private boolean rejectDuringPlayMode() {
        if (editorState.isPlayModeActive()) {
            editorState.setStatusMessage("Stop Play mode before creating, opening or saving scenes", true);
            return true;
        }
        return false;
    }

    private File dialogDirectory() {
        if (currentFile != null && currentFile.getAbsoluteFile().getParentFile() != null) {
            return currentFile.getAbsoluteFile().getParentFile();
        }
        return defaultDirectory;
    }

    private void reportFailure(String title, String message) {
        editorState.setStatusMessage(message, true);
        dialogs.showError(title, message);
    }

    private void markClean() {
        dirty = false;
        notifyListeners();
    }

    private void setDirty(boolean value) {
        if (dirty != value) {
            dirty = value;
            notifyListeners();
        }
    }

    private void notifyListeners() {
        for (Listener listener : listeners) {
            listener.onDocumentStateChanged(this);
        }
    }
}
