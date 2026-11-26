package io.github.ldev22live.gdxunity.engine.scene.scenario;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/** Serializer for {@link OwnerComponent} (TD-106): {@code {"player": "<player id>"}}. */
public class OwnerComponentSerializer implements ComponentSerializer<OwnerComponent> {
    public static final String TYPE_ID = "OwnerComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<OwnerComponent> getComponentClass() {
        return OwnerComponent.class;
    }

    @Override
    public void serialize(OwnerComponent component, Json json) {
        json.writeValue("player", component.getPlayerId());
    }

    /** @throws SceneSerializationException when {@code player} is missing or not a UUID */
    @Override
    public OwnerComponent deserialize(JsonValue jsonData, Json json) {
        String player = jsonData == null ? null : jsonData.getString("player", null);
        if (player == null) {
            throw new SceneSerializationException("OwnerComponent needs a 'player' id");
        }
        try {
            return new OwnerComponent(player);
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException(e.getMessage(), e);
        }
    }
}
