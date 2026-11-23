package io.github.ldev22live.gdxunity.engine.scene.lighting.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.lighting.ToneMappingComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/**
 * Serializer for {@link ToneMappingComponent}.
 */
public class ToneMappingComponentSerializer implements ComponentSerializer<ToneMappingComponent> {
    public static final String TYPE_ID = "ToneMappingComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<ToneMappingComponent> getComponentClass() {
        return ToneMappingComponent.class;
    }

    @Override
    public void serialize(ToneMappingComponent component, Json json) {
        json.writeValue("exposure", component.getExposure());
        json.writeValue("operator", component.getOperator().name());
    }

    @Override
    public ToneMappingComponent deserialize(JsonValue jsonData, Json json) {
        ToneMappingComponent component = new ToneMappingComponent();
        String operator = jsonData.getString("operator", component.getOperator().name());
        try {
            component.setExposure(jsonData.getFloat("exposure", 1f));
            component.setOperator(ToneMappingComponent.Operator.valueOf(operator));
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Invalid ToneMappingComponent (operator '" + operator + "'): "
                + e.getMessage(), e);
        }
        return component;
    }
}
