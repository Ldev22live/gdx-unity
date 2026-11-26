package io.github.ldev22live.gdxunity.editor.core.project.contract;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.PreservedFields;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * {@code project-manifest.json}, the shared project contract's manifest (TD-106; schema {@code docs/contracts/project-manifest.schema.json}).
 * It is the single source of truth for a project's identity, name, start scene, scenes, assets, capabilities and revision; the
 * {@code gdxunity.project} marker mirrors the name and start scene for older readers (see {@link ProjectMarker}).
 *
 * @param projectId    stable across saves; a new one only for a new project or Save as Copy
 * @param scenes       every scene, with a stable id
 * @param assets       every file a scene reaches, with size and SHA-256
 * @param required     capabilities without which a client must open the project read-only
 * @param optional     capabilities a client may lack, editing with named limits
 * @param revision     this save's identity and the revision it was saved from
 * @param extensions   namespaced data every client keeps unchanged (compact JSON per name)
 */
public record ProjectManifest(String projectId, String name, String startScene, List<SceneEntry> scenes, List<AssetEntry> assets,
                              List<String> required, List<String> optional, Revision revision, Map<String, String> extensions) {
    /** The contract version this editor reads and writes. */
    public static final int CONTRACT_VERSION = 1;
    /** The manifest's file name at the project root. */
    public static final String FILE_NAME = "project-manifest.json";
    /** The most assets a manifest may list (as in the schema; confirmed against the shipped projects at A12). */
    public static final int MAX_ASSETS = 20000;
    /** The largest single asset accepted, bytes (512 MiB). */
    public static final long MAX_ASSET_BYTES = 512L * 1024 * 1024;

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern CAPABILITY = Pattern.compile("^[a-z][a-z0-9-]*(:[a-z0-9-]+)?/[a-z0-9.]+$");
    private static final Pattern TIMESTAMP = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$");
    private static final Set<String> KINDS = Set.of("scene", "terrain", "material", "texture", "model", "mesh", "sprite", "vector",
        "animation", "graph", "song", "sound", "prefab", "script", "other");
    private static final Set<String> MEMBERS = Set.of("contractVersion", "projectId", "name", "startScene", "scenes", "assets",
        "capabilities", "revision", "extensions");

    public ProjectManifest {
        uuid("projectId", projectId);
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new ManifestException("name must be 1 to 200 characters: " + name);
        }
        ProjectPaths.checkText(Objects.requireNonNull(startScene, "startScene cannot be null"));
        scenes = List.copyOf(scenes);
        assets = List.copyOf(assets);
        required = List.copyOf(required);
        optional = List.copyOf(optional);
        Objects.requireNonNull(revision, "revision cannot be null");
        extensions = Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
        if (scenes.isEmpty()) {
            throw new ManifestException("a project needs at least one scene");
        }
        if (scenes.stream().noneMatch(s -> s.path().equals(startScene))) {
            throw new ManifestException("startScene " + startScene + " is not one of the scenes");
        }
        if (assets.size() > MAX_ASSETS) {
            throw new ManifestException("too many assets: " + assets.size() + " (at most " + MAX_ASSETS + ")");
        }
        for (String capability : required) {
            capability("required", capability);
        }
        for (String capability : optional) {
            capability("optional", capability);
        }
    }

    /** A scene of the project. */
    public record SceneEntry(String id, String path) {
        public SceneEntry {
            uuid("scene id", id);
            ProjectPaths.checkText(Objects.requireNonNull(path, "path cannot be null"));
        }
    }

    /** A file a scene reaches, with its size and SHA-256 at the last save. */
    public record AssetEntry(String path, String kind, long size, String sha256, String licence) {
        public AssetEntry {
            ProjectPaths.checkText(Objects.requireNonNull(path, "path cannot be null"));
            if (!KINDS.contains(kind)) {
                throw new ManifestException("unknown asset kind '" + kind + "' for " + path);
            }
            if (size < 0 || size > MAX_ASSET_BYTES) {
                throw new ManifestException("asset size out of range for " + path + ": " + size);
            }
            if (sha256 == null || !SHA256.matcher(sha256).matches()) {
                throw new ManifestException("sha256 of " + path + " must be 64 lowercase hex digits: " + sha256);
            }
        }
    }

    /** A save's identity. {@code base} is the revision it was saved from, null for the first. */
    public record Revision(String id, String base, String client, String clientVersion, String savedAt) {
        public Revision {
            uuid("revision id", id);
            if (base != null) {
                uuid("base revision", base);
            }
            if (!"desktop".equals(client) && !"web".equals(client)) {
                throw new ManifestException("savedBy.client must be desktop or web: " + client);
            }
            if (clientVersion == null || clientVersion.isBlank()) {
                throw new ManifestException("savedBy.version is required");
            }
            if (savedAt == null || !TIMESTAMP.matcher(savedAt).matches()) {
                throw new ManifestException("savedAt must be an ISO-8601 UTC time: " + savedAt);
            }
        }
    }

    /**
     * Reads a manifest, strictly: a newer contract version, an unknown member, a missing member, an unsafe path, a malformed hash
     * or capability is refused with the reason. Nothing is guessed.
     *
     * @throws ManifestException naming what is wrong
     */
    public static ProjectManifest parse(String json) {
        JsonValue root;
        try {
            root = new JsonReader().parse(Objects.requireNonNull(json, "json cannot be null"));
        } catch (RuntimeException e) {
            throw new ManifestException("not JSON: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new ManifestException("the manifest must be a JSON object");
        }
        int version = root.getInt("contractVersion", -1);
        if (version > CONTRACT_VERSION) {
            throw new ManifestException("contract version " + version + " needs a newer editor (this one reads " + CONTRACT_VERSION + ")");
        }
        if (version != CONTRACT_VERSION) {
            throw new ManifestException("missing or invalid contractVersion: " + version);
        }
        for (JsonValue member = root.child; member != null; member = member.next) {
            if (!MEMBERS.contains(member.name)) {
                throw new ManifestException("unknown member '" + member.name + "' (contract version 1 allows none; use extensions)");
            }
        }
        try {
            List<SceneEntry> scenes = new ArrayList<>();
            for (JsonValue s = required(root, "scenes").child; s != null; s = s.next) {
                scenes.add(new SceneEntry(s.getString("id"), s.getString("path")));
            }
            List<AssetEntry> assets = new ArrayList<>();
            for (JsonValue a = required(root, "assets").child; a != null; a = a.next) {
                assets.add(new AssetEntry(a.getString("path"), a.getString("kind"), a.getLong("size"), a.getString("sha256"),
                    a.getString("licence", null)));
            }
            JsonValue capabilities = required(root, "capabilities");
            JsonValue revision = required(root, "revision");
            JsonValue savedBy = required(revision, "savedBy");
            JsonValue base = revision.get("base");
            Map<String, String> extensions = new LinkedHashMap<>();
            JsonValue ext = root.get("extensions");
            for (JsonValue e = ext == null ? null : ext.child; e != null; e = e.next) {
                if (!e.isObject()) {
                    throw new ManifestException("extension '" + e.name + "' must be an object");
                }
                extensions.put(e.name, PreservedFields.toJson(e));
            }
            return new ProjectManifest(root.getString("projectId"), root.getString("name"), root.getString("startScene"), scenes, assets,
                strings(required(capabilities, "required")), strings(required(capabilities, "optional")),
                new Revision(revision.getString("id"), base == null || base.isNull() ? null : base.asString(),
                    savedBy.getString("client"), savedBy.getString("version"), revision.getString("savedAt")),
                extensions);
        } catch (IllegalArgumentException e) {
            throw e instanceof ManifestException m ? m : new ManifestException(e.getMessage());
        }
    }

    /** The manifest as deterministic, indented JSON (members in schema order). */
    public String toJson() {
        StringBuilder out = new StringBuilder("{\n");
        out.append("  \"contractVersion\": ").append(CONTRACT_VERSION).append(",\n");
        out.append("  \"projectId\": ").append(q(projectId)).append(",\n");
        out.append("  \"name\": ").append(q(name)).append(",\n");
        out.append("  \"startScene\": ").append(q(startScene)).append(",\n");
        out.append("  \"scenes\": [");
        for (int i = 0; i < scenes.size(); i++) {
            out.append(i == 0 ? "\n" : ",\n").append("    {\"id\": ").append(q(scenes.get(i).id())).append(", \"path\": ")
                .append(q(scenes.get(i).path())).append('}');
        }
        out.append(scenes.isEmpty() ? "],\n" : "\n  ],\n");
        out.append("  \"assets\": [");
        for (int i = 0; i < assets.size(); i++) {
            AssetEntry a = assets.get(i);
            out.append(i == 0 ? "\n" : ",\n").append("    {\"path\": ").append(q(a.path())).append(", \"kind\": ").append(q(a.kind()))
                .append(", \"size\": ").append(a.size()).append(", \"sha256\": ").append(q(a.sha256()));
            if (a.licence() != null) {
                out.append(", \"licence\": ").append(q(a.licence()));
            }
            out.append('}');
        }
        out.append(assets.isEmpty() ? "],\n" : "\n  ],\n");
        out.append("  \"capabilities\": {\"required\": ").append(list(required)).append(", \"optional\": ").append(list(optional))
            .append("},\n");
        out.append("  \"revision\": {\"id\": ").append(q(revision.id())).append(", \"base\": ")
            .append(revision.base() == null ? "null" : q(revision.base())).append(", \"savedBy\": {\"client\": ")
            .append(q(revision.client())).append(", \"version\": ").append(q(revision.clientVersion())).append("}, \"savedAt\": ")
            .append(q(revision.savedAt())).append("}");
        if (!extensions.isEmpty()) {
            out.append(",\n  \"extensions\": {");
            boolean first = true;
            for (Map.Entry<String, String> e : extensions.entrySet()) {
                out.append(first ? "" : ", ").append(q(e.getKey())).append(": ").append(e.getValue());
                first = false;
            }
            out.append('}');
        }
        return out.append("\n}\n").toString();
    }

    /** This manifest with another revision (a save) and the given scenes, assets and capabilities. */
    public ProjectManifest withSave(Revision next, List<AssetEntry> newAssets, List<String> newRequired) {
        return new ProjectManifest(projectId, name, startScene, scenes, newAssets, newRequired, optional, next, extensions);
    }

    private static JsonValue required(JsonValue parent, String name) {
        JsonValue value = parent.get(name);
        if (value == null) {
            throw new ManifestException("missing '" + name + "'");
        }
        return value;
    }

    private static List<String> strings(JsonValue array) {
        List<String> out = new ArrayList<>();
        for (JsonValue v = array.child; v != null; v = v.next) {
            out.add(v.asString());
        }
        return out;
    }

    private static String list(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            out.append(i == 0 ? "" : ", ").append(q(values.get(i)));
        }
        return out.append(']').toString();
    }

    private static String q(String text) {
        return PreservedFields.quote(text);
    }

    private static void uuid(String what, String value) {
        try {
            if (value == null || !UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new ManifestException(what + " must be a lowercase UUID: " + value);
        }
    }

    private static void capability(String list, String value) {
        if (value == null || !CAPABILITY.matcher(value).matches()) {
            throw new ManifestException("malformed " + list + " capability: " + value);
        }
    }

    /** A manifest refused, with the reason. */
    public static final class ManifestException extends IllegalArgumentException {
        public ManifestException(String message) {
            super("project-manifest.json: " + message);
        }
    }
}
