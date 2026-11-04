package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/** Reads and writes {@link DynamicMeshComponent}: the mesh path and the colour. */
public class DynamicMeshComponentSerializer implements ComponentSerializer<DynamicMeshComponent> {
    /** The type name in scene files. */
    public static final String TYPE_ID = "DynamicMeshComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<DynamicMeshComponent> getComponentClass() {
        return DynamicMeshComponent.class;
    }

    @Override
    public void serialize(DynamicMeshComponent component, Json json) {
        json.writeValue("mesh", component.getMeshPath());
        Color color = component.getColor();
        json.writeValue("colorR", color.r);
        json.writeValue("colorG", color.g);
        json.writeValue("colorB", color.b);
        json.writeValue("colorA", color.a);
    }

    @Override
    public DynamicMeshComponent deserialize(JsonValue jsonData, Json json) {
        String path = jsonData.getString("mesh", null);
        if (path == null || path.isBlank()) {
            throw new SceneSerializationException("DynamicMeshComponent requires 'mesh'");
        }
        try {
            DynamicMeshComponent component = new DynamicMeshComponent(path);
            Color defaults = component.getColor();
            component.setColor(new Color(jsonData.getFloat("colorR", defaults.r), jsonData.getFloat("colorG", defaults.g),
                jsonData.getFloat("colorB", defaults.b), jsonData.getFloat("colorA", defaults.a)));
            return component;
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Invalid DynamicMeshComponent: " + e.getMessage(), e);
        }
    }
}
