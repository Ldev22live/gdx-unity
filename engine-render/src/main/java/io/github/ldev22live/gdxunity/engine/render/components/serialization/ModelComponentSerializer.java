package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/** Serializer for {@link ModelComponent}: asset path, import scale and playback settings (not the play position). */
public class ModelComponentSerializer implements ComponentSerializer<ModelComponent> {
    public static final String TYPE_ID = "ModelComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<ModelComponent> getComponentClass() {
        return ModelComponent.class;
    }

    @Override
    public void serialize(ModelComponent component, Json json) {
        json.writeValue("model", component.getModelPath());
        json.writeValue("importScale", component.getImportScale());
        if (component.getAnimation() != null) {
            json.writeValue("animation", component.getAnimation());
        }
        json.writeValue("loop", component.isLoop());
        json.writeValue("playOnStart", component.isPlayOnStart());
        json.writeValue("speed", component.getSpeed());
    }

    @Override
    public ModelComponent deserialize(JsonValue jsonData, Json json) {
        String path = jsonData.getString("model", null);
        if (path == null || path.isBlank()) {
            throw new SceneSerializationException("ModelComponent requires 'model'");
        }
        try {
            ModelComponent component = new ModelComponent(path);
            component.setImportScale(jsonData.getFloat("importScale", ModelComponent.DEFAULT_IMPORT_SCALE));
            component.setAnimation(jsonData.getString("animation", null));
            component.setLoop(jsonData.getBoolean("loop", true));
            component.setPlayOnStart(jsonData.getBoolean("playOnStart", true));
            component.setSpeed(jsonData.getFloat("speed", 1f));
            return component;
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Invalid ModelComponent: " + e.getMessage(), e);
        }
    }
}
