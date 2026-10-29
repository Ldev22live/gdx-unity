package io.github.ldev22live.gdxunity.editor.core.action;

import com.badlogic.gdx.Input;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.io.File;
import java.util.Objects;

/**
 * Registers the standard editor actions that only depend on {@link EditorState}.
 * Desktop-only actions (window toggles, theme) are registered by the desktop shell.
 */
public final class DefaultEditorActions {

    public static final String MENU_FILE = "File";
    public static final String MENU_EDIT = "Edit";
    public static final String MENU_CREATE = "Create";
    public static final String MENU_TOOLS = "Tools";
    public static final String MENU_WINDOW = "Window";
    public static final String MENU_HELP = "Help";

    public static final String FILE_NEW_SCENE = "file.new-scene";
    public static final String FILE_SAVE_BACKUP = "file.save-backup";
    public static final String FILE_LOAD_BACKUP = "file.load-backup";
    public static final String EDIT_UNDO = "edit.undo";
    public static final String EDIT_REDO = "edit.redo";
    public static final String EDIT_REDO_ALTERNATE = "edit.redo.ctrl-shift-z";
    public static final String EDIT_DUPLICATE = "edit.duplicate";
    public static final String EDIT_DELETE = "edit.delete";
    public static final String CREATE_EMPTY_ENTITY = "create.empty-entity";
    public static final String TOOL_SELECT = "tools.select";
    public static final String TOOL_MOVE = "tools.move";
    public static final String TOOL_ROTATE = "tools.rotate";
    public static final String TOOL_SCALE = "tools.scale";
    public static final String VIEW_TOGGLE_2D_3D = "view.toggle-2d-3d";
    public static final String HELP_ABOUT = "help.about";

    /**
     * Fixed scene file used by the backup save/load actions until file dialogs replace them
     * (Phase 12B step B, TD-022). Resolved against the working directory.
     */
    public static final String SCENE_BACKUP_FILE_NAME = "scene_backup.json";

    private static final int GROUP_FIRST = 0;
    private static final int GROUP_SECOND = 1;

    private DefaultEditorActions() {}

    /** Registers File, Edit, Create, Tools and Help actions. */
    public static void registerDefaults(EditorActionRegistry registry, EditorState editorState) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(editorState, "editorState cannot be null");

        // File
        registry.register(EditorAction.builder(FILE_NEW_SCENE, "New Scene", () -> editorState.newScene("Untitled Scene"))
            .menu(MENU_FILE, GROUP_FIRST)
            .shortcut(Input.Keys.N, EditorKeyboardDispatcher.MOD_CTRL)
            .enabledWhen(() -> !editorState.isPlayModeActive())
            .build());
        registry.register(EditorAction.builder(FILE_SAVE_BACKUP, "Save Scene Backup", () -> saveBackup(editorState))
            .menu(MENU_FILE, GROUP_SECOND)
            .shortcut(Input.Keys.S, EditorKeyboardDispatcher.MOD_CTRL)
            .enabledWhen(() -> !editorState.isPlayModeActive() && editorState.getActiveScene() != null)
            .build());
        registry.register(EditorAction.builder(FILE_LOAD_BACKUP, "Load Scene Backup", () -> loadBackup(editorState))
            .menu(MENU_FILE, GROUP_SECOND)
            .enabledWhen(() -> !editorState.isPlayModeActive())
            .build());

        // Edit
        registry.register(EditorAction.builder(EDIT_UNDO, "Undo", editorState::undo)
            .menu(MENU_EDIT, GROUP_FIRST)
            .shortcut(Input.Keys.Z, EditorKeyboardDispatcher.MOD_CTRL)
            .enabledWhen(() -> editorState.getCommandHistory().canUndo())
            .build());
        registry.register(EditorAction.builder(EDIT_REDO, "Redo", editorState::redo)
            .menu(MENU_EDIT, GROUP_FIRST)
            .shortcut(Input.Keys.Y, EditorKeyboardDispatcher.MOD_CTRL)
            .enabledWhen(() -> editorState.getCommandHistory().canRedo())
            .build());
        // Alternate redo binding, not shown in menus
        registry.register(EditorAction.builder(EDIT_REDO_ALTERNATE, "Redo", editorState::redo)
            .shortcut(Input.Keys.Z, EditorKeyboardDispatcher.MOD_CTRL | EditorKeyboardDispatcher.MOD_SHIFT)
            .enabledWhen(() -> editorState.getCommandHistory().canRedo())
            .build());
        registry.register(EditorAction.builder(EDIT_DUPLICATE, "Duplicate", editorState::duplicateSelectedEntity)
            .menu(MENU_EDIT, GROUP_SECOND)
            .shortcut(Input.Keys.D, EditorKeyboardDispatcher.MOD_CTRL)
            .enabledWhen(() -> canEditSelection(editorState))
            .build());
        registry.register(EditorAction.builder(EDIT_DELETE, "Delete", editorState::deleteSelectedEntity)
            .menu(MENU_EDIT, GROUP_SECOND)
            .shortcut(Input.Keys.FORWARD_DEL, 0)
            .enabledWhen(() -> canEditSelection(editorState))
            .build());

        // Create
        registry.register(EditorAction.builder(CREATE_EMPTY_ENTITY, "Empty Entity", () -> createEmptyEntity(editorState))
            .menu(MENU_CREATE, GROUP_FIRST)
            .enabledWhen(() -> !editorState.isPlayModeActive() && editorState.getActiveScene() != null)
            .build());

        // Tools
        registerTool(registry, editorState, TOOL_SELECT, "Select", ToolMode.SELECT, Input.Keys.Q);
        registerTool(registry, editorState, TOOL_MOVE, "Move", ToolMode.MOVE, Input.Keys.W);
        registerTool(registry, editorState, TOOL_ROTATE, "Rotate", ToolMode.ROTATE, Input.Keys.E);
        registerTool(registry, editorState, TOOL_SCALE, "Scale", ToolMode.SCALE, Input.Keys.R);

        registry.register(EditorAction.builder(VIEW_TOGGLE_2D_3D, "2D / 3D", () -> toggleViewportMode(editorState))
            .menu(MENU_TOOLS, GROUP_SECOND)
            .build());

        // Help
        registry.register(EditorAction.builder(HELP_ABOUT, "About GdxUnity",
                () -> editorState.setStatusMessage("GdxUnity Editor — Java 21, libGDX", false))
            .menu(MENU_HELP, GROUP_FIRST)
            .build());
    }

    private static void registerTool(EditorActionRegistry registry, EditorState editorState,
                                     String id, String label, ToolMode mode, int keycode) {
        registry.register(EditorAction.builder(id, label, () -> editorState.setToolMode(mode))
            .menu(MENU_TOOLS, GROUP_FIRST)
            .shortcut(keycode, 0)
            .build());
    }

    private static void toggleViewportMode(EditorState editorState) {
        ViewportMode next = editorState.getViewportMode().toggled();
        editorState.setViewportMode(next);
        editorState.setStatusMessage("Scene view: " + (next == ViewportMode.VIEW_3D ? "3D" : "2D"), false);
    }

    private static boolean canEditSelection(EditorState editorState) {
        return !editorState.isPlayModeActive() && editorState.getSelectedEntity() != null;
    }

    private static void createEmptyEntity(EditorState editorState) {
        Entity created = editorState.createEntity("Entity");
        if (created != null) {
            editorState.setSelectedEntity(created);
        }
    }

    private static void saveBackup(EditorState editorState) {
        File file = new File(SCENE_BACKUP_FILE_NAME);
        try {
            editorState.saveSceneToFile(file);
            editorState.setStatusMessage("Scene saved to " + file.getName(), false);
        } catch (Exception e) {
            editorState.setStatusMessage("Save failed: " + e.getMessage(), true);
        }
    }

    private static void loadBackup(EditorState editorState) {
        File file = new File(SCENE_BACKUP_FILE_NAME);
        try {
            editorState.loadSceneFromFile(file);
            editorState.setStatusMessage("Scene loaded from " + file.getName(), false);
        } catch (Exception e) {
            editorState.setStatusMessage("Load failed: " + e.getMessage(), true);
        }
    }
}
