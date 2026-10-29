package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One node placed in a {@link Graph}: its {@link NodeType} id, its position on the canvas, the literal values typed
 * into unconnected data inputs, and node settings ({@link NodeType#getProperties()}, for example a variable name).
 */
public final class GraphNode {
    private final String id;
    private final String typeId;
    private float x;
    private float y;
    private final Map<String, Object> literals = new LinkedHashMap<>();
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public GraphNode(String id, String typeId, float x, float y) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.typeId = Objects.requireNonNull(typeId, "typeId cannot be null");
        this.x = x;
        this.y = y;
    }

    public String getId() {
        return id;
    }

    public String getTypeId() {
        return typeId;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /** Literal for an unconnected data input, or null when none was typed. */
    public Object getLiteral(String pinId) {
        return literals.get(pinId);
    }

    /** @param value the literal, or null to clear it */
    public void setLiteral(String pinId, Object value) {
        if (value == null) {
            literals.remove(pinId);
        } else {
            literals.put(pinId, value);
        }
    }

    public Map<String, Object> getLiterals() {
        return Collections.unmodifiableMap(literals);
    }

    public Object getProperty(String name) {
        return properties.get(name);
    }

    /** @param value the setting, or null to clear it */
    public void setProperty(String name, Object value) {
        if (value == null) {
            properties.remove(name);
        } else {
            properties.put(name, value);
        }
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }
}
