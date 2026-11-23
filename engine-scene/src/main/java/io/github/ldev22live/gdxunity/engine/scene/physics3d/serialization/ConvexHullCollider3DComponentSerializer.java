package io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ConvexHullCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/** Serializer for {@link ConvexHullCollider3DComponent}: offset and trigger only, the points come from the mesh. */
public class ConvexHullCollider3DComponentSerializer implements ComponentSerializer<ConvexHullCollider3DComponent> {
    public static final String TYPE_ID = "ConvexHullCollider3DComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<ConvexHullCollider3DComponent> getComponentClass() {
        return ConvexHullCollider3DComponent.class;
    }

    @Override
    public void serialize(ConvexHullCollider3DComponent component, Json json) {
        Collider3DJson.write(json, component);
    }

    @Override
    public ConvexHullCollider3DComponent deserialize(JsonValue jsonData, Json json) {
        return Collider3DJson.read(jsonData, new ConvexHullCollider3DComponent());
    }
}
