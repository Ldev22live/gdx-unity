package io.github.ldev22live.gdxunity.editor.core.command;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EditorCommandsTest {

    @Test
    void testRenameEntityCommand() {
        EditorState state = new EditorState();
        Entity entity = state.createEntity("OriginalName");
        assertNotNull(entity);
        assertEquals("OriginalName", entity.getName());

        RenameEntityCommand cmd = new RenameEntityCommand(state, entity.getId(), "OriginalName", "NewName");
        assertTrue(state.executeCommand(cmd));
        assertEquals("NewName", entity.getName());

        assertTrue(state.undo());
        assertEquals("OriginalName", entity.getName());

        assertTrue(state.redo());
        assertEquals("NewName", entity.getName());
    }

    @Test
    void testChangeEntityActiveCommand() {
        EditorState state = new EditorState();
        Entity entity = state.createEntity("TestEntity");
        assertTrue(entity.isActiveSelf());

        ChangeEntityActiveCommand cmd = new ChangeEntityActiveCommand(state, entity.getId(), true, false);
        assertTrue(state.executeCommand(cmd));
        assertFalse(entity.isActiveSelf());

        assertTrue(state.undo());
        assertTrue(entity.isActiveSelf());

        assertTrue(state.redo());
        assertFalse(entity.isActiveSelf());
    }

    @Test
    void testChangeTransformCommand() {
        EditorState state = new EditorState();
        Entity entity = state.createEntity("MovingEntity");
        TransformSnapshot before = TransformSnapshot.capture(entity.getTransform());

        entity.getTransform().setLocalPosition(10f, 20f, 0f);
        TransformSnapshot after = TransformSnapshot.capture(entity.getTransform());

        ChangeTransformCommand cmd = new ChangeTransformCommand(state, entity.getId(), before, after, "Move");
        // Command execute applies 'after'
        assertTrue(state.executeCommand(cmd));
        assertEquals(10f, entity.getTransform().getLocalPosition().x);
        assertEquals(20f, entity.getTransform().getLocalPosition().y);

        assertTrue(state.undo());
        assertEquals(0f, entity.getTransform().getLocalPosition().x);
        assertEquals(0f, entity.getTransform().getLocalPosition().y);

        assertTrue(state.redo());
        assertEquals(10f, entity.getTransform().getLocalPosition().x);
        assertEquals(20f, entity.getTransform().getLocalPosition().y);
    }

    @Test
    void testCreateEntityAndUndo() {
        EditorState state = new EditorState();
        assertEquals(0, state.getActiveScene().getEntityCount());

        Entity entity = state.createEntity("Spawned");
        assertNotNull(entity);
        assertEquals(1, state.getActiveScene().getEntityCount());
        UUID entityId = entity.getId();

        assertTrue(state.undo());
        assertEquals(0, state.getActiveScene().getEntityCount());
        assertNull(state.getActiveScene().findEntityById(entityId));

        assertTrue(state.redo());
        assertEquals(1, state.getActiveScene().getEntityCount());
        assertNotNull(state.getActiveScene().findEntityById(entityId));
    }

    @Test
    void testDeleteEntityAndRestore() {
        EditorState state = new EditorState();
        Entity entity = state.createEntity("Victim");
        entity.getTransform().setLocalPosition(50f, 60f, 0f);
        UUID id = entity.getId();
        assertEquals(1, state.getActiveScene().getEntityCount());

        assertTrue(state.deleteSelectedEntity());
        assertEquals(0, state.getActiveScene().getEntityCount());
        assertNull(state.getActiveScene().findEntityById(id));

        assertTrue(state.undo());
        assertEquals(1, state.getActiveScene().getEntityCount());
        Entity restored = state.getActiveScene().findEntityById(id);
        assertNotNull(restored);
        assertEquals("Victim", restored.getName());
        assertEquals(50f, restored.getTransform().getLocalPosition().x);
        assertEquals(60f, restored.getTransform().getLocalPosition().y);

        assertTrue(state.redo());
        assertEquals(0, state.getActiveScene().getEntityCount());
    }

    @Test
    void testAssignSpriteAssetCommand() {
        EditorState state = new EditorState();
        Entity entity = state.createEntity("SpriteEntity");
        SpriteRendererComponent sprite = new SpriteRendererComponent(100f, 100f);
        entity.addComponent(sprite);

        AssetReference asset1 = new AssetReference("textures/hero.png");
        AssetReference asset2 = new AssetReference("textures/enemy.png");

        AssignSpriteAssetCommand cmd1 = new AssignSpriteAssetCommand(state, entity.getId(), null, asset1);
        assertTrue(state.executeCommand(cmd1));
        assertEquals(asset1, sprite.getAssetReference());

        AssignSpriteAssetCommand cmd2 = new AssignSpriteAssetCommand(state, entity.getId(), asset1, asset2);
        assertTrue(state.executeCommand(cmd2));
        assertEquals(asset2, sprite.getAssetReference());

        assertTrue(state.undo());
        assertEquals(asset1, sprite.getAssetReference());

        assertTrue(state.undo());
        assertNull(sprite.getAssetReference());

        assertTrue(state.redo());
        assertEquals(asset1, sprite.getAssetReference());
    }

    @Test
    void testHistoryResetOnSceneSwitch() {
        EditorState state = new EditorState();
        state.createEntity("E1");
        state.createEntity("E2");
        assertTrue(state.getCommandHistory().canUndo());

        state.setActiveScene(new Scene("Another Scene"));
        assertFalse(state.getCommandHistory().canUndo());
        assertFalse(state.getCommandHistory().canRedo());
    }
}
