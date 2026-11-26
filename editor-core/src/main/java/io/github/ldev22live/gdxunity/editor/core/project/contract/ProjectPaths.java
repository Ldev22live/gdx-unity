package io.github.ldev22live.gdxunity.editor.core.project.contract;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Project-relative paths of the shared project contract (TD-106 point 7, roadmap A12.5). A path must be relative, use forward
 * slashes, contain no {@code ..} segment, no drive letter, no percent-encoded separator or dot, and must resolve, after following
 * any symbolic link, to a location inside the project folder. The schema's pattern is not trusted alone: every path is also checked
 * on the real file system.
 */
public final class ProjectPaths {
    /** The longest path accepted (as in the manifest schema). */
    public static final int MAX_LENGTH = 512;
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:");
    private static final Pattern ENCODED = Pattern.compile("%[0-9A-Fa-f]{2}");

    private ProjectPaths() {
    }

    /**
     * Checks the text of {@code path} without touching the disk.
     *
     * @throws UnsafePathException naming the reason
     */
    public static void checkText(String path) {
        Objects.requireNonNull(path, "path cannot be null");
        if (path.isEmpty() || path.length() > MAX_LENGTH) {
            throw new UnsafePathException(path, "it must be 1 to " + MAX_LENGTH + " characters long");
        }
        if (path.indexOf('\0') >= 0) {
            throw new UnsafePathException(path, "it contains a NUL character");
        }
        if (path.contains("\\")) {
            throw new UnsafePathException(path, "backslashes are not allowed; use '/'");
        }
        if (path.startsWith("/") || DRIVE.matcher(path).find()) {
            throw new UnsafePathException(path, "it is absolute; project paths are relative to the project folder");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.equals("..")) {
                throw new UnsafePathException(path, "it leaves the project folder ('..')");
            }
            if (segment.isEmpty() || segment.equals(".")) {
                throw new UnsafePathException(path, "it has an empty or '.' segment");
            }
            if (ENCODED.matcher(segment).find()) {
                String decoded;
                try {
                    decoded = URLDecoder.decode(segment, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    throw new UnsafePathException(path, "it has a malformed percent-encoding");
                }
                if (decoded.contains("/") || decoded.contains("\\") || decoded.equals("..") || decoded.equals(".")) {
                    throw new UnsafePathException(path, "it percent-encodes a separator or a dot segment");
                }
            }
        }
    }

    /**
     * {@code path} resolved inside {@code projectRoot}. For a file that exists, its real path (symbolic links followed) must still be
     * inside the project's real folder.
     *
     * @throws UnsafePathException when the text is unsafe or the resolved location leaves the project
     * @throws IOException when the project folder cannot be read
     */
    public static Path resolve(Path projectRoot, String path) throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        checkText(path);
        Path root = projectRoot.toRealPath();
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new UnsafePathException(path, "it resolves outside the project folder");
        }
        Path existing = resolved;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing != null && !existing.toRealPath().startsWith(root)) {
            throw new UnsafePathException(path, "a symbolic link takes it outside the project folder");
        }
        return resolved;
    }

    /** A project path refused for a stated reason. */
    public static final class UnsafePathException extends IllegalArgumentException {
        private final String path;

        public UnsafePathException(String path, String reason) {
            super("Unsafe project path '" + path + "': " + reason);
            this.path = path;
        }

        /** The refused path. */
        public String getPath() {
            return path;
        }
    }
}
