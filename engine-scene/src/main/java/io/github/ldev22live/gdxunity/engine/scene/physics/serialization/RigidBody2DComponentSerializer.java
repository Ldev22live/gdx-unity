package io.github.ldev22live.gdxunity.engine.scene.physics.serialization;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/**
 * Serializer for {@link RigidBody2DComponent}.
 */
public class RigidBody2DComponentSerializer implements ComponentSerializer<RigidBody2DComponent> {
    public static final String TYPE_ID = "RigidBody2DComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<RigidBody2DComponent> getComponentClass() {
        return RigidBody2DComponent.class;
    }

    @Override
    public void serialize(RigidBody2DComponent component, Json json) {
        json.writeValue("bodyType", component.getBodyType().name());
        json.writeValue("gravityScale", component.getGravityScale());
        json.writeValue("linearDamping", component.getLinearDamping());
        json.writeValue("angularDamping", component.getAngularDamping());
        json.writeValue("fixedRotation", component.isFixedRotation());
        json.writeValue("bullet", component.isBullet());

        Vector2 vel = component.getInitialLinearVelocity();
        json.writeValue("initialLinearVelocityX", vel.x);
        json.writeValue("initialLinearVelocityY", vel.y);
        json.writeValue("initialAngularVelocity", component.getInitialAngularVelocity());
    }

    @Override
    public RigidBody2DComponent deserialize(JsonValue jsonData, Json json) {
        RigidBody2DComponent component = new RigidBody2DComponent();

        String bodyTypeStr = jsonData.getString("bodyType", "DYNAMIC");
        try {
            component.setBodyType(RigidBody2DComponent.BodyType.valueOf(bodyTypeStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            component.setBodyType(RigidBody2DComponent.BodyType.DYNAMIC);
        }

        component.setGravityScale(jsonData.getFloat("gravityScale", 1.0f));
        component.setLinearDamping(jsonData.getFloat("linearDamping", 0.0f));
        component.setAngularDamping(jsonData.getFloat("angularDamping", 0.05f));
        component.setFixedRotation(jsonData.getBoolean("fixedRotation", false));
        component.setBullet(jsonData.getBoolean("bullet", false));

        float velX = jsonData.getFloat("initialLinearVelocityX", 0.0f);
        float velY = jsonData.getFloat("initialLinearVelocityY", 0.0f);
        component.setInitialLinearVelocity(velX, velY);

        component.setInitialAngularVelocity(jsonData.getFloat("initialAngularVelocity", 0.0f));

        return component;
    }
}
