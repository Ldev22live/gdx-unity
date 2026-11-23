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
            "Rigid Body 3D", "Box Collider 3D", "Sphere Collider 3D", "Capsule Collider 3D", "Convex Hull Collider 3D", "Mesh Collider 3D", "Terrain Collider 3D",
            "Character Controller 3D", "Joint 3D"), physics);
        assertEquals(9, catalog.search("physics 3d").size());
        assertEquals(List.of("Sprite Renderer", "Sprite Animator"), catalog.search("SPRITE").stream().map(ComponentCatalog.Entry::displayName).toList());
        assertEquals(catalog.getEntries().size(), catalog.search("  ").size());
        assertTrue(catalog.search("does-not-exist").isEmpty());
    }

    @Test
    void shouldOfferSkyLightAndToneMappingOncePerEntityUnderLighting() {
        assertEquals(List.of("Directional Light", "Point Light", "Sky Light", "Tone Mapping", "Sky and Weather"),
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
    void shouldOfferOnePlayerPerSceneUnderGame() {
        assertEquals("Game", entry("Player").category());
        assertEquals(io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent.class, entry("Player").type());
        assertTrue(catalog.addTo(editorState, entity, entry("Player")));
        assertFalse(catalog.canAdd(entity, entry("Player")), "one per entity");

        Entity other = editorState.createEntity("Other");
        assertFalse(catalog.addTo(editorState, other, entry("Player")), "one per scene (TD-088)");
        assertTrue(editorState.getStatusMessage().contains("already the player"), editorState.getStatusMessage());
        assertNull(other.getComponent(io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent.class));
    }

    @Test
    void shouldOfferOneSpawnPointPerSceneUnderGame() {
        assertEquals("Game", entry("Spawn Point").category());
        assertTrue(catalog.addTo(editorState, entity, entry("Spawn Point")));

        Entity other = editorState.createEntity("Other");
        assertFalse(catalog.addTo(editorState, other, entry("Spawn Point")), "one per scene (roadmap M3c.2)");
        assertTrue(editorState.getStatusMessage().contains("already the spawn point"), editorState.getStatusMessage());
    }

    @Test
    void shouldAddAPerspectiveCameraTo3DAndTo2_5DScenesAndAnOrthographicOneTo2DScenes() {
        for (io.github.ldev22live.gdxunity.engine.scene.GameMode mode : io.github.ldev22live.gdxunity.engine.scene.GameMode.values()) {
            editorState.newScene("Camera " + mode, mode);
            Entity entity = editorState.createEntity("Cam");

            assertTrue(catalog.addTo(editorState, entity, entry("Camera")), editorState.getStatusMessage());

            CameraComponent.Projection expected = mode == io.github.ldev22live.gdxunity.engine.scene.GameMode.MODE_2D
                ? CameraComponent.Projection.ORTHOGRAPHIC : CameraComponent.Projection.PERSPECTIVE;
            assertEquals(expected, entity.getComponent(CameraComponent.class).getProjection(), mode.getLabel());
        }
    }

    @Test
    void shouldOfferAudioSourceUnderAudioAndCreateAPlayableDefault() {
        ComponentCatalog.Entry audio = entry("Audio Source");

        assertEquals("Audio", audio.category());
        assertEquals(io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent.class, audio.type());
        assertTrue(audio.factory().apply(null)
            instanceof io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent);
        assertEquals(4, catalog.search("audio").size(), "Audio Source, Audio Listener, Engine Sound and Music Source");
    }

    @Test
    void shouldOfferEngineSoundUnderAudio() {
        ComponentCatalog.Entry engine = entry("Engine Sound");

        assertEquals("Audio", engine.category());
        assertTrue(engine.factory().apply(null)
            instanceof io.github.ldev22live.gdxunity.engine.scene.audio.EngineSoundComponent);
        assertTrue(engine.allowMultiple());
    }

    @Test
    void shouldOfferASingleAudioListenerUnderAudio() {
        ComponentCatalog.Entry listener = entry("Audio Listener");

        assertEquals("Audio", listener.category());
        assertTrue(listener.factory().apply(null)
            instanceof io.github.ldev22live.gdxunity.engine.scene.audio.AudioListenerComponent);
        assertFalse(listener.allowMultiple(), "a scene is heard from one place");
    }

    @Test
    void shouldOfferMusicSourceUnderAudio() {
        ComponentCatalog.Entry music = entry("Music Source");

        assertEquals("Audio", music.category());
        assertTrue(music.factory().apply(null)
            instanceof io.github.ldev22live.gdxunity.engine.scene.audio.MusicSourceComponent);
    }

    @Test
    void shouldRejectDuplicateEntryNames() {
        assertThrows(IllegalArgumentException.class, () -> catalog.register(entry("Script")));
    }
}
