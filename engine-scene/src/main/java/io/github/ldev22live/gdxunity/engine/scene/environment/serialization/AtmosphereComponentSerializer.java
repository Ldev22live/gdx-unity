package io.github.ldev22live.gdxunity.engine.scene.environment.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmosphereComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/**
 * Serializer for {@link AtmosphereComponent} (TD-090). Every setting is written; a missing field reads as the clear
 * day default, and a value out of range is an error naming it.
 */
public class AtmosphereComponentSerializer implements ComponentSerializer<AtmosphereComponent> {
    public static final String TYPE_ID = "AtmosphereComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<AtmosphereComponent> getComponentClass() {
        return AtmosphereComponent.class;
    }

    @Override
    public void serialize(AtmosphereComponent component, Json json) {
        writeColor(json, "zenith", component.getZenithColor());
        writeColor(json, "horizon", component.getHorizonColor());
        json.writeValue("fog", component.isFogEnabled());
        writeColor(json, "fog", component.getFogColor());
        json.writeValue("fogStart", component.getFogStart());
        json.writeValue("fogEnd", component.getFogEnd());
        json.writeValue("weather", component.getWeather().name());
        json.writeValue("weatherIntensity", component.getWeatherIntensity());
        json.writeValue("sunlight", component.getSunlight());
    }

    @Override
    public AtmosphereComponent deserialize(JsonValue jsonData, Json json) {
        AtmosphereComponent component = new AtmosphereComponent();
        String weather = jsonData.getString("weather", component.getWeather().name());
        try {
            component.setZenithColor(readColor(jsonData, "zenith", component.getZenithColor()));
            component.setHorizonColor(readColor(jsonData, "horizon", component.getHorizonColor()));
            component.setFogEnabled(jsonData.getBoolean("fog", component.isFogEnabled()));
            component.setFogColor(readColor(jsonData, "fog", component.getFogColor()));
            component.setFogRange(jsonData.getFloat("fogStart", component.getFogStart()),
                jsonData.getFloat("fogEnd", component.getFogEnd()));
            component.setWeather(AtmosphereComponent.Weather.valueOf(weather));
            component.setWeatherIntensity(jsonData.getFloat("weatherIntensity", component.getWeatherIntensity()));
            component.setSunlight(jsonData.getFloat("sunlight", component.getSunlight()));
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Invalid AtmosphereComponent (weather '" + weather + "'): " + e.getMessage(), e);
        }
        return component;
    }

    static void writeColor(Json json, String prefix, Color color) {
        json.writeValue(prefix + "R", color.r);
        json.writeValue(prefix + "G", color.g);
        json.writeValue(prefix + "B", color.b);
    }

    static Color readColor(JsonValue jsonData, String prefix, Color fallback) {
        return new Color(
            jsonData.getFloat(prefix + "R", fallback.r),
            jsonData.getFloat(prefix + "G", fallback.g),
            jsonData.getFloat(prefix + "B", fallback.b),
            1f);
    }
}
