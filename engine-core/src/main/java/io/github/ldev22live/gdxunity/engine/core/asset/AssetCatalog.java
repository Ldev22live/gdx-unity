package io.github.ldev22live.gdxunity.engine.core.asset;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Scans a configured asset root directory on disk and maintains an inventory of discovered assets.
 * Pure filesystem metadata discovery without eager GPU memory allocation.
 */
public class AssetCatalog {

    private final File rootDir;
    private final Map<AssetReference, AssetMetadata> assetsByRef = new LinkedHashMap<>();

    public AssetCatalog(File rootDir) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir cannot be null");
    }

    public File getRootDir() {
        return rootDir;
    }

    /**
     * Recursively scans the root directory for supported image assets.
     */
    public synchronized void scan() {
        assetsByRef.clear();
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            return;
        }

        Path rootPath = rootDir.toPath().toAbsolutePath().normalize();

        try (Stream<Path> stream = Files.walk(rootPath)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String fileName = p.getFileName().toString();
                int dotIdx = fileName.lastIndexOf('.');
                if (dotIdx > 0 && dotIdx < fileName.length() - 1) {
                    String ext = fileName.substring(dotIdx + 1).toLowerCase();
                    AssetType type = AssetType.fromExtension(ext);
                    if (type != AssetType.UNKNOWN) {
                        Path rel = rootPath.relativize(p.toAbsolutePath().normalize());
                        try {
                            AssetReference ref = new AssetReference(rel.toString());
                            long size = Files.size(p);
                            long modified = Files.getLastModifiedTime(p).toMillis();
                            AssetMetadata meta = new AssetMetadata(ref, type, size, modified);
                            assetsByRef.put(ref, meta);
                        } catch (Exception ignored) {
                            // Skip files that fail normalization or validation
                        }
                    }
                }
            });
        } catch (IOException ignored) {
            // Unreadable directory or I/O error
        }
    }

    public synchronized List<AssetMetadata> getAllAssets() {
        List<AssetMetadata> list = new ArrayList<>(assetsByRef.values());
        list.sort(Comparator.comparing(AssetMetadata::getPath));
        return Collections.unmodifiableList(list);
    }

    public synchronized AssetMetadata getMetadata(AssetReference reference) {
        return assetsByRef.get(reference);
    }

    public synchronized boolean hasAsset(AssetReference reference) {
        return assetsByRef.containsKey(reference);
    }

    public synchronized File resolveFile(AssetReference reference) {
        if (reference == null) return null;
        return new File(rootDir, reference.getPath());
    }
}
