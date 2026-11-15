package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/** Serializer for {@link TerrainComponent}. */
public class TerrainComponentSerializer implements ComponentSerializer<TerrainComponent> {
    public static final String TYPE_ID = "Terrain";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<TerrainComponent> getComponentClass() {
        return TerrainComponent.class;
    }

    @Override
    public void serialize(TerrainComponent component, Json json) {
        json.writeValue("terrainPath", component.getTerrainPath());
    }

    @Override
    public TerrainComponent deserialize(JsonValue jsonData, Json json) {
        return new TerrainComponent(jsonData.getString("terrainPath", ""));
    }
}
