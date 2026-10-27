package io.github.ldev22live.gdxunity.engine.scene.physics.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/**
 * Serializer for {@link CircleCollider2DComponent}.
 */
public class CircleCollider2DComponentSerializer implements ComponentSerializer<CircleCollider2DComponent> {
    public static final String TYPE_ID = "CircleCollider2DComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<CircleCollider2DComponent> getComponentClass() {
        return CircleCollider2DComponent.class;
    }

    @Override
    public void serialize(CircleCollider2DComponent component, Json json) {
        json.writeValue("radius", component.getRadius());
        json.writeValue("offsetX", component.getOffset().x);
        json.writeValue("offsetY", component.getOffset().y);
        json.writeValue("density", component.getDensity());
        json.writeValue("friction", component.getFriction());
        json.writeValue("restitution", component.getRestitution());
        json.writeValue("isSensor", component.isSensor());
        json.writeValue("categoryBits", (int) component.getCategoryBits());
        json.writeValue("maskBits", (int) component.getMaskBits());
    }

    @Override
    public CircleCollider2DComponent deserialize(JsonValue jsonData, Json json) {
        CircleCollider2DComponent component = new CircleCollider2DComponent();

        component.setRadius(jsonData.getFloat("radius", 25.0f));

        float offX = jsonData.getFloat("offsetX", 0.0f);
        float offY = jsonData.getFloat("offsetY", 0.0f);
        component.setOffset(offX, offY);

        component.setDensity(jsonData.getFloat("density", 1.0f));
        component.setFriction(jsonData.getFloat("friction", 0.2f));
        component.setRestitution(jsonData.getFloat("restitution", 0.0f));
        component.setSensor(jsonData.getBoolean("isSensor", false));
        component.setCategoryBits((short) jsonData.getInt("categoryBits", 0x0001));
        component.setMaskBits((short) jsonData.getInt("maskBits", 0xFFFF));

        return component;
    }
}
