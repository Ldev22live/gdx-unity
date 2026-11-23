package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolRegistry;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutionResult;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutor;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlan;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlanStep;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.CameraComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TerrainComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.CharacterController3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.TerrainCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.serialization.PlayerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.play.serialization.SpawnPointComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainIO;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M3.4 (TD-089) in the level editor: the Ground tool paints with soft edges, the Hole tool cuts and fills holes, the water
 * level is set, moved and removed, each as one undo step that the file follows; the assistant's {@code terrain_edit} does the same;
 * and in Play a character falls through a cut hole while one beside it stands (real Jolt, editor Play).
 */
class LayersHolesWaterEditingTest {
    private static final float WIDTH = 800f;
    private static final float HEIGHT = 600f;
    private static final float FRAME = 1f / 60f;

    @TempDir
    Path project;

    private TerrainLibrary library;
    private TerrainSession terrainSession;
    private EditorState editorState;
    private Scene scene;
    private LevelSession level;
    private ViewportInteraction interaction;
    private PlayModeController play;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() throws IOException {
        Path characters = Files.createDirectories(project.resolve(LevelSession.CHARACTER_FOLDER));
        Files.copy(Path.of("..", "assets", LevelSession.CHARACTER_FOLDER, "Player.prefab"), characters.resolve("Player.prefab"),
            StandardCopyOption.REPLACE_EXISTING);
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new CameraComponentSerializer());
        registry.register(new MeshPrimitiveComponentSerializer());
        registry.register(new TerrainComponentSerializer());
        registry.register(new TerrainCollider3DComponentSerializer());
        registry.register(new CharacterController3DComponentSerializer());
        registry.register(new PlayerComponentSerializer());
        registry.register(new SpawnPointComponentSerializer());
        registry.register(new PrefabInstanceComponentSerializer());
        library = new TerrainLibrary(AssetSource.directory(project.toFile()));
        terrainSession = new TerrainSession(library);
        editorState = new EditorState(new SceneSerializer(registry));
        scene = new Scene("Level");
        scene.setGameMode(GameMode.MODE_3D);
        scene.setHeightFields(new TerrainHeightFields(library));
        editorState.setActiveScene(scene);
        editorState.setPrefabService(new PrefabService(editorState, project.toFile()));
        level = new LevelSession(editorState, terrainSession, project.toFile());
        PerspectiveCamera3D camera = new PerspectiveCamera3D(60f, WIDTH, HEIGHT, 1f, 20000f);
        camera.setPosition(0f, 800f, 500f);
        camera.setDirection(new Vector3(0f, -800f, -500f).nor());
        camera.update();
        interaction = new LevelViewportInteraction(level, camera, new TerrainViewportInteraction(editorState, terrainSession, camera,
            new NothingElse()));
        level.setActive(true);
        level.createLevel(Surface.GRASS);
        play = new PlayModeController(editorState);
        play.getProfileStep().setInput(key -> false);
        editorState.setPlayModeController(play);
    }

    @AfterEach
    void tearDown() {
        play.stop();
    }

    private void click(int modifiers) {
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, modifiers);
        interaction.touchDragged(WIDTH / 2 + 5f, HEIGHT / 2, WIDTH, HEIGHT);
        interaction.touchUp(Input.Buttons.LEFT);
    }

    private Terrain onDisk() throws IOException {
        String path = level.findGround().getComponent(TerrainComponent.class).getTerrainPath();
        return TerrainIO.fromJson(Files.readString(project.resolve(path)));
    }

    @Test
    void shouldPaintASoftEdgedPatchWithTheGroundToolAsOneUndoStep() throws IOException {
        Terrain terrain = level.findTerrain();
        level.setTool(LevelSession.Tool.PAINT);
        level.setPaintSurface(Surface.ROCK);
        terrainSession.getBrush().setRadius(300f);
        int steps = editorState.getCommandHistory().getUndoCount();

        click(0);

        int centre = terrain.getResolutionX() / 2;
        assertEquals(Surface.ROCK, terrain.dominantSurface(centre, centre));
        boolean soft = false;
        for (int ix = centre; ix < terrain.getResolutionX(); ix++) {
            int weight = terrain.getLayerWeight(ix, centre, Surface.ROCK);
            soft |= weight > 0 && weight < Terrain.FULL_WEIGHT;
        }
        assertTrue(soft, "between the patch and the grass the ground is part rock (a soft edge)");
        assertEquals(steps + 1, editorState.getCommandHistory().getUndoCount(), "one undo step");
        assertArrayEquals(terrain.copyWeights(), onDisk().copyWeights(), "the file follows");
        assertTrue(editorState.undo());
        assertFalse(terrain.hasWeights());
        assertFalse(onDisk().hasWeights(), "and follows the undo");
    }

    @Test
    void shouldCutAHoleWithTheHoleToolAndFillItWithCtrlEachOneUndoStep() throws IOException {
        Terrain terrain = level.findTerrain();
        level.setTool(LevelSession.Tool.HOLE);
        terrainSession.getBrush().setRadius(150f);

        click(0);

        assertTrue(terrain.hasHoles(), "a hole is cut where the view points");
        assertTrue(onDisk().hasHoles(), "the file follows");
        click(EditorKeyboardDispatcher.MOD_CTRL);
        assertFalse(terrain.hasHoles(), "Ctrl fills it");
        assertTrue(editorState.undo(), "undo the fill");
        assertTrue(terrain.hasHoles());
        assertTrue(editorState.undo(), "undo the cut");
        assertFalse(terrain.hasHoles());
        assertFalse(onDisk().hasHoles());
    }

    @Test
    void shouldAddMoveAndRemoveWaterEachAsOneUndoStepThatTheFileFollows() throws IOException {
        Terrain terrain = level.findTerrain();
        terrain.setHeight(0, 0, 400f); // some relief: water starts a quarter of the way up
        int steps = editorState.getCommandHistory().getUndoCount();

        assertTrue(level.addWater());
        assertEquals(100f, terrain.getWaterLevel(), 0.01f, "a quarter of the way from 0 to 400");
        assertFalse(level.addWater(), "Add does nothing when there is water");
        assertTrue(level.moveWater(1));
        assertEquals(100f + LevelSession.WATER_STEP, terrain.getWaterLevel(), 0.01f);
        assertEquals(terrain.getWaterLevel(), onDisk().getWaterLevel(), 0.01f, "the file follows");
        assertTrue(level.setWaterLevel(Float.NaN));
        assertFalse(terrain.hasWater());
        assertFalse(onDisk().hasWater());
        assertEquals(steps + 3, editorState.getCommandHistory().getUndoCount(), "add, up and remove: three undo steps");

        assertTrue(editorState.undo());
        assertEquals(100f + LevelSession.WATER_STEP, terrain.getWaterLevel(), 0.01f, "undo brings the water back");
        assertFalse(level.moveWater(0), "no change, no step");
    }

    @Test
    void shouldLetTheAssistantPaintCutAndFloodAsOneUndoStepPerPlan() {
        Terrain terrain = level.findTerrain();
        AgentToolRegistry tools = new AgentToolRegistry();
        tools.registerTool(new TerrainEditTool(level));
        AgentPlan plan = new AgentPlan("A rocky pond");
        plan.addStep(new AgentPlanStep("s1", TerrainEditTool.ID, params("action", "paint", "surface", "rock", "x", 0f, "z", 0f,
            "radius", 300f)));
        plan.addStep(new AgentPlanStep("s2", TerrainEditTool.ID, params("action", "hole", "x", 500f, "z", 500f, "radius", 100f)));
        plan.addStep(new AgentPlanStep("s3", TerrainEditTool.ID, params("action", "water", "level", -20f)));
        int steps = editorState.getCommandHistory().getUndoCount();

        AgentPlanExecutionResult result = new AgentPlanExecutor(tools).executePlan(plan, new AgentToolContext(editorState));

        assertTrue(result.isSuccess(), result.getMessage());
        int centre = terrain.getResolutionX() / 2;
        assertEquals(Surface.ROCK, terrain.dominantSurface(centre, centre));
        assertTrue(terrain.hasHoles());
        assertEquals(-20f, terrain.getWaterLevel());
        assertEquals(steps + 1, editorState.getCommandHistory().getUndoCount(), "the whole plan is one undo step");
        assertTrue(editorState.undo());
        assertFalse(terrain.hasWeights() || terrain.hasHoles() || terrain.hasWater(), "and undoes as one");

        TerrainEditTool tool = new TerrainEditTool(level);
        AgentToolContext context = new AgentToolContext(editorState);
        assertEquals("'action' must be one of raise, lower, smooth, flatten, noise, paint, hole, erode, ramp, water or heightmap: dig",
            tool.validateParameters(params("action", "dig"), context).get(0));
        assertTrue(tool.validateParameters(params("action", "hole", "x", 99999f, "z", 0f), context).get(0).contains("off the ground"));
        assertTrue(tool.validateParameters(params("action", "paint", "x", 0f, "z", 0f, "surface", "lava"), context).get(0)
            .startsWith("'surface' must be"));
        AgentToolResult water = tool.execute(params("action", "water", "level", "none"), context);
        assertTrue(water.isSuccess());
        assertEquals(false, water.getOutputs().get("changed"), "no water to remove");
    }

    @Test
    void shouldRefusePlayOverAHoleAndLetACharacterWalkIntoItAndFall() {
        Terrain terrain = level.findTerrain();
        Entity camera = editorState.createEntity("Main Camera");
        camera.addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        assertTrue(editorState.setGameGenre(GameGenres.ACTION_RPG));
        level.setFocus(LevelSession.Focus.CHARACTERS);
        level.setObjectPath(LevelSession.CHARACTER_FOLDER + "/Player.prefab");
        Entity player = level.placeObject(0f, 0f, 0f);
        assertNotNull(player, editorState.getStatusMessage());
        float standing = player.getTransform().getWorldPosition().y;
        level.setFocus(LevelSession.Focus.TERRAIN);
        level.setTool(LevelSession.Tool.HOLE);
        terrainSession.getBrush().setRadius(200f);
        click(0); // the view points at the middle of the level, where the player stands
        assertTrue(terrain.isHoleAt(0f, 0f), "the hole is under the player");

        assertFalse(play.play(), "Play's ground check sees the hole under the player");
        assertTrue(editorState.getStatusMessage().contains("Nothing under Player to stand on"), editorState.getStatusMessage());

        // Start beside the hole and walk into it: W walks away from the camera, towards -z
        player.getTransform().setLocalPosition(new Vector3(0f, standing, 400f));
        assertFalse(terrain.isHoleAt(0f, 400f));
        boolean[] walking = {false};
        play.getProfileStep().setInput(key -> walking[0] && key == Input.Keys.W);
        assertTrue(play.play(), editorState.getStatusMessage());
        Entity playing = play.getRuntimeScene().findEntityById(player.getId());
        for (int frame = 0; frame < 30; frame++) {
            play.update(FRAME);
        }
        assertTrue(play.getRuntimeScene().getPhysics3D().isCharacterGrounded(playing), "stands beside the hole");
        assertEquals(standing, playing.getTransform().getWorldPosition().y, 5f);
        walking[0] = true;
        float lowest = Float.MAX_VALUE;
        for (int frame = 0; frame < 120; frame++) {
            play.update(FRAME);
            lowest = Math.min(lowest, playing.getTransform().getWorldPosition().y);
        }
        assertTrue(lowest < standing - 200f, "walked into the hole and fell through: lowest " + lowest + " from " + standing);
        play.stop();
    }

    /**
     * The milestone's done-when on real data (roadmap M3.4): on a template copy of the shipped valley, a slope is repainted with a
     * soft edge, a hole is cut and the valley floor gets water, through the same tool the assistant uses. The project is left in
     * build/m3-4-valley for running the game on it; the shipped sample is checked unchanged.
     */
    @Test
    void shouldRepaintASlopeOfTheShippedValleyWithASoftEdgeCutAHoleAndAddWater() throws IOException {
        Path assets = Path.of("..", "assets");
        Path shipped = assets.resolve("terrains/Valley.terrain");
        String original = Files.readString(shipped);
        Path out = Path.of("build", "m3-4-valley");
        if (Files.exists(out)) {
            try (Stream<Path> walk = Files.walk(out)) {
                for (Path path : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
        File root = TemplateProject.create(assets.toFile(),
            "samples/terrain-3d.json", out.toFile(), "Valley").folder();
        TerrainLibrary valleyLibrary = new TerrainLibrary(AssetSource.directory(root));
        EditorState editor = new EditorState(new SceneSerializer(new ComponentSerializerRegistry()));
        Scene valleyScene = new Scene("Valley");
        valleyScene.createEntity("Valley").addComponent(new TerrainComponent("terrains/Valley.terrain"));
        editor.setActiveScene(valleyScene);
        LevelSession valleyLevel = new LevelSession(editor, new TerrainSession(valleyLibrary), root);
        TerrainEditTool tool = new TerrainEditTool(valleyLevel);
        AgentToolContext context = new AgentToolContext(editor);
        Terrain valley = valleyLevel.findTerrain();
        float[] heights = valley.copyHeights();

        assertTrue(tool.execute(params("action", "paint", "surface", "rock", "x", -900f, "z", 1000f, "radius", 450f), context).isSuccess());
        assertTrue(tool.execute(params("action", "paint", "surface", "dirt", "x", 700f, "z", 1300f, "radius", 300f), context).isSuccess());
        assertTrue(tool.execute(params("action", "hole", "x", -250f, "z", 900f, "radius", 140f), context).isSuccess());
        assertTrue(tool.execute(params("action", "water", "level", 5f), context).isSuccess());

        // Across the left slope (rising from the floor at x 0 to the ridge), rock fades out from the brush's centre: a soft edge
        int row = Math.round((1000f + valley.getSizeZ() / 2f) / valley.getCellSize());
        int centreColumn = Math.round((-900f + valley.getSizeX() / 2f) / valley.getCellSize());
        assertEquals(Surface.ROCK, valley.dominantSurface(centreColumn, row), "the slope is rock under the brush");
        int partial = 0;
        for (int column = centreColumn; column < centreColumn + 12; column++) {
            int weight = valley.getLayerWeight(column, row, Surface.ROCK);
            partial += weight > 0 && weight < Terrain.FULL_WEIGHT ? 1 : 0;
        }
        assertTrue(partial >= 3, "several points across the edge are part rock: " + partial);
        assertTrue(valley.getHeight(centreColumn, row) > 150f, "it is a slope, well above the valley floor");
        assertTrue(valley.isHoleAt(-250f, 900f), "the hole is cut");
        assertEquals(5f, valley.getWaterLevel());
        assertArrayEquals(heights, valley.copyHeights(), "painting, holes and water leave the shape alone");
        Terrain saved = TerrainIO.fromJson(Files.readString(root.toPath().resolve("terrains/Valley.terrain")));
        assertArrayEquals(valley.copyWeights(), saved.copyWeights(), "the project's file has it all");
        assertTrue(saved.isHoleAt(-250f, 900f));
        assertEquals(original, Files.readString(shipped), "the shipped valley is unchanged");
    }

    private static Map<String, Object> params(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    /** The view's ordinary selection and camera; nothing here reaches it. */
    private static final class NothingElse implements ViewportInteraction {
        @Override
        public boolean touchDown(float x, float y, float w, float h, int button, int modifiers) {
            return false;
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
}
