package io.github.ldev22live.gdxunity.editor.core.action;

import com.badlogic.gdx.Input;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
            DefaultEditorActions.MENU_FILE, DefaultEditorActions.MENU_EDIT, DefaultEditorActions.MENU_CREATE,
            DefaultEditorActions.MENU_TOOLS, DefaultEditorActions.MENU_HELP), registry.getMenus());
        assertFalse(registry.getMenuActions(DefaultEditorActions.MENU_EDIT).stream()
            .anyMatch(a -> a.getId().equals(DefaultEditorActions.EDIT_REDO_ALTERNATE)), "Alternate binding is shortcut-only");
    }
}
