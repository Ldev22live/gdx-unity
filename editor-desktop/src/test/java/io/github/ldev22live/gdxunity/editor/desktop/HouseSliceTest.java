package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.DeleteEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.project.EditorProject;
import io.github.ldev22live.gdxunity.editor.core.project.GameExportCommand;
import io.github.ldev22live.gdxunity.editor.core.project.GameExporter;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.core.workspace.Workspace;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceNavigator;
import io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.NoDialogs;
import io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.RecordingView;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshOperationScript;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshSaveSession;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshWorkspace;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.desktop.GameSession;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.FRAME;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.fakeRuntimeImage;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.newEditor;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.place;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.playExported;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap MC.2, the 3D house slice (Appendix C3): the CC0 Kenney house from the Buildings palette, its roof changed entirely in
 * ForgeRoom by the slice's deterministic operation script ({@code fixtures/mesh-sources/derived/kenney-house-roof}), with the three
 * scopes kept distinct. <b>Create Unique Mesh</b> changes one placed house and leaves the prefab file alone. <b>Edit Shared
 * Prefab</b> warns with the instance count, then changes the prefab and every house that has not overridden the roof, including
 * houses placed afterwards. <b>Edit Instance</b> (a placed house's own overrides) wins over the shared prefab. Then Action RPG +
 * 3D Play, Stop, undo, save, reopen, Play and export, with the shipped originals unchanged.
 */
class HouseSliceTest {
    /** Tests run in the module folder; the shipped assets and fixtures are one level up. */
    private static final File ASSETS = new File("../assets");
    private static final Path SLICE = Path.of("..", "fixtures", "mesh-sources", "derived", "kenney-house-roof");
    private static final String HOUSE_PREFAB = StarterPalette.BUILDING_FOLDER + "/CC0 House.prefab";
    private static final String ROOF = "Roof NE";
    private static final Vector3 UNIQUE_AT = new Vector3(-500f, 0f, -600f);
    private static final Vector3 SHARED_AT = new Vector3(500f, 0f, -600f);
    private static final Vector3 LATER_AT = new Vector3(0f, 0f, -1400f);
    private static final Vector3 SPAWN_AT = new Vector3(-400f, 0f, 400f);
    private static final Vector3 PLAYER_AT = new Vector3(300f, 0f, 500f);

    @TempDir
    Path temp;

    private static Input previousInput;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
        previousInput = Gdx.input;
        // The game session reads the keyboard; none is held
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

    @Test
    void shouldKeepUniqueMeshSharedPrefabAndInstanceEditsDistinctThroughPlayReopenAndExport() throws Exception {
        byte[] shippedPrefab = Files.readAllBytes(ASSETS.toPath().resolve(HOUSE_PREFAB));
        MeshOperationScript script = MeshOperationScript.read(SLICE.resolve("operations.json"));
        byte[] shippedRoof = Files.readAllBytes(ASSETS.toPath().resolve(script.source()));
        byte[] expectedDerived = Files.readAllBytes(SLICE.resolve("model.gltf"));

        // A new project with a level, the player, its spawn point and two houses from the Buildings palette
        EditorState editor = newEditor();
        TemplateGallery.Choice empty3D = TemplateGallery.emptyStarts(ASSETS).stream().filter(c -> c.mode() == GameMode.MODE_3D)
            .findFirst().orElseThrow();
        TemplateProject.Result made = TemplateGallery.create(empty3D, ASSETS, temp.resolve("project").toFile(), "Slice",
            editor.getSerializer());
        File root = made.folder();
        Path projectPrefab = root.toPath().resolve(HOUSE_PREFAB);
        File sceneFile = new File(root, made.startScene());
        SceneFileController files = new SceneFileController(editor, new NoDialogs(), root);
        assertTrue(files.openFile(sceneFile), editor.getStatusMessage());
        PrefabService prefabs = new PrefabService(editor, root);
        editor.setPrefabService(prefabs);
        TerrainLibrary terrains = new TerrainLibrary(AssetSource.directory(root));
        Scene scene = editor.getActiveScene();
        scene.setHeightFields(new TerrainHeightFields(terrains));
        scene.setColliderGeometry(new ColliderGeometry(null));
        LevelSession level = new LevelSession(editor, new TerrainSession(terrains), root);
        level.setActive(true);
        assertTrue(editor.executeCommand(new DeleteEntityCommand(editor, scene.findEntityByName("Ground").getId())));
        assertNotNull(level.createLevel(Surface.GRASS), editor.getStatusMessage());
        Terrain terrain = level.findTerrain();
        level.setFocus(LevelSession.Focus.CHARACTERS);
        assertNotNull(place(level, terrain, StarterPalette.CHARACTER_FOLDER + "/Player.prefab", PLAYER_AT), editor.getStatusMessage());
        assertNotNull(place(level, terrain, StarterPalette.CHARACTER_FOLDER + "/Spawn Point.prefab", SPAWN_AT), editor.getStatusMessage());
        assertTrue(editor.setGameGenre(GameGenres.ACTION_RPG));
        level.setFocus(LevelSession.Focus.OBJECTS);
        Entity uniqueHouse = place(level, terrain, HOUSE_PREFAB, UNIQUE_AT);
        Entity sharedHouse = place(level, terrain, HOUSE_PREFAB, SHARED_AT);
        assertNotNull(uniqueHouse, editor.getStatusMessage());
        assertNotNull(sharedHouse, editor.getStatusMessage());
        String original = roofOf(uniqueHouse);
        assertEquals(script.source(), original, "the script's source is the house roof's model");
        WorkspaceNavigator navigator = new WorkspaceNavigator(editor, new RecordingView());

        // Create Unique Mesh: the first house's roof only; the prefab file is untouched
        byte[] prefabBefore = Files.readAllBytes(projectPrefab);
        MeshSaveSession unique = openRoof(editor, navigator, root, uniqueHouse);
        script.applyTo(unique.drafts());
        String uniqueModel = unique.save(MeshSaveSession.Mode.UNIQUE, "Roof NE raised", editor, false);
        navigator.backToWorld();
        assertEquals(script.output(), uniqueModel);
        assertArrayEquals(expectedDerived, Files.readAllBytes(root.toPath().resolve(uniqueModel)),
            "the editor's save equals the slice's shipped derived model: the script is the edit");
        assertEquals(uniqueModel, roofOf(uniqueHouse));
        assertEquals(original, roofOf(sharedHouse), "a unique mesh changes no other house");
        assertArrayEquals(prefabBefore, Files.readAllBytes(projectPrefab), "nor the prefab");
        assertTrue(prefabs.getOverrides(roofEntity(uniqueHouse)).stream().anyMatch(p -> p.contains("ModelComponent")),
            "the unique mesh is this instance's override");

        // Edit Shared Prefab from the second house: refused without confirmation, the warning names every instance
        MeshSaveSession shared = openRoof(editor, navigator, root, sharedHouse);
        script.applyTo(shared.drafts());
        IOException warning = assertThrows(IOException.class,
            () -> shared.save(MeshSaveSession.Mode.PREFAB, "Roof NE shared", editor, false));
        assertTrue(warning.getMessage().contains("All 2 instance(s)"), warning.getMessage());
        assertEquals(original, roofOf(sharedHouse), "nothing changes before the person confirms");
        String sharedModel = shared.save(MeshSaveSession.Mode.PREFAB, "Roof NE shared", editor, true);
        navigator.backToWorld();
        assertArrayEquals(expectedDerived, Files.readAllBytes(root.toPath().resolve(sharedModel)));
        assertEquals(sharedModel, roofOf(sharedHouse));
        assertEquals(uniqueModel, roofOf(uniqueHouse), "an instance's own override wins over the shared prefab");
        assertTrue(Files.readString(projectPrefab).contains(sharedModel), "the project's prefab holds the shared roof");
        Entity laterHouse = place(level, terrain, HOUSE_PREFAB, LATER_AT);
        assertEquals(sharedModel, roofOf(laterHouse), "a house placed afterwards has the shared roof");
        assertTrue(editor.undo(), "the later placement undoes");

        // Undo and redo the shared edit: the prefab file and both houses follow
        assertTrue(editor.undo());
        assertEquals(original, roofOf(sharedHouse));
        assertArrayEquals(prefabBefore, Files.readAllBytes(projectPrefab), "undo restores the prefab file");
        assertEquals(uniqueModel, roofOf(uniqueHouse));
        assertTrue(editor.redo());
        assertEquals(sharedModel, roofOf(sharedHouse));

        // Action RPG + 3D Play shows both roofs; Stop returns the scene unchanged
        String authored = editor.getSerializer().toJson(scene);
        assertEquals(Map.of("unique", uniqueModel, "shared", sharedModel), playedRoofs(editor, uniqueHouse, sharedHouse));
        assertEquals(authored, editor.getSerializer().toJson(editor.getActiveScene()), "Stop leaves the scene as it was");

        // Save, close, reopen (the prefab sync runs), Play again
        assertTrue(files.save(), editor.getStatusMessage());
        EditorState reopened = newEditor();
        reopened.setPrefabService(new PrefabService(reopened, root));
        assertTrue(new SceneFileController(reopened, new NoDialogs(), root).openFile(sceneFile), reopened.getStatusMessage());
        Scene back = reopened.getActiveScene();
        back.setHeightFields(new TerrainHeightFields(new TerrainLibrary(AssetSource.directory(root))));
        back.setColliderGeometry(new ColliderGeometry(null));
        Entity uniqueBack = back.findEntityById(uniqueHouse.getId());
        Entity sharedBack = back.findEntityById(sharedHouse.getId());
        assertEquals(uniqueModel, roofOf(uniqueBack), "the unique roof survives reopening");
        assertEquals(sharedModel, roofOf(sharedBack), "the shared roof survives reopening");
        assertEquals(Map.of("unique", uniqueModel, "shared", sharedModel), playedRoofs(reopened, uniqueBack, sharedBack));

        // Export: both derived models byte for byte, both houses bound, the game's files play without errors
        GameExporter.Result exported = GameExportCommand.run(new EditorProject(root), null, fakeRuntimeImage(temp.resolve("image")).toFile(),
            temp.resolve("export").toFile());
        Path game = exported.folder().toPath().resolve("app").resolve(GameExporter.GAME_FOLDER);
        assertArrayEquals(expectedDerived, Files.readAllBytes(game.resolve(uniqueModel)));
        assertArrayEquals(expectedDerived, Files.readAllBytes(game.resolve(sharedModel)));
        Scene exportedScene = GameSession.loadScene(game.resolve(made.startScene()).toFile());
        assertEquals(uniqueModel, roofOf(exportedScene.findEntityById(uniqueHouse.getId())));
        assertEquals(sharedModel, roofOf(exportedScene.findEntityById(sharedHouse.getId())));
        assertEquals(List.of(), playExported(game.toFile(), exportedScene), "the exported game's files play in the game runtime");

        // The shipped originals: the repository's prefab and the CC0 roof model are unchanged
        assertArrayEquals(shippedPrefab, Files.readAllBytes(ASSETS.toPath().resolve(HOUSE_PREFAB)));
        assertArrayEquals(shippedRoof, Files.readAllBytes(ASSETS.toPath().resolve(script.source())));
        assertArrayEquals(shippedRoof, Files.readAllBytes(root.toPath().resolve(script.source())), "and the project's copy too");
    }

    /** Edit 3D Model on {@code house}, then the 3D Modeling session of its Roof NE part. */
    private static MeshSaveSession openRoof(EditorState editor, WorkspaceNavigator navigator, File root, Entity house)
        throws IOException {
        editor.setSelectedEntity(house);
        assertTrue(navigator.editModel(house), editor.getStatusMessage());
        assertEquals(Workspace.MODELING_3D, navigator.current());
        return new MeshSaveSession(root.toPath(), new MeshWorkspace(root.toPath(), house).open(roofEntity(house)));
    }

    private static Entity roofEntity(Entity house) {
        for (var child : house.getTransform().getChildren()) {
            if (child.getEntity().getName().equals(ROOF)) {
                return child.getEntity();
            }
        }
        throw new AssertionError(house.getName() + " has no " + ROOF);
    }

    private static String roofOf(Entity house) {
        return roofEntity(house).getComponent(ModelComponent.class).getModelPath();
    }

    /** One frame of editor Play: the roof models of the two houses in the runtime copy, then Stop. */
    private static Map<String, String> playedRoofs(EditorState editor, Entity uniqueHouse, Entity sharedHouse) {
        PlayModeController play = new PlayModeController(editor);
        editor.setPlayModeController(play);
        play.getProfileStep().setInput(key -> false);
        assertTrue(play.play(), editor.getStatusMessage());
        try {
            play.update(FRAME);
            Scene runtime = play.getRuntimeScene();
            return Map.of("unique", roofOf(runtime.findEntityById(uniqueHouse.getId())),
                "shared", roofOf(runtime.findEntityById(sharedHouse.getId())));
        } finally {
            play.stop();
        }
    }
}
