package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorAction;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.sculpt.BrushSettings;
import io.github.ldev22live.gdxunity.engine.sculpt.BrushType;
import io.github.ldev22live.gdxunity.engine.sculpt.SymmetrySettings;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The menu and keyboard actions of sculpting (TD-038): create a sculpt, enter and leave sculpt mode, choose the
 * brush, change its size and strength, mirror, and save. Registered by the desktop shell; all logic is here so it
 * is tested without a window.
 */
public final class SculptActions {
    /** Creates a sculpt asset and an entity showing it (Create menu). */
    public static final String CREATE_SCULPT = "create.sculpt";
    /** Switches sculpt mode on or off (Tools menu and the viewport toolbar). */
    public static final String TOGGLE_MODE = "tools.sculpt-toggle";
    /** Turns sculpt mode on (Tools menu). */
    public static final String ENTER_MODE = "tools.sculpt-enter";
    /** Turns sculpt mode off (Tools menu). */
    public static final String EXIT_MODE = "tools.sculpt-exit";
    /** Followed by the lowercase {@link BrushType} name, for example {@code tools.sculpt-brush.smooth}. */
    public static final String BRUSH_PREFIX = "tools.sculpt-brush.";
    /** Toggles mirroring across the plane x = 0. */
    public static final String MIRROR_X = "tools.sculpt-mirror-x";
    /** Toggles mirroring across the plane y = 0. */
    public static final String MIRROR_Y = "tools.sculpt-mirror-y";
    /** Toggles mirroring across the plane z = 0. */
    public static final String MIRROR_Z = "tools.sculpt-mirror-z";
    /** Toggles the wireframe overlay on the sculpts. */
    public static final String TOGGLE_WIREFRAME = "tools.sculpt-wireframe";
    /** Makes the brush larger. */
    public static final String BRUSH_LARGER = "tools.sculpt-larger";
    /** Makes the brush smaller. */
    public static final String BRUSH_SMALLER = "tools.sculpt-smaller";
    /** Makes the brush stronger. */
    public static final String BRUSH_STRONGER = "tools.sculpt-stronger";
    /** Makes the brush weaker. */
    public static final String BRUSH_WEAKER = "tools.sculpt-weaker";
    /** Writes every modified sculpt to its file (File menu). */
    public static final String SAVE_SCULPTS = "file.save-sculpts";

    /** One key press changes the brush radius by this factor. */
    static final float RADIUS_STEP = 1.25f;
    /** Smallest brush radius the keys reach, in the sculpt's own units. */
    static final float MIN_RADIUS = 0.05f;
    /** Largest brush radius the keys reach, in the sculpt's own units. */
    static final float MAX_RADIUS = 5000f;
    /** One key press changes the brush strength by this much. */
    static final float STRENGTH_STEP = 0.1f;
    /** Weakest brush strength the keys reach. */
    static final float MIN_STRENGTH = 0.05f;

    private static final int GROUP_BRUSH = 3;
    private static final int GROUP_MIRROR = 4;
    private static final int GROUP_SIZE = 5;

    private SculptActions() {
    }

    /**
     * Registers the sculpt actions.
     *
     * @param spawnPosition where a newly created sculpt is placed
     */
    public static void register(EditorActionRegistry registry, EditorState editorState, SculptSession session,
                                Supplier<Vector3> spawnPosition) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(spawnPosition, "spawnPosition cannot be null");
        BooleanSupplier editable = () -> !editorState.isPlayModeActive();
        BooleanSupplier canEnter = () -> editable.getAsBoolean() && !session.isSculptMode();
        BooleanSupplier sculpting = session::isSculptMode;

        registry.register(EditorAction.builder(CREATE_SCULPT, "Sculpt",
                () -> createSculpt(editorState, session, spawnPosition))
            .menu(DefaultEditorActions.MENU_CREATE, GROUP_BRUSH)
            .enabledWhen(editable)
            .build());
        registry.register(EditorAction.builder(TOGGLE_MODE, "Sculpt", () -> setMode(editorState, session, !session.isSculptMode()))
            .menu(DefaultEditorActions.MENU_TOOLS, GROUP_BRUSH)
            .shortcut(Input.Keys.F4, 0)
            .enabledWhen(editable)
            .build());
        registry.register(EditorAction.builder(ENTER_MODE, "Enter Sculpt Mode", () -> setMode(editorState, session, true))
            .menu(DefaultEditorActions.MENU_TOOLS, GROUP_BRUSH)
            .enabledWhen(canEnter)
            .build());
        registry.register(EditorAction.builder(EXIT_MODE, "Exit Sculpt Mode", () -> setMode(editorState, session, false))
            .menu(DefaultEditorActions.MENU_TOOLS, GROUP_BRUSH)
            .enabledWhen(sculpting)
            .build());
        for (BrushType type : BrushType.values()) {
            registry.register(EditorAction.builder(brushActionId(type), "Sculpt Brush: " + displayName(type),
                    () -> selectBrush(editorState, session, type))
                .menu(DefaultEditorActions.MENU_TOOLS, GROUP_BRUSH)
                .enabledWhen(editable)
                .build());
        }
        registerMirror(registry, editorState, session, MIRROR_X, "X", editable);
        registerMirror(registry, editorState, session, MIRROR_Y, "Y", editable);
        registerMirror(registry, editorState, session, MIRROR_Z, "Z", editable);
        registry.register(EditorAction.builder(TOGGLE_WIREFRAME, "Sculpt Wireframe", () -> {
                session.setWireframe(!session.isWireframe());
                editorState.setStatusMessage("Sculpt wireframe: " + (session.isWireframe() ? "on" : "off"), false);
            })
            .menu(DefaultEditorActions.MENU_TOOLS, GROUP_MIRROR)
            .build());
        registry.register(EditorAction.builder(BRUSH_LARGER, "Sculpt Brush Larger",
                () -> scaleRadius(editorState, session, RADIUS_STEP))
            .menu(DefaultEditorActions.MENU_TOOLS, GROUP_SIZE)
            .shortcut(Input.Keys.RIGHT_BRACKET, 0)
            .enabledWhen(sculpting)
            .build());
        registry.register(EditorAction.builder(BRUSH_SMALLER, "Sculpt Brush Smaller",
                () -> scaleRadius(editorState, session, 1f / RADIUS_STEP))
            .menu(DefaultEditorActions.MENU_TOOLS, GROUP_SIZE)
            .shortcut(Input.Keys.LEFT_BRACKET, 0)
            .enabledWhen(sculpting)
            .build());
        registry.register(EditorAction.builder(BRUSH_STRONGER, "Sculpt Brush Stronger",
                () -> changeStrength(editorState, session, STRENGTH_STEP))
            .menu(DefaultEditorActions.MENU_TOOLS, GROUP_SIZE)
            .shortcut(Input.Keys.EQUALS, 0)
            .enabledWhen(sculpting)
            .build());
        registry.register(EditorAction.builder(BRUSH_WEAKER, "Sculpt Brush Weaker",
                () -> changeStrength(editorState, session, -STRENGTH_STEP))
            .menu(DefaultEditorActions.MENU_TOOLS, GROUP_SIZE)
            .shortcut(Input.Keys.MINUS, 0)
            .enabledWhen(sculpting)
            .build());
        registry.register(EditorAction.builder(SAVE_SCULPTS, "Save Sculpts", () -> saveSculpts(editorState, session))
            .menu(DefaultEditorActions.MENU_FILE, GROUP_BRUSH)
            .shortcut(Input.Keys.S, EditorKeyboardDispatcher.MOD_CTRL | EditorKeyboardDispatcher.MOD_ALT)
            .enabledWhen(session::hasModifiedDocuments)
            .build());
    }

    /** The id of the action that selects a brush. */
    public static String brushActionId(BrushType type) {
        return BRUSH_PREFIX + type.name().toLowerCase(Locale.ROOT);
    }

    private static String displayName(BrushType type) {
        String name = type.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static void registerMirror(EditorActionRegistry registry, EditorState editorState, SculptSession session,
                                       String id, String axis, BooleanSupplier enabled) {
        registry.register(EditorAction.builder(id, "Sculpt Mirror " + axis, () -> {
                SymmetrySettings symmetry = session.getSymmetry();
                boolean now;
                switch (axis) {
                    case "X" -> {
                        symmetry.setMirrorX(!symmetry.isMirrorX());
                        now = symmetry.isMirrorX();
                    }
                    case "Y" -> {
                        symmetry.setMirrorY(!symmetry.isMirrorY());
                        now = symmetry.isMirrorY();
                    }
                    default -> {
                        symmetry.setMirrorZ(!symmetry.isMirrorZ());
                        now = symmetry.isMirrorZ();
                    }
                }
                editorState.setStatusMessage("Sculpt mirror " + axis + ": " + (now ? "on" : "off"), false);
            })
            .menu(DefaultEditorActions.MENU_TOOLS, GROUP_MIRROR)
            .enabledWhen(enabled)
            .build());
    }

    private static void createSculpt(EditorState editorState, SculptSession session, Supplier<Vector3> spawnPosition) {
        AssetReference asset;
        try {
            asset = session.getAssets().create("Sculpt");
            session.open(asset);
        } catch (IOException | RuntimeException e) {
            editorState.setStatusMessage("Cannot create the sculpt: " + e.getMessage(), true);
            return;
        }
        Component component = new DynamicMeshComponent(asset.getPath());
        if (editorState.executeCommand(new CreateEntityCommand(editorState, "Sculpt", spawnPosition.get(), List.of(component)))) {
            session.setSculptMode(true);
            editorState.setStatusMessage("Created " + asset.getPath() + ". " + brushSummary(session), false);
        }
    }

    private static void setMode(EditorState editorState, SculptSession session, boolean on) {
        session.setSculptMode(on);
        if (!on) {
            editorState.setStatusMessage("Sculpt mode off", false);
        } else if (editorState.getViewportMode() != ViewportMode.VIEW_3D) {
            editorState.setStatusMessage("Sculpt mode on. Switch to the 3D view to sculpt. " + brushSummary(session), false);
        } else if (session.getDocumentOf(editorState.getSelectedEntity()) == null) {
            editorState.setStatusMessage("Sculpt mode on. Select a sculpt to paint on it. " + brushSummary(session), false);
        } else {
            editorState.setStatusMessage("Sculpt mode on. " + brushSummary(session), false);
        }
    }

    private static void selectBrush(EditorState editorState, SculptSession session, BrushType type) {
        session.setBrushType(type);
        session.setSculptMode(true);
        editorState.setStatusMessage(brushSummary(session), false);
    }

    private static void scaleRadius(EditorState editorState, SculptSession session, float factor) {
        BrushSettings brush = session.getBrush();
        brush.setRadius(Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, brush.getRadius() * factor)));
        editorState.setStatusMessage(brushSummary(session), false);
    }

    private static void changeStrength(EditorState editorState, SculptSession session, float delta) {
        BrushSettings brush = session.getBrush();
        brush.setStrength(Math.max(MIN_STRENGTH, Math.min(1f, brush.getStrength() + delta)));
        editorState.setStatusMessage(brushSummary(session), false);
    }

    private static void saveSculpts(EditorState editorState, SculptSession session) {
        try {
            int saved = session.saveAllModified();
            editorState.setStatusMessage("Saved " + saved + " sculpt" + (saved == 1 ? "" : "s"), false);
        } catch (IOException e) {
            editorState.setStatusMessage("Cannot save sculpts: " + e.getMessage(), true);
        }
    }

    /** One line describing the brush, for the status bar and the viewport. */
    public static String brushSummary(SculptSession session) {
        BrushSettings brush = session.getBrush();
        return String.format(Locale.ROOT, "Brush: %s, radius %.2f, strength %.2f%s", displayName(session.getBrushType()),
            brush.getRadius(), brush.getStrength(), mirrorSummary(session.getSymmetry()));
    }

    private static String mirrorSummary(SymmetrySettings symmetry) {
        StringBuilder axes = new StringBuilder();
        if (symmetry.isMirrorX()) {
            axes.append('X');
        }
        if (symmetry.isMirrorY()) {
            axes.append('Y');
        }
        if (symmetry.isMirrorZ()) {
            axes.append('Z');
        }
        return axes.isEmpty() ? "" : ", mirror " + axes;
    }
}
