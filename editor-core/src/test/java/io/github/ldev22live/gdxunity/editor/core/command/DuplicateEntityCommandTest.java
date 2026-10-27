package io.github.ldev22live.gdxunity.editor.core.command;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateEntityCommandTest {
    private EditorState editorState;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        editorState.newScene("Duplicate Test Scene");
    }

    @Test
    void shouldDuplicateEntityWithIndependentIdAndUndoRedo() {
        Entity original = editorState.createEntity("Player");
        original.getTransform().setLocalPosition(100f, 200f, 0f);
        original.addComponent(new ShapeRendererComponent(50f, 50f, com.badlogic.gdx.graphics.Color.RED));

        editorState.setSelectedEntity(original);
        assertEquals(1, editorState.getActiveScene().getEntityCount());

        // Duplicate
        Entity copy = editorState.duplicateSelectedEntity();
        assertNotNull(copy);
        assertNotEquals(original.getId(), copy.getId());
        assertEquals("Player (Copy)", copy.getName());
        assertEquals(120f, copy.getTransform().getLocalPosition().x);
        assertEquals(220f, copy.getTransform().getLocalPosition().y);
        assertNotNull(copy.getComponent(ShapeRendererComponent.class));
        assertEquals(2, editorState.getActiveScene().getEntityCount());

        // Selection should be updated to copy
        assertEquals(copy.getId(), editorState.getSelectedEntityId());

        // Undo duplication
        assertTrue(editorState.undo());
        assertEquals(1, editorState.getActiveScene().getEntityCount());
        assertNull(editorState.getActiveScene().findEntityById(copy.getId()));

        // Redo duplication
        assertTrue(editorState.redo());
        assertEquals(2, editorState.getActiveScene().getEntityCount());
        assertNotNull(editorState.getActiveScene().findEntityById(copy.getId()));
    }

    @Test
    void shouldFailGracefullyWhenNoEntitySelected() {
        assertNull(editorState.duplicateSelectedEntity());
        assertEquals(0, editorState.getActiveScene().getEntityCount());
    }
}
