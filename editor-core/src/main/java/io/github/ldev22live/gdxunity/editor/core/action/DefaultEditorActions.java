package io.github.ldev22live.gdxunity.editor.core.action;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.component.ComponentCatalog;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

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
    public static final String FILE_OPEN_SCENE = "file.open-scene";
    public static final String FILE_SAVE_SCENE = "file.save-scene";
    public static final String FILE_SAVE_SCENE_AS = "file.save-scene-as";
    public static final String FILE_EXIT = "file.exit";
    public static final String EDIT_UNDO = "edit.undo";
    public static final String EDIT_REDO = "edit.redo";
    public static final String EDIT_REDO_ALTERNATE = "edit.redo.ctrl-shift-z";
    public static final String EDIT_DUPLICATE = "edit.duplicate";
    public static final String EDIT_DELETE = "edit.delete";
    public static final String CREATE_EMPTY_ENTITY = "create.empty-entity";
    public static final String CREATE_BOX = "create.box";
    public static final String CREATE_SPHERE = "create.sphere";
    public static final String CREATE_PLANE = "create.plane";
    public static final String CREATE_DIRECTIONAL_LIGHT = "create.directional-light";
    public static final String CREATE_POINT_LIGHT = "create.point-light";
    public static final String TOOL_SELECT = "tools.select";
    public static final String TOOL_MOVE = "tools.move";
    public static final String TOOL_ROTATE = "tools.rotate";
    public static final String TOOL_SCALE = "tools.scale";
    public static final String VIEW_TOGGLE_2D_3D = "view.toggle-2d-3d";
    public static final String HELP_ABOUT = "help.about";

    private static final int GROUP_FIRST = 0;
    private static final int GROUP_SECOND = 1;
    private static final int GROUP_THIRD = 2;

    private DefaultEditorActions() {}

    /** Registers Edit, Create, Tools and Help actions. File actions: {@link #registerFileActions}. */
    public static void registerDefaults(EditorActionRegistry registry, EditorState editorState) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(editorState, "editorState cannot be null");

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

    /**
     * Registers File menu actions backed by {@code files} (TD-022).
     *
     * @param requestExit closes the application; only called after unsaved changes were handled
     */
    public static void registerFileActions(EditorActionRegistry registry, EditorState editorState,
                                           SceneFileController files, Runnable requestExit) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(files, "files cannot be null");
        Objects.requireNonNull(requestExit, "requestExit cannot be null");
        BooleanSupplier editing = () -> !editorState.isPlayModeActive();

        registry.register(EditorAction.builder(FILE_NEW_SCENE, "New Scene", files::newScene)
            .menu(MENU_FILE, GROUP_FIRST)
            .shortcut(Input.Keys.N, EditorKeyboardDispatcher.MOD_CTRL)
            .enabledWhen(editing)
            .build());
        registry.register(EditorAction.builder(FILE_OPEN_SCENE, "Open Scene...", files::open)
            .menu(MENU_FILE, GROUP_FIRST)
            .shortcut(Input.Keys.O, EditorKeyboardDispatcher.MOD_CTRL)
            .enabledWhen(editing)
            .build());
        registry.register(EditorAction.builder(FILE_SAVE_SCENE, "Save Scene", files::save)
            .menu(MENU_FILE, GROUP_SECOND)
            .shortcut(Input.Keys.S, EditorKeyboardDispatcher.MOD_CTRL)
            .enabledWhen(() -> editing.getAsBoolean() && editorState.getActiveScene() != null)
            .build());
        registry.register(EditorAction.builder(FILE_SAVE_SCENE_AS, "Save Scene As...", files::saveAs)
            .menu(MENU_FILE, GROUP_SECOND)
            .shortcut(Input.Keys.S, EditorKeyboardDispatcher.MOD_CTRL | EditorKeyboardDispatcher.MOD_SHIFT)
            .enabledWhen(() -> editing.getAsBoolean() && editorState.getActiveScene() != null)
            .build());
        registry.register(EditorAction.builder(FILE_EXIT, "Exit", () -> {
                if (files.confirmDiscardChanges()) {
                    requestExit.run();
                }
            })
            .menu(MENU_FILE, GROUP_THIRD)
            .build());
    }

    /**
     * Registers Create menu entries for 3D primitives and lights. Each creates one entity at
     * {@code spawnPosition} (normally the center of the view) with its component attached, as one undo step.
     */
    public static void registerCreate3DActions(EditorActionRegistry registry, EditorState editorState,
                                               Supplier<Vector3> spawnPosition) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(spawnPosition, "spawnPosition cannot be null");
        BooleanSupplier canCreate = () -> !editorState.isPlayModeActive() && editorState.getActiveScene() != null;
        registerCreate(registry, editorState, canCreate, spawnPosition, CREATE_BOX, "Box", GROUP_SECOND,
            () -> new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX));
        registerCreate(registry, editorState, canCreate, spawnPosition, CREATE_SPHERE, "Sphere", GROUP_SECOND,
            () -> new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.SPHERE));
        registerCreate(registry, editorState, canCreate, spawnPosition, CREATE_PLANE, "Plane", GROUP_SECOND,
            () -> new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.PLANE));
        registerCreate(registry, editorState, canCreate, spawnPosition, CREATE_DIRECTIONAL_LIGHT, "Directional Light", GROUP_THIRD,
            DirectionalLightComponent::new);
        registerCreate(registry, editorState, canCreate, spawnPosition, CREATE_POINT_LIGHT, "Point Light", GROUP_THIRD,
            ComponentCatalog::createPointLight);
    }

    private static void registerCreate(EditorActionRegistry registry, EditorState editorState, BooleanSupplier canCreate,
                                       Supplier<Vector3> spawnPosition, String id, String name, int group,
                                       Supplier<Component> component) {
        registry.register(EditorAction.builder(id, name, () -> editorState.executeCommand(
                new CreateEntityCommand(editorState, name, spawnPosition.get(), List.of(component.get()))))
            .menu(MENU_CREATE, group)
            .enabledWhen(canCreate)
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
}
