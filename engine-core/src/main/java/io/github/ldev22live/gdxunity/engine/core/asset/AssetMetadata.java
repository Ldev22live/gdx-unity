package io.github.ldev22live.gdxunity.engine.core.asset;

import java.util.Objects;

/**
 * Metadata record for a discovered asset.
 */
public record AssetMetadata(
    AssetReference reference,
    AssetType type,
    long fileSizeBytes,
    long lastModifiedMillis
) {
    public AssetMetadata {
        Objects.requireNonNull(reference, "reference cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }

    public String getPath() {
        return reference.getPath();
    }

    public String getFileName() {
        return reference.getFileName();
    }
}
