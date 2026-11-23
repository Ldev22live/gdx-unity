package io.github.ldev22live.gdxunity.engine.terrain;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The project's terrains by path (TD-082). Everything that names a {@code .terrain} file gets the <em>same</em> {@link Terrain}
 * object, so a stroke shows on every user at once. A file that is missing or cannot be read gives no terrain and a stored
 * explanation ({@link #getError}); nothing is invented in its place.
 */
public final class TerrainLibrary {
    private final AssetSource source;
    private final Map<String, Terrain> terrains = new HashMap<>();
    private final Map<String, String> errors = new HashMap<>();

    public TerrainLibrary(AssetSource source) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
    }

    /** Where the project's files come from. */
    public AssetSource getSource() {
        return source;
    }

    private static String key(String path) {
        return Objects.requireNonNull(path, "path cannot be null").trim().replace('\\', '/');
    }

    /**
     * The terrain in {@code path}, read on first use.
     *
     * @return the shared terrain, or null when the file is missing or unreadable ({@link #getError} says why)
     */
    public Terrain get(String path) {
        String key = key(path);
        Terrain cached = terrains.get(key);
        if (cached != null) {
            return cached;
        }
        if (errors.containsKey(key)) {
            return null;
        }
        FileHandle file = source.resolve(new AssetReference(key));
        if (!file.exists()) {
            errors.put(key, "terrain file not found: " + key);
            return null;
        }
        try {
            Terrain terrain = TerrainIO.fromJson(file.readString("UTF-8"));
            terrains.put(key, terrain);
            return terrain;
        } catch (TerrainFormatException | GdxRuntimeException e) {
            errors.put(key, key + ": " + e.getMessage());
            return null;
        }
    }

    /** Why {@code path} has no terrain, or null if it has one or was never asked for. */
    public String getError(String path) {
        return errors.get(key(path));
    }

    /**
     * Writes a terrain to its file and makes it the shared one for that path.
     *
     * @throws GdxRuntimeException if the file cannot be written
     */
    public void save(String path, Terrain terrain) {
        Objects.requireNonNull(terrain, "terrain cannot be null");
        String key = key(path);
        FileHandle file = source.resolve(new AssetReference(key));
        file.writeString(TerrainIO.toJson(terrain), false, "UTF-8");
        terrains.putIfAbsent(key, terrain);
        errors.remove(key);
    }

    /**
     * Reads {@code path} again after the file changed on disk. The shared terrain takes over the new heights when the grid is the same;
     * otherwise the next {@link #get} reads the new one.
     */
    public void reload(String path) {
        String key = key(path);
        errors.remove(key);
        Terrain existing = terrains.get(key);
        if (existing == null) {
            return;
        }
        try {
            Terrain fresh = TerrainIO.fromJson(source.resolve(new AssetReference(key)).readString("UTF-8"));
            try {
                existing.copyFrom(fresh);
            } catch (IllegalArgumentException differentGrid) {
                terrains.put(key, fresh);
            }
        } catch (TerrainFormatException | GdxRuntimeException e) {
            terrains.remove(key);
            errors.put(key, key + ": " + e.getMessage());
        }
    }

    /** Number of terrains read so far. */
    public int size() {
        return terrains.size();
    }
}
