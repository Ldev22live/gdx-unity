package io.github.ldev22live.gdxunity.engine.scene.audio;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/** Serializer for {@link AudioListenerComponent}, which has no settings. */
public class AudioListenerComponentSerializer implements ComponentSerializer<AudioListenerComponent> {
    public static final String TYPE_ID = "AudioListener";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<AudioListenerComponent> getComponentClass() {
        return AudioListenerComponent.class;
    }

    @Override
    public void serialize(AudioListenerComponent component, Json json) {
        // Nothing to write: the entity's transform is all a listener needs
    }

    @Override
    public AudioListenerComponent deserialize(JsonValue jsonData, Json json) {
        return new AudioListenerComponent();
    }
}
