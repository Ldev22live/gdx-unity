package io.github.ldev22live.gdxunity.engine.scene.serialization;

import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.Component;

/**
 * Handles serialization and deserialization of a specific {@link Component} type.
 */
public interface ComponentSerializer<T extends Component> {
    /**
     * Unique type identifier stored in the JSON document.
     */
    String getTypeId();

    /**
     * Component class handled by this serializer.
     */
    Class<T> getComponentClass();

    /**
     * Serializes component properties into the supplied JSON writer or builder.
     */
    void serialize(T component, com.badlogic.gdx.utils.Json json);

    /**
     * Deserializes component properties from the JSON value.
     */
    T deserialize(JsonValue jsonData, com.badlogic.gdx.utils.Json json);
}
