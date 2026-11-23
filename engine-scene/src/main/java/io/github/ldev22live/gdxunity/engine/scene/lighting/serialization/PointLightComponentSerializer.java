package io.github.ldev22live.gdxunity.engine.scene.lighting.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.lighting.PointLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/**
 * Serializer for {@link PointLightComponent}. The light is placed at its entity's world position when
 * rendered; the component's own {@code position} field is persisted for round-trip fidelity.
 */
public class PointLightComponentSerializer implements ComponentSerializer<PointLightComponent> {
    public static final String TYPE_ID = "PointLightComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<PointLightComponent> getComponentClass() {
        return PointLightComponent.class;
    }

    @Override
    public void serialize(PointLightComponent component, Json json) {
        json.writeValue("positionX", component.getPosition().x);
        json.writeValue("positionY", component.getPosition().y);
        json.writeValue("positionZ", component.getPosition().z);
        LightColorJson.write(json, component.getColor());
        json.writeValue("intensity", component.getIntensity());
        json.writeValue("range", component.getRange());
    }

    @Override
    public PointLightComponent deserialize(JsonValue jsonData, Json json) {
        PointLightComponent component = new PointLightComponent();
        component.setPosition(
            jsonData.getFloat("positionX", 0f),
            jsonData.getFloat("positionY", 0f),
            jsonData.getFloat("positionZ", 0f));
        component.setColor(LightColorJson.read(jsonData, Color.WHITE));
        component.setIntensity(jsonData.getFloat("intensity", 1f));
        component.setRange(jsonData.getFloat("range", 10f));
        return component;
    }
}
