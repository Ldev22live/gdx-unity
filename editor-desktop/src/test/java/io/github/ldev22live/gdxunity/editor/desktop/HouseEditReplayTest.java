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
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshSaveSession;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshWorkspace;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.desktop.GameSession;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.sculpt.StaticMeshImport;
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
import java.util.stream.Stream;

import io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.NoDialogs;
import io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.RecordingView;

import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.FRAME;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.fakeRuntimeImage;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.newEditor;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.place;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.playExported;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M3c.6, house-edit replay: on a new project, a house from the Buildings palette is placed in the World, one roof part is
 * raised in 3D Modeling and saved as a derived model bound to that part, Back to World, Play, Stop, save, close, reopen, Play again
 * and export. At every stage the scene (editor, Play, reopened, exported) must show the raised roof: the part is bound to the derived
 * model and that model's geometry is the raised one, while the provider original and the house's other roof parts stay as shipped.
 *
 * <p>Drawing the result needs a GL context and is checked by {@link HouseEditReplayGraphicsSmoke}; this test covers everything that
 * runs headless and is part of the Gradle suite.
 */
class HouseEditReplayTest {
    /** Tests run in the module folder; the shipped assets are one level up. */
    private static final File ASSETS = new File("../assets");
    private static final String HOUSE_PREFAB = StarterPalette.BUILDING_FOLDER + "/CC0 House.prefab";
    /** The roof part raised; its three siblings must stay on the shipped model. */
    private static final String ROOF = "Roof NE";
    private static final List<String> OTHER_ROOFS = List.of("Roof SE", "Roof SW", "Roof NW");
    /** How far the roof top is raised, in the model's own units (the prefab's import scale makes it world units). */
    private static final float RAISE = 0.4f;
    /** Where the house stands, away from the player and spawn point, in level coordinates. */
    private static final Vector3 HOUSE_AT = new Vector3(0f, 0f, -600f);
    private static final Vector3 SPAWN_AT = new Vector3(-400f, 0f, 400f);
    private static final Vector3 PLAYER_AT = new Vector3(300f, 0f, 500f);
    private static final float EPSILON = 1e-3f;

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
    void shouldShowTheRaisedRoofInTheSceneInPlayAfterReopeningAndInTheExport() throws Exception {
        // A new project (Empty 3D) with a flat level, the player and its spawn point, as in the owner journey
        EditorState editor = newEditor();
        TemplateGallery.Choice empty3D = TemplateGallery.emptyStarts(ASSETS).stream().filter(c -> c.mode() == GameMode.MODE_3D)
            .findFirst().orElseThrow();
        TemplateProject.Result made = TemplateGallery.create(empty3D, ASSETS, temp.resolve("project").toFile(), "Replay",
            editor.getSerializer());
        File root = made.folder();
        File sceneFile = new File(root, made.startScene());
        SceneFileController files = new SceneFileController(editor, new NoDialogs(), root);
        assertTrue(files.openFile(sceneFile), editor.getStatusMessage());
        editor.setPrefabService(new PrefabService(editor, root));
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

        // The house from the Buildings palette, placed on the ground in one undo step
        level.setFocus(LevelSession.Focus.OBJECTS);
        Entity house = place(level, terrain, HOUSE_PREFAB, HOUSE_AT);
        assertNotNull(house, editor.getStatusMessage());
        Entity roof = scene.findEntityByName(ROOF);
        assertNotNull(roof, "the house has its roof parts");
        String shipped = roof.getComponent(ModelComponent.class).getModelPath();
        Path original = root.toPath().resolve(shipped);
        assertTrue(Files.isRegularFile(original), "the new project holds the house's model: " + shipped);
        byte[] originalBytes = Files.readAllBytes(original);
        float importScale = roof.getComponent(ModelComponent.class).getImportScale();
        float shippedTop = topOf(root.toPath(), shipped);
        float shippedTopInWorld = worldTop(roof, shippedTop);

        // Edit 3D Model on the house opens 3D Modeling with its parts; the roof part's top is raised and saved as a derived model
        editor.setSelectedEntity(house);
        WorkspaceNavigator navigator = new WorkspaceNavigator(editor, new RecordingView());
        assertTrue(navigator.editModel(house), editor.getStatusMessage());
        assertEquals(Workspace.MODELING_3D, navigator.current());
        MeshWorkspace workspace = new MeshWorkspace(root.toPath(), house);
        assertEquals(HOUSE_PREFAB, workspace.prefabPath());
        assertTrue(workspace.parts().contains(roof), "the roof is one of the house's editable parts");
        MeshSaveSession modeling = new MeshSaveSession(root.toPath(), workspace.open(roof));
        int raised = raiseTop(modeling.drafts(), RAISE);
        assertTrue(raised >= 3, "the roof's top vertices were moved: " + raised);
        String derived = modeling.save(MeshSaveSession.Mode.UNIQUE, "Roof NE raised", editor, false);
        navigator.backToWorld();
        assertEquals(Workspace.WORLD, navigator.current());
        assertSame(house, editor.getSelectedEntity(), "Back to World keeps the house selected");

        // The World: the roof part shows the derived model, raised; the original and the other roof parts are as shipped
        assertNotEquals(shipped, derived);
        assertEquals(derived, roof.getComponent(ModelComponent.class).getModelPath());
        assertEquals(shippedTop + RAISE, topOf(root.toPath(), derived), EPSILON, "the derived model's roof is raised");
        assertEquals(shippedTopInWorld + RAISE * importScale, worldTop(roof, topOf(root.toPath(), derived)), EPSILON * importScale);
        for (String other : OTHER_ROOFS) {
            assertEquals(shipped, scene.findEntityByName(other).getComponent(ModelComponent.class).getModelPath(), other);
        }
        assertArrayEquals(originalBytes, Files.readAllBytes(original), "the provider original is never overwritten");
        assertTrue(editor.undo(), "the binding undoes");
        assertEquals(shipped, roof.getComponent(ModelComponent.class).getModelPath());
        assertTrue(editor.redo(), "and redoes");
        assertEquals(derived, roof.getComponent(ModelComponent.class).getModelPath());

        // Play shows the derived roof; Stop returns the authoring scene unchanged
        String authored = editor.getSerializer().toJson(scene);
        assertEquals(derived, playedRoof(editor), "Play shows the raised roof");
        assertEquals(authored, editor.getSerializer().toJson(editor.getActiveScene()), "Stop leaves the scene as it was");

        // Save, close, reopen: the prefab override keeps the derived roof through the prefab's sync; Play again shows it
        assertTrue(files.save(), editor.getStatusMessage());
        assertTrue(Files.readString(sceneFile.toPath()).contains(derived), "the saved scene records the derived roof");
        EditorState reopened = newEditor();
        reopened.setPrefabService(new PrefabService(reopened, root));
        assertTrue(new SceneFileController(reopened, new NoDialogs(), root).openFile(sceneFile), reopened.getStatusMessage());
        Scene back = reopened.getActiveScene();
        back.setHeightFields(new TerrainHeightFields(new TerrainLibrary(AssetSource.directory(root))));
        back.setColliderGeometry(new ColliderGeometry(null));
        Entity reopenedRoof = back.findEntityByName(ROOF);
        assertEquals(derived, reopenedRoof.getComponent(ModelComponent.class).getModelPath(), "the derived roof survives reopening");
        for (String other : OTHER_ROOFS) {
            assertEquals(shipped, back.findEntityByName(other).getComponent(ModelComponent.class).getModelPath(), other);
        }
        assertEquals(shippedTopInWorld + RAISE * importScale, worldTop(reopenedRoof, topOf(root.toPath(), derived)),
            EPSILON * importScale, "the reopened roof stands where it was raised");
        assertEquals(derived, playedRoof(reopened), "Play after reopening shows the raised roof");

        // Export: the game holds the derived model byte for byte, binds it to the roof, and its files play without errors
        GameExporter.Result exported = GameExportCommand.run(new EditorProject(root), null, fakeRuntimeImage(temp.resolve("image")).toFile(),
            temp.resolve("export").toFile());
        Path game = exported.folder().toPath().resolve("app").resolve(GameExporter.GAME_FOLDER);
        assertArrayEquals(Files.readAllBytes(root.toPath().resolve(derived)), Files.readAllBytes(game.resolve(derived)));
        assertTrue(Files.isRegularFile(game.resolve(shipped)), "the other roof parts' shipped model is exported too");
        Scene exportedScene = GameSession.loadScene(game.resolve(made.startScene()).toFile());
        assertEquals(derived, exportedScene.findEntityByName(ROOF).getComponent(ModelComponent.class).getModelPath());
        assertEquals(List.of(), playExported(game.toFile(), exportedScene), "the exported game's files play in the game runtime");
    }

    @Test
    void shouldRefuseToOverwriteTheShippedRoofModel() throws Exception {
        EditorState editor = newEditor();
        TemplateGallery.Choice empty3D = TemplateGallery.emptyStarts(ASSETS).stream().filter(c -> c.mode() == GameMode.MODE_3D)
            .findFirst().orElseThrow();
        TemplateProject.Result made = TemplateGallery.create(empty3D, ASSETS, temp.resolve("refuse").toFile(), "Refuse",
            editor.getSerializer());
        File root = made.folder();
        assertTrue(new SceneFileController(editor, new NoDialogs(), root).openFile(new File(root, made.startScene())));
        PrefabService prefabs = new PrefabService(editor, root);
        editor.setPrefabService(prefabs);
        Entity house = prefabs.instantiate(new AssetReference(HOUSE_PREFAB), HOUSE_AT);
        assertNotNull(house, editor.getStatusMessage());
        Entity roof = editor.getActiveScene().findEntityByName(ROOF);
        String shipped = roof.getComponent(ModelComponent.class).getModelPath();
        byte[] before = Files.readAllBytes(root.toPath().resolve(shipped));
        MeshSaveSession modeling = new MeshSaveSession(root.toPath(), new MeshWorkspace(root.toPath(), house).open(roof));
        assertTrue(raiseTop(modeling.drafts(), RAISE) > 0);

        IOException refused = assertThrows(IOException.class,
            () -> modeling.save(MeshSaveSession.Mode.UPDATE, null, editor, true));
        assertTrue(refused.getMessage().contains("read-only"), refused.getMessage());
        assertArrayEquals(before, Files.readAllBytes(root.toPath().resolve(shipped)), "the shipped roof model is unchanged");
        assertEquals(shipped, roof.getComponent(ModelComponent.class).getModelPath(), "the roof stays bound to it");
        assertFalse(Files.exists(root.toPath().resolve("models/edited")) && listed(root.toPath().resolve("models/edited")) > 0,
            "nothing was written");
    }

    /**
     * Moves every vertex at the top of the draft that reaches highest up by {@code amount}, one undoable translate per vertex, as a
     * person does when selecting the roof's top and dragging it up.
     *
     * @return how many vertices were moved
     */
    private static int raiseTop(List<MeshPartEditor> drafts, float amount) {
        MeshPartEditor highest = null;
        float top = Float.NEGATIVE_INFINITY;
        for (MeshPartEditor draft : drafts) {
            for (MeshPartEditor.Vertex vertex : draft.geometry().vertices()) {
                if (vertex.y() > top) {
                    top = vertex.y();
                    highest = draft;
                }
            }
        }
        assertNotNull(highest, "the roof has geometry");
        List<MeshPartEditor.Vertex> vertices = highest.geometry().vertices();
        int moved = 0;
        for (int i = 0; i < vertices.size(); i++) {
            if (vertices.get(i).y() >= top - EPSILON) {
                assertTrue(highest.apply(MeshPartEditor.Tool.Translate, MeshPartEditor.Kind.Vertex, i, 0f, amount, 0f));
                moved++;
            }
        }
        return moved;
    }

    /** The highest point of a model in its own units, read from the project file as the workspace reads it (no GL). */
    private static float topOf(Path root, String model) throws IOException {
        float top = Float.NEGATIVE_INFINITY;
        for (MeshPartEditor draft : MeshPartEditor.open(StaticMeshImport.read(root, Path.of(model)))) {
            for (MeshPartEditor.Vertex vertex : draft.geometry().vertices()) {
                top = Math.max(top, vertex.y());
            }
        }
        return top;
    }

    /** Where a model's highest point stands in the world for {@code part}: its transform and import scale (the prefab is unrotated). */
    private static float worldTop(Entity part, float modelTop) {
        float scale = part.getComponent(ModelComponent.class).getImportScale();
        return new Vector3(0f, modelTop * scale, 0f).mul(part.getTransform().getWorldMatrix()).y;
    }

    /** Plays one frame in editor Play, returns the roof part's model there, and stops. */
    private static String playedRoof(EditorState editor) {
        PlayModeController play = new PlayModeController(editor);
        editor.setPlayModeController(play);
        play.getProfileStep().setInput(key -> false);
        assertTrue(play.play(), editor.getStatusMessage());
        try {
            play.update(FRAME);
            return play.getRuntimeScene().findEntityByName(ROOF).getComponent(ModelComponent.class).getModelPath();
        } finally {
            play.stop();
        }
    }

    private static long listed(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.count();
        }
    }
}
