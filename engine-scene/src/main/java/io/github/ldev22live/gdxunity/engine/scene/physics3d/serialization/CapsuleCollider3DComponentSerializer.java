package io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CapsuleCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/** Serializer for {@link CapsuleCollider3DComponent}. */
public class CapsuleCollider3DComponentSerializer implements ComponentSerializer<CapsuleCollider3DComponent> {
    public static final String TYPE_ID = "CapsuleCollider3DComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<CapsuleCollider3DComponent> getComponentClass() {
        return CapsuleCollider3DComponent.class;
    }

    @Override
    public void serialize(CapsuleCollider3DComponent component, Json json) {
        json.writeValue("radius", component.getRadius());
        json.writeValue("height", component.getHeight());
        Collider3DJson.write(json, component);
    }

    @Override
    public CapsuleCollider3DComponent deserialize(JsonValue jsonData, Json json) {
        CapsuleCollider3DComponent component = new CapsuleCollider3DComponent(
            jsonData.getFloat("radius", CapsuleCollider3DComponent.DEFAULT_RADIUS),
            jsonData.getFloat("height", CapsuleCollider3DComponent.DEFAULT_HEIGHT));
        return Collider3DJson.read(jsonData, component);
    }
}
