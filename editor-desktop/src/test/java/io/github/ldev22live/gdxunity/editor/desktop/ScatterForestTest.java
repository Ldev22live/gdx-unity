package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.SkyLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Scatter tool on the shipped palette (roadmap M3.6, TD-097): in a new 3D project with the starter palette, pines and CC0 oaks are
 * scattered over a hill around a CC0 House and the player, each stroke one undo step. Nothing lands in the house or at the player's
 * start or on ground steeper than the limit. The level plays as Action RPG + 3D on real Jolt and is measured, and is written as a
 * project for the game runtime's timed runs ({@code GDXUNITY_RENDER_STATS=1}, with {@code GDXUNITY_OBJECT_CULLING=0} to compare).
 */
class ScatterForestTest {
    private static final File SHIPPED = new File("../assets");
    private static final Path OUT = Path.of("build", "m3-6-scatter-forest");
    private static final String SCENE = "scenes/level.json";
    private static final float FRAME = 1f / 60f;
    /** The hill: this high at its top, falling off over this distance (a Gaussian), so its sides slope up to about 25 degrees. */
    private static final float HILL_HEIGHT = 700f;
    private static final float HILL_WIDTH = 1300f;
    /** Strokes run along x over this half width, this far apart in z. */
    private static final float FOREST_HALF = 2200f;
    private static final float ROW_GAP = 450f;
    private static final float DAB_STEP = 100f;
    private static final float BRUSH = 500f;
    private static final float MAX_SLOPE = 30f;
    private static final Vector3 HOUSE_AT = new Vector3(-900f, 0f, 900f);
    private static final Vector3 PLAYER_AT = new Vector3(0f, 0f, 1700f);
    private static final int MEASURED_FRAMES = 300;
    private static final int WARM_UP_FRAMES = 60;

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

    @Test
    void shouldScatterAForestOnAHillAroundAHouseAndPlayIt() throws IOException {
        deleteRecursively(OUT);
        File project = Files.createDirectories(OUT).toFile();
        StarterPalette.addTo(SHIPPED, new TemplateProject.Result(project, SCENE, List.of()), GameMode.MODE_3D);
        EditorState editor = new EditorState();
        EditorApplication.registerComponentSerializers(editor.getSerializer().getRegistry());
        Scene scene = new Scene("Forest");
        scene.setGameMode(GameMode.MODE_3D);
        TerrainLibrary terrains = new TerrainLibrary(AssetSource.directory(project));
        scene.setColliderGeometry(new ColliderGeometry(null));
        scene.setHeightFields(new TerrainHeightFields(terrains));
        scene.createEntity("Main Camera").addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        scene.createEntity("Sun").addComponent(new DirectionalLightComponent());
        scene.createEntity("Sky Light").addComponent(new SkyLightComponent());
        editor.setActiveScene(scene);
        editor.setPrefabService(new PrefabService(editor, project));
        TerrainSession terrainSession = new TerrainSession(terrains);
        LevelSession level = new LevelSession(editor, terrainSession, project);
        assertNotNull(level.createLevel(Surface.GRASS), editor.getStatusMessage());
        Terrain terrain = level.findTerrain();
        for (int iz = 0; iz < terrain.getResolutionZ(); iz++) {
            for (int ix = 0; ix < terrain.getResolutionX(); ix++) {
                float x = -terrain.getSizeX() / 2f + ix * terrain.getCellSize();
                float z = -terrain.getSizeZ() / 2f + iz * terrain.getCellSize();
                terrain.setHeight(ix, iz, HILL_HEIGHT * (float) Math.exp(-(x * x + z * z) / (HILL_WIDTH * HILL_WIDTH)));
            }
        }
        terrains.save(level.findGround().getComponent(TerrainComponent.class)
            .getTerrainPath(), terrain);

        level.setFocus(LevelSession.Focus.BUILDINGS);
        level.setObjectPath(StarterPalette.BUILDING_FOLDER + "/CC0 House.prefab");
        Entity house = level.placeObject(HOUSE_AT.x, terrain.heightAt(HOUSE_AT.x, HOUSE_AT.z), HOUSE_AT.z);
        assertNotNull(house, editor.getStatusMessage());
        level.setFocus(LevelSession.Focus.CHARACTERS);
        level.setObjectPath(StarterPalette.CHARACTER_FOLDER + "/Player.prefab");
        Entity player = level.placeObject(PLAYER_AT.x, terrain.heightAt(PLAYER_AT.x, PLAYER_AT.z), PLAYER_AT.z);
        assertNotNull(player, editor.getStatusMessage());
        assertTrue(editor.setGameGenre(GameGenres.ACTION_RPG));

        // Scatter: pines all over, then oaks thinner, row by row; each row one stroke and one undo step
        level.setFocus(LevelSession.Focus.OBJECTS);
        level.setTool(LevelSession.Tool.SCATTER);
        terrainSession.getBrush().setRadius(BRUSH);
        level.getScatterSettings().setMaxSlopeDegrees(MAX_SLOPE);
        level.getScatterSettings().setSpacing(160f);
        Map<String, Integer> scattered = new LinkedHashMap<>();
        int strokes = 0;
        for (String[] kind : new String[][] {{"Pine Tree", "8"}, {"CC0 Oak Tree", "2"}}) {
            level.setObjectPath(StarterPalette.LEVEL_FOLDER + "/" + kind[0] + ".prefab");
            level.getScatterSettings().setDensity(Float.parseFloat(kind[1]));
            for (float z = -FOREST_HALF; z <= FOREST_HALF; z += ROW_GAP) {
                int undo = editor.getCommandHistory().getUndoCount();
                assertTrue(level.beginScatter(false), editor.getStatusMessage());
                for (float x = -FOREST_HALF; x <= FOREST_HALF; x += DAB_STEP) {
                    level.scatterAt(x, z);
                }
                int placed = level.endScatter();
                scattered.merge(kind[0], placed, Integer::sum);
                strokes++;
                assertEquals(undo + (placed > 0 ? 1 : 0), editor.getCommandHistory().getUndoCount(), "a stroke is one undo step");
            }
        }
        assertTrue(scattered.get("Pine Tree") > 200, "a forest of pines: " + scattered);

        // Nothing in the house, at the player's start, or on ground over the slope limit
        float houseReach = 200f * (float) Math.sqrt(2) + 20f;
        int onSteep = 0;
        for (Entity entity : scene.getAllEntities()) {
            PrefabInstanceComponent link = PrefabService.linkOf(entity);
            if (link == null || !link.isRoot() || !link.getPrefabPath().startsWith(StarterPalette.LEVEL_FOLDER + "/")) {
                continue;
            }
            Vector3 at = entity.getTransform().getWorldPosition();
            assertTrue(Vector3.dst(at.x, 0f, at.z, HOUSE_AT.x, 0f, HOUSE_AT.z) > houseReach, "outside the house: " + at);
            assertTrue(Vector3.dst(at.x, 0f, at.z, PLAYER_AT.x, 0f, PLAYER_AT.z) >= LevelSession.CHARACTER_KEEP_OUT,
                "clear of the player's start: " + at);
            if (slopeAt(terrain, at.x, at.z) > MAX_SLOPE + 0.5f) {
                onSteep++;
            }
        }
        assertEquals(0, onSteep, "nothing on ground steeper than the limit");

        Files.createDirectories(OUT.resolve("scenes"));
        Files.writeString(OUT.resolve(SCENE), editor.getSerializer().toJson(scene), StandardCharsets.UTF_8);
        Files.writeString(OUT.resolve("gdxunity.project"), "format=1\nname=Scatter Forest\nstartScene=" + SCENE + "\n",
            StandardCharsets.UTF_8);

        // Play it: Action RPG + 3D on real Jolt, the player walking into the forest
        PlayModeController play = new PlayModeController(editor);
        editor.setPlayModeController(play);
        boolean[] forward = {false};
        play.getProfileStep().setInput(key -> forward[0] && key == Input.Keys.W);
        long startNanos = System.nanoTime();
        assertTrue(play.play(), editor.getStatusMessage());
        double startMs = (System.nanoTime() - startNanos) / 1e6;
        long[] frames = new long[MEASURED_FRAMES];
        try {
            forward[0] = true;
            for (int i = 0; i < WARM_UP_FRAMES; i++) {
                play.update(FRAME);
            }
            for (int i = 0; i < MEASURED_FRAMES; i++) {
                long before = System.nanoTime();
                play.update(FRAME);
                frames[i] = System.nanoTime() - before;
            }
            Entity walking = play.getRuntimeScene().findEntityById(player.getId());
            assertTrue(play.getRuntimeScene().getPhysics3D().isCharacterGrounded(walking), "the player stays on the hill among the trees");
        } finally {
            play.stop();
        }
        Arrays.sort(frames);
        String result = String.format(Locale.ROOT, "scattered\t%s%nstrokes\t%d%nentities\t%d%nplay start ms\t%.1f%n"
                + "update median ms\t%.3f%nupdate p95 ms\t%.3f%n", scattered, strokes, scene.getAllEntities().size(), startMs,
            frames[MEASURED_FRAMES / 2] / 1e6, frames[MEASURED_FRAMES * 95 / 100] / 1e6);
        Files.writeString(OUT.resolve("play-update-cost.tsv"), result, StandardCharsets.UTF_8);
        System.out.println("M3.6 scatter forest, editor Play on real Jolt (no rendering):\n" + result);
    }

    private static float slopeAt(Terrain terrain, float x, float z) {
        int ix = Math.round((x + terrain.getSizeX() / 2f) / terrain.getCellSize());
        int iz = Math.round((z + terrain.getSizeZ() / 2f) / terrain.getCellSize());
        return (float) Math.toDegrees(Math.acos(terrain.normalAt(ix, iz, new Vector3()).y));
    }

    private static void deleteRecursively(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(path);
            }
        }
    }
}
