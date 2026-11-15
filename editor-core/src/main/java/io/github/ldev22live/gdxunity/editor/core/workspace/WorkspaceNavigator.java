package io.github.ldev22live.gdxunity.editor.core.workspace;

import io.github.ldev22live.gdxunity.editor.core.EditorState;

import java.util.Objects;

/**
 * Moving between workspaces through Customize and Back to World (roadmap Appendix E2, TD-084, milestones M1b.5 and M1b.7), without any
 * UI toolkit: the editor supplies a {@link View} that shows centre tabs and runs actions. Navigating changes only what is shown. It
 * never touches the scene, the selection, the undo history or unsaved changes, so a person can leave the World and come back with
 * nothing lost.
 */
public final class WorkspaceNavigator {
    /** Sculpt's "enter sculpt mode" action, which turns the World's view into 3D Modeling. */
    public static final String SCULPT_ENTER_ACTION = CustomizeGateway.SCULPT_ENTER_ACTION;
    /** Sculpt's "exit sculpt mode" action. */
    public static final String SCULPT_EXIT_ACTION = "tools.sculpt-exit";
    /** Shown when Customize > Assets is chosen. */
    public static final String ASSETS_MESSAGE = "Assets: the Content Browser below the view. Double-click a file to open it, or drag it "
        + "into the World";

    /** What the navigator needs from the editor's screen. */
    public interface View {
        /** Title of the centre tab on screen, one of the {@code Workspace.TAB_} titles. */
        String centerTab();

        /** Shows the centre tab with this title. */
        void showCenterTab(String title);

        /** Whether sculpt mode is on, which makes the Viewport tab 3D Modeling. */
        boolean isSculptMode();

        /** Runs an editor action; false when it is not available now. */
        boolean runAction(String actionId);

        /** Opens the Content Browser, the Assets workspace. */
        void showContentBrowser();
    }

    private final EditorState editorState;
    private final View view;

    public WorkspaceNavigator(EditorState editorState, View view) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.view = Objects.requireNonNull(view, "view cannot be null");
    }

    /** The workspace on screen: the centre tab, and sculpt mode for 3D Modeling. */
    public Workspace current() {
        return Workspace.forCenterTab(view.centerTab(), view.isSculptMode());
    }

    /** Customize > a workspace. */
    public void open(Workspace.Destination destination) {
        Objects.requireNonNull(destination, "destination cannot be null");
        switch (destination.workspace()) {
            case WORLD -> backToWorld();
            case MODELING_3D -> {
                view.showCenterTab(Workspace.TAB_VIEWPORT);
                if (!view.isSculptMode() && !view.runAction(SCULPT_ENTER_ACTION)) {
                    editorState.setStatusMessage("3D Modeling is not available now" + (editorState.isPlayModeActive()
                        ? " (stop Play first)" : ""), true);
                }
            }
            case ASSETS -> {
                view.showContentBrowser();
                editorState.setStatusMessage(ASSETS_MESSAGE, false);
            }
            default -> {
                exitSculptMode();
                view.showCenterTab(destination.tab());
            }
        }
    }

    /**
     * Back to World: the scene view, or the game view while playing, with sculpt mode off. The selection, open documents, unsaved
     * changes and the camera stay as they are.
     */
    public void backToWorld() {
        exitSculptMode();
        view.showCenterTab(editorState.isPlayModeActive() ? Workspace.TAB_SCENE : Workspace.TAB_VIEWPORT);
    }

    private void exitSculptMode() {
        if (view.isSculptMode()) {
            view.runAction(SCULPT_EXIT_ACTION);
        }
    }
}
