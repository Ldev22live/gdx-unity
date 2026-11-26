package io.github.ldev22live.gdxunity.editor.desktop.audit;

import io.github.ldev22live.gdxunity.editor.core.project.StarterPalette;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateProject;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetProvenance;
import io.github.ldev22live.gdxunity.engine.core.asset.OriginalAssets;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The desktop and web support matrix per shipped asset (roadmap M2b.7, TD-101): every file the editor package ships (all of
 * {@code assets/} but {@code build/} and the editor's own preference files, as {@code editor-desktop/build.gradle} packages it) with its
 * format, its licence record, whether the samples or the starter palette use it, and what the desktop and the Babylon web player do
 * with it, in the status words of {@code docs/BACKEND_SUPPORT_MATRIX.md}.
 * <p>
 * The web column follows what the web build actually receives: {@code engine-babylon/scripts/sync-samples.mjs} copies only some
 * folders and formats into the player, mirrored here by {@link #WEB_RULES}. A model is <b>Works</b> on the web only when the web tests
 * load it ({@code engine-babylon/test/modelFiles.test.ts} loads every served model that names no outside file); any other served model
 * is <b>Unchecked</b>. A scene takes the web status the sample inventory gives it (MP.1).
 */
public final class AssetSupportMatrix {
    /** The status words of the backend support matrix (M9.4), which every cell starts with. */
    public enum Status {
        WORKS("Works"),
        PARTIAL("Partial"),
        NO("No"),
        UNCHECKED("Unchecked"),
        NOT_APPLICABLE("Not applicable");

        private final String word;

        Status(String word) {
            this.word = word;
        }

        /** The word as the matrix writes it. */
        public String word() {
            return word;
        }
    }

    /**
     * What one backend does with a file.
     *
     * @param status the status word
     * @param note   why, in a few words
     */
    public record Support(Status status, String note) {
        public Support {
            Objects.requireNonNull(status, "status cannot be null");
            Objects.requireNonNull(note, "note cannot be null");
        }

        @Override
        public String toString() {
            return note.isEmpty() ? status.word() : status.word() + ": " + note;
        }
    }

    /**
     * One shipped file.
     *
     * @param path    path under the asset root
     * @param format  its extension, lower case
     * @param record  its licence record: a download's manifest entry, an original, a copy of a recorded download, or none
     * @param usedBy  "samples", "palette", both, or "-" when neither uses it
     * @param desktop what the desktop game runtime and editor do with it
     * @param web     what the Babylon web player does with it
     */
    public record Row(String path, String format, String record, String usedBy, Support desktop, Support web) {
    }

    /**
     * One copy rule of the web build: files under {@code folder} (in its subfolders too when {@code recursive}) with one of the
     * extensions, and, when {@code requiredSegment} is not empty, with that text in their path.
     */
    public record WebRule(String folder, boolean recursive, Set<String> extensions, String requiredSegment) {
        /** Whether this rule copies {@code path} into the web build. */
        public boolean serves(String path) {
            if (!path.startsWith(folder + "/")) {
                return false;
            }
            String rest = path.substring(folder.length() + 1);
            if (!recursive && rest.contains("/")) {
                return false;
            }
            return extensions.contains(extensionOf(path)) && (requiredSegment.isEmpty() || path.contains(requiredSegment));
        }
    }

    /** The copy rules of {@code engine-babylon/scripts/sync-samples.mjs}, in its order; {@code AssetSupportMatrixTest} checks the two agree. */
    public static final List<WebRule> WEB_RULES = List.of(
        new WebRule("samples", false, Set.of("json"), ""),
        new WebRule("models", true, Set.of("gltf", "glb", "bin", "png", "jpg"), ""),
        new WebRule("music", false, Set.of("song"), ""),
        new WebRule("animations", true, Set.of("anim"), ""),
        new WebRule("graphs", false, Set.of("graph"), ""),
        new WebRule("characters", false, Set.of("gltf", "glb", "bin", "png", "jpg"), ""),
        new WebRule("sprites", false, Set.of("sprite"), ""),
        new WebRule("graphs/samples", false, Set.of("graph"), ""),
        new WebRule("terrains", false, Set.of("terrain"), ""),
        new WebRule("materials", false, Set.of("material"), ""),
        new WebRule("textures", true, Set.of("png", "jpg", "jpeg"), ""),
        // M2b.7: the downloaded packs' model folders, which the starter pack's prefabs and the refreshed samples name
        new WebRule("thirdparty", true, Set.of("gltf", "glb", "bin", "png", "jpg"), "/Models/"));

    /** Files at the asset root the editor package leaves out (editor-desktop/build.gradle, sampleProjectExcludes). */
    public static final Set<String> NOT_PACKAGED = Set.of("assets.txt", "workspace_layout.json", "editor_theme.json", "scene_backup.json");
    /** The folder the build writes into, never packaged. */
    private static final String BUILD_FOLDER = "build/";
    /** Source formats of downloaded model packs, which neither backend loads (both read glTF and GLB). */
    private static final Set<String> MODEL_SOURCE_FORMATS = Set.of("fbx", "obj", "mtl", "dae", "stl");
    /** Documentation, licence text, links and archives. */
    private static final Set<String> DOCUMENTS = Set.of("txt", "md", "url", "html", "zip", "project");
    /** File formats the desktop reads as content (engine-core AssetType), and glTF buffers. */
    private static final Map<String, String> DESKTOP_FORMATS = Map.ofEntries(
        Map.entry("gltf", "drawn (ModelAssets)"), Map.entry("glb", "drawn (ModelAssets)"),
        Map.entry("bin", "data of the glTF or character beside it"), Map.entry("png", "image"), Map.entry("jpg", "image"),
        Map.entry("jpeg", "image"), Map.entry("prefab", "placed from the palette"), Map.entry("graph", "visual script"),
        Map.entry("anim", "clip"), Map.entry("sculpt", "sculpt"), Map.entry("sprite", "sprite"), Map.entry("song", "song"),
        Map.entry("material", "material"), Map.entry("terrain", "terrain"), Map.entry("wav", "sound"), Map.entry("ogg", "sound"),
        Map.entry("java", "script, compiled in the editor (TD-019)"));
    /** What the web player does with each format it is given, from docs/BACKEND_SUPPORT_MATRIX.md (models and scenes apart). */
    private static final Map<String, Support> WEB_FORMATS = Map.of(
        "terrain", new Support(Status.WORKS, "drawn with paint, holes and water (M3.11)"),
        "sprite", new Support(Status.WORKS, "Sprite Animator and Tile Map"),
        "material", new Support(Status.WORKS, "on mesh primitives (M2.5)"),
        "anim", new Support(Status.WORKS, "Animator clips"),
        "graph", new Support(Status.PARTIAL, "the web node set; physics, navigation and some Java method nodes refused"),
        "song", new Support(Status.PARTIAL, "sampled, sfxr and SoundFont instruments silent"));
    private static final String SAMPLES_FOLDER = "samples/";
    private static final String NOT_SERVED = "not copied into the web build (engine-babylon/scripts/sync-samples.mjs)";

    private final File assetsRoot;
    private final AssetProvenance provenance;
    private final Map<String, String> sceneWeb;
    private final Set<String> recordedHashes = new HashSet<>();
    private final Set<String> usedBySamples = new TreeSet<>();
    private final Set<String> usedByPalette = new TreeSet<>();

    /**
     * @param assetsRoot the asset root (the editor package's sample project)
     * @param sceneWeb   the web column of the sample inventory for each scene, by file name in {@code samples/} (MP.1)
     * @throws IOException if a record or a scene cannot be read
     */
    public AssetSupportMatrix(File assetsRoot, Map<String, String> sceneWeb) throws IOException {
        this.assetsRoot = Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null");
        this.sceneWeb = Map.copyOf(Objects.requireNonNull(sceneWeb, "sceneWeb cannot be null"));
        ThirdPartyManifest manifest = ThirdPartyManifest.read(assetsRoot);
        OriginalAssets originals = OriginalAssets.read(assetsRoot);
        this.provenance = new AssetProvenance(manifest, originals);
        manifest.entries().forEach(entry -> entry.files().forEach(file -> recordedHashes.add(file.sha256())));
        for (String sample : this.sceneWeb.keySet()) {
            usedBySamples.addAll(TemplateProject.dependenciesOf(assetsRoot, SAMPLES_FOLDER + sample));
        }
        for (String prefab : StarterPalette.prefabs(assetsRoot)) {
            usedByPalette.addAll(TemplateProject.dependenciesOf(assetsRoot, prefab));
        }
    }

    /** Whether the web build receives {@code path}. */
    public static boolean servedOnWeb(String path) {
        return WEB_RULES.stream().anyMatch(rule -> rule.serves(path));
    }

    /** The files the editor package ships, under the asset root, in path order. */
    public List<String> shippedFiles() throws IOException {
        Path root = assetsRoot.toPath();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                .map(p -> root.relativize(p).toString().replace('\\', '/'))
                .filter(p -> !p.startsWith(BUILD_FOLDER) && !NOT_PACKAGED.contains(p))
                .sorted().toList();
        }
    }

    /**
     * The matrix: one row per shipped file.
     *
     * @throws IllegalStateException when a file has a format the matrix has no rule for, so a person decides its status
     * @throws IOException           when a file cannot be read
     */
    public List<Row> rows() throws IOException {
        List<Row> rows = new ArrayList<>();
        for (String path : shippedFiles()) {
            String format = extensionOf(path);
            Support desktop = desktop(path, format);
            rows.add(new Row(path, format, record(path), usedBy(path), desktop, web(path, format, desktop)));
        }
        return rows;
    }

    private String usedBy(String path) {
        boolean samples = usedBySamples.contains(path);
        boolean palette = usedByPalette.contains(path);
        return samples && palette ? "samples, palette" : samples ? "samples" : palette ? "palette" : "-";
    }

    private String record(String path) throws IOException {
        AssetProvenance.Provenance found = provenance.of(path);
        return switch (found.kind()) {
            case THIRD_PARTY -> "download, " + found.licence();
            case ORIGINAL -> "original, " + found.licence();
            case UNRECORDED -> recordedHashes.contains(sha256(assetsRoot.toPath().resolve(path)))
                ? "copy of a recorded download, CC0-1.0" : "none";
        };
    }

    private static Support desktop(String path, String format) {
        if (DOCUMENTS.contains(format)) {
            return new Support(Status.NOT_APPLICABLE, "documentation, licence text, link or archive");
        }
        if (MODEL_SOURCE_FORMATS.contains(format)) {
            return new Support(Status.NO, "source format: the desktop loads glTF and GLB only");
        }
        if (format.equals("json")) {
            return new Support(Status.WORKS, jsonRole(path));
        }
        String note = DESKTOP_FORMATS.get(format);
        if (note == null) {
            throw new IllegalStateException("The support matrix has no rule for ." + format + " (" + path + "): decide its status in "
                + "AssetSupportMatrix");
        }
        return new Support(Status.WORKS, note);
    }

    private static String jsonRole(String path) {
        if (path.startsWith(SAMPLES_FOLDER)) {
            return "scene";
        }
        if (path.startsWith("licenses/")) {
            return "licence record";
        }
        if (path.startsWith("characters/")) {
            return path.startsWith("characters/makehuman/") ? "Character Creator body data" : "saved character";
        }
        if (path.equals("asset-classification.json")) {
            return "Content Browser classification";
        }
        if (path.equals("templates/gallery.json")) {
            return "template gallery";
        }
        if (path.startsWith("graphs/")) {
            return "the Examples menu's list";
        }
        throw new IllegalStateException("The support matrix has no rule for " + path + ": decide its status in AssetSupportMatrix");
    }

    private Support web(String path, String format, Support desktop) throws IOException {
        if (desktop.status() == Status.NOT_APPLICABLE) {
            return desktop;
        }
        if (MODEL_SOURCE_FORMATS.contains(format)) {
            return new Support(Status.NO, "source format, not served; the web loads glTF and GLB");
        }
        if (format.equals("java")) {
            return new Support(Status.NO, "Java scripts run on the desktop only; the web runs hand-ported TypeScript (TD-041)");
        }
        if (format.equals("prefab")) {
            return new Support(Status.NOT_APPLICABLE, "a scene holds its own copy of each placed prefab, which is what the web reads");
        }
        if (format.equals("json") && !path.startsWith(SAMPLES_FOLDER)) {
            return new Support(Status.NOT_APPLICABLE, "editor data: " + jsonRole(path));
        }
        if (path.startsWith("prefabs/") || path.startsWith("templates/")) {
            return new Support(Status.NOT_APPLICABLE, "an editor picture (palette thumbnail or template preview)");
        }
        if (path.startsWith("characters/makehuman/")) {
            return new Support(Status.NOT_APPLICABLE, "Character Creator data; the web plays the baked character");
        }
        if (!servedOnWeb(path)) {
            String gap = usedBySamples.contains(path) || usedByPalette.contains(path) ? "; a sample or the palette uses it" : "";
            return new Support(Status.NO, NOT_SERVED + gap);
        }
        if (path.startsWith(SAMPLES_FOLDER)) {
            return scene(path.substring(SAMPLES_FOLDER.length()));
        }
        if (format.equals("gltf") || format.equals("glb")) {
            return standsAlone(path)
                ? new Support(Status.WORKS, "loaded by the web tests (modelFiles.test.ts)")
                : new Support(Status.UNCHECKED, "served; names outside files, so the web tests do not load it");
        }
        if (path.startsWith("textures/")) {
            return new Support(Status.WORKS, "named by a material (M2.5)");
        }
        if (format.equals("bin") || format.equals("png") || format.equals("jpg")) {
            return new Support(Status.UNCHECKED, "served beside its model and loaded only through it");
        }
        Support known = WEB_FORMATS.get(format);
        if (known == null) {
            throw new IllegalStateException("The support matrix has no web rule for ." + format + " (" + path + ")");
        }
        return known;
    }

    /** Whether a model names no outside file, so the web tests can load it from its bytes alone. */
    public boolean standsAlone(String model) throws IOException {
        return TemplateProject.dependenciesOf(assetsRoot, model).equals(Set.of(model));
    }

    private Support scene(String sample) {
        String inventory = sceneWeb.get(sample);
        if (inventory == null) {
            throw new IllegalStateException("No web status from the sample inventory for " + sample);
        }
        if (inventory.equals("yes")) {
            return new Support(Status.WORKS, "plays (web tests; marked web in the gallery)");
        }
        if (inventory.startsWith("components supported")) {
            return new Support(Status.UNCHECKED, inventory);
        }
        return new Support(Status.PARTIAL, "drawn; " + inventory.replaceFirst("^no: ", ""));
    }

    /** The matrix as tab-separated text, one row per file, with a header; the same rows always give the same text. */
    public static String tsv(List<Row> rows) {
        StringBuilder out = new StringBuilder("path\tformat\trecord\tusedBy\tdesktop\tweb\n");
        for (Row row : rows) {
            out.append(row.path()).append('\t').append(row.format()).append('\t').append(row.record()).append('\t')
                .append(row.usedBy()).append('\t').append(row.desktop()).append('\t').append(row.web()).append('\n');
        }
        return out.toString();
    }

    /**
     * The summary as Markdown: files counted by top folder (a downloaded pack by its folder), desktop status and web status, with the
     * web note when all of a group's files share it.
     */
    public static String summaryMarkdown(List<Row> rows) {
        Map<String, int[]> counts = new TreeMap<>();
        Map<String, Set<String>> notes = new TreeMap<>();
        for (Row row : rows) {
            String key = group(row.path()) + "\t" + row.desktop().status().word() + "\t" + row.web().status().word();
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
            notes.computeIfAbsent(key, k -> new TreeSet<>()).add(row.web().note());
        }
        StringBuilder out = new StringBuilder("| Folder | Files | Desktop | Web | Web note |\n|---|---|---|---|---|\n");
        counts.forEach((key, n) -> {
            String[] parts = key.split("\t");
            Set<String> groupNotes = notes.get(key);
            String note = groupNotes.size() == 1 ? groupNotes.iterator().next() : "varies (see the per-file table)";
            out.append("| `").append(parts[0]).append("` | ").append(n[0]).append(" | ").append(parts[1]).append(" | ").append(parts[2])
                .append(" | ").append(note.replace("|", "\\|")).append(" |\n");
        });
        return out.toString();
    }

    /** A file's group in the summary: its top folder, two levels for a downloaded pack and a model format folder. */
    static String group(String path) {
        String[] parts = path.split("/");
        if (parts.length == 1) {
            return "(asset root)";
        }
        if (parts[0].equals("thirdparty") || (parts[0].equals("models") && parts.length > 2)
            || (parts[0].equals("textures") && parts.length > 2)) {
            return parts[0] + "/" + parts[1] + (parts[0].equals("thirdparty") && parts.length > 3 ? "/" + parts[2] : "") + "/";
        }
        return parts[0] + "/";
    }

    static String extensionOf(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }
}
