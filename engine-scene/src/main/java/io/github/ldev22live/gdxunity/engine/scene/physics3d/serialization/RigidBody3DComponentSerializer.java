package io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/** Serializer for {@link RigidBody3DComponent}. */
public class RigidBody3DComponentSerializer implements ComponentSerializer<RigidBody3DComponent> {
    public static final String TYPE_ID = "RigidBody3DComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<RigidBody3DComponent> getComponentClass() {
        return RigidBody3DComponent.class;
    }

    @Override
    public void serialize(RigidBody3DComponent component, Json json) {
        json.writeValue("motionType", component.getMotionType().name());
        json.writeValue("mass", component.getMass());
        json.writeValue("linearDamping", component.getLinearDamping());
        json.writeValue("angularDamping", component.getAngularDamping());
        json.writeValue("gravityFactor", component.getGravityFactor());
        json.writeValue("friction", component.getFriction());
        json.writeValue("restitution", component.getRestitution());
        json.writeValue("layer", component.getLayer());
        json.writeValue("collisionMask", component.getCollisionMask());
    }

    @Override
    public RigidBody3DComponent deserialize(JsonValue jsonData, Json json) {
        String motion = jsonData.getString("motionType", RigidBody3DComponent.MotionType.DYNAMIC.name());
        RigidBody3DComponent component;
        try {
            component = new RigidBody3DComponent(RigidBody3DComponent.MotionType.valueOf(motion));
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Unknown 3D motion type: " + motion, e);
        }
        component.setMass(jsonData.getFloat("mass", RigidBody3DComponent.DEFAULT_MASS));
        component.setLinearDamping(jsonData.getFloat("linearDamping", RigidBody3DComponent.DEFAULT_DAMPING));
        component.setAngularDamping(jsonData.getFloat("angularDamping", RigidBody3DComponent.DEFAULT_DAMPING));
        component.setGravityFactor(jsonData.getFloat("gravityFactor", 1f));
        component.setFriction(jsonData.getFloat("friction", RigidBody3DComponent.DEFAULT_FRICTION));
        component.setRestitution(jsonData.getFloat("restitution", 0f));
        component.setLayer(jsonData.getInt("layer", PhysicsLayers3D.DEFAULT_LAYER));
        component.setCollisionMask(jsonData.getInt("collisionMask", PhysicsLayers3D.ALL_LAYERS));
        return component;
    }
}
