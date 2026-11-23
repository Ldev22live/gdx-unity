package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ModelComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TerrainComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.TerrainCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The curated CC0 starter pack in use (roadmap M2b.2): in a new project made with the starter palette, Kenney's tree and rock are
 * placed many times from Objects and the house from Buildings, and everything comes back after save and reopen, offline (nothing here
 * goes online), with the licence records in the project.
 */
class StarterPackPlacementTest {
    private static final File SHIPPED = new File("../assets");
    private static final String TREE = "prefabs/level/CC0 Oak Tree.prefab";
    private static final String ROCK = "prefabs/level/CC0 Rock.prefab";
    private static final String HOUSE = "prefabs/buildings/CC0 House.prefab";

    @TempDir
    Path projects;

    private File project;
    private EditorState editorState;
    private LevelSession level;
    private Scene scene;
    private SceneSerializer serializer;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() throws IOException {
        // A new 3D project, given the starter palette exactly as the template gallery gives it
        project = Files.createDirectories(projects.resolve("Starter Game")).toFile();
        StarterPalette.addTo(SHIPPED, new TemplateProject.Result(project, "scenes/level.json", List.of()), GameMode.MODE_3D);
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new TerrainComponentSerializer());
        registry.register(new TerrainCollider3DComponentSerializer());
        registry.register(new ModelComponentSerializer());
        registry.register(new PrefabInstanceComponentSerializer());
        serializer = new SceneSerializer(registry);
        editorState = new EditorState(serializer);
        scene = new Scene("Level");
        scene.setGameMode(GameMode.MODE_3D);
        editorState.setActiveScene(scene);
        editorState.setPrefabService(new PrefabService(editorState, project));
        level = new LevelSession(editorState, new TerrainSession(new TerrainLibrary(AssetSource.directory(project))), project);
        assertNotNull(level.createLevel(Surface.GRASS));
    }

    private Entity place(String prefab, float x, float z) {
        level.setObjectPath(prefab);
        Entity placed = level.placeObject(x, 0f, z);
        assertNotNull(placed, prefab + ": " + editorState.getStatusMessage());
        return placed;
    }

    /** The model paths under {@code root}, its children included. */
    private static List<String> models(Scene scene, Entity root) {
        List<String> found = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            for (Transform at = entity.getTransform(); at != null; at = at.getParent()) {
                if (at == root.getTransform()) {
                    ModelComponent model = entity.getComponent(ModelComponent.class);
                    if (model != null) {
                        found.add(model.getModelPath());
                    }
                    break;
                }
            }
        }
        return found;
    }

    @Test
    void shouldOfferThePackInTheNewProjectsObjectsAndBuildings() {
        level.setFocus(LevelSession.Focus.OBJECTS);
        assertTrue(level.listObjects().containsAll(List.of(TREE, ROCK)), level.listObjects().toString());
        level.setFocus(LevelSession.Focus.BUILDINGS);
        assertTrue(level.listObjects().contains(HOUSE), level.listObjects().toString());
    }

    @Test
    void shouldPlaceFromThePaletteAsOneUndoStep() {
        int before = scene.getAllEntities().size();
        level.setFocus(LevelSession.Focus.BUILDINGS);

        Entity house = place(HOUSE, 0f, 0f);

        // The root, 16 pieces, and the doorway's two jambs and lintel as collision-only children (TD-096)
        assertEquals(before + 20, scene.getAllEntities().size(), "the house, its 16 pieces and its 3 doorway colliders");
        assertTrue(editorState.undo());
        assertEquals(before, scene.getAllEntities().size(), "one undo takes the whole house away");
        assertTrue(scene.getAllEntities().stream().noneMatch(e -> e.getId().equals(house.getId())));
    }

    @Test
    void shouldPlaceEachManyTimesAndKeepThemThroughSaveAndReopen() throws IOException {
        List<Entity> placed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            placed.add(place(TREE, -400f + i * 150f, 200f));
            placed.add(place(ROCK, -400f + i * 150f, -200f));
        }
        placed.add(place(HOUSE, 0f, 0f));

        assertEquals(11, placed.size());
        Entity house = placed.get(placed.size() - 1);
        assertEquals(16, models(scene, house).size(), "the whole house, every piece a model");
        for (Entity entity : placed) {
            for (String model : models(scene, entity)) {
                assertTrue(new File(project, model).isFile(), "the project has " + model);
                assertTrue(ThirdPartyManifest.read(project).find(model).isPresent(), "and its licence record: " + model);
            }
        }

        Path saved = project.toPath().resolve("scenes/level.json");
        Files.createDirectories(saved.getParent());
        Files.writeString(saved, serializer.toJson(scene));
        Scene reopened = serializer.fromJson(Files.readString(saved));

        for (Entity entity : placed) {
            Entity again = reopened.getAllEntities().stream().filter(e -> e.getId().equals(entity.getId())).findFirst().orElseThrow();
            assertEquals(models(scene, entity), models(reopened, again), entity.getName());
            assertEquals(entity.getTransform().getWorldPosition().x, again.getTransform().getWorldPosition().x, 0.01f);
            assertEquals(entity.getTransform().getWorldPosition().z, again.getTransform().getWorldPosition().z, 0.01f);
            assertNotNull(PrefabService.linkOf(again), "still linked to its prefab: " + entity.getName());
        }
    }
}
