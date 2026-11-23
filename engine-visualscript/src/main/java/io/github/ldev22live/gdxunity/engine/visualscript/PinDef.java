package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.Objects;

/**
 * One pin of a node type. Execution pins carry control flow (white wires); data pins carry a value of
 * {@link #type()} (coloured wires). An execution output connects to one input; an execution input accepts many.
 * A data input accepts one wire; a data output feeds many.
 *
 * @param type        value type of a data pin; null for execution pins
 * @param objectClass the Java class of an {@link ValueType#OBJECT} pin (what a method takes or returns); null for every other pin
 */
public record PinDef(String id, String label, Kind kind, Direction direction, ValueType type, Class<?> objectClass) {
    public enum Kind { EXEC, DATA }

    public enum Direction { INPUT, OUTPUT }

    /** Id of the execution input every action node has. */
    public static final String EXEC_IN = "exec";
    /** Id of the main execution output. */
    public static final String EXEC_OUT = "then";

    public PinDef {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(label, "label cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(direction, "direction cannot be null");
        if ((kind == Kind.DATA) != (type != null)) {
            throw new IllegalArgumentException("Data pins need a type and execution pins must not have one: " + id);
        }
        if ((type == ValueType.OBJECT) != (objectClass != null)) {
            throw new IllegalArgumentException("Only object pins have a class, and every object pin needs one: " + id);
        }
    }

    /** A pin that is not an object pin. */
    public PinDef(String id, String label, Kind kind, Direction direction, ValueType type) {
        this(id, label, kind, direction, type, null);
    }

    public static PinDef execIn() {
        return new PinDef(EXEC_IN, "", Kind.EXEC, Direction.INPUT, null);
    }

    public static PinDef execOut(String id, String label) {
        return new PinDef(id, label, Kind.EXEC, Direction.OUTPUT, null);
    }

    public static PinDef in(String id, String label, ValueType type) {
        return new PinDef(id, label, Kind.DATA, Direction.INPUT, type);
    }

    public static PinDef out(String id, String label, ValueType type) {
        return new PinDef(id, label, Kind.DATA, Direction.OUTPUT, type);
    }

    /** A data input that takes an object of {@code objectClass}. */
    public static PinDef inObject(String id, String label, Class<?> objectClass) {
        return new PinDef(id, label, Kind.DATA, Direction.INPUT, ValueType.OBJECT, objectClass);
    }

    /** A data output that gives an object of {@code objectClass}. */
    public static PinDef outObject(String id, String label, Class<?> objectClass) {
        return new PinDef(id, label, Kind.DATA, Direction.OUTPUT, ValueType.OBJECT, objectClass);
    }

    public boolean isExec() {
        return kind == Kind.EXEC;
    }

    public boolean isInput() {
        return direction == Direction.INPUT;
    }

    /** The Java class of the values on a data pin: the object's class for an object pin, else the value type's class. */
    public Class<?> valueClass() {
        if (type == null) {
            throw new IllegalStateException("An execution pin carries no value: " + id);
        }
        return type == ValueType.OBJECT ? objectClass : type.valueClass();
    }
}
