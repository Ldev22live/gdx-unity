package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolRegistry;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutionResult;
import io.github.ldev22live.gdxunity.editor.core.agent.executor.AgentPlanExecutor;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlan;
import io.github.ldev22live.gdxunity.editor.core.agent.plan.AgentPlanStep;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M3.5: a heightmap round-trips through a 16-bit PNG, reads the images other tools write, and imports as one undo step; the
 * Erode and Ramp tools are one undo step each that the {@code .terrain} file follows; the assistant reaches all of it.
 */
class ErosionRampHeightmapTest {
    private static final float WIDTH = 800f;
    private static final float HEIGHT = 600f;

    @TempDir
    Path project;

    private TerrainSession terrainSession;
    private EditorState editorState;
    private LevelSession level;
    private ViewportInteraction interaction;
    private Terrain terrain;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        terrainSession = new TerrainSession(new TerrainLibrary(AssetSource.directory(project.toFile())));
        editorState = new EditorState(new SceneSerializer());
        editorState.setActiveScene(new Scene("Level"));
        level = new LevelSession(editorState, terrainSession, project.toFile());
        PerspectiveCamera3D camera = new PerspectiveCamera3D(60f, WIDTH, HEIGHT, 1f, 20000f);
        camera.setPosition(0f, 800f, 500f);
        camera.setDirection(new Vector3(0f, -800f, -500f).nor());
        camera.update();
        interaction = new LevelViewportInteraction(level, camera, new TerrainViewportInteraction(editorState, terrainSession, camera,
            new NothingElse()));
        level.setActive(true);
        level.createLevel(Surface.GRASS);
        terrain = level.findTerrain();
        for (int iz = 0; iz < terrain.getResolutionZ(); iz++) {
            for (int ix = 0; ix < terrain.getResolutionX(); ix++) {
                // Steep hills (slopes up to about 3, well past erosion's 0.7) so the Erode tool has something to wear down
                terrain.setHeight(ix, iz, (float) (600 * Math.sin(ix * 0.2) * Math.cos(iz * 0.15) + ix * 2));
            }
        }
    }

    private Terrain onDisk() throws IOException {
        String path = level.findGround().getComponent(TerrainComponent.class).getTerrainPath();
        return TerrainIO.fromJson(Files.readString(project.resolve(path)));
    }

    // ---- the PNG ----------------------------------------------------------------------------------------------------------

    @Test
    void shouldRoundTripA16BitHeightmapExactlyAndKeepItsRange() throws IOException {
        int[] samples = {0, 1, 255, 256, 32_767, 65_535};
        HeightmapPng.Heightmap back = HeightmapPng.read(HeightmapPng.write(3, 2, samples, -12.5f, 480f));

        assertEquals(3, back.width());
        assertEquals(2, back.height());
        assertArrayEquals(samples, back.samples(), "every 16-bit step survives");
        assertEquals(-12.5f, back.lowest());
        assertEquals(480f, back.highest());
    }

    @Test
    void shouldRoundTripTheTerrainThroughAPngWithinOneStepOfItsRange() throws IOException {
        float[] heights = terrain.copyHeights();
        float[] range = terrain.heightRange();
        byte[] png = Heightmaps.export(terrain);

        Terrain fresh = new Terrain(terrain.getResolutionX(), terrain.getResolutionZ(), terrain.getCellSize());
        Heightmaps.importInto(fresh, HeightmapPng.read(png));

        float step = (range[1] - range[0]) / HeightmapPng.MAX_SAMPLE;
        float[] read = fresh.copyHeights();
        for (int i = 0; i < heights.length; i++) {
            assertEquals(heights[i], read[i], step, "height " + i);
        }
        assertArrayEquals(png, Heightmaps.export(fresh), "exporting the imported terrain gives the same file: a true round trip");
    }

    @Test
    void shouldReadTheHeightmapsOtherToolsWriteAndResampleThemToTheGrid() throws IOException {
        // 16-bit gray, as written by Java's own encoder (its own row filters)
        BufferedImage gray16 = new BufferedImage(3, 3, BufferedImage.TYPE_USHORT_GRAY);
        gray16.getRaster().setSample(1, 1, 0, 65_535);
        HeightmapPng.Heightmap map = HeightmapPng.read(png(gray16));
        assertEquals(65_535, map.samples()[4]);
        assertFalse(map.hasRange(), "another tool's image does not say its range");

        Terrain small = new Terrain(5, 5, 10f);
        Heightmaps.importInto(small, map);
        assertEquals(Heightmaps.DEFAULT_RANGE, small.getHeight(2, 2), 0.01f, "white is the default range's top, at the middle");
        assertEquals(Heightmaps.DEFAULT_RANGE / 2f, small.getHeight(1, 2), 0.01f, "half way to the middle, half way up (bilinear)");
        assertEquals(0f, small.getHeight(0, 0), "black is 0");

        // 8-bit gray, and colour (the red channel is read)
        BufferedImage gray8 = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_GRAY);
        gray8.getRaster().setSample(1, 0, 0, 255);
        assertArrayEquals(new int[]{0, 65_535}, HeightmapPng.read(png(gray8)).samples(), "8 bits scaled to 16");
        BufferedImage rgb = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        rgb.setRGB(1, 1, 0xFF0000);
        rgb.setRGB(0, 1, 0x00FF00);
        assertArrayEquals(new int[]{0, 0, 0, 65_535}, HeightmapPng.read(png(rgb)).samples(), "red is height, green is ignored");

        BufferedImage palette = new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_INDEXED);
        assertTrue(assertThrows(IOException.class, () -> HeightmapPng.read(png(palette))).getMessage().contains("palette"));
        assertTrue(assertThrows(IOException.class, () -> HeightmapPng.read("not a png".getBytes())).getMessage().contains("Not a PNG"));
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", out));
        return out.toByteArray();
    }

    // ---- the level editor ------------------------------------------------------------------------------------------------------

    @Test
    void shouldExportAndImportAHeightmapThroughTheActionsWithImportAsOneUndoStep() throws IOException {
        File file = project.resolve("terrains/valley.png").toFile();
        EditorActionRegistry actions = new EditorActionRegistry();
        LevelActions.registerHeightmaps(actions, editorState, level, new Dialogs(file), project.resolve("terrains").toFile());
        float[] shaped = terrain.copyHeights();

        assertTrue(actions.execute(LevelActions.EXPORT_HEIGHTMAP));
        assertTrue(file.isFile(), editorState.getStatusMessage());
        for (int iz = 0; iz < terrain.getResolutionZ(); iz++) {
            for (int ix = 0; ix < terrain.getResolutionX(); ix++) {
                terrain.setHeight(ix, iz, 0f); // flattened by hand
            }
        }
        int steps = editorState.getCommandHistory().getUndoCount();

        assertTrue(actions.execute(LevelActions.IMPORT_HEIGHTMAP));

        float step = (terrain.heightRange()[1] - terrain.heightRange()[0]) / HeightmapPng.MAX_SAMPLE;
        assertEquals(shaped[terrain.index(30, 40)], terrain.getHeight(30, 40), step, "the shape is back");
        assertEquals(steps + 1, editorState.getCommandHistory().getUndoCount(), "import is one undo step");
        assertArrayEquals(terrain.copyHeights(), onDisk().copyHeights(), "the file follows");
        assertTrue(editorState.undo());
        assertEquals(0f, terrain.getHeight(30, 40), "undo puts the flattened ground back");
        assertArrayEquals(terrain.copyHeights(), onDisk().copyHeights(), "and the file follows the undo");
    }

    @Test
    void shouldErodeAndLayARampFromTheViewportEachAsOneUndoStepThatTheFileFollows() throws IOException {
        terrainSession.getBrush().setRadius(250f);
        terrainSession.getBrush().setStrength(1f);
        float[] before = terrain.copyHeights();
        level.setFocus(LevelSession.Focus.TERRAIN);
        level.setTool(LevelSession.Tool.ERODE);
        int steps = editorState.getCommandHistory().getUndoCount();

        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, 0);
        interaction.touchDragged(WIDTH / 2 + 20f, HEIGHT / 2, WIDTH, HEIGHT);
        interaction.touchUp(Input.Buttons.LEFT);

        assertFalse(Arrays.equals(before, terrain.copyHeights()), "erosion changed the ground under the brush");
        assertEquals(steps + 1, editorState.getCommandHistory().getUndoCount());
        assertArrayEquals(terrain.copyHeights(), onDisk().copyHeights());

        level.setTool(LevelSession.Tool.RAMP);
        float[] eroded = terrain.copyHeights();
        interaction.touchDown(WIDTH / 2 - 150f, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, 0);
        interaction.touchDragged(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT);
        interaction.touchDragged(WIDTH / 2 + 150f, HEIGHT / 2, WIDTH, HEIGHT);
        interaction.touchUp(Input.Buttons.LEFT);

        assertFalse(Arrays.equals(eroded, terrain.copyHeights()), "the ramp was laid");
        assertEquals(steps + 2, editorState.getCommandHistory().getUndoCount());
        assertTrue(editorState.undo());
        assertArrayEquals(eroded, terrain.copyHeights(), "undo takes the ramp back");
        assertTrue(editorState.undo());
        assertArrayEquals(before, terrain.copyHeights(), "and the erosion");
        assertArrayEquals(before, onDisk().copyHeights(), "the file follows both");
    }

    @Test
    void shouldLetTheAssistantErodeRampImportAndExportAHeightmap() throws Exception {
        AgentToolRegistry tools = new AgentToolRegistry();
        tools.registerTool(new TerrainEditTool(level));
        tools.registerTool(new TerrainHeightmapExportTool(level));
        AgentToolContext context = new AgentToolContext(editorState);
        float[] before = terrain.copyHeights();

        AgentToolResult exported = tools.getTool(TerrainHeightmapExportTool.ID).orElseThrow()
            .execute(params("path", "terrains/saved.png"), context);
        assertTrue(exported.isSuccess(), exported.getMessage());
        assertTrue(Files.isRegularFile(project.resolve("terrains/saved.png")));

        AgentPlan plan = new AgentPlan("Wear the hills and cut a path");
        plan.addStep(new AgentPlanStep("s1", TerrainEditTool.ID, params("action", "erode", "x", 0f, "z", 0f, "radius", 400f, "strength", 1f)));
        plan.addStep(new AgentPlanStep("s2", TerrainEditTool.ID, params("action", "ramp", "x", -600f, "z", 0f, "x2", 600f, "z2", 0f,
            "radius", 80f)));
        int steps = editorState.getCommandHistory().getUndoCount();
        AgentPlanExecutionResult result = new AgentPlanExecutor(tools).executePlan(plan, context);
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(steps + 1, editorState.getCommandHistory().getUndoCount(), "the plan is one undo step");
        float start = before[terrain.index(Math.round((-600f + terrain.getSizeX() / 2f) / terrain.getCellSize()), terrain.getResolutionZ() / 2)];
        assertTrue(Math.abs(terrain.heightAt(0f, 0f) - (start + terrain.heightAt(600f, 0f)) / 2f) < 5f,
            "the middle of the ramp is half way between its ends");

        AgentToolResult imported = tools.getTool(TerrainEditTool.ID).orElseThrow()
            .execute(params("action", "heightmap", "path", "terrains/saved.png"), context);
        assertTrue(imported.isSuccess(), imported.getMessage());
        float step = (terrain.heightRange()[1] - terrain.heightRange()[0]) / HeightmapPng.MAX_SAMPLE;
        assertEquals(before[terrain.index(30, 40)], terrain.getHeight(30, 40), step * 2f, "the saved heightmap puts the old shape back");
        assertTrue(new TerrainEditTool(level).validateParameters(params("action", "heightmap", "path", "../outside.png"), context)
            .get(0).contains("inside the project"));
        assertTrue(new TerrainHeightmapExportTool(level).validateParameters(params("path", "terrains/x.txt"), context)
            .get(0).contains(".png"));
    }

    /**
     * Roadmap M3.5 on real data: a template copy of the shipped valley exports its heightmap, gets a ramp up its left slope and its
     * right ridge eroded, and importing the exported heightmap puts the valley back as it was (a round trip), all as undo steps. The
     * edited copy (ramp and erosion, without the import) is left in build/m3-5-valley for the game runtime; the heightmap beside it.
     */
    @Test
    void shouldRampErodeAndRoundTripTheShippedValleysHeightmap() throws Exception {
        Path assets = Path.of("..", "assets");
        String original = Files.readString(assets.resolve("terrains/Valley.terrain"));
        Path out = Path.of("build", "m3-5-valley");
        if (Files.exists(out)) {
            try (Stream<Path> walk = Files.walk(out)) {
                for (Path path : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
        File root = TemplateProject.create(assets.toFile(), "samples/terrain-3d.json", out.toFile(), "Valley").folder();
        EditorState editor = new EditorState(new SceneSerializer());
        Scene scene = new Scene("Valley");
        scene.createEntity("Valley").addComponent(new TerrainComponent("terrains/Valley.terrain"));
        editor.setActiveScene(scene);
        LevelSession valleyLevel = new LevelSession(editor, new TerrainSession(new TerrainLibrary(AssetSource.directory(root))), root);
        Terrain valley = valleyLevel.findTerrain();
        float[] shipped = valley.copyHeights();
        File heightmap = new File(root, "terrains/Valley-heightmap.png");
        assertTrue(valleyLevel.exportHeightmap(heightmap), editor.getStatusMessage());
        AgentToolContext context = new AgentToolContext(editor);
        TerrainEditTool tool = new TerrainEditTool(valleyLevel);

        // A ramp from the valley floor up the left slope, and erosion along the right ridge
        assertTrue(tool.execute(params("action", "ramp", "x", 0f, "z", 1100f, "x2", -1500f, "z2", 400f, "radius", 120f), context).isSuccess());
        assertTrue(tool.execute(params("action", "erode", "x", 1300f, "z", 800f, "radius", 600f, "strength", 1f), context).isSuccess());
        float floor = valley.heightAt(0f, 1100f);
        float top = valley.heightAt(-1500f, 400f);
        assertEquals((floor + top) / 2f, valley.heightAt(-750f, 750f), 6f, "half way up the ramp is half way between its ends");
        assertFalse(Arrays.equals(shipped, valley.copyHeights()));
        Files.copy(root.toPath().resolve("terrains/Valley.terrain"), out.resolve("Valley-edited.terrain"));

        assertTrue(valleyLevel.importHeightmap(heightmap), editor.getStatusMessage());
        float step = (valley.heightRange()[1] - valley.heightRange()[0]) / HeightmapPng.MAX_SAMPLE;
        float[] back = valley.copyHeights();
        for (int i = 0; i < shipped.length; i++) {
            assertEquals(shipped[i], back[i], step, "the exported heightmap puts the shipped valley back, point " + i);
        }
        assertTrue(editor.undo(), "undo the import: the ramp and erosion are back");
        assertEquals((floor + top) / 2f, valley.heightAt(-750f, 750f), 6f);
        assertEquals(original, Files.readString(assets.resolve("terrains/Valley.terrain")), "the shipped valley is unchanged");
    }

    private static Map<String, Object> params(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    /** Dialogs that answer every file question with one file. */
    private static final class Dialogs implements EditorDialogService {
        private final File file;

        Dialogs(File file) {
            this.file = file;
        }

        @Override
        public Optional<File> chooseOpenFile(String title, File initialDirectory, String filterDescription, String extension) {
            return Optional.of(file);
        }

        @Override
        public Optional<File> chooseSaveFile(String title, File suggestedFile, String filterDescription, String extension) {
            return Optional.of(file);
        }

        @Override
        public UnsavedChangesChoice askUnsavedChanges(String documentName) {
            throw new AssertionError("no question expected");
        }

        @Override
        public void showError(String title, String message) {
            throw new AssertionError(title + ": " + message);
        }
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
