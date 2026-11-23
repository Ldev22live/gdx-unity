package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainPathComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M3.7's done-when on real data: on a template copy of the shipped valley, a road laid with the Path tool's operations follows
 * a curve from the valley floor beside the hero up and over the left ridge, at an even grade, level across, painted, with pines along
 * it; it undoes in one step; it is saved with the scene and reopens. The project is left in build/m3-7-road for the game runtime.
 */
class RoadAcrossAHillTest {
    private static final File ASSETS = new File("../assets");
    private static final Path OUT = Path.of("build", "m3-7-road");
    /** The road's points, in the valley's own space: the floor beside the hero, up the left slope, over the ridge, a little beyond. */
    private static final float[][] POINTS = {{0f, 1400f}, {-900f, 1150f}, {-1750f, 1000f}, {-2400f, 750f}};

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldLayARoadThatFollowsACurveAcrossTheValleysRidge() throws IOException {
        clean(OUT);
        String shippedValley = Files.readString(ASSETS.toPath().resolve("terrains/Valley.terrain"));
        EditorState editor = new EditorState();
        EditorApplication.registerComponentSerializers(editor.getSerializer().getRegistry());
        TemplateGallery.Choice valleySample = TemplateGallery.samples(ASSETS).stream()
            .filter(c -> "samples/terrain-3d.json".equals(c.scene())).findFirst().orElseThrow();
        TemplateProject.Result made = TemplateGallery.create(valleySample, ASSETS, OUT.toFile(), "Road", editor.getSerializer());
        File root = made.folder();
        File sceneFile = new File(root, made.startScene());
        SceneFileController files = new SceneFileController(editor, new NoDialogs(), root);
        assertTrue(files.openFile(sceneFile), editor.getStatusMessage());
        TerrainLibrary terrains = new TerrainLibrary(AssetSource.directory(root));
        editor.getActiveScene().setHeightFields(new TerrainHeightFields(terrains));
        editor.setPrefabService(new PrefabService(editor, root));
        LevelSession level = new LevelSession(editor, new TerrainSession(terrains), root);
        level.setActive(true);
        Terrain valley = level.findTerrain();
        assertNotNull(valley);
        float[] shipped = valley.copyHeights();
        float floor = valley.heightAt(POINTS[0][0], POINTS[0][1]);
        float ridge = valley.heightAt(POINTS[2][0], POINTS[2][1]);
        assertTrue(ridge - floor > 250f, "the road has a hill to cross: " + floor + " to " + ridge);

        // The Path tool: a click per point, then Pine Trees beside it, then Apply
        level.setFocus(LevelSession.Focus.TERRAIN);
        level.setTool(LevelSession.Tool.PATH);
        Entity road = null;
        for (float[] point : POINTS) {
            road = level.addPathPoint(point[0], point[1]);
        }
        assertTrue(level.setPathPrefab(StarterPalette.LEVEL_FOLDER + "/Pine Tree.prefab"));
        int entities = editor.getActiveScene().getAllEntities().size();
        int steps = editor.getCommandHistory().getUndoCount();
        assertTrue(level.applyPath(), editor.getStatusMessage());

        float[] xs = new float[POINTS.length];
        float[] zs = new float[POINTS.length];
        for (int i = 0; i < POINTS.length; i++) {
            xs[i] = POINTS[i][0];
            zs[i] = POINTS[i][1];
        }
        TerrainPath.Samples line = TerrainPath.sample(xs, zs, valley.getCellSize());
        float highest = -Float.MAX_VALUE;
        float previous = valley.heightAt(line.x()[0], line.z()[0]);
        for (int i = 1; i < line.size(); i++) {
            float h = valley.heightAt(line.x()[i], line.z()[i]);
            float step = line.distance()[i] - line.distance()[i - 1];
            assertTrue(Math.abs(h - previous) <= TerrainPath.MAX_ROAD_GRADE * step + 3f,
                "the road climbs evenly: " + (h - previous) + " over " + step + " at " + line.x()[i] + ", " + line.z()[i]);
            highest = Math.max(highest, h);
            previous = h;
        }
        assertTrue(highest > floor + 200f, "the road goes up over the ridge: " + highest + " from " + floor);
        int mid = line.size() / 2;
        float tx = line.x()[mid + 1] - line.x()[mid - 1];
        float tz = line.z()[mid + 1] - line.z()[mid - 1];
        float length = (float) Math.hypot(tx, tz);
        float sideX = -tz / length * 30f;
        float sideZ = tx / length * 30f;
        assertEquals(valley.heightAt(line.x()[mid] + sideX, line.z()[mid] + sideZ),
            valley.heightAt(line.x()[mid] - sideX, line.z()[mid] - sideZ), 3f, "level across the road on the slope");
        int column = Math.round((line.x()[mid] + valley.getSizeX() / 2f) / valley.getCellSize());
        int row = Math.round((line.z()[mid] + valley.getSizeZ() / 2f) / valley.getCellSize());
        assertEquals(Surface.DIRT, valley.dominantSurface(column, row), "the road is painted");
        long pines = editor.getActiveScene().getAllEntities().stream()
            .filter(e -> e.getComponent(PrefabInstanceComponent.class) != null && e.getName().startsWith("Pine")).count();
        assertTrue(pines >= 6, "pines stand along the road: " + pines);
        assertEquals(steps + 1, editor.getCommandHistory().getUndoCount(), "Apply is one undo step");

        assertTrue(editor.undo());
        assertArrayEquals(shipped, valley.copyHeights(), "undo takes the road back");
        assertEquals(entities, editor.getActiveScene().getAllEntities().size(), "and the pines");
        assertTrue(editor.redo(), "redo lays it again");

        // Saved with the scene: the path stays, to extend and apply again
        assertTrue(files.save(), editor.getStatusMessage());
        EditorState reopened = new EditorState();
        EditorApplication.registerComponentSerializers(reopened.getSerializer().getRegistry());
        assertTrue(new SceneFileController(reopened, new NoDialogs(), root).openFile(sceneFile), reopened.getStatusMessage());
        Entity back = reopened.getActiveScene().findEntityById(road.getId());
        assertNotNull(back, "the road's path is saved");
        List<Vector2> points = back.getComponent(TerrainPathComponent.class).getPoints();
        assertEquals(POINTS.length, points.size());
        assertEquals(new Vector2(POINTS[2][0], POINTS[2][1]), points.get(2));
        assertEquals(shippedValley, Files.readString(ASSETS.toPath().resolve("terrains/Valley.terrain")), "the shipped valley is unchanged");
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

    /** Dialogs never shown: the test saves to the file it opened. */
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
