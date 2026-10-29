package io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/** Serializer for {@link BoxCollider3DComponent}. */
public class BoxCollider3DComponentSerializer implements ComponentSerializer<BoxCollider3DComponent> {
    public static final String TYPE_ID = "BoxCollider3DComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<BoxCollider3DComponent> getComponentClass() {
        return BoxCollider3DComponent.class;
    }

    @Override
    public void serialize(BoxCollider3DComponent component, Json json) {
        Vector3 size = component.getSize();
        json.writeValue("sizeX", size.x);
        json.writeValue("sizeY", size.y);
        json.writeValue("sizeZ", size.z);
        Collider3DJson.write(json, component);
    }

    @Override
    public BoxCollider3DComponent deserialize(JsonValue jsonData, Json json) {
        float d = BoxCollider3DComponent.DEFAULT_SIZE;
        BoxCollider3DComponent component = new BoxCollider3DComponent(new Vector3(
            jsonData.getFloat("sizeX", d), jsonData.getFloat("sizeY", d), jsonData.getFloat("sizeZ", d)));
        return Collider3DJson.read(jsonData, component);
    }
}
