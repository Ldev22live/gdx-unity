package io.github.ldev22live.gdxunity.engine.scene.prefab;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Serializer for {@link PrefabInstanceComponent}.
 */
public class PrefabInstanceComponentSerializer implements ComponentSerializer<PrefabInstanceComponent> {
    public static final String TYPE_ID = "PrefabInstance";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<PrefabInstanceComponent> getComponentClass() {
        return PrefabInstanceComponent.class;
    }

    @Override
    public void serialize(PrefabInstanceComponent component, Json json) {
        json.writeValue("prefab", component.getPrefabPath());
        json.writeValue("sourceId", component.getSourceId().toString());
        json.writeValue("root", component.isRoot());
        json.writeArrayStart("overrides");
        for (String path : component.getOverrides()) {
            json.writeValue(path);
        }
        json.writeArrayEnd();
        if (component.isRoot()) {
            json.writeArrayStart("knownSourceIds");
            for (UUID id : component.getKnownSourceIds()) {
                json.writeValue(id.toString());
            }
            json.writeArrayEnd();
        }
    }

    @Override
    public PrefabInstanceComponent deserialize(JsonValue jsonData, Json json) {
        String prefab = jsonData.getString("prefab", null);
        String sourceId = jsonData.getString("sourceId", null);
        if (prefab == null || sourceId == null) {
            throw new SceneSerializationException("PrefabInstance requires 'prefab' and 'sourceId'");
        }
        PrefabInstanceComponent component;
        try {
            component = new PrefabInstanceComponent(prefab, UUID.fromString(sourceId), jsonData.getBoolean("root", false));
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("PrefabInstance has invalid sourceId: " + sourceId, e);
        }

        Set<String> overrides = new LinkedHashSet<>();
        JsonValue overridesVal = jsonData.get("overrides");
        if (overridesVal != null && overridesVal.isArray()) {
            for (JsonValue v = overridesVal.child; v != null; v = v.next) {
                overrides.add(v.asString());
            }
        }
        component.setOverrides(overrides);

        Set<UUID> known = new LinkedHashSet<>();
        JsonValue knownVal = jsonData.get("knownSourceIds");
        if (knownVal != null && knownVal.isArray()) {
            for (JsonValue v = knownVal.child; v != null; v = v.next) {
                try {
                    known.add(UUID.fromString(v.asString()));
                } catch (IllegalArgumentException e) {
                    throw new SceneSerializationException("PrefabInstance has invalid known source id: " + v.asString(), e);
                }
            }
        }
        component.setKnownSourceIds(known);
        return component;
    }
}
