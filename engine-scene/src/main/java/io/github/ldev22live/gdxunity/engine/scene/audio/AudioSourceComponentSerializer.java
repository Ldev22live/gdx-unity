package io.github.ldev22live.gdxunity.engine.scene.audio;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/** Serializer for {@link AudioSourceComponent}. */
public class AudioSourceComponentSerializer implements ComponentSerializer<AudioSourceComponent> {
    public static final String TYPE_ID = "AudioSource";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<AudioSourceComponent> getComponentClass() {
        return AudioSourceComponent.class;
    }

    @Override
    public void serialize(AudioSourceComponent component, Json json) {
        json.writeValue("cue", component.getCue());
        json.writeValue("sound", component.getSound());
        json.writeValue("volume", component.getVolume());
        json.writeValue("pitch", component.getPitch());
        json.writeValue("playOnStart", component.isPlayOnStart());
        // Written only when they differ from the defaults, so scenes made before Phase 20-C do not change when saved again
        if (component.isSpatial()) {
            json.writeValue("spatial", true);
        }
        if (component.getMinDistance() != AudioSourceComponent.DEFAULT_MIN_DISTANCE
            || component.getMaxDistance() != AudioSourceComponent.DEFAULT_MAX_DISTANCE) {
            json.writeValue("minDistance", component.getMinDistance());
            json.writeValue("maxDistance", component.getMaxDistance());
        }
    }

    @Override
    public AudioSourceComponent deserialize(JsonValue jsonData, Json json) {
        try {
            AudioSourceComponent component = new AudioSourceComponent(
                jsonData.getString("cue", AudioSourceComponent.DEFAULT_CUE),
                jsonData.getString("sound", AudioSourceComponent.DEFAULT_SOUND));
            component.setVolume(jsonData.getFloat("volume", 1f));
            component.setPitch(jsonData.getFloat("pitch", 1f));
            component.setPlayOnStart(jsonData.getBoolean("playOnStart", false));
            component.setSpatial(jsonData.getBoolean("spatial", false));
            component.setDistances(jsonData.getFloat("minDistance", AudioSourceComponent.DEFAULT_MIN_DISTANCE),
                jsonData.getFloat("maxDistance", AudioSourceComponent.DEFAULT_MAX_DISTANCE));
            return component;
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("AudioSource is invalid: " + e.getMessage(), e);
        }
    }
}
