package io.github.ldev22live.gdxunity.engine.scene.play.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.play.SpawnPointComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/** Serializer for {@link SpawnPointComponent} (roadmap M3c.2): it has no fields; the entity's transform is the spawn. */
public class SpawnPointComponentSerializer implements ComponentSerializer<SpawnPointComponent> {
    public static final String TYPE_ID = "SpawnPointComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<SpawnPointComponent> getComponentClass() {
        return SpawnPointComponent.class;
    }

    @Override
    public void serialize(SpawnPointComponent component, Json json) {
        // No fields: the entity's position and yaw are the spawn
    }

    @Override
    public SpawnPointComponent deserialize(JsonValue jsonData, Json json) {
        return new SpawnPointComponent();
    }
}
