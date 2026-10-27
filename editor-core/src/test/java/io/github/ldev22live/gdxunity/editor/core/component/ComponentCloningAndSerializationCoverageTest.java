package io.github.ldev22live.gdxunity.editor.core.component;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.DuplicateEntityCommand;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptLanguage;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Systematically tests cloning, serialization, and deserialization coverage
 * for every registered component type in GdxUnity.
 */
class ComponentCloningAndSerializationCoverageTest {
    private EditorState editorState;
    private Scene scene;
    private SceneSerializer serializer;

    @BeforeEach
    void setUp() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new io.github.ldev22live.gdxunity.engine.render.components.serialization.ShapeRendererComponentSerializer());
        registry.register(new io.github.ldev22live.gdxunity.engine.render.components.serialization.SpriteRendererComponentSerializer());
        registry.register(new io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponentSerializer());
        registry.register(new io.github.ldev22live.gdxunity.engine.scene.physics.serialization.RigidBody2DComponentSerializer());
        registry.register(new io.github.ldev22live.gdxunity.engine.scene.physics.serialization.BoxCollider2DComponentSerializer());
        registry.register(new io.github.ldev22live.gdxunity.engine.scene.physics.serialization.CircleCollider2DComponentSerializer());
        serializer = new SceneSerializer(registry);
        editorState = new EditorState(serializer);
        scene = new Scene("ComponentTestScene");
        editorState.setActiveScene(scene);
    }

    @Test
    void shouldCloneAndSerializeShapeRendererComponentStateAccurately() {
        Entity entity = scene.createEntity("ShapeHolder");
        ShapeRendererComponent shape = new ShapeRendererComponent(64f, 48f, new Color(0.2f, 0.4f, 0.8f, 0.9f));
        shape.setShapeType(ShapeRendererComponent.ShapeType.LINE);
        shape.setCentered(false);
        entity.addComponent(shape);

        // 1. Verify Duplication
        DuplicateEntityCommand duplicateCmd = new DuplicateEntityCommand(editorState, entity.getId());
        assertTrue(editorState.executeCommand(duplicateCmd));
        Entity clone = duplicateCmd.getDuplicatedEntity();
        assertNotNull(clone);
        assertNotEquals(entity.getId(), clone.getId());

        ShapeRendererComponent clonedShape = clone.getComponent(ShapeRendererComponent.class);
        assertNotNull(clonedShape);
        assertNotSame(shape, clonedShape);
        assertEquals(64f, clonedShape.getWidth(), 0.001f);
        assertEquals(48f, clonedShape.getHeight(), 0.001f);
        assertEquals(shape.getColor(), clonedShape.getColor());

        // 2. Verify Serialization Round-Trip
        String json = serializer.toJson(scene);
        Scene restoredScene = serializer.fromJson(json);
        Entity restoredEntity = restoredScene.findEntityById(entity.getId());
        assertNotNull(restoredEntity);

        ShapeRendererComponent restoredShape = restoredEntity.getComponent(ShapeRendererComponent.class);
        assertNotNull(restoredShape);
        assertEquals(64f, restoredShape.getWidth(), 0.001f);
        assertEquals(48f, restoredShape.getHeight(), 0.001f);
        assertEquals(ShapeRendererComponent.ShapeType.LINE, restoredShape.getShapeType());
        assertFalse(restoredShape.isCentered());
        assertEquals(new Color(0.2f, 0.4f, 0.8f, 0.9f), restoredShape.getColor());
    }

    @Test
    void shouldCloneAndSerializeSpriteRendererComponentStateAccurately() {
        Entity entity = scene.createEntity("SpriteHolder");
        AssetReference assetRef = new AssetReference("textures/player.png");
        SpriteRendererComponent sprite = new SpriteRendererComponent(assetRef, 128f, 128f);
        sprite.setTint(new Color(1f, 0.5f, 0.2f, 1f));
        sprite.setCentered(false);
        entity.addComponent(sprite);

        // 1. Verify Duplication
        DuplicateEntityCommand duplicateCmd = new DuplicateEntityCommand(editorState, entity.getId());
        assertTrue(editorState.executeCommand(duplicateCmd));
        Entity clone = duplicateCmd.getDuplicatedEntity();
        assertNotNull(clone);

        SpriteRendererComponent clonedSprite = clone.getComponent(SpriteRendererComponent.class);
        assertNotNull(clonedSprite);
        assertNotSame(sprite, clonedSprite);
        assertEquals(128f, clonedSprite.getWidth(), 0.001f);
        assertEquals(128f, clonedSprite.getHeight(), 0.001f);
        assertEquals("textures/player.png", clonedSprite.getAssetReference().getPath());

        // 2. Verify Serialization Round-Trip
        String json = serializer.toJson(scene);
        Scene restoredScene = serializer.fromJson(json);
        Entity restoredEntity = restoredScene.findEntityById(entity.getId());
        assertNotNull(restoredEntity);

        SpriteRendererComponent restoredSprite = restoredEntity.getComponent(SpriteRendererComponent.class);
        assertNotNull(restoredSprite);
        assertEquals("textures/player.png", restoredSprite.getAssetReference().getPath());
        assertEquals(128f, restoredSprite.getWidth(), 0.001f);
        assertEquals(128f, restoredSprite.getHeight(), 0.001f);
        assertFalse(restoredSprite.isCentered());
        assertEquals(new Color(1f, 0.5f, 0.2f, 1f), restoredSprite.getTint());
    }

    @Test
    void shouldCloneAndSerializeEntityScriptBindingComponentStateAccurately() {
        Entity entity = scene.createEntity("ScriptHolder");
        EntityScriptBindingComponent scriptBinding = new EntityScriptBindingComponent(
            "scripts/EnemyMovement.java",
            "EnemyMovement",
            ScriptLanguage.JAVA
        );
        scriptBinding.setEnabled(true);
        entity.addComponent(scriptBinding);

        // 1. Verify Duplication
        DuplicateEntityCommand duplicateCmd = new DuplicateEntityCommand(editorState, entity.getId());
        assertTrue(editorState.executeCommand(duplicateCmd));
        Entity clone = duplicateCmd.getDuplicatedEntity();
        assertNotNull(clone);

        EntityScriptBindingComponent clonedScript = clone.getComponent(EntityScriptBindingComponent.class);
        assertNotNull(clonedScript);
        assertNotSame(scriptBinding, clonedScript);
        assertEquals("scripts/EnemyMovement.java", clonedScript.getScriptPath());
        assertEquals("EnemyMovement", clonedScript.getScriptClassName());
        assertEquals(ScriptLanguage.JAVA, clonedScript.getLanguage());
        assertTrue(clonedScript.isEnabled());

        // 2. Verify Serialization Round-Trip
        String json = serializer.toJson(scene);
        Scene restoredScene = serializer.fromJson(json);
        Entity restoredEntity = restoredScene.findEntityById(entity.getId());
        assertNotNull(restoredEntity);

        EntityScriptBindingComponent restoredScript = restoredEntity.getComponent(EntityScriptBindingComponent.class);
        assertNotNull(restoredScript);
        assertEquals("scripts/EnemyMovement.java", restoredScript.getScriptPath());
        assertEquals("EnemyMovement", restoredScript.getScriptClassName());
        assertEquals(ScriptLanguage.JAVA, restoredScript.getLanguage());
        assertTrue(restoredScript.isEnabled());
    }

    @Test
    void shouldCloneAndSerializeRigidBody2DComponentStateAccurately() {
        Entity entity = scene.createEntity("RigidBodyHolder");
        var rb = new io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent(
            io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent.BodyType.DYNAMIC
        );
        rb.setGravityScale(2.5f);
        rb.setLinearDamping(0.1f);
        rb.setAngularDamping(0.2f);
        rb.setFixedRotation(true);
        rb.setBullet(true);
        rb.setInitialLinearVelocity(10.0f, -5.0f);
        rb.setInitialAngularVelocity(1.5f);
        entity.addComponent(rb);

        // 1. Verify Duplication
        DuplicateEntityCommand duplicateCmd = new DuplicateEntityCommand(editorState, entity.getId());
        assertTrue(editorState.executeCommand(duplicateCmd));
        Entity clone = duplicateCmd.getDuplicatedEntity();
        assertNotNull(clone);

        var clonedRb = clone.getComponent(io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent.class);
        assertNotNull(clonedRb);
        assertNotSame(rb, clonedRb);
        assertEquals(io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent.BodyType.DYNAMIC, clonedRb.getBodyType());
        assertEquals(2.5f, clonedRb.getGravityScale(), 0.001f);
        assertEquals(0.1f, clonedRb.getLinearDamping(), 0.001f);
        assertEquals(0.2f, clonedRb.getAngularDamping(), 0.001f);
        assertTrue(clonedRb.isFixedRotation());
        assertTrue(clonedRb.isBullet());
        assertEquals(10.0f, clonedRb.getInitialLinearVelocity().x, 0.001f);
        assertEquals(-5.0f, clonedRb.getInitialLinearVelocity().y, 0.001f);
        assertEquals(1.5f, clonedRb.getInitialAngularVelocity(), 0.001f);

        // 2. Verify Serialization Round-Trip
        String json = serializer.toJson(scene);
        Scene restoredScene = serializer.fromJson(json);
        Entity restoredEntity = restoredScene.findEntityById(entity.getId());
        assertNotNull(restoredEntity);

        var restoredRb = restoredEntity.getComponent(io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent.class);
        assertNotNull(restoredRb);
        assertEquals(io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent.BodyType.DYNAMIC, restoredRb.getBodyType());
        assertEquals(2.5f, restoredRb.getGravityScale(), 0.001f);
        assertEquals(0.1f, restoredRb.getLinearDamping(), 0.001f);
        assertEquals(0.2f, restoredRb.getAngularDamping(), 0.001f);
        assertTrue(restoredRb.isFixedRotation());
        assertTrue(restoredRb.isBullet());
        assertEquals(10.0f, restoredRb.getInitialLinearVelocity().x, 0.001f);
        assertEquals(-5.0f, restoredRb.getInitialLinearVelocity().y, 0.001f);
        assertEquals(1.5f, restoredRb.getInitialAngularVelocity(), 0.001f);
    }

    @Test
    void shouldCloneAndSerializeBoxCollider2DComponentStateAccurately() {
        Entity entity = scene.createEntity("BoxColliderHolder");
        var box = new io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent(80.0f, 40.0f);
        box.setOffset(5.0f, -10.0f);
        box.setDensity(2.0f);
        box.setFriction(0.5f);
        box.setRestitution(0.75f);
        box.setSensor(true);
        box.setCategoryBits((short) 0x0002);
        box.setMaskBits((short) 0x0004);
        entity.addComponent(box);

        // 1. Verify Duplication
        DuplicateEntityCommand duplicateCmd = new DuplicateEntityCommand(editorState, entity.getId());
        assertTrue(editorState.executeCommand(duplicateCmd));
        Entity clone = duplicateCmd.getDuplicatedEntity();
        assertNotNull(clone);

        var clonedBox = clone.getComponent(io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent.class);
        assertNotNull(clonedBox);
        assertNotSame(box, clonedBox);
        assertEquals(80.0f, clonedBox.getWidth(), 0.001f);
        assertEquals(40.0f, clonedBox.getHeight(), 0.001f);
        assertEquals(5.0f, clonedBox.getOffset().x, 0.001f);
        assertEquals(-10.0f, clonedBox.getOffset().y, 0.001f);
        assertEquals(2.0f, clonedBox.getDensity(), 0.001f);
        assertEquals(0.5f, clonedBox.getFriction(), 0.001f);
        assertEquals(0.75f, clonedBox.getRestitution(), 0.001f);
        assertTrue(clonedBox.isSensor());
        assertEquals((short) 0x0002, clonedBox.getCategoryBits());
        assertEquals((short) 0x0004, clonedBox.getMaskBits());

        // 2. Verify Serialization Round-Trip
        String json = serializer.toJson(scene);
        Scene restoredScene = serializer.fromJson(json);
        Entity restoredEntity = restoredScene.findEntityById(entity.getId());
        assertNotNull(restoredEntity);

        var restoredBox = restoredEntity.getComponent(io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent.class);
        assertNotNull(restoredBox);
        assertEquals(80.0f, restoredBox.getWidth(), 0.001f);
        assertEquals(40.0f, restoredBox.getHeight(), 0.001f);
        assertEquals(5.0f, restoredBox.getOffset().x, 0.001f);
        assertEquals(-10.0f, restoredBox.getOffset().y, 0.001f);
        assertEquals(2.0f, restoredBox.getDensity(), 0.001f);
        assertEquals(0.5f, restoredBox.getFriction(), 0.001f);
        assertEquals(0.75f, restoredBox.getRestitution(), 0.001f);
        assertTrue(restoredBox.isSensor());
        assertEquals((short) 0x0002, restoredBox.getCategoryBits());
        assertEquals((short) 0x0004, restoredBox.getMaskBits());
    }

    @Test
    void shouldCloneAndSerializeCircleCollider2DComponentStateAccurately() {
        Entity entity = scene.createEntity("CircleColliderHolder");
        var circle = new io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent(35.0f);
        circle.setOffset(-2.0f, 3.0f);
        circle.setDensity(1.5f);
        circle.setFriction(0.3f);
        circle.setRestitution(0.9f);
        circle.setSensor(false);
        entity.addComponent(circle);

        // 1. Verify Duplication
        DuplicateEntityCommand duplicateCmd = new DuplicateEntityCommand(editorState, entity.getId());
        assertTrue(editorState.executeCommand(duplicateCmd));
        Entity clone = duplicateCmd.getDuplicatedEntity();
        assertNotNull(clone);

        var clonedCircle = clone.getComponent(io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent.class);
        assertNotNull(clonedCircle);
        assertNotSame(circle, clonedCircle);
        assertEquals(35.0f, clonedCircle.getRadius(), 0.001f);
        assertEquals(-2.0f, clonedCircle.getOffset().x, 0.001f);
        assertEquals(3.0f, clonedCircle.getOffset().y, 0.001f);
        assertEquals(1.5f, clonedCircle.getDensity(), 0.001f);
        assertEquals(0.3f, clonedCircle.getFriction(), 0.001f);
        assertEquals(0.9f, clonedCircle.getRestitution(), 0.001f);
        assertFalse(clonedCircle.isSensor());

        // 2. Verify Serialization Round-Trip
        String json = serializer.toJson(scene);
        Scene restoredScene = serializer.fromJson(json);
        Entity restoredEntity = restoredScene.findEntityById(entity.getId());
        assertNotNull(restoredEntity);

        var restoredCircle = restoredEntity.getComponent(io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent.class);
        assertNotNull(restoredCircle);
        assertEquals(35.0f, restoredCircle.getRadius(), 0.001f);
        assertEquals(-2.0f, restoredCircle.getOffset().x, 0.001f);
        assertEquals(3.0f, restoredCircle.getOffset().y, 0.001f);
        assertEquals(1.5f, restoredCircle.getDensity(), 0.001f);
        assertEquals(0.3f, restoredCircle.getFriction(), 0.001f);
        assertEquals(0.9f, restoredCircle.getRestitution(), 0.001f);
        assertFalse(restoredCircle.isSensor());
    }

    @Test
    void shouldCloneAndSerializeMultipleCollidersOnSameEntityWithoutVectorSharing() {
        Entity compound = scene.createEntity("CompoundCarrier");
        var box1 = new io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent(40f, 20f);
        box1.setOffset(-15f, 0f);
        var box2 = new io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent(10f, 30f);
        box2.setOffset(15f, 0f);
        var circle = new io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent(12f);
        circle.setOffset(0f, 20f);

        compound.addComponent(box1);
        compound.addComponent(box2);
        compound.addComponent(circle);

        DuplicateEntityCommand duplicateCmd = new DuplicateEntityCommand(editorState, compound.getId());
        assertTrue(editorState.executeCommand(duplicateCmd));
        Entity clone = duplicateCmd.getDuplicatedEntity();
        assertNotNull(clone);

        var clonedBoxes = clone.getComponents(io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent.class);
        var clonedCircles = clone.getComponents(io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent.class);
        assertEquals(2, clonedBoxes.size());
        assertEquals(1, clonedCircles.size());

        // Ensure vectors are mutable and independent (not shared references)
        clonedBoxes.get(0).setOffset(999f, 999f);
        assertEquals(-15f, box1.getOffset().x, 0.001f);

        // Serialization round trip of compound entity
        String json = serializer.toJson(scene);
        Scene restoredScene = serializer.fromJson(json);
        Entity restored = restoredScene.findEntityById(compound.getId());
        assertNotNull(restored);
        assertEquals(2, restored.getComponents(io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent.class).size());
        assertEquals(1, restored.getComponents(io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent.class).size());
    }
}

