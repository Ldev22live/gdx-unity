package io.github.ldev22live.gdxunity.editor.core.action;

import com.badlogic.gdx.Input;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DefaultEditorActionsTest {

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private EditorState editorState;
    private EditorActionRegistry registry;
    private EditorKeyboardDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        registry = new EditorActionRegistry();
        DefaultEditorActions.registerDefaults(registry, editorState);
        dispatcher = new EditorKeyboardDispatcher();
        registry.bindShortcuts(dispatcher);
    }

    @Test
    void shouldSwitchToolsWithSingleKeyShortcuts() {
        dispatcher.dispatch(Input.Keys.W, 0);
        assertEquals(ToolMode.MOVE, editorState.getToolMode());
        dispatcher.dispatch(Input.Keys.E, 0);
        assertEquals(ToolMode.ROTATE, editorState.getToolMode());
        dispatcher.dispatch(Input.Keys.R, 0);
        assertEquals(ToolMode.SCALE, editorState.getToolMode());
        dispatcher.dispatch(Input.Keys.Q, 0);
        assertEquals(ToolMode.SELECT, editorState.getToolMode());
    }

    @Test
    void shouldEnableUndoOnlyWhenHistoryHasCommands() {
        EditorAction undo = registry.find(DefaultEditorActions.EDIT_UNDO).orElseThrow();
        assertFalse(undo.isEnabled());

        assertTrue(registry.execute(DefaultEditorActions.CREATE_EMPTY_ENTITY));
        assertTrue(undo.isEnabled());

        int entityCount = editorState.getActiveScene().getAllEntities().size();
        assertTrue(registry.execute(DefaultEditorActions.EDIT_UNDO));
        assertEquals(entityCount - 1, editorState.getActiveScene().getAllEntities().size());
        assertTrue(registry.find(DefaultEditorActions.EDIT_REDO).orElseThrow().isEnabled());
    }

    @Test
    void shouldCreateEntityAndSelectIt() {
        assertTrue(registry.execute(DefaultEditorActions.CREATE_EMPTY_ENTITY));
        Entity selected = editorState.getSelectedEntity();
        assertNotNull(selected);
        assertEquals("Entity", selected.getName());
    }

    @Test
    void shouldRequireSelectionForDuplicateAndDelete() {
        assertFalse(registry.execute(DefaultEditorActions.EDIT_DELETE));
        assertFalse(registry.execute(DefaultEditorActions.EDIT_DUPLICATE));

        Entity entity = editorState.getActiveScene().createEntity("Target");
        editorState.setSelectedEntity(entity);
        assertTrue(registry.execute(DefaultEditorActions.EDIT_DELETE));
        assertNull(editorState.getActiveScene().findEntityById(entity.getId()));
    }

    @Test
    void shouldExposeMenusInStandardOrder() {
        assertEquals(List.of(
            DefaultEditorActions.MENU_EDIT, DefaultEditorActions.MENU_CREATE,
            DefaultEditorActions.MENU_TOOLS, DefaultEditorActions.MENU_HELP), registry.getMenus());
        assertFalse(registry.getMenuActions(DefaultEditorActions.MENU_EDIT).stream()
            .anyMatch(a -> a.getId().equals(DefaultEditorActions.EDIT_REDO_ALTERNATE)), "Alternate binding is shortcut-only");
    }

    @Test
    void shouldExitOnlyAfterUnsavedChangesAreResolved() {
        AtomicInteger exits = new AtomicInteger();
        StubDialogs dialogs = new StubDialogs();
        SceneFileController files = new SceneFileController(editorState, dialogs, null);
        EditorActionRegistry fileRegistry = new EditorActionRegistry();
        DefaultEditorActions.registerFileActions(fileRegistry, editorState, files, exits::incrementAndGet);

        editorState.createEntity("Unsaved");
        dialogs.answer = EditorDialogService.UnsavedChangesChoice.CANCEL;
        fileRegistry.execute(DefaultEditorActions.FILE_EXIT);
        assertEquals(0, exits.get(), "Cancel keeps the editor open");

        dialogs.answer = EditorDialogService.UnsavedChangesChoice.DISCARD;
        fileRegistry.execute(DefaultEditorActions.FILE_EXIT);
        assertEquals(1, exits.get());
        assertEquals("Ctrl+Shift+S", fileRegistry.find(DefaultEditorActions.FILE_SAVE_SCENE_AS).orElseThrow().getShortcutText());
    }

    private static class StubDialogs implements EditorDialogService {
        UnsavedChangesChoice answer = UnsavedChangesChoice.CANCEL;

        @Override
        public Optional<File> chooseOpenFile(String title, File dir, String desc, String ext) {
            return Optional.empty();
        }

        @Override
        public Optional<File> chooseSaveFile(String title, File file, String desc, String ext) {
            return Optional.empty();
        }

        @Override
        public UnsavedChangesChoice askUnsavedChanges(String documentName) {
            return answer;
        }

        @Override
        public void showError(String title, String message) {}
    }
}
