package io.github.ldev22live.gdxunity.editor.sculpt;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptSurface;

import java.util.Objects;

/**
 * A sculpt open for editing: its asset, the surface being edited and what the renderer draws. It counts as modified
 * once the surface changed after the last save. Undoing every stroke still counts as modified, which errs on the
 * side of asking before data is lost.
 */
public final class SculptDocument {
    private final AssetReference asset;
    private final SculptSurface surface;
    private final SculptDynamicMesh drawable;
    private long savedRevision;

    SculptDocument(AssetReference asset, SculptSurface surface) {
        this.asset = Objects.requireNonNull(asset, "asset cannot be null");
        this.surface = Objects.requireNonNull(surface, "surface cannot be null");
        this.drawable = new SculptDynamicMesh(surface);
        this.savedRevision = surface.getRevision();
    }

    /** The asset this document was loaded from and is saved to. */
    public AssetReference getAsset() {
        return asset;
    }

    /** The surface brushes edit. */
    public SculptSurface getSurface() {
        return surface;
    }

    /** What the renderer draws for this sculpt. */
    public SculptDynamicMesh getDrawable() {
        return drawable;
    }

    /** True if the surface changed since the last save (or since loading). */
    public boolean isModified() {
        return surface.getRevision() != savedRevision;
    }

    void markSaved() {
        savedRevision = surface.getRevision();
    }
}
