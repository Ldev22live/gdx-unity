package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/** Serializer for {@link GraphScriptComponent}; only the asset path is stored. */
public class GraphScriptComponentSerializer implements ComponentSerializer<GraphScriptComponent> {
    public static final String TYPE_ID = "GraphScriptComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<GraphScriptComponent> getComponentClass() {
        return GraphScriptComponent.class;
    }

    @Override
    public void serialize(GraphScriptComponent component, Json json) {
        json.writeValue("graph", component.getGraphPath());
    }

    @Override
    public GraphScriptComponent deserialize(JsonValue jsonData, Json json) {
        String path = jsonData.getString("graph", null);
        if (path == null || path.isBlank()) {
            throw new SceneSerializationException("GraphScriptComponent requires 'graph'");
        }
        return new GraphScriptComponent(path);
    }
}
