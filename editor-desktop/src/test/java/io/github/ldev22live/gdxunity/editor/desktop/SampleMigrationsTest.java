package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.sprite.TilemapSession;
import io.github.ldev22live.gdxunity.editor.terrain.LevelSession;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainEditTool;
import io.github.ldev22live.gdxunity.editor.terrain.TerrainSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import io.github.ldev22live.gdxunity.engine.desktop.GameComponentSerializers;
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics.StaticColliderSource2D.StaticBox2D;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainIO;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap MC.7: the migration plan in {@code docs/migration/sample-migrations.json} covers every sample once, no sample is replaced
 * all at once (each migrated slice is a new file beside an original that stays as a fixture, with its ids), every evidence a slice
 * names exists, and the evidence MP.3 lacked: editing each migrated scene in its workspace, undone and redone.
 */
class SampleMigrationsTest {
    private static final File REPOSITORY = new File("..");
    private static final File ASSETS = new File("../assets");
    private static final File LEDGER = new File("../docs/migration/sample-migrations.json");
    private static final List<String> EVIDENCE_TESTS = List.of("behaviour", "collision", "load", "undoRedo", "export");
    /** Evidence a slice may name besides, checked the same way when it does (M2b.6's done-when: reopen and Play isolation). */
    private static final List<String> OPTIONAL_EVIDENCE_TESTS = List.of("reopen", "playIsolation", "provenance");

    @TempDir
    Path project;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static JsonValue ledger() throws IOException {
        return new JsonReader().parse(Files.readString(LEDGER.toPath(), StandardCharsets.UTF_8));
    }

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        return new SceneSerializer(registry);
    }

    private static Scene load(String sample) throws IOException {
        return serializer().fromJson(Files.readString(new File(ASSETS, "samples/" + sample).toPath(), StandardCharsets.UTF_8));
    }

    private static Set<String> gallery() throws IOException {
        Set<String> scenes = new TreeSet<>();
        JsonValue templates = new JsonReader().parse(Files.readString(new File(ASSETS, "templates/gallery.json").toPath())).get("templates");
        for (JsonValue t = templates.child; t != null; t = t.next) {
            scenes.add(t.getString("scene"));
        }
        return scenes;
    }

    @Test
    void shouldPlanEverySampleInExactlyOneGroup() throws IOException {
        Map<String, String> groupOf = new HashMap<>();
        for (JsonValue group = ledger().get("groups").child; group != null; group = group.next) {
            for (String sample : group.get("samples").asStringArray()) {
                String previous = groupOf.put(sample, group.getString("id"));
                assertEquals(null, previous, sample + " is in two groups");
                assertTrue(new File(ASSETS, "samples/" + sample).isFile(), sample + " named in the plan does not exist");
            }
            assertTrue(group.has("rollback") && group.has("blockers") && group.has("cost"), group.getString("id"));
        }
        Set<String> shipped = new TreeSet<>(List.of(new File(ASSETS, "samples").list((dir, name) -> name.endsWith(".json"))));
        assertEquals(shipped, new TreeSet<>(groupOf.keySet()), "a new sample needs a place in the migration plan");
    }

    @Test
    void shouldKeepEveryOriginalAsAFixtureBesideItsMigratedSliceWithItsIds() throws IOException {
        Set<String> gallery = gallery();
        Set<String> originals = new HashSet<>();
        Map<String, ThirdPartyManifest.FileRecord> downloads = new HashMap<>();
        for (ThirdPartyManifest.Entry entry : ThirdPartyManifest.read(ASSETS).entries()) {
            for (ThirdPartyManifest.FileRecord record : entry.files()) {
                downloads.put(record.path(), record);
            }
        }
        int slices = 0;
        for (JsonValue slice = ledger().get("slices").child; slice != null; slice = slice.next) {
            slices++;
            String original = slice.getString("original");
            String migrated = slice.getString("migrated");
            assertNotEquals(original, migrated, "a migration writes a new file");
            assertTrue(originals.add(original), original + " is migrated twice in one plan: one slice per sample");
            for (String scene : List.of(original, migrated)) {
                assertTrue(gallery.contains("samples/" + scene), scene + " is offered, the original too");
            }
            assertTrue(new File(REPOSITORY, slice.getString("generator")).isFile(), "the generator is kept: " + slice.getString("generator"));
            for (String file : slice.get("newFiles").asStringArray()) {
                assertTrue(new File(ASSETS, file).isFile(), file);
            }
            // A downloaded asset needs its manifest entry (M2b.1) before a slice may use it (M2b.6: the first slice that does)
            for (String file : slice.get("downloadedAssets").asStringArray()) {
                assertTrue(downloads.containsKey(file), migrated + " uses " + file + ", which has no licence record");
                assertEquals(downloads.get(file).sha256(), sha256(new File(ASSETS, file)), file + " is the file that was downloaded");
                assertTrue(Files.readString(new File(ASSETS, "samples/" + migrated).toPath()).contains(file)
                        || namedByAPrefabTheSliceUses(migrated, file), migrated + " uses " + file);
            }

            Scene before = load(original);
            Scene after = load(migrated);
            Set<String> replaced = Set.of(slice.get("replacedEntities").asStringArray());
            for (Entity entity : before.getAllEntities()) {
                if (replaced.contains(entity.getName())) {
                    assertEquals(null, after.findEntityById(entity.getId()), entity.getName() + " was replaced");
                } else {
                    assertNotNull(after.findEntityById(entity.getId()), migrated + " keeps " + entity.getName() + "'s id");
                }
            }
            before.dispose();
            after.dispose();
        }
        assertTrue(slices >= 2, "the MP.3 slices are recorded");
    }

    @Test
    void shouldFindEveryEvidenceASliceNames() throws IOException {
        List<Path> tests = testSources();
        for (JsonValue entry = ledger().get("slices").child; entry != null; entry = entry.next) {
            JsonValue slice = entry;
            JsonValue evidence = slice.get("evidence");
            for (String screenshot : evidence.get("screenshots").asStringArray()) {
                assertTrue(new File(REPOSITORY, screenshot).isFile(), screenshot);
            }
            assertTrue(new File(REPOSITORY, evidence.getString("performance")).isFile(), evidence.getString("performance"));
            List<String> kinds = new ArrayList<>(EVIDENCE_TESTS);
            OPTIONAL_EVIDENCE_TESTS.stream().filter(evidence::has).forEach(kinds::add);
            for (String kind : kinds) {
                String reference = evidence.getString(kind);
                String[] parts = reference.split("#");
                Path source = tests.stream().filter(p -> p.getFileName().toString().equals(parts[0] + ".java")).findFirst()
                    .orElseThrow(() -> new AssertionError(slice.getString("id") + " " + kind + ": no test class " + parts[0]));
                assertTrue(Files.readString(source).contains("void " + parts[1] + "("),
                    slice.getString("id") + " " + kind + ": " + parts[0] + " has no test " + parts[1]);
            }
        }
    }

    /** Whether a prefab the migrated scene places names {@code file}: a refreshed object's model is in its prefab, not the scene. */
    private static boolean namedByAPrefabTheSliceUses(String migrated, String file) throws IOException {
        JsonValue scene = new JsonReader().parse(Files.readString(new File(ASSETS, "samples/" + migrated).toPath(), StandardCharsets.UTF_8));
        Set<String> prefabs = new TreeSet<>();
        for (JsonValue entity = scene.get("entities").child; entity != null; entity = entity.next) {
            for (JsonValue component = entity.get("components").child; component != null; component = component.next) {
                if ("PrefabInstance".equals(component.getString("type"))) {
                    prefabs.add(component.get("data").getString("prefab"));
                }
            }
        }
        for (String prefab : prefabs) {
            if (Files.readString(new File(ASSETS, prefab).toPath(), StandardCharsets.UTF_8).contains(file)) {
                return true;
            }
        }
        return false;
    }

    private static String sha256(File file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath())));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    private static List<Path> testSources() throws IOException {
        List<Path> found = new ArrayList<>();
        File[] modules = REPOSITORY.listFiles(File::isDirectory);
        for (File module : modules == null ? new File[0] : modules) {
            Path tests = module.toPath().resolve("src").resolve("test");
            if (Files.isDirectory(tests)) {
                try (Stream<Path> walk = Files.walk(tests)) {
                    walk.filter(p -> p.toString().endsWith(".java")).forEach(found::add);
                }
            }
        }
        return found;
    }

    /** The 2D slice in its workspace: a tile painted in 2D Studio: Tiles is solid, and undo and redo take it out and back. */
    @Test
    void shouldPaintTheMigratedTileMapAndUndoAndRedoItWithItsCollision() throws IOException {
        Scene scene = load("platformer-tiles-2d.json");
        TilemapComponent tiles = scene.findEntityByName("Level").getComponent(TilemapComponent.class);
        List<StaticBox2D> before = tiles.getStaticBoxes();
        int[] cellsBefore = tiles.getMap().copyCells(0);
        // An empty cell above the first gap (x 600 to 620, y 120 to 140): a new stepping stone
        int col = 30;
        int row = tiles.getMap().getRows() - 1 - 6;
        assertEquals(0, tiles.getMap().get(0, col, row), "the cell starts empty");

        TilemapSession session = new TilemapSession(tiles.getMap());
        session.setAutoRule("ground");
        session.setTool(TilemapSession.Tool.PAINT);
        session.pointerDown(col, row);
        session.pointerUp(col, row);

        assertTrue(tiles.getMap().isSolid(tiles.getMap().get(0, col, row)), "painted with a solid ground tile");
        assertTrue(tiles.getStaticBoxes().contains(new StaticBox2D(610f, 130f, 20f, 20f)), "the new tile collides: " + tiles.getStaticBoxes());
        assertEquals(before.size() + 1, tiles.getStaticBoxes().size());

        assertTrue(session.getHistory().undo());
        assertArrayEquals(cellsBefore, tiles.getMap().copyCells(0), "undo takes the tile back out");
        assertEquals(before, tiles.getStaticBoxes());
        assertTrue(session.getHistory().redo());
        assertTrue(tiles.getStaticBoxes().contains(new StaticBox2D(610f, 130f, 20f, 20f)), "redo puts it back");
        scene.dispose();
    }

    /** The 2.5D slice in its workspace: a raised slope on the migrated ground, written to its file, undone and redone. */
    @Test
    void shouldSculptTheMigratedTerrainAndUndoAndRedoItWithItsFile() throws IOException {
        // Edits go to a copy of the terrain file: the shipped one is never written by a test
        Path terrains = Files.createDirectories(project.resolve("terrains"));
        Files.copy(new File(ASSETS, "terrains/Platformer Ground.terrain").toPath(), terrains.resolve("Platformer Ground.terrain"));
        Scene scene = load("platformer-profile-terrain-25d.json");
        EditorState editor = new EditorState(serializer());
        editor.setActiveScene(scene);
        TerrainSession terrainSession = new TerrainSession(new TerrainLibrary(AssetSource.directory(project.toFile())));
        LevelSession level = new LevelSession(editor, terrainSession, project.toFile());
        assertEquals("Ground", level.findGround().getName(), "the migrated ground is the level's terrain");
        Terrain terrain = level.findTerrain();
        int centreX = terrain.getResolutionX() / 2;
        int centreZ = terrain.getResolutionZ() / 2;
        assertEquals(0f, terrain.getHeight(centreX, centreZ));

        EditorCommand raise = new TerrainEditTool(level).createCommand(
            Map.of("action", "raise", "x", 0f, "z", 0f, "radius", 150f, "strength", 1f), null);
        assertNotNull(raise, "the brush changed the ground");
        assertTrue(editor.executeCommand(raise));
        float raised = terrain.getHeight(centreX, centreZ);
        assertTrue(raised > 0f, "a slope rises under the brush: " + raised);
        assertEquals(raised, onDisk().getHeight(centreX, centreZ), "the .terrain file follows the edit");

        assertTrue(editor.undo());
        assertEquals(0f, terrain.getHeight(centreX, centreZ), "undo flattens it again");
        assertEquals(0f, onDisk().getHeight(centreX, centreZ), "and the file follows the undo");
        assertTrue(editor.redo());
        assertEquals(raised, terrain.getHeight(centreX, centreZ), "redo raises it again");
        assertEquals(raised, onDisk().getHeight(centreX, centreZ));
    }

    private Terrain onDisk() throws IOException {
        return TerrainIO.fromJson(Files.readString(project.resolve("terrains/Platformer Ground.terrain"), StandardCharsets.UTF_8));
    }
}
