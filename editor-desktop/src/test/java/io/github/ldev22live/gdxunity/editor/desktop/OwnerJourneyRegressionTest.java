package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetImport;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.command.DeleteEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeState;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.project.EditorProject;
import io.github.ldev22live.gdxunity.editor.core.project.GameExportCommand;
import io.github.ldev22live.gdxunity.editor.core.project.GameExporter;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.editor.core.workspace.CustomizeGateway;
import io.github.ldev22live.gdxunity.editor.core.workspace.ModelingHandOff;
import io.github.ldev22live.gdxunity.editor.core.workspace.Workspace;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceNavigator;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.editor.terrain.LevelViewportInteraction;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainSession;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainViewportInteraction;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.desktop.GameScripts;
import io.github.ldev22live.gdxunity.engine.desktop.GameSession;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainIO;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M3c.5: the owner acceptance journey (milestone chart section 3, roadmap section 1a) as one regression test, run through the
 * editor's own operations on a new project: New Game (empty 3D start), sculpt and paint the ground, place objects, place the player and
 * its spawn point, Action RPG + 3D, Play and walk on the sculpted ground, Stop with nothing changed, import a house and hand it to 3D
 * Modeling, save, close, reopen, Play again, export and run the exported game's files in the game runtime.
 *
 * <p>Each step is recorded PASS or BLOCKED with its reason in {@code build/owner-journey/journey-steps.md}; a BLOCKED step names the
 * milestone that unblocks it and is asserted to stay what it is, so a step cannot quietly start or stop passing. The project is left in
 * {@code build/owner-journey/project} for the human desktop checklist (docs/reports/m3c-5-owner-journey-regression.md) and for running
 * the real editor and game runtime on it. What a person must see, hear or click is not checked here.
 */
class OwnerJourneyRegressionTest {
    /** Tests run in the module folder; the shipped assets are one level up. */
    private static final File ASSETS = new File("../assets");
    private static final Path OUT = Path.of("build", "owner-journey");
    private static final float FRAME = 1f / 60f;
    /** Two seconds of walking. */
    private static final int WALK_FRAMES = 120;
    /** The panel the strokes are made in, and the camera looking down at the middle of the level. */
    private static final float WIDTH = 800f;
    private static final float HEIGHT = 600f;
    /** Where the person sculpts a hill, and the player and spawn point stand, in level coordinates. */
    private static final Vector3 SPAWN_AT = new Vector3(-400f, 0f, 400f);
    private static final Vector3 PLAYER_AT = new Vector3(300f, 0f, 500f);
    private static final String HOUSE_MODEL = "models/landscape/cabin.gltf";

    /** One step's result. */
    private record Step(int number, String what, boolean passed, String evidence) { }

    @TempDir
    Path temp;

    private final List<Step> steps = new ArrayList<>();

    private static Input previousInput;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
        previousInput = Gdx.input;
        // The exported game's session reads the keyboard; none is held, as in TemplateGalleryProjectsTest
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

    private void pass(int number, String what, String evidence) {
        steps.add(new Step(number, what, true, evidence));
    }

    private void blocked(int number, String what, String reason) {
        steps.add(new Step(number, what, false, reason));
    }

    @Test
    void shouldRunTheOwnerJourneyFromANewProjectToAnExportedGame() throws Exception {
        clean(OUT);
        Map<String, Long> shipped = snapshot(ASSETS.toPath().resolve("samples"));

        // 1. Launch; create a project (Project > New Game > Empty 3D); it opens in World
        EditorState editor = newEditor();
        TemplateGallery.Choice empty3D = TemplateGallery.emptyStarts(ASSETS).stream().filter(c -> c.mode() == GameMode.MODE_3D)
            .findFirst().orElseThrow();
        TemplateProject.Result made = TemplateGallery.create(empty3D, ASSETS, OUT.resolve("project").toFile(), "Journey",
            editor.getSerializer());
        File root = made.folder();
        File sceneFile = new File(root, made.startScene());
        SceneFileController files = new SceneFileController(editor, new NoDialogs(), root);
        assertTrue(files.openFile(sceneFile), editor.getStatusMessage());
        TerrainLibrary terrains = new TerrainLibrary(AssetSource.directory(root));
        Scene scene = editor.getActiveScene();
        // What the editor's renderer gives the open scene: the ground's heights, and the meshes of hull colliders (the Boulder's, TD-096)
        scene.setHeightFields(new TerrainHeightFields(terrains));
        scene.setColliderGeometry(new ColliderGeometry(null));
        RecordingView view = new RecordingView();
        WorkspaceNavigator navigator = new WorkspaceNavigator(editor, view);
        assertEquals(Workspace.WORLD, navigator.current());
        pass(1, "New project (Empty 3D) opens in World", "`" + root.getName() + "`, " + made.files().size() + " files, the starter palette "
            + "included; the World is the screen on open");

        // 2. Sculpt terrain, paint ground, an editable .terrain
        editor.setPrefabService(new PrefabService(editor, root));
        TerrainSession terrainSession = new TerrainSession(terrains);
        LevelSession level = new LevelSession(editor, terrainSession, root);
        level.setActive(true);
        Entity box = scene.findEntityByName("Ground");
        assertTrue(editor.executeCommand(new DeleteEntityCommand(editor, box.getId())), "the start's flat box is removed");
        Entity ground = level.createLevel(Surface.GRASS);
        assertNotNull(ground, editor.getStatusMessage());
        String terrainPath = ground.getComponent(TerrainComponent.class).getTerrainPath();
        Terrain terrain = level.findTerrain();
        PerspectiveCamera3D camera = new PerspectiveCamera3D(60f, WIDTH, HEIGHT, 1f, 20000f);
        camera.setPosition(0f, 800f, 500f);
        camera.setDirection(new Vector3(0f, -800f, -500f).nor());
        camera.update();
        ViewportInteraction strokes = new LevelViewportInteraction(level, camera,
            new TerrainViewportInteraction(editor, terrainSession, camera, new NothingElse()));
        level.setFocus(LevelSession.Focus.TERRAIN);
        level.setTool(LevelSession.Tool.RAISE);
        terrainSession.getBrush().setRadius(300f);
        terrainSession.getBrush().setStrength(1f);
        for (int i = 0; i < 4; i++) {
            stroke(strokes);
        }
        float hill = terrain.heightAt(0f, 0f);
        assertTrue(hill > 20f, "a hill was raised: " + hill);
        level.setTool(LevelSession.Tool.PAINT);
        level.setPaintSurface(Surface.GRAVEL);
        stroke(strokes);
        int centre = terrain.getResolutionX() / 2;
        assertEquals(Surface.GRAVEL, terrain.dominantSurface(centre, centre));
        Terrain onDisk = TerrainIO.fromJson(Files.readString(root.toPath().resolve(terrainPath)));
        assertArrayEquals(terrain.copyHeights(), onDisk.copyHeights(), "the .terrain file follows the strokes");
        assertTrue(editor.undo() && editor.redo(), "the paint stroke undoes and redoes");
        pass(2, "Sculpt a hill, paint gravel, an editable .terrain", "`" + terrainPath + "`: hill " + Math.round(hill)
            + " units high, gravel at the centre, file equals the edited terrain, the paint stroke undoes and redoes");

        // 3. Place trees and boulders (the starter palette); realistic verified CC0 assets do not exist yet
        level.setFocus(LevelSession.Focus.OBJECTS);
        Entity tree = place(level, terrain, StarterPalette.LEVEL_FOLDER + "/Oak Tree.prefab", 600f, -300f);
        Entity boulder = place(level, terrain, StarterPalette.LEVEL_FOLDER + "/Boulder.prefab", -500f, -200f);
        assertNotNull(tree, editor.getStatusMessage());
        assertNotNull(boulder, editor.getStatusMessage());
        pass(3, "Place trees and boulders from Objects", "Oak Tree and Boulder placed from the project's own palette, one undo step "
            + "each");
        blocked(3, "Realistic verified CC0 trees and boulders; scatter foliage", "No CC0 import or provenance yet (M2b.2, M2b.3); no "
            + "scatter tool (M3.6). The palette holds five primitive-built prefabs");

        // 4. Place the player from a character prefab, with its spawn point
        level.setFocus(LevelSession.Focus.CHARACTERS);
        Entity player = place(level, terrain, StarterPalette.CHARACTER_FOLDER + "/Player.prefab", PLAYER_AT.x, PLAYER_AT.z);
        Entity spawn = place(level, terrain, StarterPalette.CHARACTER_FOLDER + "/Spawn Point.prefab", SPAWN_AT.x, SPAWN_AT.z);
        assertNotNull(player, editor.getStatusMessage());
        assertNotNull(spawn, editor.getStatusMessage());
        pass(4, "Place the player and a spawn point from Characters", "Player (collision, movement, camera from the profile) and Spawn "
            + "Point placed on the sculpted ground");

        // 5. Action RPG + 3D, Play: the player starts at the spawn point and walks on the sculpted ground
        assertTrue(editor.setGameGenre(GameGenres.ACTION_RPG));
        assertEquals(GameMode.MODE_3D, editor.getActiveScene().getGameMode());
        editor.setSelectedEntity(tree);
        String authored = editor.getSerializer().toJson(scene);
        int undoSteps = editor.getCommandHistory().getUndoCount();
        PlayModeController play = new PlayModeController(editor);
        editor.setPlayModeController(play);
        boolean[] forward = {false};
        play.getProfileStep().setInput(key -> forward[0] && key == Input.Keys.W);
        assertTrue(play.play(), editor.getStatusMessage());
        Entity playing = play.getRuntimeScene().findEntityById(player.getId());
        Vector3 start = playing.getTransform().getWorldPosition().cpy();
        assertEquals(SPAWN_AT.x, start.x, 0.5f, "starts at the spawn point");
        assertEquals(SPAWN_AT.z, start.z, 0.5f, "starts at the spawn point");
        for (int i = 0; i < 30; i++) {
            play.update(FRAME);
        }
        forward[0] = true;
        for (int i = 0; i < WALK_FRAMES; i++) {
            play.update(FRAME);
        }
        Vector3 walked = playing.getTransform().getWorldPosition();
        float distance = Vector3.dst(start.x, 0f, start.z, walked.x, 0f, walked.z);
        assertTrue(distance > 100f, "W walks the player: " + distance);
        assertTrue(play.getRuntimeScene().getPhysics3D().isCharacterGrounded(playing), "on the ground after walking");
        float groundHere = terrain.heightAt(walked.x, walked.z);
        assertTrue(walked.y > groundHere, "standing on the terrain, not fallen through: " + walked.y + " over " + groundHere);
        pass(5, "Action RPG + 3D, Play", "starts at the spawn point; W walks it " + Math.round(distance) + " units over the sculpted "
            + "terrain, grounded (real Jolt, editor Play)");

        // 6. Stop: the authoring scene, selection and undo history return unchanged
        play.stop();
        assertEquals(PlayModeState.STOPPED, play.getState());
        assertEquals(authored, editor.getSerializer().toJson(editor.getActiveScene()), "the scene is as it was");
        assertSame(tree, editor.getSelectedEntity(), "the selection is kept");
        assertEquals(undoSteps, editor.getCommandHistory().getUndoCount(), "the undo history is kept");
        pass(6, "Stop", "scene JSON identical, selection kept, " + undoSteps + " undo steps kept");

        // 7. Place a house (File > Import Asset, then into the World); Edit 3D Model opens 3D Modeling with its notice
        AssetImport.Result imported = AssetImport.importFile(root, new File(ASSETS, HOUSE_MODEL));
        CreateEntityCommand addHouse = new CreateEntityCommand(editor, "House", new Vector3(0f, terrain.heightAt(0f, -600f), -600f),
            List.<Component>of(new ModelComponent(imported.asset())));
        assertTrue(editor.executeCommand(addHouse));
        Entity house = addHouse.getCreatedEntity();
        editor.setSelectedEntity(house);
        CustomizeGateway.EditOffer first = CustomizeGateway.editSelected(house, editor.getSerializer().getRegistry()).get(0);
        assertEquals(CustomizeGateway.Kind.OPEN_MODELING, first.kind());
        assertTrue(navigator.editModel(house), editor.getStatusMessage());
        assertEquals(Workspace.MODELING_3D, navigator.current());
        ModelingHandOff.Notice notice = ModelingHandOff.noticeFor(house);
        assertEquals(ModelingHandOff.Capability.MESH_PLANNED, notice.capability());
        navigator.backToWorld();
        assertSame(house, editor.getSelectedEntity());
        pass(7, "Place a house; Edit 3D Model opens 3D Modeling in context", "`" + imported.asset() + "` imported and placed; "
            + "\"" + first.label() + "\" opens 3D Modeling with the notice \"" + notice.title() + "\"; Back to World keeps the selection");
        blocked(7, "A realistic house from the Buildings palette", "No Buildings palette entries yet (M2b.3); the house was imported "
            + "by hand");

        // 8. Modify the house's geometry and save a derived model: mesh editing does not exist yet
        assertTrue(!notice.meshEditing());
        blocked(8, "Extrude a wall, move a window, change a material; save a derived model", "No mesh editing (R7a: M7a.1 to M7a.5); "
            + "a glTF model has no material slot (M2.2 gap). The notice says so instead of offering a transform as a mesh edit");

        // 9. Save, close, reopen, Play, export; terrain, models, profile and gameplay survive
        assertTrue(files.save(), editor.getStatusMessage());
        String saved = Files.readString(sceneFile.toPath());
        EditorState reopened = newEditor();
        SceneFileController reopenedFiles = new SceneFileController(reopened, new NoDialogs(), root);
        assertTrue(reopenedFiles.openFile(sceneFile), reopened.getStatusMessage());
        Scene back = reopened.getActiveScene();
        back.setHeightFields(new TerrainHeightFields(new TerrainLibrary(AssetSource.directory(root))));
        back.setColliderGeometry(new ColliderGeometry(null));
        for (String name : List.of("Level Ground", "Oak Tree", "Boulder", "Player", "Spawn Point", "House", "Main Camera")) {
            assertNotNull(back.findEntityByName(name), name + " survives save and reopen");
        }
        assertEquals(GameGenres.ACTION_RPG, back.getGameGenre(), "the genre survives");
        assertEquals(GameMode.MODE_3D, back.getGameMode(), "the mode survives");
        assertEquals(saved, reopened.getSerializer().toJson(back), "the reopened scene saves to the same text");
        Terrain reread = new TerrainLibrary(AssetSource.directory(root)).get(terrainPath);
        assertArrayEquals(terrain.copyHeights(), reread.copyHeights(), "the sculpted terrain survives");
        assertEquals(Surface.GRAVEL, reread.dominantSurface(centre, centre), "the painted ground survives");
        PlayModeController replay = new PlayModeController(reopened);
        reopened.setPlayModeController(replay);
        replay.getProfileStep().setInput(key -> false);
        assertTrue(replay.play(), reopened.getStatusMessage());
        Entity replaying = replay.getRuntimeScene().findEntityByName("Player");
        assertEquals(SPAWN_AT.x, replaying.getTransform().getWorldPosition().x, 0.5f, "after reopening, Play starts at the spawn again");
        replay.stop();

        GameExporter.Result exported = GameExportCommand.run(new EditorProject(root), null, fakeRuntimeImage(temp.resolve("image")).toFile(),
            OUT.resolve("export").toFile());
        Path game = exported.folder().toPath().resolve("app").resolve(GameExporter.GAME_FOLDER);
        for (String file : List.of(made.startScene(), terrainPath, imported.asset())) {
            assertTrue(Files.isRegularFile(game.resolve(file)), "the export holds " + file);
        }
        List<String> runtimeErrors = playExported(game.toFile(), new File(game.toFile(), made.startScene()));
        assertEquals(List.of(), runtimeErrors, "the exported game's files play in the game runtime");
        pass(9, "Save, close, reopen, Play, export", "every entity, the genre, the mode, the terrain heights and paint survive; Play starts "
            + "at the spawn again; the export holds the scene, the .terrain and the house; its files play 2 s in the game runtime with 0 "
            + "errors");
        blocked(9, "Launch the exported game as a program", "The tests export with a stand-in runtime image (no packaged editor is "
            + "built); the export was played by the game runtime's session instead. A packaged run is on the human checklist");

        assertEquals(shipped, snapshot(ASSETS.toPath().resolve("samples")), "the shipped samples are unchanged");
        writeSteps();
        // Every BLOCKED step is one of these; a change here must be a deliberate one
        assertEquals(List.of("3: Realistic verified CC0 trees and boulders; scatter foliage",
                "7: A realistic house from the Buildings palette",
                "8: Extrude a wall, move a window, change a material; save a derived model",
                "9: Launch the exported game as a program"),
            steps.stream().filter(s -> !s.passed()).map(s -> s.number() + ": " + s.what()).toList());
    }

    private static EditorState newEditor() {
        EditorState editor = new EditorState();
        EditorApplication.registerComponentSerializers(editor.getSerializer().getRegistry());
        return editor;
    }

    private static Entity place(LevelSession level, Terrain terrain, String prefab, float x, float z) {
        level.setObjectPath(prefab);
        return level.placeObject(x, terrain.heightAt(x, z), z);
    }

    private static void stroke(ViewportInteraction interaction) {
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, 0);
        interaction.touchDragged(WIDTH / 2 + 10f, HEIGHT / 2, WIDTH, HEIGHT);
        interaction.touchUp(Input.Buttons.LEFT);
    }

    /** Plays the exported scene for two seconds in the game runtime's session, as the exported game does. */
    private static List<String> playExported(File gameRoot, File scene) throws IOException {
        List<String> errors = new ArrayList<>();
        GameSession.Log log = new GameSession.Log() {
            @Override
            public void info(String message) { }

            @Override
            public void error(String message) {
                errors.add(message);
            }
        };
        Scene loaded = GameSession.loadScene(scene);
        loaded.setColliderGeometry(new ColliderGeometry(null));
        loaded.setHeightFields(new TerrainHeightFields(new TerrainLibrary(AssetSource.directory(gameRoot))));
        try (GameScripts scripts = new GameScripts(new File(gameRoot, "no-scripts"), OwnerJourneyRegressionTest.class.getClassLoader())) {
            GameSession session = new GameSession(gameRoot, loaded, scripts, log);
            try {
                errors.addAll(session.start());
                for (int i = 0; i < WALK_FRAMES; i++) {
                    session.update(FRAME);
                }
                errors.addAll(session.getRuntimeErrors());
            } finally {
                session.dispose();
            }
        }
        return errors;
    }

    private void writeSteps() throws IOException {
        StringBuilder text = new StringBuilder("# Owner journey regression: step results\n\n"
            + "Written by `OwnerJourneyRegressionTest` (roadmap M3c.5). PASS means checked by the test; nothing here was seen or "
            + "clicked by a person.\n\n| Step | What | Result | Evidence or reason |\n|---|---|---|---|\n");
        for (Step step : steps) {
            text.append("| ").append(step.number()).append(" | ").append(step.what()).append(" | ")
                .append(step.passed() ? "PASS" : "BLOCKED").append(" | ").append(step.evidence()).append(" |\n");
        }
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("journey-steps.md"), text.toString());
    }

    /** A packaged editor's layout with stand-in files, as in {@code TemplateGalleryProjectsTest}. */
    private static Path fakeRuntimeImage(Path root) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(GameExporter.EDITOR_LAUNCHER_NAME + ".exe", "editor exe");
        files.put(GameExporter.LAUNCHER_NAME + ".exe", "game exe");
        files.put("runtime/bin/java.dll", "jvm");
        files.put("app/engine-desktop-1.0.0.jar", "engine");
        files.put("app/" + GameExporter.LAUNCHER_NAME + ".cfg", "game cfg");
        files.put("app/" + GameExporter.EDITOR_LAUNCHER_NAME + ".cfg", "editor cfg");
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = root.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
        return root;
    }

    private static void clean(Path folder) throws IOException {
        if (Files.exists(folder)) {
            try (Stream<Path> walk = Files.walk(folder)) {
                for (Path path : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
    }

    private static Map<String, Long> snapshot(Path folder) throws IOException {
        Map<String, Long> files = new java.util.TreeMap<>();
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                files.put(folder.relativize(file) + " size", Files.size(file));
                files.put(folder.relativize(file) + " time", Files.getLastModifiedTime(file).toMillis());
            }
        }
        return files;
    }

    /** The screen as far as navigation needs it: the World tab, sculpt mode for 3D Modeling. */
    private static final class RecordingView implements WorkspaceNavigator.View {
        private String tab = Workspace.TAB_WORLD;
        private boolean sculpt;

        @Override
        public String centerTab() {
            return tab;
        }

        @Override
        public void showCenterTab(String title) {
            tab = title;
        }

        @Override
        public boolean isSculptMode() {
            return sculpt;
        }

        @Override
        public boolean runAction(String actionId) {
            if (actionId.equals(WorkspaceNavigator.SCULPT_ENTER_ACTION)) {
                sculpt = true;
            } else if (actionId.equals(WorkspaceNavigator.SCULPT_EXIT_ACTION)) {
                sculpt = false;
            }
            return true;
        }

        @Override
        public void showContentBrowser() { }

        @Override
        public void frameSelection() { }
    }

    /** The view's ordinary selection and camera: never reached by the strokes and placements here. */
    private static final class NothingElse implements ViewportInteraction {
        @Override
        public boolean touchDown(float x, float y, float w, float h, int button, int modifiers) {
            throw new AssertionError("a stroke reached the ordinary interaction");
        }

        @Override
        public boolean touchDragged(float x, float y, float w, float h) {
            return false;
        }

        @Override
        public boolean touchUp(int button) {
            return false;
        }

        @Override
        public boolean scrolled(float amountY) {
            return false;
        }

        @Override
        public Vector3 worldPointAt(float x, float y, float w, float h) {
            return new Vector3();
        }
    }

    /** Dialogs never shown: the journey saves to the file it opened and asks nothing. */
    private static final class NoDialogs implements EditorDialogService {
        @Override
        public Optional<File> chooseOpenFile(String title, File initialDirectory, String filterDescription, String extension) {
            throw new AssertionError("no open dialog in the journey: " + title);
        }

        @Override
        public Optional<File> chooseSaveFile(String title, File suggestedFile, String filterDescription, String extension) {
            throw new AssertionError("no save dialog in the journey: " + title);
        }

        @Override
        public UnsavedChangesChoice askUnsavedChanges(String documentName) {
            throw new AssertionError("no unsaved-changes question in the journey: " + documentName);
        }

        @Override
        public void showError(String title, String message) {
            throw new AssertionError(title + ": " + message);
        }
    }
}
