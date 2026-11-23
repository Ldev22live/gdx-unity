package io.github.ldev22live.gdxunity.engine.scene.audio;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/** Serializer for {@link MusicSourceComponent}. */
public class MusicSourceComponentSerializer implements ComponentSerializer<MusicSourceComponent> {
    public static final String TYPE_ID = "MusicSource";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<MusicSourceComponent> getComponentClass() {
        return MusicSourceComponent.class;
    }

    @Override
    public void serialize(MusicSourceComponent component, Json json) {
        json.writeValue("song", component.getSong());
        json.writeValue("volume", component.getVolume());
        json.writeValue("loop", component.isLoop());
        json.writeValue("playOnStart", component.isPlayOnStart());
    }

    @Override
    public MusicSourceComponent deserialize(JsonValue jsonData, Json json) {
        try {
            MusicSourceComponent component = new MusicSourceComponent(jsonData.getString("song", ""));
            component.setVolume(jsonData.getFloat("volume", 1f));
            component.setLoop(jsonData.getBoolean("loop", true));
            component.setPlayOnStart(jsonData.getBoolean("playOnStart", true));
            return component;
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("MusicSource is invalid: " + e.getMessage(), e);
        }
    }
}
