package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

/**
 * Types a data pin or graph variable can carry. Values are plain Java objects: {@link Float}, {@link Boolean},
 * {@link String}, {@link Vector3} and {@link Entity} (an unset entity input means the graph's own entity).
 */
public enum ValueType {
    FLOAT,
    BOOLEAN,
    STRING,
    VECTOR3,
    ENTITY;

    /** Value of an unconnected pin with no literal set. */
    public Object defaultValue() {
        return switch (this) {
            case FLOAT -> 0f;
            case BOOLEAN -> false;
            case STRING -> "";
            case VECTOR3 -> new Vector3();
            case ENTITY -> null;
        };
    }

    public boolean accepts(Object value) {
        return switch (this) {
            case FLOAT -> value instanceof Float;
            case BOOLEAN -> value instanceof Boolean;
            case STRING -> value instanceof String;
            case VECTOR3 -> value instanceof Vector3;
            case ENTITY -> value == null || value instanceof Entity;
        };
    }

    /** Copy safe to hand to another owner (vectors are mutable). */
    public Object copy(Object value) {
        return value instanceof Vector3 v ? new Vector3(v) : value;
    }

    /** Whether a literal of this type can be typed into a pin or stored in a file (entities cannot). */
    public boolean hasLiteral() {
        return this != ENTITY;
    }
}
