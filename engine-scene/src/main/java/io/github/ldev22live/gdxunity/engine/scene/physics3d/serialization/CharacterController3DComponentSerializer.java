package io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/** Serializer for {@link CharacterController3DComponent}. */
public class CharacterController3DComponentSerializer implements ComponentSerializer<CharacterController3DComponent> {
    public static final String TYPE_ID = "CharacterController3DComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<CharacterController3DComponent> getComponentClass() {
        return CharacterController3DComponent.class;
    }

    @Override
    public void serialize(CharacterController3DComponent component, Json json) {
        json.writeValue("radius", component.getRadius());
        json.writeValue("height", component.getHeight());
        json.writeValue("slopeLimitDegrees", component.getSlopeLimitDegrees());
        json.writeValue("stepHeight", component.getStepHeight());
        json.writeValue("mass", component.getMass());
        json.writeValue("gravityScale", component.getGravityScale());
        json.writeValue("layer", component.getLayer());
        json.writeValue("collisionMask", component.getCollisionMask());
    }

    @Override
    public CharacterController3DComponent deserialize(JsonValue jsonData, Json json) {
        CharacterController3DComponent component = new CharacterController3DComponent(
            jsonData.getFloat("radius", CharacterController3DComponent.DEFAULT_RADIUS),
            jsonData.getFloat("height", CharacterController3DComponent.DEFAULT_HEIGHT));
        component.setSlopeLimitDegrees(jsonData.getFloat("slopeLimitDegrees", CharacterController3DComponent.DEFAULT_SLOPE_LIMIT_DEGREES));
        component.setStepHeight(jsonData.getFloat("stepHeight", CharacterController3DComponent.DEFAULT_STEP_HEIGHT));
        component.setMass(jsonData.getFloat("mass", CharacterController3DComponent.DEFAULT_MASS));
        component.setGravityScale(jsonData.getFloat("gravityScale", 1f));
        component.setLayer(jsonData.getInt("layer", PhysicsLayers3D.DEFAULT_LAYER));
        component.setCollisionMask(jsonData.getInt("collisionMask", PhysicsLayers3D.ALL_LAYERS));
        return component;
    }
}
