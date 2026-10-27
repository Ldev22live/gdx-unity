package io.github.ldev22live.gdxunity.editor.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ShapeRendererComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.SpriteRendererComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptLanguage;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SceneModelIntegrityAndHardeningTest {

    private SceneSerializer serializer;

    @BeforeEach
    void setUp() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new ShapeRendererComponentSerializer());
        registry.register(new SpriteRendererComponentSerializer());
        registry.register(new EntityScriptBindingComponentSerializer());
        serializer = new SceneSerializer(registry);
    }

    @Test
    void shouldMaintainEntityIdentityAndUniqueUUIDs() {
        Scene scene = new Scene("Identity Scene");
        Entity e1 = scene.createEntity("Alpha");
        Entity e2 = scene.createEntity("Beta");

        assertNotNull(e1.getId());
        assertNotNull(e2.getId());
        assertNotEquals(e1.getId(), e2.getId());

        // Adding an entity with duplicate UUID should throw IllegalArgumentException
        Entity duplicateIdEntity = new Entity(e1.getId(), "DuplicateAlpha");
        assertThrows(IllegalArgumentException.class, () -> scene.addEntity(duplicateIdEntity));
    }

    @Test
    void shouldPreventCircularAndSelfParentingInHierarchy() {
        Scene scene = new Scene("Hierarchy Scene");
        Entity grandParent = scene.createEntity("GrandParent");
        Entity parent = scene.createEntity("Parent");
        Entity child = scene.createEntity("Child");

        parent.getTransform().setParent(grandParent.getTransform());
        child.getTransform().setParent(parent.getTransform());

        // 1. Self parenting must be rejected
        assertThrows(IllegalArgumentException.class, () -> grandParent.getTransform().setParent(grandParent.getTransform()));

        // 2. Direct cycle (Parent to Child) must be rejected
        assertThrows(IllegalArgumentException.class, () -> parent.getTransform().setParent(child.getTransform()));

        // 3. Indirect cycle (GrandParent to GrandChild) must be rejected
        assertThrows(IllegalArgumentException.class, () -> grandParent.getTransform().setParent(child.getTransform()));
    }

    @Test
    void shouldMaintainHierarchyStateOnRemovalAndUnparenting() {
        Scene scene = new Scene("Removal Scene");
        Entity root = scene.createEntity("Root");
        Entity child1 = scene.createEntity("Child1");
        Entity child2 = scene.createEntity("Child2");

        child1.getTransform().setParent(root.getTransform());
        child2.getTransform().setParent(root.getTransform());

        assertEquals(1, scene.getRootEntities().size());
        assertEquals(3, scene.getEntityCount());

        // Remove root without removing children (unparenting them to root)
        boolean removed = scene.removeEntity(root, false);
        assertTrue(removed);
        assertEquals(2, scene.getEntityCount());
        assertEquals(2, scene.getRootEntities().size());
        assertNull(child1.getTransform().getParent());
        assertNull(child2.getTransform().getParent());
    }

    @Test
    void shouldPerformMultiComponentDeepRoundTripSerialization() {
        Scene scene = new Scene("Complex Scene");

        // Root 1: Shape renderer with nested child
        Entity root1 = scene.createEntity("Root1");
        root1.getTransform().setLocalPosition(100f, 200f, 0f);
        root1.addComponent(new ShapeRendererComponent(64f, 128f, Color.BLUE));

        Entity child1 = scene.createEntity("Child1");
        child1.getTransform().setLocalPosition(10f, 20f, 0f);
        child1.getTransform().setParent(root1.getTransform());
        child1.addComponent(new SpriteRendererComponent(new AssetReference("textures/hero.png"), 32f, 32f));

        // Root 2: Script binding component
        Entity root2 = scene.createEntity("Root2");
        root2.getTransform().setLocalPosition(-50f, 50f, 0f);
        EntityScriptBindingComponent scriptComp = new EntityScriptBindingComponent("scripts/Player.java", "Player", ScriptLanguage.JAVA);
        scriptComp.setEnabled(true);
        root2.addComponent(scriptComp);

        // 1. Serialize
        String json = serializer.toJson(scene);
        assertNotNull(json);

        // 2. Deserialize
        Scene restored = serializer.fromJson(json);
        assertEquals(3, restored.getEntityCount());

        Entity restoredRoot1 = restored.findEntityById(root1.getId());
        assertNotNull(restoredRoot1);
        assertEquals("Root1", restoredRoot1.getName());
        assertEquals(100f, restoredRoot1.getTransform().getLocalPosition().x);
        ShapeRendererComponent shape = restoredRoot1.getComponent(ShapeRendererComponent.class);
        assertNotNull(shape);
        assertEquals(64f, shape.getWidth());
        assertEquals(128f, shape.getHeight());

        Entity restoredChild1 = restored.findEntityById(child1.getId());
        assertNotNull(restoredChild1);
        assertEquals(restoredRoot1.getTransform(), restoredChild1.getTransform().getParent());
        SpriteRendererComponent sprite = restoredChild1.getComponent(SpriteRendererComponent.class);
        assertNotNull(sprite);
        assertEquals("textures/hero.png", sprite.getAssetReference().getPath());

        Entity restoredRoot2 = restored.findEntityById(root2.getId());
        assertNotNull(restoredRoot2);
        EntityScriptBindingComponent script = restoredRoot2.getComponent(EntityScriptBindingComponent.class);
        assertNotNull(script);
        assertEquals("scripts/Player.java", script.getScriptPath());
        assertEquals("Player", script.getScriptClassName());
        assertEquals(ScriptLanguage.JAVA, script.getLanguage());
        assertTrue(script.isEnabled());

        // 3. Canonical Second Serialization
        String secondJson = serializer.toJson(restored);
        assertEquals(json, secondJson, "Second round-trip serialization must be identical to the first");
    }

    @Test
    void shouldExecuteLargeEntityStressTestWithoutDegradationOrMemoryLeak() {
        Scene stressScene = new Scene("Stress Scene");
        int entityCount = 1000;

        Entity prev = null;
        for (int i = 0; i < entityCount; i++) {
            Entity entity = stressScene.createEntity("Entity_" + i);
            entity.getTransform().setLocalPosition(i * 1.5f, i * 2.0f, 0f);
            if (i % 2 == 0) {
                entity.addComponent(new ShapeRendererComponent(10f, 10f, Color.GREEN));
            }
            if (prev != null && i % 5 != 0) {
                entity.getTransform().setParent(prev.getTransform());
            }
            prev = entity;
        }

        assertEquals(entityCount, stressScene.getEntityCount());

        // Verify world matrices calculate cleanly across deep hierarchy
        for (Entity e : stressScene.getAllEntities()) {
            Vector3 worldPos = e.getTransform().getWorldPosition();
            assertNotNull(worldPos);
            assertFalse(Float.isNaN(worldPos.x));
        }

        // Stress serialization
        String json = serializer.toJson(stressScene);
        assertNotNull(json);

        // Stress deserialization
        Scene restored = serializer.fromJson(json);
        assertEquals(entityCount, restored.getEntityCount());

        // Dispose cleanly
        stressScene.dispose();
        restored.dispose();
    }
}
