package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One typed input or output of a graph function, or one parameter of a custom event (TD-070). The name is also the id of the pin
 * that carries it, so it is a plain identifier and never one of the execution pin ids.
 */
public record GraphParam(String name, ValueType type) {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> RESERVED_PIN_IDS = Set.of(PinDef.EXEC_IN, PinDef.EXEC_OUT);

    public GraphParam {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Not a usable parameter name: '" + name
                + "' (letters, digits and underscore, not starting with a digit, and not '" + PinDef.EXEC_IN + "' or '" + PinDef.EXEC_OUT + "')");
        }
        if (type == ValueType.OBJECT) {
            throw new IllegalArgumentException("Parameter " + name + " cannot be an object: use a Number, Whole Number, Boolean, Text, Vector or Entity");
        }
    }

    /** Whether {@code name} could be a parameter, function or event name. */
    public static boolean isValidName(String name) {
        return name != null && IDENTIFIER.matcher(name).matches() && !RESERVED_PIN_IDS.contains(name);
    }

    PinDef asInputPin() {
        return PinDef.in(name, name, type);
    }

    PinDef asOutputPin() {
        return PinDef.out(name, name, type);
    }
}
