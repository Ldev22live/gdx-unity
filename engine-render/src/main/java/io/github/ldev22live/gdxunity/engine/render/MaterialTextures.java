package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The textures materials name (TD-081), read from the project and kept one per path. Textures repeat, so tiling works, and are mip
 * mapped. A file that is missing or is not an image gives no texture and a stored reason ({@link #getError}); nothing is invented in
 * its place. A failure is not remembered: asking again tries again, which suits the caller, who asks only when a material changed.
 * Needs a graphics context to load; owns the textures and disposes them.
 */
final class MaterialTextures implements TextureSource, Disposable {
    private final AssetSource source;
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, String> errors = new HashMap<>();

    MaterialTextures(AssetSource source) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
    }

    @Override
    public Texture get(String path) {
        String key = Objects.requireNonNull(path, "path cannot be null").trim().replace('\\', '/');
        Texture cached = textures.get(key);
        if (cached != null) {
            return cached;
        }
        errors.remove(key);
        FileHandle file = source.resolve(new AssetReference(key));
        if (!file.exists()) {
            errors.put(key, "texture file not found: " + key);
            return null;
        }
        try {
            Texture texture = new Texture(file, Pixmap.Format.RGBA8888, true);
            texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            textures.put(key, texture);
            return texture;
        } catch (GdxRuntimeException e) {
            errors.put(key, key + ": " + e.getMessage());
            return null;
        }
    }

    /** Why {@code path} has no texture, or null if it has one or was never asked for. */
    String getError(String path) {
        return errors.get(path.trim().replace('\\', '/'));
    }

    int size() {
        return textures.size();
    }

    /** Disposes every texture; all are attempted and failures reported together (TD-008). Idempotent. */
    @Override
    public void dispose() {
        ResourceManager owned = new ResourceManager();
        textures.values().forEach(owned::register);
        textures.clear();
        owned.dispose();
    }
}
