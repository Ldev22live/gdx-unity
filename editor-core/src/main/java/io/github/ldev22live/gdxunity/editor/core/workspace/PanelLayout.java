package io.github.ldev22live.gdxunity.editor.core.workspace;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorAction;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Which of the editor's side and bottom panels are open (roadmap Appendix E1, milestones M1b.2 and M1b.4, TD-085). The World layout
 * has no permanent hierarchy, inspector or asset browser: the Outliner and the bottom panel (Content Browser and Output Log) start
 * closed, and Details shows while something is selected. Each can be opened at any time and the choice is saved with the layout
 * ({@link WorkspaceLayoutConfig}), so nothing becomes unreachable.
 */
public final class PanelLayout {
    /** Opens or closes the Outliner. */
    public static final String TOGGLE_OUTLINER = "window.toggle-outliner";
    /** Steps Details through {@link DetailsMode}. */
    public static final String CYCLE_DETAILS = "window.cycle-details";
    /** Opens or closes the bottom panel (Content Browser and Output Log). */
    public static final String TOGGLE_BOTTOM_PANEL = "window.toggle-bottom-panel";
    private static final int GROUP_PANELS = 3;

    /** When Details shows. */
    public enum DetailsMode {
        /** While an entity is selected (selection-aware, Appendix E1 and M1b.4). */
        WITH_SELECTION("with selection"),
        ALWAYS("always"),
        HIDDEN("hidden");

        private final String label;

        DetailsMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** The mode after this one: with selection, always, hidden, and round again. */
        public DetailsMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        /** The mode with this name, or empty for null or an unknown name. */
        public static Optional<DetailsMode> parse(String name) {
            if (name == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    private final WorkspaceLayoutConfig config;

    /** Reads and writes the panel state of {@code config}, which is what the editor saves. */
    public PanelLayout(WorkspaceLayoutConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public boolean isOutlinerShown() {
        return config.isOutlinerShown();
    }

    public boolean isBottomPanelShown() {
        return !config.isBottomPanelCollapsed();
    }

    public DetailsMode getDetailsMode() {
        return DetailsMode.parse(config.getDetailsMode()).orElse(DetailsMode.WITH_SELECTION);
    }

    /** Whether Details is open now. */
    public boolean isDetailsShown(boolean somethingSelected) {
        return switch (getDetailsMode()) {
            case WITH_SELECTION -> somethingSelected;
            case ALWAYS -> true;
            case HIDDEN -> false;
        };
    }

    public void toggleOutliner() {
        config.setOutlinerShown(!config.isOutlinerShown());
    }

    public void toggleBottomPanel() {
        config.setBottomPanelCollapsed(!config.isBottomPanelCollapsed());
    }

    /**
     * Makes sure Details shows for a selection, for an action that leads there (Customize > Edit Material, M1b.4): a hidden Details
     * goes back to "with selection"; "always" is kept.
     *
     * @return whether the mode changed
     */
    public boolean revealDetails() {
        if (getDetailsMode() != DetailsMode.HIDDEN) {
            return false;
        }
        config.setDetailsMode(DetailsMode.WITH_SELECTION.name());
        return true;
    }

    /** Steps Details to its next mode and returns it. */
    public DetailsMode cycleDetails() {
        DetailsMode next = getDetailsMode().next();
        config.setDetailsMode(next.name());
        return next;
    }

    /**
     * Registers the three panel actions in the Window menu. Each reports the new state in the status bar. They change the editor's
     * layout, not the scene, so they are not undo steps.
     */
    public static void registerActions(EditorActionRegistry registry, EditorState editorState, PanelLayout panels) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(panels, "panels cannot be null");
        registry.register(EditorAction.builder(TOGGLE_OUTLINER, "Outliner", () -> {
                panels.toggleOutliner();
                editorState.setStatusMessage("Outliner " + (panels.isOutlinerShown() ? "open" : "closed"), false);
            })
            .menu(DefaultEditorActions.MENU_WINDOW, GROUP_PANELS)
            .build());
        registry.register(EditorAction.builder(CYCLE_DETAILS, "Details", () -> {
                DetailsMode mode = panels.cycleDetails();
                editorState.setStatusMessage("Details: " + mode.getLabel(), false);
            })
            .menu(DefaultEditorActions.MENU_WINDOW, GROUP_PANELS)
            .build());
        registry.register(EditorAction.builder(TOGGLE_BOTTOM_PANEL, "Content Browser and Log", () -> {
                panels.toggleBottomPanel();
                editorState.setStatusMessage("Content Browser and Output Log " + (panels.isBottomPanelShown() ? "open" : "closed"), false);
            })
            .menu(DefaultEditorActions.MENU_WINDOW, GROUP_PANELS)
            .build());
    }
}
