package io.github.ldev22live.gdxunity.editor.core.asset.library;

/** The kinds of asset the Asset Library downloads from a provider (TD-095), each with the project folder its files go under. */
public enum AssetType {
    /** A glTF model with its buffers and textures. */
    MODEL("Models", "models", "model"),
    /** A set of PBR texture maps, imported with a {@code .material} that uses them. */
    TEXTURE("Textures", "textures", "texture");

    private final String label;
    private final String folder;
    private final String category;

    AssetType(String label, String folder, String category) {
        this.label = label;
        this.folder = folder;
        this.category = category;
    }

    /** The name shown in the Asset Library. */
    public String label() {
        return label;
    }

    /** The top project folder its files go under ({@code models/thirdparty/...}). */
    public String folder() {
        return folder;
    }

    /** The manifest's category word (TD-094). */
    public String category() {
        return category;
    }
}
