package io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Joint3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/** Serializer for {@link Joint3DComponent} (TD-051). Vectors are written as three flat numbers, like the other components. */
public class Joint3DComponentSerializer implements ComponentSerializer<Joint3DComponent> {
    public static final String TYPE_ID = "Joint3DComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<Joint3DComponent> getComponentClass() {
        return Joint3DComponent.class;
    }

    @Override
    public void serialize(Joint3DComponent component, Json json) {
        json.writeValue("type", component.getType().name());
        json.writeValue("connectedName", component.getConnectedName());
        writeVector(json, "anchor", component.getAnchor());
        writeVector(json, "connectedAnchor", component.getConnectedAnchor());
        writeVector(json, "axis", component.getAxis());
        json.writeValue("collideConnected", component.isCollideConnected());
        json.writeValue("limitsEnabled", component.isLimitsEnabled());
        json.writeValue("lowerLimit", component.getLowerLimit());
        json.writeValue("upperLimit", component.getUpperLimit());
        json.writeValue("motorMode", component.getMotorMode().name());
        json.writeValue("motorTarget", component.getMotorTarget());
        json.writeValue("motorMaxForce", component.getMotorMaxForce());
    }

    @Override
    public Joint3DComponent deserialize(JsonValue jsonData, Json json) {
        Joint3DComponent component = new Joint3DComponent();
        try {
            component.setType(Joint3DComponent.Type.valueOf(jsonData.getString("type", Joint3DComponent.Type.HINGE.name())));
            component.setMotorMode(Joint3DComponent.MotorMode.valueOf(jsonData.getString("motorMode", Joint3DComponent.MotorMode.OFF.name())));
            component.setConnectedName(jsonData.getString("connectedName", ""));
            component.setAnchor(readVector(jsonData, "anchor", 0f, 0f, 0f));
            component.setConnectedAnchor(readVector(jsonData, "connectedAnchor", 0f, 0f, 0f));
            component.setAxis(readVector(jsonData, "axis", 0f, 1f, 0f));
            component.setCollideConnected(jsonData.getBoolean("collideConnected", false));
            component.setLimitsEnabled(jsonData.getBoolean("limitsEnabled", false));
            component.setLimits(jsonData.getFloat("lowerLimit", 0f), jsonData.getFloat("upperLimit", 0f));
            component.setMotorTarget(jsonData.getFloat("motorTarget", 0f));
            component.setMotorMaxForce(jsonData.getFloat("motorMaxForce", Joint3DComponent.DEFAULT_MOTOR_MAX_FORCE));
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Invalid Joint 3D: " + e.getMessage(), e);
        }
        return component;
    }

    private static void writeVector(Json json, String name, Vector3 value) {
        json.writeValue(name + "X", value.x);
        json.writeValue(name + "Y", value.y);
        json.writeValue(name + "Z", value.z);
    }

    private static Vector3 readVector(JsonValue data, String name, float x, float y, float z) {
        return new Vector3(data.getFloat(name + "X", x), data.getFloat(name + "Y", y), data.getFloat(name + "Z", z));
    }
}
