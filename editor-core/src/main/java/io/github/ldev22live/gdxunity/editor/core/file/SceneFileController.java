package io.github.ldev22live.gdxunity.editor.core.file;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    public static final String SCENE_FILTER_DESCRIPTION = "Forgeroom Scene (*.json)";
    public static final String OPEN_FILTER_DESCRIPTION = "Forgeroom files (scenes, songs, graphs, clips, sprites)";
    /**
     * The asset types File > Open sends to their own editor instead of loading as a scene: they are the ones with an editor that opens
     * one file (a prefab has none of its own, it is placed in a scene and edited there).
     */
    private static final List<AssetType> OPENABLE_ASSETS = List.of(AssetType.SONG, AssetType.GRAPH, AssetType.ANIMATION, AssetType.SPRITE);

    /**
     * Opens an asset file in the editor that belongs to it (the shell supplies it, as the editors are not part of this module).
     * Returns null when the editor was started, otherwise why it could not be.
     */
    @FunctionalInterface
    public interface AssetOpener {
        String open(File file, AssetType type);
    }
    public static final String APPLICATION_NAME = "Forgeroom Editor";
    static final String UNTITLED_NAME = "Untitled";

    /** Notified when the file, dirty flag or title changes. */
    public interface Listener {
        void onDocumentStateChanged(SceneFileController controller);
    }

    private final EditorState editorState;
    private final EditorDialogService dialogs;
    private final File defaultDirectory;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> beforeSaveHooks = new CopyOnWriteArrayList<>();

    private File currentFile;
    private boolean dirty;
    private File readOnlyFolder;
    private AssetOpener assetOpener;

    /**
     * @param defaultDirectory where dialogs start when no file is open yet; may be null
     */
    public SceneFileController(EditorState editorState, EditorDialogService dialogs, File defaultDirectory) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs cannot be null");
        this.defaultDirectory = defaultDirectory;
        editorState.getCommandHistory().addListener(history -> setDirty(true));
    }

    /**
     * Marks a folder of shipped originals (the sample games) as read-only: a scene opened from it opens as an untitled copy, so
     * Save asks where to keep it, and nothing can be saved into the folder. The originals stay as they shipped (roadmap R1).
     *
     * @param folder the folder, or null for none
     */
    public void setReadOnlyFolder(File folder) {
        this.readOnlyFolder = folder;
    }

    /** Whether {@code file} is inside the read-only folder set with {@link #setReadOnlyFolder}. */
    public boolean isReadOnly(File file) {
        Objects.requireNonNull(file, "file cannot be null");
        if (readOnlyFolder == null) {
            return false;
        }
        return file.getAbsoluteFile().toPath().normalize().startsWith(readOnlyFolder.getAbsoluteFile().toPath().normalize());
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    /**
     * Runs {@code hook} before every save, so temporary editor changes to the scene (such as a Timeline preview pose)
     * can be undone first and are not written to the file.
     */
    public void addBeforeSaveHook(Runnable hook) {
        beforeSaveHooks.add(Objects.requireNonNull(hook, "hook cannot be null"));
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

    /** E.g. {@code "level1.json* - Forgeroom Editor"}; the asterisk marks unsaved changes. */
    public String getWindowTitle() {
        return getDocumentName() + (dirty ? "*" : "") + " - " + APPLICATION_NAME;
    }

    /** Replaces the scene with an empty one, after offering to save unsaved changes. */
    public boolean newScene() {
        return newScene(null);
    }

    /** Like {@link #newScene()}, for a scene of the given game mode (null: none chosen). */
    public boolean newScene(io.github.ldev22live.gdxunity.engine.scene.GameMode mode) {
        if (rejectDuringPlayMode() || !confirmDiscardChanges()) {
            return false;
        }
        editorState.newScene("Untitled Scene", mode);
        currentFile = null;
        markClean();
        return true;
    }

    /** Asks for a scene file and opens it, after offering to save unsaved changes. */
    public boolean open() {
        return choose("Open", dialogDirectory(), OPEN_FILTER_DESCRIPTION, openExtensions());
    }

    /** Scenes first, then the extensions of the assets that open in an editor. */
    static List<String> openExtensions() {
        List<String> extensions = new ArrayList<>();
        extensions.add(SCENE_EXTENSION);
        for (AssetType type : OPENABLE_ASSETS) {
            extensions.addAll(type.getExtensions());
        }
        return List.copyOf(extensions);
    }

    /** Sets what opens a song, graph, clip or sprite chosen in File > Open; without one such a file is refused with a message. */
    public void setAssetOpener(AssetOpener assetOpener) {
        this.assetOpener = assetOpener;
    }

    /**
     * Like {@link #open()}, but the dialog starts in {@code directory}, for example the project's sample games.
     *
     * @param title dialog title
     */
    public boolean openFrom(String title, File directory) {
        return choose(title, directory, SCENE_FILTER_DESCRIPTION, List.of(SCENE_EXTENSION));
    }

    /**
     * Asks for a file. A scene replaces the open one, so unsaved changes are offered for saving, but only once a scene was chosen:
     * opening a song or a graph leaves the scene alone and must not ask about it.
     */
    private boolean choose(String title, File directory, String filterDescription, List<String> extensions) {
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(directory, "directory cannot be null");
        if (rejectDuringPlayMode()) {
            return false;
        }
        Optional<File> chosen = dialogs.chooseOpenFile(title, directory, filterDescription, extensions);
        if (chosen.isEmpty()) {
            return false;
        }
        File file = chosen.get();
        boolean isAsset = AssetType.fromPath(file.getName()) != AssetType.UNKNOWN;
        if (!isAsset && !confirmDiscardChanges()) {
            return false;
        }
        return openFile(file);
    }

    /**
     * File > Use Sample as Template: asks for one of the project's sample scenes and makes a separate project from it in the user's
     * projects folder (see {@link io.github.ldev22live.gdxunity.editor.core.project.TemplateProject}). The open scene and the shipped
     * sample are not touched, so no unsaved-changes prompt is needed. The editor opens one project per start, so the new project is
     * opened by starting the editor on it; the message says how.
     *
     * @param sourceRoot       the project that holds the samples
     * @param samplesDirectory where the dialog starts
     * @param projectsDir      where the new project is made
     * @return true if a project was made
     */
    public boolean useAsTemplate(File sourceRoot, File samplesDirectory, File projectsDir) {
        Objects.requireNonNull(sourceRoot, "sourceRoot cannot be null");
        Objects.requireNonNull(samplesDirectory, "samplesDirectory cannot be null");
        Objects.requireNonNull(projectsDir, "projectsDir cannot be null");
        Optional<File> chosen = dialogs.chooseOpenFile("Use Sample as Template", samplesDirectory, SCENE_FILTER_DESCRIPTION,
            List.of(SCENE_EXTENSION));
        if (chosen.isEmpty()) {
            return false;
        }
        Optional<String> relative = ProjectPaths.relativeAssetPath(sourceRoot, chosen.get());
        if (relative.isEmpty()) {
            reportFailure("Template failed", chosen.get().getName() + " is not inside this project (" + sourceRoot
                + "), so it cannot be used as a template.");
            return false;
        }
        try {
            TemplateProject.Result made = TemplateProject.create(sourceRoot, relative.get(), projectsDir, null);
            // Objects and Characters have something to place in the new project (roadmap M3c.5)
            Optional<GameMode> mode = StarterPalette.modeNamedBy(new File(made.folder(), made.startScene()));
            TemplateProject.Result result = mode.isPresent() ? StarterPalette.addTo(sourceRoot, made, mode.get()) : made;
            editorState.setStatusMessage("Created the project '" + result.folder().getName() + "' from " + chosen.get().getName() + " ("
                + result.files().size() + " files) in " + result.folder().getParent() + ". Open it by starting the editor with --project \""
                + result.folder().getAbsolutePath() + "\". The sample is unchanged.", false);
            return true;
        } catch (IOException e) {
            reportFailure("Template failed", "Could not make a project from " + chosen.get().getName() + ": " + e.getMessage());
            return false;
        }
    }

    /** Opens the given scene file without prompting. Reports failures to the user and the status bar. */
    public boolean openFile(File file) {
        Objects.requireNonNull(file, "file cannot be null");
        AssetType assetType = AssetType.fromPath(file.getName());
        if (assetType != AssetType.UNKNOWN) {
            return openAsset(file, assetType);
        }
        try {
            editorState.loadSceneFromFile(file);
        } catch (Exception e) {
            reportFailure("Open failed", "Could not open " + file.getName() + ": " + e.getMessage());
            return false;
        }
        if (isReadOnly(file)) {
            // A shipped sample: keep working on a copy that has no file yet, so Save asks for a name and place
            currentFile = null;
            editorState.setStatusMessage("Opened sample " + file.getName() + " as a copy. Save Scene As keeps your own version; "
                + "the original stays unchanged.", false);
        } else {
            currentFile = file;
        }
        markClean();
        return true;
    }

    /**
     * A file that is not a scene: a song, graph, clip or sprite opens in its own editor and leaves the scene alone; any other asset is
     * refused with a message that says what it is, because a scene parser can only fail on it with a confusing schema error.
     */
    private boolean openAsset(File file, AssetType type) {
        String kind = type.name().toLowerCase(Locale.ROOT);
        if (!OPENABLE_ASSETS.contains(type)) {
            reportFailure("Open failed", file.getName() + " is a " + kind + " file, not a scene, and File > Open does not open it. "
                + "File > Open opens scenes, songs, graphs, animation clips and sprites. "
                + io.github.ldev22live.gdxunity.editor.core.asset.AssetOpening.guidance(type)
                + " To bring a file from outside the project into it, use File > Import Asset.");
            return false;
        }
        if (assetOpener == null) {
            reportFailure("Open failed", file.getName() + " is a " + kind + " file, not a scene, and no editor is attached to "
                + "File > Open here. Open it from the Content Browser.");
            return false;
        }
        String problem = assetOpener.open(file, type);
        if (problem != null) {
            reportFailure("Open failed", problem);
            return false;
        }
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
        if (isReadOnly(file)) {
            reportFailure("Save failed", file.getName() + " is in the folder of shipped samples, which is read-only. "
                + "Use Save Scene As and choose another folder or name.");
            return false;
        }
        for (Runnable hook : beforeSaveHooks) {
            hook.run();
        }
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
