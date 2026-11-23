package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.math.Vector2;
import io.github.ldev22live.gdxunity.engine.render.GdxTestHelper;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainPathComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap M3.7: a Terrain Path survives save and load with every value, and a bad kind is refused with its name. */
class TerrainPathComponentSerializerTest {
    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new TerrainPathComponentSerializer());
        return new SceneSerializer(registry);
    }

    @Test
    void shouldRoundTripEveryValueOfAPath() {
        Scene scene = new Scene("Level");
        Entity entity = scene.createEntity("River");
        TerrainPathComponent path = new TerrainPathComponent();
        path.setKind(TerrainPath.Kind.RIVER);
        path.setWidth(90f);
        path.setDepth(25f);
        path.setPaint(Surface.SAND);
        path.setPrefabPath("prefabs/level/Rock.prefab");
        path.setSpacing(250f);
        path.setPoints(List.of(new Vector2(-100f, 20f), new Vector2(0f, 0f), new Vector2(150.5f, -30f)));
        entity.addComponent(path);

        String json = serializer().toJson(scene);
        TerrainPathComponent back = serializer().fromJson(json).findEntityByName("River").getComponent(TerrainPathComponent.class);

        assertEquals(TerrainPath.Kind.RIVER, back.getKind());
        assertEquals(90f, back.getWidth());
        assertEquals(25f, back.getDepth());
        assertEquals(Surface.SAND, back.getPaint());
        assertEquals("prefabs/level/Rock.prefab", back.getPrefabPath());
        assertEquals(250f, back.getSpacing());
        assertEquals(path.getPoints(), back.getPoints());
        assertTrue(json.matches("(?s).*\"kind\":\\s*\"river\".*"), "the kind is saved by name: " + json);
    }

    @Test
    void shouldRefuseAnUnknownKindWithItsName() {
        String saved = serializer().toJson(sceneWith());
        String json = saved.replaceAll("\"kind\":\\s*\"road\"", "\"kind\": \"canal\"");
        assertTrue(!json.equals(saved), "the test changed the kind");

        RuntimeException error = assertThrows(RuntimeException.class, () -> serializer().fromJson(json));
        Throwable cause = error;
        boolean named = false;
        while (cause != null) {
            named |= cause.getMessage() != null && cause.getMessage().contains("canal");
            cause = cause.getCause();
        }
        assertTrue(named, "the error names the unknown kind");
    }

    private static Scene sceneWith() {
        Scene scene = new Scene("Level");
        scene.createEntity("Road").addComponent(new TerrainPathComponent());
        return scene;
    }
}
