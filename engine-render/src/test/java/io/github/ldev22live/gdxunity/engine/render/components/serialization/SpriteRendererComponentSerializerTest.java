package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpriteRendererComponentSerializerTest {

    @Test
    void testSerializationRoundTrip() {
        SpriteRendererComponent original = new SpriteRendererComponent(new AssetReference("textures/hero.png"), 64f, 64f);
        original.setCentered(false);
        original.setTint(new Color(1f, 0.5f, 0.2f, 1f));

        SpriteRendererComponentSerializer serializer = new SpriteRendererComponentSerializer();
        Json json = new Json();
        java.io.StringWriter writer = new java.io.StringWriter();
        com.badlogic.gdx.utils.JsonWriter jsonWriter = new com.badlogic.gdx.utils.JsonWriter(writer);
        json.setWriter(jsonWriter);

        json.writeObjectStart();
        serializer.serialize(original, json);
        json.writeObjectEnd();

        String jsonString = writer.toString();
        assertNotNull(jsonString);
        assertTrue(jsonString.contains("textures/hero.png"));

        JsonValue jsonValue = new com.badlogic.gdx.utils.JsonReader().parse(jsonString);
        SpriteRendererComponent deserialized = serializer.deserialize(jsonValue, json);

        assertNotNull(deserialized);
        assertNotNull(deserialized.getAssetReference());
        assertEquals("textures/hero.png", deserialized.getAssetReference().getPath());
        assertEquals(64f, deserialized.getWidth());
        assertEquals(64f, deserialized.getHeight());
        assertFalse(deserialized.isCentered());
        assertEquals(new Color(1f, 0.5f, 0.2f, 1f), deserialized.getTint());
    }

    @Test
    void testUnresolvedOrNullAssetReference() {
        SpriteRendererComponent original = new SpriteRendererComponent(128f, 128f);
        SpriteRendererComponentSerializer serializer = new SpriteRendererComponentSerializer();
        Json json = new Json();
        java.io.StringWriter writer = new java.io.StringWriter();
        com.badlogic.gdx.utils.JsonWriter jsonWriter = new com.badlogic.gdx.utils.JsonWriter(writer);
        json.setWriter(jsonWriter);

        json.writeObjectStart();
        serializer.serialize(original, json);
        json.writeObjectEnd();

        String jsonString = writer.toString();
        JsonValue jsonValue = new com.badlogic.gdx.utils.JsonReader().parse(jsonString);
        SpriteRendererComponent deserialized = serializer.deserialize(jsonValue, json);

        assertNotNull(deserialized);
        assertNull(deserialized.getAssetReference());
        assertEquals(128f, deserialized.getWidth());
        assertEquals(128f, deserialized.getHeight());
    }
}
