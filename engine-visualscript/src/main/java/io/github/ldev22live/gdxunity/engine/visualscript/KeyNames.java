package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.Input;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Keys by the names of libGDX's {@link Input.Keys} constants ({@code A}, {@code LEFT}, {@code SPACE}, {@code SHIFT_LEFT}), as the Key
 * Down and Input Axis nodes take them (TD-060). Several keys are separated by commas; case and spaces around a name do not matter.
 */
public final class KeyNames {
    /** Constants of {@link Input.Keys} that are not keys. */
    private static final List<String> NOT_KEYS = List.of("ANY_KEY", "UNKNOWN", "MAX_KEYCODE");
    private static final Map<String, Integer> CODES = readConstants();

    private KeyNames() {
    }

    private static Map<String, Integer> readConstants() {
        Map<String, Integer> codes = new HashMap<>();
        for (Field field : Input.Keys.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class && !NOT_KEYS.contains(field.getName())) {
                try {
                    codes.put(field.getName(), field.getInt(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot read Input.Keys." + field.getName(), e);
                }
            }
        }
        return codes;
    }

    /**
     * The key codes named by a comma separated list.
     *
     * @throws IllegalArgumentException for a name that is not a key, naming it
     */
    public static List<Integer> parse(String names) {
        Objects.requireNonNull(names, "names cannot be null");
        List<Integer> codes = new ArrayList<>();
        for (String raw : names.split(",")) {
            String name = raw.trim().toUpperCase(Locale.ROOT);
            Integer code = CODES.get(name);
            if (code == null) {
                throw new IllegalArgumentException("Unknown key '" + raw.trim() + "'. Use libGDX key names such as A, LEFT, SPACE or SHIFT_LEFT");
            }
            codes.add(code);
        }
        return codes;
    }

    /** Keys other than letters, digits and function keys, by libGDX's name, and the {@code KeyboardEvent.code} the browser reports. */
    private static final Map<String, String> BROWSER_CODES = Map.ofEntries(
        Map.entry("UP", "ArrowUp"), Map.entry("DOWN", "ArrowDown"), Map.entry("LEFT", "ArrowLeft"), Map.entry("RIGHT", "ArrowRight"),
        Map.entry("SPACE", "Space"), Map.entry("ENTER", "Enter"), Map.entry("ESCAPE", "Escape"), Map.entry("TAB", "Tab"),
        Map.entry("BACKSPACE", "Backspace"), Map.entry("SHIFT_LEFT", "ShiftLeft"), Map.entry("SHIFT_RIGHT", "ShiftRight"),
        Map.entry("CONTROL_LEFT", "ControlLeft"), Map.entry("CONTROL_RIGHT", "ControlRight"), Map.entry("ALT_LEFT", "AltLeft"),
        Map.entry("ALT_RIGHT", "AltRight"));

    /**
     * The browser's key codes ({@code KeyboardEvent.code}) for a comma separated list of key names, for TypeScript code. The web player
     * maps letters, NUM_0 to NUM_9, F1 to F12, the arrows, Space, Enter, Escape, Tab, Backspace and the left and right Shift, Control
     * and Alt keys; the desktop knows more.
     *
     * @throws IllegalArgumentException for a name that is not a key, or has no browser equivalent here
     */
    public static List<String> browserCodes(String names) {
        Objects.requireNonNull(names, "names cannot be null");
        parse(names);
        List<String> codes = new ArrayList<>();
        for (String raw : names.split(",")) {
            String name = raw.trim().toUpperCase(Locale.ROOT);
            String code;
            if (name.matches("[A-Z]")) {
                code = "Key" + name;
            } else if (name.matches("NUM_[0-9]")) {
                code = "Digit" + name.charAt(4);
            } else if (name.matches("F([1-9]|1[0-2])")) {
                code = name;
            } else {
                code = BROWSER_CODES.get(name);
            }
            if (code == null) {
                throw new IllegalArgumentException("The key '" + raw.trim() + "' has no equivalent in the web player");
            }
            codes.add(code);
        }
        return codes;
    }

    /** The comma separated names as {@code Input.Keys.X} constants, for generated code. */
    public static List<String> constants(String names) {
        Objects.requireNonNull(names, "names cannot be null");
        parse(names);
        List<String> constants = new ArrayList<>();
        for (String raw : names.split(",")) {
            constants.add("Input.Keys." + raw.trim().toUpperCase(Locale.ROOT));
        }
        return constants;
    }
}
