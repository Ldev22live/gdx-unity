package io.github.ldev22live.gdxunity.engine.scene.play;

import com.badlogic.gdx.Input.Keys;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Which keys trigger each {@link InputAction} (TD-088). Immutable; every action has at least one key. Remapping keys in the editor
 * is not part of TD-088.
 */
public final class InputMapping {
    private static final InputMapping DEFAULTS = new InputMapping(defaultKeys());

    private final Map<InputAction, int[]> keys;

    private InputMapping(Map<InputAction, int[]> keys) {
        EnumMap<InputAction, int[]> copy = new EnumMap<>(InputAction.class);
        for (InputAction action : InputAction.values()) {
            int[] actionKeys = keys.get(action);
            if (actionKeys == null || actionKeys.length == 0) {
                throw new IllegalArgumentException("Input action " + action + " has no key");
            }
            copy.put(action, actionKeys.clone());
        }
        this.keys = copy;
    }

    /** WASD and the arrows move, Space jumps, Q and E turn, F or the left Ctrl key fires. */
    public static InputMapping defaults() {
        return DEFAULTS;
    }

    /** A copy of this mapping in which {@code action} is triggered by {@code keyCodes} (LibGDX key codes) instead. */
    public InputMapping with(InputAction action, int... keyCodes) {
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(keyCodes, "keyCodes cannot be null");
        EnumMap<InputAction, int[]> changed = new EnumMap<>(keys);
        changed.put(action, keyCodes);
        return new InputMapping(changed);
    }

    /** The keys of an action, as a copy. */
    public int[] keysOf(InputAction action) {
        return keys.get(Objects.requireNonNull(action, "action cannot be null")).clone();
    }

    /** Whether any key of {@code action} is held down in {@code input}. */
    public boolean isActive(InputAction action, InputSource input) {
        Objects.requireNonNull(input, "input cannot be null");
        for (int key : keys.get(Objects.requireNonNull(action, "action cannot be null"))) {
            if (input.isPressed(key)) {
                return true;
            }
        }
        return false;
    }

    private static Map<InputAction, int[]> defaultKeys() {
        EnumMap<InputAction, int[]> keys = new EnumMap<>(InputAction.class);
        keys.put(InputAction.MOVE_LEFT, new int[] {Keys.A, Keys.LEFT});
        keys.put(InputAction.MOVE_RIGHT, new int[] {Keys.D, Keys.RIGHT});
        keys.put(InputAction.MOVE_FORWARD, new int[] {Keys.W, Keys.UP});
        keys.put(InputAction.MOVE_BACK, new int[] {Keys.S, Keys.DOWN});
        keys.put(InputAction.JUMP, new int[] {Keys.SPACE});
        keys.put(InputAction.TURN_LEFT, new int[] {Keys.Q});
        keys.put(InputAction.TURN_RIGHT, new int[] {Keys.E});
        keys.put(InputAction.FIRE, new int[] {Keys.F, Keys.CONTROL_LEFT});
        return keys;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof InputMapping mapping)) {
            return false;
        }
        for (InputAction action : InputAction.values()) {
            if (!Arrays.equals(keys.get(action), mapping.keys.get(action))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (InputAction action : InputAction.values()) {
            hash = 31 * hash + Arrays.hashCode(keys.get(action));
        }
        return hash;
    }
}
