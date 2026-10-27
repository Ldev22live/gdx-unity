package io.github.ldev22live.gdxunity.engine.scene.serialization;

import io.github.ldev22live.gdxunity.engine.scene.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry mapping component types and type strings to {@link ComponentSerializer}s.
 */
public final class ComponentSerializerRegistry {
    private final Map<String, ComponentSerializer<?>> serializersByTypeId = new LinkedHashMap<>();
    private final Map<Class<? extends Component>, ComponentSerializer<?>> serializersByClass = new LinkedHashMap<>();

    public synchronized <T extends Component> void register(ComponentSerializer<T> serializer) {
        Objects.requireNonNull(serializer, "serializer cannot be null");
        serializersByTypeId.put(serializer.getTypeId(), serializer);
        serializersByClass.put(serializer.getComponentClass(), serializer);
    }

    public synchronized ComponentSerializer<?> getByTypeId(String typeId) {
        return serializersByTypeId.get(typeId);
    }

    @SuppressWarnings("unchecked")
    public synchronized <T extends Component> ComponentSerializer<T> getByClass(Class<T> clazz) {
        return (ComponentSerializer<T>) serializersByClass.get(clazz);
    }

    public synchronized Map<String, ComponentSerializer<?>> getAll() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(serializersByTypeId));
    }
}
