package io.github.ldev22live.gdxunity.engine.scene.lighting.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/**
 * Serializer for {@link DirectionalLightComponent}.
 */
public class DirectionalLightComponentSerializer implements ComponentSerializer<DirectionalLightComponent> {
    public static final String TYPE_ID = "DirectionalLightComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<DirectionalLightComponent> getComponentClass() {
        return DirectionalLightComponent.class;
    }

    @Override
    public void serialize(DirectionalLightComponent component, Json json) {
        json.writeValue("directionX", component.getDirection().x);
        json.writeValue("directionY", component.getDirection().y);
        json.writeValue("directionZ", component.getDirection().z);
        LightColorJson.write(json, component.getColor());
        json.writeValue("intensity", component.getIntensity());
        json.writeValue("castShadows", component.isCastShadows());
    }

    @Override
    public DirectionalLightComponent deserialize(JsonValue jsonData, Json json) {
        DirectionalLightComponent component = new DirectionalLightComponent();
        component.setDirection(
            jsonData.getFloat("directionX", 0f),
            jsonData.getFloat("directionY", -1f),
            jsonData.getFloat("directionZ", -0.5f));
        component.setColor(LightColorJson.read(jsonData, Color.WHITE));
        component.setIntensity(jsonData.getFloat("intensity", 1f));
        // Scenes saved before Phase 13-B have no flag; their sun casts shadows like a new one
        component.setCastShadows(jsonData.getBoolean("castShadows", true));
        return component;
    }
}
