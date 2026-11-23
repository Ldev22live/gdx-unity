package io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/** Serializer for {@link MeshCollider3DComponent}: offset and trigger only, the triangles come from the mesh. */
public class MeshCollider3DComponentSerializer implements ComponentSerializer<MeshCollider3DComponent> {
    public static final String TYPE_ID = "MeshCollider3DComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<MeshCollider3DComponent> getComponentClass() {
        return MeshCollider3DComponent.class;
    }

    @Override
    public void serialize(MeshCollider3DComponent component, Json json) {
        Collider3DJson.write(json, component);
    }

    @Override
    public MeshCollider3DComponent deserialize(JsonValue jsonData, Json json) {
        return Collider3DJson.read(jsonData, new MeshCollider3DComponent());
    }
}
