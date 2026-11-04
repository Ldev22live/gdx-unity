package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/**
 * Serializer for {@link CameraComponent}. Keys: {@code projection}, {@code orthographicHeight}, {@code fieldOfView},
 * {@code nearPlane}, {@code farPlane}; missing keys take the component defaults.
 */
public class CameraComponentSerializer implements ComponentSerializer<CameraComponent> {
    public static final String TYPE_ID = "CameraComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<CameraComponent> getComponentClass() {
        return CameraComponent.class;
    }

    @Override
    public void serialize(CameraComponent component, Json json) {
        json.writeValue("projection", component.getProjection().name());
        json.writeValue("orthographicHeight", component.getOrthographicHeight());
        json.writeValue("fieldOfView", component.getFieldOfView());
        json.writeValue("nearPlane", component.getNearPlane());
        json.writeValue("farPlane", component.getFarPlane());
    }

    @Override
    public CameraComponent deserialize(JsonValue jsonData, Json json) {
        String projection = jsonData.getString("projection", CameraComponent.Projection.ORTHOGRAPHIC.name());
        try {
            CameraComponent component = new CameraComponent(CameraComponent.Projection.valueOf(projection));
            component.setOrthographicHeight(jsonData.getFloat("orthographicHeight", CameraComponent.DEFAULT_ORTHOGRAPHIC_HEIGHT));
            component.setFieldOfView(jsonData.getFloat("fieldOfView", CameraComponent.DEFAULT_FIELD_OF_VIEW));
            component.setClipPlanes(jsonData.getFloat("nearPlane", CameraComponent.DEFAULT_NEAR_PLANE),
                jsonData.getFloat("farPlane", CameraComponent.DEFAULT_FAR_PLANE));
            return component;
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Invalid CameraComponent (projection '" + projection + "'): "
                + e.getMessage(), e);
        }
    }
}
