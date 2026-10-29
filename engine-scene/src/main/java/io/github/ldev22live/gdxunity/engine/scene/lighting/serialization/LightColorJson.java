package io.github.ldev22live.gdxunity.engine.scene.lighting.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/** Shared RGBA field layout for light colors. */
final class LightColorJson {

    private LightColorJson() {}

    static void write(Json json, Color color) {
        json.writeValue("colorR", color.r);
        json.writeValue("colorG", color.g);
        json.writeValue("colorB", color.b);
        json.writeValue("colorA", color.a);
    }

    static Color read(JsonValue jsonData, Color fallback) {
        return new Color(
            jsonData.getFloat("colorR", fallback.r),
            jsonData.getFloat("colorG", fallback.g),
            jsonData.getFloat("colorB", fallback.b),
            jsonData.getFloat("colorA", fallback.a));
    }
}
