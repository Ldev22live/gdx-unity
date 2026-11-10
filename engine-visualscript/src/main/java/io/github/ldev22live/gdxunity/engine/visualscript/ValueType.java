package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

/**
 * Types a data pin or graph variable can carry. Values are plain Java objects: {@link Float}, {@link Integer}, {@link Boolean},
 * {@link String}, {@link Vector3}, {@link Entity} (an unset entity input means the graph's own entity) and, for pins made from
 * Java methods (TD-060), any other object ({@link #OBJECT}, whose pin names its class, see {@link PinDef#valueClass()}).
 */
public enum ValueType {
    FLOAT,
    BOOLEAN,
    STRING,
    VECTOR3,
    ENTITY,
    /** Whole numbers; added with Java method nodes (TD-060). Reads as a {@link #FLOAT} where a float is wanted, not the other way. */
    INTEGER,
    /** Any other Java object, null by default; method/component nodes use typed pins, with no editable literal. */
    OBJECT;

    /** Value of an unconnected pin with no literal set. */
    public Object defaultValue() {
        return switch (this) {
            case FLOAT -> 0f;
            case BOOLEAN -> false;
            case STRING -> "";
            case VECTOR3 -> new Vector3();
            case ENTITY, OBJECT -> null;
            case INTEGER -> 0;
        };
    }

    public boolean accepts(Object value) {
        return switch (this) {
            case FLOAT -> value instanceof Float;
            case BOOLEAN -> value instanceof Boolean;
            case STRING -> value instanceof String;
            case VECTOR3 -> value instanceof Vector3;
            case ENTITY -> value == null || value instanceof Entity;
            case INTEGER -> value instanceof Integer;
            case OBJECT -> true;
        };
    }

    /** Copy safe to hand to another owner (vectors are mutable). */
    public Object copy(Object value) {
        return value instanceof Vector3 v ? new Vector3(v) : value;
    }

    /** Whether a literal of this type can be typed into a pin or stored in a file (entities and other objects cannot). */
    public boolean hasLiteral() {
        return this != ENTITY && this != OBJECT;
    }

    /** Whether a pin of this type may hold null (a hit that found no entity, an object not made yet). */
    public boolean allowsNull() {
        return this == ENTITY || this == OBJECT;
    }

    /** The Java class values of this type have, for the types that have one class; {@code Object} for {@link #OBJECT}. */
    public Class<?> valueClass() {
        return switch (this) {
            case FLOAT -> Float.class;
            case BOOLEAN -> Boolean.class;
            case STRING -> String.class;
            case VECTOR3 -> Vector3.class;
            case ENTITY -> Entity.class;
            case INTEGER -> Integer.class;
            case OBJECT -> Object.class;
        };
    }
}
