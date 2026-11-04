package io.github.ldev22live.gdxunity.editor.sculpt;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.DynamicMesh;
import io.github.ldev22live.gdxunity.engine.render.DynamicMeshSource;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.sculpt.BrushSettings;
import io.github.ldev22live.gdxunity.engine.sculpt.BrushType;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptMesh;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptSurface;
import io.github.ldev22live.gdxunity.engine.sculpt.SymmetrySettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The sculpts open in the editor, the current brush, and whether sculpt mode is on (TD-038). It is the
 * {@link DynamicMeshSource} the renderer asks for the geometry of {@link DynamicMeshComponent}s: a sculpt a scene
 * refers to is loaded the first time it is needed, and what the renderer draws is the live surface being edited.
 *
 * <p>A file that cannot be loaded is remembered with its error, so it is not read again every frame; closing the
 * sculpt or {@link #reload} tries again. Not thread-safe: use it on the render thread.
 */
public class SculptSession implements DynamicMeshSource {
    /**
     * Brush radius a session starts with, in the sculpt's own units: a bit under a third of the radius of a new
     * sculpt ({@link SculptAssets#DEFAULT_RADIUS}), so the first stroke is neither a pin prick nor the whole sphere.
     */
    public static final float DEFAULT_BRUSH_RADIUS = 15f;

    private final SculptAssets assets;
    private final Map<String, SculptDocument> documents = new LinkedHashMap<>();
    private final Map<String, String> loadErrors = new LinkedHashMap<>();

    private boolean sculptMode;
    private boolean wireframe;
    private BrushType brushType = BrushType.DRAW;
    private final BrushSettings brush = new BrushSettings();
    private final SymmetrySettings symmetry = new SymmetrySettings();

    /** Creates a session that reads and writes sculpts through {@code assets}. */
    public SculptSession(SculptAssets assets) {
        this.assets = Objects.requireNonNull(assets, "assets cannot be null");
        brush.setRadius(DEFAULT_BRUSH_RADIUS);
    }

    /** The assets the session reads and writes. */
    public SculptAssets getAssets() {
        return assets;
    }

    /**
     * Opens a sculpt, or returns it if it is already open.
     *
     * @throws IOException if it cannot be loaded; the error is also remembered for {@link #getLoadError}
     */
    public SculptDocument open(AssetReference asset) throws IOException {
        Objects.requireNonNull(asset, "asset cannot be null");
        SculptDocument open = documents.get(asset.getPath());
        if (open != null) {
            return open;
        }
        try {
            SculptMesh mesh = assets.load(asset);
            SculptDocument document = new SculptDocument(asset, new SculptSurface(mesh));
            documents.put(asset.getPath(), document);
            loadErrors.remove(asset.getPath());
            return document;
        } catch (IOException | RuntimeException e) {
            loadErrors.put(asset.getPath(), e.getMessage());
            if (e instanceof IOException io) {
                throw io;
            }
            throw new IOException(asset.getPath() + ": " + e.getMessage(), e);
        }
    }

    /** The open sculpt at a path, or null. Does not load anything. */
    public SculptDocument getDocument(String path) {
        return documents.get(new AssetReference(path).getPath());
    }

    /** The open sculpt used by an entity's {@link DynamicMeshComponent}, or null if it has none or it is not open. */
    public SculptDocument getDocumentOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        for (Component component : entity.getAllComponents()) {
            if (component.isEnabled() && component instanceof DynamicMeshComponent mesh) {
                return getDocument(mesh.getMeshPath());
            }
        }
        return null;
    }

    /** The open sculpts, in the order they were opened. */
    public List<SculptDocument> getOpenDocuments() {
        return Collections.unmodifiableList(new ArrayList<>(documents.values()));
    }

    /** Why a sculpt could not be loaded, or null if it loaded or was never asked for. */
    public String getLoadError(String path) {
        return loadErrors.get(new AssetReference(path).getPath());
    }

    /**
     * Writes a sculpt to its file.
     *
     * @throws IOException if the file cannot be written
     * @throws IllegalArgumentException if the sculpt is not open
     */
    public void save(AssetReference asset) throws IOException {
        SculptDocument document = documents.get(asset.getPath());
        if (document == null) {
            throw new IllegalArgumentException("Sculpt is not open: " + asset.getPath());
        }
        assets.save(asset, document.getSurface().getMesh());
        document.markSaved();
    }

    /**
     * Writes every modified sculpt. Every save is attempted; the first failure is thrown with the others attached.
     *
     * @return the number of sculpts written
     * @throws IOException if any file cannot be written
     */
    public int saveAllModified() throws IOException {
        int saved = 0;
        IOException failure = null;
        for (SculptDocument document : new ArrayList<>(documents.values())) {
            if (!document.isModified()) {
                continue;
            }
            try {
                save(document.getAsset());
                saved++;
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        return saved;
    }

    /** Closes a sculpt without saving; later use loads it from its file again. */
    public void close(AssetReference asset) {
        documents.remove(asset.getPath());
        loadErrors.remove(asset.getPath());
    }

    /** Throws away the open copy and the remembered error of a sculpt, so it is loaded again when next needed. */
    public void reload(AssetReference asset) {
        close(asset);
    }

    /** True if any open sculpt has unsaved changes. */
    public boolean hasModifiedDocuments() {
        for (SculptDocument document : documents.values()) {
            if (document.isModified()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The renderer's question: the geometry for a mesh path. Loads the sculpt on first use; returns null (and
     * remembers the error) when it cannot be loaded.
     */
    @Override
    public DynamicMesh find(String path) {
        String key;
        try {
            key = new AssetReference(path).getPath();
        } catch (IllegalArgumentException e) {
            return null;
        }
        SculptDocument open = documents.get(key);
        if (open != null) {
            return open.getDrawable();
        }
        if (loadErrors.containsKey(key)) {
            return null;
        }
        try {
            return open(new AssetReference(key)).getDrawable();
        } catch (IOException e) {
            return null;
        }
    }

    /** Whether sculpt mode is on: left-drag in the 3D viewport sculpts the selected sculpt. */
    public boolean isSculptMode() {
        return sculptMode;
    }

    /** Turns sculpt mode on or off. */
    public void setSculptMode(boolean sculptMode) {
        this.sculptMode = sculptMode;
    }

    /** Whether the edges of the sculpts are drawn over their surface. */
    @Override
    public boolean isWireframe() {
        return wireframe;
    }

    /** Turns the wireframe overlay on or off. */
    public void setWireframe(boolean wireframe) {
        this.wireframe = wireframe;
    }

    /** The selected brush. */
    public BrushType getBrushType() {
        return brushType;
    }

    /** Selects the brush. */
    public void setBrushType(BrushType brushType) {
        this.brushType = Objects.requireNonNull(brushType, "brushType cannot be null");
    }

    /** The brush settings; changing them affects the next stroke. */
    public BrushSettings getBrush() {
        return brush;
    }

    /** The mirror planes; changing them affects the next stroke. */
    public SymmetrySettings getSymmetry() {
        return symmetry;
    }
}
