package io.github.ldev22live.gdxunity.engine.scene.physics.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/**
 * Serializer for {@link BoxCollider2DComponent}.
 */
public class BoxCollider2DComponentSerializer implements ComponentSerializer<BoxCollider2DComponent> {
    public static final String TYPE_ID = "BoxCollider2DComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<BoxCollider2DComponent> getComponentClass() {
        return BoxCollider2DComponent.class;
    }

    @Override
    public void serialize(BoxCollider2DComponent component, Json json) {
        json.writeValue("width", component.getWidth());
        json.writeValue("height", component.getHeight());
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
    public BoxCollider2DComponent deserialize(JsonValue jsonData, Json json) {
        BoxCollider2DComponent component = new BoxCollider2DComponent();

        component.setWidth(jsonData.getFloat("width", 50.0f));
        component.setHeight(jsonData.getFloat("height", 50.0f));

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
