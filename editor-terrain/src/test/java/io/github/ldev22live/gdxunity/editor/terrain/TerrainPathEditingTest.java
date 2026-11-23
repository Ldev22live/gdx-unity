package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolRegistry;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutionResult;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutor;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlan;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlanStep;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainPathComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TerrainComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TerrainPathComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.TerrainCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainIO;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap M3.7 in the level editor: the Path tool builds a road point by point, and Apply carves, paints and places in one undo step. */
class TerrainPathEditingTest {
    private static final float WIDTH = 800f;
    private static final float HEIGHT = 600f;
    private static final String PINE = StarterPalette.LEVEL_FOLDER + "/Pine Tree.prefab";

    @TempDir
    Path project;

    private EditorState editorState;
    private LevelSession level;
    private ViewportInteraction interaction;
    private Terrain terrain;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() throws IOException {
        Path objects = Files.createDirectories(project.resolve(StarterPalette.LEVEL_FOLDER));
        Files.copy(Path.of("..", "assets", PINE), objects.resolve("Pine Tree.prefab"), StandardCopyOption.REPLACE_EXISTING);
        TerrainSession terrainSession = new TerrainSession(new TerrainLibrary(AssetSource.directory(project.toFile())));
        // Placing a prefab snapshots the scene, so every component in it needs its serializer
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new MeshPrimitiveComponentSerializer());
        registry.register(new TerrainComponentSerializer());
        registry.register(new TerrainCollider3DComponentSerializer());
        registry.register(new TerrainPathComponentSerializer());
        registry.register(new PrefabInstanceComponentSerializer());
        editorState = new EditorState(new SceneSerializer(registry));
        editorState.setActiveScene(new Scene("Level"));
        editorState.setPrefabService(new PrefabService(editorState, project.toFile()));
        level = new LevelSession(editorState, terrainSession, project.toFile());
        PerspectiveCamera3D camera = new PerspectiveCamera3D(60f, WIDTH, HEIGHT, 1f, 20000f);
        camera.setPosition(0f, 1500f, 900f);
        camera.setDirection(new Vector3(0f, -1500f, -900f).nor());
        camera.update();
        interaction = new LevelViewportInteraction(level, camera, new TerrainViewportInteraction(editorState, terrainSession, camera,
            new NothingElse()));
        level.setActive(true);
        level.createLevel(Surface.GRASS);
        terrain = level.findTerrain();
        for (int iz = 0; iz < terrain.getResolutionZ(); iz++) {
            for (int ix = 0; ix < terrain.getResolutionX(); ix++) {
                float d = (float) Math.hypot(terrain.xAt(ix), terrain.zAt(iz));
                terrain.setHeight(ix, iz, d < 900f ? 250f * (float) (0.5 + 0.5 * Math.cos(Math.PI * d / 900f)) : 0f); // a hill
            }
        }
        level.setFocus(LevelSession.Focus.TERRAIN);
        level.setTool(LevelSession.Tool.PATH);
    }

    private void click(float x, float y) {
        interaction.touchDown(x, y, WIDTH, HEIGHT, Input.Buttons.LEFT, 0);
        interaction.touchUp(Input.Buttons.LEFT);
    }

    private Terrain onDisk() throws IOException {
        String path = level.findGround().getComponent(TerrainComponent.class).getTerrainPath();
        return TerrainIO.fromJson(Files.readString(project.resolve(path)));
    }

    @Test
    void shouldStartAPathWithAClickAndAddAPointPerClickEachOneUndoStep() {
        int steps = editorState.getCommandHistory().getUndoCount();

        click(WIDTH * 0.2f, HEIGHT / 2);
        Entity path = level.selectedPath();
        assertNotNull(path, "the first click starts a path and selects it");
        assertEquals("Road", path.getName());
        assertEquals(1, path.getComponent(TerrainPathComponent.class).getPointCount());
        click(WIDTH * 0.5f, HEIGHT / 2);
        click(WIDTH * 0.8f, HEIGHT / 2);

        TerrainPathComponent component = level.selectedPath().getComponent(TerrainPathComponent.class);
        assertEquals(3, component.getPointCount(), "a point per click on the selected path");
        assertTrue(component.getPoints().get(0).x < component.getPoints().get(2).x, "left to right as clicked");
        assertEquals(steps + 3, editorState.getCommandHistory().getUndoCount(), "one undo step per click");
        assertTrue(level.removeLastPathPoint());
        assertEquals(2, component.getPointCount(), "Undo Point");
        assertTrue(level.setPathKind(TerrainPath.Kind.RIVER));
        assertEquals(TerrainPath.Kind.RIVER, component.getKind());
        assertTrue(editorState.undo());
        assertEquals(TerrainPath.Kind.ROAD, component.getKind(), "every change undoes");

        level.startNewPath();
        assertNull(level.selectedPath(), "New Path: the next click starts another one");
    }

    @Test
    void shouldCarvePaintAndPlaceMeshesAlongThePathAsOneUndoStepThatTheFileFollows() throws IOException {
        Entity path = level.addPathPoint(-1100f, -200f);
        level.addPathPoint(0f, 100f);
        level.addPathPoint(1100f, -100f);
        assertTrue(level.setPathPrefab(PINE));
        float[] before = terrain.copyHeights();
        int entities = editorState.getActiveScene().getAllEntities().size();
        int steps = editorState.getCommandHistory().getUndoCount();

        assertTrue(level.applyPath(), editorState.getStatusMessage());

        assertFalse(Arrays.equals(before, terrain.copyHeights()), "the ground is carved");
        assertTrue(terrain.heightAt(0f, 100f) > 100f, "the road goes over the hill");
        int middle = Math.round((0f + terrain.getSizeX() / 2f) / terrain.getCellSize());
        int row = Math.round((100f + terrain.getSizeZ() / 2f) / terrain.getCellSize());
        assertEquals(Surface.DIRT, terrain.dominantSurface(middle, row), "the road is painted");
        int placed = editorState.getActiveScene().getAllEntities().size() - entities;
        assertTrue(placed >= 4, "pines stand along the road: " + placed);
        assertEquals(steps + 1, editorState.getCommandHistory().getUndoCount(), "carve and every pine: one undo step");
        assertArrayEquals(terrain.copyHeights(), onDisk().copyHeights(), "the file follows");
        assertSame(path, level.selectedPath(), "the path stays selected, to extend and apply again");

        assertTrue(editorState.undo());
        assertArrayEquals(before, terrain.copyHeights(), "undo puts the ground back");
        assertFalse(terrain.hasWeights(), "and the paint");
        assertEquals(entities, editorState.getActiveScene().getAllEntities().size(), "and removes the pines");
        assertArrayEquals(before, onDisk().copyHeights(), "and the file follows");
    }

    @Test
    void shouldRefuseToApplyAPathWithOnePoint() {
        level.addPathPoint(0f, 0f);

        assertFalse(level.applyPath());
        assertTrue(editorState.getStatusMessage().contains("needs at least two points"), editorState.getStatusMessage());
    }

    @Test
    void shouldLetTheAssistantLayARoadAcrossTheHillAsOneUndoStep() {
        AgentToolRegistry tools = new AgentToolRegistry();
        tools.registerTool(new TerrainPathTool(level));
        AgentPlan plan = new AgentPlan("A road over the hill");
        plan.addStep(new AgentPlanStep("s1", TerrainPathTool.ID, params("points", List.of(List.of(-1100, 0), List.of(0, 50), List.of(1100, 0)),
            "kind", "road", "width", 100, "prefab", PINE, "name", "Hill Road")));
        int steps = editorState.getCommandHistory().getUndoCount();
        float[] before = terrain.copyHeights();

        AgentPlanExecutionResult result = new AgentPlanExecutor(tools).executePlan(plan, new AgentToolContext(editorState));

        assertTrue(result.isSuccess(), result.getMessage());
        Entity road = editorState.getActiveScene().findEntityByName("Hill Road");
        assertNotNull(road, "the path entity is in the scene, to extend later");
        assertEquals(3, road.getComponent(TerrainPathComponent.class).getPointCount());
        assertEquals(new Vector2(0f, 50f), road.getComponent(TerrainPathComponent.class).getPoints().get(1));
        assertFalse(Arrays.equals(before, terrain.copyHeights()));
        assertEquals(steps + 1, editorState.getCommandHistory().getUndoCount());
        assertTrue(editorState.undo());
        assertNull(editorState.getActiveScene().findEntityByName("Hill Road"));
        assertArrayEquals(before, terrain.copyHeights());

        TerrainPathTool tool = new TerrainPathTool(level);
        AgentToolContext context = new AgentToolContext(editorState);
        assertTrue(tool.validateParameters(params("points", List.of(List.of(0, 0))), context).get(0).contains("at least two"));
        assertTrue(tool.validateParameters(params("points", List.of(List.of(0, 0), List.of(99999, 0))), context).get(0).contains("off the ground"));
        assertTrue(tool.validateParameters(params("points", List.of(List.of(0, 0), List.of(100, 0)), "kind", "canal"), context).get(0)
            .contains("road, river or cliff"));
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
