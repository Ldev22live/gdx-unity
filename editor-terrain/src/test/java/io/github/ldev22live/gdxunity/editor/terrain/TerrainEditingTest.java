package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainBrush;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainIO;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Terrain editing (TD-082): new files, brush strokes in the viewport as one undoable step, and the file that follows. */
class TerrainEditingTest {
    private static final float WIDTH = 800f;
    private static final float HEIGHT = 600f;

    /** Remembers what it was asked, so a test can tell what was passed on. */
    private static final class RecordingInteraction implements ViewportInteraction {
        final List<String> calls = new ArrayList<>();

        @Override
        public boolean touchDown(float x, float y, float w, float h, int button, int modifiers) {
            calls.add("touchDown " + button);
            return true;
        }

        @Override
        public boolean touchDragged(float x, float y, float w, float h) {
            calls.add("touchDragged");
            return true;
        }

        @Override
        public boolean touchUp(int button) {
            calls.add("touchUp " + button);
            return true;
        }

        @Override
        public boolean scrolled(float amountY) {
            calls.add("scrolled");
            return true;
        }

        @Override
        public Vector3 worldPointAt(float x, float y, float w, float h) {
            calls.add("worldPointAt");
            return new Vector3();
        }
    }

    @TempDir
    Path project;

    private TerrainLibrary library;
    private TerrainSession session;
    private EditorState editorState;
    private Entity ground;
    private Terrain terrain;
    private String path;
    private PerspectiveCamera3D camera;
    private RecordingInteraction normal;
    private TerrainViewportInteraction interaction;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        library = new TerrainLibrary(AssetSource.directory(project.toFile()));
        session = new TerrainSession(library);
        path = TerrainAssets.createNew(project.toFile(), library, "Valley", true);
        terrain = library.get(path);
        editorState = new EditorState(new SceneSerializer());
        Scene scene = new Scene("Level");
        editorState.setActiveScene(scene);
        ground = scene.createEntity("Ground");
        ground.addComponent(new TerrainComponent(path));
        editorState.setSelectedEntity(ground);
        camera = new PerspectiveCamera3D(60f, WIDTH, HEIGHT, 1f, 20000f);
        lookAtOriginFrom(0f, 800f, 500f);
        normal = new RecordingInteraction();
        interaction = new TerrainViewportInteraction(editorState, session, camera, normal);
        session.setPaintMode(true);
        session.getBrush().setRadius(150f);
        session.getBrush().setStrength(1f);
    }

    private void lookAtOriginFrom(float x, float y, float z) {
        camera.setPosition(x, y, z);
        camera.setDirection(new Vector3(-x, -y, -z).nor());
        camera.update();
    }

    private void stroke(int modifiers) {
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, modifiers);
        interaction.touchDragged(WIDTH / 2 + 20f, HEIGHT / 2, WIDTH, HEIGHT);
        interaction.touchUp(Input.Buttons.LEFT);
    }

    @Test
    void shouldCreateTerrainsUnderUniqueNamesInTheTerrainsFolder() {
        String second = TerrainAssets.createNew(project.toFile(), library, "Valley", false);
        String blank = TerrainAssets.createNew(project.toFile(), library, "  ", true);
        String odd = TerrainAssets.createNew(project.toFile(), library, "a/b:c", true);

        assertEquals("terrains/Valley.terrain", path);
        assertEquals("terrains/Valley 2.terrain", second);
        assertEquals("terrains/Terrain.terrain", blank);
        assertEquals("terrains/a_b_c.terrain", odd);
        assertTrue(Files.isRegularFile(project.resolve(second)));
        assertEquals(0f, terrain.heightRange()[1] - terrain.heightRange()[0], "flat ground starts flat");
        float[] hills = library.get(second).heightRange();
        assertTrue(hills[1] - hills[0] > 50f, "hills have relief");
        assertEquals("Valley 2", library.get(second).getName());
        assertTrue(TerrainAssets.isTerrainPath(" a/B.TERRAIN "));
        assertFalse(TerrainAssets.isTerrainPath("a/b.material"));
        assertFalse(TerrainAssets.isTerrainPath(null));
    }

    @Test
    void shouldPaintTheSelectedTerrainAndRecordOneUndoStepThatTheFileFollows() throws IOException {
        float[] before = terrain.copyHeights();

        stroke(0);

        assertTrue(normal.calls.isEmpty(), "a stroke is not passed on to the normal interaction: " + normal.calls);
        assertTrue(terrain.heightAt(0f, 0f) > 1f, "the raise brush lifted the ground under the cursor: " + terrain.heightAt(0f, 0f));
        assertTrue(editorState.getCommandHistory().canUndo());
        float[] painted = terrain.copyHeights();
        assertArrayEquals(painted, TerrainIO.fromJson(Files.readString(project.resolve(path))).copyHeights(), "the file follows the stroke");

        assertTrue(editorState.getCommandHistory().undo());
        assertArrayEquals(before, terrain.copyHeights(), "one undo takes back the whole stroke");
        assertArrayEquals(before, TerrainIO.fromJson(Files.readString(project.resolve(path))).copyHeights(), "and the file");
        assertFalse(editorState.getCommandHistory().canUndo(), "it was a single step");
        assertTrue(editorState.getCommandHistory().redo());
        assertArrayEquals(painted, terrain.copyHeights());
        assertSame(terrain, library.get(path), "still the one shared terrain");
    }

    @Test
    void shouldInvertTheBrushWithControl() {
        stroke(EditorKeyboardDispatcher.MOD_CTRL);

        assertTrue(terrain.heightAt(0f, 0f) < -1f, "ctrl turns raise into lower: " + terrain.heightAt(0f, 0f));
    }

    @Test
    void shouldPassEverythingElseToTheNormalInteraction() {
        session.setPaintMode(false);
        stroke(0);
        assertEquals(List.of("touchDown 0", "touchDragged", "touchUp 0"), normal.calls, "not in paint mode");
        assertFalse(editorState.getCommandHistory().canUndo());

        normal.calls.clear();
        session.setPaintMode(true);
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.RIGHT, 0);
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, EditorKeyboardDispatcher.MOD_ALT);
        interaction.scrolled(1f);
        assertEquals(List.of("touchDown 1", "touchDown 0", "scrolled"), normal.calls, "right button, alt-drag orbit and scrolling");

        normal.calls.clear();
        lookAtOriginFrom(0f, 800f, 500f);
        camera.setDirection(new Vector3(0f, 1f, 0f));
        camera.update();
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, 0);
        assertEquals(List.of("touchDown 0"), normal.calls, "a click that misses the terrain");
        assertFalse(interaction.isStroking());
    }

    @Test
    void shouldNotPaintWhenTheSelectedEntityHasNoTerrainOrItsFileIsMissing() {
        ground.getComponent(TerrainComponent.class).setTerrainPath("terrains/none.terrain");
        stroke(0);
        assertEquals("touchDown 0", normal.calls.get(0));
        assertNull(session.getTerrainOf(ground));
        assertNull(session.getTerrainOf(null));
        assertNull(session.getTerrainOf(editorState.getActiveScene().createEntity("Empty")));
        assertFalse(editorState.getCommandHistory().canUndo());
    }

    @Test
    void shouldFindTheGroundOfAMovedAndScaledTerrain() {
        ground.getTransform().setLocalPosition(1000f, 0f, 0f);
        ground.getTransform().setLocalScale(2f, 2f, 2f);
        lookAtOriginFrom(1000f, 800f, 500f);
        camera.setDirection(new Vector3(0f, -800f, -500f).nor());
        camera.update();

        stroke(0);

        assertTrue(normal.calls.isEmpty());
        assertTrue(terrain.heightAt(0f, 0f) > 0f, "the centre of the terrain's own space was painted");
        assertEquals(0f, terrain.heightAt(2000f, 0f), "far from the brush it is flat");
    }

    @Test
    void shouldPaintWithTheBrushOfTheSession() {
        TerrainBrush brush = session.getBrush();
        brush.setType(TerrainBrush.Type.LOWER);

        stroke(0);

        assertTrue(terrain.heightAt(0f, 0f) < -1f, "the lower brush was used: " + terrain.heightAt(0f, 0f));
        assertTrue(editorState.getCommandHistory().getUndoDescription().contains("Lower"));
    }
}
