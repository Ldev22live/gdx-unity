package io.github.ldev22live.gdxunity.editor.core.workspace;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.core.command.RenameEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M1b.5, automated part: leaving the World through Customize and coming back loses nothing. The navigator is the one the editor
 * uses ({@code EditorApplication} delegates to it); the screen is a recording stand-in. What a person must still check by hand is in
 * {@code docs/reports/r1b-outstanding-work-reconciliation.md}.
 */
class WorldFirstNavigationTest {
    @TempDir
    Path folder;

    private EditorState editorState;
    private FakeView view;
    private WorkspaceNavigator navigator;
    private Entity hero;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        view = new FakeView();
        navigator = new WorkspaceNavigator(editorState, view);
        hero = editorState.createEntity("Hero");
        editorState.setSelectedEntity(hero);
        editorState.executeCommand(new RenameEntityCommand(editorState, hero.getId(), "Hero", "Knight"));
    }

    /** A screen that shows tabs and knows sculpt mode, as the editor's does. */
    private static final class FakeView implements WorkspaceNavigator.View {
        String tab = Workspace.TAB_WORLD;
        boolean sculpt;
        boolean contentBrowser;
        boolean refuseActions;
        int framed;
        int cameraPosition = 17;
        final List<String> actions = new ArrayList<>();

        @Override
        public String centerTab() {
            return tab;
        }

        @Override
        public void showCenterTab(String title) {
            tab = title;
        }

        @Override
        public boolean isSculptMode() {
            return sculpt;
        }

        @Override
        public boolean runAction(String actionId) {
            actions.add(actionId);
            if (refuseActions) {
                return false;
            }
            if (actionId.equals(WorkspaceNavigator.SCULPT_ENTER_ACTION)) {
                sculpt = true;
            } else if (actionId.equals(WorkspaceNavigator.SCULPT_EXIT_ACTION)) {
                sculpt = false;
            }
            return true;
        }

        @Override
        public void showContentBrowser() {
            contentBrowser = true;
        }

        @Override
        public Runnable captureWorldCamera() {
            int before = cameraPosition;
            return () -> cameraPosition = before;
        }

        @Override
        public void frameSelection() {
            framed++;
            cameraPosition = 0;
        }
    }

    @Test
    void shouldOpenEveryWorkspaceAndComeBackToTheWorldWithTheSceneSelectionAndHistoryUnchanged() {
        Scene scene = editorState.getActiveScene();
        int undoSteps = editorState.getCommandHistory().getUndoCount();

        for (Workspace.Destination destination : Workspace.destinations()) {
            navigator.open(destination);
            if (destination.workspace() == Workspace.ASSETS) {
                assertEquals(WorkspaceNavigator.ASSETS_MESSAGE, editorState.getStatusMessage(), "Assets says where the files are");
            } else {
                assertEquals(destination.workspace(), navigator.current(), destination.label());
            }
            assertSame(hero, editorState.getSelectedEntity(), "the selection survives " + destination.label());
            assertSame(scene, editorState.getActiveScene());
            assertEquals(undoSteps, editorState.getCommandHistory().getUndoCount(), "navigating is never an undo step");

            navigator.backToWorld();
            assertEquals(Workspace.WORLD, navigator.current(), "Back to World from " + destination.label());
            assertFalse(view.sculpt, "Back to World leaves sculpt mode");
        }
        assertTrue(view.contentBrowser, "Assets opens the Content Browser");

        assertTrue(editorState.undo(), "the edit made before leaving the World still undoes");
        assertEquals("Hero", hero.getName());
        assertTrue(editorState.redo());
        assertEquals("Knight", hero.getName());
    }

    @Test
    void shouldLeaveSculptModeWhenGoingFrom3DModelingToAnotherWorkspace() {
        navigator.open(destination(Workspace.MODELING_3D));
        assertEquals(Workspace.MODELING_3D, navigator.current());

        navigator.open(destination(Workspace.LOGIC));

        assertEquals(Workspace.LOGIC, navigator.current());
        assertEquals(List.of(WorkspaceNavigator.SCULPT_ENTER_ACTION, WorkspaceNavigator.SCULPT_EXIT_ACTION), view.actions);
    }

    @Test
    void shouldSayWhy3DModelingDidNotOpen() {
        view.refuseActions = true;

        navigator.open(destination(Workspace.MODELING_3D));

        assertEquals("3D Modeling is not available now", editorState.getStatusMessage());
        assertTrue(editorState.isStatusError());
        assertEquals(Workspace.WORLD, navigator.current(), "the view stays the World, with sculpt mode off");
    }

    @Test
    void shouldGoBackToTheGameViewWhilePlaying() {
        editorState.setPlayModeController(new PlayModeController(editorState));
        assertTrue(editorState.getPlayModeController().play());
        navigator.open(destination(Workspace.ANIMATION));

        navigator.backToWorld();

        assertEquals(Workspace.TAB_SCENE, view.tab, "during Play the World is the game view (Appendix E3)");
        assertEquals(Workspace.WORLD, navigator.current());
        editorState.getPlayModeController().stop();
        assertEquals("Knight", hero.getName(), "Stop restores the edited scene");
    }

    @Test
    void shouldKeepUnsavedChangesAcrossWorkspacesAndSaveAndReopenThemAfterwards() {
        RecordingDialogs dialogs = new RecordingDialogs();
        SceneFileController files = new SceneFileController(editorState, dialogs, folder.toFile());
        editorState.executeCommand(new RenameEntityCommand(editorState, hero.getId(), "Knight", "Paladin"));
        assertTrue(files.isDirty());

        for (Workspace.Destination destination : Workspace.destinations()) {
            navigator.open(destination);
        }
        navigator.backToWorld();

        assertTrue(files.isDirty(), "navigating neither saves nor discards");
        assertEquals(0, dialogs.unsavedQuestions, "and asks nothing");
        dialogs.saveAs = folder.resolve("journey.json").toFile();
        assertTrue(files.save());
        assertFalse(files.isDirty());
        assertTrue(files.openFile(dialogs.saveAs));
        assertTrue(editorState.getActiveScene().findEntityByName("Paladin") != null, "the edit made before navigating was saved");
    }

    @Test
    void shouldKeepPanelChoicesThroughSaveAndReloadOfTheLayout() {
        WorkspaceLayoutSerializer serializer = new WorkspaceLayoutSerializer();
        WorkspaceLayoutConfig config = serializer.createDefaultConfig();
        PanelLayout panels = new PanelLayout(config);
        EditorActionRegistry registry = new EditorActionRegistry();
        PanelLayout.registerActions(registry, editorState, panels);

        assertTrue(registry.execute(PanelLayout.TOGGLE_OUTLINER));
        assertTrue(registry.execute(PanelLayout.TOGGLE_BOTTOM_PANEL));
        while (panels.getDetailsMode() != PanelLayout.DetailsMode.HIDDEN) {
            registry.execute(PanelLayout.CYCLE_DETAILS);
        }
        navigator.open(destination(Workspace.AUDIO_MUSIC));
        navigator.backToWorld();
        PanelLayout reloaded = new PanelLayout(serializer.deserialize(serializer.serialize(config)));

        assertEquals(panels.isOutlinerShown(), reloaded.isOutlinerShown());
        assertEquals(panels.isBottomPanelShown(), reloaded.isBottomPanelShown());
        assertEquals(PanelLayout.DetailsMode.HIDDEN, reloaded.getDetailsMode());
        assertTrue(reloaded.revealDetails(), "Customize > Edit Material shows a hidden Details again");
        assertEquals(PanelLayout.DetailsMode.WITH_SELECTION, reloaded.getDetailsMode());
        assertFalse(reloaded.revealDetails(), "and leaves a shown Details alone");
    }

    /**
     * Roadmap M3c.4: Edit 3D Model hands a house to 3D Modeling in context (the 3D view, the house framed, the notice in the status
     * bar), touching neither the scene, the selection nor the undo history, and Back to World turns a 2D view back.
     */
    @Test
    void shouldOpenModelingOnPrefabRootAndKeepItsSelection() {
        Entity house = editorState.createEntity("House");
        house.addComponent(new PrefabInstanceComponent("prefabs/House.prefab", UUID.randomUUID(), true));
        Entity wall = editorState.createEntity("Wall");
        wall.addComponent(new ModelComponent("models/wall.glb"));
        wall.getTransform().setParent(house.getTransform());
        editorState.setSelectedEntity(house);
        int undoSteps = editorState.getCommandHistory().getUndoCount();

        var offers = CustomizeGateway.editSelected(house, editorState.getSerializer().getRegistry());
        assertTrue(offers.stream().anyMatch(offer -> offer.kind() == CustomizeGateway.Kind.OPEN_MODELING));
        assertFalse(offers.stream().anyMatch(offer -> CustomizeGateway.SCULPT_FROM_SELECTED_ACTION.equals(offer.actionId())));
        assertTrue(navigator.editModel(house));
        assertEquals(Workspace.MODELING_3D, navigator.current());
        assertEquals(1, view.framed);
        assertTrue(editorState.getStatusMessage().contains("Prefab mesh sources"));
        navigator.backToWorld();

        assertEquals(Workspace.WORLD, navigator.current());
        assertSame(house, editorState.getSelectedEntity());
        assertSame(house.getTransform(), wall.getTransform().getParent());
        assertEquals(undoSteps, editorState.getCommandHistory().getUndoCount());
    }

    @Test
    void shouldOpen3DModelingOnTheSelectedHouseInContextAndComeBackToTheSameWorld() {
        Entity house = editorState.createEntity("House");
        house.addComponent(new ModelComponent("models/house.glb"));
        editorState.setSelectedEntity(house);
        editorState.setViewportMode(ViewportMode.VIEW_2D);
        int undoSteps = editorState.getCommandHistory().getUndoCount();

        assertTrue(navigator.editModel(house));

        assertEquals(Workspace.MODELING_3D, navigator.current(), "3D Modeling is on screen");
        assertEquals(ViewportMode.VIEW_3D, editorState.getViewportMode(), "a 3D model is looked at in the 3D view");
        assertEquals(1, view.framed, "the camera frames the house");
        assertSame(house, editorState.getSelectedEntity(), "the house stays selected");
        assertEquals(undoSteps, editorState.getCommandHistory().getUndoCount(), "opening 3D Modeling is never an undo step");
        assertEquals(1, editorState.getActiveScene().getAllEntities().stream().filter(e -> e.getName().startsWith("House")).count(),
            "no copy is made: sculpting a copy is its own, separate choice");
        assertEquals("3D Modeling: House. " + ModelingHandOff.noticeFor(house).summary(), editorState.getStatusMessage());
        assertFalse(editorState.isStatusError());

        navigator.backToWorld();

        assertEquals(Workspace.WORLD, navigator.current());
        assertEquals(17, view.cameraPosition, "Back to World restores the camera before the model was framed");
        assertEquals(ViewportMode.VIEW_2D, editorState.getViewportMode(), "Back to World turns the view back to what it was");
        assertSame(house, editorState.getSelectedEntity());
    }

    @Test
    void shouldKeepThe3DViewWhenItWasAlready3DAndRestoreItWhenGoingToAnotherWorkspace() {
        Entity house = editorState.createEntity("House");
        house.addComponent(new ModelComponent("models/house.glb"));
        editorState.setViewportMode(ViewportMode.VIEW_3D);
        assertTrue(navigator.editModel(house));
        navigator.backToWorld();
        assertEquals(ViewportMode.VIEW_3D, editorState.getViewportMode());

        editorState.setViewportMode(ViewportMode.VIEW_2D);
        assertTrue(navigator.editModel(house));
        assertTrue(navigator.editModel(house), "choosing it again while there changes nothing");
        navigator.open(destination(Workspace.LOGIC));
        assertEquals(ViewportMode.VIEW_2D, editorState.getViewportMode(), "leaving 3D Modeling for any workspace turns the view back");
        assertFalse(view.sculpt);

        assertTrue(navigator.editModel(house));
        view.sculpt = false; // the person leaves 3D Modeling with the Sculpt toggle (F4), keeping the 3D view
        navigator.open(destination(Workspace.ANIMATION));
        navigator.backToWorld();
        assertEquals(ViewportMode.VIEW_3D, editorState.getViewportMode(), "a later, unrelated Back to World does not flip the view");
    }

    @Test
    void shouldRefuseEdit3DModelWithTheReasonForNoMeshDuringPlayAndWhen3DModelingCannotOpen() {
        assertFalse(navigator.editModel(hero), "the Knight has no model or shape");
        assertEquals("Knight has no 3D mesh. Knight has no model, shape or sculpt to work on here. Go Back to World, or select a model, "
            + "shape or sculpt.", editorState.getStatusMessage());
        assertTrue(editorState.isStatusError());
        assertFalse(navigator.editModel(null));
        assertEquals(Workspace.WORLD, navigator.current());

        Entity house = editorState.createEntity("House");
        house.addComponent(new ModelComponent("models/house.glb"));
        editorState.setViewportMode(ViewportMode.VIEW_2D);
        view.refuseActions = true;
        assertFalse(navigator.editModel(house));
        assertEquals("3D Modeling is not available now", editorState.getStatusMessage());
        assertEquals(ViewportMode.VIEW_2D, editorState.getViewportMode(), "a refused hand-off leaves the view as it was");
        assertEquals(0, view.framed);
    }

    @Test
    void shouldRefuseEdit3DModelDuringPlay() {
        Entity house = new Entity("House"); // not in the played scene, whose model file would not exist here
        house.addComponent(new ModelComponent("models/house.glb"));
        editorState.setPlayModeController(new PlayModeController(editorState));
        assertTrue(editorState.getPlayModeController().play());

        assertFalse(navigator.editModel(house));

        assertEquals("Edit 3D Model is not available while the game plays (stop Play first)", editorState.getStatusMessage());
        assertEquals(List.of(), view.actions, "3D Modeling was not entered");
        editorState.getPlayModeController().stop();
    }

    private static Workspace.Destination destination(Workspace workspace) {
        return Workspace.destinations().stream().filter(d -> d.workspace() == workspace).findFirst().orElseThrow();
    }

    private static final class RecordingDialogs implements EditorDialogService {
        File saveAs;
        int unsavedQuestions;

        @Override
        public Optional<File> chooseOpenFile(String title, File initialDirectory, String filterDescription, String extension) {
            return Optional.empty();
        }

        @Override
        public Optional<File> chooseSaveFile(String title, File suggestedFile, String filterDescription, String extension) {
            return Optional.ofNullable(saveAs);
        }

        @Override
        public UnsavedChangesChoice askUnsavedChanges(String documentName) {
            unsavedQuestions++;
            return UnsavedChangesChoice.CANCEL;
        }

        @Override
        public void showError(String title, String message) {
            throw new AssertionError(title + ": " + message);
        }
    }
}
