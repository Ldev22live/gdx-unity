package io.github.ldev22live.gdxunity.editor.core.action;

import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Single source of truth for editor operations. Menus, toolbars, keyboard shortcuts and the AI agent all
 * execute actions through this registry, so each operation has one implementation and one enabled rule.
 */
public class EditorActionRegistry {

    private final Map<String, EditorAction> actions = new LinkedHashMap<>();

    /**
     * Registers an action.
     *
     * @throws IllegalArgumentException if an action with the same id is already registered
     */
    public void register(EditorAction action) {
        Objects.requireNonNull(action, "action cannot be null");
        if (actions.containsKey(action.getId())) {
            throw new IllegalArgumentException("Duplicate editor action id: " + action.getId());
        }
        actions.put(action.getId(), action);
    }

    public Optional<EditorAction> find(String id) {
        return Optional.ofNullable(actions.get(id));
    }

    public Collection<EditorAction> getAll() {
        return Collections.unmodifiableCollection(actions.values());
    }

    /**
     * Executes an action if it is currently enabled.
     *
     * @return true if the action ran, false if it is disabled
     * @throws IllegalArgumentException if no action has this id
     */
    public boolean execute(String id) {
        EditorAction action = actions.get(id);
        if (action == null) {
            throw new IllegalArgumentException("Unknown editor action id: " + id);
        }
        if (!action.isEnabled()) {
            return false;
        }
        action.getHandler().run();
        return true;
    }

    /** Menu names in the order their first action was registered. */
    public List<String> getMenus() {
        Set<String> menus = new LinkedHashSet<>();
        for (EditorAction action : actions.values()) {
            if (action.getMenu() != null) {
                menus.add(action.getMenu());
            }
        }
        return new ArrayList<>(menus);
    }

    /** Actions shown in a menu, ordered by group and then by registration order. */
    public List<EditorAction> getMenuActions(String menu) {
        List<EditorAction> result = new ArrayList<>();
        for (EditorAction action : actions.values()) {
            if (Objects.equals(action.getMenu(), menu)) {
                result.add(action);
            }
        }
        // List.sort is stable, so registration order is kept within a group
        result.sort(Comparator.comparingInt(EditorAction::getGroup));
        return result;
    }

    /**
     * Registers every action that has a shortcut as a global shortcut. The shortcut runs the action through
     * {@link #execute(String)}, so disabled actions do nothing.
     */
    public void bindShortcuts(EditorKeyboardDispatcher dispatcher) {
        Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
        for (EditorAction action : actions.values()) {
            if (action.hasShortcut()) {
                String id = action.getId();
                dispatcher.registerGlobalShortcut(id, action.getKeycode(), action.getModifiers(), () -> execute(id));
            }
        }
    }
}
