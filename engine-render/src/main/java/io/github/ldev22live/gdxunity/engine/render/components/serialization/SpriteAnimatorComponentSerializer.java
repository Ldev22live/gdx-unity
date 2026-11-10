package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;

/** Serializer for {@link SpriteAnimatorComponent}: the sprite's asset path and the playback settings; the loaded pixels are never written. */
public class SpriteAnimatorComponentSerializer implements ComponentSerializer<SpriteAnimatorComponent> {
    public static final String TYPE_ID = "SpriteAnimatorComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<SpriteAnimatorComponent> getComponentClass() {
        return SpriteAnimatorComponent.class;
    }

    @Override
    public void serialize(SpriteAnimatorComponent component, Json json) {
        json.writeValue("assetPath", component.getAssetReference() != null ? component.getAssetReference().getPath() : null);
        json.writeValue("tag", component.getTag());
        json.writeValue("slice", component.getSlice());
        json.writeValue("speed", component.getSpeed());
        json.writeValue("loop", component.isLoop());
        json.writeValue("playOnStart", component.isPlayOnStart());
        json.writeValue("flipX", component.isFlipX());
        json.writeValue("flipY", component.isFlipY());
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
    public SpriteAnimatorComponent deserialize(JsonValue jsonData, Json json) {
        SpriteAnimatorComponent component = new SpriteAnimatorComponent();
        String path = jsonData.getString("assetPath", null);
        if (path != null && !path.isBlank()) {
            component.setAssetReference(new AssetReference(path));
        }
        component.setTag(jsonData.getString("tag", null));
        component.setSlice(jsonData.getString("slice", null));
        component.setSpeed(jsonData.getFloat("speed", 1f));
        component.setLoop(jsonData.getBoolean("loop", true));
        component.setPlayOnStart(jsonData.getBoolean("playOnStart", true));
        component.setFlipX(jsonData.getBoolean("flipX", false));
        component.setFlipY(jsonData.getBoolean("flipY", false));
        component.setWidth(jsonData.getFloat("width", 0f));
        component.setHeight(jsonData.getFloat("height", 0f));
        component.setCentered(jsonData.getBoolean("centered", true));
        JsonValue tint = jsonData.get("tint");
        if (tint != null) {
            component.setTint(new Color(tint.getFloat("r", 1f), tint.getFloat("g", 1f), tint.getFloat("b", 1f), tint.getFloat("a", 1f)));
        }
        return component;
    }
}
