package io.github.ldev22live.gdxunity.editor.core.prefab;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Contents of a {@code .prefab} file (TD-027): a tree of entities in the scene file format with prefab-local
 * ids, plus the id of the root.
 * <pre>
 * { "schemaVersion": 1, "kind": "prefab", "name": "Crate", "rootId": "...",
 *   "entities": [ { "id", "parentId", "name", "active", "transform", "components" }, ... ] }
 * </pre>
 * Entities are written parents first. Instances are not stored here; each instance entity points back to
 * its prefab entity with a {@code PrefabInstance} component.
 */
public final class PrefabDocument {
    public static final int SCHEMA_VERSION = 1;
    public static final String KIND = "prefab";

    /** Width at which the pretty printer puts small objects on one line. */
    private static final int PRETTY_PRINT_LINE_WIDTH = 100;

    private final String name;
    private final UUID rootId;
    private final Map<UUID, JsonValue> entities;
    private final Map<UUID, UUID> parents;
    /** Ids in depth-first order from the root, so every parent comes before its children. */
    private final List<UUID> order;

    /**
     * @param entities entity states in the scene file format keyed by prefab-local id, parents first; their own
     *                 {@code id}/{@code parentId} fields are ignored
     * @param parents  parent of every entity except the root
     */
    public PrefabDocument(String name, UUID rootId, Map<UUID, JsonValue> entities, Map<UUID, UUID> parents) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.rootId = Objects.requireNonNull(rootId, "rootId cannot be null");
        this.entities = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(entities, "entities cannot be null")));
        this.parents = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(parents, "parents cannot be null")));
        if (!this.entities.containsKey(rootId)) {
            throw new IllegalArgumentException("Prefab root " + rootId + " is not among its entities");
        }
        for (Map.Entry<UUID, UUID> entry : this.parents.entrySet()) {
            if (!this.entities.containsKey(entry.getKey()) || !this.entities.containsKey(entry.getValue())) {
                throw new IllegalArgumentException("Prefab parent link refers to a missing entity: " + entry);
            }
        }
        for (UUID id : this.entities.keySet()) {
            if (!id.equals(rootId) && !this.parents.containsKey(id)) {
                throw new IllegalArgumentException("Prefab entity " + id + " has no parent and is not the root");
            }
        }
        this.order = depthFirstOrder();
        if (order.size() != this.entities.size()) {
            throw new IllegalArgumentException("Prefab hierarchy has a cycle or entities not under the root");
        }
    }

    private List<UUID> depthFirstOrder() {
        Map<UUID, List<UUID>> children = new LinkedHashMap<>();
        for (UUID id : entities.keySet()) {
            UUID parent = parents.get(id);
            if (parent != null) {
                children.computeIfAbsent(parent, k -> new ArrayList<>()).add(id);
            }
        }
        List<UUID> result = new ArrayList<>();
        List<UUID> stack = new ArrayList<>(List.of(rootId));
        while (!stack.isEmpty() && result.size() <= entities.size()) {
            UUID id = stack.remove(stack.size() - 1);
            result.add(id);
            List<UUID> kids = children.getOrDefault(id, List.of());
            for (int i = kids.size() - 1; i >= 0; i--) {
                stack.add(kids.get(i));
            }
        }
        return result;
    }

    public String getName() {
        return name;
    }

    public UUID getRootId() {
        return rootId;
    }

    /** Prefab-local ids, parents first. */
    public List<UUID> getEntityIds() {
        return Collections.unmodifiableList(order);
    }

    public boolean contains(UUID id) {
        return entities.containsKey(id);
    }

    /** State of one entity in the scene file format; callers must not modify it. */
    public JsonValue getEntity(UUID id) {
        return entities.get(id);
    }

    /** Parent id, or null for the root. */
    public UUID getParent(UUID id) {
        return parents.get(id);
    }

    public String toJson() {
        JsonValue root = new JsonValue(JsonValue.ValueType.object);
        root.addChild("schemaVersion", new JsonValue(SCHEMA_VERSION));
        root.addChild("kind", new JsonValue(KIND));
        root.addChild("name", new JsonValue(name));
        root.addChild("rootId", new JsonValue(rootId.toString()));
        JsonValue array = new JsonValue(JsonValue.ValueType.array);
        for (UUID id : order) {
            JsonValue entity = new JsonValue(JsonValue.ValueType.object);
            entity.addChild("id", new JsonValue(id.toString()));
            UUID parent = parents.get(id);
            entity.addChild("parentId", parent != null ? new JsonValue(parent.toString()) : new JsonValue(JsonValue.ValueType.nullValue));
            for (JsonValue field = entities.get(id).child; field != null; field = field.next) {
                if (!"id".equals(field.name) && !"parentId".equals(field.name)) {
                    entity.addChild(field.name, PrefabState.copy(field));
                }
            }
            array.addChild(entity);
        }
        root.addChild("entities", array);
        return root.prettyPrint(JsonWriter.OutputType.json, PRETTY_PRINT_LINE_WIDTH);
    }

    /**
     * @throws SceneSerializationException when the text is not a valid prefab document
     */
    public static PrefabDocument parse(String text) {
        if (text == null || text.isBlank()) {
            throw new SceneSerializationException("Prefab file is empty");
        }
        JsonValue root;
        try {
            root = new JsonReader().parse(text);
        } catch (Exception e) {
            throw new SceneSerializationException("Malformed prefab JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new SceneSerializationException("Prefab root element must be an object");
        }
        int version = root.getInt("schemaVersion", -1);
        if (version < 1 || version > SCHEMA_VERSION) {
            throw new SceneSerializationException("Unsupported prefab schemaVersion: " + version);
        }
        if (!KIND.equals(root.getString("kind", null))) {
            throw new SceneSerializationException("Not a prefab document (kind is " + root.getString("kind", null) + ")");
        }
        UUID rootId = parseId(root.getString("rootId", null), "rootId");
        JsonValue array = root.get("entities");
        if (array == null || !array.isArray()) {
            throw new SceneSerializationException("Prefab has no entities array");
        }
        Map<UUID, JsonValue> entities = new LinkedHashMap<>();
        Map<UUID, UUID> parents = new LinkedHashMap<>();
        for (JsonValue entity = array.child; entity != null; entity = entity.next) {
            UUID id = parseId(entity.getString("id", null), "id");
            if (entities.put(id, entity) != null) {
                throw new SceneSerializationException("Duplicate prefab entity id: " + id);
            }
            String parent = entity.getString("parentId", null);
            if (parent != null) {
                parents.put(id, parseId(parent, "parentId"));
            }
        }
        try {
            return new PrefabDocument(root.getString("name", "Prefab"), rootId, entities, parents);
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Invalid prefab: " + e.getMessage(), e);
        }
    }

    private static UUID parseId(String value, String field) {
        if (value == null) {
            throw new SceneSerializationException("Prefab is missing '" + field + "'");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Prefab has invalid " + field + ": " + value, e);
        }
    }
}
