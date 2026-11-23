package io.github.ldev22live.gdxunity.engine.core.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized service managing lifecycle, caching, and disposal of GPU {@link Texture}s.
 * Ensures:
 * - Repeated requests for the same {@link AssetReference} return the cached texture.
 * - Centralized disposal of all loaded GPU textures on shutdown.
 * - Missing or corrupt textures return null or fallback without crashing the render loop.
 * - No GPU textures are loaded eagerly during asset browsing.
 */
public class AssetService implements Disposable {

    private final AssetCatalog catalog;
    private final AssetSource source;
    private final Map<AssetReference, Texture> textureCache = new ConcurrentHashMap<>();
    private final Map<AssetReference, String> loadErrors = new ConcurrentHashMap<>();
    private boolean disposed = false;

    public AssetService(AssetCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
        this.source = catalog;
    }

    /**
     * Creates a service that loads assets from a source without a scannable catalog, such as the web runtime's
     * bundled files ({@link AssetSource#internal()}). {@link #getCatalog()} is unavailable on such a service.
     *
     * @param source where asset files are read from
     */
    public AssetService(AssetSource source) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.catalog = null;
    }

    /** True when this service was created with an {@link AssetCatalog}, so {@link #getCatalog()} may be called. */
    public boolean hasCatalog() {
        return catalog != null;
    }

    public AssetCatalog getCatalog() {
        if (catalog == null) {
            throw new IllegalStateException("This AssetService reads from an AssetSource and has no catalog");
        }
        return catalog;
    }

    /**
     * Retrieves or synchronously loads the {@link Texture} corresponding to the reference.
     * Returns null and records an error if the asset cannot be found or decoded.
     *
     * @param reference the asset reference to load
     * @return loaded Texture or null on failure
     */
    public synchronized Texture loadTexture(AssetReference reference) {
        checkNotDisposed();
        if (reference == null) {
            return null;
        }

        // 1. Check cache
        if (textureCache.containsKey(reference)) {
            return textureCache.get(reference);
        }

        // 2. Resolve the file through the asset source
        FileHandle file = source.resolve(reference);
        if (!file.exists() || file.isDirectory()) {
            loadErrors.put(reference, "Asset file not found: " + reference.getPath());
            return null;
        }

        // 3. Decode Texture
        try {
            Texture texture = new Texture(file);
            textureCache.put(reference, texture);
            loadErrors.remove(reference);
            return texture;
        } catch (Throwable t) {
            loadErrors.put(reference, "Failed to decode texture: " + t.getMessage());
            return null;
        }
    }

    /**
     * The file of a project asset, for assets that are not textures (a sprite, which the component reads itself). The handle may name a file that
     * does not exist; check {@link FileHandle#exists()}.
     */
    public FileHandle resolve(AssetReference reference) {
        checkNotDisposed();
        return source.resolve(Objects.requireNonNull(reference, "reference cannot be null"));
    }

    /**
     * Checks if a texture is already loaded into GPU memory.
     */
    public boolean isLoaded(AssetReference reference) {
        return reference != null && textureCache.containsKey(reference);
    }

    /**
     * Returns the error message if a load attempt previously failed, or null.
     */
    public String getLoadError(AssetReference reference) {
        return (reference != null) ? loadErrors.get(reference) : null;
    }

    /**
     * Unloads a specific texture from cache and GPU memory.
     */
    public synchronized void unload(AssetReference reference) {
        if (reference == null || disposed) return;
        Texture tex = textureCache.remove(reference);
        if (tex != null) {
            try {
                tex.dispose();
            } catch (Throwable ignored) {}
        }
        loadErrors.remove(reference);
    }

    @Override
    public synchronized void dispose() {
        if (disposed) return;
        disposed = true;

        for (Texture tex : textureCache.values()) {
            if (tex != null) {
                try {
                    tex.dispose();
                } catch (Throwable ignored) {}
            }
        }
        textureCache.clear();
        loadErrors.clear();
    }

    public boolean isDisposed() {
        return disposed;
    }

    private void checkNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("AssetService has already been disposed");
        }
    }
}
