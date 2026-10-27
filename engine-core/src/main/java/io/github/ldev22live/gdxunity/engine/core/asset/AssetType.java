package io.github.ldev22live.gdxunity.engine.core.asset;

/**
 * Standard asset types recognized by the engine.
 */
public enum AssetType {
    TEXTURE("png", "jpg", "jpeg"),
    UNKNOWN;

    private final String[] extensions;

    AssetType(String... extensions) {
        this.extensions = extensions;
    }

    public static AssetType fromExtension(String ext) {
        if (ext == null) return UNKNOWN;
        String clean = ext.toLowerCase();
        for (AssetType type : values()) {
            for (String e : type.extensions) {
                if (e.equals(clean)) {
                    return type;
                }
            }
        }
        return UNKNOWN;
    }
}
