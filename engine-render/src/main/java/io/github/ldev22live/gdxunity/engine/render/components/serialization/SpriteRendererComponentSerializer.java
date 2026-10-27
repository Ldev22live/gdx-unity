package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/**
 * Serializer for {@link SpriteRendererComponent}.
 * Serializes stable {@link AssetReference} path instead of native GPU texture instances.
 */
public class SpriteRendererComponentSerializer implements ComponentSerializer<SpriteRendererComponent> {
    public static final String TYPE_ID = "SpriteRendererComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<SpriteRendererComponent> getComponentClass() {
        return SpriteRendererComponent.class;
    }

    @Override
    public void serialize(SpriteRendererComponent component, Json json) {
        if (component.getAssetReference() != null) {
            json.writeValue("assetPath", component.getAssetReference().getPath());
        } else {
            json.writeValue("assetPath", (String) null);
        }

        json.writeValue("width", component.getWidth());
        json.writeValue("height", component.getHeight());
        json.writeValue("centered", component.isCentered());

        Color c = component.getTint();
        json.writeObjectStart("tint");
        json.writeValue("r", c.r);
        json.writeValue("g", c.g);
        json.writeValue("b", c.b);
        json.writeValue("a", c.a);
        json.writeObjectEnd();
    }

    @Override
    public SpriteRendererComponent deserialize(JsonValue jsonData, Json json) {
        String assetPath = jsonData.getString("assetPath", null);
        AssetReference ref = (assetPath != null && !assetPath.isBlank() && !assetPath.equalsIgnoreCase("null"))
            ? new AssetReference(assetPath)
            : null;

        float width = jsonData.getFloat("width", 50f);
        float height = jsonData.getFloat("height", 50f);
        boolean centered = jsonData.getBoolean("centered", true);

        Color tint = Color.WHITE;
        JsonValue tintVal = jsonData.get("tint");
        if (tintVal != null) {
            tint = new Color(
                tintVal.getFloat("r", 1f),
                tintVal.getFloat("g", 1f),
                tintVal.getFloat("b", 1f),
                tintVal.getFloat("a", 1f)
            );
        }

        SpriteRendererComponent component = new SpriteRendererComponent();
        component.setAssetReference(ref);
        component.setWidth(width);
        component.setHeight(height);
        component.setCentered(centered);
        component.setTint(tint);
        return component;
    }
}
