package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetImport;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetPlacement;
import io.github.ldev22live.gdxunity.editor.core.asset.SpriteReload;
import io.github.ldev22live.gdxunity.editor.core.command.AddComponentCommand;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.project.EditorProject;
import io.github.ldev22live.gdxunity.editor.core.project.GameExportCommand;
import io.github.ldev22live.gdxunity.editor.core.project.GameExporter;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.core.workspace.CustomizeGateway;
import io.github.ldev22live.gdxunity.editor.core.workspace.Workspace;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceNavigator;
import io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.NoDialogs;
import io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.RecordingView;
import io.github.ldev22live.gdxunity.editor.sprite.SpriteSession;
import io.github.ldev22live.gdxunity.editor.sprite.TilemapSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.desktop.GameSession;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileCameraRig;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileFix;
import io.github.ldev22live.gdxunity.engine.render.play.Run2DMover;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.FRAME;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.fakeRuntimeImage;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.newEditor;
import static io.github.ldev22live.gdxunity.editor.desktop.HouseReplaySupport.playExported;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M3c.7, Scenario B (2D Platformer + 2D), from a new project: tiles and a platform, a sprite character, Platformer + 2D
 * (TD-105) with Play, the camera, movement and collision, Stop, Customize to 2D Studio to change a frame, back to the World, Play
 * again, save, reopen and export. Each step is PASS (checked here) or BLOCKED with the missing capability; the table is written to
 * {@code build/scenario-b/scenario-steps.md}.
 */
class ScenarioB2DPlatformerTest {
    private static final File ASSETS = new File("../assets");
    private static final Path OUT = Path.of("build", "scenario-b");
    private static final String HERO = "sprites/hero.sprite";
    private static final String TILES = "sprites/tiles.sprite";
    /** The tile painted for the ground and the wall; marked solid. */
    private static final int GROUND_TILE = 1;
    private static final int COLS = TilemapComponent.DEFAULT_COLS;
    private static final int ROWS = TilemapComponent.DEFAULT_ROWS;
    private static final float TILE = TilemapComponent.DEFAULT_TILE_SIZE;
    /** The ground is the bottom row: its top is one tile above the map's origin at (0, 0). */
    private static final float GROUND_TOP = TILE;
    /** The wall is the last column, three tiles high on the ground: its left face. */
    private static final float WALL_LEFT = (COLS - 1) * TILE;
    /** A platform box: 128 × 32, its top 96 above the origin, from x 200 to 328. */
    private static final float PLATFORM_X = 264f;
    private static final float PLATFORM_Y = 80f;
    private static final float PLATFORM_WIDTH = 128f;
    private static final float PLATFORM_HEIGHT = 32f;
    private static final float PLATFORM_TOP = PLATFORM_Y + PLATFORM_HEIGHT / 2f;
    private static final float START_X = 64f;
    /** The hero's 16-pixel canvas: its centre stands half of that, plus Box2D's skins, above what it stands on. */
    private static final float STANDING = 8f + 2f;
    private static final float SLOP = 2f;
    private static final int PAINT = 0x00FF00FF;
    private static final int EDITED_FRAME = 2;

    private record Step(String number, String what, boolean passed, String evidence) { }

    @TempDir
    Path temp;

    private final List<Step> steps = new ArrayList<>();
    private final Set<Integer> held = new HashSet<>();
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
    void shouldBuildPlayEditAndExportA2DPlatformerFromANewProject() throws Exception {
        // 1. New project: Empty 2D
        EditorState editor = newEditor();
        TemplateGallery.Choice start = TemplateGallery.emptyStarts(ASSETS).stream().filter(c -> c.mode() == GameMode.MODE_2D)
            .findFirst().orElseThrow();
        TemplateProject.Result made = TemplateGallery.create(start, ASSETS, temp.resolve("project").toFile(), "Platformer",
            editor.getSerializer());
        File root = made.folder();
        File sceneFile = new File(root, made.startScene());
        SceneFileController files = new SceneFileController(editor, new NoDialogs(), root);
        assertTrue(files.openFile(sceneFile), editor.getStatusMessage());
        Scene scene = editor.getActiveScene();
        assertEquals(GameMode.MODE_2D, scene.getGameMode());
        WorkspaceNavigator navigator = new WorkspaceNavigator(editor, new RecordingView());
        pass("1", "New project (Empty 2D) opens in World", "`" + root.getName() + "`, Game Mode 2D");

        // 2. Tiles and a platform: import the shipped tile set, Add a Tile Map, paint the ground row and a wall, mark the tile solid
        assertEquals(TILES, AssetImport.importFile(root, new File(ASSETS, TILES)).asset());
        TilemapComponent tilemap = new TilemapComponent(new AssetReference(TILES));
        assertTrue(editor.executeCommand(new CreateEntityCommand(editor, "Level", new Vector3(), List.<Component>of(tilemap))));
        TilemapSession tiles = new TilemapSession(tilemap.getMap());
        tiles.setTile(GROUND_TILE);
        tiles.setTool(TilemapSession.Tool.RECT);
        tiles.pointerDown(0, ROWS - 1);
        tiles.pointerUp(COLS - 1, ROWS - 1);
        tiles.pointerDown(COLS - 1, ROWS - 4);
        tiles.pointerUp(COLS - 1, ROWS - 2);
        tiles.setSolid(GROUND_TILE, true);
        assertEquals(2, tilemap.getStaticBoxes().size(), "the ground row and the wall become two boxes in 2D physics");
        //    a platform: Create an entity with a shape, a static Rigid Body 2D and a Box Collider 2D of the same size
        assertTrue(editor.executeCommand(new CreateEntityCommand(editor, "Platform", new Vector3(PLATFORM_X, PLATFORM_Y, 0f),
            List.<Component>of(new ShapeRendererComponent(PLATFORM_WIDTH, PLATFORM_HEIGHT, Color.BROWN),
                new RigidBody2DComponent(RigidBody2DComponent.BodyType.STATIC), new BoxCollider2DComponent(PLATFORM_WIDTH, PLATFORM_HEIGHT)))));
        pass("2", "Tiles and platforms", "`" + TILES + "` imported; a Tile Map with the ground row and a 3-tile wall painted with the "
            + "rectangle tool, tile " + GROUND_TILE + " marked solid (2 static boxes); a 128 × 32 platform with a static Rigid Body 2D "
            + "and Box Collider 2D");

        // 3. A sprite character: import the hero and drag it into the World above the ground
        assertEquals(HERO, AssetImport.importFile(root, new File(ASSETS, HERO)).asset());
        assertEquals(AssetPlacement.Result.PLACED, AssetPlacement.place(editor, new AssetReference(HERO),
            new Vector3(START_X, GROUND_TOP + STANDING + 4f, 0f)));
        Entity hero = scene.findEntityByName("hero");
        assertNotNull(hero);
        SpriteAnimatorComponent heroSprite = hero.getComponent(SpriteAnimatorComponent.class);
        heroSprite.load(new FileHandle(new File(root, HERO))); // the World has drawn it once
        assertTrue(editor.executeCommand(new AddComponentCommand(editor, hero.getId(), new PlayerComponent())));
        pass("3", "A sprite character", "`" + HERO + "` imported and dragged in; Player added (Add Component > Game)");
        blocked("3", "A CC0 sprite character", "The hero is a ForgeRoom original (Apache-2.0); the repository has no CC0 2D character "
            + "yet (CC0 sets are M2b.5 and MP.2)");

        // 4. Platformer + 2D; Play names what is missing and offers each fix as a button
        assertTrue(editor.setGameGenre(GameGenres.PLATFORMER));
        PlayModeController play = new PlayModeController(editor);
        editor.setPlayModeController(play);
        play.getProfileStep().setInput(held::contains);
        List<String> fixes = new ArrayList<>();
        for (int attempt = 0; attempt < 4 && !play.play(); attempt++) {
            List<ProfileFix> offered = play.getProfileStep().getLastFixes();
            assertFalse(offered.isEmpty(), "Play refused without a fix: " + editor.getStatusMessage());
            fixes.add(offered.get(0).label());
            assertTrue(play.getProfileStep().applyFix(editor, offered.get(0)), editor.getStatusMessage());
        }
        assertTrue(editor.isPlayModeActive(), editor.getStatusMessage());
        stopPlay(play); // the authored scene with the fixes, recorded below
        String authored = editor.getSerializer().toJson(editor.getActiveScene());
        int undoSteps = editor.getCommandHistory().getUndoCount();
        assertEquals(16f, hero.getComponent(BoxCollider2DComponent.class).getWidth(), "the collider fits the 16-pixel hero");
        assertTrue(hero.getComponent(RigidBody2DComponent.class).isFixedRotation());
        pass("4", "Choose Platformer + 2D", "Play offered, one at a time: " + String.join("; ", fixes) + ". Each is one undo step");

        // 5. Play: the camera follows, the hero runs, jumps onto the platform, runs off, and stops at the wall
        assertTrue(play.play(), editor.getStatusMessage());
        assertTrue(editor.getStatusMessage().startsWith("Playing as Platformer / Side-scroller + 2D."), editor.getStatusMessage());
        Scene runtime = play.getRuntimeScene();
        Entity runner = runtime.findEntityById(hero.getId());
        assertNotNull(runner.getComponent(Run2DMover.class));
        Entity camera = cameraOf(runtime);
        assertInstanceOf(ProfileCameraRig.class, camera.getComponent(CameraRigComponent.class));
        run(play, 30);
        assertEquals(GROUND_TOP + STANDING, runner.getTransform().getWorldPosition().y, SLOP, "stands on the tile ground");
        held.add(Keys.D);
        held.add(Keys.SPACE);
        run(play, 40); // run and jump: over the platform's middle
        held.remove(Keys.D);
        run(play, 80); // fall straight down onto it
        assertEquals(PLATFORM_TOP + STANDING, runner.getTransform().getWorldPosition().y, SLOP, "landed on the platform");
        held.clear();
        held.add(Keys.D);
        run(play, 180); // off the platform's end, down to the ground, into the wall
        assertEquals(GROUND_TOP + STANDING, runner.getTransform().getWorldPosition().y, SLOP, "back on the ground");
        assertEquals(WALL_LEFT - 8f, runner.getTransform().getWorldPosition().x, 4f, "stopped by the tile wall");
        assertEquals(runner.getTransform().getWorldPosition().x, camera.getTransform().getWorldPosition().x, 60f, "the camera followed");
        assertEquals(1, runner.getComponent(Run2DMover.class).getJumps());
        pass("5", "Play: camera, movement and collision", "starts on the tile ground; D runs, Space jumps (once per press) and the "
            + "hero lands on the platform; it runs off, lands on the ground and stops at the tile wall (x within 4 of "
            + WALL_LEFT + " − 8); the camera follows and stays level (real Box2D, editor Play)");

        // 6. Stop: the World as it was
        held.clear();
        stopPlay(play);
        assertEquals(authored, editor.getSerializer().toJson(editor.getActiveScene()), "the scene is as it was");
        assertEquals(undoSteps, editor.getCommandHistory().getUndoCount(), "the undo history is kept");
        pass("6", "Stop", "scene JSON identical, " + undoSteps + " undo steps kept");

        // 7. Customize > Edit 2D Asset: 2D Studio; paint a walk frame, save; Back to World shows it
        editor.setSelectedEntity(hero);
        CustomizeGateway.EditOffer edit = CustomizeGateway.editSelected(hero, editor.getSerializer().getRegistry()).get(0);
        assertEquals("Edit 2D Asset hero.sprite", edit.label());
        assertEquals(Workspace.STUDIO_2D, edit.workspace());
        navigator.open(Workspace.destinations().stream().filter(d -> d.workspace() == Workspace.STUDIO_2D).findFirst().orElseThrow());
        SpriteSession studio = SpriteSession.open(new File(root, edit.assetPath()));
        int before = studio.getDocument().getPixel(0, EDITED_FRAME, 0, 0);
        assertNotEquals(PAINT, before);
        studio.setFrame(EDITED_FRAME);
        studio.paint(List.of(new int[] {0, 0}), PAINT, "Pencil");
        studio.saveAs(studio.getFile());
        navigator.backToWorld();
        assertEquals(1, SpriteReload.reloadUsers(editor.getActiveScene(), HERO));
        heroSprite.load(new FileHandle(new File(root, HERO)));
        assertEquals(PAINT, heroSprite.getDocument().getPixel(0, EDITED_FRAME, 0, 0));
        pass("7", "Customize > Edit 2D Asset; change a frame; Back to World", "\"" + edit.label() + "\" leads to 2D Studio; a pixel of "
            + "frame " + (EDITED_FRAME + 1) + " painted and saved; the World reloads and shows it");

        // 8. Play again: the edited sprite plays
        assertTrue(play.play(), editor.getStatusMessage());
        SpriteAnimatorComponent playing = play.getRuntimeScene().findEntityById(hero.getId()).getComponent(SpriteAnimatorComponent.class);
        if (playing.needsLoad()) {
            playing.load(new FileHandle(new File(root, HERO)));
        }
        assertEquals(PAINT, playing.getDocument().getPixel(0, EDITED_FRAME, 0, 0));
        run(play, 10);
        stopPlay(play);
        pass("8", "Play again", "the Play copy has the edited frame; Stop returns to the World");

        // 9. Save, reopen, Play, export; the export's files play in the game runtime as Platformer + 2D
        assertTrue(files.save(), editor.getStatusMessage());
        EditorState reopened = newEditor();
        assertTrue(new SceneFileController(reopened, new NoDialogs(), root).openFile(sceneFile), reopened.getStatusMessage());
        Scene back = reopened.getActiveScene();
        assertEquals(GameGenres.PLATFORMER, back.getGameGenre());
        assertEquals(GameMode.MODE_2D, back.getGameMode());
        for (String name : List.of("Level", "Platform", "hero")) {
            assertNotNull(back.findEntityByName(name), name + " survives save and reopen");
        }
        assertEquals(2, back.findEntityByName("Level").getComponent(TilemapComponent.class).getStaticBoxes().size(), "the tiles too");
        PlayModeController replay = new PlayModeController(reopened);
        reopened.setPlayModeController(replay);
        replay.getProfileStep().setInput(key -> false);
        assertTrue(replay.play(), reopened.getStatusMessage());
        replay.stop();
        GameExporter.Result exported = GameExportCommand.run(new EditorProject(root), null,
            fakeRuntimeImage(temp.resolve("image")).toFile(), temp.resolve("export").toFile());
        Path game = exported.folder().toPath().resolve("app").resolve(GameExporter.GAME_FOLDER);
        for (String file : List.of(made.startScene(), HERO, TILES)) {
            assertTrue(Files.isRegularFile(game.resolve(file)), "the export holds " + file);
        }
        Scene exportedScene = GameSession.loadScene(game.resolve(made.startScene()).toFile());
        assertEquals(List.of(), playExported(game.toFile(), exportedScene), "the exported game's files play in the game runtime");
        pass("9", "Save, reopen, Play, export", "genre, mode, entities and tiles survive; Play works after reopening; the export holds "
            + "the scene, the edited hero and the tile set, and its files play in the game runtime with 0 errors");
        blocked("9", "Launch the exported game as a program", "The tests export with a stand-in runtime image; the export's files ran "
            + "in the game runtime's session instead. A packaged run is on the human checklist");

        writeSteps();
        assertEquals(List.of("3: A CC0 sprite character", "9: Launch the exported game as a program"),
            steps.stream().filter(s -> !s.passed()).map(s -> s.number() + ": " + s.what()).toList());
    }

    private static Entity cameraOf(Scene scene) {
        for (Entity entity : scene.getAllEntities()) {
            if (entity.getComponent(CameraComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("no camera");
    }

    private static void stopPlay(PlayModeController play) {
        play.stop();
    }

    private static void run(PlayModeController play, int frames) {
        for (int i = 0; i < frames; i++) {
            play.update(FRAME);
        }
    }

    private void writeSteps() throws IOException {
        StringBuilder text = new StringBuilder("# Scenario B (2D Platformer + 2D): step results\n\n"
            + "Written by `ScenarioB2DPlatformerTest` (roadmap M3c.7). PASS means checked by the test; nothing here was seen or "
            + "pressed by a person.\n\n| Step | What | Result | Evidence or reason |\n|---|---|---|---|\n");
        for (Step step : steps) {
            text.append("| ").append(step.number()).append(" | ").append(step.what()).append(" | ")
                .append(step.passed() ? "PASS" : "BLOCKED").append(" | ").append(step.evidence()).append(" |\n");
        }
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("scenario-steps.md"), text.toString());
    }
}
