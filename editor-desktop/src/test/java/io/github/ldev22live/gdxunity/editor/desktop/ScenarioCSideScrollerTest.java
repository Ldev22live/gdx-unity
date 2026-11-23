package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetImport;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetPlacement;
import io.github.ldev22live.gdxunity.editor.core.asset.SpriteReload;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.component.ComponentCatalog;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.project.EditorProject;
import io.github.ldev22live.gdxunity.editor.core.project.GameExportCommand;
import io.github.ldev22live.gdxunity.editor.core.project.GameExporter;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.core.workspace.CustomizeGateway;
import io.github.ldev22live.gdxunity.editor.core.workspace.ModelingHandOff;
import io.github.ldev22live.gdxunity.editor.core.workspace.Workspace;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceNavigator;
import io.github.ldev22live.gdxunity.editor.sprite.SpriteSession;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetService;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.desktop.GameScripts;
import io.github.ldev22live.gdxunity.engine.desktop.GameSession;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.render.play.PlaneRunMover;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileCameraRig;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.BoxCollider3DComponent;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M3c.8, acceptance scenario C (Appendix E4): a 2.5D side-scroller built in the World from compatible 2D and 3D assets, played
 * as Platformer + 2.5D with the side camera and the depth constraint, a sprite edited in 2D Studio, back to the World, played again,
 * saved, reopened and exported. Run through the editor's own operations on a new Empty 2.5D project, on real Jolt.
 *
 * <p>Each step is recorded PASS or BLOCKED in {@code build/scenario-c/scenario-steps.md}; the BLOCKED list is asserted, so no step can
 * quietly change. The project is left in {@code build/scenario-c/project} for the human checklist in
 * docs/reports/m3c-8-scenario-c-side-scroller.md. What a person must see or press is not checked here.
 */
class ScenarioCSideScrollerTest {
    private static final File ASSETS = new File("../assets");
    private static final Path OUT = Path.of("build", "scenario-c");
    private static final float FRAME = 1f / 60f;
    /** A default character's centre above what it stands on: half of 80 plus the 30 radius. */
    private static final float STANDING = 70f;
    /** The capsule's radius: how far its centre stays from a wall it runs into. */
    private static final float RADIUS = 30f;
    /** Jolt lets a resting character sink or stop this close (about 2 cm, with margin). */
    private static final float SLOP = 5f;
    /** The level, as in the shipped Platformer + 2.5D sample: start 500 left of a 300-wide platform whose top is 120 high. */
    private static final float SPAWN_X = -200f;
    private static final float PLATFORM_X = 300f;
    private static final Vector3 PLATFORM_SIZE = new Vector3(300f, 40f, 400f);
    private static final float PLATFORM_CENTRE_Y = 100f;
    private static final float PLATFORM_TOP = PLATFORM_CENTRE_Y + PLATFORM_SIZE.y / 2f;
    private static final String COIN = "sprites/coin.sprite";
    /** The colour the person paints into the coin in 2D Studio. */
    private static final int PAINT = 0x00FF00FF;

    private record Step(String number, String what, boolean passed, String evidence) { }

    @TempDir
    Path temp;

    private final List<Step> steps = new ArrayList<>();
    private static Input previousInput;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
        previousInput = Gdx.input;
        // The exported game's session reads the keyboard; none is held
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

    private void pass(String number, String what, String evidence) {
        steps.add(new Step(number, what, true, evidence));
    }

    private void blocked(String number, String what, String reason) {
        steps.add(new Step(number, what, false, reason));
    }

    @Test
    void shouldBuildPlayEditAndExportA25DSideScrollerFromANewProject() throws Exception {
        clean(OUT);
        Map<String, Long> shipped = snapshot(ASSETS.toPath().resolve("samples"));

        // 1. New project: Empty 2.5D, opened in World
        EditorState editor = newEditor();
        TemplateGallery.Choice start = TemplateGallery.emptyStarts(ASSETS).stream().filter(c -> c.mode() == GameMode.MODE_2_5D)
            .findFirst().orElseThrow();
        TemplateProject.Result made = TemplateGallery.create(start, ASSETS, OUT.resolve("project").toFile(), "SideScroller",
            editor.getSerializer());
        File root = made.folder();
        File sceneFile = new File(root, made.startScene());
        SceneFileController files = new SceneFileController(editor, new NoDialogs(), root);
        assertTrue(files.openFile(sceneFile), editor.getStatusMessage());
        TerrainLibrary terrains = new TerrainLibrary(AssetSource.directory(root));
        editor.setAssetService(new AssetService(AssetSource.directory(root))); // as the editor does for its project
        Scene scene = editor.getActiveScene();
        scene.setHeightFields(new TerrainHeightFields(terrains));
        assertEquals(GameMode.MODE_2_5D, scene.getGameMode());
        WorkspaceNavigator navigator = new WorkspaceNavigator(editor, new RecordingView());
        assertEquals(Workspace.WORLD, navigator.current());
        pass("1", "New project (Empty 2.5D) opens in World", "`" + root.getName() + "`, Game Mode 2.5D, the starter palette included");

        // 2. Build with compatible 3D and 2D assets
        editor.setPrefabService(new PrefabService(editor, root));
        LevelSession level = new LevelSession(editor, new TerrainSession(terrains), root);
        level.setActive(true);
        assertNotNull(level.createLevel(Surface.GRASS), editor.getStatusMessage()); // World strip > Terrain > New Level
        Terrain ground = level.findTerrain();
        //    a platform: Create > Box, its size in Details, Add Component > Box Collider 3D (fitted to the box)
        EditorActionRegistry actions = new EditorActionRegistry();
        DefaultEditorActions.registerCreate3DActions(actions, editor, () -> new Vector3(PLATFORM_X, PLATFORM_CENTRE_Y, 0f));
        assertTrue(actions.execute(DefaultEditorActions.CREATE_BOX));
        Entity platform = scene.findEntityByName("Box");
        assertNotNull(platform);
        MeshPrimitiveComponent box = platform.getComponent(MeshPrimitiveComponent.class);
        assertTrue(editor.executeCommand(new ModifyComponentPropertyCommand<>(editor, platform.getId(), box, box.getSize(),
            new Vector3(PLATFORM_SIZE), box::setSize, "Size")));
        editor.setSelectedEntity(platform);
        ComponentCatalog catalog = ComponentCatalog.createDefault();
        ComponentCatalog.Entry collider = catalog.getEntries().stream().filter(e -> e.displayName().equals("Box Collider 3D"))
            .findFirst().orElseThrow();
        assertTrue(catalog.addTo(editor, platform, collider), editor.getStatusMessage());
        assertEquals(PLATFORM_SIZE, platform.getComponent(BoxCollider3DComponent.class).getSize(), "the collider fits the box");
        //    3D scenery behind the play plane: a pine from Objects
        level.setFocus(LevelSession.Focus.OBJECTS);
        level.setObjectPath(StarterPalette.LEVEL_FOLDER + "/Pine Tree.prefab");
        Entity pine = level.placeObject(600f, ground.heightAt(600f, -250f), -250f);
        assertNotNull(pine, editor.getStatusMessage());
        //    a 2D asset: File > Import Asset, then drag the sprite into the World above the platform
        AssetImport.Result imported = AssetImport.importFile(root, new File(ASSETS, COIN));
        assertEquals(COIN, imported.asset());
        assertEquals(AssetPlacement.Result.PLACED, AssetPlacement.place(editor, new AssetReference(COIN),
            new Vector3(PLATFORM_X, PLATFORM_TOP + 100f, 0f)));
        Entity coin = scene.findEntityByName("coin");
        assertNotNull(coin);
        assertEquals(AssetPlacement.DROPPED_SPRITE_SIZE, coin.getComponent(SpriteAnimatorComponent.class).getWidth(), 0.001f,
            "sized for the 2.5D world, not 16 units (fixed here)");
        //    the player and its spawn point, from Characters
        level.setFocus(LevelSession.Focus.CHARACTERS);
        level.setObjectPath(StarterPalette.CHARACTER_FOLDER + "/Player.prefab");
        Entity player = level.placeObject(-600f, 0f, 0f);
        level.setObjectPath(StarterPalette.CHARACTER_FOLDER + "/Spawn Point.prefab");
        Entity spawn = level.placeObject(SPAWN_X, 0f, 0f);
        assertNotNull(player, editor.getStatusMessage());
        assertNotNull(spawn, editor.getStatusMessage());
        pass("2", "Build the level from 3D and 2D assets", "New Level (flat grass), a 300 x 40 platform from Create > Box with a fitted "
            + "Box Collider 3D, a pine from Objects behind the play plane, `" + COIN + "` imported and dragged in as a sprite (100 units wide, sized for the world), the "
            + "Player and a Spawn Point from Characters");

        // 3. Platformer + 2.5D: Play with the side camera and the depth constraint; run, collide, jump onto the platform
        assertTrue(editor.setGameGenre(GameGenres.PLATFORMER));
        String authored = editor.getSerializer().toJson(scene);
        int undoSteps = editor.getCommandHistory().getUndoCount();
        Set<Integer> held = new HashSet<>();
        PlayModeController play = new PlayModeController(editor);
        editor.setPlayModeController(play);
        play.getProfileStep().setInput(held::contains);
        assertTrue(play.play(), editor.getStatusMessage());
        Scene runtime = play.getRuntimeScene();
        Entity runner = runtime.findEntityById(player.getId());
        assertNotNull(runner.getComponent(PlaneRunMover.class), "the profile runs the player along the plane");
        assertInstanceOf(ProfileCameraRig.class, runtime.findEntityByName("Main Camera").getComponent(CameraRigComponent.class),
            "the side camera");
        run(play, 30);
        assertEquals(SPAWN_X, runner.getTransform().getWorldPosition().x, SLOP, "starts at the spawn point");
        assertEquals(STANDING, runner.getTransform().getWorldPosition().y, SLOP, "stands on the level");
        held.add(Keys.D);
        run(play, 120);
        float wall = PLATFORM_X - PLATFORM_SIZE.x / 2f - RADIUS;
        assertEquals(wall, runner.getTransform().getWorldPosition().x, SLOP, "ran right and stopped against the platform's side");
        assertEquals(0f, runner.getTransform().getWorldPosition().z, 0.01f, "on the play plane");
        Vector3 camera = runtime.findEntityByName("Main Camera").getTransform().getWorldPosition();
        assertEquals(runner.getTransform().getWorldPosition().x, camera.x, 20f, "the side camera followed");
        assertEquals(GameGenres.SIDE_CAMERA_DISTANCE, camera.z, 1f, "in front of the play plane");
        held.add(Keys.SPACE);
        for (int frame = 0; frame < 120; frame++) {
            if (frame == 30) {
                held.remove(Keys.D);
            }
            run(play, 1);
        }
        assertEquals(PLATFORM_TOP + STANDING, runner.getTransform().getWorldPosition().y, SLOP, "jumped onto the platform");
        assertEquals(0f, runner.getTransform().getWorldPosition().z, 0.01f, "still on the play plane");
        pass("3", "Platformer + 2.5D, Play", "starts at the spawn point; the side camera follows in front of the plane; D runs into "
            + "the platform's side and stops; Space jumps onto it; depth stays 0 (real Jolt, editor Play)");

        // 4. Stop: the World as it was
        held.clear();
        play.stop();
        assertEquals(authored, editor.getSerializer().toJson(editor.getActiveScene()), "the scene is as it was");
        assertEquals(undoSteps, editor.getCommandHistory().getUndoCount(), "the undo history is kept");
        pass("4", "Stop", "scene JSON identical, " + undoSteps + " undo steps kept");

        // 5. Customize > Edit Sprite: 2D Studio; paint the coin, undo and redo, save; Back to World shows it
        SpriteAnimatorComponent worldCoin = coin.getComponent(SpriteAnimatorComponent.class);
        worldCoin.load(new FileHandle(new File(root, COIN))); // the World has drawn it once
        int before = worldCoin.getDocument().getPixel(0, 0, 0, 0);
        assertNotEquals(PAINT, before, "the corner is not already the new colour");
        editor.setSelectedEntity(coin);
        CustomizeGateway.EditOffer editSprite = CustomizeGateway.editSelected(coin, editor.getSerializer().getRegistry()).get(0);
        assertEquals(CustomizeGateway.Kind.OPEN_ASSET, editSprite.kind());
        assertEquals(Workspace.STUDIO_2D, editSprite.workspace());
        assertEquals(COIN, editSprite.assetPath());
        navigator.open(Workspace.destinations().stream().filter(d -> d.workspace() == Workspace.STUDIO_2D).findFirst().orElseThrow());
        assertEquals(Workspace.STUDIO_2D, navigator.current());
        SpriteSession studio = SpriteSession.open(new File(root, editSprite.assetPath()));
        studio.setColor(PAINT);
        SpriteSession.Stroke pencil = studio.beginPencil();
        pencil.add(0, 0);
        pencil.commit();
        assertEquals(PAINT, studio.getDocument().getPixel(0, 0, 0, 0));
        assertTrue(studio.getHistory().undo());
        assertEquals(before, studio.getDocument().getPixel(0, 0, 0, 0), "undo in 2D Studio");
        assertTrue(studio.getHistory().redo());
        studio.saveAs(studio.getFile());
        navigator.backToWorld();
        assertSame(coin, editor.getSelectedEntity());
        assertEquals(before, worldCoin.getDocument().getPixel(0, 0, 0, 0), "until the change is noticed, the World has the old look");
        assertEquals(1, SpriteReload.reloadUsers(editor.getActiveScene(), COIN), "what the editor does when the file changes");
        worldCoin.load(new FileHandle(new File(root, COIN))); // the World's next draw
        assertEquals(PAINT, worldCoin.getDocument().getPixel(0, 0, 0, 0), "the World shows the edited sprite");
        pass("5", "Customize > Edit Sprite in 2D Studio; Back to World", "\"" + editSprite.label() + "\" leads to 2D Studio; a pixel "
            + "painted, undone and redone, saved to `" + COIN + "`; back in the World the coin reloads and shows it (fixed here: it "
            + "kept the old look before)");
        //    a 3D model's geometry cannot be edited yet
        editor.setSelectedEntity(platform);
        assertTrue(!ModelingHandOff.noticeFor(platform).meshEditing());
        blocked("5", "Edit a 3D model's geometry (the platform or the pine)", "No mesh editing (R7a); Edit 3D Model opens 3D Modeling "
            + "with a notice and offers a sculpt copy instead (M3c.4)");

        // 6. Play again: the edited sprite and the level play
        assertTrue(play.play(), editor.getStatusMessage());
        SpriteAnimatorComponent playingCoin = play.getRuntimeScene().findEntityById(coin.getId())
            .getComponent(SpriteAnimatorComponent.class);
        assertTrue(playingCoin.needsLoad(), "the Play copy reads the sprite file afresh");
        playingCoin.load(new FileHandle(new File(root, COIN)));
        assertEquals(PAINT, playingCoin.getDocument().getPixel(0, 0, 0, 0), "Play shows the edited sprite");
        play.stop();
        pass("6", "Play again", "the Play copy loads the edited coin; Stop returns to the World");

        // 7. Save, reopen, Play, export; the export's files play in the game runtime as Platformer + 2.5D
        assertTrue(files.save(), editor.getStatusMessage());
        EditorState reopened = newEditor();
        assertTrue(new SceneFileController(reopened, new NoDialogs(), root).openFile(sceneFile), reopened.getStatusMessage());
        Scene back = reopened.getActiveScene();
        back.setHeightFields(new TerrainHeightFields(new TerrainLibrary(AssetSource.directory(root))));
        assertEquals(GameGenres.PLATFORMER, back.getGameGenre());
        assertEquals(GameMode.MODE_2_5D, back.getGameMode());
        for (String name : List.of("Level Ground", "Box", "Pine Tree", "coin", "Player", "Spawn Point")) {
            assertNotNull(back.findEntityByName(name), name + " survives save and reopen");
        }
        PlayModeController replay = new PlayModeController(reopened);
        reopened.setPlayModeController(replay);
        replay.getProfileStep().setInput(key -> false);
        assertTrue(replay.play(), reopened.getStatusMessage());
        replay.stop();
        GameExporter.Result exported = GameExportCommand.run(new EditorProject(root), null,
            fakeRuntimeImage(temp.resolve("image")).toFile(), OUT.resolve("export").toFile());
        Path game = exported.folder().toPath().resolve("app").resolve(GameExporter.GAME_FOLDER);
        for (String file : List.of(made.startScene(), COIN)) {
            assertTrue(Files.isRegularFile(game.resolve(file)), "the export holds " + file);
        }
        assertEquals(PAINT, spritePixel(game.resolve(COIN)), "the export holds the edited sprite");
        String profile = playExported(game.toFile(), new File(game.toFile(), made.startScene()));
        assertTrue(profile.startsWith("Playing as Platformer / Side-scroller + 2.5D."), profile);
        pass("7", "Save, reopen, Play, export", "genre, mode and every entity survive; Play works after reopening; the export holds the "
            + "scene and the edited sprite; its files play 2 s in the game runtime as Platformer + 2.5D with 0 errors");
        blocked("7", "Launch the exported game as a program", "The tests export with a stand-in runtime image; the export's files ran "
            + "in the game runtime's session instead. A packaged run is on the human checklist");
        blocked("2", "CC0 2D and 3D assets", "The level uses the project's primitive-built prefabs and a shipped original sprite; CC0 "
            + "sets are M2b.5 and MP.2");

        assertEquals(shipped, snapshot(ASSETS.toPath().resolve("samples")), "the shipped samples are unchanged");
        writeSteps();
        assertEquals(List.of("5: Edit a 3D model's geometry (the platform or the pine)", "7: Launch the exported game as a program",
                "2: CC0 2D and 3D assets"),
            steps.stream().filter(s -> !s.passed()).map(s -> s.number() + ": " + s.what()).toList());
    }

    private static void run(PlayModeController play, int frames) {
        for (int i = 0; i < frames; i++) {
            play.update(FRAME);
        }
    }

    private static int spritePixel(Path file) throws IOException {
        SpriteAnimatorComponent probe = new SpriteAnimatorComponent(new AssetReference(COIN));
        probe.load(new FileHandle(file.toFile()));
        return probe.getDocument().getPixel(0, 0, 0, 0);
    }

    private static EditorState newEditor() {
        EditorState editor = new EditorState();
        EditorApplication.registerComponentSerializers(editor.getSerializer().getRegistry());
        return editor;
    }

    /** Plays the exported scene for two seconds in the game runtime's session; returns the profile line it printed. */
    private static String playExported(File gameRoot, File scene) throws IOException {
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
        try (GameScripts scripts = new GameScripts(new File(gameRoot, "no-scripts"), ScenarioCSideScrollerTest.class.getClassLoader())) {
            GameSession session = new GameSession(gameRoot, loaded, scripts, log);
            try {
                errors.addAll(session.start());
                for (int i = 0; i < 120; i++) {
                    session.update(FRAME);
                }
                errors.addAll(session.getRuntimeErrors());
                assertEquals(List.of(), errors, "the exported game plays with no errors");
                return session.getProfileMessage();
            } finally {
                session.dispose();
            }
        }
    }

    private void writeSteps() throws IOException {
        StringBuilder text = new StringBuilder("# Scenario C (2.5D side-scroller): step results\n\n"
            + "Written by `ScenarioCSideScrollerTest` (roadmap M3c.8). PASS means checked by the test; nothing here was seen or "
            + "pressed by a person.\n\n| Step | What | Result | Evidence or reason |\n|---|---|---|---|\n");
        for (Step step : steps) {
            text.append("| ").append(step.number()).append(" | ").append(step.what()).append(" | ")
                .append(step.passed() ? "PASS" : "BLOCKED").append(" | ").append(step.evidence()).append(" |\n");
        }
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("scenario-steps.md"), text.toString());
    }

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
        Map<String, Long> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                files.put(folder.relativize(file) + " size", Files.size(file));
                files.put(folder.relativize(file) + " time", Files.getLastModifiedTime(file).toMillis());
            }
        }
        return files;
    }

    /** The screen as navigation needs it: centre tabs, and sculpt mode for 3D Modeling. */
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

    /** Dialogs never shown: the scenario saves to the file it opened and asks nothing. */
    private static final class NoDialogs implements EditorDialogService {
        @Override
        public Optional<File> chooseOpenFile(String title, File initialDirectory, String filterDescription, String extension) {
            throw new AssertionError("no open dialog in the scenario: " + title);
        }

        @Override
        public Optional<File> chooseSaveFile(String title, File suggestedFile, String filterDescription, String extension) {
            throw new AssertionError("no save dialog in the scenario: " + title);
        }

        @Override
        public UnsavedChangesChoice askUnsavedChanges(String documentName) {
            throw new AssertionError("no unsaved-changes question in the scenario: " + documentName);
        }

        @Override
        public void showError(String title, String message) {
            throw new AssertionError(title + ": " + message);
        }
    }
}
