package io.github.ldev22live.gdxunity.editor.core.asset;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The "used by" list of the Content Browser (roadmap M1.5): which of the project's files name an asset. A scene, prefab, graph,
 * material, clip, song, terrain or sprite file uses an asset when one of its JSON strings is exactly the asset's project path; a Java or
 * Kotlin script uses it when a string literal in it is. The project's {@code build} folder is skipped (compiled output, exports).
 *
 * <p>A file that is not readable JSON but mentions the path is still listed: a "used by" list that drops a user on a parse error would
 * hide a real use. Only exact names are found; a path a script builds in code is not.
 */
public final class AssetUsages {
    private static final List<String> DATA_EXTENSIONS = List.of("json", "prefab", "graph", "material", "anim", "song", "terrain", "sprite");
    private static final List<String> SCRIPT_EXTENSIONS = List.of("java", "kt");
    /** Folder of build output inside a project, never searched. */
    private static final String BUILD_FOLDER = "build";

    private AssetUsages() { }

    /**
     * @param projectRoot the project's asset folder
     * @param assetPath   the asset's project-relative path, with forward slashes
     * @return the project-relative paths of the files that name it, in name order; never the asset itself
     * @throws IOException if the project cannot be read
     */
    public static List<String> usersOf(File projectRoot, String assetPath) throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        Objects.requireNonNull(assetPath, "assetPath cannot be null");
        Path root = projectRoot.toPath().toAbsolutePath().normalize();
        String target = assetPath.replace('\\', '/');
        List<String> users = new ArrayList<>();
        if (!Files.isDirectory(root) || target.isBlank()) {
            return users;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile).sorted()::iterator) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (relative.equals(target) || relative.startsWith(BUILD_FOLDER + "/")) {
                    continue;
                }
                String extension = extensionOf(relative);
                boolean data = DATA_EXTENSIONS.contains(extension);
                if (!data && !SCRIPT_EXTENSIONS.contains(extension)) {
                    continue;
                }
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (!text.contains(target)) {
                    continue;
                }
                if (data ? namesInJson(text, target) : text.contains("\"" + target + "\"")) {
                    users.add(relative);
                }
            }
        }
        return users;
    }

    /** True when a JSON string in the text is exactly the target; a file that does not parse but mentions it counts as a use. */
    private static boolean namesInJson(String text, String target) {
        JsonValue root;
        try {
            root = new JsonReader().parse(text);
        } catch (RuntimeException e) {
            // Not readable JSON, yet the path appears in it: list it rather than hide a use
            return true;
        }
        return containsString(root, target);
    }

    private static boolean containsString(JsonValue value, String target) {
        if (value == null) {
            return false;
        }
        if (value.isString()) {
            return target.equals(value.asString());
        }
        for (JsonValue child = value.child; child != null; child = child.next) {
            if (containsString(child, target)) {
                return true;
            }
        }
        return false;
    }

    private static String extensionOf(String path) {
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        return dot > slash ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
