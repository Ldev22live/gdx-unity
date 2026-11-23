package io.github.ldev22live.gdxunity.engine.material;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The project's materials by path (TD-081). Everything that names a {@code .material} file gets the <em>same</em> {@link Material}
 * object, so changing one in the editor changes every surface that uses it at once (shared materials). A file that is missing or
 * cannot be read gives no material and a stored explanation ({@link #getError}); nothing is invented in its place, so callers must
 * decide what a surface without its material looks like and the editor can say why.
 */
public final class MaterialLibrary {
    private final AssetSource source;
    private final Map<String, Material> materials = new HashMap<>();
    private final Map<String, String> errors = new HashMap<>();

    public MaterialLibrary(AssetSource source) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
    }

    /** Where the project's files come from; a renderer reads the textures a material names from the same place. */
    public AssetSource getSource() {
        return source;
    }

    /** The path as the library keys it: trimmed, with forward slashes. */
    static String key(String path) {
        return Objects.requireNonNull(path, "path cannot be null").trim().replace('\\', '/');
    }

    /**
     * The material in {@code path}, read on first use.
     *
     * @return the shared material, or null when the file is missing or unreadable ({@link #getError} says why)
     */
    public Material get(String path) {
        String key = key(path);
        Material cached = materials.get(key);
        if (cached != null) {
            return cached;
        }
        if (errors.containsKey(key)) {
            return null;
        }
        FileHandle file = source.resolve(new AssetReference(key));
        if (!file.exists()) {
            errors.put(key, "material file not found: " + key);
            return null;
        }
        try {
            Material material = MaterialIO.fromJson(file.readString("UTF-8"));
            materials.put(key, material);
            return material;
        } catch (MaterialFormatException | GdxRuntimeException e) {
            errors.put(key, key + ": " + e.getMessage());
            return null;
        }
    }

    /** Why {@code path} has no material, or null if it has one or was never asked for. */
    public String getError(String path) {
        return errors.get(key(path));
    }

    /**
     * Writes a material to its file and makes it the shared one for that path.
     *
     * @throws GdxRuntimeException if the file cannot be written
     */
    public void save(String path, Material material) {
        Objects.requireNonNull(material, "material cannot be null");
        String key = key(path);
        FileHandle file = source.resolve(new AssetReference(key));
        file.writeString(MaterialIO.toJson(material), false, "UTF-8");
        Material existing = materials.get(key);
        if (existing == null) {
            materials.put(key, material);
        } else if (existing != material) {
            existing.copyFrom(material);
        }
        errors.remove(key);
    }

    /** Reads {@code path} again (after the file changed on disk) into the shared material, so every user sees the change. */
    public void reload(String path) {
        String key = key(path);
        errors.remove(key);
        Material existing = materials.get(key);
        if (existing == null) {
            return;
        }
        FileHandle file = source.resolve(new AssetReference(key));
        try {
            existing.copyFrom(MaterialIO.fromJson(file.readString("UTF-8")));
        } catch (MaterialFormatException | GdxRuntimeException e) {
            materials.remove(key);
            errors.put(key, key + ": " + e.getMessage());
        }
    }

    /** Number of materials read so far. */
    public int size() {
        return materials.size();
    }
}
