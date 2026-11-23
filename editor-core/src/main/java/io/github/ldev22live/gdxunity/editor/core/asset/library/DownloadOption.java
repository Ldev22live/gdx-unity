package io.github.ldev22live.gdxunity.editor.core.asset.library;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One resolution of a remote asset that can be downloaded, with every file it needs (TD-095).
 *
 * @param resolution the provider's resolution key, for example {@code 2k}
 * @param files      the files, each with where it goes inside the asset's folder
 */
public record DownloadOption(String resolution, List<RemoteFile> files) {
    /** How a downloaded texture file is used by the {@code .material} made for it. */
    public enum Role {
        /** Not a texture map (a glTF, its buffer, or a texture the glTF names itself). */
        OTHER,
        /** Base colour. */
        BASE_COLOR,
        /** OpenGL-convention normal map. */
        NORMAL,
        /** Occlusion in red, roughness in green, metalness in blue, as glTF reads it. */
        OCCLUSION_ROUGHNESS_METALLIC,
        /** A grey roughness map on its own. */
        ROUGHNESS,
        /** A grey ambient occlusion map on its own. */
        AMBIENT_OCCLUSION,
        /** A zip the provider packs the asset's files in; unpacked with the pack importer's rules (only readable formats kept). */
        ARCHIVE
    }

    /**
     * One file to download.
     *
     * @param path  where it goes, relative to the asset's folder, forward slashes
     * @param url   where it is downloaded from
     * @param bytes its size as the provider states it
     * @param md5   the provider's MD5 (lower-case hex), or an empty string when the provider gives none
     * @param role  how a material uses it
     */
    public record RemoteFile(String path, String url, long bytes, String md5, Role role) {
        public RemoteFile {
            Objects.requireNonNull(path, "path cannot be null");
            Objects.requireNonNull(url, "url cannot be null");
            Objects.requireNonNull(md5, "md5 cannot be null");
            Objects.requireNonNull(role, "role cannot be null");
            if (bytes < 0) {
                throw new IllegalArgumentException("bytes cannot be negative: " + bytes);
            }
        }
    }

    public DownloadOption {
        Objects.requireNonNull(resolution, "resolution cannot be null");
        files = List.copyOf(files);
        if (files.isEmpty()) {
            throw new IllegalArgumentException("A download option needs at least one file: " + resolution);
        }
    }

    /** The total size the provider states. */
    public long totalBytes() {
        long total = 0L;
        for (RemoteFile file : files) {
            total += file.bytes();
        }
        return total;
    }

    /** The resolution as a person reads it ({@code 2K}). */
    public String label() {
        return resolution.toUpperCase(Locale.ROOT);
    }

    /** The number in the resolution key ({@code 2} for {@code 2k}), or {@link Integer#MAX_VALUE} for a key without one. */
    public int size() {
        String digits = resolution.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
    }

    /** A size in bytes as a person reads it: {@code 812 KB}, {@code 12.4 MB}, {@code 1.2 GB}. */
    public static String formatBytes(long bytes) {
        if (bytes < 1024L * 1024L) {
            return Math.max(1L, (bytes + 1023L) / 1024L) + " KB";
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
