package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ModelComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.sculpt.StaticMeshImport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MeshSaveSessionTest {
    @TempDir Path root;
    private final String source = "thirdparty/wall.gltf";
    private Entity first, second;
    private EditorState state;
    private MeshSaveSession session;
    private String original;

    @BeforeAll static void initNatives() { GdxNativesLoader.load(); }

    @BeforeEach void openWall() throws IOException {
        original = StaticMeshImport.read(Path.of("..", "assets"),
            Path.of("thirdparty/kenney/building-kit/Models/GLB format/wall.glb")).modelJson();
        Files.createDirectory(root.resolve("thirdparty"));
        Files.writeString(root.resolve(source), original);
        state = new EditorState();
        Scene scene = new Scene("World");
        first = scene.createEntity("Wall");
        second = scene.createEntity("Another wall");
        first.addComponent(new ModelComponent(source));
        second.addComponent(new ModelComponent(source));
        state.setActiveScene(scene);
        session = open(first);
    }

    private MeshSaveSession open(Entity entity) throws IOException {
        return new MeshSaveSession(root, new MeshWorkspace(root, entity).open(entity));
    }

    @Test void shouldSaveNewEditedTrianglesAttributesAndMaterialsWithoutChangingEitherInstance() throws IOException {
        var draft = session.drafts().getFirst();
        assertTrue(draft.apply(MeshPartEditor.Tool.Extrude, MeshPartEditor.Kind.Face, 0, .2f, 0, 0));
        assertTrue(draft.apply(MeshPartEditor.Tool.Material, MeshPartEditor.Kind.Face, 0, -1, 0, 0));
        String path = session.save(MeshSaveSession.Mode.NEW, "Wall variant", state, false);
        var saved = StaticMeshImport.read(root, Path.of(path));
        var reopened = MeshPartEditor.open(saved);
        assertEquals(draft.geometry().vertices(), reopened.getFirst().geometry().vertices().stream()
            .map(v -> new MeshPartEditor.Vertex(v.x(), v.y(), v.z(), draft.geometry().vertices().get(v.sourceIndex()).sourceIndex())).toList());
        assertEquals(-1, reopened.getFirst().geometry().faces().getFirst().material());
        assertEquals(draft.geometry().faces().size(), reopened.get(0).geometry().faces().size() + reopened.get(1).geometry().faces().size());
        assertEquals(source, saved.model().require("asset").require("extras").require("forgeroomMeshSource").getString("path"));
        assertEquals(source, first.getComponent(ModelComponent.class).getModelPath());
        assertEquals(source, second.getComponent(ModelComponent.class).getModelPath());
        assertEquals(original, Files.readString(root.resolve(source)));
        assertThrows(IOException.class, () -> session.save(MeshSaveSession.Mode.NEW, "Wall variant", state, false));
        assertThrows(IOException.class, () -> session.save(MeshSaveSession.Mode.NEW, "../escape", state, false));
    }

    @Test void shouldMakeOneInstanceUniqueAndUndoItsBinding() throws IOException {
        String derived = session.save(MeshSaveSession.Mode.UNIQUE, "Wall unique", state, false);
        assertEquals(derived, first.getComponent(ModelComponent.class).getModelPath());
        assertEquals(source, second.getComponent(ModelComponent.class).getModelPath());
        assertTrue(state.getCommandHistory().undo());
        assertEquals(source, first.getComponent(ModelComponent.class).getModelPath());
        assertTrue(state.getCommandHistory().redo());
        assertEquals(derived, first.getComponent(ModelComponent.class).getModelPath());
        assertEquals(derived, open(first).target());
    }

    @Test void shouldRequireConfirmationAndRefuseProviderOrConflictingUpdates() throws IOException {
        assertThrows(IOException.class, () -> session.save(MeshSaveSession.Mode.UPDATE, "", state, true));
        String derived = session.save(MeshSaveSession.Mode.UNIQUE, "Shared wall", state, false);
        second.getComponent(ModelComponent.class).setModelPath(derived);
        MeshSaveSession stale = open(second);
        assertTrue(session.updateWarning(state).contains("1 other instance"));
        byte[] before = Files.readAllBytes(root.resolve(derived));
        assertTrue(session.drafts().getFirst().apply(MeshPartEditor.Tool.Translate, MeshPartEditor.Kind.Face, 0, 1, 0, 0));
        assertThrows(IOException.class, () -> session.save(MeshSaveSession.Mode.UPDATE, "", state, false));
        assertArrayEquals(before, Files.readAllBytes(root.resolve(derived)));
        assertEquals(derived, session.save(MeshSaveSession.Mode.UPDATE, "", state, true));
        assertEquals(session.drafts().getFirst().geometry(), open(second).drafts().getFirst().geometry());
        assertThrows(IOException.class, () -> stale.save(MeshSaveSession.Mode.UPDATE, "", state, true));
        assertEquals(original, Files.readString(root.resolve(source)));
    }
    @Test void shouldUpdateABinaryProjectModelWithItsExternalTextureEmbedded() throws IOException {
        Path fixture = Path.of("..", "assets", "thirdparty", "kenney", "building-kit", "Models", "GLB format");
        Path destination = Files.createDirectories(root.resolve("models/edited"));
        Files.copy(fixture.resolve("wall.glb"), destination.resolve("wall.glb"));
        Files.createDirectory(destination.resolve("Textures"));
        Files.copy(fixture.resolve("Textures/colormap.png"), destination.resolve("Textures/colormap.png"));
        first.getComponent(ModelComponent.class).setModelPath("models/edited/wall.glb");
        MeshSaveSession binary = open(first);
        assertTrue(binary.drafts().getFirst().apply(MeshPartEditor.Tool.Translate, MeshPartEditor.Kind.Edge, 0, .125f, 0, 0));
        String path = binary.save(MeshSaveSession.Mode.UPDATE, "", state, true);
        Files.delete(destination.resolve("Textures/colormap.png"));
        var saved = StaticMeshImport.read(root, Path.of(path));
        assertEquals(1, saved.model().require("buffers").size);
        assertTrue(saved.model().require("images").get(0).has("bufferView"));
        assertEquals(binary.drafts().getFirst().geometry(), open(first).drafts().getFirst().geometry());
    }

    @Test void shouldEditTheSharedPrefabOnlyAfterAWarningAndInOneUndoStep() throws IOException {
        state.getSerializer().getRegistry().register(new ModelComponentSerializer());
        PrefabService prefabs = new PrefabService(state, root.toFile());
        state.setPrefabService(prefabs);
        Entity house = state.getActiveScene().createEntity("House");
        first.getTransform().setParent(house.getTransform(), false);
        AssetReference prefab = prefabs.createPrefab(house);
        Entity other = prefabs.instantiate(prefab, new Vector3(500f, 0f, 0f));
        Entity otherWall = other.getTransform().getChildren().getFirst().getEntity();
        String prefabBefore = Files.readString(root.resolve(prefab.getPath()));
        MeshSaveSession shared = open(first);
        assertTrue(shared.drafts().getFirst().apply(MeshPartEditor.Tool.Extrude, MeshPartEditor.Kind.Face, 0, .2f, 0, 0));
        int undoSteps = state.getCommandHistory().getUndoCount();

        IOException warning = assertThrows(IOException.class, () -> shared.save(MeshSaveSession.Mode.PREFAB, "Shared", state, false));
        assertTrue(warning.getMessage().contains("All 2 instance(s)"), warning.getMessage());
        assertTrue(warning.getMessage().contains(prefab.getPath()), warning.getMessage());
        assertFalse(Files.exists(root.resolve("models/edited/Shared.gltf")), "nothing is written before the confirmation");
        assertEquals(prefabBefore, Files.readString(root.resolve(prefab.getPath())));

        String derived = shared.save(MeshSaveSession.Mode.PREFAB, "Shared", state, true);
        assertEquals(derived, first.getComponent(ModelComponent.class).getModelPath());
        assertEquals(derived, otherWall.getComponent(ModelComponent.class).getModelPath(), "every instance changes");
        assertEquals(source, second.getComponent(ModelComponent.class).getModelPath(), "a wall outside the prefab does not");
        assertTrue(Files.readString(root.resolve(prefab.getPath())).contains(derived), "the prefab file holds the new model");
        assertTrue(prefabs.getOverrides(first).isEmpty(), "it is the prefab's value, not an override");
        assertEquals(original, Files.readString(root.resolve(source)), "the source is never overwritten");
        assertEquals(undoSteps + 1, state.getCommandHistory().getUndoCount(), "one undo step");

        assertTrue(state.getCommandHistory().undo());
        assertEquals(source, first.getComponent(ModelComponent.class).getModelPath());
        assertEquals(source, otherWall.getComponent(ModelComponent.class).getModelPath());
        assertEquals(prefabBefore, Files.readString(root.resolve(prefab.getPath())), "undo restores the prefab file");
        assertTrue(state.getCommandHistory().redo());
        assertEquals(derived, otherWall.getComponent(ModelComponent.class).getModelPath());
        IOException loose = assertThrows(IOException.class, () -> open(second).prefabWarning(state));
        assertTrue(loose.getMessage().contains("not part of a prefab"), loose.getMessage());
    }

}
