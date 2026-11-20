package io.github.ldev22live.gdxunity.engine.scene.play.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/** Serializer for {@link PlayerComponent} (TD-088): whether the profile's movement overrides the player's own logic. */
public class PlayerComponentSerializer implements ComponentSerializer<PlayerComponent> {
    public static final String TYPE_ID = "PlayerComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<PlayerComponent> getComponentClass() {
        return PlayerComponent.class;
    }

    @Override
    public void serialize(PlayerComponent component, Json json) {
        json.writeValue("useProfileMovement", component.isUseProfileMovement());
    }

    @Override
    public PlayerComponent deserialize(JsonValue jsonData, Json json) {
        PlayerComponent component = new PlayerComponent();
        component.setUseProfileMovement(jsonData.getBoolean("useProfileMovement", false));
        return component;
    }
}
