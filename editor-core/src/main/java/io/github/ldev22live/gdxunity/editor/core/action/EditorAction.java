package io.github.ldev22live.gdxunity.editor.core.action;

import com.badlogic.gdx.Input;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * A named editor operation shared by menus, toolbars, keyboard shortcuts and automation.
 *
 * <p>Actions are immutable descriptions; their enabled state is evaluated on demand through
 * {@link #isEnabled()} so menus always reflect current editor state. Create instances with
 * {@link #builder(String, String, Runnable)}.
 */
public final class EditorAction {

    /** Keycode value meaning "no keyboard shortcut". */
    public static final int NO_SHORTCUT = -1;

    private final String id;
    private final String label;
    private final String menu;
    private final int group;
    private final int keycode;
    private final int modifiers;
    private final Runnable handler;
    private final BooleanSupplier enabledCondition;

    private EditorAction(Builder builder) {
        this.id = builder.id;
        this.label = builder.label;
        this.menu = builder.menu;
        this.group = builder.group;
        this.keycode = builder.keycode;
        this.modifiers = builder.modifiers;
        this.handler = builder.handler;
        this.enabledCondition = builder.enabledCondition;
    }

    /**
     * Starts building an action.
     *
     * @param id      stable identifier, e.g. {@code "edit.undo"}; used by automation and shortcuts
     * @param label   user-facing menu text
     * @param handler code run when the action executes
     */
    public static Builder builder(String id, String label, Runnable handler) {
        return new Builder(id, label, handler);
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    /** Top-level menu this action appears in, or {@code null} if it is not shown in a menu. */
    public String getMenu() {
        return menu;
    }

    /** Group index inside the menu; menus place a separator between different groups. */
    public int getGroup() {
        return group;
    }

    public boolean hasShortcut() {
        return keycode != NO_SHORTCUT;
    }

    public int getKeycode() {
        return keycode;
    }

    /** Bitwise OR of {@link EditorKeyboardDispatcher#MOD_CTRL}, {@code MOD_SHIFT} and {@code MOD_ALT}. */
    public int getModifiers() {
        return modifiers;
    }

    public boolean isEnabled() {
        return enabledCondition.getAsBoolean();
    }

    Runnable getHandler() {
        return handler;
    }

    /** Human-readable shortcut such as {@code "Ctrl+Shift+Z"}, or an empty string if there is none. */
    public String getShortcutText() {
        if (!hasShortcut()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        if ((modifiers & EditorKeyboardDispatcher.MOD_CTRL) != 0) {
            text.append("Ctrl+");
        }
        if ((modifiers & EditorKeyboardDispatcher.MOD_SHIFT) != 0) {
            text.append("Shift+");
        }
        if ((modifiers & EditorKeyboardDispatcher.MOD_ALT) != 0) {
            text.append("Alt+");
        }
        text.append(keycode == Input.Keys.FORWARD_DEL ? "Delete" : Input.Keys.toString(keycode));
        return text.toString();
    }

    /** Fluent builder for {@link EditorAction}. */
    public static final class Builder {
        private final String id;
        private final String label;
        private final Runnable handler;
        private String menu;
        private int group;
        private int keycode = NO_SHORTCUT;
        private int modifiers;
        private BooleanSupplier enabledCondition = () -> true;

        private Builder(String id, String label, Runnable handler) {
            this.id = requireText(id, "id");
            this.label = requireText(label, "label");
            this.handler = Objects.requireNonNull(handler, "handler cannot be null");
        }

        /** Places the action in a top-level menu, in the given separator group. */
        public Builder menu(String menu, int group) {
            this.menu = requireText(menu, "menu");
            this.group = group;
            return this;
        }

        public Builder shortcut(int keycode, int modifiers) {
            if (keycode < 0) {
                throw new IllegalArgumentException("keycode must be non-negative, got: " + keycode);
            }
            this.keycode = keycode;
            this.modifiers = modifiers;
            return this;
        }

        public Builder enabledWhen(BooleanSupplier condition) {
            this.enabledCondition = Objects.requireNonNull(condition, "condition cannot be null");
            return this;
        }

        public EditorAction build() {
            return new EditorAction(this);
        }

        private static String requireText(String value, String name) {
            Objects.requireNonNull(value, name + " cannot be null");
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " cannot be blank");
            }
            return value;
        }
    }
}
