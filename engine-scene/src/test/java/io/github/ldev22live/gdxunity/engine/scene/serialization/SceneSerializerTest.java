package io.github.ldev22live.gdxunity.engine.scene.serialization;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SceneSerializerTest {

    static class TestComponent extends BaseComponent {
        String tag = "default";
        int value = 42;

        public TestComponent() {}

        public TestComponent(String tag, int value) {
            this.tag = tag;
            this.value = value;
        }
    }

    static class TestComponentSerializer implements ComponentSerializer<TestComponent> {
        @Override
        public String getTypeId() {
            return "TestComponent";
        }

        @Override
        public Class<TestComponent> getComponentClass() {
            return TestComponent.class;
        }

        @Override
        public void serialize(TestComponent component, Json json) {
            json.writeValue("tag", component.tag);
            json.writeValue("value", component.value);
        }

        @Override
        public TestComponent deserialize(JsonValue jsonData, Json json) {
            String tag = jsonData.getString("tag", "default");
            int val = jsonData.getInt("value", 0);
            return new TestComponent(tag, val);
        }
    }

    private SceneSerializer serializer;

    @BeforeEach
    void setUp() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new TestComponentSerializer());
        serializer = new SceneSerializer(registry);
    }

    @Test
    void shouldSerializeAndDeserializeSceneRoundTrip() {
        Scene original = new Scene("MainScene");

        Entity parent = original.createEntity("ParentNode");
        parent.getTransform().setLocalPosition(10f, 20f, 30f);
        parent.addComponent(new TestComponent("customTag", 999));

        Entity child = original.createEntity("ChildNode");
        child.getTransform().setLocalPosition(1f, 2f, 3f);
        child.getTransform().setParent(parent.getTransform());

        String json = serializer.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"name\": \"MainScene\""));

        // Deserialize
        Scene restored = serializer.fromJson(json);
        assertEquals("MainScene", restored.getName());
        assertEquals(2, restored.getEntityCount());

        Entity restoredParent = restored.findEntityById(parent.getId());
        assertNotNull(restoredParent);
        assertEquals("ParentNode", restoredParent.getName());
        Vector3 parentPos = restoredParent.getTransform().getLocalPosition();
        assertEquals(10f, parentPos.x, 0.001f);
        assertEquals(20f, parentPos.y, 0.001f);
        assertEquals(30f, parentPos.z, 0.001f);

        TestComponent comp = restoredParent.getComponent(TestComponent.class);
        assertNotNull(comp);
        assertEquals("customTag", comp.tag);
        assertEquals(999, comp.value);

        Entity restoredChild = restored.findEntityById(child.getId());
        assertNotNull(restoredChild);
        assertEquals("ChildNode", restoredChild.getName());
        assertEquals(restoredParent.getTransform(), restoredChild.getTransform().getParent());
    }

    @Test
    void shouldRejectUnsupportedFutureSchemaVersion() {
        String json = "{\n" +
            "  \"schemaVersion\": 99,\n" +
            "  \"name\": \"FutureScene\",\n" +
            "  \"entities\": []\n" +
            "}";

        assertThrows(SceneSerializationException.class, () -> serializer.fromJson(json));
    }

    @Test
    void shouldRejectMalformedJson() {
        String json = "{ not valid json";
        assertThrows(SceneSerializationException.class, () -> serializer.fromJson(json));
    }

    @Test
    void shouldRejectDuplicateEntityIdsInJson() {
        UUID id = UUID.randomUUID();
        String json = "{\n" +
            "  \"schemaVersion\": 1,\n" +
            "  \"name\": \"DuplicateScene\",\n" +
            "  \"entities\": [\n" +
            "    { \"id\": \"" + id + "\", \"name\": \"E1\" },\n" +
            "    { \"id\": \"" + id + "\", \"name\": \"E2\" }\n" +
            "  ]\n" +
            "}";

        assertThrows(SceneSerializationException.class, () -> serializer.fromJson(json));
    }

    @Test
    void shouldRejectNonExistentParentReference() {
        UUID id = UUID.randomUUID();
        UUID missingParentId = UUID.randomUUID();
        String json = "{\n" +
            "  \"schemaVersion\": 1,\n" +
            "  \"name\": \"BrokenHierarchy\",\n" +
            "  \"entities\": [\n" +
            "    { \"id\": \"" + id + "\", \"name\": \"Orphan\", \"parentId\": \"" + missingParentId + "\" }\n" +
            "  ]\n" +
            "}";

        assertThrows(SceneSerializationException.class, () -> serializer.fromJson(json));
    }
}
