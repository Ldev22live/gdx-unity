package io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.SphereCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/** Serializer for {@link SphereCollider3DComponent}. */
public class SphereCollider3DComponentSerializer implements ComponentSerializer<SphereCollider3DComponent> {
    public static final String TYPE_ID = "SphereCollider3DComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<SphereCollider3DComponent> getComponentClass() {
        return SphereCollider3DComponent.class;
    }

    @Override
    public void serialize(SphereCollider3DComponent component, Json json) {
        json.writeValue("radius", component.getRadius());
        Collider3DJson.write(json, component);
    }

    @Override
    public SphereCollider3DComponent deserialize(JsonValue jsonData, Json json) {
        SphereCollider3DComponent component = new SphereCollider3DComponent(
            jsonData.getFloat("radius", SphereCollider3DComponent.DEFAULT_RADIUS));
        return Collider3DJson.read(jsonData, component);
    }
}
