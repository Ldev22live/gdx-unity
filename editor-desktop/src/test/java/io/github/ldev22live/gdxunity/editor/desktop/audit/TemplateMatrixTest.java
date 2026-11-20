package io.github.ldev22live.gdxunity.editor.desktop.audit;

import io.github.ldev22live.gdxunity.editor.desktop.EditorApplication;
import io.github.ldev22live.gdxunity.editor.desktop.audit.TemplateMatrix.Domain;
import io.github.ldev22live.gdxunity.editor.desktop.audit.TemplateMatrix.Status;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptAssets;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptSession;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.desktop.GameComponentSerializers;
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
 * The sample-to-template matrix of Appendix F5 (milestone M1.1): each rule on a small scene written for it, the real samples against
 * the editability audit, and the table kept in {@code docs/FEATURE_TEST_MATRIX.md}. Refresh the table with
 * {@code GDXUNITY_UPDATE_AUDIT=1}, as for the audit.
 */
class TemplateMatrixTest {
    private static final String BEGIN = "<!-- template-matrix:begin -->";
    private static final String END = "<!-- template-matrix:end -->";
    private static final File ASSETS = new File("../assets");
    private static final File MATRIX = new File("../docs/FEATURE_TEST_MATRIX.md");

    @TempDir
    Path root;

    @BeforeAll
    static void init() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    private static EditabilityAudit.Row row(EditabilityAudit.Logic logic, boolean defect) {
        return new EditabilityAudit.Row("s.json", 1, 1, defect ? List.of("X") : List.of(), logic, List.of(), List.of(), List.of());
    }

    private TemplateMatrix.Entry judge(EditabilityAudit.Row audit, String... components) throws IOException {
        Path scene = root.resolve("s.json");
        Files.writeString(scene, "{\"schemaVersion\":1,\"name\":\"S\",\"entities\":[{\"id\":\"a\",\"name\":\"A\",\"active\":true,"
            + "\"parentId\":null,\"transform\":{},\"components\":[" + String.join(",", components) + "]}]}");
        return TemplateMatrix.evaluate(root.toFile(), scene.toFile(), audit);
    }

    private static String component(String type, String data) {
        return "{\"type\":\"" + type + "\",\"enabled\":true,\"data\":{" + data + "}}";
    }

    private void touch(String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }

    private static Status status(TemplateMatrix.Entry entry, Domain domain) {
        return entry.cells().get(domain).status();
    }

    @Test
    void shouldFailLogicAndGameplayWhenBehaviourIsJavaSourcePassWhenItIsGraphsAndSkipWhenThereIsNone() throws IOException {
        String body = component("BoxCollider3DComponent", "");

        TemplateMatrix.Entry java = judge(row(EditabilityAudit.Logic.JAVA, false), body);
        assertEquals(Status.FAIL, status(java, Domain.LOGIC));
        assertEquals(Status.FAIL, status(java, Domain.GAMEPLAY));
        assertEquals("behaviour is Java source", java.cells().get(Domain.LOGIC).evidence());
        assertEquals(Status.FAIL, status(judge(row(EditabilityAudit.Logic.JAVA_AND_GRAPH, false), body), Domain.LOGIC));

        TemplateMatrix.Entry graph = judge(row(EditabilityAudit.Logic.GRAPH, false), body);
        assertEquals(Status.PASS, status(graph, Domain.LOGIC));
        assertEquals(Status.PASS, status(graph, Domain.GAMEPLAY));

        TemplateMatrix.Entry none = judge(row(EditabilityAudit.Logic.NONE, false), component("TextComponent", ""));
        assertEquals(Status.NOT_APPLICABLE, status(none, Domain.LOGIC));
        assertEquals(Status.NOT_APPLICABLE, status(none, Domain.GAMEPLAY));
        assertEquals(Status.PASS, status(none, Domain.UI));
    }

    @Test
    void shouldFailTheWorldWhenTheAuditFoundADefectAndPassOtherwise() throws IOException {
        assertEquals(Status.FAIL, status(judge(row(EditabilityAudit.Logic.NONE, true)), Domain.WORLD));
        assertEquals(Status.PASS, status(judge(row(EditabilityAudit.Logic.NONE, false)), Domain.WORLD));
    }

    @Test
    void shouldFailAGroundThatIsABakedModelAndPassATerrainFileThatExists() throws IOException {
        TemplateMatrix.Entry baked = judge(row(EditabilityAudit.Logic.NONE, false),
            component("ModelComponent", "\"model\":\"models/landscape/island-terrain.gltf\""));
        assertEquals(Status.FAIL, status(baked, Domain.TERRAIN));
        assertTrue(baked.cells().get(Domain.TERRAIN).evidence().contains("island-terrain"));
        assertEquals(Status.NOT_APPLICABLE, status(baked, Domain.MODELS_3D), "the ground alone is not a 3D model to edit");

        touch("terrains/Valley.terrain");
        assertEquals(Status.PASS, status(judge(row(EditabilityAudit.Logic.NONE, false),
            component("Terrain", "\"terrainPath\":\"terrains/Valley.terrain\"")), Domain.TERRAIN));
        assertEquals(Status.FAIL, status(judge(row(EditabilityAudit.Logic.NONE, false),
            component("Terrain", "\"terrainPath\":\"terrains/Gone.terrain\"")), Domain.TERRAIN));
        assertEquals(Status.NOT_APPLICABLE, status(judge(row(EditabilityAudit.Logic.NONE, false)), Domain.TERRAIN));
    }

    @Test
    void shouldFailAGltfModelBecauseThereIsNoMeshEditorAndPassASculpt() throws IOException {
        assertEquals(Status.FAIL, status(judge(row(EditabilityAudit.Logic.NONE, false),
            component("ModelComponent", "\"model\":\"characters/Tall_Man.gltf\"")), Domain.MODELS_3D));
        assertEquals(Status.PASS, status(judge(row(EditabilityAudit.Logic.NONE, false),
            component("DynamicMeshComponent", "")), Domain.MODELS_3D));
    }

    @Test
    void shouldFailFlattenedImagesAndMissingSpritesAndPassSpriteFiles() throws IOException {
        touch("sprites/hero.sprite");
        assertEquals(Status.PASS, status(judge(row(EditabilityAudit.Logic.NONE, false),
            component("SpriteAnimatorComponent", "\"assetPath\":\"sprites/hero.sprite\"")), Domain.ART_2D));
        TemplateMatrix.Entry flat = judge(row(EditabilityAudit.Logic.NONE, false),
            component("TilemapComponent", "\"tileSet\":\"art/tiles.png\""));
        assertEquals(Status.FAIL, status(flat, Domain.ART_2D));
        assertTrue(flat.cells().get(Domain.ART_2D).evidence().contains("flattened"));
        assertEquals(Status.FAIL, status(judge(row(EditabilityAudit.Logic.NONE, false),
            component("SpriteAnimatorComponent", "\"assetPath\":\"sprites/gone.sprite\"")), Domain.ART_2D));
        assertEquals(Status.NOT_APPLICABLE, status(judge(row(EditabilityAudit.Logic.NONE, false)), Domain.ART_2D));
    }

    @Test
    void shouldFailAnimationBakedIntoAModelOrMissingAndPassClipsAndSpriteAnimators() throws IOException {
        touch("animations/Bob.anim");
        assertEquals(Status.PASS, status(judge(row(EditabilityAudit.Logic.NONE, false),
            component("AnimatorComponent", "\"clip\":\"animations/Bob.anim\"")), Domain.ANIMATION));
        assertEquals(Status.FAIL, status(judge(row(EditabilityAudit.Logic.NONE, false),
            component("AnimatorComponent", "\"clip\":\"animations/Gone.anim\"")), Domain.ANIMATION));
        TemplateMatrix.Entry baked = judge(row(EditabilityAudit.Logic.NONE, false),
            component("ModelComponent", "\"model\":\"characters/Tall_Man.gltf\",\"animation\":\"Walk\""));
        assertEquals(Status.FAIL, status(baked, Domain.ANIMATION));
        assertTrue(baked.cells().get(Domain.ANIMATION).evidence().contains("baked"));
        assertEquals(Status.NOT_APPLICABLE, status(judge(row(EditabilityAudit.Logic.NONE, false)), Domain.ANIMATION));
    }

    @Test
    void shouldPassASynthesizedCueFailARecordedClipAndPassAnEngineSound() throws IOException {
        assertEquals(Status.PASS, status(judge(row(EditabilityAudit.Logic.NONE, false),
            component("AudioSource", "\"cue\":\"shoot\",\"sound\":\"Laser\"")), Domain.SOUND));
        TemplateMatrix.Entry clip = judge(row(EditabilityAudit.Logic.NONE, false),
            component("AudioSource", "\"cue\":\"door\",\"sound\":\"sounds/door.wav\""));
        assertEquals(Status.FAIL, status(clip, Domain.SOUND));
        assertTrue(clip.cells().get(Domain.SOUND).evidence().contains("recorded clip"));
        assertEquals(Status.PASS, status(judge(row(EditabilityAudit.Logic.NONE, false),
            component("EngineSound", "\"profile\":\"Hatchback\"")), Domain.SOUND));
    }

    @Test
    void shouldPassASongThatExistsAndFailOneThatDoesNot() throws IOException {
        touch("music/theme.song");
        assertEquals(Status.PASS, status(judge(row(EditabilityAudit.Logic.NONE, false),
            component("MusicSource", "\"song\":\"music/theme.song\"")), Domain.MUSIC));
        assertEquals(Status.FAIL, status(judge(row(EditabilityAudit.Logic.NONE, false),
            component("MusicSource", "\"song\":\"music/gone.song\"")), Domain.MUSIC));
        assertEquals(Status.NOT_APPLICABLE, status(judge(row(EditabilityAudit.Logic.NONE, false)), Domain.MUSIC));
    }

    @Test
    void shouldCountMaterialsAndLightsAsEditableAndNeverCallASampleFullyCustomizableWhileGatesAreUnchecked() throws IOException {
        TemplateMatrix.Entry lit = judge(row(EditabilityAudit.Logic.NONE, false), component("DirectionalLightComponent", ""));
        assertEquals(Status.PASS, status(lit, Domain.MATERIALS));
        assertTrue(lit.failures().isEmpty());
        assertEquals(TemplateMatrix.Label.CHECKS_PENDING, lit.label(), "never Fully Customizable while gates are unchecked");
        assertTrue(lit.flattened().isEmpty());

        TemplateMatrix.Entry java = judge(row(EditabilityAudit.Logic.JAVA, false), component("BoxCollider3DComponent", ""));
        assertEquals(TemplateMatrix.Label.PARTIALLY_EDITABLE, java.label());
        assertEquals("Technical Demo / Partially Editable", java.label().text());
        assertEquals(List.of(Domain.LOGIC, Domain.GAMEPLAY), java.failures());
        assertTrue(TemplateMatrix.UNCHECKED_GATES.size() >= 6);
        assertEquals(Domain.values().length, java.cells().size(), "every domain has a result");
    }

    @Test
    void shouldLabelFlattenedContentWithNoEditableReplacementIncompleteAndAGltfPropPartiallyEditable() throws IOException {
        TemplateMatrix.Entry prop = judge(row(EditabilityAudit.Logic.NONE, false),
            component("ModelComponent", "\"model\":\"models/cabin.gltf\""));
        assertEquals(TemplateMatrix.Label.PARTIALLY_EDITABLE, prop.label(), "a glTF prop can be sculpted as a project-owned copy");
        assertEquals(List.of(new TemplateMatrix.Flattened(Domain.MODELS_3D, "glTF models", TemplateMatrix.SCULPT_COPY)), prop.flattened());

        TemplateMatrix.Entry ground = judge(row(EditabilityAudit.Logic.NONE, false),
            component("ModelComponent", "\"model\":\"models/hills-terrain.gltf\""));
        assertEquals(TemplateMatrix.Label.INCOMPLETE, ground.label(), "a baked ground has no conversion to a .terrain");
        assertEquals("baked glTF ground (hills-terrain): no editable replacement in the template", ground.flattened().get(0).describe());

        assertEquals(TemplateMatrix.Label.INCOMPLETE, judge(row(EditabilityAudit.Logic.GRAPH, false),
            component("ModelComponent", "\"model\":\"characters/Tall_Man.gltf\",\"animation\":\"Walk\"")).label(), "a baked clip");
        assertEquals(TemplateMatrix.Label.INCOMPLETE, judge(row(EditabilityAudit.Logic.NONE, false),
            component("TilemapComponent", "\"tileSet\":\"art/tiles.png\"")).label(), "a flattened image");
        assertEquals(TemplateMatrix.Label.INCOMPLETE, judge(row(EditabilityAudit.Logic.NONE, false),
            component("AudioSource", "\"cue\":\"door\",\"sound\":\"sounds/door.wav\"")).label(), "a recorded clip");

        TemplateMatrix.Entry both = judge(row(EditabilityAudit.Logic.JAVA, false), component("ModelComponent",
            "\"model\":\"models/hills-terrain.gltf\""), component("ModelComponent", "\"model\":\"models/cabin.gltf\""));
        assertEquals(TemplateMatrix.Label.INCOMPLETE, both.label(), "INCOMPLETE outranks a Java-source failure");
        assertEquals(List.of(Domain.TERRAIN, Domain.MODELS_3D), both.flattened().stream().map(TemplateMatrix.Flattened::domain).toList());
    }

    @Test
    void shouldLabelTheShippedSamplesFromTheMatrixWithNoneFullyCustomizable() throws IOException {
        List<TemplateMatrix.Entry> entries = TemplateMatrix.evaluateAll(ASSETS, new File(ASSETS, "samples"), audit());
        Set<String> incomplete = entries.stream().filter(e -> e.label() == TemplateMatrix.Label.INCOMPLETE)
            .map(TemplateMatrix.Entry::sample).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of("character-creator-3d.json", "landscape-3d.json", "landscape-alpine-3d.json", "landscape-autumn-3d.json",
            "landscape-desert-3d.json", "landscape-island-3d.json", "physics-mesh-3d.json"), incomplete,
            "baked grounds and clips baked into a glTF");
        for (TemplateMatrix.Entry entry : entries) {
            assertFalse(entry.label() == TemplateMatrix.Label.FULLY_CUSTOMIZABLE, entry.sample());
            assertEquals(entry.failures().isEmpty(), entry.label() == TemplateMatrix.Label.CHECKS_PENDING, entry.sample());
        }
        assertEquals(TemplateMatrix.Label.PARTIALLY_EDITABLE,
            entries.stream().filter(e -> e.sample().equals("sculpted-rock-3d.json")).findFirst().orElseThrow().label(),
            "a glTF rock alone can be sculpted as a copy");
    }

    @Test
    void shouldFailExactlyTheSamplesWhoseLogicIsJavaSourceAndPassTheWorldOfEverySample() throws IOException {
        List<EditabilityAudit.Row> rows = audit();
        List<TemplateMatrix.Entry> entries = TemplateMatrix.evaluateAll(ASSETS, new File(ASSETS, "samples"), rows);

        assertEquals(rows.size(), entries.size());
        Set<String> javaSamples = rows.stream().filter(EditabilityAudit.Row::isLogicGap).map(EditabilityAudit.Row::sample)
            .collect(Collectors.toCollection(TreeSet::new));
        Set<String> failingLogic = entries.stream().filter(e -> status(e, Domain.LOGIC) == Status.FAIL).map(TemplateMatrix.Entry::sample)
            .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(javaSamples, failingLogic);
        for (TemplateMatrix.Entry entry : entries) {
            assertEquals(Status.PASS, status(entry, Domain.WORLD), entry.sample());
        }
    }

    @Test
    void shouldFailTheGroundOfTheFiveLandscapeScenesAndPassTheTerrainSample() throws IOException {
        List<TemplateMatrix.Entry> entries = TemplateMatrix.evaluateAll(ASSETS, new File(ASSETS, "samples"), audit());

        for (TemplateMatrix.Entry entry : entries) {
            if (entry.sample().startsWith("landscape-")) {
                assertEquals(Status.FAIL, status(entry, Domain.TERRAIN), entry.sample() + ": its ground is a generated model");
            }
            if (entry.sample().equals("terrain-3d.json")) {
                assertEquals(Status.PASS, status(entry, Domain.TERRAIN), "the terrain sample has a .terrain");
            }
        }
    }

    @Test
    void shouldKeepTheTableInTheFeatureMatrixUpToDate() throws IOException {
        String table = TemplateMatrix.toMarkdown(TemplateMatrix.evaluateAll(ASSETS, new File(ASSETS, "samples"), audit()));
        String matrix = Files.readString(MATRIX.toPath());
        int begin = matrix.indexOf(BEGIN);
        int end = matrix.indexOf(END);
        assertTrue(begin >= 0 && end > begin, "FEATURE_TEST_MATRIX.md needs the markers " + BEGIN + " and " + END);
        String newline = matrix.contains("\r\n") ? "\r\n" : "\n";
        String expected = BEGIN + newline + table.replace("\n", newline) + END;
        String current = matrix.substring(begin, end + END.length());

        if ("1".equals(System.getenv(EditabilityAuditTest.UPDATE_VARIABLE)) && !current.equals(expected)) {
            Files.writeString(MATRIX.toPath(), matrix.substring(0, begin) + expected + matrix.substring(end + END.length()));
            return;
        }
        assertEquals(expected, current, "the template table in FEATURE_TEST_MATRIX.md is stale: run this test with "
            + EditabilityAuditTest.UPDATE_VARIABLE + "=1");
    }

    private static List<EditabilityAudit.Row> audit() throws IOException {
        return editorAudit().auditAll(new File(ASSETS, "samples"));
    }

    /** The editability audit with the editor's own serializers and Details editors, as the editor builds it. */
    static EditabilityAudit editorAudit() {
        return editorAudit(ASSETS);
    }

    /** The same audit for another asset root (a test's own project, MP.1). */
    static EditabilityAudit editorAudit(File assets) {
        ComponentSerializerRegistry serializers = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(serializers);
        return new EditabilityAudit(assets, serializers, EditorApplication.createDetailEditors(
            asset -> { }, new ModelAssets(assets), (asset, entity) -> { }, new SculptSession(new SculptAssets(assets)), asset -> { },
            new MaterialLibrary(AssetSource.directory(assets)),
            new io.github.ldev22live.gdxunity.editor.terrain.TerrainSession(
                new io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary(AssetSource.directory(assets))), assets));
    }
}
