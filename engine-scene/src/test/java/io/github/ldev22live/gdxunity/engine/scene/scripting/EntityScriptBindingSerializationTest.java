package io.github.ldev22live.gdxunity.engine.scene.scripting;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityScriptBindingSerializationTest {

    @Test
    void testSerializerDirectRoundTrip() {
        EntityScriptBindingComponent original = new EntityScriptBindingComponent(
            "scripts/EnemyAI.kt",
            "game.EnemyAI",
            ScriptLanguage.KOTLIN
        );
        original.setEnabled(false);

        EntityScriptBindingComponentSerializer serializer = new EntityScriptBindingComponentSerializer();
        Json json = new Json();
        java.io.StringWriter writer = new java.io.StringWriter();
        com.badlogic.gdx.utils.JsonWriter jsonWriter = new com.badlogic.gdx.utils.JsonWriter(writer);
        json.setWriter(jsonWriter);

        json.writeObjectStart();
        serializer.serialize(original, json);
        json.writeObjectEnd();

        String jsonStr = writer.toString();
        assertNotNull(jsonStr);
        assertTrue(jsonStr.contains("EnemyAI.kt"));
        assertTrue(jsonStr.contains("game.EnemyAI"));
        assertTrue(jsonStr.contains("KOTLIN"));

        JsonValue jsonValue = new JsonReader().parse(jsonStr);
        EntityScriptBindingComponent deserialized = serializer.deserialize(jsonValue, json);

        assertEquals("scripts/EnemyAI.kt", deserialized.getScriptPath());
        assertEquals("game.EnemyAI", deserialized.getScriptClassName());
        assertEquals(ScriptLanguage.KOTLIN, deserialized.getLanguage());
    }

    @Test
    void testSceneSerializationWithScriptBinding() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new EntityScriptBindingComponentSerializer());
        SceneSerializer sceneSerializer = new SceneSerializer(registry);

        Scene scene = new Scene("ScriptedScene");
        Entity entity = scene.createEntity("Hero");
        EntityScriptBindingComponent comp = new EntityScriptBindingComponent(
            "scripts/HeroController.java",
            "HeroController",
            ScriptLanguage.JAVA
        );
        entity.addComponent(comp);

        String sceneJson = sceneSerializer.toJson(scene);
        assertNotNull(sceneJson);
        assertTrue(sceneJson.contains("HeroController"));

        Scene loaded = sceneSerializer.fromJson(sceneJson);
        assertNotNull(loaded);
        Entity loadedEntity = loaded.findEntityByName("Hero");
        assertNotNull(loadedEntity);

        EntityScriptBindingComponent loadedComp = loadedEntity.getComponent(EntityScriptBindingComponent.class);
        assertNotNull(loadedComp);
        assertEquals("scripts/HeroController.java", loadedComp.getScriptPath());
        assertEquals("HeroController", loadedComp.getScriptClassName());
        assertEquals(ScriptLanguage.JAVA, loadedComp.getLanguage());
    }
}
