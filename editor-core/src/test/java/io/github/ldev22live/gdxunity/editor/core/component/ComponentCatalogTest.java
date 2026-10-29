package io.github.ldev22live.gdxunity.editor.core.component;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComponentCatalogTest {

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private EditorState editorState;
    private ComponentCatalog catalog;
    private Entity entity;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        catalog = ComponentCatalog.createDefault();
        entity = editorState.getActiveScene().createEntity("Target");
    }

    private ComponentCatalog.Entry entry(String name) {
        return catalog.getEntries().stream().filter(e -> e.displayName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void shouldSearchByNameOrCategoryCaseInsensitively() {
        List<String> physics = catalog.search("physics").stream().map(ComponentCatalog.Entry::displayName).toList();
        assertEquals(List.of("Rigid Body 2D", "Box Collider 2D", "Circle Collider 2D"), physics);
        assertEquals(List.of("Sprite Renderer"), catalog.search("SPRITE").stream().map(ComponentCatalog.Entry::displayName).toList());
        assertEquals(catalog.getEntries().size(), catalog.search("  ").size());
        assertTrue(catalog.search("does-not-exist").isEmpty());
    }

    @Test
    void shouldAddComponentAsOneUndoableCommand() {
        assertTrue(catalog.addTo(editorState, entity, entry("Rigid Body 2D")));
        assertNotNull(entity.getComponent(RigidBody2DComponent.class));

        editorState.undo();
        assertNull(entity.getComponent(RigidBody2DComponent.class));
    }

    @Test
    void shouldRejectSecondSingleInstanceComponentButAllowMultipleColliders() {
        ComponentCatalog.Entry rigidBody = entry("Rigid Body 2D");
        assertTrue(catalog.addTo(editorState, entity, rigidBody));
        assertFalse(catalog.canAdd(entity, rigidBody));
        assertFalse(catalog.addTo(editorState, entity, rigidBody));
        assertTrue(editorState.isStatusError());

        ComponentCatalog.Entry box = entry("Box Collider 2D");
        assertTrue(catalog.addTo(editorState, entity, box));
        assertTrue(catalog.canAdd(entity, box));
        assertTrue(catalog.addTo(editorState, entity, box));
        assertEquals(2, entity.getAllComponents().stream().filter(c -> c instanceof BoxCollider2DComponent).count());
    }

    @Test
    void shouldRejectDuplicateEntryNames() {
        assertThrows(IllegalArgumentException.class, () -> catalog.register(entry("Script")));
    }
}
