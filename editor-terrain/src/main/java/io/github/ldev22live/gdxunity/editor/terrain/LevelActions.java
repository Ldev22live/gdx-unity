package io.github.ldev22live.gdxunity.editor.terrain;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorAction;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;

import java.io.File;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** The level editor's editor action (TD-083): the Level button in the top bar and the Tools menu. */
public final class LevelActions {
    /** Turns the level editor on or off. */
    public static final String TOGGLE = "tools.level-toggle";
    /** Puts a PNG heightmap's heights on the level ground (roadmap M3.5). */
    public static final String IMPORT_HEIGHTMAP = "tools.level-import-heightmap";
    /** Writes the level ground's heights as a 16-bit PNG heightmap (roadmap M3.5). */
    public static final String EXPORT_HEIGHTMAP = "tools.level-export-heightmap";
    private static final String HEIGHTMAP_FILTER = "PNG heightmaps";
    private static final String HEIGHTMAP_EXTENSION = "png";

    private static final int GROUP_LEVEL = 3;

    private LevelActions() { }

    /**
     * Registers {@link #TOGGLE}. Turning the level editor on also switches to the 3D view, which is where levels are built.
     *
     * @param onChanged told the new state after each toggle, so the shell can show its 3D view tab and turn off a mode that would take
     *                  the mouse (sculpting)
     */
    public static void register(EditorActionRegistry registry, EditorState editorState, LevelSession level, Consumer<Boolean> onChanged) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(level, "level cannot be null");
        Objects.requireNonNull(onChanged, "onChanged cannot be null");
        registry.register(EditorAction.builder(TOGGLE, "Level", () -> {
                boolean on = !level.isActive();
                level.setActive(on);
                if (on && editorState.getViewportMode() != ViewportMode.VIEW_3D) {
                    editorState.setViewportMode(ViewportMode.VIEW_3D);
                }
                editorState.setStatusMessage(on
                    ? "Level editor on: pick a tool, then drag on the ground (Place and Erase: click). Alt-drag orbits."
                    : "Level editor off", false);
                onChanged.accept(on);
            })
            .menu(DefaultEditorActions.MENU_TOOLS, GROUP_LEVEL)
            .enabledWhen(() -> !editorState.isPlayModeActive())
            .build());
    }

    /**
     * Registers {@link #IMPORT_HEIGHTMAP} and {@link #EXPORT_HEIGHTMAP} (roadmap M3.5): the level ground's heights from or to a 16-bit
     * PNG, chosen in a file dialog. Import is one undo step; both say what happened in the status bar.
     *
     * @param defaultFolder where the dialogs open: the project's terrains folder
     */
    public static void registerHeightmaps(EditorActionRegistry registry, EditorState editorState, LevelSession level,
                                          EditorDialogService dialogs, File defaultFolder) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(level, "level cannot be null");
        Objects.requireNonNull(dialogs, "dialogs cannot be null");
        Objects.requireNonNull(defaultFolder, "defaultFolder cannot be null");
        BooleanSupplier hasGround = () -> !editorState.isPlayModeActive() && level.findTerrain() != null;
        registry.register(EditorAction.builder(IMPORT_HEIGHTMAP, "Import Heightmap...", () ->
                dialogs.chooseOpenFile("Import Heightmap", defaultFolder, HEIGHTMAP_FILTER, HEIGHTMAP_EXTENSION)
                    .ifPresent(level::importHeightmap))
            .menu(DefaultEditorActions.MENU_TOOLS, GROUP_LEVEL)
            .enabledWhen(hasGround)
            .build());
        registry.register(EditorAction.builder(EXPORT_HEIGHTMAP, "Export Heightmap...", () -> {
                String name = level.findTerrain() == null ? "heightmap" : level.findTerrain().getName();
                dialogs.chooseSaveFile("Export Heightmap", new File(defaultFolder, name + "." + HEIGHTMAP_EXTENSION), HEIGHTMAP_FILTER,
                    HEIGHTMAP_EXTENSION).ifPresent(level::exportHeightmap);
            })
            .menu(DefaultEditorActions.MENU_TOOLS, GROUP_LEVEL)
            .enabledWhen(hasGround)
            .build());
    }
}
