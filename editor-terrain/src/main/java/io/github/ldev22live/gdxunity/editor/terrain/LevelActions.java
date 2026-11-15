package io.github.ldev22live.gdxunity.editor.terrain;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorAction;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;

import java.util.Objects;
import java.util.function.Consumer;

/** The level editor's editor action (TD-083): the Level button in the top bar and the Tools menu. */
public final class LevelActions {
    /** Turns the level editor on or off. */
    public static final String TOGGLE = "tools.level-toggle";

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
}
