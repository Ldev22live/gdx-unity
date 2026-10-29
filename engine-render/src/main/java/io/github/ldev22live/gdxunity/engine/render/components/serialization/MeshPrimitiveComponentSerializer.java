package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/**
 * Serializer for {@link MeshPrimitiveComponent}.
 */
public class MeshPrimitiveComponentSerializer implements ComponentSerializer<MeshPrimitiveComponent> {
    public static final String TYPE_ID = "MeshPrimitiveComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<MeshPrimitiveComponent> getComponentClass() {
        return MeshPrimitiveComponent.class;
    }

    @Override
    public void serialize(MeshPrimitiveComponent component, Json json) {
        json.writeValue("shape", component.getShape().name());
        json.writeValue("sizeX", component.getSize().x);
        json.writeValue("sizeY", component.getSize().y);
        json.writeValue("sizeZ", component.getSize().z);
        Color color = component.getColor();
        json.writeValue("colorR", color.r);
        json.writeValue("colorG", color.g);
        json.writeValue("colorB", color.b);
        json.writeValue("colorA", color.a);
    }

    @Override
    public MeshPrimitiveComponent deserialize(JsonValue jsonData, Json json) {
        String shapeName = jsonData.getString("shape", MeshPrimitiveComponent.Shape.BOX.name());
        MeshPrimitiveComponent.Shape shape;
        try {
            shape = MeshPrimitiveComponent.Shape.valueOf(shapeName);
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Unknown mesh primitive shape: " + shapeName, e);
        }
        float size = MeshPrimitiveComponent.DEFAULT_SIZE;
        return new MeshPrimitiveComponent(shape,
            jsonData.getFloat("sizeX", size), jsonData.getFloat("sizeY", size), jsonData.getFloat("sizeZ", size),
            new Color(jsonData.getFloat("colorR", 1f), jsonData.getFloat("colorG", 1f),
                jsonData.getFloat("colorB", 1f), jsonData.getFloat("colorA", 1f)));
    }
}
