package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/**
 * Serializer for {@link ShapeRendererComponent}.
 */
public class ShapeRendererComponentSerializer implements ComponentSerializer<ShapeRendererComponent> {
    public static final String TYPE_ID = "ShapeRendererComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<ShapeRendererComponent> getComponentClass() {
        return ShapeRendererComponent.class;
    }

    @Override
    public void serialize(ShapeRendererComponent component, Json json) {
        json.writeValue("width", component.getWidth());
        json.writeValue("height", component.getHeight());
        json.writeValue("shapeType", component.getShapeType().name());
        json.writeValue("centered", component.isCentered());

        Color c = component.getColor();
        json.writeObjectStart("color");
        json.writeValue("r", c.r);
        json.writeValue("g", c.g);
        json.writeValue("b", c.b);
        json.writeValue("a", c.a);
        json.writeObjectEnd();
    }

    @Override
    public ShapeRendererComponent deserialize(JsonValue jsonData, Json json) {
        float width = jsonData.getFloat("width", 50f);
        float height = jsonData.getFloat("height", 50f);
        String shapeTypeName = jsonData.getString("shapeType", ShapeRendererComponent.ShapeType.FILLED.name());
        ShapeRendererComponent.ShapeType shapeType;
        try {
            shapeType = ShapeRendererComponent.ShapeType.valueOf(shapeTypeName);
        } catch (IllegalArgumentException e) {
            shapeType = ShapeRendererComponent.ShapeType.FILLED;
        }
        boolean centered = jsonData.getBoolean("centered", true);

        Color color = Color.CYAN;
        JsonValue colorVal = jsonData.get("color");
        if (colorVal != null) {
            color = new Color(
                colorVal.getFloat("r", 1f),
                colorVal.getFloat("g", 1f),
                colorVal.getFloat("b", 1f),
                colorVal.getFloat("a", 1f)
            );
        }

        return new ShapeRendererComponent(width, height, color, shapeType, centered);
    }
}
