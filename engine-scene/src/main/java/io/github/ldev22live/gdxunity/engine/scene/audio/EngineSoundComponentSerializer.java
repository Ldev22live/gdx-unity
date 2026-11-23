package io.github.ldev22live.gdxunity.engine.scene.audio;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/** Serializer for {@link EngineSoundComponent}. */
public class EngineSoundComponentSerializer implements ComponentSerializer<EngineSoundComponent> {
    public static final String TYPE_ID = "EngineSound";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<EngineSoundComponent> getComponentClass() {
        return EngineSoundComponent.class;
    }

    @Override
    public void serialize(EngineSoundComponent component, Json json) {
        json.writeValue("profile", component.getProfile());
        json.writeValue("volume", component.getVolume());
        json.writeValue("spatial", component.isSpatial());
        json.writeValue("minDistance", component.getMinDistance());
        json.writeValue("maxDistance", component.getMaxDistance());
    }

    @Override
    public EngineSoundComponent deserialize(JsonValue jsonData, Json json) {
        try {
            EngineSoundComponent component = new EngineSoundComponent(
                jsonData.getString("profile", EngineSoundComponent.DEFAULT_PROFILE));
            component.setVolume(jsonData.getFloat("volume", 1f));
            component.setSpatial(jsonData.getBoolean("spatial", false));
            component.setDistances(jsonData.getFloat("minDistance", EngineSoundComponent.DEFAULT_MIN_DISTANCE),
                jsonData.getFloat("maxDistance", EngineSoundComponent.DEFAULT_MAX_DISTANCE));
            return component;
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("EngineSound is invalid: " + e.getMessage(), e);
        }
    }
}
