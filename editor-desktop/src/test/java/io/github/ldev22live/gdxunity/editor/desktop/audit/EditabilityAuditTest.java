package io.github.ldev22live.gdxunity.editor.desktop.audit;

import io.github.ldev22live.gdxunity.editor.desktop.EditorApplication;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptAssets;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.desktop.GameComponentSerializers;
import io.github.ldev22live.gdxunity.engine.material.MaterialIO;
import io.github.ldev22live.gdxunity.engine.material.MaterialLibrary;
import io.github.ldev22live.gdxunity.engine.render.ModelAssets;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The editability audit of roadmap Phase R1, run over every sample scene. It fails on defects (a component nobody can edit, a
 * file a scene names that is missing), and it pins the known gaps (samples whose logic is Java source, asset types with no
 * opening editor) so a new gap cannot appear unnoticed and a closed gap must be taken off the list. The audit's table is kept in
 * {@code docs/FEATURE_TEST_MATRIX.md}; this test fails when the table is stale. To refresh it, run the test with the environment
 * variable {@value #UPDATE_VARIABLE} set to 1.
 */
class EditabilityAuditTest {
    static final String UPDATE_VARIABLE = "GDXUNITY_UPDATE_AUDIT";
    private static final String BEGIN = "<!-- editability-audit:begin -->";
    private static final String END = "<!-- editability-audit:end -->";
    /** Tests run in the module folder; the project's assets and docs are one level up. */
    private static final File ASSETS = new File("../assets");
    private static final File MATRIX = new File("../docs/FEATURE_TEST_MATRIX.md");

    /**
     * Samples whose behaviour is still Java source the graph editor cannot show (roadmap R1: convert them to graphs, or accept them as
     * reference code). Closing a gap means taking the sample off this list.
     */
    private static final Set<String> LOGIC_GAPS = Set.of("arena-fps-3d.json", "blockworld-3d.json", "breakout-2d.json",
        "character-playground-3d.json", "collector-3d.json", "fighter-25d.json", "grapple-gorge-25d.json", "hopper-3d.json",
        "obby-3d.json", "physics-sandbox-3d.json", "pixel-adventure-2d.json", "platformer-2d.json", "platformer-profile-3d.json", "platformer-tiles-2d.json", "puzzle-3d.json",
        "racing-3d.json", "ruins-runner-25d.json", "runner-3d.json", "shooter-2d.json", "sprite-25d.json", "three-heroes-25d.json");
    /**
     * Asset types the samples use that no editor opens: glTF models have a Details editor on their component but no viewer of their own,
     * materials are edited in the Details of the surface that uses them until the Material Editor tab exists (roadmap R2), and terrains
     * are painted in the 3D view from the Details of the Terrain component (roadmap R3).
     */
    private static final Set<String> TYPES_WITHOUT_EDITOR = Set.of("model", "material", "terrain");

    @TempDir
    static Path temp;

    @BeforeAll
    static void init() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    private static List<EditabilityAudit.Row> audit() throws IOException {
        ComponentSerializerRegistry serializers = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(serializers);
        EditabilityAudit audit = new EditabilityAudit(ASSETS, serializers, EditorApplication.createDetailEditors(
            asset -> { }, new ModelAssets(ASSETS), (asset, entity) -> { }, new SculptSession(new SculptAssets(ASSETS)), asset -> { },
            new MaterialLibrary(AssetSource.directory(ASSETS)),
            new io.github.ldev22live.gdxunity.editor.terrain.TerrainSession(new io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary(AssetSource.directory(ASSETS))), ASSETS));
        return audit.auditAll(new File(ASSETS, "samples"));
    }

    @Test
    void shouldFindEveryComponentOfEverySampleEditableAndEveryNamedFileInPlace() throws IOException {
        List<EditabilityAudit.Row> rows = audit();

        assertTrue(rows.size() >= 38, "every sample is audited: " + rows.size());
        for (EditabilityAudit.Row row : rows) {
            assertEquals(List.of(), row.componentsNoEditor(), row.sample() + ": component types with no Details editor");
            assertEquals(List.of(), row.missingScripts(), row.sample() + ": scripts that do not exist");
            assertEquals(List.of(), row.missingAssets(), row.sample() + ": assets that do not exist");
        }
    }

    @Test
    void shouldListExactlyTheSamplesWhoseLogicIsStillJavaSource() throws IOException {
        Set<String> gaps = audit().stream().filter(EditabilityAudit.Row::isLogicGap).map(EditabilityAudit.Row::sample)
            .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(new TreeSet<>(LOGIC_GAPS), gaps,
            "a new Java-only sample needs a graph (or an entry here); a converted one must leave the list");
    }

    @Test
    void shouldListExactlyTheAssetTypesThatNothingOpens() throws IOException {
        Set<String> types = audit().stream().flatMap(row -> row.assetTypesNoEditor().stream())
            .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(new TreeSet<>(TYPES_WITHOUT_EDITOR), types);
    }

    @Test
    void shouldAgreeWithTheContentBrowserOnWhichAssetTypesOpenInAnEditor() {
        assertEquals(io.github.ldev22live.gdxunity.editor.core.asset.AssetOpening.WITH_EDITOR, EditabilityAudit.OPENING_EDITORS.keySet(),
            "the audit and the Content Browser's double-click must name the same editors");
    }

    @Test
    void shouldReadEveryMaterialFileOfTheProject() throws IOException {
        File[] files = new File(ASSETS, "materials").listFiles((dir, name) -> name.endsWith(".material"));

        assertTrue(files != null && files.length >= 6, "the showroom's materials exist");
        for (File file : files) {
            assertEquals(file.getName().replace(".material", ""), MaterialIO.fromJson(Files.readString(file.toPath())).getName(), file.getName());
        }
    }

    @Test
    void shouldFindEveryTextureTheProjectsMaterialsName() throws IOException {
        File[] files = new File(ASSETS, "materials").listFiles((dir, name) -> name.endsWith(".material"));

        assertTrue(files != null && files.length > 0);
        int named = 0;
        for (File file : files) {
            io.github.ldev22live.gdxunity.engine.material.Material material = MaterialIO.fromJson(Files.readString(file.toPath()));
            for (io.github.ldev22live.gdxunity.engine.material.Material.Slot slot
                : io.github.ldev22live.gdxunity.engine.material.Material.Slot.values()) {
                String texture = material.getTexture(slot);
                if (!texture.isEmpty()) {
                    named++;
                    assertTrue(new File(ASSETS, texture).isFile(), file.getName() + " " + slot + ": texture missing: " + texture);
                }
            }
        }
        assertTrue(named >= 4, "the showroom's textured materials name their textures: " + named);
    }

    @Test
    void shouldFlagAComponentWithoutADetailsEditorAndAMissingFile() throws IOException {
        Path scene = temp.resolve("broken.json");
        Files.writeString(scene, """
            {"schemaVersion":1,"name":"Broken","gameMode":"3d","active":true,"entities":[
              {"id":"a","name":"A","active":true,"parentId":null,"transform":{},"components":[
                {"type":"AudioSource","enabled":true,"data":{"cue":"x","sound":"sounds/missing.wav"}},
                {"type":"EntityScriptBindingComponent","enabled":true,"data":{"scriptPath":"scripts/nope.java","scriptClassName":"nope","language":"JAVA"}},
                {"type":"NoSuchComponent","enabled":true,"data":{}}]}]}""");
        ComponentSerializerRegistry serializers = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(serializers);
        EditabilityAudit audit = new EditabilityAudit(ASSETS, serializers, EditorApplication.createDetailEditors(
            asset -> { }, new ModelAssets(ASSETS), (asset, entity) -> { }, new SculptSession(new SculptAssets(ASSETS)), asset -> { },
            new MaterialLibrary(AssetSource.directory(ASSETS)),
            new io.github.ldev22live.gdxunity.editor.terrain.TerrainSession(new io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary(AssetSource.directory(ASSETS))), ASSETS));

        EditabilityAudit.Row row = audit.audit(scene.toFile());

        assertEquals(List.of("NoSuchComponent"), row.componentsNoEditor());
        assertEquals(List.of("scripts/nope.java"), row.missingScripts());
        assertEquals(List.of("sounds/missing.wav"), row.missingAssets());
        assertEquals(EditabilityAudit.Logic.JAVA, row.logic());
        assertTrue(row.hasDefects());
    }

    @Test
    void shouldKeepTheTableInTheFeatureMatrixUpToDate() throws IOException {
        String table = EditabilityAudit.toMarkdown(audit());
        String matrix = Files.readString(MATRIX.toPath());
        int begin = matrix.indexOf(BEGIN);
        int end = matrix.indexOf(END);
        assertTrue(begin >= 0 && end > begin, "FEATURE_TEST_MATRIX.md needs the markers " + BEGIN + " and " + END);
        String newline = matrix.contains("\r\n") ? "\r\n" : "\n";
        String expected = BEGIN + newline + table.replace("\n", newline) + END;
        String current = matrix.substring(begin, end + END.length());

        if ("1".equals(System.getenv(UPDATE_VARIABLE)) && !current.equals(expected)) {
            Files.writeString(MATRIX.toPath(), matrix.substring(0, begin) + expected + matrix.substring(end + END.length()));
            return;
        }
        assertEquals(expected, current, "the audit table in FEATURE_TEST_MATRIX.md is stale: run this test with " + UPDATE_VARIABLE + "=1");
        assertFalse(table.isBlank());
    }
}
