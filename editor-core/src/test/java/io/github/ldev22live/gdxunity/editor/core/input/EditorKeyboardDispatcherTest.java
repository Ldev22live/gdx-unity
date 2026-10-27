package io.github.ldev22live.gdxunity.editor.core.input;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EditorKeyboardDispatcherTest {

    @Test
    void shouldDispatchGlobalShortcutWhenModifiersMatch() {
        EditorKeyboardDispatcher dispatcher = new EditorKeyboardDispatcher();
        AtomicInteger undoCount = new AtomicInteger(0);

        // Register Ctrl+Z (Keycode 54 = Z in LibGDX)
        dispatcher.registerGlobalShortcut("undo", 54, EditorKeyboardDispatcher.MOD_CTRL, undoCount::incrementAndGet);

        // Wrong modifier -> no match
        assertFalse(dispatcher.dispatch(54, 0));
        assertEquals(0, undoCount.get());

        // Correct modifier -> match!
        assertTrue(dispatcher.dispatch(54, EditorKeyboardDispatcher.MOD_CTRL));
        assertEquals(1, undoCount.get());
    }

    @Test
    void shouldPrioritizeWorkspaceShortcutOverGlobalShortcut() {
        EditorKeyboardDispatcher dispatcher = new EditorKeyboardDispatcher();
        AtomicBoolean globalFired = new AtomicBoolean(false);
        AtomicBoolean workspaceFired = new AtomicBoolean(false);

        // Keycode 44 = Space
        dispatcher.registerGlobalShortcut("space-action", 44, 0, () -> globalFired.set(true));
        dispatcher.registerWorkspaceShortcut("audio", "space-action", 44, 0, () -> workspaceFired.set(true));

        // When in "scene" workspace: global fires
        dispatcher.setActiveWorkspaceId("scene");
        assertTrue(dispatcher.dispatch(44, 0));
        assertTrue(globalFired.get());
        assertFalse(workspaceFired.get());

        // Reset and switch to "audio" workspace: workspace fires
        globalFired.set(false);
        dispatcher.setActiveWorkspaceId("audio");
        assertTrue(dispatcher.dispatch(44, 0));
        assertFalse(globalFired.get());
        assertTrue(workspaceFired.get());
    }

    @Test
    void shouldSuppressShortcutWhenTextEntryIsFocusedUnlessExplicitlyAllowed() {
        AtomicBoolean textFocused = new AtomicBoolean(false);
        EditorKeyboardDispatcher dispatcher = new EditorKeyboardDispatcher(textFocused::get);

        AtomicInteger deleteFired = new AtomicInteger(0);
        AtomicInteger saveFired = new AtomicInteger(0);

        // Keycode 112 = Delete, normal shortcut (not allowed during text entry)
        dispatcher.registerGlobalShortcut("delete", 112, 0, deleteFired::incrementAndGet, false);

        // Keycode 47 = S, Ctrl+S is allowed during text entry
        dispatcher.registerGlobalShortcut("save", 47, EditorKeyboardDispatcher.MOD_CTRL, saveFired::incrementAndGet, true);

        // Normal state (not typing)
        textFocused.set(false);
        assertTrue(dispatcher.dispatch(112, 0));
        assertEquals(1, deleteFired.get());
        assertTrue(dispatcher.dispatch(47, EditorKeyboardDispatcher.MOD_CTRL));
        assertEquals(1, saveFired.get());

        // User is now editing a TextField in the Inspector
        textFocused.set(true);

        // Delete key should be suppressed so user doesn't delete scene entities while typing in a text field!
        assertFalse(dispatcher.dispatch(112, 0));
        assertEquals(1, deleteFired.get()); // unchanged!

        // Ctrl+S should still execute to allow saving
        assertTrue(dispatcher.dispatch(47, EditorKeyboardDispatcher.MOD_CTRL));
        assertEquals(2, saveFired.get());
    }

    @Test
    void shouldAllowUnregisteringShortcuts() {
        EditorKeyboardDispatcher dispatcher = new EditorKeyboardDispatcher();
        AtomicInteger count = new AtomicInteger(0);

        dispatcher.registerGlobalShortcut("test", 10, 0, count::incrementAndGet);
        assertTrue(dispatcher.dispatch(10, 0));
        assertEquals(1, count.get());

        dispatcher.unregisterGlobalShortcut("test");
        assertFalse(dispatcher.dispatch(10, 0));
        assertEquals(1, count.get());
    }
}
