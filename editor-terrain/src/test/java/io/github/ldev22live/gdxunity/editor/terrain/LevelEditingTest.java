package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.viewport.OrthographicViewCamera;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewKind;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The level editor (TD-083): a level on a base ground, painted ground, placed and erased objects, all undoable. */
class LevelEditingTest {
    private static final float WIDTH = 800f;
    private static final float HEIGHT = 600f;
    private static final String OBJECT = LevelSession.OBJECT_FOLDER + "/Tree.prefab";
    private static final String PREFAB_TEXT = """
        {"schemaVersion": 1, "kind": "prefab", "name": "Tree", "rootId": "11111111-1111-1111-1111-111111111111",
         "entities": [{"id": "11111111-1111-1111-1111-111111111111", "parentId": null, "name": "Tree", "active": true,
           "transform": {"position": {"x": 0, "y": 0, "z": 0}, "rotation": {"x": 0, "y": 0, "z": 0, "w": 1},
                         "scale": {"x": 1, "y": 1, "z": 1}}, "components": []}]}
        """;

    /** Remembers what it was asked, so a test can tell what was passed on. */
    private static final class Recording implements ViewportInteraction {
        final List<String> calls = new ArrayList<>();

        @Override
        public boolean touchDown(float x, float y, float w, float h, int button, int modifiers) {
            calls.add("touchDown");
            return true;
        }

        @Override
        public boolean touchDragged(float x, float y, float w, float h) {
            calls.add("touchDragged");
            return true;
        }

        @Override
        public boolean touchUp(int button) {
            calls.add("touchUp");
            return true;
        }

        @Override
        public boolean scrolled(float amountY) {
            return true;
        }

        @Override
        public Vector3 worldPointAt(float x, float y, float w, float h) {
            return new Vector3();
        }
    }

    @TempDir
    Path project;

    private TerrainLibrary library;
    private TerrainSession terrainSession;
    private EditorState editorState;
    private LevelSession level;
    private Scene scene;
    private Recording normal;
    private LevelViewportInteraction interaction;
    private PerspectiveCamera3D camera;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() throws IOException {
        library = new TerrainLibrary(AssetSource.directory(project.toFile()));
        terrainSession = new TerrainSession(library);
        editorState = new EditorState(new SceneSerializer());
        scene = new Scene("Level");
        editorState.setActiveScene(scene);
        editorState.setPrefabService(new PrefabService(editorState, project.toFile()));
        level = new LevelSession(editorState, terrainSession, project.toFile());
        Files.createDirectories(project.resolve(LevelSession.OBJECT_FOLDER));
        Files.writeString(project.resolve(OBJECT), PREFAB_TEXT);
        camera = new PerspectiveCamera3D(60f, WIDTH, HEIGHT, 1f, 20000f);
        camera.setPosition(0f, 800f, 500f);
        camera.setDirection(new Vector3(0f, -800f, -500f).nor());
        camera.update();
        normal = new Recording();
        interaction = new LevelViewportInteraction(level, camera,
            new TerrainViewportInteraction(editorState, terrainSession, camera, normal));
        level.setActive(true);
    }

    private void click(int button, int modifiers) {
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, button, modifiers);
        interaction.touchDragged(WIDTH / 2 + 5f, HEIGHT / 2, WIDTH, HEIGHT);
        interaction.touchUp(button);
    }

    private List<Entity> placed() {
        List<Entity> result = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            if (PrefabService.linkOf(entity) != null) {
                result.add(entity);
            }
        }
        return result;
    }

    @Test
    void shouldStartALevelOnTheChosenBaseGroundAsOneUndoStepAndRefuseASecond() {
        Entity ground = level.createLevel(Surface.SAND);

        assertNotNull(ground);
        assertEquals("Level Ground", ground.getName());
        assertSame(ground, level.findGround());
        Terrain terrain = level.findTerrain();
        for (byte code : terrain.copySurfaces()) {
            assertEquals(Surface.SAND.code(), code, "every point starts on the base ground");
        }
        assertNull(level.createLevel(Surface.GRASS), "one level ground per scene");

        assertTrue(editorState.getCommandHistory().undo());
        assertNull(level.findGround(), "undo takes the ground out of the scene again");
    }

    @Test
    void shouldChangeTheBaseOfTheWholeGroundInOneUndoStepAndKeepTheFileInStep() throws IOException {
        level.createLevel(Surface.GRASS);
        Terrain terrain = level.findTerrain();
        String path = TerrainSession.componentOf(level.findGround()).getTerrainPath();

        assertTrue(level.setBaseSurface(Surface.SNOW));

        for (byte code : terrain.copySurfaces()) {
            assertEquals(Surface.SNOW.code(), code);
        }
        assertEquals(Surface.SNOW, TerrainIO.fromJson(Files.readString(project.resolve(path))).getSurface(3, 3), "the file follows");
        assertFalse(level.setBaseSurface(Surface.SNOW), "already snow everywhere: nothing to do");
        assertTrue(editorState.getCommandHistory().undo());
        assertEquals(Surface.GRASS, terrain.getSurface(3, 3));
        assertEquals(Surface.GRASS, TerrainIO.fromJson(Files.readString(project.resolve(path))).getSurface(3, 3), "and the file after undo");
    }

    @Test
    void shouldPaintAPatchOfAnotherGroundWithoutTouchingTheRestOrTheHeights() {
        level.createLevel(Surface.GRASS);
        Terrain terrain = level.findTerrain();
        float[] heights = terrain.copyHeights();
        level.setPaintSurface(Surface.GRAVEL);
        level.setTool(LevelSession.Tool.PAINT);
        terrainSession.getBrush().setRadius(200f);

        click(Input.Buttons.LEFT, 0);

        int centre = terrain.getResolutionX() / 2;
        assertEquals(Surface.GRAVEL, terrain.dominantSurface(centre, centre), "the patch is gravel under the cursor (a soft layer, TD-089)");
        assertEquals(Surface.GRASS, terrain.dominantSurface(0, 0), "far ground is still the base");
        assertTrue(java.util.Arrays.equals(heights, terrain.copyHeights()), "painting ground does not change the shape");
        assertTrue(normal.calls.isEmpty(), "the stroke was not passed on");

        assertTrue(editorState.getCommandHistory().undo());
        assertEquals(Surface.GRASS, terrain.dominantSurface(centre, centre), "one undo takes the patch back");
    }

    @Test
    void shouldPlaceTheChosenObjectWhereClickedKeepingTheSelectionAndUndoIt() {
        level.createLevel(Surface.GRASS);
        Entity ground = level.findGround();
        editorState.setSelectedEntity(ground);
        level.setObjectPath(OBJECT);
        level.setTool(LevelSession.Tool.PLACE);

        click(Input.Buttons.LEFT, 0);

        assertEquals(1, placed().size());
        Entity tree = placed().get(0);
        Vector3 at = tree.getTransform().getWorldPosition();
        assertEquals(0f, at.x, 3f, "where the click landed");
        assertEquals(0f, at.z, 3f);
        assertSame(ground, editorState.getSelectedEntity(), "placing does not take the selection from the ground");
        float scale = tree.getTransform().getLocalScale().x;
        assertTrue(Math.abs(scale - 1f) <= LevelSession.SCALE_JITTER + 1e-4f, "scaled a little: " + scale);
        assertTrue(normal.calls.isEmpty(), "the click, the drag and the release stay with the level editor: " + normal.calls);

        assertTrue(editorState.getCommandHistory().undo());
        assertTrue(placed().isEmpty(), "one undo removes the object");
        assertTrue(editorState.getCommandHistory().redo());
        assertEquals(1, placed().size());
    }

    /** UI M3: the Buildings drawer places a building square and at its own size, selected, as one undo step; Erase takes it away. */
    @Test
    void shouldPlaceABuildingSquareAtItsOwnSizeFromTheBuildingsDrawerAndEraseIt() throws IOException {
        String building = LevelSession.BUILDING_FOLDER + "/Cottage.prefab";
        Files.createDirectories(project.resolve(LevelSession.BUILDING_FOLDER));
        Files.writeString(project.resolve(building), PREFAB_TEXT.replace("\"Tree\"", "\"Cottage\""));
        level.createLevel(Surface.GRASS);
        level.setFocus(LevelSession.Focus.BUILDINGS);
        assertEquals(LevelSession.BUILDING_FOLDER, level.getObjectFolder());
        assertEquals(List.of(building), level.listObjects(), "the drawer lists the buildings, not the trees");
        assertEquals(LevelSession.Tool.PLACE, level.getTool());
        assertTrue(LevelSession.Focus.BUILDINGS.offers(LevelSession.Tool.ERASE));
        assertFalse(LevelSession.Focus.BUILDINGS.offers(LevelSession.Tool.RAISE), "no ground tools in the Buildings drawer");
        level.setObjectPath(building);
        int undoSteps = editorState.getCommandHistory().getUndoCount();

        click(Input.Buttons.LEFT, 0);

        assertEquals(1, placed().size());
        Entity cottage = placed().get(0);
        assertEquals(1f, cottage.getTransform().getLocalScale().x, 1e-6f, "at its own size");
        assertEquals(0f, cottage.getTransform().getLocalRotation().getYaw(), 1e-3f, "square, facing -Z");
        assertSame(cottage, editorState.getSelectedEntity(), "selected, to be moved or turned next");
        assertEquals(undoSteps + 1, editorState.getCommandHistory().getUndoCount(), "one undo step");

        level.setTool(LevelSession.Tool.ERASE);
        click(Input.Buttons.LEFT, 0);
        assertTrue(placed().isEmpty(), "Erase takes a building away too");
    }

    /** UI M2: the full-map view paints ground and places objects where clicked, through its own orthographic camera. */
    @Test
    void shouldPaintAndPlaceThroughTheIsometricViewWhereClickedEachAsOneUndoStep() {
        level.createLevel(Surface.GRASS);
        Terrain terrain = level.findTerrain();
        OrthographicViewCamera isometric = new OrthographicViewCamera(ViewKind.ISOMETRIC);
        isometric.resize((int) WIDTH, (int) HEIGHT);
        isometric.setZoom(2f);
        isometric.turn(1);
        isometric.setFocus(new Vector3(300f, 0f, -200f));
        interaction = new LevelViewportInteraction(level, isometric.getCamera(),
            new TerrainViewportInteraction(editorState, terrainSession, isometric.getCamera(), normal));
        int undoSteps = editorState.getCommandHistory().getUndoCount();

        level.setPaintSurface(Surface.GRAVEL);
        level.setTool(LevelSession.Tool.PAINT);
        terrainSession.getBrush().setRadius(120f);
        click(Input.Buttons.LEFT, 0);
        int column = Math.round((300f + terrain.getSizeX() / 2f) / terrain.getCellSize());
        int row = Math.round((-200f + terrain.getSizeZ() / 2f) / terrain.getCellSize());
        assertEquals(Surface.GRAVEL, terrain.dominantSurface(column, row), "painted under the cursor, at the view's focus");
        assertEquals(Surface.GRASS, terrain.dominantSurface(0, 0));
        assertEquals(undoSteps + 1, editorState.getCommandHistory().getUndoCount(), "one undo step");

        level.setObjectPath(OBJECT);
        level.setTool(LevelSession.Tool.PLACE);
        click(Input.Buttons.LEFT, 0);
        assertEquals(1, placed().size());
        Vector3 at = placed().get(0).getTransform().getWorldPosition();
        assertEquals(300f, at.x, 3f, "placed where the click landed on the ground: " + at);
        assertEquals(-200f, at.z, 3f);
        assertEquals(undoSteps + 2, editorState.getCommandHistory().getUndoCount());
        assertTrue(normal.calls.isEmpty(), "both stayed with the level editor: " + normal.calls);
    }

    @Test
    void shouldEraseOnlyTheObjectNearestTheClickAndLeaveFarOnesAlone() {
        level.createLevel(Surface.GRASS);
        level.setObjectPath(OBJECT);
        level.placeObject(0f, 0f, 0f);
        level.placeObject(1000f, 0f, 1000f);
        assertEquals(2, placed().size());

        level.setTool(LevelSession.Tool.ERASE);
        click(Input.Buttons.LEFT, 0);

        assertEquals(1, placed().size());
        assertEquals(1000f, placed().get(0).getTransform().getWorldPosition().x, 1f, "the far one stays");
        click(Input.Buttons.LEFT, 0);
        assertEquals(1, placed().size(), "nothing within reach this time");
    }

    @Test
    void shouldNotPlaceWithoutAnObjectOrWithAltHeldOrOnTheWrongButtonAndPassThoseOn() {
        level.createLevel(Surface.GRASS);
        level.setTool(LevelSession.Tool.PLACE);
        level.setObjectPath("");

        click(Input.Buttons.LEFT, 0);
        assertTrue(placed().isEmpty(), "no object chosen");

        level.setObjectPath(OBJECT);
        click(Input.Buttons.LEFT, io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher.MOD_ALT);
        click(Input.Buttons.RIGHT, 0);
        assertTrue(placed().isEmpty(), "Alt-drag orbits and the right button flies; neither places");
        assertFalse(normal.calls.isEmpty(), "they went on to the normal interaction");
    }

    @Test
    void shouldSendTheStrokeToTheLevelGroundWhateverIsSelected() {
        level.createLevel(Surface.GRASS);
        Terrain terrain = level.findTerrain();
        Entity other = scene.createEntity("Elsewhere");
        editorState.setSelectedEntity(other);
        level.setTool(LevelSession.Tool.RAISE);
        terrainSession.getBrush().setRadius(150f);
        terrainSession.getBrush().setStrength(1f);

        click(Input.Buttons.LEFT, 0);

        assertTrue(terrain.heightAt(0f, 0f) > 1f, "the raise brush lifted the level's ground: " + terrain.heightAt(0f, 0f));
        assertTrue(normal.calls.isEmpty());
    }

    @Test
    void shouldListTheObjectsOfTheLevelFolderByNameAndTurnTheBrushOffWhenTheEditorIsOff() throws IOException {
        Files.writeString(project.resolve(LevelSession.OBJECT_FOLDER + "/Boulder.prefab"), PREFAB_TEXT);
        Files.writeString(project.resolve(LevelSession.OBJECT_FOLDER + "/notes.txt"), "not a prefab");

        assertEquals(List.of(LevelSession.OBJECT_FOLDER + "/Boulder.prefab", OBJECT), level.listObjects());
        assertEquals("Boulder", LevelSession.objectLabel(LevelSession.OBJECT_FOLDER + "/Boulder.prefab"));

        level.setTool(LevelSession.Tool.RAISE);
        assertTrue(terrainSession.isPaintMode());
        level.setTool(LevelSession.Tool.PLACE);
        assertFalse(terrainSession.isPaintMode(), "placing is not a brush stroke");
        level.setTool(LevelSession.Tool.RAISE);
        level.setActive(false);
        assertFalse(terrainSession.isPaintMode(), "off means the mouse is the editor's again");
    }

    @Test
    void shouldOfferOnlyTheGroundToolsForTerrainAndOnlyPlaceAndEraseForObjects() {
        level.setTool(LevelSession.Tool.ERASE);

        level.setFocus(LevelSession.Focus.TERRAIN);
        assertEquals(LevelSession.Tool.RAISE, level.getTool(), "Erase is not a terrain tool, so the focus takes its first one");
        assertTrue(terrainSession.isPaintMode());
        assertThrows(IllegalArgumentException.class, () -> level.setTool(LevelSession.Tool.PLACE));
        level.setTool(LevelSession.Tool.PAINT);

        level.setFocus(LevelSession.Focus.OBJECTS);
        assertEquals(LevelSession.Tool.PLACE, level.getTool());
        assertFalse(terrainSession.isPaintMode(), "no brush strokes while placing objects");
        for (LevelSession.Tool tool : LevelSession.Tool.values()) {
            assertEquals(tool == LevelSession.Tool.PLACE || tool == LevelSession.Tool.ERASE, LevelSession.Focus.OBJECTS.offers(tool), tool.name());
            // The ground tools: the brushes and the Path tool (roadmap M3.7)
            assertEquals(tool.isBrush() || tool == LevelSession.Tool.PATH, LevelSession.Focus.TERRAIN.offers(tool), tool.name());
            assertTrue(LevelSession.Focus.ALL.offers(tool));
        }
        level.setTool(LevelSession.Tool.ERASE);
        level.setFocus(LevelSession.Focus.OBJECTS);
        assertEquals(LevelSession.Tool.ERASE, level.getTool(), "a tool the focus offers is kept");

        level.setActive(false);
        assertEquals(LevelSession.Focus.ALL, level.getFocus(), "turned off, the Level button's full palette comes back next time");
    }
}
