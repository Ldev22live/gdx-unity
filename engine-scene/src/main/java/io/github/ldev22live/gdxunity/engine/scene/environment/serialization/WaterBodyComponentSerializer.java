package io.github.ldev22live.gdxunity.engine.scene.environment.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterBodyComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/**
 * Serializer for {@link WaterBodyComponent} (TD-090). A missing field reads as the default lake; a value out of range
 * is an error naming it.
 */
public class WaterBodyComponentSerializer implements ComponentSerializer<WaterBodyComponent> {
    public static final String TYPE_ID = "WaterBodyComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<WaterBodyComponent> getComponentClass() {
        return WaterBodyComponent.class;
    }

    @Override
    public void serialize(WaterBodyComponent component, Json json) {
        json.writeValue("width", component.getWidth());
        json.writeValue("length", component.getLength());
        Color color = component.getColor();
        AtmosphereComponentSerializer.writeColor(json, "color", color);
        json.writeValue("opacity", color.a);
        json.writeValue("roughness", component.getRoughness());
        json.writeValue("waveHeight", component.getWaveHeight());
        json.writeValue("waveLength", component.getWaveLength());
        json.writeValue("waveSpeed", component.getWaveSpeed());
    }

    @Override
    public WaterBodyComponent deserialize(JsonValue jsonData, Json json) {
        WaterBodyComponent component = new WaterBodyComponent();
        try {
            component.setSize(jsonData.getFloat("width", component.getWidth()), jsonData.getFloat("length", component.getLength()));
            Color color = AtmosphereComponentSerializer.readColor(jsonData, "color", component.getColor());
            color.a = jsonData.getFloat("opacity", component.getColor().a);
            if (!(color.a >= 0f && color.a <= 1f)) {
                throw new IllegalArgumentException("opacity must be within 0 and 1: " + color.a);
            }
            component.setColor(color);
            component.setRoughness(jsonData.getFloat("roughness", component.getRoughness()));
            component.setWaves(jsonData.getFloat("waveHeight", component.getWaveHeight()),
                jsonData.getFloat("waveLength", component.getWaveLength()),
                jsonData.getFloat("waveSpeed", component.getWaveSpeed()));
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Invalid WaterBodyComponent: " + e.getMessage(), e);
        }
        return component;
    }
}
