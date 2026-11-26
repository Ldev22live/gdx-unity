package io.github.ldev22live.gdxunity.engine.scene;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * JSON members this version of the engine does not understand, kept so that saving never drops them (TD-106, roadmap A12).
 * Each member is kept as its compact JSON text, so nested objects, arrays and explicit {@code null} values survive unchanged.
 * Immutable; the serializers attach it to a scene, an entity, a transform or a component while reading and write it back after the
 * members they know.
 */
public final class PreservedFields {
    /** No preserved members. */
    public static final PreservedFields EMPTY = new PreservedFields(Map.of());

    private final Map<String, String> rawByName;

    private PreservedFields(Map<String, String> rawByName) {
        this.rawByName = rawByName;
    }

    /**
     * The members of {@code object} whose names are not in {@code known}, in file order; {@link #EMPTY} when there are none.
     *
     * @throws IllegalArgumentException when {@code object} is not a JSON object
     */
    public static PreservedFields unknownMembers(JsonValue object, Set<String> known) {
        Objects.requireNonNull(known, "known cannot be null");
        if (object == null) {
            return EMPTY;
        }
        if (!object.isObject()) {
            throw new IllegalArgumentException("Expected a JSON object, got " + object.type());
        }
        Map<String, String> kept = new LinkedHashMap<>();
        for (JsonValue member = object.child; member != null; member = member.next) {
            if (!known.contains(member.name)) {
                kept.put(member.name, toJson(member));
            }
        }
        return kept.isEmpty() ? EMPTY : new PreservedFields(Collections.unmodifiableMap(kept));
    }

    /**
     * The compact JSON text of {@code value}. {@link JsonValue#toJson} gives a bare string value without its quotes and a null
     * value as Java {@code null}; this quotes and escapes strings and writes {@code null}.
     */
    public static String toJson(JsonValue value) {
        Objects.requireNonNull(value, "value cannot be null");
        if (value.isNull()) {
            return "null";
        }
        if (value.isString()) {
            return quote(value.asString());
        }
        if (value.isValue()) {
            return value.asString();
        }
        return value.toJson(JsonWriter.OutputType.json);
    }

    /** {@code text} as a quoted, escaped JSON string. */
    public static String quote(String text) {
        return JsonWriter.OutputType.json.quoteValue(Objects.requireNonNull(text, "text cannot be null"));
    }

    /**
     * Preserved members from names and compact JSON texts.
     *
     * @throws IllegalArgumentException when a text is not one complete JSON value
     */
    public static PreservedFields of(Map<String, String> rawByName) {
        Objects.requireNonNull(rawByName, "rawByName cannot be null");
        if (rawByName.isEmpty()) {
            return EMPTY;
        }
        Map<String, String> copy = new LinkedHashMap<>();
        rawByName.forEach((name, raw) -> {
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(raw, "raw JSON cannot be null for " + name);
            try {
                new JsonReader().parse("{\"v\":" + raw + "}");
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Not a JSON value for '" + name + "': " + raw, e);
            }
            copy.put(name, raw);
        });
        return new PreservedFields(Collections.unmodifiableMap(copy));
    }

    public boolean isEmpty() {
        return rawByName.isEmpty();
    }

    /** The preserved member names, in file order. */
    public Set<String> names() {
        return rawByName.keySet();
    }

    /** The compact JSON text of {@code name}, or null when it is not preserved. */
    public String raw(String name) {
        return rawByName.get(name);
    }

    /** The preserved member {@code name} parsed, or null when it is not preserved. */
    public JsonValue parsed(String name) {
        String raw = rawByName.get(name);
        return raw == null ? null : new JsonReader().parse("{\"v\":" + raw + "}").get("v");
    }

    /** These members as a name to compact JSON map, in file order (unmodifiable). */
    public Map<String, String> asMap() {
        return rawByName;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PreservedFields that && rawByName.equals(that.rawByName);
    }

    @Override
    public int hashCode() {
        return rawByName.hashCode();
    }

    @Override
    public String toString() {
        return "PreservedFields" + rawByName;
    }
}
