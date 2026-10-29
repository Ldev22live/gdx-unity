package io.github.ldev22live.gdxunity.editor.core.prefab;

import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compares and merges entity state in the scene file format (TD-027).
 * <p>
 * An entity is flattened into property paths with leaf values:
 * {@code name}, {@code active}, {@code transform/position/x}, ..., and per component
 * {@code component/<type>#<n>} (presence), {@code component/<type>#<n>/enabled} and
 * {@code component/<type>#<n>/data/<field>}, where {@code n} counts components of the same type.
 * An override is a path whose value differs between an instance entity and its prefab entity; a differing
 * presence path means the instance added or removed that component. Prefab links themselves are never part
 * of the state.
 */
public final class PrefabState {
    public static final String NAME = "name";
    public static final String ACTIVE = "active";
    public static final String TRANSFORM = "transform";
    public static final String COMPONENT = "component";

    /**
     * Paths that belong to each instance root rather than to the prefab: where it is placed and what it is
     * called. They are never shown as overrides, never applied to the prefab and never reverted.
     */
    public static final Set<String> ROOT_PATHS = Set.of(
        NAME,
        "transform/position/x", "transform/position/y", "transform/position/z",
        "transform/rotation/x", "transform/rotation/y", "transform/rotation/z", "transform/rotation/w");

    private static final String SEPARATOR = "/";

    private PrefabState() {}

    /** Flattens one entity of the scene file format; see the class comment for the paths. */
    public static Map<String, JsonValue> flatten(JsonValue entityJson) {
        Objects.requireNonNull(entityJson, "entityJson cannot be null");
        Map<String, JsonValue> flat = new LinkedHashMap<>();
        JsonValue name = entityJson.get(NAME);
        if (name != null) {
            flat.put(NAME, name);
        }
        JsonValue active = entityJson.get(ACTIVE);
        if (active != null) {
            flat.put(ACTIVE, active);
        }
        JsonValue transform = entityJson.get(TRANSFORM);
        if (transform != null) {
            flattenInto(TRANSFORM, transform, flat);
        }
        JsonValue components = entityJson.get("components");
        if (components != null && components.isArray()) {
            Map<String, Integer> countByType = new HashMap<>();
            for (JsonValue component = components.child; component != null; component = component.next) {
                String type = component.getString("type", null);
                if (type == null || PrefabInstanceComponentSerializer.TYPE_ID.equals(type)) {
                    continue;
                }
                int index = countByType.merge(type, 1, Integer::sum) - 1;
                String key = componentKey(type, index);
                flat.put(key, component);
                flat.put(key + SEPARATOR + "enabled", component.get("enabled") != null
                    ? component.get("enabled") : new JsonValue(true));
                JsonValue data = component.get("data");
                if (data != null) {
                    flattenInto(key + SEPARATOR + "data", data, flat);
                }
            }
        }
        return flat;
    }

    /** {@code component/<type>#<index>}. */
    public static String componentKey(String typeId, int index) {
        return COMPONENT + SEPARATOR + typeId + "#" + index;
    }

    /**
     * Key of {@code component} on its entity, or null when its type has no registered serializer (such
     * components are not part of prefab state).
     */
    public static String componentKey(SceneSerializer serializer, Entity entity, Component component) {
        Map<String, Integer> countByType = new HashMap<>();
        for (Component c : entity.getAllComponents()) {
            ComponentSerializer<?> s = serializer.getRegistry().getByClass(c.getClass());
            if (s == null) {
                continue;
            }
            int index = countByType.merge(s.getTypeId(), 1, Integer::sum) - 1;
            if (c == component) {
                return PrefabInstanceComponentSerializer.TYPE_ID.equals(s.getTypeId()) ? null : componentKey(s.getTypeId(), index);
            }
        }
        return null;
    }

    /** The component key a path belongs to, or null for entity-level paths. */
    public static String componentKeyOf(String path) {
        if (!path.startsWith(COMPONENT + SEPARATOR)) {
            return null;
        }
        int second = path.indexOf(SEPARATOR, COMPONENT.length() + 1);
        return second < 0 ? path : path.substring(0, second);
    }

    /** Paths where {@code instance} differs from {@code prefab}, in a stable order. */
    public static Set<String> diff(Map<String, JsonValue> prefab, Map<String, JsonValue> instance) {
        Set<String> union = new LinkedHashSet<>(prefab.keySet());
        union.addAll(instance.keySet());
        Set<String> changedPresence = new LinkedHashSet<>();
        for (String path : union) {
            if (path.equals(componentKeyOf(path)) && prefab.containsKey(path) != instance.containsKey(path)) {
                changedPresence.add(path);
            }
        }
        Set<String> result = new LinkedHashSet<>(changedPresence);
        for (String path : union) {
            String key = componentKeyOf(path);
            if (path.equals(key) || (key != null && changedPresence.contains(key))) {
                continue;
            }
            JsonValue a = prefab.get(path);
            JsonValue b = instance.get(path);
            if (a == null || b == null || !leafEquals(a, b)) {
                result.add(path);
            }
        }
        return result;
    }

    /**
     * The prefab entity with {@code overrides} taken from the instance, in the scene file format
     * (without id and parent).
     */
    public static JsonValue merge(JsonValue prefabEntity, JsonValue instanceEntity, Set<String> overrides) {
        Map<String, JsonValue> result = flatten(prefabEntity);
        Map<String, JsonValue> instance = flatten(instanceEntity);
        for (String path : overrides) {
            String key = componentKeyOf(path);
            if (path.equals(key)) {
                result.keySet().removeIf(p -> p.equals(key) || p.startsWith(key + SEPARATOR));
                if (instance.containsKey(key)) {
                    for (Map.Entry<String, JsonValue> entry : instance.entrySet()) {
                        if (entry.getKey().equals(key) || entry.getKey().startsWith(key + SEPARATOR)) {
                            result.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            } else if (key == null || result.containsKey(key)) {
                if (instance.containsKey(path)) {
                    result.put(path, instance.get(path));
                } else {
                    result.remove(path);
                }
            }
        }
        return unflatten(result);
    }

    /** Builds an entity in the scene file format (name, active, transform, components) from flattened paths. */
    public static JsonValue unflatten(Map<String, JsonValue> flat) {
        JsonValue entity = new JsonValue(JsonValue.ValueType.object);
        JsonValue components = new JsonValue(JsonValue.ValueType.array);
        Map<String, JsonValue> componentByKey = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : flat.entrySet()) {
            String path = entry.getKey();
            String key = componentKeyOf(path);
            if (key == null) {
                setPath(entity, path, copy(entry.getValue()));
            } else if (path.equals(key)) {
                JsonValue component = new JsonValue(JsonValue.ValueType.object);
                component.addChild("type", new JsonValue(typeOf(key)));
                component.addChild("enabled", new JsonValue(true));
                component.addChild("data", new JsonValue(JsonValue.ValueType.object));
                componentByKey.put(key, component);
                components.addChild(component);
            }
        }
        for (Map.Entry<String, JsonValue> entry : flat.entrySet()) {
            String path = entry.getKey();
            String key = componentKeyOf(path);
            if (key != null && !path.equals(key) && componentByKey.containsKey(key)) {
                String rest = path.substring(key.length() + 1);
                JsonValue component = componentByKey.get(key);
                if (rest.equals("enabled")) {
                    component.remove("enabled");
                    component.addChild("enabled", copy(entry.getValue()));
                } else {
                    setPath(component, rest, copy(entry.getValue()));
                }
            }
        }
        entity.addChild("components", components);
        return entity;
    }

    /**
     * Makes {@code entity} match {@code state} (scene file format): name, active, transform and every
     * serializable component. Components whose state is already equal stay the same objects; prefab links and
     * components without a serializer are left alone.
     *
     * @return true when anything changed
     */
    public static boolean applyTo(Entity entity, JsonValue state, SceneSerializer serializer) {
        Map<String, JsonValue> current = flatten(serializer.entityToJsonValue(entity));
        Map<String, JsonValue> target = flatten(state);
        if (diff(current, target).isEmpty()) {
            return false;
        }

        String name = state.getString(NAME, null);
        if (name != null) {
            entity.setName(name);
        }
        entity.setActive(state.getBoolean(ACTIVE, true));
        JsonValue transform = state.get(TRANSFORM);
        if (transform != null) {
            serializer.readTransform(transform, entity.getTransform());
        }

        // Components are matched by type and position among their type, so a changed type is replaced as a
        // whole; replacing one of two colliders alone would shift the other's position.
        Set<String> touchedTypes = new LinkedHashSet<>();
        for (String path : diff(current, target)) {
            String key = componentKeyOf(path);
            if (key != null) {
                touchedTypes.add(typeOf(key));
            }
        }
        List<Component> toRemove = new ArrayList<>();
        for (Component component : entity.getAllComponents()) {
            ComponentSerializer<?> s = serializer.getRegistry().getByClass(component.getClass());
            if (s != null && touchedTypes.contains(s.getTypeId())) {
                toRemove.add(component);
            }
        }
        for (Component component : toRemove) {
            entity.removeComponent(component);
        }
        JsonValue components = state.get("components");
        if (components != null) {
            for (JsonValue component = components.child; component != null; component = component.next) {
                if (touchedTypes.contains(component.getString("type", ""))) {
                    Component created = serializer.readComponent(component);
                    if (created != null) {
                        entity.addComponent(created);
                    }
                }
            }
        }
        return true;
    }

    /** Numbers compare by float value (the precision components store); everything else by JSON text. */
    static boolean leafEquals(JsonValue a, JsonValue b) {
        if (a.isNumber() && b.isNumber()) {
            return Float.compare(a.asFloat(), b.asFloat()) == 0;
        }
        if (a.type() != b.type()) {
            return false;
        }
        return a.toJson(JsonWriter.OutputType.json).equals(b.toJson(JsonWriter.OutputType.json));
    }

    /** Deep copy, so a value can be attached to a second tree. */
    public static JsonValue copy(JsonValue value) {
        JsonValue result;
        switch (value.type()) {
            case object, array -> {
                result = new JsonValue(value.type());
                for (JsonValue child = value.child; child != null; child = child.next) {
                    if (child.name != null) {
                        result.addChild(child.name, copy(child));
                    } else {
                        result.addChild(copy(child));
                    }
                }
            }
            case stringValue -> result = new JsonValue(value.asString());
            case doubleValue -> result = new JsonValue(value.asDouble());
            case longValue -> result = new JsonValue(value.asLong());
            case booleanValue -> result = new JsonValue(value.asBoolean());
            default -> result = new JsonValue(JsonValue.ValueType.nullValue);
        }
        return result;
    }

    private static void flattenInto(String prefix, JsonValue value, Map<String, JsonValue> flat) {
        if (value.isObject()) {
            for (JsonValue child = value.child; child != null; child = child.next) {
                flattenInto(prefix + SEPARATOR + child.name, child, flat);
            }
        } else {
            flat.put(prefix, value);
        }
    }

    private static void setPath(JsonValue root, String path, JsonValue value) {
        String[] parts = path.split(SEPARATOR);
        JsonValue node = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonValue next = node.get(parts[i]);
            if (next == null) {
                next = new JsonValue(JsonValue.ValueType.object);
                node.addChild(parts[i], next);
            }
            node = next;
        }
        node.remove(parts[parts.length - 1]);
        node.addChild(parts[parts.length - 1], value);
    }

    private static String typeOf(String componentKey) {
        String rest = componentKey.substring(COMPONENT.length() + 1);
        return rest.substring(0, rest.lastIndexOf('#'));
    }
}
