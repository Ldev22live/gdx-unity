package io.github.ldev22live.gdxunity.editor.core.file;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns a file a person picked into the project-relative path the editors use (an asset path such as {@code music/theme.song}, with
 * forward slashes on every platform). The editors name only what the project holds, so a file outside the project's assets folder has
 * no such path.
 */
public final class ProjectPaths {
    private ProjectPaths() { }

    /**
     * @param root the project's assets folder
     * @param file the picked file
     * @return the path of {@code file} below {@code root}, or empty when the file is not inside it (or is the folder itself); a sibling
     * folder that merely starts with the same letters, or a path that climbs out with {@code ..}, is outside
     */
    public static Optional<String> relativeAssetPath(File root, File file) {
        Objects.requireNonNull(root, "root cannot be null");
        Objects.requireNonNull(file, "file cannot be null");
        Path base = root.toPath().toAbsolutePath().normalize();
        Path chosen = file.toPath().toAbsolutePath().normalize();
        if (!chosen.startsWith(base) || chosen.equals(base)) {
            return Optional.empty();
        }
        return Optional.of(base.relativize(chosen).toString().replace('\\', '/'));
    }
}
