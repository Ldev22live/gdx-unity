package io.github.ldev22live.gdxunity.editor.core.project.contract;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The asset list of a project manifest and its checks (TD-106 points 2 and 7, roadmap A12.3 and A12.5).
 *
 * <p><b>Which files are listed.</b> Every file a scene reaches: a string value anywhere in a scene that names an existing project
 * file; then, transitively, string values in reached JSON assets ({@code .material}, {@code .prefab}, {@code .sprite} and other JSON
 * files, paths relative to the project root) and the {@code uri}s of a reached {@code .gltf} (relative to the glTF's folder).
 * Strings that are not existing project files (names, tags, text) are ignored; a path that would leave the project is never
 * followed. The list is sorted by path, so the same project gives the same manifest.
 *
 * <p><b>Hashes.</b> SHA-256 of the file's bytes, lowercase hex, as the schema says.
 */
public final class AssetInventory {
    private static final int BUFFER_BYTES = 64 * 1024;

    private AssetInventory() {
    }

    /** The SHA-256 of {@code file}, lowercase hex. */
    public static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            for (int n; (n = in.read(buffer)) > 0; ) {
                digest.update(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * The manifest entries for every file the given scenes reach (the scenes themselves included), sorted by path.
     *
     * @param licences licence record ids by path (from the TD-094 manifest); files without one are project-owned
     */
    public static List<ProjectManifest.AssetEntry> inventory(Path projectRoot, List<String> scenePaths, Map<String, String> licences)
        throws IOException {
        return inventory(projectRoot, scenePaths, licences, Map.of());
    }

    static List<ProjectManifest.AssetEntry> inventory(Path projectRoot, List<String> scenePaths, Map<String, String> licences,
                                                      Map<String, Path> stagedScenes) throws IOException {
        Objects.requireNonNull(licences, "licences cannot be null");
        Map<String, ProjectManifest.AssetEntry> found = new TreeMap<>();
        Deque<String> queue = new ArrayDeque<>(scenePaths);
        while (!queue.isEmpty()) {
            String path = queue.poll();
            if (found.containsKey(path)) {
                continue;
            }
            Path file = stagedScenes.getOrDefault(path, ProjectPaths.resolve(projectRoot, path));
            if (!Files.isRegularFile(file)) {
                continue;
            }
            found.put(path, new ProjectManifest.AssetEntry(path, stagedScenes.containsKey(path) ? "scene" : kindOf(path), Files.size(file), sha256(file), licences.get(path)));
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".gltf")) {
                String folder = path.contains("/") ? path.substring(0, path.lastIndexOf('/') + 1) : "";
                for (String uri : strings(file, "uri")) {
                    if (!uri.regionMatches(true, 0, "data:", 0, 5)) {
                        enqueue(projectRoot, folder + uri, queue);
                    }
                }
            } else if (stagedScenes.containsKey(path) || lower.endsWith(".json") || lower.endsWith(".material") || lower.endsWith(".prefab") || lower.endsWith(".sprite")
                || lower.endsWith(".anim") || lower.endsWith(".graph") || lower.endsWith(".song")) {
                for (String value : strings(file, null)) {
                    enqueue(projectRoot, value, queue);
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    private static void enqueue(Path projectRoot, String candidate, Deque<String> queue) throws IOException {
        if (candidate.isEmpty() || candidate.length() > ProjectPaths.MAX_LENGTH || candidate.startsWith("data:")) {
            return;
        }
        try {
            ProjectPaths.checkText(candidate);
        } catch (ProjectPaths.UnsafePathException e) {
            return; // not a project path (text, an absolute path, a name): never followed
        }
        if (Files.isRegularFile(ProjectPaths.resolve(projectRoot, candidate))) {
            queue.add(candidate);
        }
    }

    /** String values of a JSON file: every one, or only those of members named {@code member}. Non-JSON files give none. */
    private static List<String> strings(Path file, String member) throws IOException {
        List<String> out = new ArrayList<>();
        JsonValue root;
        try {
            root = new JsonReader().parse(Files.readString(file));
        } catch (RuntimeException e) {
            return out;
        }
        collect(root, member, out);
        return out;
    }

    private static void collect(JsonValue value, String member, List<String> out) {
        for (JsonValue child = value == null ? null : value.child; child != null; child = child.next) {
            if (child.isString() && (member == null || member.equals(child.name))) {
                out.add(child.asString());
            }
            collect(child, member, out);
        }
    }

    /** The manifest kind of a path, by extension. */
    public static String kindOf(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        String ext = dot < 0 ? "" : lower.substring(dot + 1);
        return switch (ext) {
            case "json" -> lower.startsWith("samples/") || lower.startsWith("scenes/") ? "scene" : "other";
            case "terrain" -> "terrain";
            case "material" -> "material";
            case "png", "jpg", "jpeg", "ktx2", "hdr" -> "texture";
            case "gltf", "glb", "bin", "obj", "fbx" -> "model";
            case "mesh", "sculpt" -> "mesh";
            case "sprite" -> "sprite";
            case "vector" -> "vector";
            case "anim" -> "animation";
            case "graph" -> "graph";
            case "song" -> "song";
            case "wav", "ogg", "mp3", "sf2" -> "sound";
            case "prefab" -> "prefab";
            case "java", "kt" -> "script";
            default -> "other";
        };
    }

    /** What is wrong with a listed asset on disk. */
    public enum Issue { MISSING, MODIFIED, UNSAFE_PATH }

    /** One finding of {@link #verify}. */
    public record Finding(String path, Issue issue, String detail) {
    }

    /**
     * Checks every listed asset: present, inside the project, same size and SHA-256. A finding is reported, never repaired: no
     * other file is substituted and nothing is downloaded.
     */
    public static List<Finding> verify(Path projectRoot, ProjectManifest manifest) throws IOException {
        List<Finding> findings = new ArrayList<>();
        for (ProjectManifest.AssetEntry asset : manifest.assets()) {
            Path file;
            try {
                file = ProjectPaths.resolve(projectRoot, asset.path());
            } catch (ProjectPaths.UnsafePathException e) {
                findings.add(new Finding(asset.path(), Issue.UNSAFE_PATH, e.getMessage()));
                continue;
            }
            if (!Files.isRegularFile(file)) {
                findings.add(new Finding(asset.path(), Issue.MISSING, "not in the project"));
            } else if (Files.size(file) != asset.size() || !sha256(file).equals(asset.sha256())) {
                findings.add(new Finding(asset.path(), Issue.MODIFIED, "its contents differ from the manifest's SHA-256"));
            }
        }
        return findings;
    }
}
