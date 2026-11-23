package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.Objects;

/**
 * A named value each running graph keeps for its entity, read and written by Get / Set Variable nodes.
 *
 * @param initialValue value at Begin Play; must match {@code type}
 */
public record GraphVariable(String name, ValueType type, Object initialValue) {
    public GraphVariable {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Variable name cannot be blank");
        }
        if (!type.hasLiteral()) {
            throw new IllegalArgumentException("Variables of type " + type + " are not supported");
        }
        if (initialValue == null) {
            initialValue = type.defaultValue();
        }
        if (!type.accepts(initialValue)) {
            throw new IllegalArgumentException("Initial value " + initialValue + " is not a " + type);
        }
        initialValue = type.copy(initialValue);
    }
}
