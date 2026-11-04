package io.github.ldev22live.gdxunity.engine.animation;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/** Serializer for {@link AnimatorComponent}: the clip path and playback settings. */
public class AnimatorComponentSerializer implements ComponentSerializer<AnimatorComponent> {
    public static final String TYPE_ID = "AnimatorComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<AnimatorComponent> getComponentClass() {
        return AnimatorComponent.class;
    }

    @Override
    public void serialize(AnimatorComponent component, Json json) {
        json.writeValue("clip", component.getClipPath());
        json.writeValue("playOnStart", component.isPlayOnStart());
        json.writeValue("wrap", component.getWrapMode().name());
        json.writeValue("speed", component.getSpeed());
    }

    @Override
    public AnimatorComponent deserialize(JsonValue jsonData, Json json) {
        String path = jsonData.getString("clip", null);
        if (path == null || path.isBlank()) {
            throw new SceneSerializationException("AnimatorComponent requires 'clip'");
        }
        try {
            AnimatorComponent component = new AnimatorComponent(path);
            component.setPlayOnStart(jsonData.getBoolean("playOnStart", true));
            component.setWrapMode(WrapMode.valueOf(jsonData.getString("wrap", WrapMode.LOOP.name())));
            component.setSpeed(jsonData.getFloat("speed", 1f));
            return component;
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Invalid AnimatorComponent: " + e.getMessage(), e);
        }
    }
}
