package io.github.ldev22live.gdxunity.editor.desktop.audit;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The sample-to-template matrix of roadmap Appendix F5 (milestone M1.1): for each sample scene and each editing domain the sample
 * actually uses, whether that part can be opened and changed in the editor, as {@link Status#PASS}, {@link Status#FAIL} or
 * {@link Status#NOT_APPLICABLE} (the domain is not used), with a short reason where it is not a pass. {@link Status#BLOCKED} is for
 * a check that cannot be made yet.
 *
 * <p>It judges only what can be read from the scene file and the project's files, without a window, and by rules that are written
 * out below so a reader can disagree with them. A missing editor, a missing source or behaviour that exists only as Java source is a
 * FAIL, never "not applicable" (Appendix F5). What it cannot judge, for any sample, is the part that needs a person or a run: that a
 * user's edit succeeds, undo and redo, Play and Stop per sample, a per-sample export, a license manifest and the visual and listening
 * checks. Those are listed in {@link #UNCHECKED_GATES} and no sample is called fully customizable while they stand.
 *
 * <p>The rules, per domain:
 * <ul>
 *   <li><b>World</b>: always used; FAIL if a component has no Details editor or a named file is missing.</li>
 *   <li><b>Terrain</b>: used by a Terrain component (PASS when its file exists) or by a glTF model whose name says it is the ground
 *       (FAIL: the ground is a baked model, not a {@code .terrain}).</li>
 *   <li><b>3D models</b>: used by a glTF model that is not the ground (FAIL: there is no mesh editor, so only its placement can be
 *       changed) or by a sculpt (PASS: the sculpt editor).</li>
 *   <li><b>2D art</b>: used by a Sprite Renderer, Sprite Animator or Tilemap; FAIL when one of them names a flattened image (a PNG or
 *       JPEG) instead of a {@code .sprite} the sprite editor opens, or a missing file.</li>
 *   <li><b>Logic</b>: used when the scene has scripts or graphs; PASS for graphs only, FAIL when any behaviour is Java source.</li>
 *   <li><b>Animation</b>: used by an Animator (a {@code .anim} clip: PASS), a Sprite Animator (a {@code .sprite}: PASS) or a model
 *       that plays a clip baked into its glTF (FAIL: nothing to edit).</li>
 *   <li><b>Materials and light</b>: used by a material file or a light; PASS, as both have Details editors.</li>
 *   <li><b>Sound effects</b>: used by an Audio Source or Engine Sound; PASS for a synthesized cue (a name edited in Details), FAIL
 *       for a recorded clip (no waveform editor yet).</li>
 *   <li><b>Music</b>: used by a Music Source; PASS when its {@code .song} exists (the Music tab opens it), FAIL when it does not.</li>
 *   <li><b>UI text</b>: used by a Text component (data, edited in Details). A game that draws interface in script code is not seen
 *       here.</li>
 *   <li><b>Gameplay and physics</b>: used by physics, a character controller or scripts; PASS unless the rules are Java source.</li>
 * </ul>
 *
 * <p>Flattened imported content (Appendix F1, milestone M1.7) is listed separately as {@link Flattened}, with the project-owned
 * editable replacement the editor offers for it, or none: a glTF model can be sculpted as a project-owned copy (Customize, Edit in
 * 3D); a glTF ground has no conversion to a {@code .terrain} (M3.10), a clip baked into a glTF has no {@code .anim} equivalent in
 * the template, a PNG or JPEG has no import into a layered {@code .sprite}, and a recorded clip has no sound editor (MC.5). The
 * {@link Label} follows from the cells, the flattened content and {@link #UNCHECKED_GATES}; see {@link Entry#label()}.
 */
public final class TemplateMatrix {
    /** The result for one domain. */
    public enum Status {
        PASS("PASS"), FAIL("FAIL"), BLOCKED("BLOCKED"), NOT_APPLICABLE("n/a");

        private final String text;

        Status(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    /** The editing domains of Appendix F2 that a scene file can show (Packaging is a gate, see {@link #UNCHECKED_GATES}). */
    public enum Domain {
        WORLD("World"), TERRAIN("Terrain"), MODELS_3D("3D models"), ART_2D("2D art"), LOGIC("Logic"), ANIMATION("Animation"),
        MATERIALS("Materials, light"), SOUND("Sound effects"), MUSIC("Music"), UI("UI text"), GAMEPLAY("Gameplay, physics");

        private final String label;

        Domain(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** What a template may be called (Appendix F5 and milestone M1.7), from the most to the least editable. */
    public enum Label {
        /** Every used domain passes and every gate of Appendix F5 has been checked. */
        FULLY_CUSTOMIZABLE("Fully Customizable", "every part it uses can be edited, and every check has been made"),
        /** Every used domain passes, but the gates in {@link #UNCHECKED_GATES} have not been checked. */
        CHECKS_PENDING("Not yet rated (checks pending)",
            "every part it uses has an editor, but editing, undo, Play, export, licenses and a person's checks are not yet verified"),
        /** A used domain fails, and any flattened content has a project-owned editable replacement. */
        PARTIALLY_EDITABLE("Technical Demo / Partially Editable", "some parts it uses cannot be edited yet"),
        /** Flattened content with no editable source and no project-owned replacement (Appendix F1). */
        INCOMPLETE("INCOMPLETE", "it holds flattened content that cannot be edited and has no editable replacement in the template");

        private final String text;
        private final String meaning;

        Label(String text, String meaning) {
            this.text = text;
            this.meaning = meaning;
        }

        /** The label as shown, e.g. "Technical Demo / Partially Editable". */
        public String text() {
            return text;
        }

        /** What the label means, as a clause that follows "labelled X: ". */
        public String meaning() {
            return meaning;
        }
    }

    /**
     * Flattened imported content a sample uses (Appendix F1): a format that does not carry its editable source.
     *
     * @param domain      the domain it fails
     * @param content     what it is, e.g. "baked glTF ground (terrain-hills)"
     * @param replacement the project-owned editable replacement the editor offers, or null when there is none
     */
    public record Flattened(Domain domain, String content, String replacement) {
        public Flattened {
            Objects.requireNonNull(domain, "domain cannot be null");
            Objects.requireNonNull(content, "content cannot be null");
        }

        /** Whether the editor offers no way to replace it with project-owned editable content. */
        public boolean hasNoReplacement() {
            return replacement == null;
        }

        /** One line for a reader, e.g. "glTF models: Customize > Edit in 3D sculpts a project-owned copy". */
        public String describe() {
            return content + ": " + (replacement == null ? "no editable replacement in the template" : replacement);
        }
    }

    /** What the editor offers for a glTF model that is not the ground (milestone M1b.7). */
    static final String SCULPT_COPY = "Customize > Edit in 3D sculpts a project-owned copy";

    /** The checks of Appendix F5 that no sample passes yet because nothing makes them automatically. */
    public static final List<String> UNCHECKED_GATES = List.of(
        "a user's edit succeeds in the editor", "undo and redo of that edit", "Play and Stop of the edited sample",
        "export of the sample and launch of the export", "a license manifest for the sample's assets",
        "desktop visual and listening validation by a person");

    /**
     * The result for one domain.
     *
     * @param status   the result
     * @param evidence why, for anything that is not a pass or "not used"; empty otherwise
     */
    public record Cell(Status status, String evidence) {
        public Cell {
            Objects.requireNonNull(status, "status cannot be null");
            Objects.requireNonNull(evidence, "evidence cannot be null");
        }
    }

    /**
     * One sample's results.
     *
     * @param sample    file name of the scene
     * @param cells     the result for every domain
     * @param flattened the flattened imported content it uses, in domain order
     */
    public record Entry(String sample, Map<Domain, Cell> cells, List<Flattened> flattened) {
        public Entry {
            Objects.requireNonNull(sample, "sample cannot be null");
            cells = Map.copyOf(cells);
            flattened = List.copyOf(flattened);
        }

        /** The domains that failed. */
        public List<Domain> failures() {
            List<Domain> result = new ArrayList<>();
            for (Domain domain : Domain.values()) {
                Cell cell = cells.get(domain);
                if (cell != null && cell.status() == Status.FAIL) {
                    result.add(domain);
                }
            }
            return result;
        }

        /**
         * What the sample may be called (Appendix F5, milestone M1.7): {@link Label#INCOMPLETE} when flattened content has no
         * editable replacement; otherwise {@link Label#PARTIALLY_EDITABLE} when a used domain fails; otherwise
         * {@link Label#CHECKS_PENDING} while {@link #UNCHECKED_GATES} is not empty, and {@link Label#FULLY_CUSTOMIZABLE} only after.
         */
        public Label label() {
            if (flattened.stream().anyMatch(Flattened::hasNoReplacement)) {
                return Label.INCOMPLETE;
            }
            if (!failures().isEmpty()) {
                return Label.PARTIALLY_EDITABLE;
            }
            return UNCHECKED_GATES.isEmpty() ? Label.FULLY_CUSTOMIZABLE : Label.CHECKS_PENDING;
        }
    }

    private TemplateMatrix() { }

    /**
     * Judges one scene.
     *
     * @param assetsRoot the project's asset folder
     * @param scene      the scene file
     * @param audit      what the editability audit found in the same scene (defects and where its logic lives)
     * @throws IOException if the file cannot be read
     */
    public static Entry evaluate(File assetsRoot, File scene, EditabilityAudit.Row audit) throws IOException {
        Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null");
        Objects.requireNonNull(scene, "scene cannot be null");
        Objects.requireNonNull(audit, "audit cannot be null");
        Scan scan = Scan.of(new JsonReader().parse(Files.readString(scene.toPath())));
        Map<Domain, Cell> cells = new EnumMap<>(Domain.class);
        List<Flattened> flattened = new ArrayList<>();
        cells.put(Domain.WORLD, audit.hasDefects()
            ? new Cell(Status.FAIL, "missing editor or file") : new Cell(Status.PASS, ""));
        cells.put(Domain.TERRAIN, terrain(assetsRoot, scan, flattened));
        cells.put(Domain.MODELS_3D, models(scan, flattened));
        cells.put(Domain.ART_2D, art(assetsRoot, scan, flattened));
        cells.put(Domain.LOGIC, logic(audit));
        cells.put(Domain.ANIMATION, animation(assetsRoot, scan, flattened));
        cells.put(Domain.MATERIALS, scan.has("DirectionalLightComponent", "PointLightComponent", "SkyLightComponent", "ToneMappingComponent",
            "AtmosphereComponent", "WaterBodyComponent", "PostProcessVolumeComponent")
            || scan.anyString("MeshPrimitiveComponent", "materialPath") ? new Cell(Status.PASS, "") : notUsed());
        cells.put(Domain.SOUND, sound(scan, flattened));
        cells.put(Domain.MUSIC, music(assetsRoot, scan));
        cells.put(Domain.UI, scan.has("TextComponent") ? new Cell(Status.PASS, "") : notUsed());
        cells.put(Domain.GAMEPLAY, gameplay(audit, scan));
        return new Entry(scene.getName(), cells, flattened);
    }

    /** Judges every {@code .json} scene in a folder, in name order, using the editability audit's rows. */
    public static List<Entry> evaluateAll(File assetsRoot, File samplesFolder, List<EditabilityAudit.Row> auditRows) throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (EditabilityAudit.Row row : auditRows) {
            entries.add(evaluate(assetsRoot, new File(samplesFolder, row.sample()), row));
        }
        return entries;
    }

    private static Cell notUsed() {
        return new Cell(Status.NOT_APPLICABLE, "");
    }

    private static boolean isGround(String modelPath) {
        return baseName(modelPath).toLowerCase(Locale.ROOT).contains("terrain");
    }

    private static String baseName(String path) {
        String name = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static Cell terrain(File root, Scan scan, List<Flattened> flattened) {
        List<String> baked = new ArrayList<>();
        for (String model : scan.strings("ModelComponent", "model")) {
            if (isGround(model)) {
                baked.add(baseName(model));
            }
        }
        if (!baked.isEmpty()) {
            flattened.add(new Flattened(Domain.TERRAIN, "baked glTF ground (" + String.join(", ", new TreeSet<>(baked)) + ")", null));
            return new Cell(Status.FAIL, "ground is a baked model (" + String.join(", ", new TreeSet<>(baked)) + ")");
        }
        List<String> terrains = scan.strings("Terrain", "terrainPath");
        if (terrains.isEmpty()) {
            return notUsed();
        }
        for (String path : terrains) {
            if (!new File(root, path).isFile()) {
                return new Cell(Status.FAIL, "terrain file missing");
            }
        }
        return new Cell(Status.PASS, "");
    }

    private static Cell models(Scan scan, List<Flattened> flattened) {
        for (String model : scan.strings("ModelComponent", "model")) {
            if (!isGround(model)) {
                flattened.add(new Flattened(Domain.MODELS_3D, "glTF models", SCULPT_COPY));
                return new Cell(Status.FAIL, "glTF models, no mesh editor");
            }
        }
        return scan.has("DynamicMeshComponent") ? new Cell(Status.PASS, "") : notUsed();
    }

    private static Cell art(File root, Scan scan, List<Flattened> flattened) {
        if (!scan.has("SpriteRendererComponent", "SpriteAnimatorComponent", "TilemapComponent")) {
            return notUsed();
        }
        List<String> named = new ArrayList<>(scan.strings("SpriteAnimatorComponent", "assetPath"));
        named.addAll(scan.strings("TilemapComponent", "tileSet"));
        for (String path : named) {
            AssetType type = AssetType.fromPath(path);
            if (type == AssetType.TEXTURE) {
                flattened.add(new Flattened(Domain.ART_2D, "flattened images (PNG or JPEG)", null));
                return new Cell(Status.FAIL, "flattened image, no .sprite source");
            }
            if (!new File(root, path).isFile()) {
                return new Cell(Status.FAIL, "sprite file missing");
            }
        }
        return new Cell(Status.PASS, "");
    }

    private static Cell logic(EditabilityAudit.Row audit) {
        return switch (audit.logic()) {
            case NONE -> notUsed();
            case GRAPH -> new Cell(Status.PASS, "");
            case JAVA, JAVA_AND_GRAPH -> new Cell(Status.FAIL, "behaviour is Java source");
        };
    }

    private static Cell animation(File root, Scan scan, List<Flattened> flattened) {
        boolean bakedClip = false;
        for (JsonValue model : scan.components("ModelComponent")) {
            if (!model.getString("animation", "").isBlank()) {
                bakedClip = true;
            }
        }
        if (bakedClip) {
            flattened.add(new Flattened(Domain.ANIMATION, "clips baked into a glTF", null));
            return new Cell(Status.FAIL, "clip baked into the glTF");
        }
        List<String> clips = scan.strings("AnimatorComponent", "clip");
        for (String clip : clips) {
            if (!new File(root, clip).isFile()) {
                return new Cell(Status.FAIL, "clip file missing");
            }
        }
        return clips.isEmpty() && !scan.has("SpriteAnimatorComponent") ? notUsed() : new Cell(Status.PASS, "");
    }

    private static Cell sound(Scan scan, List<Flattened> flattened) {
        if (!scan.has("AudioSource", "EngineSound")) {
            return notUsed();
        }
        for (String sound : scan.strings("AudioSource", "sound")) {
            if (AudioSourceComponent.isClip(sound)) {
                flattened.add(new Flattened(Domain.SOUND, "recorded sound clips", null));
                return new Cell(Status.FAIL, "recorded clip, no waveform editor");
            }
        }
        return new Cell(Status.PASS, "");
    }

    private static Cell music(File root, Scan scan) {
        if (!scan.has("MusicSource")) {
            return notUsed();
        }
        for (String song : scan.strings("MusicSource", "song")) {
            if (song.isBlank() || !new File(root, song).isFile()) {
                return new Cell(Status.FAIL, "song file missing");
            }
        }
        return new Cell(Status.PASS, "");
    }

    private static Cell gameplay(EditabilityAudit.Row audit, Scan scan) {
        boolean used = audit.logic() != EditabilityAudit.Logic.NONE || scan.has("RigidBody2DComponent", "RigidBody3DComponent",
            "BoxCollider2DComponent", "BoxCollider3DComponent", "CharacterController3DComponent", "NavAgent3DComponent",
            "NavMeshBoundsComponent");
        if (!used) {
            return notUsed();
        }
        return audit.isLogicGap() ? new Cell(Status.FAIL, "rules are Java source") : new Cell(Status.PASS, "");
    }

    /** The matrix as the Markdown table kept in the feature matrix; the same entries always give the same text. */
    public static String toMarkdown(List<Entry> entries) {
        StringBuilder out = new StringBuilder("| Sample |");
        StringBuilder rule = new StringBuilder("|---|");
        for (Domain domain : Domain.values()) {
            out.append(' ').append(domain.label()).append(" |");
            rule.append("---|");
        }
        out.append(" Label (Appendix F5) |\n").append(rule).append("---|\n");
        for (Entry entry : entries) {
            out.append("| `").append(entry.sample()).append("` |");
            for (Domain domain : Domain.values()) {
                Cell cell = entry.cells().get(domain);
                out.append(' ').append(cell.status() == Status.FAIL ? "**FAIL**" : cell.status().text());
                if (!cell.evidence().isEmpty()) {
                    out.append(" (").append(cell.evidence()).append(')');
                }
                out.append(" |");
            }
            out.append(' ').append(entry.label().text());
            for (Flattened item : entry.flattened()) {
                out.append("; flattened: ").append(item.describe());
            }
            out.append(" |\n");
        }
        return out.toString();
    }

    /** What a scene file holds, by component type, so each rule can ask for the strings it needs. */
    private static final class Scan {
        private final Map<String, List<JsonValue>> byType = new java.util.HashMap<>();

        static Scan of(JsonValue root) {
            Scan scan = new Scan();
            for (JsonValue entity = root.get("entities").child; entity != null; entity = entity.next) {
                JsonValue components = entity.get("components");
                for (JsonValue component = components == null ? null : components.child; component != null; component = component.next) {
                    JsonValue data = component.get("data");
                    scan.byType.computeIfAbsent(component.getString("type"), k -> new ArrayList<>())
                        .add(data == null ? new JsonValue(JsonValue.ValueType.object) : data);
                }
            }
            return scan;
        }

        boolean has(String... types) {
            for (String type : types) {
                if (byType.containsKey(type)) {
                    return true;
                }
            }
            return false;
        }

        List<JsonValue> components(String type) {
            return byType.getOrDefault(type, List.of());
        }

        List<String> strings(String type, String key) {
            List<String> result = new ArrayList<>();
            for (JsonValue data : components(type)) {
                String value = data.getString(key, "");
                if (!value.isBlank()) {
                    result.add(value);
                }
            }
            return result;
        }

        boolean anyString(String type, String key) {
            return !strings(type, key).isEmpty();
        }
    }
}
