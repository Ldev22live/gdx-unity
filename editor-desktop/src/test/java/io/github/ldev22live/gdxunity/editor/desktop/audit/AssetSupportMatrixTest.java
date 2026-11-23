package io.github.ldev22live.gdxunity.editor.desktop.audit;

import com.badlogic.gdx.utils.GdxNativesLoader;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The desktop and web support matrix per shipped asset (roadmap M2b.7, TD-101). On the real {@code assets/}: every file the editor
 * package ships has a recorded desktop and web status; nothing a sample or the starter palette uses is left out of the web build; the
 * copy rules agree with {@code engine-babylon/scripts/sync-samples.mjs}; and the recorded matrix
 * ({@code docs/migration/asset-support.tsv} and the summary in {@code docs/ASSET_SUPPORT_MATRIX.md}) is current. Refresh them with
 * {@code GDXUNITY_UPDATE_AUDIT=1}, as for the other audits.
 */
class AssetSupportMatrixTest {
    private static final File ASSETS = new File("../assets");
    private static final File REPOSITORY = new File("..");
    private static final File TSV = new File("../docs/migration/asset-support.tsv");
    private static final File PAGE = new File("../docs/ASSET_SUPPORT_MATRIX.md");
    private static final File SYNC = new File("../engine-babylon/scripts/sync-samples.mjs");
    private static final String SUMMARY_BEGIN = "<!-- asset-support:summary:begin -->";
    private static final String SUMMARY_END = "<!-- asset-support:summary:end -->";
    /** Formats the web build carries when a scene uses them; anything else a scene names is desktop-only by design (Java scripts). */
    private static final Set<String> WEB_FORMATS = Set.of("gltf", "glb", "bin", "png", "jpg", "jpeg", "terrain", "sprite", "material",
        "anim", "graph", "song", "json");

    private static List<AssetSupportMatrix.Row> realRows;

    @TempDir
    Path temp;

    @BeforeAll
    static void build() throws IOException {
        GdxNativesLoader.load();
        realRows = new AssetSupportMatrix(ASSETS, sceneWeb()).rows();
    }

    private static Map<String, String> sceneWeb() throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        Set<String> web = SampleInventory.babylonComponents(
            REPOSITORY.toPath().resolve("engine-babylon").resolve("src").resolve("runtime").resolve("SceneBuilder.ts"));
        Set<String> baseline = SampleInventory.baselineScenes(
            REPOSITORY.toPath().resolve("docs").resolve("performance").resolve("baseline-windows.tsv"));
        SampleInventory inventory = new SampleInventory(new SampleInventory.Sources(REPOSITORY, ASSETS, web, baseline),
            new SceneSerializer(registry));
        List<EditabilityAudit.Row> audit = TemplateMatrixTest.editorAudit().auditAll(new File(ASSETS, "samples"));
        return inventory.scenes(audit, TemplateMatrix.evaluateAll(ASSETS, new File(ASSETS, "samples"), audit)).stream()
            .collect(Collectors.toMap(SampleInventory.SceneRow::sample, SampleInventory.SceneRow::web));
    }

    private static AssetSupportMatrix.Row row(String path) {
        return realRows.stream().filter(r -> r.path().equals(path)).findFirst().orElseThrow(() -> new AssertionError("no row: " + path));
    }

    @Test
    void shouldRecordADesktopAndWebStatusForEveryShippedFile() throws IOException {
        List<String> shipped = new AssetSupportMatrix(ASSETS, sceneWeb()).shippedFiles();
        assertEquals(shipped, realRows.stream().map(AssetSupportMatrix.Row::path).toList(), "one row per shipped file, in order");
        assertTrue(shipped.size() > 1000, "the whole package, downloads included: " + shipped.size());
        for (AssetSupportMatrix.Row row : realRows) {
            assertFalse(row.record().isBlank(), row.path());
            assertTrue(row.desktop().status() != AssetSupportMatrix.Status.NOT_APPLICABLE || !row.desktop().note().isBlank(), row.path());
            assertFalse(row.web().toString().isBlank(), row.path());
        }
        assertFalse(shipped.contains("workspace_layout.json"), "the editor's own preferences are not packaged");
    }

    @Test
    void shouldServeOnTheWebEveryFileASampleOrThePaletteUsesInAWebFormat() {
        List<String> gaps = realRows.stream()
            .filter(r -> !r.usedBy().equals("-") && WEB_FORMATS.contains(r.format()))
            .filter(r -> r.web().status() == AssetSupportMatrix.Status.NO)
            .map(r -> r.path() + " (" + r.web() + ")").toList();
        assertEquals(List.of(), gaps, "used, in a format the web reads, but not in the web build");
    }

    @Test
    void shouldCallTheRefreshedValleysKenneyModelsWorkingOnTheWebAndTheirSourceFormatsNot() {
        for (String model : List.of("tree_oak.glb", "tree_pineTallA.glb", "rock_tallA.glb", "plant_bushLarge.glb")) {
            AssetSupportMatrix.Row row = row("thirdparty/kenney/nature-kit/Models/GLTF format/" + model);
            assertEquals(AssetSupportMatrix.Status.WORKS, row.desktop().status(), model);
            assertEquals(AssetSupportMatrix.Status.WORKS, row.web().status(), model + ": " + row.web());
            assertEquals("download, CC0-1.0", row.record(), model);
            assertTrue(row.usedBy().contains("samples") && row.usedBy().contains("palette"), model + ": " + row.usedBy());
        }
        AssetSupportMatrix.Row fbx = realRows.stream().filter(r -> r.format().equals("fbx")).findFirst().orElseThrow();
        assertEquals(AssetSupportMatrix.Status.NO, fbx.desktop().status());
        assertEquals(AssetSupportMatrix.Status.NO, fbx.web().status());
        AssetSupportMatrix.Row door = realRows.stream()
            .filter(r -> r.path().startsWith("models/thirdparty/polyhaven/rollershutter_door/") && r.format().equals("gltf"))
            .findFirst().orElseThrow();
        assertEquals(AssetSupportMatrix.Status.UNCHECKED, door.web().status(), "a glTF naming outside files is not loaded by the web tests");
        assertEquals(AssetSupportMatrix.Status.PARTIAL, row("samples/landscape-cc0-terrain-3d.json").web().status(),
            "drawn, but its Hero's genre profile does not play on the web");
        AssetSupportMatrix.Row script = realRows.stream().filter(r -> r.format().equals("java")).findFirst().orElseThrow();
        assertEquals(AssetSupportMatrix.Status.WORKS, script.desktop().status());
        assertEquals(AssetSupportMatrix.Status.NO, script.web().status(), "Java scripts are ported to the web by hand");
    }

    @Test
    void shouldMirrorTheCopyRulesOfTheWebSyncScript() throws IOException {
        String script = Files.readString(SYNC.toPath(), StandardCharsets.UTF_8);
        for (AssetSupportMatrix.WebRule rule : AssetSupportMatrix.WEB_RULES) {
            String last = rule.folder().substring(rule.folder().lastIndexOf('/') + 1);
            assertTrue(script.contains("\"" + last + "\""), "sync-samples.mjs copies " + rule.folder());
            if (!rule.requiredSegment().isEmpty()) {
                assertTrue(script.contains("\"" + rule.requiredSegment() + "\""), rule.requiredSegment());
            }
        }
        assertTrue(AssetSupportMatrix.servedOnWeb("thirdparty/kenney/nature-kit/Models/GLTF format/tree_oak.glb"));
        assertFalse(AssetSupportMatrix.servedOnWeb("thirdparty/kenney/nature-kit/Isometric/tree_oak_NE.png"), "the 2D renders stay out");
        assertFalse(AssetSupportMatrix.servedOnWeb("characters/makehuman/mesh.bin"), "characters/ is copied at its top level only");
        assertTrue(AssetSupportMatrix.servedOnWeb("graphs/samples/runner.graph"));
        assertFalse(AssetSupportMatrix.servedOnWeb("prefabs/level/CC0 Rock.prefab"));
    }

    @Test
    void shouldRefuseAFormatItHasNoRuleFor() throws IOException {
        Files.createDirectories(temp.resolve("misc"));
        Files.writeString(temp.resolve("misc/notes.xyz"), "?");
        AssetSupportMatrix matrix = new AssetSupportMatrix(temp.toFile(), Map.of());
        IllegalStateException refused = assertThrows(IllegalStateException.class, matrix::rows);
        assertTrue(refused.getMessage().contains("notes.xyz"), refused.getMessage());
    }

    @Test
    void shouldKeepTheRecordedMatrixUpToDate() throws IOException {
        String tsv = AssetSupportMatrix.tsv(realRows);
        String page = Files.readString(PAGE.toPath(), StandardCharsets.UTF_8);
        int from = page.indexOf(SUMMARY_BEGIN);
        int to = page.indexOf(SUMMARY_END);
        assertTrue(from >= 0 && to > from, "ASSET_SUPPORT_MATRIX.md needs its summary markers");
        String newline = page.contains("\r\n") ? "\r\n" : "\n";
        String updatedPage = page.substring(0, from) + SUMMARY_BEGIN + newline
            + AssetSupportMatrix.summaryMarkdown(realRows).replace("\n", newline) + page.substring(to);
        String recorded = TSV.isFile() ? Files.readString(TSV.toPath(), StandardCharsets.UTF_8).replace("\r\n", "\n") : "";
        if ("1".equals(System.getenv(EditabilityAuditTest.UPDATE_VARIABLE))) {
            Files.writeString(TSV.toPath(), tsv, StandardCharsets.UTF_8);
            Files.writeString(PAGE.toPath(), updatedPage, StandardCharsets.UTF_8);
            return;
        }
        assertEquals(tsv, recorded, "docs/migration/asset-support.tsv is stale: run this test with "
            + EditabilityAuditTest.UPDATE_VARIABLE + "=1");
        assertEquals(updatedPage, page, "the summary in ASSET_SUPPORT_MATRIX.md is stale");
        Map<AssetSupportMatrix.Status, Long> web = realRows.stream()
            .collect(Collectors.groupingBy(r -> r.web().status(), TreeMap::new, Collectors.counting()));
        System.out.println("M2b.7 web status of " + realRows.size() + " shipped files: " + web);
        assertTrue(realRows.stream().map(AssetSupportMatrix.Row::path).collect(Collectors.toMap(Function.identity(), p -> 1)).size()
            == realRows.size(), "each file once");
    }
}
