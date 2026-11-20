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
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.SkyLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Collider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
 * Gameplay collision of placed objects (roadmap M2b.4, TD-096), on the real Jolt physics through editor Play: in a new 3D project with
 * the starter palette, each object from Objects and Buildings is placed in front of the player, the level is played as Action RPG +
 * 3D and the player walks into it. Trunks, rocks and walls stop the player; a bush does not; the CC0 House is entered through its
 * doorway and its back wall stops the player inside. A level full of placed objects is measured, and written as a project for the game
 * runtime's timed runs.
 */
class PlacedObjectCollisionTest {
    /** Tests run in the module folder; the shipped assets are one level up. */
    private static final File SHIPPED = new File("../assets");
    private static final float FRAME = 1f / 60f;
    /** Frames for the player to settle on the ground before walking. */
    private static final int SETTLE_FRAMES = 30;
    /** Two seconds of walking at the Action RPG's 400 units a second: 800 units with nothing in the way. */
    private static final int WALK_FRAMES = 120;
    /** The player starts this far in front of the object (+Z) and walks towards it (-Z, the way W walks with yaw 0). */
    private static final float START_Z = 500f;
    /** Where the object stands. */
    private static final float OBJECT_Z = 0f;
    /** Nearer than this to the object's centre, the player went into it. */
    private static final float INSIDE_MARGIN = 20f;
    /** The CC0 House's doorway is 1 m wide around x = -100 on its front wall at z = +200; the back wall's inside face is at z = -195. */
    private static final float DOORWAY_X = -100f;
    private static final float HOUSE_FRONT_Z = 200f;
    private static final float HOUSE_BACK_INSIDE_Z = -195f;

    @TempDir
    Path projects;

    private static Input previousInput;

    /** The measured level, kept for the game runtime's timed runs ({@code GDXUNITY_RENDER_STATS=1}). */
    private static final Path STRESS = Path.of("build", "m2b-4-placed-objects");
    private static final String STRESS_SCENE = "scenes/level.json";
    /** The measured level: a grid this many cells a side, this far apart, one object per cell; a house takes every 30th cell. */
    private static final int GRID = 15;
    private static final float GRID_SPACING = 260f;
    private static final int HOUSE_EVERY = 30;
    /** Frames played for the measurement, after this many not measured while the JIT warms up. */
    private static final int MEASURED_FRAMES = 300;
    private static final int WARM_UP_FRAMES = 60;

    private EditorState editor;
    private LevelSession level;
    private Terrain terrain;
    private Entity player;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
        previousInput = Gdx.input;
        // Nothing reads the keyboard here: Play's profile is given its own keys
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

    @BeforeEach
    void setUp() throws IOException {
        open(Files.createDirectories(projects.resolve("Collision Game")).toFile());
    }

    /** A new 3D project with the starter palette, a flat level and the player, played as Action RPG + 3D. */
    private void open(File project) throws IOException {
        StarterPalette.addTo(SHIPPED, new TemplateProject.Result(project, "scenes/level.json", List.of()), GameMode.MODE_3D);
        editor = new EditorState();
        EditorApplication.registerComponentSerializers(editor.getSerializer().getRegistry());
        Scene scene = new Scene("Level");
        scene.setGameMode(GameMode.MODE_3D);
        TerrainLibrary terrains = new TerrainLibrary(AssetSource.directory(project));
        // What the editor's renderer gives the open scene: rock hulls are read from their shapes, the ground from its terrain
        scene.setColliderGeometry(new ColliderGeometry(null));
        scene.setHeightFields(new TerrainHeightFields(terrains));
        editor.setActiveScene(scene);
        editor.setPrefabService(new PrefabService(editor, project));
        level = new LevelSession(editor, new TerrainSession(terrains), project);
        assertNotNull(level.createLevel(Surface.GRASS), editor.getStatusMessage());
        terrain = level.findTerrain();
        level.setFocus(LevelSession.Focus.CHARACTERS);
        player = place(StarterPalette.CHARACTER_FOLDER + "/Player.prefab", 0f, START_Z);
        assertTrue(editor.setGameGenre(GameGenres.ACTION_RPG));
    }

    private Entity place(String prefab, float x, float z) {
        level.setObjectPath(prefab);
        Entity placed = level.placeObject(x, terrain.heightAt(x, z), z);
        assertNotNull(placed, prefab + ": " + editor.getStatusMessage());
        return placed;
    }

    /** Plays from (x, START_Z), holds W for two seconds and returns where the player ended, then stops. */
    private Vector3 walkFrom(float x) {
        Vector3 at = player.getTransform().getLocalPosition();
        player.getTransform().setLocalPosition(x, at.y, START_Z);
        PlayModeController play = new PlayModeController(editor);
        editor.setPlayModeController(play);
        boolean[] forward = {false};
        play.getProfileStep().setInput(key -> forward[0] && key == Input.Keys.W);
        assertTrue(play.play(), editor.getStatusMessage());
        try {
            Entity playing = play.getRuntimeScene().findEntityById(player.getId());
            for (int i = 0; i < SETTLE_FRAMES; i++) {
                play.update(FRAME);
            }
            forward[0] = true;
            for (int i = 0; i < WALK_FRAMES; i++) {
                play.update(FRAME);
            }
            return playing.getTransform().getWorldPosition().cpy();
        } finally {
            play.stop();
        }
    }

    @Test
    void shouldStopTheWalkingPlayerAtEveryObjectThatBlocksAndLetItThroughABush() {
        // With nothing in the way the player walks the whole way, along -Z
        Vector3 open = walkFrom(0f);
        assertTrue(open.z < OBJECT_Z - 200f, "nothing in the way: walked to " + open);
        assertEquals(0f, open.x, 5f, "W walks straight along -Z");

        Map<String, Float> stoppedAt = new LinkedHashMap<>();
        List<String> blocking = List.of(
            StarterPalette.LEVEL_FOLDER + "/Oak Tree.prefab", StarterPalette.LEVEL_FOLDER + "/Pine Tree.prefab",
            StarterPalette.LEVEL_FOLDER + "/CC0 Oak Tree.prefab", StarterPalette.LEVEL_FOLDER + "/Rock.prefab",
            StarterPalette.LEVEL_FOLDER + "/Boulder.prefab", StarterPalette.LEVEL_FOLDER + "/CC0 Rock.prefab",
            StarterPalette.BUILDING_FOLDER + "/Cottage.prefab", StarterPalette.BUILDING_FOLDER + "/Watchtower.prefab",
            StarterPalette.BUILDING_FOLDER + "/CC0 House.prefab");
        for (String prefab : blocking) {
            level.setFocus(prefab.startsWith(StarterPalette.BUILDING_FOLDER) ? LevelSession.Focus.BUILDINGS : LevelSession.Focus.OBJECTS);
            Entity object = place(prefab, 0f, OBJECT_Z);
            Vector3 end = walkFrom(0f);
            assertTrue(end.z > OBJECT_Z + INSIDE_MARGIN, prefab + " stops the player in front of it: " + end);
            assertTrue(end.z < START_Z - 20f, prefab + ": the player walked up to it: " + end);
            stoppedAt.put(prefab, end.z);
            assertTrue(editor.undo(), "the placement undoes");
            assertTrue(editor.getActiveScene().findEntityById(object.getId()) == null, prefab + " is gone after undo");
        }

        level.setFocus(LevelSession.Focus.OBJECTS);
        place(StarterPalette.LEVEL_FOLDER + "/Bush.prefab", 0f, OBJECT_Z);
        Vector3 throughBush = walkFrom(0f);
        assertTrue(throughBush.z < OBJECT_Z - 200f, "a bush is walked through: " + throughBush);

        System.out.println("M2b.4 the player stopped at z (the object at z 0, the player 30 round): " + stoppedAt);
    }

    @Test
    void shouldMeasureTheFrameCostOfALevelFullOfPlacedObjects() throws IOException {
        deleteRecursively(STRESS);
        File project = Files.createDirectories(STRESS).toFile();
        open(project);
        String[] objects = {"/CC0 Oak Tree.prefab", "/CC0 Rock.prefab", "/CC0 Oak Tree.prefab", "/Pine Tree.prefab", "/Boulder.prefab"};
        Map<String, Integer> counts = new LinkedHashMap<>();
        int cell = 0;
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++, cell++) {
                float x = (i - (GRID - 1) / 2f) * GRID_SPACING;
                float z = (j - (GRID - 1) / 2f) * GRID_SPACING - GRID_SPACING / 2f;
                if (Math.abs(x) < GRID_SPACING && Math.abs(z - START_Z) < GRID_SPACING) {
                    continue; // the player's own cell stays clear
                }
                boolean house = cell % HOUSE_EVERY == 0;
                level.setFocus(house ? LevelSession.Focus.BUILDINGS : LevelSession.Focus.OBJECTS);
                String prefab = house ? StarterPalette.BUILDING_FOLDER + "/CC0 House.prefab"
                    : StarterPalette.LEVEL_FOLDER + objects[cell % objects.length];
                place(prefab, x, z);
                counts.merge(prefab.substring(prefab.lastIndexOf('/') + 1), 1, Integer::sum);
            }
        }
        Scene scene = editor.getActiveScene();
        scene.createEntity("Sun").addComponent(new DirectionalLightComponent());
        scene.createEntity("Sky Light").addComponent(new SkyLightComponent());
        Files.createDirectories(STRESS.resolve("scenes"));
        Files.writeString(STRESS.resolve(STRESS_SCENE), editor.getSerializer().toJson(scene), StandardCharsets.UTF_8);
        Files.writeString(STRESS.resolve("gdxunity.project"), "format=1\nname=Placed Objects\nstartScene=" + STRESS_SCENE + "\n",
            StandardCharsets.UTF_8);

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
        } finally {
            play.stop();
        }
        Arrays.sort(frames);
        int bodies = 0;
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.getComponents(Collider3DComponent.class).isEmpty()) {
                bodies++;
            }
        }
        String result = String.format(Locale.ROOT, "objects\t%s%nentities\t%d%ncollider entities\t%d%nplay start ms\t%.1f%n"
                + "update median ms\t%.3f%nupdate p95 ms\t%.3f%nupdate max ms\t%.3f%n", counts, scene.getAllEntities().size(), bodies,
            startMs, frames[MEASURED_FRAMES / 2] / 1e6, frames[MEASURED_FRAMES * 95 / 100] / 1e6, frames[MEASURED_FRAMES - 1] / 1e6);
        Files.writeString(STRESS.resolve("play-update-cost.tsv"), result, StandardCharsets.UTF_8);
        System.out.println("M2b.4 placed objects, editor Play on real Jolt (no rendering):\n" + result);
        assertTrue(bodies > GRID * GRID, "every placed object brought its colliders: " + bodies);
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

    @Test
    void shouldWalkIntoTheCc0HouseThroughItsDoorwayAndStopAtItsBackWall() {
        level.setFocus(LevelSession.Focus.BUILDINGS);
        place(StarterPalette.BUILDING_FOLDER + "/CC0 House.prefab", 0f, OBJECT_Z);

        Vector3 atWindow = walkFrom(-DOORWAY_X);
        assertTrue(atWindow.z > OBJECT_Z + HOUSE_FRONT_Z, "the front window wall stops the player outside: " + atWindow);

        Vector3 inside = walkFrom(DOORWAY_X);
        assertTrue(inside.z < OBJECT_Z + HOUSE_FRONT_Z - 50f, "through the doorway into the house: " + inside);
        assertTrue(inside.z > OBJECT_Z + HOUSE_BACK_INSIDE_Z, "the back wall stops the player inside: " + inside);
        assertEquals(DOORWAY_X, inside.x, 10f, "the doorway's jambs did not push the player aside");
    }
}
