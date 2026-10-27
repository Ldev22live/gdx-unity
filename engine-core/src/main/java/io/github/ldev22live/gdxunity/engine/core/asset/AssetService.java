package io.github.ldev22live.gdxunity.engine.core.asset;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

import java.io.File;
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
    private final Map<AssetReference, Texture> textureCache = new ConcurrentHashMap<>();
    private final Map<AssetReference, String> loadErrors = new ConcurrentHashMap<>();
    private boolean disposed = false;

    public AssetService(AssetCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
    }

    public AssetCatalog getCatalog() {
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

        // 2. Resolve file from catalog
        File file = catalog.resolveFile(reference);
        if (file == null || !file.exists() || !file.isFile()) {
            loadErrors.put(reference, "Asset file not found on disk: " + reference.getPath());
            return null;
        }

        // 3. Decode Texture
        try {
            Texture texture = new Texture(file.getAbsolutePath());
            textureCache.put(reference, texture);
            loadErrors.remove(reference);
            return texture;
        } catch (Throwable t) {
            loadErrors.put(reference, "Failed to decode texture: " + t.getMessage());
            return null;
        }
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
