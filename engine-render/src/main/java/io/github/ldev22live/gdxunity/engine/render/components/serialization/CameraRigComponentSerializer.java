package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/** Serializer for {@link CameraRigComponent} (TD-050); missing keys take the component defaults. */
public class CameraRigComponentSerializer implements ComponentSerializer<CameraRigComponent> {
    public static final String TYPE_ID = "CameraRigComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<CameraRigComponent> getComponentClass() {
        return CameraRigComponent.class;
    }

    @Override
    public void serialize(CameraRigComponent component, Json json) {
        json.writeValue("mode", component.getMode().name());
        json.writeValue("targetName", component.getTargetName());
        Vector3 offset = component.getFollowOffset();
        json.writeValue("followOffsetX", offset.x);
        json.writeValue("followOffsetY", offset.y);
        json.writeValue("followOffsetZ", offset.z);
        json.writeValue("followRate", component.getFollowRate());
        json.writeValue("pivotHeight", component.getPivotHeight());
        json.writeValue("eyeHeight", component.getEyeHeight());
        json.writeValue("yaw", component.getYaw());
        json.writeValue("pitch", component.getPitch());
        json.writeValue("minPitch", component.getMinPitch());
        json.writeValue("maxPitch", component.getMaxPitch());
        json.writeValue("distance", component.getDistance());
        json.writeValue("minDistance", component.getMinDistance());
        json.writeValue("maxDistance", component.getMaxDistance());
        json.writeValue("occlusionEnabled", component.isOcclusionEnabled());
        json.writeValue("occlusionMargin", component.getOcclusionMargin());
        json.writeValue("occlusionMask", component.getOcclusionMask());
    }

    @Override
    public CameraRigComponent deserialize(JsonValue jsonData, Json json) {
        String mode = jsonData.getString("mode", CameraRigComponent.Mode.FOLLOW.name());
        try {
            CameraRigComponent component = new CameraRigComponent();
            component.setMode(CameraRigComponent.Mode.valueOf(mode));
            component.setTargetName(jsonData.getString("targetName", ""));
            component.setFollowOffset(new Vector3(
                jsonData.getFloat("followOffsetX", CameraRigComponent.DEFAULT_FOLLOW_OFFSET.x),
                jsonData.getFloat("followOffsetY", CameraRigComponent.DEFAULT_FOLLOW_OFFSET.y),
                jsonData.getFloat("followOffsetZ", CameraRigComponent.DEFAULT_FOLLOW_OFFSET.z)));
            component.setFollowRate(jsonData.getFloat("followRate", CameraRigComponent.DEFAULT_FOLLOW_RATE));
            component.setPivotHeight(jsonData.getFloat("pivotHeight", CameraRigComponent.DEFAULT_PIVOT_HEIGHT));
            component.setEyeHeight(jsonData.getFloat("eyeHeight", CameraRigComponent.DEFAULT_EYE_HEIGHT));
            // Limits first: yaw/pitch/distance are held inside them
            component.setPitchLimits(jsonData.getFloat("minPitch", CameraRigComponent.DEFAULT_MIN_PITCH),
                jsonData.getFloat("maxPitch", CameraRigComponent.DEFAULT_MAX_PITCH));
            component.setDistanceLimits(jsonData.getFloat("minDistance", CameraRigComponent.DEFAULT_MIN_DISTANCE),
                jsonData.getFloat("maxDistance", CameraRigComponent.DEFAULT_MAX_DISTANCE));
            component.setYaw(jsonData.getFloat("yaw", 0f));
            component.setPitch(jsonData.getFloat("pitch", CameraRigComponent.DEFAULT_PITCH));
            component.setDistance(jsonData.getFloat("distance", CameraRigComponent.DEFAULT_DISTANCE));
            component.setOcclusionEnabled(jsonData.getBoolean("occlusionEnabled", false));
            component.setOcclusionMargin(jsonData.getFloat("occlusionMargin", CameraRigComponent.DEFAULT_OCCLUSION_MARGIN));
            component.setOcclusionMask(jsonData.getInt("occlusionMask", PhysicsLayers3D.ALL_LAYERS));
            return component;
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Invalid CameraRigComponent (mode '" + mode + "'): " + e.getMessage(), e);
        }
    }
}
