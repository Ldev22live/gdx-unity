package io.github.ldev22live.gdxunity.editor.core.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;

import static org.junit.jupiter.api.Assertions.*;

class ComponentCommandTest {

    private EditorState editorState;
    private Entity entity;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        editorState.newScene("Test Scene");
        entity = editorState.createEntity("Test Entity");
    }

    @Test
    void testRemoveComponentCommandExecuteAndUndo() {
        RigidBody2DComponent rb = new RigidBody2DComponent();
        entity.addComponent(rb);
        assertNotNull(entity.getComponent(RigidBody2DComponent.class));

        RemoveComponentCommand removeCmd = new RemoveComponentCommand(editorState, entity.getId(), rb);
        assertTrue(editorState.getCommandHistory().execute(removeCmd));
        assertNull(entity.getComponent(RigidBody2DComponent.class));

        // Undo
        assertTrue(editorState.getCommandHistory().canUndo());
        editorState.undo();
        assertNotNull(entity.getComponent(RigidBody2DComponent.class));

        // Redo
        assertTrue(editorState.getCommandHistory().canRedo());
        editorState.redo();
        assertNull(entity.getComponent(RigidBody2DComponent.class));
    }

    @Test
    void testModifyComponentPropertyCommandExecuteAndUndo() {
        BoxCollider2DComponent collider = new BoxCollider2DComponent(100f, 50f);
        entity.addComponent(collider);

        assertEquals(100f, collider.getWidth());

        ModifyComponentPropertyCommand<BoxCollider2DComponent, Float> modifyCmd =
            new ModifyComponentPropertyCommand<>(
                editorState,
                entity.getId(),
                collider,
                100f,
                200f,
                collider::setWidth,
                "Change Box Collider Width"
            );

        assertTrue(editorState.getCommandHistory().execute(modifyCmd));
        assertEquals(200f, collider.getWidth());

        // Undo
        editorState.undo();
        assertEquals(100f, collider.getWidth());

        // Redo
        editorState.redo();
        assertEquals(200f, collider.getWidth());
    }
}
