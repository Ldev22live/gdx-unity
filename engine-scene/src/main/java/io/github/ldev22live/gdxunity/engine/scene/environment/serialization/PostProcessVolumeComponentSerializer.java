package io.github.ldev22live.gdxunity.engine.scene.environment.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessVolumeComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/**
 * Serializer for {@link PostProcessVolumeComponent} (TD-091). Every setting is written; a missing field reads as a new volume's
 * default (global, every effect off), and a value out of range is an error naming it.
 */
public class PostProcessVolumeComponentSerializer implements ComponentSerializer<PostProcessVolumeComponent> {
    public static final String TYPE_ID = "PostProcessVolumeComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<PostProcessVolumeComponent> getComponentClass() {
        return PostProcessVolumeComponent.class;
    }

    @Override
    public void serialize(PostProcessVolumeComponent component, Json json) {
        json.writeValue("global", component.isGlobal());
        json.writeValue("sizeX", component.getSizeX());
        json.writeValue("sizeY", component.getSizeY());
        json.writeValue("sizeZ", component.getSizeZ());
        json.writeValue("blendDistance", component.getBlendDistance());
        json.writeValue("weight", component.getWeight());
        json.writeValue("priority", component.getPriority());
        json.writeValue("bloom", component.isBloom());
        json.writeValue("bloomIntensity", component.getBloomIntensity());
        json.writeValue("bloomThreshold", component.getBloomThreshold());
        json.writeValue("bloomRadius", component.getBloomRadius());
        json.writeValue("grading", component.isGrading());
        json.writeValue("exposure", component.getExposure());
        json.writeValue("contrast", component.getContrast());
        json.writeValue("saturation", component.getSaturation());
        json.writeValue("temperature", component.getTemperature());
        json.writeValue("tint", component.getTint());
        AtmosphereComponentSerializer.writeColor(json, "filter", component.getColorFilter());
        json.writeValue("ambientOcclusion", component.isAmbientOcclusion());
        json.writeValue("aoIntensity", component.getAoIntensity());
        json.writeValue("aoRadius", component.getAoRadius());
        json.writeValue("depthOfField", component.isDepthOfField());
        json.writeValue("focusDistance", component.getFocusDistance());
        json.writeValue("focusRange", component.getFocusRange());
        json.writeValue("blur", component.getBlur());
    }

    @Override
    public PostProcessVolumeComponent deserialize(JsonValue jsonData, Json json) {
        PostProcessVolumeComponent c = new PostProcessVolumeComponent();
        try {
            c.setGlobal(jsonData.getBoolean("global", c.isGlobal()));
            c.setSize(jsonData.getFloat("sizeX", c.getSizeX()), jsonData.getFloat("sizeY", c.getSizeY()),
                jsonData.getFloat("sizeZ", c.getSizeZ()));
            c.setBlendDistance(jsonData.getFloat("blendDistance", c.getBlendDistance()));
            c.setWeight(jsonData.getFloat("weight", c.getWeight()));
            c.setPriority(jsonData.getInt("priority", c.getPriority()));
            c.setBloom(jsonData.getBoolean("bloom", c.isBloom()));
            c.setBloomIntensity(jsonData.getFloat("bloomIntensity", c.getBloomIntensity()));
            c.setBloomThreshold(jsonData.getFloat("bloomThreshold", c.getBloomThreshold()));
            c.setBloomRadius(jsonData.getFloat("bloomRadius", c.getBloomRadius()));
            c.setGrading(jsonData.getBoolean("grading", c.isGrading()));
            c.setExposure(jsonData.getFloat("exposure", c.getExposure()));
            c.setContrast(jsonData.getFloat("contrast", c.getContrast()));
            c.setSaturation(jsonData.getFloat("saturation", c.getSaturation()));
            c.setTemperature(jsonData.getFloat("temperature", c.getTemperature()));
            c.setTint(jsonData.getFloat("tint", c.getTint()));
            c.setColorFilter(AtmosphereComponentSerializer.readColor(jsonData, "filter", c.getColorFilter()));
            c.setAmbientOcclusion(jsonData.getBoolean("ambientOcclusion", c.isAmbientOcclusion()));
            c.setAoIntensity(jsonData.getFloat("aoIntensity", c.getAoIntensity()));
            c.setAoRadius(jsonData.getFloat("aoRadius", c.getAoRadius()));
            c.setDepthOfField(jsonData.getBoolean("depthOfField", c.isDepthOfField()));
            c.setFocusDistance(jsonData.getFloat("focusDistance", c.getFocusDistance()));
            c.setFocusRange(jsonData.getFloat("focusRange", c.getFocusRange()));
            c.setBlur(jsonData.getFloat("blur", c.getBlur()));
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Invalid PostProcessVolumeComponent: " + e.getMessage(), e);
        }
        return c;
    }
}
