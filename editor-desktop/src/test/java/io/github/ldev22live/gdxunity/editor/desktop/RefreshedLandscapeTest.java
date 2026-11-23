package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ChangeTransformCommand;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first opt-in sample refresh (roadmap M2b.6, TD-100; tools/make_refreshed_landscape.py): Landscape Valley (CC0) is Landscape
 * Valley (Terrain) with its generated oaks, pines, rocks and bushes replaced by the CC0 starter pack's prefabs. Each replacement keeps
 * the original's id, name, place, turn and relative size; the prefab sync on opening changes nothing; more can be placed, and a moved
 * one undoes, redoes, saves and reopens with its prefab link and size; the trunks and rocks block in Play and the bushes do not; and
 * stopping Play leaves the edited scene exactly as it was.
 */
class RefreshedLandscapeTest {
    /** Tests run in the module folder; the shipped assets are one level up. They are only read here. */
    private static final File ASSETS = new File("../assets");
    private static final String ORIGINAL = "landscape-terrain-3d.json";
    private static final String REFRESHED = "landscape-cc0-terrain-3d.json";
    /** The generated model each replaced entity showed, and the prefab now in its place. */
    private static final Map<String, String> REPLACED = Map.of(
        "models/landscape/oak-tree.gltf", StarterPalette.LEVEL_FOLDER + "/CC0 Oak Tree.prefab",
        "models/landscape/pine-tree.gltf", StarterPalette.LEVEL_FOLDER + "/CC0 Pine Tree.prefab",
        "models/landscape/rock.gltf", StarterPalette.LEVEL_FOLDER + "/CC0 Rock.prefab",
        "models/landscape/bush.gltf", StarterPalette.LEVEL_FOLDER + "/CC0 Bush.prefab");
    /** 11 oaks, 26 pines, 12 rocks and 16 bushes. */
    private static final int REPLACED_COUNT = 65;
    /** The generated models were imported at 100 units per metre; an original's import scale over this was its size. */
    private static final float UNITS_PER_METRE = 100f;
    private static final float EPSILON = 1e-4f;
    private static final float FRAME = 1f / 60f;
    private static final int PLAY_FRAMES = 30;
    /**
     * The ray looks straight down at an object's centre from this high above its foot: above every crown (the crowns do not collide),
     * so on any slope it meets the top of a trunk or a rock first, and the ground under a bush.
     */
    private static final float RAY_ABOVE = 2000f;
    /** How far the moved rock goes along x. */
    private static final float MOVE = 250f;

    @TempDir
    Path temp;

    private static Input previousInput;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
        previousInput = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> method.getReturnType() == boolean.class ? false
                : method.getReturnType() == int.class ? 0
                : method.getReturnType() == float.class ? 0f
                : method.getReturnType() == long.class ? 0L : null);
    }

    @AfterAll
    static void restoreInput() {
        Gdx.input = previousInput;
    }

    private static EditorState newEditor() {
        EditorState editor = new EditorState();
        EditorApplication.registerComponentSerializers(editor.getSerializer().getRegistry());
        return editor;
    }

    private static JsonValue sceneJson(String sample) throws IOException {
        return new JsonReader().parse(Files.readString(new File(ASSETS, "samples/" + sample).toPath(), StandardCharsets.UTF_8));
    }

    /** A project made from the gallery's Landscape Valley (CC0), opened as the editor opens it (prefabs synced on load). */
    private record Opened(EditorState editor, SceneFileController files, File root, File sceneFile) {
    }

    private Opened openProject(String name) throws IOException {
        TemplateGallery.Choice choice = TemplateGallery.samples(ASSETS).stream()
            .filter(c -> c.scene().equals("samples/" + REFRESHED)).findFirst().orElseThrow();
        EditorState editor = newEditor();
        TemplateProject.Result made = TemplateGallery.create(choice, ASSETS, temp.toFile(), name, editor.getSerializer());
        return reopen(made.folder(), new File(made.folder(), made.startScene()));
    }

    private static Opened reopen(File root, File sceneFile) {
        EditorState editor = newEditor();
        editor.setPrefabService(new PrefabService(editor, root));
        SceneFileController files = new SceneFileController(editor, new NoDialogs(), root);
        assertTrue(files.openFile(sceneFile), editor.getStatusMessage());
        Scene scene = editor.getActiveScene();
        scene.setHeightFields(new TerrainHeightFields(new TerrainLibrary(AssetSource.directory(root))));
        scene.setColliderGeometry(new ColliderGeometry(null));
        return new Opened(editor, files, root, sceneFile);
    }

    private static List<Entity> instancesOf(Scene scene, String prefab) {
        List<Entity> roots = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            PrefabInstanceComponent link = PrefabService.linkOf(entity);
            if (link != null && link.isRoot() && link.getPrefabPath().equals(prefab)) {
                roots.add(entity);
            }
        }
        return roots;
    }

    @Test
    void shouldReplaceEveryGeneratedTreeRockAndBushWithItsCC0PrefabWhereTheOriginalStood() throws IOException {
        JsonValue original = sceneJson(ORIGINAL);
        Scene refreshed = newEditor().getSerializer().fromJson(Files.readString(new File(ASSETS, "samples/" + REFRESHED).toPath()));
        Map<String, JsonValue> refreshedById = new LinkedHashMap<>();
        for (JsonValue entity = sceneJson(REFRESHED).get("entities").child; entity != null; entity = entity.next) {
            refreshedById.put(entity.getString("id"), entity);
        }
        int replaced = 0;
        int kept = 0;
        for (JsonValue entity = original.get("entities").child; entity != null; entity = entity.next) {
            String id = entity.getString("id");
            JsonValue components = entity.get("components");
            String model = components.size == 1 && "ModelComponent".equals(components.child.getString("type"))
                ? components.child.get("data").getString("model") : null;
            String prefab = model == null ? null : REPLACED.get(model);
            assertTrue(refreshedById.containsKey(id), entity.getString("name") + " keeps its id");
            if (prefab == null) {
                assertEquals(entity.toJson(OutputType.json),
                    refreshedById.get(id).toJson(OutputType.json),
                    entity.getString("name") + " is kept as it was");
                kept++;
                continue;
            }
            Entity root = refreshed.findEntityById(UUID.fromString(id));
            PrefabInstanceComponent link = PrefabService.linkOf(root);
            assertNotNull(link, root.getName() + " is a prefab instance");
            assertTrue(link.isRoot(), root.getName() + " is the instance's root");
            assertEquals(prefab, link.getPrefabPath(), root.getName());
            assertEquals(entity.getString("name"), root.getName());
            assertNull(root.getComponent(ModelComponent.class), root.getName() + " no longer shows the generated model itself");
            JsonValue position = entity.get("transform").get("position");
            assertEquals(new Vector3(position.getFloat("x"), position.getFloat("y"), position.getFloat("z")),
                root.getTransform().getLocalPosition(), root.getName() + " stands where the original stood");
            JsonValue rotation = entity.get("transform").get("rotation");
            Quaternion turn = new Quaternion(rotation.getFloat("x"), rotation.getFloat("y"), rotation.getFloat("z"), rotation.getFloat("w"));
            assertTrue(turn.equals(root.getTransform().getLocalRotation()), root.getName() + " keeps its turn");
            float size = components.child.get("data").getFloat("importScale") / UNITS_PER_METRE;
            Vector3 scale = root.getTransform().getLocalScale();
            assertEquals(size, scale.x, EPSILON, root.getName() + " keeps its relative size");
            assertEquals(scale.x, scale.y, EPSILON);
            assertEquals(scale.x, scale.z, EPSILON);
            assertFalse(root.getTransform().getChildren().isEmpty(), root.getName() + " has the prefab's model under it");
            replaced++;
        }
        assertEquals(REPLACED_COUNT, replaced, "every generated oak, pine, rock and bush is replaced");
        assertTrue(kept >= 10, "the ground, lake, mountains, cabin, lights, camera and Hero are kept: " + kept);
        for (Entity entity : refreshed.getAllEntities()) {
            ModelComponent model = entity.getComponent(ModelComponent.class);
            if (model != null) {
                assertFalse(REPLACED.containsKey(model.getModelPath()), entity.getName() + " still shows " + model.getModelPath());
            }
        }
        refreshed.dispose();
    }

    @Test
    void shouldChangeNothingWhenTheEditorSyncsTheRefreshedPrefabsOnOpening() throws IOException {
        Opened opened = openProject("Synced");
        Scene asSaved = opened.editor().getSerializer().fromJson(Files.readString(opened.sceneFile().toPath()));
        assertEquals(opened.editor().getSerializer().toJson(asSaved), opened.editor().getSerializer().toJson(opened.editor().getActiveScene()),
            "every instance already matches its prefab with its stored overrides");
        asSaved.dispose();
    }

    @Test
    void shouldCopyTheCC0ModelsAndTheirLicenceRecordsIntoAProjectMadeFromTheRefreshedSample() throws IOException {
        Opened opened = openProject("Licensed");
        ThirdPartyManifest manifest = ThirdPartyManifest.read(opened.root());
        ThirdPartyManifest.Entry nature = manifest.entry("kenney-nature-kit").orElseThrow();
        for (String model : List.of("tree_oak.glb", "tree_pineTallA.glb", "rock_tallA.glb", "plant_bushLarge.glb")) {
            String path = "thirdparty/kenney/nature-kit/Models/GLTF format/" + model;
            assertTrue(new File(opened.root(), path).isFile(), path + " is in the project, so it draws offline");
            assertTrue(nature.files().stream().anyMatch(f -> f.path().equals(path)), path + " has its CC0 record in the project");
        }
        assertEquals("CC0-1.0", nature.licence());
    }

    @Test
    void shouldPlaceMoreMoveUndoRedoSaveAndReopenTheRefreshedObjects() throws IOException {
        Opened opened = openProject("Edited");
        EditorState editor = opened.editor();
        Scene scene = editor.getActiveScene();
        String pine = StarterPalette.LEVEL_FOLDER + "/CC0 Pine Tree.prefab";
        int pines = instancesOf(scene, pine).size();
        assertEquals(26, pines);

        // Multiple instances: three more pines from Objects, each its own undo step
        PrefabService prefabs = editor.getPrefabService();
        List<Entity> placed = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Entity root = prefabs.instantiate(new AssetReference(pine), new Vector3(-600f + i * 300f, 0f, 1200f), 30f * i, 1f, true);
            assertNotNull(root, editor.getStatusMessage());
            placed.add(root);
        }
        assertEquals(pines + 3, instancesOf(scene, pine).size());
        assertEquals(placed.get(2), editor.getSelectedEntity(), "the last one placed is selected");
        assertTrue(editor.undo());
        assertEquals(pines + 2, instancesOf(scene, pine).size(), "undo takes the last pine away");
        assertTrue(editor.redo());
        assertEquals(pines + 3, instancesOf(scene, pine).size(), "redo puts it back");

        // Select a refreshed rock by one of its members, as a click on its model does, and move it in one undo step
        Entity rock = scene.findEntityByName("Rock 1");
        Entity member = rock.getTransform().getChildren().get(0).getEntity();
        assertEquals(rock, PrefabService.instanceRootOf(member), "a click on the rock's model finds the instance");
        editor.setSelectedEntity(rock);
        Vector3 from = new Vector3(rock.getTransform().getLocalPosition());
        Vector3 size = new Vector3(rock.getTransform().getLocalScale());
        TransformSnapshot before = TransformSnapshot.capture(rock.getTransform());
        rock.getTransform().setLocalPosition(from.x + MOVE, from.y, from.z);
        TransformSnapshot after = TransformSnapshot.capture(rock.getTransform());
        assertTrue(editor.executeCommand(new ChangeTransformCommand(editor, rock.getId(), before, after, "Move Rock 1")));
        assertTrue(editor.undo());
        assertEquals(from, rock.getTransform().getLocalPosition(), "undo puts the rock back");
        assertTrue(editor.redo());
        assertEquals(from.x + MOVE, rock.getTransform().getLocalPosition().x, EPSILON, "redo moves it again");

        // Save and reopen: the moved rock, its size and link, and the placed pines survive
        assertTrue(opened.files().save(), editor.getStatusMessage());
        Opened back = reopen(opened.root(), opened.sceneFile());
        Scene reread = back.editor().getActiveScene();
        Entity rockBack = reread.findEntityById(rock.getId());
        assertNotNull(rockBack);
        assertEquals(from.x + MOVE, rockBack.getTransform().getLocalPosition().x, EPSILON, "the move survives");
        assertEquals(size, rockBack.getTransform().getLocalScale(), "its size, the instance's override, survives the prefab sync");
        assertEquals(StarterPalette.LEVEL_FOLDER + "/CC0 Rock.prefab", PrefabService.linkOf(rockBack).getPrefabPath());
        assertEquals(pines + 3, instancesOf(reread, pine).size(), "the placed pines survive");
        for (Entity root : placed) {
            assertNotNull(reread.findEntityById(root.getId()), root.getName() + " survives");
        }
    }

    @Test
    void shouldBlockAtEveryRefreshedTrunkAndRockInPlayLetBushesThroughAndRestoreTheSceneOnStop() throws IOException {
        Opened opened = openProject("Played");
        EditorState editor = opened.editor();
        Scene scene = editor.getActiveScene();
        String edited = editor.getSerializer().toJson(scene);
        String onDisk = Files.readString(opened.sceneFile().toPath());
        Entity hero = null;
        for (Entity entity : scene.getAllEntities()) {
            if (entity.getComponent(PlayerComponent.class) != null) {
                hero = entity;
            }
        }
        assertNotNull(hero, "the Hero is kept");
        Vector3 heroAt = new Vector3(hero.getTransform().getWorldPosition());

        PlayModeController play = new PlayModeController(editor);
        editor.setPlayModeController(play);
        boolean[] forward = {false};
        play.getProfileStep().setInput(key -> forward[0] && key == Input.Keys.W);
        assertTrue(play.play(), editor.getStatusMessage());
        int blocked = 0;
        int walkedThrough = 0;
        try {
            Scene runtime = play.getRuntimeScene();
            for (int i = 0; i < PLAY_FRAMES; i++) {
                play.update(FRAME);
            }
            PhysicsSystem3D physics = runtime.getPhysics3D();
            for (Map.Entry<String, String> replaced : REPLACED.entrySet()) {
                boolean bush = replaced.getValue().endsWith("/CC0 Bush.prefab");
                for (Entity root : instancesOf(runtime, replaced.getValue())) {
                    Vector3 foot = root.getTransform().getWorldPosition();
                    Vector3 origin = new Vector3(foot.x, foot.y + RAY_ABOVE, foot.z);
                    Optional<PhysicsSystem3D.RaycastHit> hit = physics.raycast(origin, new Vector3(0f, -1f, 0f), RAY_ABOVE * 2f,
                        PhysicsLayers3D.ALL_LAYERS);
                    boolean hitsIt = hit.isPresent() && PrefabService.instanceRootOf(hit.get().entity()) == root;
                    if (bush) {
                        assertFalse(hitsIt, root.getName() + " is walked through");
                        walkedThrough++;
                    } else {
                        assertTrue(hitsIt, root.getName() + " blocks at its trunk or body: " + hit.map(h -> h.entity().getName()));
                        blocked++;
                    }
                }
            }
            // The Hero walks in the runtime copy, not in the edited scene
            forward[0] = true;
            for (int i = 0; i < PLAY_FRAMES * 2; i++) {
                play.update(FRAME);
            }
            Vector3 walked = runtime.findEntityById(hero.getId()).getTransform().getWorldPosition();
            assertTrue(walked.dst(heroAt) > 100f, "the Hero walks in Play: from " + heroAt + " to " + walked);
            assertEquals(heroAt, hero.getTransform().getWorldPosition(), "the edited Hero stays put while Play runs");
        } finally {
            play.stop();
        }
        assertEquals(49, blocked, "11 oaks, 26 pines and 12 rocks block");
        assertEquals(16, walkedThrough, "16 bushes do not");
        assertFalse(play.isPlaying());
        assertEquals(edited, editor.getSerializer().toJson(editor.getActiveScene()), "Stop leaves the edited scene exactly as it was");
        assertEquals(onDisk, Files.readString(opened.sceneFile().toPath()), "and Play wrote nothing to the file");
    }

    /** Dialogs never shown: the tests save to the file they opened and ask nothing. */
    private static final class NoDialogs implements EditorDialogService {
        @Override
        public Optional<File> chooseOpenFile(String title, File initialDirectory, String filterDescription, String extension) {
            throw new AssertionError("no open dialog: " + title);
        }

        @Override
        public Optional<File> chooseSaveFile(String title, File suggestedFile, String filterDescription, String extension) {
            throw new AssertionError("no save dialog: " + title);
        }

        @Override
        public UnsavedChangesChoice askUnsavedChanges(String documentName) {
            throw new AssertionError("no unsaved-changes question: " + documentName);
        }

        @Override
        public void showError(String title, String message) {
            throw new AssertionError(title + ": " + message);
        }
    }
}
