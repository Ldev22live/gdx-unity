package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

/**
 * Serializer for {@link TextComponent}. Keys: {@code text}, {@code colorR/G/B/A}, {@code scale}, {@code anchor},
 * {@code offsetX}, {@code offsetY}; missing keys take the component defaults.
 */
public class TextComponentSerializer implements ComponentSerializer<TextComponent> {
    public static final String TYPE_ID = "TextComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<TextComponent> getComponentClass() {
        return TextComponent.class;
    }

    @Override
    public void serialize(TextComponent component, Json json) {
        json.writeValue("text", component.getText());
        Color color = component.getColor();
        json.writeValue("colorR", color.r);
        json.writeValue("colorG", color.g);
        json.writeValue("colorB", color.b);
        json.writeValue("colorA", color.a);
        json.writeValue("scale", component.getScale());
        json.writeValue("anchor", component.getAnchor().name());
        json.writeValue("offsetX", component.getOffsetX());
        json.writeValue("offsetY", component.getOffsetY());
    }

    @Override
    public TextComponent deserialize(JsonValue jsonData, Json json) {
        TextComponent component = new TextComponent(jsonData.getString("text", ""));
        String anchor = jsonData.getString("anchor", component.getAnchor().name());
        try {
            component.setColor(new Color(jsonData.getFloat("colorR", 1f), jsonData.getFloat("colorG", 1f),
                jsonData.getFloat("colorB", 1f), jsonData.getFloat("colorA", 1f)));
            component.setScale(jsonData.getFloat("scale", TextComponent.DEFAULT_SCALE));
            component.setAnchor(TextComponent.Anchor.valueOf(anchor));
            component.setOffset(jsonData.getFloat("offsetX", component.getOffsetX()),
                jsonData.getFloat("offsetY", component.getOffsetY()));
            return component;
        } catch (IllegalArgumentException e) {
            throw new SceneSerializationException("Invalid TextComponent (anchor '" + anchor + "'): " + e.getMessage(), e);
        }
    }
}
