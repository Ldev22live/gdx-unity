package io.github.ldev22live.gdxunity.editor.desktop.audit;

import io.github.ldev22live.gdxunity.editor.desktop.audit.TemplateAudit.Gate;
import io.github.ldev22live.gdxunity.editor.desktop.audit.TemplateMatrix.Status;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fully editable template audit of roadmap MT.8: its rules on a small project written for them, what it says about the shipped
 * samples and graphs, and the tables in {@code docs/reports/fully-editable-template-audit.md}, refreshed with
 * {@code GDXUNITY_UPDATE_AUDIT=1} as for the M1.1 audit.
 */
class TemplateAuditTest {
    private static final File ASSETS = new File("../assets");
    private static final File REPOSITORY = new File("..");
    private static final File REPORT = new File("../docs/reports/fully-editable-template-audit.md");

    @TempDir
    Path project;

    @BeforeAll
    static void init() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    private void write(String relative, String text) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static String scene(String... components) {
        return "{\"schemaVersion\":1,\"name\":\"Test\",\"gameMode\":\"3d\",\"entities\":[{\"id\":\"a\",\"name\":\"A\",\"active\":true,"
            + "\"parentId\":null,\"transform\":{},\"components\":[" + String.join(",", components) + "]}]}";
    }

    private static String component(String type, String data) {
        return "{\"type\":\"" + type + "\",\"enabled\":true,\"data\":{" + data + "}}";
    }

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        return new SceneSerializer(registry);
    }

    private List<TemplateAudit.SampleRow> audit(File assets, File repository, Set<String> web, Set<String> baseline) throws IOException {
        List<EditabilityAudit.Row> rows = TemplateMatrixTest.editorAudit(assets).auditAll(new File(assets, "samples"));
        List<TemplateMatrix.Entry> matrix = TemplateMatrix.evaluateAll(assets, new File(assets, "samples"), rows);
        SampleInventory inventory = new SampleInventory(new SampleInventory.Sources(repository, assets, web, baseline), serializer());
        return new TemplateAudit(assets).samples(rows, matrix, inventory.scenes(rows, matrix));
    }

    private static Status status(TemplateAudit.SampleRow row, Gate gate) {
        return row.gates().get(gate).status();
    }

    @Test
    void shouldFailJavaLogicABrokenReferenceABakedGroundAndAnInvalidGraph() throws IOException {
        write("assets/samples/a-3d.json", scene(
            component("EntityScriptBindingComponent", "\"scriptPath\":\"scripts/Gone.java\",\"scriptClassName\":\"Gone\",\"language\":\"JAVA\""),
            component("ModelComponent", "\"model\":\"models/hills-terrain.gltf\""),
            component("GraphScriptComponent", "\"graphPath\":\"graphs/Bad.graph\"")));
        write("assets/models/hills-terrain.gltf", "{\"asset\":{\"version\":\"2.0\"}}");
        write("assets/graphs/Bad.graph", "{\"schemaVersion\":2,\"kind\":\"graph\",\"nodes\":[{\"id\":\"n\",\"type\":\"NoSuchNode\","
            + "\"x\":0,\"y\":0}],\"links\":[]}");

        TemplateAudit.SampleRow row = audit(project.resolve("assets").toFile(), project.toFile(), Set.of(), Set.of()).get(0);

        assertEquals(Status.FAIL, status(row, Gate.LOGIC));
        assertEquals(Status.FAIL, status(row, Gate.REFERENCES), row.gates().get(Gate.REFERENCES).reason());
        assertTrue(row.gates().get(Gate.REFERENCES).reason().contains("scripts/Gone.java"));
        assertEquals(Status.FAIL, status(row, Gate.BAKED), "a baked glTF ground has no editable replacement");
        assertEquals(Status.FAIL, status(row, Gate.SOURCE));
        assertEquals(Status.FAIL, status(row, Gate.GRAPHS), row.gates().get(Gate.GRAPHS).reason());
    }

    @Test
    void shouldBlockWhatNothingCheckedAndPassNothingByDefault() throws IOException {
        write("assets/samples/a-3d.json", scene(component("DirectionalLightComponent", "")));

        TemplateAudit.SampleRow row = audit(project.resolve("assets").toFile(), project.toFile(), Set.of(), Set.of()).get(0);

        assertEquals(Status.NOT_APPLICABLE, status(row, Gate.LOGIC), "no scripts or graphs");
        assertEquals(Status.NOT_APPLICABLE, status(row, Gate.GRAPHS));
        assertEquals(Status.BLOCKED, status(row, Gate.PLAY), "not in a gallery, so no test played it");
        assertEquals(Status.BLOCKED, status(row, Gate.PROJECT));
        for (Gate gate : List.of(Gate.EXPORT_LAUNCH, Gate.USER_EDIT, Gate.LICENCE, Gate.OBSERVED)) {
            assertEquals(Status.BLOCKED, status(row, gate), gate.label());
        }
        assertTrue(row.open().contains(Gate.OBSERVED));
    }

    @Test
    void shouldPassThePlayAndProjectGatesOfEveryShippedSampleAndCallNoneComplete() throws IOException {
        List<TemplateAudit.SampleRow> rows = realSamples();

        assertEquals(new File(ASSETS, "samples").list((dir, name) -> name.endsWith(".json")).length, rows.size());
        for (TemplateAudit.SampleRow row : rows) {
            for (Gate gate : List.of(Gate.SERIALIZATION, Gate.PLAY, Gate.PROJECT, Gate.REFERENCES, Gate.REGISTRATION)) {
                assertEquals(Status.PASS, status(row, gate), row.sample() + " " + gate.label());
            }
            assertFalse(row.open().isEmpty(), row.sample() + " cannot pass every gate while the observed checks are pending");
            assertFalse(row.label().equals("Fully Customizable"), row.sample());
        }
        Set<String> javaLogic = rows.stream().filter(r -> status(r, Gate.LOGIC) == Status.FAIL).map(TemplateAudit.SampleRow::sample)
            .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(21, javaLogic.size(), javaLogic.toString());
    }

    @Test
    void shouldLoadAndValidateEveryGraphAndFindTheSixThatMatchTheirScripts() throws IOException {
        List<TemplateAudit.GraphRow> graphs = new TemplateAudit(ASSETS).graphs(realInventory());

        assertEquals(20, graphs.size());
        for (TemplateAudit.GraphRow graph : graphs) {
            assertEquals(Status.PASS, graph.loads().status(), graph.graph());
            assertEquals(Status.PASS, graph.validates().status(), graph.graph() + ": " + graph.validates().reason());
        }
        Set<String> matching = graphs.stream().filter(g -> g.parity().status() == Status.PASS).map(TemplateAudit.GraphRow::graph)
            .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of("graphs/samples/BreakoutPaddle.graph", "graphs/samples/CollectorPlayer.graph",
            "graphs/samples/RunnerPlayer.graph", "graphs/samples/SandboxKick.graph", "graphs/samples/ShooterShip.graph",
            "graphs/samples/WalkerPlayer.graph"), matching, "the six of M1.2, each compared with its script by a test");
    }

    @Test
    void shouldKeepTheTablesInTheAuditReportUpToDate() throws IOException {
        List<SampleInventory.SceneRow> inventory = realInventory();
        List<TemplateAudit.SampleRow> rows = realSamples();
        String report = Files.readString(REPORT.toPath(), StandardCharsets.UTF_8);
        String updated = replace(report, "counts", TemplateAudit.countsMarkdown(rows));
        updated = replace(updated, "samples", TemplateAudit.samplesMarkdown(rows));
        updated = replace(updated, "graphs", TemplateAudit.graphsMarkdown(new TemplateAudit(ASSETS).graphs(inventory)));

        if ("1".equals(System.getenv(EditabilityAuditTest.UPDATE_VARIABLE)) && !updated.equals(report)) {
            Files.writeString(REPORT.toPath(), updated, StandardCharsets.UTF_8);
            return;
        }
        assertEquals(updated, report, "the tables in fully-editable-template-audit.md are stale: run this test with "
            + EditabilityAuditTest.UPDATE_VARIABLE + "=1");
    }

    private static String replace(String text, String name, String table) {
        String begin = "<!-- template-audit:" + name + ":begin -->";
        String end = "<!-- template-audit:" + name + ":end -->";
        int from = text.indexOf(begin);
        int to = text.indexOf(end);
        assertTrue(from >= 0 && to > from, "the report needs the markers " + begin + " and " + end);
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        return text.substring(0, from) + begin + newline + table.replace("\n", newline) + text.substring(to);
    }

    private List<TemplateAudit.SampleRow> realSamples() throws IOException {
        return audit(ASSETS, REPOSITORY, web(), baseline());
    }

    private static List<SampleInventory.SceneRow> realInventory() throws IOException {
        List<EditabilityAudit.Row> rows = TemplateMatrixTest.editorAudit().auditAll(new File(ASSETS, "samples"));
        List<TemplateMatrix.Entry> matrix = TemplateMatrix.evaluateAll(ASSETS, new File(ASSETS, "samples"), rows);
        return new SampleInventory(new SampleInventory.Sources(REPOSITORY, ASSETS, web(), baseline()), serializer()).scenes(rows, matrix);
    }

    private static Set<String> web() throws IOException {
        return SampleInventory.babylonComponents(
            REPOSITORY.toPath().resolve("engine-babylon").resolve("src").resolve("runtime").resolve("SceneBuilder.ts"));
    }

    private static Set<String> baseline() throws IOException {
        return SampleInventory.baselineScenes(REPOSITORY.toPath().resolve("docs").resolve("performance").resolve("baseline-windows.tsv"));
    }
}
