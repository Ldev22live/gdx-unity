package io.github.ldev22live.gdxunity.editor.desktop.audit;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetOpening;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.core.asset.OriginalAssets;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The sample inventory of roadmap MP.1 (Appendix A3 and B6): every sample scene and every content file the samples can use, as
 * they are today, so later migrations (MP.3, M2b.6, MC.7) start from facts and no sample is presumed broken.
 * <p>
 * For a scene: its declared dimension (the file's {@code gameMode}) and genre, its gallery title, whether the editor's own
 * serializer loads it, its behaviour (graphs, Java scripts), the formats of the files it names and any that are missing, which parts
 * open in an editor (the {@link TemplateMatrix} of M1.1), the licence basis of what it uses, whether the web player can play it,
 * which tests name it, and whether the M9.5 performance baseline plays it.
 * <p>
 * For a content file: its format, where it opens ({@link AssetOpening}), how it was made (a generator in {@code tools/}, the
 * character creator, MakeHuman data, or no record), its licence basis, and the samples that use it.
 * <p>
 * Licences are reported as the repository records them. Only the MakeHuman data has a licence file of its own (CC0 1.0); every other
 * file was made in this repository and has no per-asset licence record, so its licence is the owner's to confirm (the repository's
 * {@code LICENSE} is Apache 2.0 for the code). Nothing here downloads anything.
 * <p>
 * The replacement plan of Appendix A3 (roadmap M2b.6, {@link #replacements}): for each scene, what it shows now, and from the migration
 * ledger ({@code docs/migration/sample-migrations.json}, MC.7) the proposed replacement, its sources and licences, the conversion,
 * dependencies, the expected visual result, collision, the rollback path and how far its migration has gone.
 */
public final class SampleInventory {
    /** Licence basis of the MakeHuman base data. */
    static final String LICENCE_CC0 = "CC0 1.0 (MakeHuman, LICENSE-ASSETS.txt)";
    /** Licence basis of a character the creator baked from the MakeHuman data. */
    static final String LICENCE_CC0_DERIVED = "CC0 base data; the baked file is project-made, no record";
    /** Licence basis of everything else: made here, no per-asset record. */
    static final String LICENCE_UNRECORDED = "project-made, no licence record";

    private static final String MAKEHUMAN = "characters/makehuman/";
    /** Content folders under the asset root that hold sample content, in the order the inventory lists them. */
    static final List<String> CONTENT_FOLDERS = List.of("models", "characters", "sprites", "textures", "materials", "terrains",
        "sculpts", "prefabs", "animations", "graphs", "music", "scripts");
    /** The inventory's own test, which names samples to check the inventory, not to test them. */
    static final String SELF_TEST = "SampleInventoryTest";
    /** How an editable part that fails is written in a scene row ("Logic FAIL"). */
    private static final String FAIL_SUFFIX = " FAIL";
    /** The folder downloaded files are imported under (M2b.1), at the asset root or inside a type's folder. */
    private static final String THIRD_PARTY_FOLDER = "thirdparty/";
    private static final Pattern TOOL_IN_GENERATOR =Pattern.compile("(tools/)?(make_[a-z_]+\\.(py|mjs))");

    /**
     * One sample scene.
     *
     * @param sample      file name in {@code samples/}
     * @param name        the scene's own name
     * @param dimension   the declared {@code gameMode} (2d, 2.5d, 3d), or "undeclared"
     * @param genre       the declared genre, or "-"
     * @param template    the gallery title, or "-" when the gallery does not offer it
     * @param entities    entities in the file
     * @param load        "loads", or "fails: " and the reason, from the editor's serializer
     * @param behaviour   where its behaviour lives, with the scripts and graphs named
     * @param formats     the formats of the files it names, with counts
     * @param missing     files it names that do not exist
     * @param editable    the parts it uses with their M1.1 status, such as "Logic FAIL"
     * @param licences    the licence bases of the files it uses (the scene file itself is project-made)
     * @param web         whether the web player plays it, and why not
     * @param tests       test classes that name this sample
     * @param baseline    true when the M9.5 performance baseline plays it in the game runtime
     */
    public record SceneRow(String sample, String name, String dimension, String genre, String template, int entities, String load,
                           String behaviour, List<String> formats, List<String> missing, List<String> editable, List<String> licences,
                           String web, List<String> tests, boolean baseline) {
    }

    /**
     * One content file.
     *
     * @param path     path under the asset root
     * @param format   the extension
     * @param opens    where it opens or how it is used
     * @param origin   how it was made, as far as the repository records it
     * @param licence  its licence basis
     * @param usedBy   samples that name it, directly or through a material, prefab or character
     */
    public record AssetRow(String path, String format, String opens, String origin, String licence, List<String> usedBy) {
    }

    /**
     * The repository facts the inventory reads besides the scenes.
     *
     * @param repositoryRoot      the repository folder (tests and tools are searched from it)
     * @param assetsRoot          the asset root
     * @param babylonComponents   the component types the web player supports
     * @param baselineScenes      the scenes in the performance baseline, as {@code samples/x.json}
     */
    public record Sources(File repositoryRoot, File assetsRoot, Set<String> babylonComponents, Set<String> baselineScenes) {
        public Sources {
            Objects.requireNonNull(repositoryRoot, "repositoryRoot cannot be null");
            Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null");
            babylonComponents = Set.copyOf(babylonComponents);
            baselineScenes = Set.copyOf(baselineScenes);
        }
    }

    /**
     * One scene's replacement plan (Appendix A3, roadmap M2b.6).
     *
     * @param sample       file name in {@code samples/}
     * @param group        the migration ledger's group for it
     * @param current      what it shows now: prefab instances, downloaded and project-made models, shapes, terrains, tile maps
     * @param replacement  the proposed replacement (the group's)
     * @param sources      the replacement's sources and their licences
     * @param conversion   the source format and the exported format
     * @param dependencies what the replacement waits on
     * @param visual       the expected visual result
     * @param editability  its editability today, from the M1.1 matrix: the parts that fail, or that every part passes
     * @param collision    the collision after the replacement
     * @param rollback     the rollback path
     * @param status       for a migrated copy, what it was migrated from; for an original, its copies; otherwise the group's plan
     */
    public record ReplacementRow(String sample, String group, String current, String replacement, String sources, String conversion,
                                 String dependencies, String visual, String editability, String collision, String rollback,
                                 String status) {
    }

    private final Sources sources;
    private final SceneSerializer serializer;
    /** The downloaded files and their licences (docs/asset-integration-report.md); empty when nothing was downloaded. */
    private final ThirdPartyManifest manifest;
    /** The 2D art made in this repository (TD-099); empty when the project records none. */
    private final OriginalAssets originals;
    private final Map<String, String> testSources = new TreeMap<>();
    private final Map<String, String> toolSources = new TreeMap<>();
    private final Map<String, JsonValue> gallery = new LinkedHashMap<>();

    /**
     * @param serializer the serializer the editor loads scenes with (every component type registered)
     */
    public SampleInventory(Sources sources, SceneSerializer serializer) throws IOException {
        this.sources = Objects.requireNonNull(sources, "sources cannot be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer cannot be null");
        this.manifest = ThirdPartyManifest.read(sources.assetsRoot());
        this.originals = OriginalAssets.read(sources.assetsRoot());
        File root = sources.repositoryRoot();
        for (Path file : files(root.toPath(), "src" + File.separator + "test", ".java")) {
            testSources.put(file.getFileName().toString().replace(".java", ""), Files.readString(file, StandardCharsets.UTF_8));
        }
        Path webTests = root.toPath().resolve("engine-babylon").resolve("test");
        if (Files.isDirectory(webTests)) {
            try (Stream<Path> walk = Files.walk(webTests)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".ts")).sorted().toList()) {
                    testSources.put("web " + file.getFileName(), Files.readString(file, StandardCharsets.UTF_8));
                }
            }
        }
        Path tools = root.toPath().resolve("tools");
        if (Files.isDirectory(tools)) {
            try (Stream<Path> list = Files.list(tools)) {
                for (Path file : list.filter(p -> p.toString().endsWith(".py") || p.toString().endsWith(".mjs")).sorted().toList()) {
                    toolSources.put("tools/" + file.getFileName(), Files.readString(file, StandardCharsets.UTF_8));
                }
            }
        }
        File galleryFile = new File(sources.assetsRoot(), "templates/gallery.json");
        if (galleryFile.isFile()) {
            JsonValue templates = new JsonReader().parse(Files.readString(galleryFile.toPath(), StandardCharsets.UTF_8)).get("templates");
            for (JsonValue template = templates.child; template != null; template = template.next) {
                gallery.put(template.getString("scene"), template);
            }
        }
    }

    /** Test sources in every module: {@code <module>/src/test/**}{@code /*.java}. */
    private static List<Path> files(Path root, String marker, String extension) throws IOException {
        List<Path> result = new ArrayList<>();
        File[] modules = root.toFile().listFiles(File::isDirectory);
        if (modules == null) {
            return result;
        }
        Arrays.sort(modules);
        for (File module : modules) {
            Path tests = module.toPath().resolve(marker);
            if (Files.isDirectory(tests)) {
                try (Stream<Path> walk = Files.walk(tests)) {
                    walk.filter(p -> p.toString().endsWith(extension)).sorted().forEach(result::add);
                }
            }
        }
        return result;
    }

    /** The scenes in {@code samples/}, in name order, with the M1.1 matrix entries for their editable parts. */
    public List<SceneRow> scenes(List<EditabilityAudit.Row> audit, List<TemplateMatrix.Entry> matrix) throws IOException {
        File[] files = new File(sources.assetsRoot(), "samples").listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            throw new IOException("No samples folder in " + sources.assetsRoot());
        }
        Arrays.sort(files);
        List<SceneRow> rows = new ArrayList<>();
        for (File file : files) {
            EditabilityAudit.Row auditRow = audit.stream().filter(r -> r.sample().equals(file.getName())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No audit row for " + file.getName()));
            TemplateMatrix.Entry entry = matrix.stream().filter(e -> e.sample().equals(file.getName())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No matrix entry for " + file.getName()));
            rows.add(scene(file, auditRow, entry));
        }
        return rows;
    }

    private SceneRow scene(File file, EditabilityAudit.Row audit, TemplateMatrix.Entry entry) throws IOException {
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JsonValue root = new JsonReader().parse(text);
        String samplePath = "samples/" + file.getName();
        String load;
        try {
            Scene scene = serializer.fromJson(text);
            load = "loads";
            scene.dispose();
        } catch (RuntimeException e) {
            load = "fails: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }

        TreeSet<String> referenced = new TreeSet<>();
        TreeSet<String> componentTypes = new TreeSet<>();
        for (JsonValue entity = root.get("entities").child; entity != null; entity = entity.next) {
            JsonValue components = entity.get("components");
            for (JsonValue component = components == null ? null : components.child; component != null; component = component.next) {
                componentTypes.add(component.getString("type"));
                JsonValue data = component.get("data");
                if (data != null) {
                    collectPaths(data, referenced);
                }
            }
        }
        TreeSet<String> uses = new TreeSet<>(referenced);
        for (String path : referenced) {
            uses.addAll(indirect(path));
        }

        Map<String, Integer> formats = new TreeMap<>();
        List<String> missing = new ArrayList<>();
        TreeSet<String> licences = new TreeSet<>();
        licences.add(LICENCE_UNRECORDED); // the scene file itself
        TreeSet<String> scripts = new TreeSet<>();
        TreeSet<String> graphs = new TreeSet<>();
        for (String path : uses) {
            formats.merge(extension(path), 1, Integer::sum);
            if (!new File(sources.assetsRoot(), path).isFile()) {
                missing.add(path);
            }
            licences.add(licenceOf(path));
            if (path.endsWith(".java")) {
                scripts.add(baseName(path));
            } else if (path.endsWith(".graph")) {
                graphs.add(baseName(path));
            }
        }
        List<String> formatList = new ArrayList<>();
        formats.forEach((format, count) -> formatList.add(format + " " + count));

        String behaviour = switch (audit.logic()) {
            case NONE -> "no scripts or graphs";
            case GRAPH -> "graphs " + String.join(", ", graphs);
            case JAVA -> "Java " + String.join(", ", scripts);
            case JAVA_AND_GRAPH -> "Java " + String.join(", ", scripts) + "; graphs " + String.join(", ", graphs);
        };

        List<String> editable = new ArrayList<>();
        for (TemplateMatrix.Domain domain : TemplateMatrix.Domain.values()) {
            TemplateMatrix.Cell cell = entry.cells().get(domain);
            if (cell != null && cell.status() != TemplateMatrix.Status.NOT_APPLICABLE) {
                editable.add(domain.label() + " " + cell.status().text());
            }
        }

        JsonValue template = gallery.get(samplePath);
        TreeSet<String> unsupported = new TreeSet<>(componentTypes);
        unsupported.removeAll(sources.babylonComponents());
        String web;
        if (!unsupported.isEmpty()) {
            web = "no: " + String.join(", ", unsupported) + " not supported";
        } else if (template != null && !template.getBoolean("web", false)) {
            // The gallery's web flag means every script has a web port that the web tests play (gallery.json)
            web = audit.isLogicGap() ? "no: Java scripts not ported" : "components supported; not marked web in the gallery";
        } else if (template == null && audit.isLogicGap()) {
            web = "not in the gallery; Java scripts";
        } else {
            web = "yes";
        }

        // The whole file name, not inside a longer one (platformer-2d.json is also the end of tilemap-platformer-2d.json)
        Pattern named = Pattern.compile("(?<![A-Za-z0-9_-])" + Pattern.quote(file.getName()));
        List<String> tests = new ArrayList<>();
        testSources.forEach((test, source) -> {
            if (!test.equals(SELF_TEST) && named.matcher(source).find()) {
                tests.add(test);
            }
        });

        String dimension = root.getString("gameMode", "undeclared");
        String genre = root.getString("gameGenre", "-");
        return new SceneRow(file.getName(), root.getString("name", "-"), dimension, genre,
            template == null ? "-" : template.getString("title"), audit.entities(), load, behaviour, formatList, missing, editable,
            List.copyOf(licences), web, tests, sources.baselineScenes().contains(samplePath));
    }

    /** Files a material, prefab or character names in turn (textures, models, materials). */
    private List<String> indirect(String path) throws IOException {
        if (!(path.endsWith(".material") || path.endsWith(".prefab") || path.endsWith(".character.json"))) {
            return List.of();
        }
        File file = new File(sources.assetsRoot(), path);
        if (!file.isFile()) {
            return List.of();
        }
        TreeSet<String> found = new TreeSet<>();
        collectPaths(new JsonReader().parse(Files.readString(file.toPath(), StandardCharsets.UTF_8)), found);
        found.remove(path);
        return List.copyOf(found);
    }

    /** Strings that name a file under the asset root: a known asset extension or a script, with no spaces outside the name. */
    private static void collectPaths(JsonValue value, TreeSet<String> paths) {
        if (value.isString()) {
            String text = value.asString();
            if (isFilePath(text)) {
                paths.add(text);
            }
            return;
        }
        for (JsonValue child = value.child; child != null; child = child.next) {
            collectPaths(child, paths);
        }
    }

    private static boolean isFilePath(String text) {
        if (text.isBlank() || text.contains("\n") || text.startsWith("http")) {
            return false;
        }
        return text.endsWith(".java") || text.endsWith(".character.json") || AssetType.fromPath(text) != AssetType.UNKNOWN;
    }

    /** Every file in the content folders, in folder order, with the samples that use each. */
    public List<AssetRow> assets(List<SceneRow> scenes) throws IOException {
        Map<String, TreeSet<String>> usedBy = new TreeMap<>();
        for (SceneRow scene : scenes) {
            File file = new File(new File(sources.assetsRoot(), "samples"), scene.sample());
            JsonValue root = new JsonReader().parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            TreeSet<String> referenced = new TreeSet<>();
            collectPaths(root.get("entities"), referenced);
            TreeSet<String> uses = new TreeSet<>(referenced);
            for (String path : referenced) {
                uses.addAll(indirect(path));
            }
            for (String path : uses) {
                usedBy.computeIfAbsent(path, k -> new TreeSet<>()).add(scene.sample().replace(".json", ""));
            }
        }
        List<AssetRow> rows = new ArrayList<>();
        for (String folder : CONTENT_FOLDERS) {
            Path base = sources.assetsRoot().toPath();
            Path dir = base.resolve(folder);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<String> paths;
            try (Stream<Path> walk = Files.walk(dir)) {
                paths = walk.filter(Files::isRegularFile)
                    .map(p -> base.relativize(p).toString().replace(File.separatorChar, '/'))
                    .filter(p -> !p.endsWith(".md") && !p.endsWith(".txt") && !p.endsWith("examples.json"))
                    .sorted(Comparator.naturalOrder()).toList();
            }
            for (String path : paths) {
                if (path.startsWith(MAKEHUMAN)) {
                    continue; // listed once below, as the character creator's data
                }
                rows.add(new AssetRow(path, extension(path), opens(path), origin(path), licenceOf(path),
                    List.copyOf(usedBy.getOrDefault(path, new TreeSet<>()))));
            }
            if (folder.equals("characters")) {
                rows.add(new AssetRow(MAKEHUMAN + "*", "bin, json", "the Character Creator reads it; not edited directly",
                    "converted from MakeHuman 1.x by tools/convert_makehuman_assets.py", LICENCE_CC0, List.of()));
            }
        }
        return rows;
    }

    /** Where a file opens, after {@link AssetOpening}: its own editor, or how it is used. */
    static String opens(String path) {
        if (path.endsWith(".character.json")) {
            return "Character Creator (Customize > Character)";
        }
        if (path.endsWith(".java")) {
            return "none: Java source, compiled by Compile Scripts";
        }
        if (path.endsWith(".bin")) {
            return "none: data of the glTF beside it";
        }
        AssetType type = AssetType.fromPath(path);
        return switch (type) {
            case GRAPH -> "Logic (graph editor)";
            case ANIMATION -> "Animation (Timeline)";
            case SPRITE -> "2D Studio: Sprites and Tiles";
            case SONG -> "Audio & Music";
            case TEXTURE -> "none: placed as a sprite or named in a material";
            case MODEL -> "none for the mesh (R7a); placement in Details";
            case PREFAB -> "none: placed, changed, applied back";
            case SCULPT -> "Sculpt, after placing it";
            case SOUND -> "none: an Audio Source plays it";
            case MATERIAL -> "Details of the surface using it";
            case TERRAIN -> "World: Terrain";
            case UNKNOWN -> "not a Forgeroom asset";
        };
    }

    /** How a file was made, as far as the repository records it. */
    String origin(String path) throws IOException {
        Optional<ThirdPartyManifest.Entry> downloaded = manifest.find(path);
        if (downloaded.isPresent()) {
            return "downloaded from " + downloaded.get().source() + " on " + downloaded.get().retrieved() + " (" + ThirdPartyManifest.PATH
                + ")";
        }
        Optional<OriginalAssets.FileRecord> made = originals.find(path);
        if (made.isPresent()) {
            return "made with " + made.get().madeWith() + " (" + OriginalAssets.PATH + ")";
        }
        if (path.startsWith(MAKEHUMAN)) {
            return "converted from MakeHuman 1.x by tools/convert_makehuman_assets.py";
        }
        File file = new File(sources.assetsRoot(), path);
        if (path.endsWith(".gltf") && file.isFile()) {
            JsonValue asset = new JsonReader().parse(Files.readString(file.toPath(), StandardCharsets.UTF_8)).get("asset");
            String generator = asset == null ? "" : asset.getString("generator", "");
            if (!generator.isBlank()) {
                Matcher tool = TOOL_IN_GENERATOR.matcher(generator);
                if (tool.find() && !toolSources.containsKey("tools/" + tool.group(2))) {
                    return "says " + tool.group(2) + ", which is not in tools/ (cannot be regenerated)";
                }
                return generator;
            }
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        List<String> writers = new ArrayList<>();
        toolSources.forEach((tool, source) -> {
            if (source.contains(name)) {
                writers.add(tool);
            }
        });
        if (!writers.isEmpty()) {
            return "named in " + String.join(", ", writers);
        }
        return "no record (made in the editor or by hand)";
    }

    /** The licence basis of a file: the third-party manifest's record when it is a downloaded file, else {@link #licence(String)}. */
    String licenceOf(String path) {
        Optional<ThirdPartyManifest.Entry> downloaded = manifest.find(path);
        if (downloaded.isPresent()) {
            ThirdPartyManifest.Entry entry = downloaded.get();
            return entry.licence() + " (" + String.join(", ", entry.authors()) + "; " + entry.licenceEvidence() + ")";
        }
        Optional<OriginalAssets.FileRecord> made = originals.find(path);
        if (made.isPresent()) {
            return made.get().licence() + " (this repository; " + OriginalAssets.PATH + ")";
        }
        return licence(path);
    }

    /** The licence basis of a file the project made or converted. */
    static String licence(String path) {
        if (path.startsWith(MAKEHUMAN)) {
            return LICENCE_CC0;
        }
        if (path.startsWith("characters/")) {
            return LICENCE_CC0_DERIVED;
        }
        return LICENCE_UNRECORDED;
    }

    private static String extension(String path) {
        if (path.endsWith(".character.json")) {
            return "character.json";
        }
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String baseName(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** The component types listed in the web player's {@code SUPPORTED_COMPONENTS} set. */
    public static Set<String> babylonComponents(Path sceneBuilder) throws IOException {
        String source = Files.readString(sceneBuilder, StandardCharsets.UTF_8);
        int start = source.indexOf("SUPPORTED_COMPONENTS");
        if (start < 0) {
            throw new IOException("No SUPPORTED_COMPONENTS in " + sceneBuilder);
        }
        String set = source.substring(start, source.indexOf("]);", start));
        Set<String> types = new TreeSet<>();
        Matcher matcher = Pattern.compile("^\\s*\"([A-Za-z0-9]+)\",", Pattern.MULTILINE).matcher(set);
        while (matcher.find()) {
            types.add(matcher.group(1));
        }
        return types;
    }

    /** The scenes in a performance baseline file (its first column, comment lines and the header skipped). */
    public static Set<String> baselineScenes(Path baseline) throws IOException {
        Set<String> scenes = new TreeSet<>();
        for (String line : Files.readAllLines(baseline, StandardCharsets.UTF_8)) {
            if (!line.startsWith("#") && !line.startsWith("scene\t") && !line.isBlank()) {
                scenes.add(line.substring(0, line.indexOf('\t')));
            }
        }
        return scenes;
    }

    /**
     * The replacement plan of every scene (Appendix A3), from the migration ledger ({@code docs/migration/sample-migrations.json}).
     *
     * @throws IllegalArgumentException when the ledger plans a scene in no group, so the plan is incomplete
     * @throws IOException              when a scene cannot be read
     */
    public List<ReplacementRow> replacements(List<SceneRow> scenes, JsonValue ledger) throws IOException {
        Objects.requireNonNull(scenes, "scenes cannot be null");
        Objects.requireNonNull(ledger, "ledger cannot be null");
        List<ReplacementRow> rows = new ArrayList<>();
        for (SceneRow scene : scenes) {
            JsonValue group = null;
            for (JsonValue g = ledger.get("groups").child; g != null; g = g.next) {
                if (Arrays.asList(g.get("samples").asStringArray()).contains(scene.sample())) {
                    group = g;
                }
            }
            if (group == null) {
                throw new IllegalArgumentException("The migration ledger plans " + scene.sample() + " in no group");
            }
            List<String> fails = scene.editable().stream().filter(part -> part.endsWith(FAIL_SUFFIX))
                .map(part -> part.substring(0, part.length() - FAIL_SUFFIX.length())).toList();
            String editability = fails.isEmpty() ? "every part passes" : "fails: " + String.join(", ", fails);
            String[] blockers = group.get("blockers").asStringArray();
            rows.add(new ReplacementRow(scene.sample(), group.getString("id"), currentAssets(scene.sample()),
                group.getString("replacement"), group.getString("sources"),
                group.getString("sourceFormat") + " to " + group.getString("exportFormat"),
                blockers.length == 0 ? "none" : String.join(", ", blockers),
                group.getString("visual"), editability, group.getString("collision"), group.getString("rollback"),
                migrationStatus(scene.sample(), group, ledger)));
        }
        return rows;
    }

    /** What a scene shows, counted from its file: prefab instances, models by where they come from, shapes, grounds and maps. */
    private String currentAssets(String sample) throws IOException {
        JsonValue scene = new JsonReader().parse(Files.readString(new File(sources.assetsRoot(), "samples/" + sample).toPath(),
            StandardCharsets.UTF_8));
        int downloaded = 0;
        int made = 0;
        TreeSet<String> downloadedFiles = new TreeSet<>();
        TreeSet<String> madeFiles = new TreeSet<>();
        int prefabs = 0;
        Map<String, Integer> kinds = new TreeMap<>();
        for (JsonValue entity = scene.get("entities").child; entity != null; entity = entity.next) {
            for (JsonValue component = entity.get("components").child; component != null; component = component.next) {
                JsonValue data = component.get("data");
                switch (component.getString("type", "")) {
                    case "ModelComponent" -> {
                        String model = data.getString("model", "");
                        if (model.startsWith(THIRD_PARTY_FOLDER) || model.contains("/" + THIRD_PARTY_FOLDER)) {
                            downloaded++;
                            downloadedFiles.add(model);
                        } else {
                            made++;
                            madeFiles.add(model);
                        }
                    }
                    case "PrefabInstance" -> prefabs += data.getBoolean("root", false) ? 1 : 0;
                    case "MeshPrimitiveComponent", "ShapeRendererComponent" -> kinds.merge("shapes", 1, Integer::sum);
                    case "Terrain" -> kinds.merge("terrains", 1, Integer::sum);
                    case "TilemapComponent" -> kinds.merge("tile maps", 1, Integer::sum);
                    case "SpriteAnimatorComponent" -> kinds.merge("sprites", 1, Integer::sum);
                    case "SculptMeshComponent" -> kinds.merge("sculpts", 1, Integer::sum);
                    default -> { }
                }
            }
        }
        List<String> parts = new ArrayList<>();
        if (prefabs > 0) {
            parts.add(prefabs + " prefab instances");
        }
        if (downloaded > 0) {
            parts.add(downloaded + " downloaded models (" + downloadedFiles.size() + " files)");
        }
        if (made > 0) {
            parts.add(made + " project-made models (" + madeFiles.size() + " files)");
        }
        kinds.forEach((kind, n) -> parts.add(n + " " + kind));
        return parts.isEmpty() ? "none" : String.join("; ", parts);
    }

    private static String migrationStatus(String sample, JsonValue group, JsonValue ledger) {
        List<String> notes = new ArrayList<>();
        for (JsonValue slice = ledger.get("slices").child; slice != null; slice = slice.next) {
            if (slice.getString("migrated").equals(sample)) {
                notes.add("migrated copy of `" + slice.getString("original") + "` (" + slice.getString("milestone") + ")");
            }
            if (slice.getString("original").equals(sample)) {
                notes.add("original kept; copy `" + slice.getString("migrated") + "` (" + slice.getString("milestone") + ")");
            }
        }
        return notes.isEmpty() ? group.getString("status") : String.join("; ", notes);
    }

    /** The replacement plan as Markdown; the same rows always give the same text. */
    public static String replacementsMarkdown(List<ReplacementRow> rows) {
        StringBuilder out = new StringBuilder();
        out.append("| Sample | Plan group | Shows now | Proposed replacement | Sources and licences | Conversion | Dependencies "
            + "| Expected visual result | Editability now (M1.1) | Collision | Rollback | Migration |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (ReplacementRow row : rows) {
            out.append("| `").append(row.sample()).append("` | ").append(row.group());
            for (String cell : List.of(row.current(), row.replacement(), row.sources(), row.conversion(), row.dependencies(),
                row.visual(), row.editability(), row.collision(), row.rollback(), row.status())) {
                out.append(" | ").append(cell.replace("|", "\\|"));
            }
            out.append(" |\n");
        }
        return out.toString();
    }

    /** The scene table as Markdown; the same rows always give the same text. */
    public static String scenesMarkdown(List<SceneRow> rows) {
        StringBuilder out = new StringBuilder();
        out.append("| Sample | Dim. | Genre | Gallery title | Entities | Loads | Behaviour | Formats used | Missing | Editable parts (M1.1) "
            + "| Licence basis | Web player | Tests naming it | M9.5 baseline |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (SceneRow row : rows) {
            out.append("| `").append(row.sample()).append("` | ").append(row.dimension()).append(" | ").append(row.genre())
                .append(" | ").append(row.template()).append(" | ").append(row.entities()).append(" | ").append(row.load())
                .append(" | ").append(row.behaviour()).append(" | ").append(join(row.formats())).append(" | ")
                .append(join(row.missing())).append(" | ").append(join(row.editable())).append(" | ").append(join(row.licences()))
                .append(" | ").append(row.web()).append(" | ").append(join(row.tests())).append(" | ")
                .append(row.baseline() ? "yes" : "-").append(" |\n");
        }
        return out.toString();
    }

    /** The content table as Markdown. */
    public static String assetsMarkdown(List<AssetRow> rows) {
        StringBuilder out = new StringBuilder();
        out.append("| File | Format | Opens in | Made by | Licence basis | Named by samples |\n");
        out.append("|---|---|---|---|---|---|\n");
        for (AssetRow row : rows) {
            out.append("| `").append(row.path()).append("` | ").append(row.format()).append(" | ").append(row.opens()).append(" | ")
                .append(row.origin()).append(" | ").append(row.licence()).append(" | ")
                .append(row.usedBy().isEmpty() ? "none" : String.join(", ", row.usedBy())).append(" |\n");
        }
        return out.toString();
    }

    private static String join(List<String> values) {
        return values.isEmpty() ? "-" : String.join(", ", values).replace("|", "\\|");
    }
}
