package io.github.ldev22live.gdxunity.engine.scene.serialization;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Handles versioned JSON serialization and deserialization of {@link Scene}s.
 * Preserves entity UUIDs, hierarchy, transforms, enabled states, and registered components.
 */
public class SceneSerializer {
    /** Current supported scene JSON schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final ComponentSerializerRegistry registry;

    public SceneSerializer() {
        this(new ComponentSerializerRegistry());
    }

    public SceneSerializer(ComponentSerializerRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    public ComponentSerializerRegistry getRegistry() {
        return registry;
    }

    /**
     * Serializes a scene into a formatted JSON string.
     */
    public String toJson(Scene scene) {
        Objects.requireNonNull(scene, "scene cannot be null");

        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        jsonWriter.setOutputType(JsonWriter.OutputType.json);
        Json json = new Json();
        json.setWriter(jsonWriter);

        json.writeObjectStart();
        json.writeValue("schemaVersion", CURRENT_SCHEMA_VERSION);
        json.writeValue("name", scene.getName());
        json.writeValue("active", scene.isActive());

        // Entities array
        json.writeArrayStart("entities");
        List<Entity> allEntities = scene.getAllEntities();
        for (Entity entity : allEntities) {
            writeEntity(entity, json);
        }
        json.writeArrayEnd();

        json.writeObjectEnd();

        com.badlogic.gdx.utils.JsonValue.PrettyPrintSettings settings = new com.badlogic.gdx.utils.JsonValue.PrettyPrintSettings();
        settings.outputType = JsonWriter.OutputType.json;
        return json.prettyPrint(stringWriter.toString(), settings);
    }

    @SuppressWarnings("unchecked")
    private void writeEntity(Entity entity, Json json) {
        json.writeObjectStart();
        json.writeValue("id", entity.getId().toString());
        json.writeValue("name", entity.getName());
        json.writeValue("active", entity.isActiveSelf());

        // Hierarchy parent
        Transform parent = entity.getTransform().getParent();
        if (parent != null) {
            json.writeValue("parentId", parent.getEntity().getId().toString());
        } else {
            json.writeValue("parentId", (String) null);
        }

        // Local Transform
        Vector3 pos = entity.getTransform().getLocalPosition();
        Quaternion rot = entity.getTransform().getLocalRotation();
        Vector3 scale = entity.getTransform().getLocalScale();

        json.writeObjectStart("transform");
        json.writeObjectStart("position");
        json.writeValue("x", pos.x);
        json.writeValue("y", pos.y);
        json.writeValue("z", pos.z);
        json.writeObjectEnd();

        json.writeObjectStart("rotation");
        json.writeValue("x", rot.x);
        json.writeValue("y", rot.y);
        json.writeValue("z", rot.z);
        json.writeValue("w", rot.w);
        json.writeObjectEnd();

        json.writeObjectStart("scale");
        json.writeValue("x", scale.x);
        json.writeValue("y", scale.y);
        json.writeValue("z", scale.z);
        json.writeObjectEnd();
        json.writeObjectEnd(); // end transform

        // Components
        json.writeArrayStart("components");
        for (Component component : entity.getAllComponents()) {
            ComponentSerializer serializer = registry.getByClass(component.getClass());
            if (serializer != null) {
                json.writeObjectStart();
                json.writeValue("type", serializer.getTypeId());
                json.writeValue("enabled", component.isEnabled());
                json.writeObjectStart("data");
                serializer.serialize(component, json);
                json.writeObjectEnd();
                json.writeObjectEnd();
            }
        }
        json.writeArrayEnd(); // end components

        json.writeObjectEnd(); // end entity
    }

    /**
     * Deserializes a scene from a JSON string.
     *
     * @param jsonString valid scene JSON string
     * @return constructed Scene
     * @throws SceneSerializationException if JSON is malformed or invalid
     */
    public Scene fromJson(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            throw new SceneSerializationException("Cannot deserialize empty JSON string");
        }

        JsonValue root;
        try {
            root = new JsonReader().parse(jsonString);
        } catch (Exception e) {
            throw new SceneSerializationException("Malformed JSON string: " + e.getMessage(), e);
        }

        if (root == null || !root.isObject()) {
            throw new SceneSerializationException("Root JSON element must be an object");
        }

        // Validate schema version
        int schemaVersion = root.getInt("schemaVersion", -1);
        if (schemaVersion < 1) {
            throw new SceneSerializationException("Missing or invalid schemaVersion in scene JSON: " + schemaVersion);
        }
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new SceneSerializationException("Unsupported future scene schemaVersion: " + schemaVersion +
                ". Supported version: " + CURRENT_SCHEMA_VERSION);
        }

        String sceneName = root.getString("name", "Untitled Scene");
        boolean active = root.getBoolean("active", true);

        Scene scene = new Scene(sceneName);
        scene.setActive(active);

        JsonValue entitiesArray = root.get("entities");
        if (entitiesArray == null || !entitiesArray.isArray()) {
            return scene; // Scene with no entities
        }

        Map<UUID, Entity> entityMap = new LinkedHashMap<>();
        Map<UUID, UUID> parentMap = new LinkedHashMap<>();
        Set<UUID> observedIds = new HashSet<>();

        Json json = new Json();

        for (JsonValue entityVal = entitiesArray.child; entityVal != null; entityVal = entityVal.next) {
            String idStr = entityVal.getString("id", null);
            if (idStr == null) {
                throw new SceneSerializationException("Entity missing required 'id' field");
            }
            UUID id;
            try {
                id = UUID.fromString(idStr);
            } catch (IllegalArgumentException e) {
                throw new SceneSerializationException("Entity has invalid UUID: " + idStr, e);
            }

            if (!observedIds.add(id)) {
                throw new SceneSerializationException("Duplicate entity UUID found in scene: " + id);
            }

            String entityName = entityVal.getString("name", "Entity");
            boolean entityActive = entityVal.getBoolean("active", true);
            String parentIdStr = entityVal.getString("parentId", null);

            Entity entity = new Entity(id, entityName);
            entity.setActive(entityActive);

            // Read Transform
            JsonValue transformVal = entityVal.get("transform");
            if (transformVal != null) {
                readTransform(transformVal, entity.getTransform());
            }

            // Read Components
            JsonValue componentsVal = entityVal.get("components");
            if (componentsVal != null && componentsVal.isArray()) {
                readComponents(componentsVal, entity, json);
            }

            entityMap.put(id, entity);
            if (parentIdStr != null && !parentIdStr.isBlank() && !parentIdStr.equalsIgnoreCase("null")) {
                try {
                    parentMap.put(id, UUID.fromString(parentIdStr));
                } catch (IllegalArgumentException e) {
                    throw new SceneSerializationException("Invalid parentId UUID: " + parentIdStr, e);
                }
            }

            scene.addEntity(entity);
        }

        // Reconnect hierarchy
        for (Map.Entry<UUID, UUID> entry : parentMap.entrySet()) {
            Entity child = entityMap.get(entry.getKey());
            Entity parent = entityMap.get(entry.getValue());
            if (parent == null) {
                throw new SceneSerializationException("Entity " + child.getName() + " references non-existent parent ID: " +
                    entry.getValue());
            }
            child.getTransform().setParent(parent.getTransform(), false);
        }

        return scene;
    }

    private void readTransform(JsonValue transformVal, Transform transform) {
        JsonValue posVal = transformVal.get("position");
        if (posVal != null) {
            transform.setLocalPosition(
                posVal.getFloat("x", 0f),
                posVal.getFloat("y", 0f),
                posVal.getFloat("z", 0f)
            );
        }

        JsonValue rotVal = transformVal.get("rotation");
        if (rotVal != null) {
            Quaternion q = new Quaternion(
                rotVal.getFloat("x", 0f),
                rotVal.getFloat("y", 0f),
                rotVal.getFloat("z", 0f),
                rotVal.getFloat("w", 1f)
            );
            transform.setLocalRotation(q);
        }

        JsonValue scaleVal = transformVal.get("scale");
        if (scaleVal != null) {
            transform.setLocalScale(
                scaleVal.getFloat("x", 1f),
                scaleVal.getFloat("y", 1f),
                scaleVal.getFloat("z", 1f)
            );
        }
    }

    @SuppressWarnings("unchecked")
    private void readComponents(JsonValue componentsVal, Entity entity, Json json) {
        for (JsonValue compVal = componentsVal.child; compVal != null; compVal = compVal.next) {
            String typeId = compVal.getString("type", null);
            if (typeId == null) {
                continue;
            }

            ComponentSerializer serializer = registry.getByTypeId(typeId);
            if (serializer == null) {
                // Unknown component type policy: ignore or log warning
                continue;
            }

            JsonValue dataVal = compVal.get("data");
            Component component = serializer.deserialize(dataVal, json);
            if (component != null) {
                boolean enabled = compVal.getBoolean("enabled", true);
                component.setEnabled(enabled);
                entity.addComponent(component);
            }
        }
    }
}
