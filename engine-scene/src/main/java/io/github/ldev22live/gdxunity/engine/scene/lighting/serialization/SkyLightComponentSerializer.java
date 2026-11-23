package io.github.ldev22live.gdxunity.engine.scene.lighting.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.lighting.SkyLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/**
 * Serializer for {@link SkyLightComponent}.
 */
public class SkyLightComponentSerializer implements ComponentSerializer<SkyLightComponent> {
    public static final String TYPE_ID = "SkyLightComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<SkyLightComponent> getComponentClass() {
        return SkyLightComponent.class;
    }

    @Override
    public void serialize(SkyLightComponent component, Json json) {
        writeColor(json, "sky", component.getSkyColor());
        writeColor(json, "ground", component.getGroundColor());
        json.writeValue("intensity", component.getIntensity());
    }

    @Override
    public SkyLightComponent deserialize(JsonValue jsonData, Json json) {
        SkyLightComponent component = new SkyLightComponent();
        component.setSkyColor(readColor(jsonData, "sky", SkyLightComponent.DEFAULT_SKY_COLOR));
        component.setGroundColor(readColor(jsonData, "ground", SkyLightComponent.DEFAULT_GROUND_COLOR));
        float intensity = jsonData.getFloat("intensity", 1f);
        try {
            component.setIntensity(intensity);
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Invalid SkyLightComponent: " + e.getMessage(), e);
        }
        return component;
    }

    private static void writeColor(Json json, String prefix, Color color) {
        json.writeValue(prefix + "R", color.r);
        json.writeValue(prefix + "G", color.g);
        json.writeValue(prefix + "B", color.b);
    }

    private static Color readColor(JsonValue jsonData, String prefix, Color fallback) {
        return new Color(
            jsonData.getFloat(prefix + "R", fallback.r),
            jsonData.getFloat(prefix + "G", fallback.g),
            jsonData.getFloat(prefix + "B", fallback.b),
            1f);
    }
}
