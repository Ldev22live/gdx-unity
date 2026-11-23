package io.github.ldev22live.gdxunity.editor.desktop.audit;

import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.component.ComponentCatalog;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.desktop.GameComponentSerializers;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightField3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightFieldSource;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.TerrainCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data consistency of every component the editor can add (roadmap milestone M0.2). The list comes from {@link ComponentCatalog}, not
 * from a hand-written list, so a component type added later is covered the moment it is in the catalog, and one with no serializer
 * fails here instead of being silently dropped by save, Duplicate or Play (the way Duplicate once dropped models and 3D bodies).
 *
 * <p>For each addable type the test checks, with the serializers the editor and the game runtime share: a serializer exists; the
 * component survives a scene save and load unchanged; Duplicate copies it unchanged; adding it undoes and redoes; and Play runs a copy
 * so the authored scene is identical after Stop.
 */
class ComponentConsistencyTest {
    /** Frames of simulation Play runs before Stop; enough for physics and scripts to start and step. */
    private static final int PLAY_FRAMES = 30;
    private static final float FRAME_SECONDS = 1f / 60f;

    @BeforeAll
    static void init() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        return new SceneSerializer(registry);
    }

    private static EditorState newState() {
        EditorState state = new EditorState(serializer());
        state.newScene("Consistency");
        // The Model entry adds the model selected in the Content Browser; a path is enough, nothing here loads it
        state.setSelectedAsset(new AssetReference("models/consistency.glb"));
        return state;
    }

    /**
     * A fresh entity ready to take a component of this type. Mesh and hull colliders need an entity that shows a mesh (the catalog
     * refuses them otherwise), so every entity gets a Mesh Primitive first unless it is the one being added. A Terrain Collider 3D
     * needs a Terrain the same way (TD-087); a path is enough, nothing here loads it.
     */
    private static Entity entityFor(EditorState state, ComponentCatalog.Entry entry) {
        Entity entity = state.createEntity(entry.displayName());
        if (!MeshPrimitiveComponent.class.isAssignableFrom(entry.type())) {
            entity.addComponent(new MeshPrimitiveComponent());
        }
        if (TerrainCollider3DComponent.class.isAssignableFrom(entry.type())) {
            entity.addComponent(new TerrainComponent("terrains/consistency.terrain"));
        }
        return entity;
    }

    /** A flat 2 by 2 ground for any terrain collider, as the renderer would read from a terrain file. */
    private static final HeightFieldSource FLAT_GROUND = new HeightFieldSource() {
        @Override
        public HeightField3D heightFieldOf(Entity entity) {
            return new HeightField3D(2, 2, 100f, 100f, -50f, -50f, new float[4]);
        }

        @Override
        public long revisionOf(Entity entity) {
            return 0L;
        }
    };

    /** The saved form of an entity's components, as text, so two entities or two loads compare field by field. */
    private static String savedComponents(SceneSerializer serializer, Entity entity) {
        JsonValue components = serializer.entityToJsonValue(entity).get("components");
        return components == null ? "" : components.toJson(JsonWriter.OutputType.json);
    }

    private static <T> T require(T value, String what) {
        assertNotNull(value, what);
        return value;
    }

    @Test
    void shouldHaveASerializerForEveryComponentTheEditorCanAdd() {
        SceneSerializer serializer = serializer();
        List<String> missing = new ArrayList<>();
        for (ComponentCatalog.Entry entry : ComponentCatalog.createDefault().getEntries()) {
            if (serializer.getRegistry().getByClass(entry.type()) == null) {
                missing.add(entry.displayName() + " (" + entry.type().getSimpleName() + ")");
            }
        }
        assertTrue(missing.isEmpty(), "These components cannot be saved, duplicated or played because no serializer is registered: " + missing);
    }

    @Test
    void shouldKeepEveryAddableComponentUnchangedThroughSaveAndLoad() {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (ComponentCatalog.Entry entry : ComponentCatalog.createDefault().getEntries()) {
            EditorState state = newState();
            Entity entity = entityFor(state, entry);
            if (!ComponentCatalog.createDefault().addTo(state, entity, entry)) {
                failures.add(entry.displayName() + ": could not be added (" + state.getStatusMessage() + ")");
                continue;
            }
            SceneSerializer serializer = state.getSerializer();
            String before = savedComponents(serializer, entity);
            Scene reloaded = serializer.fromJson(serializer.toJson(state.getActiveScene()));
            Entity again = reloaded.findEntityById(entity.getId());
            if (again == null) {
                failures.add(entry.displayName() + ": the entity is missing after reload");
                continue;
            }
            String after = savedComponents(serializer, again);
            if (!before.equals(after)) {
                failures.add(entry.displayName() + " changed in a save and load:\n  before " + before + "\n  after  " + after);
            }
            checked++;
        }
        assertTrue(failures.isEmpty(), failures.toString());
        assertTrue(checked > 20, "the catalog should hold dozens of component types: " + checked);
    }

    @Test
    void shouldCopyEveryAddableComponentUnchangedWhenTheEntityIsDuplicated() {
        List<String> failures = new ArrayList<>();
        for (ComponentCatalog.Entry entry : ComponentCatalog.createDefault().getEntries()) {
            EditorState state = newState();
            Entity entity = entityFor(state, entry);
            if (!ComponentCatalog.createDefault().addTo(state, entity, entry)) {
                failures.add(entry.displayName() + ": could not be added (" + state.getStatusMessage() + ")");
                continue;
            }
            state.setSelectedEntity(entity);
            Entity copy = state.duplicateSelectedEntity();
            if (copy == null) {
                failures.add(entry.displayName() + ": Duplicate returned nothing (" + state.getStatusMessage() + ")");
                continue;
            }
            if (entity.getAllComponents().size() != copy.getAllComponents().size()) {
                failures.add(entry.displayName() + ": the copy has " + copy.getAllComponents().size() + " components, the original "
                    + entity.getAllComponents().size());
                continue;
            }
            Component original = require(entity.getComponent(entry.type()), "original " + entry.displayName());
            Component copied = copy.getComponent(entry.type());
            if (copied == null) {
                failures.add(entry.displayName() + ": missing on the copy");
            } else if (copied == original) {
                failures.add(entry.displayName() + ": the copy shares the original's component object");
            } else if (!savedComponents(state.getSerializer(), entity).equals(savedComponents(state.getSerializer(), copy))) {
                failures.add(entry.displayName() + ": the copy differs from the original");
            }
        }
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void shouldUndoAndRedoAddingEveryAddableComponent() {
        List<String> failures = new ArrayList<>();
        for (ComponentCatalog.Entry entry : ComponentCatalog.createDefault().getEntries()) {
            EditorState state = newState();
            Entity entity = entityFor(state, entry);
            if (!ComponentCatalog.createDefault().addTo(state, entity, entry)) {
                failures.add(entry.displayName() + ": could not be added (" + state.getStatusMessage() + ")");
                continue;
            }
            String added = savedComponents(state.getSerializer(), entity);
            if (!state.undo()) {
                failures.add(entry.displayName() + ": undo was refused");
                continue;
            }
            if (entity.getComponent(entry.type()) != null) {
                failures.add(entry.displayName() + ": still there after undo");
                continue;
            }
            if (!state.redo()) {
                failures.add(entry.displayName() + ": redo was refused");
                continue;
            }
            if (entity.getComponent(entry.type()) == null) {
                failures.add(entry.displayName() + ": missing after redo");
            } else if (!added.equals(savedComponents(state.getSerializer(), entity))) {
                failures.add(entry.displayName() + ": different after undo and redo");
            }
        }
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void shouldLeaveTheAuthoredSceneIdenticalAfterPlayAndStopWithEveryAddableComponentInIt() {
        EditorState state = newState();
        ComponentCatalog catalog = ComponentCatalog.createDefault();
        List<String> notAdded = new ArrayList<>();
        for (ComponentCatalog.Entry entry : catalog.getEntries()) {
            Entity entity = entityFor(state, entry);
            if (!catalog.addTo(state, entity, entry)) {
                notAdded.add(entry.displayName() + " (" + state.getStatusMessage() + ")");
            }
        }
        assertTrue(notAdded.isEmpty(), "These could not be added to the test scene: " + notAdded);
        Scene authored = state.getActiveScene();
        String before = state.getSerializer().toJson(authored);
        int entities = authored.getAllEntities().size();
        PlayModeController play = new PlayModeController(state);
        // In the editor the renderer gives the scene that is playing a source for its mesh and hull colliders, and its terrain
        // colliders, to read (SceneRenderer3D.bindColliderGeometry); this test has no renderer, so a small extension does the same
        play.addExtension(new PlayModeController.Extension() {
            @Override
            public List<String> onPlay(Scene runtimeScene, PlayModeController controller) {
                runtimeScene.setColliderGeometry(new ColliderGeometry(null));
                runtimeScene.setHeightFields(FLAT_GROUND);
                return List.of();
            }

            @Override
            public void onStop(Scene runtimeScene) {
            }
        });

        assertTrue(play.play(), "Play refused: " + state.getStatusMessage());
        try {
            Scene runtime = play.getRuntimeScene();
            assertNotSame(authored, runtime, "Play must run a copy");
            assertEquals(entities, runtime.getAllEntities().size());
            for (Entity runtimeEntity : runtime.getAllEntities()) {
                Entity authoredEntity = authored.findEntityById(runtimeEntity.getId());
                assertNotNull(authoredEntity, runtimeEntity.getName());
                for (Component component : runtimeEntity.getAllComponents()) {
                    for (Component original : authoredEntity.getAllComponents()) {
                        assertTrue(component != original, runtimeEntity.getName() + " shares a component object with the authored scene");
                    }
                }
            }
            for (int frame = 0; frame < PLAY_FRAMES; frame++) {
                play.update(FRAME_SECONDS);
            }
        } finally {
            play.stop();
        }

        assertSame(authored, state.getActiveScene());
        assertEquals(before, state.getSerializer().toJson(authored), "Play and Stop must not change the authored scene");
        assertNull(play.getRuntimeScene());
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual, "Stop must put the authored scene back");
    }
}
