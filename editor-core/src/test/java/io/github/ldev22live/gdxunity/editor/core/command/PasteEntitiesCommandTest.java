package io.github.ldev22live.gdxunity.editor.core.command;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ShapeRendererComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PasteEntitiesCommandTest {
    private EditorState editorState;

    @BeforeEach
    void setUp() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new ShapeRendererComponentSerializer());
        editorState = new EditorState(new SceneSerializer(registry));
        editorState.newScene("Paste Test Scene");
    }

    private Entity tree(String name, float x, float z) {
        Entity entity = editorState.createEntity(name);
        entity.getTransform().setLocalPosition(x, 0f, z);
        entity.addComponent(new ShapeRendererComponent(4f, 4f, Color.GREEN));
        return entity;
    }

    @Test
    void shouldPasteNewEntitiesAlongTheGroundIn3D() {
        editorState.getActiveScene().setGameMode(GameMode.MODE_3D);
        Entity original = tree("Tree", 10f, 5f);
        editorState.setSelectedEntity(original);

        assertEquals(1, editorState.copySelectedEntities());
        List<Entity> pasted = editorState.pasteEntities();

        assertEquals(1, pasted.size());
        Entity copy = pasted.get(0);
        assertNotEquals(original.getId(), copy.getId());
        assertEquals("Tree", copy.getName());
        assertEquals(10f + PasteEntitiesCommand.PASTE_OFFSET, copy.getTransform().getLocalPosition().x);
        assertEquals(0f, copy.getTransform().getLocalPosition().y, "3D copies stay on the ground");
        assertEquals(5f + PasteEntitiesCommand.PASTE_OFFSET, copy.getTransform().getLocalPosition().z);
        assertNotNull(copy.getComponent(ShapeRendererComponent.class));
        assertNotSame(original.getComponent(ShapeRendererComponent.class), copy.getComponent(ShapeRendererComponent.class));
        assertEquals(copy.getId(), editorState.getSelectedEntityId(), "the paste is selected");
    }

    @Test
    void shouldOffsetOnTheScreenPlaneIn2DAndFurtherOnEachPaste() {
        editorState.getActiveScene().setGameMode(GameMode.MODE_2D);
        Entity original = editorState.createEntity("Coin");
        original.getTransform().setLocalPosition(100f, 50f, 0f);
        editorState.setSelectedEntity(original);
        editorState.copySelectedEntities();

        Entity first = editorState.pasteEntities().get(0);
        Entity second = editorState.pasteEntities().get(0);

        assertEquals(50f + PasteEntitiesCommand.PASTE_OFFSET, first.getTransform().getLocalPosition().y);
        assertEquals(0f, first.getTransform().getLocalPosition().z);
        assertEquals(100f + 2 * PasteEntitiesCommand.PASTE_OFFSET, second.getTransform().getLocalPosition().x, "pastes do not stack");
    }

    @Test
    void shouldPasteWhatWasCopiedEvenAfterTheOriginalIsDeleted() {
        Entity original = tree("Rock", 0f, 0f);
        editorState.setSelectedEntity(original);
        editorState.copySelectedEntities();
        editorState.deleteSelectedEntity();
        assertNull(editorState.getActiveScene().findEntityById(original.getId()));

        List<Entity> pasted = editorState.pasteEntities();

        assertEquals(1, pasted.size());
        assertEquals("Rock", pasted.get(0).getName());
        assertNotNull(pasted.get(0).getComponent(ShapeRendererComponent.class));
    }

    @Test
    void shouldPasteEverySelectedEntityAsOneUndoStepAndRedoWithTheSameIds() {
        Entity a = tree("A", 0f, 0f);
        Entity b = tree("B", 3f, 0f);
        editorState.setSelectedEntityIds(List.of(a.getId(), b.getId()), b.getId());
        assertEquals(2, editorState.copySelectedEntities());

        List<Entity> pasted = editorState.pasteEntities();
        assertEquals(2, pasted.size());
        assertEquals(4, editorState.getActiveScene().getEntityCount());
        assertEquals(Set.of(pasted.get(0).getId(), pasted.get(1).getId()), editorState.getSelectedEntityIds());

        assertTrue(editorState.undo());
        assertEquals(2, editorState.getActiveScene().getEntityCount(), "one undo removes the whole paste");
        assertTrue(editorState.getSelectedEntityIds().isEmpty());

        assertTrue(editorState.redo());
        assertEquals(4, editorState.getActiveScene().getEntityCount());
        Entity redone = editorState.getActiveScene().findEntityById(pasted.get(0).getId());
        assertNotNull(redone, "redo brings back the same entities");
        assertNotNull(redone.getComponent(ShapeRendererComponent.class), "with their components");
    }

    @Test
    void shouldRefuseToPasteWithAnEmptyClipboard() {
        assertTrue(editorState.pasteEntities().isEmpty());
        assertEquals(0, editorState.getActiveScene().getEntityCount());
        assertFalse(editorState.getCommandHistory().canUndo(), "a refused paste is not an undo step");
    }

    @Test
    void shouldRegisterCopyAndPasteInTheEditMenuEnabledOnlyWhenTheyApply() {
        EditorActionRegistry registry = new EditorActionRegistry();
        DefaultEditorActions.registerDefaults(registry, editorState);
        assertFalse(registry.find(DefaultEditorActions.EDIT_COPY).orElseThrow().isEnabled(), "nothing selected");
        assertFalse(registry.find(DefaultEditorActions.EDIT_PASTE).orElseThrow().isEnabled(), "nothing copied");

        editorState.setSelectedEntity(tree("Tree", 0f, 0f));
        assertTrue(registry.find(DefaultEditorActions.EDIT_COPY).orElseThrow().isEnabled());
        assertTrue(registry.execute(DefaultEditorActions.EDIT_COPY));
        assertTrue(registry.find(DefaultEditorActions.EDIT_PASTE).orElseThrow().isEnabled());
        assertTrue(registry.execute(DefaultEditorActions.EDIT_PASTE));
        assertEquals(2, editorState.getActiveScene().getEntityCount());
    }
}
