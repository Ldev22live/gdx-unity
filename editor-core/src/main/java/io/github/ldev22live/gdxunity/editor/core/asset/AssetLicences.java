package io.github.ldev22live.gdxunity.editor.core.asset;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetProvenance;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * The licence line the Content Browser shows for a selected file (roadmap M2b.5, TD-099; the licence detail M2b.1 left for files
 * already in a project). The project's records are read each time, so the line is never stale after an import.
 */
public final class AssetLicences {
    private AssetLicences() {
    }

    /**
     * "licence: CC0-1.0: Nature Kit by Kenney (...)", "licence: Apache-2.0: made in this repository with ...", "licence: No licence
     * record (...)", or why the records could not be read.
     *
     * @param assetsRoot the project's asset root
     * @param path       the file, relative to it with forward slashes
     */
    public static String describe(File assetsRoot, String path) {
        Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
        try {
            return "licence: " + AssetProvenance.read(assetsRoot).of(path).summary();
        } catch (IOException | IllegalArgumentException e) {
            return "licence records could not be read: " + e.getMessage();
        }
    }
}
