package io.github.ldev22live.gdxunity.editor.core.asset.library;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Preview images of remote assets (UI M7, TD-095), kept on disk so a thumbnail seen once shows offline too. A thumbnail is fetched
 * only when a person looks at its asset. Only PNG or JPEG of at most {@link #MAX_BYTES} is accepted, since that is what the editor
 * decodes; anything else is refused with the reason, never shown as a blank.
 */
public final class ThumbnailCache {
    /** The largest thumbnail accepted; previews are 256 pixels, a few hundred kilobytes at most. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final HttpSource http;
    private final File cacheDir;

    /**
     * @param cacheDir the Asset Library's cache folder; thumbnails go in {@code <provider>/thumbs/<id>}
     */
    public ThumbnailCache(HttpSource http, File cacheDir) {
        this.http = Objects.requireNonNull(http, "http cannot be null");
        this.cacheDir = Objects.requireNonNull(cacheDir, "cacheDir cannot be null");
    }

    /**
     * The asset's thumbnail as PNG or JPEG bytes: from the disk cache, else fetched and cached.
     *
     * @throws IOException when the asset has no thumbnail address, it cannot be fetched and is not cached, or it is not PNG or JPEG
     */
    public byte[] thumbnail(RemoteAsset asset) throws IOException {
        Objects.requireNonNull(asset, "asset cannot be null");
        File cached = new File(new File(new File(cacheDir, asset.provider()), "thumbs"), asset.id());
        if (cached.isFile()) {
            byte[] bytes = Files.readAllBytes(cached.toPath());
            if (isImage(bytes)) {
                return bytes;
            }
        }
        if (asset.thumbnailUrl().isEmpty()) {
            throw new IOException(asset.name() + " has no preview");
        }
        byte[] bytes;
        try (InputStream in = http.open(asset.thumbnailUrl())) {
            bytes = in.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) {
            throw new IOException("The preview of " + asset.name() + " is larger than " + (MAX_BYTES >> 20) + " MB");
        }
        if (!isImage(bytes)) {
            throw new IOException("The preview of " + asset.name() + " is not a PNG or JPEG image");
        }
        Files.createDirectories(cached.getParentFile().toPath());
        File temporary = new File(cached.getParentFile(), cached.getName() + ".part");
        Files.write(temporary.toPath(), bytes);
        Files.move(temporary.toPath(), cached.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return bytes;
    }

    /** True for PNG or JPEG bytes, by their first bytes. */
    static boolean isImage(byte[] bytes) {
        return startsWith(bytes, PNG_MAGIC) || startsWith(bytes, JPEG_MAGIC);
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
