package io.github.ldev22live.gdxunity.engine.scene.serialization;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.PreservedFields;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.play.RuntimeOnlyComponent;

import java.io.IOException;
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
 *
 * <p><b>Nothing is dropped (TD-106, roadmap A12).</b> Members this engine does not understand are kept and written back:
 * <ul>
 *   <li>top-level scene members (for example the {@code scenario} block) on {@link Scene#getPreservedFields()};</li>
 *   <li>entity members and transform members on {@link Entity#getPreservedFields()} and
 *       {@link Entity#getPreservedTransformFields()};</li>
 *   <li>a component of an unregistered type as a {@link PreservedComponent}, written back exactly;</li>
 *   <li>fields of a registered component's {@code data} that its serializer does not write back, on
 *       {@link BaseComponent#getPreservedData()}; they are written after the serializer's own fields, except a field the
 *       serializer writes itself, so an edited known field always wins.</li>
 * </ul>
 * A registered component's {@code data} is written by its serializer exactly as before, so files without unknown members are
 * byte-for-byte unchanged.
 */
public class SceneSerializer {
    /** Current supported scene JSON schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Top-level scene members this serializer reads; the rest are preserved. */
    static final Set<String> SCENE_MEMBERS = Set.of("schemaVersion", "name", "gameMode", "gameGenre", "active", "entities");
    /** Entity members this serializer reads; the rest are preserved. */
    static final Set<String> ENTITY_MEMBERS = Set.of("id", "name", "active", "parentId", "transform", "components");
    /** Transform members this serializer reads; the rest are preserved. */
    static final Set<String> TRANSFORM_MEMBERS = Set.of("position", "rotation", "scale");
    /** Component entry members this serializer reads; the rest are preserved. */
    static final Set<String> COMPONENT_ENTRY_MEMBERS = Set.of("type", "enabled", "data");

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
     * Serializes a scene into JSON laid out by {@link SceneTextFormat}: the same scene always gives the same text, and a small
     * edit changes few lines.
     */
    public String toJson(Scene scene) {
        Objects.requireNonNull(scene, "scene cannot be null");

        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = SceneTextFormat.newWriter(stringWriter);
        Json json = new Json(JsonWriter.OutputType.json);
        json.setWriter(jsonWriter);

        json.writeObjectStart();
        json.writeValue("schemaVersion", CURRENT_SCHEMA_VERSION);
        json.writeValue("name", scene.getName());
        // gameMode and gameGenre before active: the order most bundled scene files already have (M9.3)
        if (scene.getGameMode() != null) {
            json.writeValue("gameMode", scene.getGameMode().getFileValue());
        }
        if (scene.getGameGenre() != null) {
            json.writeValue("gameGenre", scene.getGameGenre());
        }
        json.writeValue("active", scene.isActive());
        writePreserved(json, scene.getPreservedFields());

        // Entities array
        json.writeArrayStart("entities");
        List<Entity> allEntities = scene.getAllEntities();
        for (Entity entity : allEntities) {
            writeEntity(entity, json);
        }
        json.writeArrayEnd();

        json.writeObjectEnd();

        // A fixed layout, so a small edit gives a small diff (M9.3, TD-093)
        return SceneTextFormat.format(stringWriter.toString());
    }

    /**
     * One entity in the scene file format (id, name, active, parentId, transform, components), parsed back into
     * a tree. Used by the prefab system to compare and copy entity state. Numbers keep the text a scene file would have
     * ({@code 1.0}, see {@link SceneTextFormat#newWriter}), so a prefab written from the tree matches the scene's layout.
     */
    public JsonValue entityToJsonValue(Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = SceneTextFormat.newWriter(stringWriter);
        Json json = new Json(JsonWriter.OutputType.json);
        json.setWriter(jsonWriter);
        writeEntity(entity, json);
        return new JsonReader().parse(stringWriter.toString());
    }

    /**
     * Creates the component described by one entry of an entity's {@code components} array ({@code type}, {@code enabled},
     * {@code data}). An unregistered type, or a registered one whose serializer gives nothing, becomes a {@link PreservedComponent}
     * holding the entry unchanged; a registered component keeps the {@code data} fields its serializer does not write back.
     *
     * @throws SceneSerializationException when the entry has no {@code type}
     */
    @SuppressWarnings("unchecked")
    public Component readComponent(JsonValue componentVal) {
        Objects.requireNonNull(componentVal, "componentVal cannot be null");
        String typeId = componentVal.getString("type", null);
        if (typeId == null || typeId.isBlank()) {
            throw new SceneSerializationException("A component entry has no 'type': " + PreservedFields.toJson(componentVal));
        }
        JsonValue dataVal = componentVal.get("data");
        ComponentSerializer serializer = registry.getByTypeId(typeId);
        Component component = serializer != null ? serializer.deserialize(dataVal, new Json()) : null;
        if (component == null) {
            component = new PreservedComponent(typeId, dataVal == null ? null : PreservedFields.toJson(dataVal));
        } else if (component instanceof BaseComponent base && dataVal != null && dataVal.isObject()) {
            base.setPreservedData(PreservedFields.unknownMembers(dataVal, writtenMembers(serializer, component)));
        }
        component.setEnabled(componentVal.getBoolean("enabled", true));
        if (component instanceof BaseComponent base) {
            base.setPreservedEntry(PreservedFields.unknownMembers(componentVal, COMPONENT_ENTRY_MEMBERS));
        }
        return component;
    }

    /** The member names {@code serializer} writes for {@code component}. */
    private static Set<String> writtenMembers(ComponentSerializer<?> serializer, Component component) {
        Set<String> names = new HashSet<>();
        JsonValue written = new JsonReader().parse(dataText(serializer, component));
        for (JsonValue member = written.child; member != null; member = member.next) {
            names.add(member.name);
        }
        return names;
    }

    /** The compact {@code data} object text {@code serializer} writes for {@code component}, as the scene writer writes it. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String dataText(ComponentSerializer serializer, Component component) {
        StringWriter text = new StringWriter();
        Json json = new Json(JsonWriter.OutputType.json);
        json.setWriter(SceneTextFormat.newWriter(text));
        json.writeObjectStart();
        serializer.serialize(component, json);
        json.writeObjectEnd();
        return text.toString();
    }

    /** {@code data} with the preserved members the serializer did not write itself appended. */
    private static String withPreserved(String data, PreservedFields preserved) {
        if (preserved.isEmpty()) {
            return data;
        }
        Set<String> written = new HashSet<>();
        JsonValue parsed = new JsonReader().parse(data);
        for (JsonValue member = parsed.child; member != null; member = member.next) {
            written.add(member.name);
        }
        StringBuilder out = new StringBuilder(data.substring(0, data.length() - 1));
        boolean first = parsed.child == null;
        for (Map.Entry<String, String> member : preserved.asMap().entrySet()) {
            if (written.contains(member.getKey())) {
                continue;
            }
            out.append(first ? "" : ",").append(PreservedFields.quote(member.getKey())).append(':')
                .append(member.getValue());
            first = false;
        }
        return out.append('}').toString();
    }

    /** Writes each preserved member as it was read. */
    private static void writePreserved(Json json, PreservedFields preserved) {
        for (Map.Entry<String, String> member : preserved.asMap().entrySet()) {
            writeRaw(json, member.getKey(), member.getValue());
        }
    }

    private static void writeRaw(Json json, String name, String rawJson) {
        try {
            json.getWriter().json(name, rawJson);
        } catch (IOException e) {
            throw new SceneSerializationException("Cannot write '" + name + "': " + e.getMessage(), e);
        }
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
        writePreserved(json, entity.getPreservedTransformFields());
        json.writeObjectEnd(); // end transform

        // Components
        json.writeArrayStart("components");
        for (Component component : entity.getAllComponents()) {
            if (component instanceof RuntimeOnlyComponent) {
                throw new SceneSerializationException("'" + entity.getName() + "' has " + component.getClass().getSimpleName()
                    + ", which only exists while the game plays (TD-088) and is never saved. Save the scene from the editor, not from Play.");
            }
            if (component instanceof PreservedComponent preserved) {
                json.writeObjectStart();
                json.writeValue("type", preserved.getTypeId());
                json.writeValue("enabled", preserved.isEnabled());
                if (preserved.getRawData() != null) {
                    writeRaw(json, "data", preserved.getRawData());
                }
                writePreserved(json, preserved.getPreservedEntry());
                json.writeObjectEnd();
                continue;
            }
            ComponentSerializer serializer = registry.getByClass(component.getClass());
            if (serializer == null) {
                // Kept as before: a component type with no serializer is editor-side state that was never saved
                continue;
            }
            json.writeObjectStart();
            json.writeValue("type", serializer.getTypeId());
            json.writeValue("enabled", component.isEnabled());
            BaseComponent base = component instanceof BaseComponent b ? b : null;
            writeRaw(json, "data", withPreserved(dataText(serializer, component),
                base == null ? PreservedFields.EMPTY : base.getPreservedData()));
            if (base != null) {
                writePreserved(json, base.getPreservedEntry());
            }
            json.writeObjectEnd();
        }
        json.writeArrayEnd(); // end components
        writePreserved(json, entity.getPreservedFields());

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
        scene.setPreservedFields(PreservedFields.unknownMembers(root, SCENE_MEMBERS));
        String modeValue = root.getString("gameMode", null);
        if (modeValue != null) {
            GameMode mode = GameMode.fromFileValue(modeValue);
            if (mode == null) {
                throw new SceneSerializationException("Unknown gameMode in scene JSON: " + modeValue);
            }
            scene.setGameMode(mode);
        }
        String genreValue = root.getString("gameGenre", null);
        if (genreValue != null) {
            try {
                scene.setGameGenre(genreValue);
            } catch (IllegalArgumentException e) {
                throw new SceneSerializationException("Invalid gameGenre in scene JSON: " + e.getMessage(), e);
            }
        }

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
            entity.setPreservedFields(PreservedFields.unknownMembers(entityVal, ENTITY_MEMBERS));

            // Read Transform
            JsonValue transformVal = entityVal.get("transform");
            if (transformVal != null) {
                readTransform(transformVal, entity.getTransform());
                if (transformVal.isObject()) {
                    entity.setPreservedTransformFields(PreservedFields.unknownMembers(transformVal, TRANSFORM_MEMBERS));
                }
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

    /** Applies a {@code transform} object (position, rotation, scale) to {@code transform}. */
    public void readTransform(JsonValue transformVal, Transform transform) {
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

    private void readComponents(JsonValue componentsVal, Entity entity, Json json) {
        for (JsonValue compVal = componentsVal.child; compVal != null; compVal = compVal.next) {
            entity.addComponent(readComponent(compVal));
        }
    }
}
