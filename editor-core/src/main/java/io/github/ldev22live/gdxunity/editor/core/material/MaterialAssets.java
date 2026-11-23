package io.github.ldev22live.gdxunity.editor.core.material;

import io.github.ldev22live.gdxunity.engine.material.Material;
import io.github.ldev22live.gdxunity.engine.material.MaterialLibrary;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

/** Creating {@code .material} files in a project (TD-081). */
public final class MaterialAssets {
    /** Folder materials are created in, relative to the project root. */
    public static final String FOLDER = "materials";
    /** File extension of a material, with the dot. */
    public static final String EXTENSION = ".material";

    private MaterialAssets() { }

    /** Whether {@code path} names a material file, by its extension. */
    public static boolean isMaterialPath(String path) {
        return path != null && path.trim().toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    /**
     * Creates a new material with the default look in {@code materials/}, under a name no other material file uses ({@code Name.material},
     * then {@code Name 2.material} and so on), and registers it with the library.
     *
     * @param assetRoot the project's asset folder
     * @param baseName  the name to start from; blank gives "Material"
     * @return the project-relative path of the new file
     */
    public static String createNew(File assetRoot, MaterialLibrary library, String baseName) {
        Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        Objects.requireNonNull(library, "library cannot be null");
        String name = baseName == null || baseName.isBlank() ? "Material" : baseName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        String path = FOLDER + "/" + name + EXTENSION;
        for (int n = 2; new File(assetRoot, path).exists(); n++) {
            path = FOLDER + "/" + name + " " + n + EXTENSION;
        }
        Material material = new Material();
        material.setName(path.substring(FOLDER.length() + 1, path.length() - EXTENSION.length()));
        library.save(path, material);
        return path;
    }
}
