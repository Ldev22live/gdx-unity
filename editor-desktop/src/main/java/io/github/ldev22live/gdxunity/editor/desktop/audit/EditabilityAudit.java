package io.github.ldev22live.gdxunity.editor.desktop.audit;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorRegistry;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;

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
 * The editability audit of roadmap Phase R1: for a sample scene, whether everything in it can be opened and changed in the
 * editor. It checks the fronts that can be checked from the scene file alone, without a window:
 * <ul>
 *   <li><b>Scene</b>: every component type in the file has a Details editor;</li>
 *   <li><b>Logic</b>: whether behaviour is a visual graph, a script (a Java-only scene counts as a gap, because the graph editor
 *       cannot show it), or there is none; and that every script file exists;</li>
 *   <li><b>Assets</b>: every file the scene names exists, and its type has an editor that opens it (the class of defect where the
 *       Content Browser listed {@code .song} files that nothing could open).</li>
 * </ul>
 * Terrain, materials, animation state machines, scenario rules and prefab overrides are later phases' fronts (R2 to R5) and are
 * not audited yet. {@link #toMarkdown} renders the result as the table kept in {@code docs/FEATURE_TEST_MATRIX.md}.
 */
public final class EditabilityAudit {
    /** Where behaviour lives in a scene. */
    public enum Logic {
        /** No script or graph: the scene only shows things. */
        NONE,
        /** Only visual graphs. */
        GRAPH,
        /** Graphs next to Java scripts: a gap, because the graph editor cannot show the Java part. */
        JAVA_AND_GRAPH,
        /** Only Java scripts: a gap, because the graph editor cannot show any of it. */
        JAVA
    }

    /**
     * The editor that opens each asset type, by the evidence in {@code EditorApplication}'s Content Browser open handler and the
     * Details editors; a type missing from this map has no editor of its own (a gap the audit reports).
     */
    public static final Map<AssetType, String> OPENING_EDITORS = new EnumMap<>(AssetType.class);

    static {
        OPENING_EDITORS.put(AssetType.GRAPH, "Visual Scripting tab");
        OPENING_EDITORS.put(AssetType.ANIMATION, "Timeline");
        OPENING_EDITORS.put(AssetType.SPRITE, "Sprite and Tiles tabs");
        OPENING_EDITORS.put(AssetType.SONG, "Music tab");
    }

    /**
     * What the audit found in one scene.
     *
     * @param sample              file name of the scene
     * @param entities            number of entities
     * @param componentTypes      number of distinct component types
     * @param componentsNoEditor  component types with no Details editor, sorted
     * @param logic               where behaviour lives
     * @param missingScripts      script files the scene names that do not exist, sorted
     * @param missingAssets       asset files the scene names that do not exist, sorted
     * @param assetTypesNoEditor  asset types the scene uses that no editor opens (their names), sorted
     */
    public record Row(String sample, int entities, int componentTypes, List<String> componentsNoEditor, Logic logic,
                      List<String> missingScripts, List<String> missingAssets, List<String> assetTypesNoEditor) {
        /** Whether behaviour still lives in Java source the graph editor cannot show. */
        public boolean isLogicGap() {
            return logic == Logic.JAVA || logic == Logic.JAVA_AND_GRAPH;
        }

        /** Problems that are plain defects: something named that does not exist, or a component nobody can edit. */
        public boolean hasDefects() {
            return !componentsNoEditor.isEmpty() || !missingScripts.isEmpty() || !missingAssets.isEmpty();
        }
    }

    private final File assetsRoot;
    private final ComponentSerializerRegistry serializers;
    private final ComponentEditorRegistry<?> editors;

    /**
     * @param assetsRoot  the project's asset folder (scripts and assets are resolved from it)
     * @param serializers every component serializer the editor loads scenes with
     * @param editors     the Details editors
     */
    public EditabilityAudit(File assetsRoot, ComponentSerializerRegistry serializers, ComponentEditorRegistry<?> editors) {
        this.assetsRoot = Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null");
        this.serializers = Objects.requireNonNull(serializers, "serializers cannot be null");
        this.editors = Objects.requireNonNull(editors, "editors cannot be null");
    }

    /**
     * Audits one scene file.
     *
     * @throws IOException if the file cannot be read
     */
    public Row audit(File scene) throws IOException {
        Objects.requireNonNull(scene, "scene cannot be null");
        JsonValue root = new JsonReader().parse(Files.readString(scene.toPath()));
        TreeSet<String> types = new TreeSet<>();
        TreeSet<String> noEditor = new TreeSet<>();
        TreeSet<String> missingScripts = new TreeSet<>();
        TreeSet<String> missingAssets = new TreeSet<>();
        TreeSet<String> typesNoEditor = new TreeSet<>();
        boolean java = false;
        boolean graph = false;
        int entities = 0;
        for (JsonValue entity = root.get("entities").child; entity != null; entity = entity.next) {
            entities++;
            JsonValue components = entity.get("components");
            for (JsonValue component = components == null ? null : components.child; component != null; component = component.next) {
                String type = component.getString("type");
                types.add(type);
                if (!hasEditor(type)) {
                    noEditor.add(type);
                }
                JsonValue data = component.get("data");
                if (type.equals("EntityScriptBindingComponent")) {
                    java = true;
                    String path = data == null ? "" : data.getString("scriptPath", "");
                    if (path.isBlank() || !new File(assetsRoot, path).isFile()) {
                        missingScripts.add(path.isBlank() ? "(no script path on " + entity.getString("name") + ")" : path);
                    }
                } else if (type.equals("GraphScriptComponent")) {
                    graph = true;
                }
                if (data != null) {
                    collectAssets(data, missingAssets, typesNoEditor);
                }
            }
        }
        Logic logic = java ? (graph ? Logic.JAVA_AND_GRAPH : Logic.JAVA) : graph ? Logic.GRAPH : Logic.NONE;
        return new Row(scene.getName(), entities, types.size(), List.copyOf(noEditor), logic, List.copyOf(missingScripts),
            List.copyOf(missingAssets), List.copyOf(typesNoEditor));
    }

    /** Audits every {@code .json} scene in a folder, in name order. */
    public List<Row> auditAll(File samplesFolder) throws IOException {
        File[] files = Objects.requireNonNull(samplesFolder, "samplesFolder cannot be null")
            .listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            throw new IOException("Not a folder: " + samplesFolder);
        }
        java.util.Arrays.sort(files);
        List<Row> rows = new ArrayList<>();
        for (File file : files) {
            rows.add(audit(file));
        }
        return rows;
    }

    private boolean hasEditor(String typeId) {
        ComponentSerializer<?> serializer = serializers.getByTypeId(typeId);
        if (serializer == null) {
            return false;
        }
        Class<? extends Component> componentClass = serializer.getComponentClass();
        return editors.hasProvider(componentClass);
    }

    /** Finds strings in a component's data that name an asset file (a known extension), checks them and their editor. */
    private void collectAssets(JsonValue value, TreeSet<String> missingAssets, TreeSet<String> typesNoEditor) {
        if (value.isString()) {
            String text = value.asString();
            AssetType type = AssetType.fromPath(text);
            if (type != AssetType.UNKNOWN && !text.contains(" ") && !text.startsWith("scripts/")) {
                if (!new File(assetsRoot, text).isFile()) {
                    missingAssets.add(text);
                }
                if (!OPENING_EDITORS.containsKey(type)) {
                    typesNoEditor.add(type.name().toLowerCase(Locale.ROOT));
                }
            }
            return;
        }
        for (JsonValue child = value.child; child != null; child = child.next) {
            collectAssets(child, missingAssets, typesNoEditor);
        }
    }

    /** The audit as the Markdown table kept in the feature matrix; the same rows always give the same text. */
    public static String toMarkdown(List<Row> rows) {
        StringBuilder out = new StringBuilder();
        out.append("| Sample | Entities | Component types | Scene (no Details editor) | Logic | Missing files | Assets with no opening editor |\n");
        out.append("|---|---|---|---|---|---|---|\n");
        for (Row row : rows) {
            List<String> missing = new ArrayList<>(row.missingScripts());
            missing.addAll(row.missingAssets());
            out.append("| `").append(row.sample()).append("` | ").append(row.entities()).append(" | ").append(row.componentTypes())
                .append(" | ").append(row.componentsNoEditor().isEmpty() ? "complete" : String.join(", ", row.componentsNoEditor()))
                .append(" | ").append(switch (row.logic()) {
                    case NONE -> "no logic";
                    case GRAPH -> "graph";
                    case JAVA_AND_GRAPH -> "**graph + Java (gap)**";
                    case JAVA -> "**Java (gap)**";
                })
                .append(" | ").append(missing.isEmpty() ? "none" : String.join(", ", missing))
                .append(" | ").append(row.assetTypesNoEditor().isEmpty() ? "none" : String.join(", ", row.assetTypesNoEditor()))
                .append(" |\n");
        }
        return out.toString();
    }
}
