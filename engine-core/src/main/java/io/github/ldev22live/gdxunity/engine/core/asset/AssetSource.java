package io.github.ldev22live.gdxunity.engine.core.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.io.File;
import java.util.Objects;

/**
 * Where a project's asset files are read from (TD-036). The editor and the desktop runtime read a project folder on
 * disk ({@link #directory(File)}); the web runtime reads the files bundled with the page ({@link #internal()}),
 * because a browser has no file system. Runtime code that loads scenes, graphs, animations, models or textures
 * goes through this interface instead of {@link File}, so the same code runs on both.
 */
@FunctionalInterface
public interface AssetSource {
    /**
     * Returns the handle of a project asset. The handle is never null; it may name a file that does not exist,
     * which callers check with {@link FileHandle#exists()}.
     *
     * @param reference the project-relative asset path
     * @return the handle to read the asset through
     */
    FileHandle resolve(AssetReference reference);

    /**
     * Reads assets from a folder on disk. Needs no {@code Gdx.files}, so headless code and tests can use it.
     *
     * @param root the project's asset folder
     * @return a source resolving references against {@code root}
     */
    static AssetSource directory(File root) {
        Objects.requireNonNull(root, "root cannot be null");
        return reference -> new FileHandle(new File(root, reference.getPath()));
    }

    /**
     * Reads assets bundled with the application through {@code Gdx.files.internal}: the web runtime's preloaded
     * assets. {@code Gdx.files} must be set when {@link #resolve} is called.
     *
     * @return a source resolving references as internal files
     */
    static AssetSource internal() {
        return reference -> Gdx.files.internal(reference.getPath());
    }
}
