package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.editor.terrain.LevelViewportInteraction;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainSession;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainViewportInteraction;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.TerrainCollider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The five landscape samples migrated to a terrain (roadmap M3.10, TD-098; tools/make_migrated_landscapes.py). Every object of the
 * original stands on the migrated ground where it stood on the baked one; the hero walks every ground in Play on real Jolt; and the
 * chart's journey runs on a project made from Landscape Valley (Terrain): reshape the valley, repaint a slope, scatter pines, save,
 * reopen, play, and the character walks on it.
 */
class MigratedLandscapesTest {
    /** Tests run in the module folder; the shipped assets are one level up. They are only read here. */
    private static final File ASSETS = new File("../assets");
    private static final List<String> MIGRATED = List.of("landscape-terrain-3d.json", "landscape-desert-terrain-3d.json",
        "landscape-alpine-terrain-3d.json", "landscape-island-terrain-3d.json", "landscape-autumn-terrain-3d.json");
    /** Entities of the originals that do not stand on the ground: water surfaces at their own level, and the mountain backdrop. */
    private static final Set<String> NOT_ON_GROUND = Set.of("Lake", "Ice", "Pond", "Oasis", "Ocean");
    /**
     * How far an object's foot may be from the migrated ground: the generators sank objects into the ground by up to 0.3 m times their
     * scale (mesas, scaled up to 1.6: 48 units), and the grid (0.47 m apart) smooths the finest bumps of the height function a little.
     */
    private static final float FOOT_TOLERANCE = 50f;
    private static final float FRAME = 1f / 60f;
    private static final int SETTLE_FRAMES = 30;
    private static final int WALK_FRAMES = 120;
    /** The least distance the hero must cover in two seconds of walking (the Action RPG walks 400 units a second). */
    private static final float WALKED_AT_LEAST = 300f;
    /** The panel the strokes are made in, and the camera looking down at the valley's middle. */
    private static final float WIDTH = 800f;
    private static final float HEIGHT = 600f;

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

    /** A shipped sample in an editor, read only, with the sources the editor's renderer would give it. */
    private static EditorState open(String sample) throws IOException {
        EditorState editor = newEditor();
        Scene scene = editor.getSerializer().fromJson(Files.readString(new File(ASSETS, "samples/" + sample).toPath()));
        scene.setHeightFields(new TerrainHeightFields(new TerrainLibrary(AssetSource.directory(ASSETS))));
        scene.setColliderGeometry(new ColliderGeometry(null));
        editor.setActiveScene(scene);
        return editor;
    }

    private static Terrain groundOf(Scene scene, File root) {
        Entity ground = scene.findEntityByName("Terrain");
        assertNotNull(ground, scene.getName() + " has its Terrain entity");
        assertNotNull(ground.getComponent(TerrainCollider3DComponent.class), "the ground collides");
        return new TerrainLibrary(AssetSource.directory(root)).get(ground.getComponent(TerrainComponent.class).getTerrainPath());
    }

    /** Plays the scene as its genre and holds W for two seconds; returns how far the player went across the ground, then stops. */
    private static float walk(EditorState editor, Terrain terrain) {
        Entity hero = null;
        for (Entity entity : editor.getActiveScene().getAllEntities()) {
            if (entity.getComponent(PlayerComponent.class) != null) {
                hero = entity;
            }
        }
        assertNotNull(hero, "the scene has a player");
        PlayModeController play = new PlayModeController(editor);
        editor.setPlayModeController(play);
        boolean[] forward = {false};
        play.getProfileStep().setInput(key -> forward[0] && key == Input.Keys.W);
        assertTrue(play.play(), editor.getStatusMessage());
        try {
            Entity playing = play.getRuntimeScene().findEntityById(hero.getId());
            for (int i = 0; i < SETTLE_FRAMES; i++) {
                play.update(FRAME);
            }
            Vector3 start = playing.getTransform().getWorldPosition().cpy();
            forward[0] = true;
            for (int i = 0; i < WALK_FRAMES; i++) {
                play.update(FRAME);
            }
            // Let go and come to rest: a dune face steeper than the 45 degree limit slides the hero down, briefly off the ground
            forward[0] = false;
            for (int i = 0; i < SETTLE_FRAMES * 2; i++) {
                play.update(FRAME);
            }
            Vector3 end = playing.getTransform().getWorldPosition();
            String where = editor.getActiveScene().getName() + ": from " + start + " to " + end;
            assertTrue(play.getRuntimeScene().getPhysics3D().isCharacterGrounded(playing), "on the ground after walking and stopping, "
                + where);
            assertTrue(end.y > terrain.heightAt(end.x, end.z), "standing on the terrain, not fallen through, " + where);
            return Vector3.dst(start.x, 0f, start.z, end.x, 0f, end.z);
        } finally {
            play.stop();
        }
    }

    @Test
    void shouldStandEveryKeptObjectOnTheMigratedGroundWhereTheBakedGroundWas() throws IOException {
        for (String sample : MIGRATED) {
            Scene scene = open(sample).getActiveScene();
            Terrain terrain = groundOf(scene, ASSETS);
            assertEquals(GameGenres.ACTION_RPG, scene.getGameGenre(), sample);
            int checked = 0;
            for (Entity entity : scene.getAllEntities()) {
                if (entity.getComponent(ModelComponent.class) == null || NOT_ON_GROUND.contains(entity.getName())
                    || entity.getName().startsWith("Mountain")) {
                    continue;
                }
                Vector3 at = entity.getTransform().getWorldPosition();
                float ground = terrain.heightAt(at.x, at.z);
                assertTrue(Math.abs(at.y - ground) <= FOOT_TOLERANCE,
                    sample + ": " + entity.getName() + " stands at " + at.y + ", the ground there is " + ground);
                checked++;
            }
            assertTrue(checked >= 30, sample + ": its trees, rocks and buildings were checked: " + checked);
            scene.dispose();
        }
    }

    @Test
    void shouldWalkTheHeroOverEveryMigratedGroundInPlay() throws IOException {
        for (String sample : MIGRATED) {
            EditorState editor = open(sample);
            float walked = walk(editor, groundOf(editor.getActiveScene(), ASSETS));
            assertTrue(walked >= WALKED_AT_LEAST, sample + ": the hero walked only " + walked);
        }
    }

    @Test
    void shouldReshapeRepaintScatterSaveReopenAndWalkTheMigratedLandscapeValley() throws Exception {
        // A project made from the gallery's Landscape Valley (Terrain), opened in World
        TemplateGallery.Choice valley = TemplateGallery.samples(ASSETS).stream()
            .filter(c -> c.scene().equals("samples/landscape-terrain-3d.json")).findFirst().orElseThrow();
        assertEquals(GameMode.MODE_3D, valley.mode());
        EditorState editor = newEditor();
        TemplateProject.Result made = TemplateGallery.create(valley, ASSETS, temp.toFile(), "Valley", editor.getSerializer());
        File root = made.folder();
        File sceneFile = new File(root, made.startScene());
        SceneFileController files = new SceneFileController(editor, new NoDialogs(), root);
        assertTrue(files.openFile(sceneFile), editor.getStatusMessage());
        Scene scene = editor.getActiveScene();
        TerrainLibrary terrains = new TerrainLibrary(AssetSource.directory(root));
        scene.setHeightFields(new TerrainHeightFields(terrains));
        scene.setColliderGeometry(new ColliderGeometry(null));
        editor.setPrefabService(new PrefabService(editor, root));
        TerrainSession terrainSession = new TerrainSession(terrains);
        LevelSession level = new LevelSession(editor, terrainSession, root);
        level.setActive(true);
        Terrain terrain = level.findTerrain();
        assertNotNull(terrain, "the migrated ground is the level's ground");
        String terrainPath = scene.findEntityByName("Terrain").getComponent(TerrainComponent.class).getTerrainPath();

        PerspectiveCamera3D camera = new PerspectiveCamera3D(60f, WIDTH, HEIGHT, 1f, 20000f);
        camera.setPosition(0f, 1500f, 900f);
        camera.setDirection(new Vector3(0f, -1500f, -900f).nor());
        camera.update();
        ViewportInteraction strokes = new LevelViewportInteraction(level, camera,
            new TerrainViewportInteraction(editor, terrainSession, camera, new NothingElse()));
        int centre = terrain.getResolutionX() / 2;

        // Reshape the valley: deepen its middle
        level.setFocus(LevelSession.Focus.TERRAIN);
        level.setTool(LevelSession.Tool.LOWER);
        terrainSession.getBrush().setRadius(500f);
        terrainSession.getBrush().setStrength(1f);
        float before = terrain.getHeight(centre, centre);
        for (int i = 0; i < 4; i++) {
            stroke(strokes);
        }
        float deepened = terrain.getHeight(centre, centre);
        assertTrue(deepened < before - 20f, "the valley was deepened: " + before + " to " + deepened);

        // Repaint the slope with rock, and undo and redo it
        level.setTool(LevelSession.Tool.PAINT);
        level.setPaintSurface(Surface.ROCK);
        Surface painted = terrain.dominantSurface(centre, centre);
        stroke(strokes);
        assertEquals(Surface.ROCK, terrain.dominantSurface(centre, centre), "the slope is painted rock");
        assertTrue(editor.undo());
        assertEquals(painted, terrain.dominantSurface(centre, centre), "undo takes the paint off");
        assertTrue(editor.redo());
        assertEquals(Surface.ROCK, terrain.dominantSurface(centre, centre), "redo puts it back");

        // Scatter pines from Objects, one undo step
        level.setFocus(LevelSession.Focus.OBJECTS);
        level.setTool(LevelSession.Tool.SCATTER);
        level.setObjectPath(StarterPalette.LEVEL_FOLDER + "/Pine Tree.prefab");
        terrainSession.getBrush().setRadius(700f);
        int undo = editor.getCommandHistory().getUndoCount();
        assertTrue(level.beginScatter(false), editor.getStatusMessage());
        for (float x = -1200f; x <= 1200f; x += 100f) {
            level.scatterAt(x, -600f);
        }
        int pines = level.endScatter();
        assertTrue(pines >= 10, "pines scattered over the valley: " + pines);
        assertEquals(undo + 1, editor.getCommandHistory().getUndoCount(), "the scatter stroke is one undo step");

        // Save, reopen: the reshaped and repainted ground and the pines are there
        assertTrue(files.save(), editor.getStatusMessage());
        EditorState reopened = newEditor();
        SceneFileController reopenedFiles = new SceneFileController(reopened, new NoDialogs(), root);
        assertTrue(reopenedFiles.openFile(sceneFile), reopened.getStatusMessage());
        Scene back = reopened.getActiveScene();
        back.setHeightFields(new TerrainHeightFields(new TerrainLibrary(AssetSource.directory(root))));
        back.setColliderGeometry(new ColliderGeometry(null));
        Terrain reread = new TerrainLibrary(AssetSource.directory(root)).get(terrainPath);
        assertArrayEquals(terrain.copyHeights(), reread.copyHeights(), "the reshaped valley survives");
        assertEquals(Surface.ROCK, reread.dominantSurface(centre, centre), "the repainted slope survives");
        int pinesBack = 0;
        for (Entity entity : back.getAllEntities()) {
            PrefabInstanceComponent link = PrefabService.linkOf(entity);
            if (link != null && link.isRoot() && link.getPrefabPath().endsWith("/Pine Tree.prefab")) {
                pinesBack++;
            }
        }
        assertEquals(pines, pinesBack, "the scattered pines survive");

        // Play: the character walks on the reshaped ground
        float walked = walk(reopened, reread);
        assertTrue(walked >= WALKED_AT_LEAST, "the hero walked only " + walked);
    }

    private static void stroke(ViewportInteraction interaction) {
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, 0);
        interaction.touchDragged(WIDTH / 2 + 10f, HEIGHT / 2, WIDTH, HEIGHT);
        interaction.touchUp(Input.Buttons.LEFT);
    }

    /** Nothing but strokes may reach the viewport here. */
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
