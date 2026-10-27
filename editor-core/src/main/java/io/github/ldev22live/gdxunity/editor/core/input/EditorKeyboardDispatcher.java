package io.github.ldev22live.gdxunity.editor.core.input;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Headless-testable centralized keyboard shortcut dispatcher.
 * Matches keycodes and modifier masks, checks focus state, and dispatches actions predictably.
 */
public class EditorKeyboardDispatcher {

    public static final int MOD_CTRL = 1;
    public static final int MOD_SHIFT = 2;
    public static final int MOD_ALT = 4;

    public interface FocusPredicate {
        /**
         * Returns true if a text input control currently owns focus and should consume regular key events.
         */
        boolean isTextEntryFocused();
    }

    public static class Shortcut {
        private final String id;
        private final int keycode;
        private final int modifiers;
        private final Runnable action;
        private final boolean allowDuringTextEntry;

        public Shortcut(String id, int keycode, int modifiers, Runnable action, boolean allowDuringTextEntry) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
            this.keycode = keycode;
            this.modifiers = modifiers;
            this.action = Objects.requireNonNull(action, "action cannot be null");
            this.allowDuringTextEntry = allowDuringTextEntry;
        }

        public String getId() { return id; }
        public int getKeycode() { return keycode; }
        public int getModifiers() { return modifiers; }
        public Runnable getAction() { return action; }
        public boolean isAllowedDuringTextEntry() { return allowDuringTextEntry; }
    }

    private final FocusPredicate focusPredicate;
    private final Map<String, Shortcut> globalShortcuts = new LinkedHashMap<>();
    private final Map<String, Map<String, Shortcut>> workspaceShortcuts = new LinkedHashMap<>();
    private String activeWorkspaceId;

    public EditorKeyboardDispatcher() {
        this(() -> false);
    }

    public EditorKeyboardDispatcher(FocusPredicate focusPredicate) {
        this.focusPredicate = Objects.requireNonNull(focusPredicate, "focusPredicate cannot be null");
    }

    public void setActiveWorkspaceId(String activeWorkspaceId) {
        this.activeWorkspaceId = activeWorkspaceId;
    }

    public String getActiveWorkspaceId() {
        return activeWorkspaceId;
    }

    public void registerGlobalShortcut(String id, int keycode, int modifiers, Runnable action) {
        registerGlobalShortcut(id, keycode, modifiers, action, false);
    }

    public void registerGlobalShortcut(String id, int keycode, int modifiers, Runnable action, boolean allowDuringTextEntry) {
        globalShortcuts.put(id, new Shortcut(id, keycode, modifiers, action, allowDuringTextEntry));
    }

    public void unregisterGlobalShortcut(String id) {
        globalShortcuts.remove(id);
    }

    public void registerWorkspaceShortcut(String workspaceId, String id, int keycode, int modifiers, Runnable action) {
        workspaceShortcuts.computeIfAbsent(workspaceId, k -> new LinkedHashMap<>())
            .put(id, new Shortcut(id, keycode, modifiers, action, false));
    }

    public void unregisterWorkspaceShortcut(String workspaceId, String id) {
        Map<String, Shortcut> map = workspaceShortcuts.get(workspaceId);
        if (map != null) {
            map.remove(id);
        }
    }

    /**
     * Attempts to dispatch a keyboard shortcut.
     *
     * @param keycode   LibGDX keycode or virtual keycode
     * @param modifiers Bitwise OR of MOD_CTRL, MOD_SHIFT, MOD_ALT
     * @return true if a registered shortcut matched and was executed, false otherwise
     */
    public boolean dispatch(int keycode, int modifiers) {
        boolean textFocused = focusPredicate.isTextEntryFocused();

        // 1. Check active workspace shortcuts first (workspace context priority)
        if (activeWorkspaceId != null) {
            Map<String, Shortcut> wsMap = workspaceShortcuts.get(activeWorkspaceId);
            if (wsMap != null) {
                for (Shortcut s : wsMap.values()) {
                    if (s.getKeycode() == keycode && s.getModifiers() == modifiers) {
                        if (textFocused && !s.isAllowedDuringTextEntry()) {
                            continue;
                        }
                        s.getAction().run();
                        return true;
                    }
                }
            }
        }

        // 2. Check global shortcuts
        for (Shortcut s : globalShortcuts.values()) {
            if (s.getKeycode() == keycode && s.getModifiers() == modifiers) {
                if (textFocused && !s.isAllowedDuringTextEntry()) {
                    continue;
                }
                s.getAction().run();
                return true;
            }
        }

        return false;
    }

    public boolean hasGlobalShortcut(String id) {
        return globalShortcuts.containsKey(id);
    }
}
