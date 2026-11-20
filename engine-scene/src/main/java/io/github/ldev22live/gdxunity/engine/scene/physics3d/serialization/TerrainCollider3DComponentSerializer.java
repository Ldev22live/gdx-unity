package io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.TerrainCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/** Serializer for {@link TerrainCollider3DComponent}: offset only (never a trigger); the heights come from the Terrain component (TD-087). */
public class TerrainCollider3DComponentSerializer implements ComponentSerializer<TerrainCollider3DComponent> {
    public static final String TYPE_ID = "TerrainCollider3DComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<TerrainCollider3DComponent> getComponentClass() {
        return TerrainCollider3DComponent.class;
    }

    @Override
    public void serialize(TerrainCollider3DComponent component, Json json) {
        Collider3DJson.write(json, component);
    }

    @Override
    public TerrainCollider3DComponent deserialize(JsonValue jsonData, Json json) {
        return Collider3DJson.read(jsonData, new TerrainCollider3DComponent());
    }
}
