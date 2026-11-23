package io.github.ldev22live.gdxunity.editor.core.action;

import com.badlogic.gdx.Input;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EditorActionRegistryTest {

    @Test
    void shouldExecuteEnabledActionAndSkipDisabledOne() {
        EditorActionRegistry registry = new EditorActionRegistry();
        AtomicInteger runs = new AtomicInteger();
        AtomicBoolean enabled = new AtomicBoolean(false);
        registry.register(EditorAction.builder("test.run", "Run", runs::incrementAndGet).enabledWhen(enabled::get).build());

        assertFalse(registry.execute("test.run"));
        assertEquals(0, runs.get());

        enabled.set(true);
        assertTrue(registry.execute("test.run"));
        assertEquals(1, runs.get());
    }

    @Test
    void shouldRejectDuplicateAndUnknownIds() {
        EditorActionRegistry registry = new EditorActionRegistry();
        registry.register(EditorAction.builder("a", "A", () -> {}).build());

        assertThrows(IllegalArgumentException.class, () -> registry.register(EditorAction.builder("a", "Again", () -> {}).build()));
        assertThrows(IllegalArgumentException.class, () -> registry.execute("missing"));
    }

    @Test
    void shouldOrderMenusByRegistrationAndItemsByGroup() {
        EditorActionRegistry registry = new EditorActionRegistry();
        registry.register(EditorAction.builder("edit.second", "Second", () -> {}).menu("Edit", 1).build());
        registry.register(EditorAction.builder("file.open", "Open", () -> {}).menu("File", 0).build());
        registry.register(EditorAction.builder("edit.first", "First", () -> {}).menu("Edit", 0).build());
        registry.register(EditorAction.builder("edit.third", "Third", () -> {}).menu("Edit", 1).build());
        registry.register(EditorAction.builder("hidden", "Hidden", () -> {}).build());

        assertEquals(List.of("Edit", "File"), registry.getMenus());
        List<String> editIds = registry.getMenuActions("Edit").stream().map(EditorAction::getId).toList();
        assertEquals(List.of("edit.first", "edit.second", "edit.third"), editIds);
    }

    @Test
    void shouldBindShortcutsThatRespectEnabledState() {
        EditorActionRegistry registry = new EditorActionRegistry();
        AtomicInteger runs = new AtomicInteger();
        AtomicBoolean enabled = new AtomicBoolean(true);
        registry.register(EditorAction.builder("edit.dup", "Duplicate", runs::incrementAndGet)
            .shortcut(Input.Keys.D, EditorKeyboardDispatcher.MOD_CTRL)
            .enabledWhen(enabled::get)
            .build());

        EditorKeyboardDispatcher dispatcher = new EditorKeyboardDispatcher();
        registry.bindShortcuts(dispatcher);

        assertTrue(dispatcher.dispatch(Input.Keys.D, EditorKeyboardDispatcher.MOD_CTRL));
        assertEquals(1, runs.get());

        enabled.set(false);
        dispatcher.dispatch(Input.Keys.D, EditorKeyboardDispatcher.MOD_CTRL);
        assertEquals(1, runs.get(), "Disabled action must not run from its shortcut");

        assertFalse(dispatcher.dispatch(Input.Keys.D, 0), "Shortcut requires its modifiers");
    }

    @Test
    void shouldFormatShortcutText() {
        EditorAction redo = EditorAction.builder("r", "Redo", () -> {})
            .shortcut(Input.Keys.Z, EditorKeyboardDispatcher.MOD_CTRL | EditorKeyboardDispatcher.MOD_SHIFT).build();
        EditorAction delete = EditorAction.builder("d", "Delete", () -> {}).shortcut(Input.Keys.FORWARD_DEL, 0).build();
        EditorAction none = EditorAction.builder("n", "None", () -> {}).build();

        assertEquals("Ctrl+Shift+Z", redo.getShortcutText());
        assertEquals("Delete", delete.getShortcutText());
        assertEquals("", none.getShortcutText());
        assertFalse(none.hasShortcut());
    }

    @Test
    void shouldValidateBuilderArguments() {
        assertThrows(IllegalArgumentException.class, () -> EditorAction.builder(" ", "Label", () -> {}));
        assertThrows(NullPointerException.class, () -> EditorAction.builder("id", "Label", null));
        assertThrows(IllegalArgumentException.class, () -> EditorAction.builder("id", "Label", () -> {}).shortcut(-5, 0));
    }
}
