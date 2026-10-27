package io.github.ldev22live.gdxunity.engine.scene.scripting;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/**
 * Serializer for {@link EntityScriptBindingComponent}.
 * Persists script path, class name, language, and enabled state.
 * Never serializes runtime class instances or transient state.
 */
public class EntityScriptBindingComponentSerializer implements ComponentSerializer<EntityScriptBindingComponent> {
    public static final String TYPE_ID = "EntityScriptBindingComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<EntityScriptBindingComponent> getComponentClass() {
        return EntityScriptBindingComponent.class;
    }

    @Override
    public void serialize(EntityScriptBindingComponent component, Json json) {
        json.writeValue("scriptPath", component.getScriptPath());
        json.writeValue("scriptClassName", component.getScriptClassName());
        json.writeValue("language", component.getLanguage() != null ? component.getLanguage().name() : null);
    }

    @Override
    public EntityScriptBindingComponent deserialize(JsonValue jsonData, Json json) {
        EntityScriptBindingComponent component = new EntityScriptBindingComponent();
        component.setScriptPath(jsonData.getString("scriptPath", null));
        component.setScriptClassName(jsonData.getString("scriptClassName", null));

        String langName = jsonData.getString("language", null);
        if (langName != null && !langName.isBlank()) {
            try {
                component.setLanguage(ScriptLanguage.valueOf(langName.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // fallback to path detection
            }
        }
        return component;
    }
}
