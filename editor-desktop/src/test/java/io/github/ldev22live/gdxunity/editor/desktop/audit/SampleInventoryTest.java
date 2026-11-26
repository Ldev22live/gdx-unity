package io.github.ldev22live.gdxunity.editor.desktop.audit;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.desktop.GameComponentSerializers;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sample inventory of roadmap MP.1: its rules on a small project written for them, the real samples (every one loads and declares
 * its dimension), and the tables in {@code docs/reports/sample-asset-migration-inventory.md}, refreshed with
 * {@code GDXUNITY_UPDATE_AUDIT=1} as for the M1.1 audit.
 */
class SampleInventoryTest {
    private static final File ASSETS = new File("../assets");
    private static final File REPOSITORY = new File("..");
    private static final File REPORT = new File("../docs/reports/sample-asset-migration-inventory.md");
    private static final String SCENES_BEGIN = "<!-- sample-inventory:scenes:begin -->";
    private static final String SCENES_END = "<!-- sample-inventory:scenes:end -->";
    private static final String ASSETS_BEGIN = "<!-- sample-inventory:assets:begin -->";
    private static final String ASSETS_END = "<!-- sample-inventory:assets:end -->";
    private static final String REPLACEMENTS_BEGIN = "<!-- sample-inventory:replacements:begin -->";
    private static final String REPLACEMENTS_END = "<!-- sample-inventory:replacements:end -->";
    private static final File LEDGER = new File("../docs/migration/sample-migrations.json");

    @TempDir
    Path project;

    @BeforeAll
    static void init() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        return new SceneSerializer(registry);
    }

    private void write(String relative, String text) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static String scene(String mode, String... components) {
        return "{\"schemaVersion\":1,\"name\":\"Test\",\"gameMode\":\"" + mode + "\",\"entities\":[{\"id\":\"a\",\"name\":\"A\","
            + "\"active\":true,\"parentId\":null,\"transform\":{},\"components\":[" + String.join(",", components) + "]}]}";
    }

    private static String component(String type, String data) {
        return "{\"type\":\"" + type + "\",\"enabled\":true,\"data\":{" + data + "}}";
    }

    private SampleInventory inventory(Set<String> web) throws IOException {
        return new SampleInventory(new SampleInventory.Sources(project.toFile(), project.resolve("assets").toFile(), web,
            Set.of("samples/a-3d.json")), serializer());
    }

    private List<SampleInventory.SceneRow> scenes(SampleInventory inventory) throws IOException {
        File assets = project.resolve("assets").toFile();
        List<EditabilityAudit.Row> audit = TemplateMatrixTest.editorAudit(assets).auditAll(new File(assets, "samples"));
        return inventory.scenes(audit, TemplateMatrix.evaluateAll(assets, new File(assets, "samples"), audit));
    }

    @Test
    void shouldListAScenesDimensionFormatsMissingFilesLicencesTestsAndBaseline() throws IOException {
        write("assets/samples/a-3d.json", scene("3d",
            component("MeshPrimitiveComponent", "\"materialPath\":\"materials/Stone.material\""),
            component("ModelComponent", "\"model\":\"models/gone.gltf\"")));
        write("assets/materials/Stone.material", "{\"baseColorTexture\":\"textures/stone.png\"}");
        write("assets/textures/stone.png", "x");
        write("assets/samples/b-a-3d.json", scene("2d"));
        // A test that names b-a-3d.json must not count as naming a-3d.json
        write("game/src/test/java/StoneTest.java", "class StoneTest { String s = \"samples/a-3d.json\"; }");
        write("game/src/test/java/OtherTest.java", "class OtherTest { String s = \"b-a-3d.json\"; }");

        List<SampleInventory.SceneRow> rows = scenes(inventory(Set.of()));

        SampleInventory.SceneRow a = rows.get(0);
        assertEquals("a-3d.json", a.sample());
        assertEquals("3d", a.dimension());
        assertEquals(List.of("gltf 1", "material 1", "png 1"), a.formats(), "the texture is found through the material");
        assertEquals(List.of("models/gone.gltf"), a.missing());
        assertEquals(List.of(SampleInventory.LICENCE_UNRECORDED), a.licences());
        assertEquals(List.of("StoneTest"), a.tests());
        assertTrue(a.baseline());
        assertEquals(List.of("OtherTest"), rows.get(1).tests());
        assertEquals("2d", rows.get(1).dimension());
        assertTrue(a.web().startsWith("no: "), a.web());
    }

    @Test
    void shouldReportAFileThatDoesNotLoadInsteadOfSkippingIt() throws IOException {
        write("assets/samples/a-3d.json", scene("3d", component("NoSuchComponent", "")));

        SampleInventory.SceneRow row = scenes(inventory(Set.of())).get(0);

        assertTrue(row.load().startsWith("fails: "), row.load());
    }

    @Test
    void shouldTellHowEachContentFileWasMadeAndWhoUsesIt() throws IOException {
        write("tools/make_rocks.py", "OUT = 'rock.gltf'\nOTHER = 'Stone.material'\n");
        write("assets/models/rock.gltf", "{\"asset\":{\"version\":\"2.0\",\"generator\":\"Rock maker (tools/make_rocks.py)\"}}");
        write("assets/models/strip.gltf", "{\"asset\":{\"version\":\"2.0\",\"generator\":\"sample (make_sample.py)\"}}");
        write("assets/materials/Stone.material", "{}");
        write("assets/music/tune.song", "{}");
        write("assets/characters/Hero.gltf", "{\"asset\":{\"version\":\"2.0\"}}");
        write("assets/characters/makehuman/mesh.bin", "x");
        write("assets/samples/a-3d.json", scene("3d", component("MusicSourceComponent", "\"song\":\"music/tune.song\"")));
        SampleInventory inventory = inventory(Set.of());

        Map<String, SampleInventory.AssetRow> rows = inventory.assets(scenes(inventory)).stream()
            .collect(Collectors.toMap(SampleInventory.AssetRow::path, r -> r, (x, y) -> x, TreeMap::new));

        assertEquals("Rock maker (tools/make_rocks.py)", rows.get("models/rock.gltf").origin());
        assertEquals("says make_sample.py, which is not in tools/ (cannot be regenerated)", rows.get("models/strip.gltf").origin());
        assertEquals("named in tools/make_rocks.py", rows.get("materials/Stone.material").origin());
        assertEquals("no record (made in the editor or by hand)", rows.get("music/tune.song").origin());
        assertEquals(List.of("a-3d"), rows.get("music/tune.song").usedBy());
        assertEquals(List.of(), rows.get("models/rock.gltf").usedBy());
        assertEquals(SampleInventory.LICENCE_CC0_DERIVED, rows.get("characters/Hero.gltf").licence());
        assertEquals(SampleInventory.LICENCE_CC0, rows.get("characters/makehuman/*").licence(), "the MakeHuman data is one row");
        assertTrue(rows.keySet().stream().noneMatch(p -> p.equals("characters/makehuman/mesh.bin")));
        assertEquals("Audio & Music", rows.get("music/tune.song").opens());
    }

    /** docs/asset-integration-report.md: a downloaded file's origin and licence come from the third-party manifest. */
    @Test
    void shouldTakeADownloadedFilesOriginAndLicenceFromTheManifest() throws IOException {
        write("assets/textures/thirdparty/brick.jpg", "x");
        write("assets/textures/own.png", "x");
        write("assets/samples/a-3d.json", scene("3d"));
        write("assets/licenses/manifest.json", "{\"schemaVersion\":1,\"kind\":\"third-party-assets\",\"assets\":[{\"id\":\"b\","
            + "\"name\":\"Brick\",\"category\":\"texture\",\"source\":\"https://polyhaven.com/a/b\",\"licence\":\"CC0-1.0\","
            + "\"licenceEvidence\":\"https://polyhaven.com/license\",\"authors\":[\"A. Maker\"],\"retrieved\":\"2026-11-19\","
            + "\"retrievedWith\":\"tool\",\"format\":\"jpg\",\"conversion\":\"none\",\"importStatus\":\"imported\",\"files\":["
            + "{\"path\":\"textures/thirdparty/brick.jpg\",\"original\":\"brick.jpg\",\"bytes\":1,\"sha256\":\"" + "0".repeat(64) + "\"}]}]}");
        SampleInventory inventory = inventory(Set.of());

        Map<String, SampleInventory.AssetRow> rows = inventory.assets(scenes(inventory)).stream()
            .collect(Collectors.toMap(SampleInventory.AssetRow::path, r -> r, (x, y) -> x, TreeMap::new));

        assertEquals("CC0-1.0 (A. Maker; https://polyhaven.com/license)", rows.get("textures/thirdparty/brick.jpg").licence());
        assertEquals("downloaded from https://polyhaven.com/a/b on 2026-11-19 (licenses/manifest.json)",
            rows.get("textures/thirdparty/brick.jpg").origin());
        assertEquals(SampleInventory.LICENCE_UNRECORDED, rows.get("textures/own.png").licence(), "made in the project");
    }

    /** Roadmap M2b.5 (TD-099): 2D art made in the repository takes its origin and licence from licenses/originals.json. */
    @Test
    void shouldTakeAnOriginalFilesOriginAndLicenceFromTheOriginalsRecord() throws IOException {
        write("assets/textures/own.png", "x");
        write("assets/textures/other.png", "x");
        write("assets/samples/a-3d.json", scene("3d"));
        write("assets/licenses/originals.json", "{\"schemaVersion\":1,\"kind\":\"project-originals\",\"files\":[{\"path\":"
            + "\"textures/own.png\",\"bytes\":1,\"sha256\":\"" + "0".repeat(64) + "\",\"licence\":\"Apache-2.0\","
            + "\"madeWith\":\"tools/make_material_samples.py\"}]}");
        SampleInventory inventory = inventory(Set.of());

        Map<String, SampleInventory.AssetRow> rows = inventory.assets(scenes(inventory)).stream()
            .collect(Collectors.toMap(SampleInventory.AssetRow::path, r -> r, (x, y) -> x, TreeMap::new));

        assertEquals("Apache-2.0 (this repository; licenses/originals.json)", rows.get("textures/own.png").licence());
        assertEquals("made with tools/make_material_samples.py (licenses/originals.json)", rows.get("textures/own.png").origin());
        assertEquals(SampleInventory.LICENCE_UNRECORDED, rows.get("textures/other.png").licence(), "not in the record");
    }

    @Test
    void shouldCallAScenePlayableOnTheWebOnlyWhenEveryComponentIsSupported() throws IOException {
        write("assets/samples/a-3d.json", scene("3d", component("CameraComponent", ""), component("TerrainComponent", "")));

        assertEquals("no: TerrainComponent not supported", scenes(inventory(Set.of("CameraComponent"))).get(0).web());
        assertEquals("yes", scenes(inventory(Set.of("CameraComponent", "TerrainComponent"))).get(0).web());
    }

    @Test
    void shouldLoadEveryShippedSampleAndFindEachOnesDeclaredDimension() throws IOException {
        List<SampleInventory.SceneRow> rows = realScenes(realInventory());

        assertEquals(new File(ASSETS, "samples").list((dir, name) -> name.endsWith(".json")).length, rows.size());
        for (SampleInventory.SceneRow row : rows) {
            assertEquals("loads", row.load(), row.sample() + " is not presumed broken: the editor's serializer loads it");
            assertTrue(Set.of("2d", "2.5d", "3d").contains(row.dimension()), row.sample() + ": " + row.dimension());
            assertEquals(List.of(), row.missing(), row.sample() + " names files that do not exist");
        }
        Map<String, Long> byDimension = rows.stream().collect(Collectors.groupingBy(SampleInventory.SceneRow::dimension,
            TreeMap::new, Collectors.counting()));
        assertEquals(Map.of("2d", 8L, "2.5d", 9L, "3d", 39L), byDimension, "2.5D: 9 with the hybrid sample (MC.4); 3D: 39 with the yard (M3b.7)");
    }

    @Test
    void shouldKeepTheTablesInTheInventoryReportUpToDate() throws IOException {
        SampleInventory inventory = realInventory();
        List<SampleInventory.SceneRow> scenes = realScenes(inventory);
        String report = Files.readString(REPORT.toPath(), StandardCharsets.UTF_8);
        String updated = replace(report, SCENES_BEGIN, SCENES_END, SampleInventory.scenesMarkdown(scenes));
        updated = replace(updated, ASSETS_BEGIN, ASSETS_END, SampleInventory.assetsMarkdown(inventory.assets(scenes)));
        updated = replace(updated, REPLACEMENTS_BEGIN, REPLACEMENTS_END,
            SampleInventory.replacementsMarkdown(inventory.replacements(scenes, ledger())));

        if ("1".equals(System.getenv(EditabilityAuditTest.UPDATE_VARIABLE)) && !updated.equals(report)) {
            Files.writeString(REPORT.toPath(), updated, StandardCharsets.UTF_8);
            return;
        }
        assertEquals(updated, report, "the tables in sample-asset-migration-inventory.md are stale: run this test with "
            + EditabilityAuditTest.UPDATE_VARIABLE + "=1");
    }

    private static JsonValue ledger() throws IOException {
        return new JsonReader().parse(Files.readString(LEDGER.toPath(), StandardCharsets.UTF_8));
    }

    /** Appendix A3 (roadmap M2b.6): every scene has its replacement plan, and the refreshed valley says what it was made from. */
    @Test
    void shouldGiveEverySceneAReplacementPlanAndTheRefreshedValleyItsOrigin() throws IOException {
        SampleInventory inventory = realInventory();
        List<SampleInventory.SceneRow> scenes = realScenes(inventory);
        List<SampleInventory.ReplacementRow> rows = inventory.replacements(scenes, ledger());
        assertEquals(scenes.size(), rows.size());
        for (SampleInventory.ReplacementRow row : rows) {
            for (String cell : List.of(row.replacement(), row.sources(), row.visual(), row.collision(), row.rollback(), row.status())) {
                assertFalse(cell.isBlank(), row.sample() + " has every A3 column");
            }
        }
        SampleInventory.ReplacementRow refreshed = rows.stream().filter(r -> r.sample().equals("landscape-cc0-terrain-3d.json"))
            .findFirst().orElseThrow();
        assertEquals("landscapes-3d", refreshed.group());
        assertTrue(refreshed.current().startsWith("65 prefab instances"), refreshed.current());
        assertEquals("migrated copy of `landscape-terrain-3d.json` (M2b.6)", refreshed.status());
        SampleInventory.ReplacementRow terrainCopy = rows.stream().filter(r -> r.sample().equals("landscape-terrain-3d.json"))
            .findFirst().orElseThrow();
        assertEquals("migrated copy of `landscape-3d.json` (M3.10); original kept; copy `landscape-cc0-terrain-3d.json` (M2b.6)",
            terrainCopy.status());
    }

    @Test
    void shouldRefuseAReplacementPlanThatLeavesASceneOut() throws IOException {
        write("assets/samples/a-3d.json", scene("3d"));
        SampleInventory inventory = inventory(Set.of());
        List<SampleInventory.SceneRow> rows = scenes(inventory);
        JsonValue empty = new JsonReader().parse("{\"groups\":[],\"slices\":[]}");
        assertThrows(IllegalArgumentException.class, () -> inventory.replacements(rows, empty));
    }

    private static String replace(String text, String begin, String end, String table) {
        int from = text.indexOf(begin);
        int to = text.indexOf(end);
        assertTrue(from >= 0 && to > from, "the report needs the markers " + begin + " and " + end);
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        return text.substring(0, from) + begin + newline + table.replace("\n", newline) + text.substring(to);
    }

    private static SampleInventory realInventory() throws IOException {
        Set<String> web = SampleInventory.babylonComponents(
            REPOSITORY.toPath().resolve("engine-babylon").resolve("src").resolve("runtime").resolve("SceneBuilder.ts"));
        Set<String> baseline = SampleInventory.baselineScenes(
            REPOSITORY.toPath().resolve("docs").resolve("performance").resolve("baseline-windows.tsv"));
        return new SampleInventory(new SampleInventory.Sources(REPOSITORY, ASSETS, web, baseline), serializer());
    }

    private static List<SampleInventory.SceneRow> realScenes(SampleInventory inventory) throws IOException {
        List<EditabilityAudit.Row> audit = TemplateMatrixTest.editorAudit().auditAll(new File(ASSETS, "samples"));
        return inventory.scenes(audit, TemplateMatrix.evaluateAll(ASSETS, new File(ASSETS, "samples"), audit));
    }
}
