package io.github.ldev22live.gdxunity.editor.core.workspace;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

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

        /** Whether sculpt mode is on, which makes the World tab 3D Modeling. */
        boolean isSculptMode();

        /** Runs an editor action; false when it is not available now. */
        boolean runAction(String actionId);

        /** Opens the Content Browser, the Assets workspace. */
        void showContentBrowser();

        /** Points the view's camera at the selected entity, as the Frame button does. */
        void frameSelection();
    }

    private final EditorState editorState;
    private final View view;
    /** The view the World showed before Edit 3D Model switched it to 3D, restored by Back to World; null when it switched nothing. */
    private ViewportMode viewBeforeModeling;

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
                view.showCenterTab(Workspace.TAB_WORLD);
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
                leave3DModeling();
                view.showCenterTab(destination.tab());
            }
        }
    }

    /**
     * Customize > Edit 3D Model (roadmap M3c.4): opens 3D Modeling on {@code entity} in context. The World's view turns to 3D (Back to
     * World turns it back), 3D Modeling opens, the camera frames the object and the status bar gives the capability notice's summary;
     * the workspace shows the whole notice ({@link ModelingHandOff#noticeFor}). The scene, the selection and the undo history are not
     * touched. Refused, with the reason in the status bar, during Play, for an object with no model or shape, or when 3D Modeling
     * cannot open.
     *
     * @return whether 3D Modeling is now on screen with {@code entity}
     */
    public boolean editModel(Entity entity) {
        if (editorState.isPlayModeActive()) {
            editorState.setStatusMessage(ModelingHandOff.EDIT_3D_MODEL + " is not available while the game plays (stop Play first)", true);
            return false;
        }
        if (!ModelingHandOff.offersEdit3DModel(entity)) {
            editorState.setStatusMessage(ModelingHandOff.noticeFor(entity).summary(), true);
            return false;
        }
        ViewportMode before = editorState.getViewportMode();
        view.showCenterTab(Workspace.TAB_WORLD);
        editorState.setViewportMode(ViewportMode.VIEW_3D);
        if (!view.isSculptMode() && !view.runAction(SCULPT_ENTER_ACTION)) {
            editorState.setViewportMode(before);
            editorState.setStatusMessage("3D Modeling is not available now", true);
            return false;
        }
        if (before != ViewportMode.VIEW_3D && viewBeforeModeling == null) {
            viewBeforeModeling = before;
        }
        view.frameSelection();
        // Set last: entering sculpt mode and framing write their own status lines
        editorState.setStatusMessage("3D Modeling: " + entity.getName() + ". " + ModelingHandOff.noticeFor(entity).summary(), false);
        return true;
    }

    /**
     * Back to World: the scene view, or the game view while playing, with sculpt mode off. The selection, open documents, unsaved
     * changes and the camera stay as they are; a 2D view that Edit 3D Model turned to 3D is turned back.
     */
    public void backToWorld() {
        leave3DModeling();
        view.showCenterTab(editorState.isPlayModeActive() ? Workspace.TAB_SCENE : Workspace.TAB_WORLD);
    }

    /**
     * Leaves 3D Modeling if it is on screen, turning back a view that Edit 3D Model turned to 3D. When 3D Modeling was already left
     * another way (the Sculpt toggle), the view the person now has is kept and the saved one forgotten.
     */
    private void leave3DModeling() {
        boolean wasModeling = view.isSculptMode();
        if (wasModeling) {
            view.runAction(SCULPT_EXIT_ACTION);
        }
        if (wasModeling && viewBeforeModeling != null && editorState.getViewportMode() == ViewportMode.VIEW_3D) {
            editorState.setViewportMode(viewBeforeModeling);
        }
        viewBeforeModeling = null;
    }
}
