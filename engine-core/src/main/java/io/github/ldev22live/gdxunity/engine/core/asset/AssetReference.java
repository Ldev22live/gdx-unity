package io.github.ldev22live.gdxunity.engine.core.asset;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Immutable identifier and normalized relative path for an asset within the asset repository.
 *
 * Requirements:
 * - Independent of absolute machine-specific paths.
 * - Normalized with forward slashes ("/").
 * - Rejects path traversal (".." or leading "/").
 */
public final class AssetReference {
    private final String path;

    public AssetReference(String path) {
        this.path = normalizeAndValidate(path);
    }

    public static String normalizeAndValidate(String rawPath) {
        Objects.requireNonNull(rawPath, "Asset path cannot be null");
        String trimmed = rawPath.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Asset path cannot be empty");
        }

        // Replace backslashes with forward slashes
        String normalized = trimmed.replace('\\', '/');

        // Remove redundant leading slashes
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Asset path cannot resolve to empty root");
        }

        // Reject absolute paths like Windows drive letters (C:)
        if (normalized.length() >= 2 && Character.isLetter(normalized.charAt(0)) && normalized.charAt(1) == ':') {
            throw new IllegalArgumentException("Absolute paths are not permitted in AssetReference: " + rawPath);
        }

        // Validate path traversal
        Path p = Paths.get(normalized);
        if (p.isAbsolute()) {
            throw new IllegalArgumentException("Absolute paths are not permitted in AssetReference: " + rawPath);
        }
        for (Path part : p) {
            String name = part.toString();
            if (name.equals("..")) {
                throw new IllegalArgumentException("Path traversal ('..') is not permitted: " + rawPath);
            }
        }

        return normalized;
    }

    public String getPath() {
        return path;
    }

    public String getFileName() {
        int idx = path.lastIndexOf('/');
        return (idx >= 0) ? path.substring(idx + 1) : path;
    }

    public String getExtension() {
        String name = getFileName();
        int idx = name.lastIndexOf('.');
        return (idx >= 0 && idx < name.length() - 1) ? name.substring(idx + 1).toLowerCase() : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssetReference that = (AssetReference) o;
        return path.equalsIgnoreCase(that.path);
    }

    @Override
    public int hashCode() {
        return path.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return path;
    }
}
