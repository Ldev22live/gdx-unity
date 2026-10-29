package io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Collider3DComponent;

/** Offset and trigger fields shared by the 3D collider serializers. */
final class Collider3DJson {

    private Collider3DJson() {}

    static void write(Json json, Collider3DComponent collider) {
        Vector3 offset = collider.getOffset();
        json.writeValue("offsetX", offset.x);
        json.writeValue("offsetY", offset.y);
        json.writeValue("offsetZ", offset.z);
        json.writeValue("trigger", collider.isTrigger());
    }

    static <C extends Collider3DComponent> C read(JsonValue data, C collider) {
        collider.setOffset(new Vector3(data.getFloat("offsetX", 0f), data.getFloat("offsetY", 0f), data.getFloat("offsetZ", 0f)));
        collider.setTrigger(data.getBoolean("trigger", false));
        return collider;
    }
}
