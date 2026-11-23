package io.github.ldev22live.gdxunity.editor.desktop.audit;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphValidator;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The fully editable template audit of roadmap MT.8 (Appendix F5 and F7): for every sample, each release gate as PASS, FAIL, BLOCKED or
 * NOT APPLICABLE with its reason; for every graph, whether it loads, validates and matches the Java script it mirrors.
 * <p>
 * It adds no judgement of its own where one exists: the editors and flattened content come from the M1.1 {@link TemplateMatrix}, logic
 * and editor registration from the {@link EditabilityAudit}, references and licences from the MP.1 {@link SampleInventory}. The gates
 * covered by tests that go through every sample (serialization, Play, duplication and export of the project) are PASS with that test
 * as evidence, only for samples those tests reach. A gate nothing has checked is BLOCKED, never PASS: a person's edit with undo in each
 * domain, launching each exported game, the licence of project-made assets (the owner's decision) and observed graphics and sound.
 */
public final class TemplateAudit {
    /** The gates, in the order of Appendix F5, with the issues Appendix F7 names. */
    public enum Gate {
        SOURCE("Source present"), EDITORS("Editors open (M1.1)"), LOGIC("No hardcoded-only logic"), BAKED("No baked-only content"),
        REGISTRATION("Editors registered"), REFERENCES("References resolve"), GRAPHS("Graphs valid"),
        SERIALIZATION("Save and reopen"), PLAY("Play"), PROJECT("Duplicate and export"), EXPORT_LAUNCH("Exported game launches"),
        USER_EDIT("Edit, undo and redo per domain"), LICENCE("Licence verified"), OBSERVED("Seen and heard");

        private final String label;

        Gate(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** One gate's result: the status and why. */
    public record Result(TemplateMatrix.Status status, String reason) {
        public Result {
            Objects.requireNonNull(status, "status cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
        }

        String cell() {
            return reason.isEmpty() ? status.text() : status.text() + ": " + reason;
        }
    }

    /** One sample's gates, in {@link Gate} order. */
    public record SampleRow(String sample, String label, Map<Gate, Result> gates) {
        /** Gates that are not PASS or NOT APPLICABLE. */
        public List<Gate> open() {
            List<Gate> open = new ArrayList<>();
            gates.forEach((gate, result) -> {
                if (result.status() == TemplateMatrix.Status.FAIL || result.status() == TemplateMatrix.Status.BLOCKED) {
                    open.add(gate);
                }
            });
            return open;
        }
    }

    /**
     * One graph.
     *
     * @param graph     path under the asset root
     * @param usedBy    samples whose scene runs it
     * @param loads     PASS, or FAIL with the reason
     * @param validates PASS, or FAIL with the validator's first problems
     * @param mirrors   the Java script it re-creates, or "-" for a graph of its own
     * @param parity    PASS when it covers its script entirely (and a test compares them), FAIL with what it leaves out, n/a for none
     */
    public record GraphRow(String graph, List<String> usedBy, Result loads, Result validates, String mirrors, Result parity) {
    }

    /** What the evidence tests reach: the gallery's scenes, which the gallery projects test opens, plays and exports. */
    private final TreeSet<String> galleryScenes = new TreeSet<>();
    private final File assetsRoot;
    private final GraphValidator validator = new GraphValidator(NodeRegistry.createDefault());
    private final Map<String, JsonValue> graphExamples = new LinkedHashMap<>();

    public TemplateAudit(File assetsRoot) throws IOException {
        this.assetsRoot = Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null");
        File gallery = new File(assetsRoot, "templates/gallery.json");
        if (gallery.isFile()) {
            JsonValue templates = new JsonReader().parse(Files.readString(gallery.toPath(), StandardCharsets.UTF_8)).get("templates");
            for (JsonValue t = templates.child; t != null; t = t.next) {
                galleryScenes.add(t.getString("scene"));
            }
        }
        File examples = new File(assetsRoot, "graphs/samples/examples.json");
        if (examples.isFile()) {
            JsonValue list = new JsonReader().parse(Files.readString(examples.toPath(), StandardCharsets.UTF_8)).get("examples");
            for (JsonValue e = list.child; e != null; e = e.next) {
                graphExamples.put("graphs/" + e.getString("graph"), e);
            }
        }
    }

    /** Every sample's gates, from the M1.1 matrix and audit and the MP.1 inventory of the same samples. */
    public List<SampleRow> samples(List<EditabilityAudit.Row> audit, List<TemplateMatrix.Entry> matrix,
                                   List<SampleInventory.SceneRow> inventory) throws IOException {
        List<SampleRow> rows = new ArrayList<>();
        for (SampleInventory.SceneRow scene : inventory) {
            EditabilityAudit.Row auditRow = find(audit, r -> r.sample().equals(scene.sample()), scene.sample());
            TemplateMatrix.Entry entry = find(matrix, e -> e.sample().equals(scene.sample()), scene.sample());
            rows.add(sample(scene, auditRow, entry));
        }
        return rows;
    }

    private static <T> T find(List<T> list, java.util.function.Predicate<T> match, String sample) {
        return list.stream().filter(match).findFirst().orElseThrow(() -> new IllegalArgumentException("Nothing for " + sample));
    }

    private SampleRow sample(SampleInventory.SceneRow scene, EditabilityAudit.Row audit, TemplateMatrix.Entry entry) throws IOException {
        Map<Gate, Result> gates = new LinkedHashMap<>();
        List<String> noSource = new ArrayList<>(scene.missing());
        for (TemplateMatrix.Flattened item : entry.flattened()) {
            if (item.hasNoReplacement()) {
                noSource.add(item.content());
            }
        }
        gates.put(Gate.SOURCE, noSource.isEmpty() ? pass("") : fail(String.join(", ", noSource)));

        List<String> failed = entry.failures().stream().map(TemplateMatrix.Domain::label).toList();
        gates.put(Gate.EDITORS, failed.isEmpty() ? pass("") : fail(String.join("; ", failed)));

        gates.put(Gate.LOGIC, switch (audit.logic()) {
            case NONE -> notApplicable();
            case GRAPH -> pass("graphs");
            case JAVA, JAVA_AND_GRAPH -> fail("Java source: " + scene.behaviour().replace("Java ", ""));
        });

        if (entry.flattened().isEmpty()) {
            gates.put(Gate.BAKED, notApplicable());
        } else {
            List<String> baked = new ArrayList<>();
            boolean anyWithout = false;
            for (TemplateMatrix.Flattened item : entry.flattened()) {
                baked.add(item.describe());
                anyWithout |= item.hasNoReplacement();
            }
            // Appendix F1: flattened content passes only with a project-owned editable replacement workflow
            gates.put(Gate.BAKED, anyWithout ? fail(String.join("; ", baked)) : pass(String.join("; ", baked)));
        }

        // A component with no Details editor. File types without an editor of their own (models, images) are the Editors and Baked
        // gates' findings; a terrain is edited in World: Terrain, a material in Details, so neither counts here
        List<String> unregistered = audit.componentsNoEditor();
        gates.put(Gate.REGISTRATION, unregistered.isEmpty() ? pass("") : fail(String.join(", ", unregistered)));

        List<String> broken = new ArrayList<>(audit.missingScripts());
        broken.addAll(audit.missingAssets());
        broken.addAll(scene.missing().stream().filter(m -> !broken.contains(m)).toList());
        gates.put(Gate.REFERENCES, broken.isEmpty() ? pass("") : fail(String.join(", ", broken)));

        gates.put(Gate.GRAPHS, graphsOf(scene.sample()));
        gates.put(Gate.SERIALIZATION, scene.load().equals("loads") ? pass("every-sample tests") : fail(scene.load()));

        boolean inGallery = galleryScenes.contains("samples/" + scene.sample());
        gates.put(Gate.PLAY, inGallery ? pass("2 s in the game session, every-sample test") : blocked("not in the gallery, not played"));
        gates.put(Gate.PROJECT, inGallery ? pass("project made and exported by test") : blocked("not in the gallery"));
        gates.put(Gate.EXPORT_LAUNCH, blocked("not launched"));
        gates.put(Gate.USER_EDIT, blocked("not run per domain"));

        boolean allCc0 = scene.licences().stream().allMatch(l -> l.startsWith("CC0 1.0"));
        gates.put(Gate.LICENCE, allCc0 ? pass("CC0 with a licence file")
            : blocked("project-made, no licence record (owner)"));
        gates.put(Gate.OBSERVED, blocked("pending a person"));
        return new SampleRow(scene.sample(), entry.label().text(), gates);
    }

    /** The graphs a scene runs: PASS when each loads and validates, n/a when it has none. */
    private Result graphsOf(String sample) throws IOException {
        JsonValue root = new JsonReader().parse(Files.readString(new File(assetsRoot, "samples/" + sample).toPath(),
            StandardCharsets.UTF_8));
        TreeSet<String> graphs = new TreeSet<>();
        collectGraphs(root.get("entities"), graphs);
        if (graphs.isEmpty()) {
            return notApplicable();
        }
        List<String> problems = new ArrayList<>();
        for (String graph : graphs) {
            Result loaded = load(graph);
            if (loaded.status() != TemplateMatrix.Status.PASS) {
                problems.add(graph + ": " + loaded.reason());
            }
        }
        return problems.isEmpty() ? pass(graphs.size() + " valid") : fail(String.join("; ", problems));
    }

    private static void collectGraphs(JsonValue value, TreeSet<String> graphs) {
        if (value.isString()) {
            if (value.asString().endsWith(".graph")) {
                graphs.add(value.asString());
            }
            return;
        }
        for (JsonValue child = value.child; child != null; child = child.next) {
            collectGraphs(child, graphs);
        }
    }

    /** Loads and validates one graph: PASS, or FAIL with the reason. */
    private Result load(String path) throws IOException {
        File file = new File(assetsRoot, path);
        if (!file.isFile()) {
            return fail("missing");
        }
        Graph graph;
        try {
            graph = GraphSerializer.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return fail("does not load: " + e.getMessage());
        }
        List<GraphValidator.Problem> problems = validator.validate(graph);
        if (!problems.isEmpty()) {
            return fail(problems.size() + " problem(s), first: " + problems.get(0).message());
        }
        return pass("");
    }

    /** Every graph under {@code graphs/}, with the samples that run it. */
    public List<GraphRow> graphs(List<SampleInventory.SceneRow> inventory) throws IOException {
        Map<String, TreeSet<String>> usedBy = new TreeMap<>();
        for (SampleInventory.SceneRow scene : inventory) {
            JsonValue root = new JsonReader().parse(Files.readString(new File(assetsRoot, "samples/" + scene.sample()).toPath(),
                StandardCharsets.UTF_8));
            TreeSet<String> graphs = new TreeSet<>();
            collectGraphs(root.get("entities"), graphs);
            for (String graph : graphs) {
                usedBy.computeIfAbsent(graph, k -> new TreeSet<>()).add(scene.sample().replace(".json", ""));
            }
        }
        Path base = assetsRoot.toPath();
        List<String> paths;
        try (Stream<Path> walk = Files.walk(base.resolve("graphs"))) {
            paths = walk.filter(p -> p.toString().endsWith(".graph"))
                .map(p -> base.relativize(p).toString().replace(File.separatorChar, '/')).sorted().toList();
        }
        List<GraphRow> rows = new ArrayList<>();
        for (String path : paths) {
            Result loaded = load(path);
            Result loads = loaded.reason().startsWith("does not load") ? loaded : pass("");
            Result validates = loads.status() == TemplateMatrix.Status.PASS ? loaded : notApplicable();
            JsonValue example = graphExamples.get(path);
            String mirrors = example == null ? "-" : example.getString("mirrors");
            Result parity;
            if (example == null) {
                parity = notApplicable();
            } else if (example.getString("notCovered", "").startsWith("Nothing left out")) {
                parity = pass("covers the whole script");
            } else {
                String left = example.getString("notCovered", "");
                int stop = left.indexOf(". ");
                parity = fail("leaves out: " + (stop > 0 ? left.substring(0, stop) : left));
            }
            rows.add(new GraphRow(path, List.copyOf(usedBy.getOrDefault(path, new TreeSet<>())), loads, validates, mirrors, parity));
        }
        return rows;
    }

    /** The sample matrix as Markdown. */
    public static String samplesMarkdown(List<SampleRow> rows) {
        StringBuilder out = new StringBuilder("| Sample | Label (M1.7) |");
        StringBuilder rule = new StringBuilder("|---|---|");
        for (Gate gate : Gate.values()) {
            out.append(' ').append(gate.label()).append(" |");
            rule.append("---|");
        }
        out.append('\n').append(rule).append('\n');
        for (SampleRow row : rows) {
            out.append("| `").append(row.sample()).append("` | ").append(row.label()).append(" |");
            for (Gate gate : Gate.values()) {
                out.append(' ').append(escape(row.gates().get(gate).cell())).append(" |");
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** The graph table as Markdown. */
    public static String graphsMarkdown(List<GraphRow> rows) {
        StringBuilder out = new StringBuilder("| Graph | Run by samples | Loads | Validates | Mirrors Java | Parity |\n|---|---|---|---|---|---|\n");
        for (GraphRow row : rows) {
            out.append("| `").append(row.graph()).append("` | ").append(row.usedBy().isEmpty() ? "none" : String.join(", ", row.usedBy()))
                .append(" | ").append(escape(row.loads().cell())).append(" | ").append(escape(row.validates().cell())).append(" | ")
                .append(escape(row.mirrors())).append(" | ").append(escape(row.parity().cell())).append(" |\n");
        }
        return out.toString();
    }

    /** Status counts per gate, for the summary: gate to {PASS, FAIL, BLOCKED, n/a}. */
    public static Map<Gate, int[]> counts(List<SampleRow> rows) {
        Map<Gate, int[]> counts = new LinkedHashMap<>();
        for (Gate gate : Gate.values()) {
            int[] c = new int[4];
            for (SampleRow row : rows) {
                c[row.gates().get(gate).status().ordinal()]++;
            }
            counts.put(gate, c);
        }
        return counts;
    }

    /** The per-gate counts as Markdown. */
    public static String countsMarkdown(List<SampleRow> rows) {
        StringBuilder out = new StringBuilder("| Gate | PASS | FAIL | BLOCKED | n/a |\n|---|---|---|---|---|\n");
        counts(rows).forEach((gate, c) -> out.append("| ").append(gate.label()).append(" | ").append(c[0]).append(" | ").append(c[1])
            .append(" | ").append(c[2]).append(" | ").append(c[3]).append(" |\n"));
        return out.toString();
    }

    private static String escape(String text) {
        return text.replace("|", "\\|");
    }

    private static Result pass(String reason) {
        return new Result(TemplateMatrix.Status.PASS, reason);
    }

    private static Result fail(String reason) {
        return new Result(TemplateMatrix.Status.FAIL, reason);
    }

    private static Result blocked(String reason) {
        return new Result(TemplateMatrix.Status.BLOCKED, reason);
    }

    private static Result notApplicable() {
        return new Result(TemplateMatrix.Status.NOT_APPLICABLE, "");
    }
}
