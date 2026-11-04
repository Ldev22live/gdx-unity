package io.github.ldev22live.gdxunity.editor.core.component;

import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.SkyLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.ToneMappingComponent;
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
        assertEquals(List.of("Rigid Body 2D", "Box Collider 2D", "Circle Collider 2D",
            "Rigid Body 3D", "Box Collider 3D", "Sphere Collider 3D", "Capsule Collider 3D"), physics);
        assertEquals(4, catalog.search("physics 3d").size());
        assertEquals(List.of("Sprite Renderer"), catalog.search("SPRITE").stream().map(ComponentCatalog.Entry::displayName).toList());
        assertEquals(catalog.getEntries().size(), catalog.search("  ").size());
        assertTrue(catalog.search("does-not-exist").isEmpty());
    }

    @Test
    void shouldOfferSkyLightAndToneMappingOncePerEntityUnderLighting() {
        assertEquals(List.of("Directional Light", "Point Light", "Sky Light", "Tone Mapping"),
            catalog.search("lighting").stream().map(ComponentCatalog.Entry::displayName).toList());

        assertTrue(catalog.addTo(editorState, entity, entry("Sky Light")));
        assertTrue(catalog.addTo(editorState, entity, entry("Tone Mapping")));
        assertFalse(catalog.addTo(editorState, entity, entry("Sky Light")), "one sky light per entity");
        assertNotNull(entity.getComponent(SkyLightComponent.class));
        assertNotNull(entity.getComponent(ToneMappingComponent.class));
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
    void shouldReportAFailingFactoryWithoutAddingAnything() {
        catalog.register(new ComponentCatalog.Entry("Broken", "Testing", RigidBody2DComponent.class, true, state -> {
            throw new IllegalStateException("disk full");
        }));
        int before = entity.getAllComponents().size();
        assertFalse(catalog.addTo(editorState, entity, entry("Broken")));
        assertEquals(before, entity.getAllComponents().size());
        assertTrue(editorState.isStatusError());
        assertTrue(editorState.getStatusMessage().contains("disk full"));
    }

    @Test
    void shouldAddModelOnlyFromSelectedModelAsset() {
        assertFalse(catalog.addTo(editorState, entity, entry("Model")));
        assertTrue(editorState.getStatusMessage().contains(".glb"), editorState.getStatusMessage());

        editorState.setSelectedAsset(new io.github.ldev22live.gdxunity.engine.core.asset.AssetReference("models/hero.glb"));
        assertTrue(catalog.addTo(editorState, entity, entry("Model")));

        assertEquals("models/hero.glb",
            entity.getComponent(io.github.ldev22live.gdxunity.engine.render.components.ModelComponent.class).getModelPath());
    }

    @Test
    void shouldOfferCameraAndTextUnderGame() {
        assertEquals("Game", entry("Camera").category());
        assertEquals("Game", entry("Text").category());
        assertEquals(CameraComponent.class,
            entry("Camera").type());
    }

    @Test
    void shouldRejectDuplicateEntryNames() {
        assertThrows(IllegalArgumentException.class, () -> catalog.register(entry("Script")));
    }
}
