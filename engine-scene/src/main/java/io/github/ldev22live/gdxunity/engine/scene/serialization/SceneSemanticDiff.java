package io.github.ldev22live.gdxunity.engine.scene.serialization;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.PreservedFields;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compares two scene files by meaning, for round-trip tests between clients (TD-106, roadmap A12). Every difference is reported
 * as {@link Kind#MISSING} (potential data loss), {@link Kind#ADDED} or {@link Kind#CHANGED}, unless it is one of the documented
 * {@link Normalization}s. The only rules applied to every value:
 * <ul>
 *   <li>object member order does not matter (JSON objects are unordered);</li>
 *   <li>entities are matched by {@code id}, and an entity's components by {@code type} and their order among components of that
 *       type;</li>
 *   <li>two numbers are equal when they are the same 32-bit float, because the engine keeps scene numbers as {@code float}
 *       ({@code 1} and {@code 1.0}, or {@code 0.9659} written back from a float, are the same value).</li>
 * </ul>
 * Null, empty and missing are <b>not</b> treated as the same anywhere, except where a {@link Normalization} names the exact
 * component type, field and value.
 */
public final class SceneSemanticDiff {
    /** What kind of difference. */
    public enum Kind {
        /** In the first file, not in the second: potential data loss. */
        MISSING,
        /** In the second file only. */
        ADDED,
        /** In both, with different values. */
        CHANGED
    }

    /** One difference, with its path ({@code entities[<id> Hero].components[OwnerComponent#0].data.player}). */
    public record Difference(Kind kind, String path, String before, String after) {
        @Override
        public String toString() {
            return kind + " " + path + (before != null ? " was " + before : "") + (after != null ? " now " + after : "");
        }
    }

    /** A documented, verified equivalence for named fields of one component type. Each names the code that makes it true. */
    public sealed interface Normalization {
        /** The component type it applies to. */
        String componentType();

        /** Why it holds: the serializer or setter code. */
        String reason();
    }

    /**
     * Field {@code field} absent in one file and {@code value} (compact JSON) in the other mean the same, because the serializer
     * reads a missing field as that value.
     */
    public record AbsentMeans(String componentType, String field, String value, String reason) implements Normalization {
        public AbsentMeans {
            Objects.requireNonNull(componentType, "componentType cannot be null");
            Objects.requireNonNull(field, "field cannot be null");
            Objects.requireNonNull(value, "value cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
        }
    }

    /** Field {@code field} holds a 16-bit value: two numbers are equal when their low 16 bits are ({@code 65535} and {@code -1}). */
    public record SixteenBits(String componentType, String field, String reason) implements Normalization {
        public SixteenBits {
            Objects.requireNonNull(componentType, "componentType cannot be null");
            Objects.requireNonNull(field, "field cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
        }
    }

    /** Fields {@code x}, {@code y}, {@code z} are a direction: equal when they point the same way (compared normalised). */
    public record Direction(String componentType, String x, String y, String z, String reason) implements Normalization {
        public Direction {
            Objects.requireNonNull(componentType, "componentType cannot be null");
            Objects.requireNonNull(x, "x cannot be null");
            Objects.requireNonNull(y, "y cannot be null");
            Objects.requireNonNull(z, "z cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
        }
    }

    /** How closely two normalised directions must agree, per component. */
    private static final double DIRECTION_TOLERANCE = 1e-5;

    /**
     * The equivalences of today's desktop serializers, each checked in the code it names (A12 round-trip scan of the 56 shipped
     * samples and the contract fixture, 2026-11-25). Nothing else is normalized.
     */
    public static final List<Normalization> DESKTOP = List.of(
        new Direction("DirectionalLightComponent", "directionX", "directionY", "directionZ",
            "DirectionalLightComponent.setDirection normalises the vector, so (-0.4,-1,-0.6) is written back as its unit vector"),
        new SixteenBits("BoxCollider2DComponent", "maskBits", "BoxCollider2DComponentSerializer casts to short: 65535 is written as -1"),
        new SixteenBits("BoxCollider2DComponent", "categoryBits", "BoxCollider2DComponentSerializer casts to short"),
        new SixteenBits("CircleCollider2DComponent", "maskBits", "the 2D collider serializers cast to short"),
        new SixteenBits("CircleCollider2DComponent", "categoryBits", "the 2D collider serializers cast to short"),
        new AbsentMeans("SpriteAnimatorComponent", "slice", "null",
            "SpriteAnimatorComponentSerializer reads a missing slice as null and writes null"));

    private final List<Normalization> normalizations;

    /** A comparison that allows only {@code normalizations}. */
    public SceneSemanticDiff(List<Normalization> normalizations) {
        this.normalizations = List.copyOf(Objects.requireNonNull(normalizations, "normalizations cannot be null"));
    }

    /** The differences between two scene file texts. */
    public List<Difference> compare(String before, String after) {
        return compare(new JsonReader().parse(before), new JsonReader().parse(after));
    }

    /** The differences between two parsed scene files. */
    public List<Difference> compare(JsonValue before, JsonValue after) {
        List<Difference> out = new ArrayList<>();
        compareObjects(before, after, "", Set.of("entities"), out);
        compareEntities(before.get("entities"), after.get("entities"), out);
        return out;
    }

    private void compareEntities(JsonValue before, JsonValue after, List<Difference> out) {
        Map<String, JsonValue> a = byId(before);
        Map<String, JsonValue> b = byId(after);
        for (Map.Entry<String, JsonValue> entry : a.entrySet()) {
            String path = "entities[" + entry.getKey() + " " + entry.getValue().getString("name", "") + "]";
            JsonValue other = b.get(entry.getKey());
            if (other == null) {
                out.add(new Difference(Kind.MISSING, path, "entity", null));
                continue;
            }
            compareObjects(entry.getValue(), other, path, Set.of("components"), out);
            compareComponents(entry.getValue().get("components"), other.get("components"), path + ".components", out);
        }
        for (String id : b.keySet()) {
            if (!a.containsKey(id)) {
                out.add(new Difference(Kind.ADDED, "entities[" + id + "]", null, "entity"));
            }
        }
    }

    private void compareComponents(JsonValue before, JsonValue after, String path, List<Difference> out) {
        Map<String, JsonValue> a = byTypeAndIndex(before);
        Map<String, JsonValue> b = byTypeAndIndex(after);
        for (Map.Entry<String, JsonValue> entry : a.entrySet()) {
            String componentPath = path + "[" + entry.getKey() + "]";
            JsonValue other = b.get(entry.getKey());
            if (other == null) {
                out.add(new Difference(Kind.MISSING, componentPath, "component", null));
                continue;
            }
            String type = entry.getValue().getString("type");
            compareObjects(entry.getValue(), other, componentPath, Set.of("data"), out);
            compareData(type, entry.getValue().get("data"), other.get("data"), componentPath + ".data", out);
        }
        for (String key : b.keySet()) {
            if (!a.containsKey(key)) {
                out.add(new Difference(Kind.ADDED, path + "[" + key + "]", null, "component"));
            }
        }
    }

    private void compareData(String type, JsonValue before, JsonValue after, String path, List<Difference> out) {
        if (before == null || after == null) {
            compareValues(before, after, path, out);
            return;
        }
        List<Difference> raw = new ArrayList<>();
        compareObjects(before, after, path, Set.of(), raw);
        Set<String> sameDirections = sameDirectionFields(type, before, after);
        for (Difference difference : raw) {
            String field = difference.path().substring(path.length() + 1);
            if (!sameDirections.contains(field) && !normalized(type, field, difference)) {
                out.add(difference);
            }
        }
    }

    /** The fields of every {@link Direction} of {@code type} whose two vectors point the same way. */
    private Set<String> sameDirectionFields(String type, JsonValue before, JsonValue after) {
        Set<String> fields = new HashSet<>();
        for (Normalization normalization : normalizations) {
            if (normalization instanceof Direction d && d.componentType().equals(type)) {
                double[] a = unit(before, d);
                double[] b = unit(after, d);
                if (a != null && b != null && Math.abs(a[0] - b[0]) <= DIRECTION_TOLERANCE
                    && Math.abs(a[1] - b[1]) <= DIRECTION_TOLERANCE && Math.abs(a[2] - b[2]) <= DIRECTION_TOLERANCE) {
                    fields.addAll(List.of(d.x(), d.y(), d.z()));
                }
            }
        }
        return fields;
    }

    private static double[] unit(JsonValue data, Direction d) {
        JsonValue x = data.get(d.x());
        JsonValue y = data.get(d.y());
        JsonValue z = data.get(d.z());
        if (x == null || y == null || z == null || !x.isNumber() || !y.isNumber() || !z.isNumber()) {
            return null;
        }
        double length = Math.sqrt(x.asDouble() * x.asDouble() + y.asDouble() * y.asDouble() + z.asDouble() * z.asDouble());
        return length == 0 ? null : new double[] {x.asDouble() / length, y.asDouble() / length, z.asDouble() / length};
    }

    private boolean normalized(String type, String field, Difference difference) {
        for (Normalization normalization : normalizations) {
            if (!normalization.componentType().equals(type)) {
                continue;
            }
            if (normalization instanceof AbsentMeans absent && absent.field().equals(field) && difference.kind() != Kind.CHANGED) {
                String value = difference.kind() == Kind.MISSING ? difference.before() : difference.after();
                if (absent.value().equals(value)) {
                    return true;
                }
            }
            if (normalization instanceof SixteenBits bits && bits.field().equals(field) && difference.kind() == Kind.CHANGED) {
                try {
                    if ((short) Long.parseLong(difference.before()) == (short) Long.parseLong(difference.after())) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return false;
    }

    /** Compares members of two objects, skipping {@code skip} (compared separately). */
    private void compareObjects(JsonValue before, JsonValue after, String path, Set<String> skip, List<Difference> out) {
        if (before == null || after == null || !before.isObject() || !after.isObject()) {
            compareValues(before, after, path, out);
            return;
        }
        Map<String, JsonValue> a = members(before);
        Map<String, JsonValue> b = members(after);
        for (Map.Entry<String, JsonValue> entry : a.entrySet()) {
            if (skip.contains(entry.getKey())) {
                continue;
            }
            String memberPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
            JsonValue other = b.get(entry.getKey());
            if (other == null) {
                out.add(new Difference(Kind.MISSING, memberPath, PreservedFields.toJson(entry.getValue()), null));
            } else {
                compareValues(entry.getValue(), other, memberPath, out);
            }
        }
        for (Map.Entry<String, JsonValue> entry : b.entrySet()) {
            if (!skip.contains(entry.getKey()) && !a.containsKey(entry.getKey())) {
                out.add(new Difference(Kind.ADDED, path.isEmpty() ? entry.getKey() : path + "." + entry.getKey(), null,
                    PreservedFields.toJson(entry.getValue())));
            }
        }
    }

    private void compareValues(JsonValue before, JsonValue after, String path, List<Difference> out) {
        if (before == null || after == null) {
            if (before != after) {
                out.add(new Difference(before == null ? Kind.ADDED : Kind.MISSING, path,
                    before == null ? null : PreservedFields.toJson(before), after == null ? null : PreservedFields.toJson(after)));
            }
            return;
        }
        if (before.isObject() && after.isObject()) {
            compareObjects(before, after, path, Set.of(), out);
            return;
        }
        if (before.isArray() && after.isArray()) {
            if (before.size != after.size) {
                out.add(new Difference(Kind.CHANGED, path, before.size + " items", after.size + " items"));
            }
            int index = 0;
            for (JsonValue x = before.child, y = after.child; x != null && y != null; x = x.next, y = y.next, index++) {
                compareValues(x, y, path + "[" + index + "]", out);
            }
            return;
        }
        if (!sameValue(before, after)) {
            out.add(new Difference(Kind.CHANGED, path, PreservedFields.toJson(before), PreservedFields.toJson(after)));
        }
    }

    private static boolean sameValue(JsonValue a, JsonValue b) {
        if (a.isNumber() && b.isNumber()) {
            return Float.compare((float) a.asDouble(), (float) b.asDouble()) == 0;
        }
        if (a.type() != b.type()) {
            return false;
        }
        if (a.isNull()) {
            return true;
        }
        return PreservedFields.toJson(a).equals(PreservedFields.toJson(b));
    }

    private static Map<String, JsonValue> members(JsonValue object) {
        Map<String, JsonValue> map = new LinkedHashMap<>();
        for (JsonValue member = object.child; member != null; member = member.next) {
            map.put(member.name, member);
        }
        return map;
    }

    private static Map<String, JsonValue> byId(JsonValue entities) {
        Map<String, JsonValue> map = new LinkedHashMap<>();
        for (JsonValue entity = entities == null ? null : entities.child; entity != null; entity = entity.next) {
            map.put(entity.getString("id", "?"), entity);
        }
        return map;
    }

    private static Map<String, JsonValue> byTypeAndIndex(JsonValue components) {
        Map<String, JsonValue> map = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (JsonValue component = components == null ? null : components.child; component != null; component = component.next) {
            String type = component.getString("type", "?");
            int index = counts.merge(type, 1, Integer::sum) - 1;
            map.put(type + "#" + index, component);
        }
        return map;
    }
}
