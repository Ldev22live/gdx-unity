package io.github.ldev22live.gdxunity.engine.core.asset;

/**
 * Standard asset types recognized by the engine.
 */
public enum AssetType {
    TEXTURE("png", "jpg", "jpeg"),
    /** glTF 2.0 models (text or binary), loaded with gdx-gltf (TD-030). */
    MODEL("gltf", "glb"),
    /** Reusable entity trees saved by the editor (TD-027). */
    PREFAB("prefab"),
    /** Visual scripts (TD-029). */
    GRAPH("graph"),
    /** Keyframe animation clips edited in the Timeline (TD-031). */
    ANIMATION("anim"),
    /** Sculpted meshes edited in sculpt mode (TD-038). */
    SCULPT("sculpt"),
    /** Recorded sound clips an Audio Source can play (Phase 20-C). */
    SOUND("wav", "ogg"),
    SPRITE("sprite"),
    /** Songs edited in the Music tab; listed in the Content Browser and opened there on double-click. */
    SONG("song"),
    /** Surface looks: PBR parameters shared by everything that names the file (TD-081). */
    MATERIAL("material"),
    /** Heightmap ground edited with brushes in the 3D view (TD-082). */
    TERRAIN("terrain"),
    /** Vector drawings (TD-104): the source of sprite layers drawn from them, edited in 2D Studio through those layers. */
    VECTOR("vector"),
    UNKNOWN;

    private final String[] extensions;

    AssetType(String... extensions) {
        this.extensions = extensions;
    }

    /** The file extensions of this type, lower case, without the dot; empty for {@link #UNKNOWN}. */
    public java.util.List<String> getExtensions() {
        return java.util.List.of(extensions);
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

    /** Type of the file at {@code path}, judged by its extension. */
    public static AssetType fromPath(String path) {
        if (path == null) return UNKNOWN;
        int dot = path.lastIndexOf('.');
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return (dot > slash && dot < path.length() - 1) ? fromExtension(path.substring(dot + 1)) : UNKNOWN;
    }
}
