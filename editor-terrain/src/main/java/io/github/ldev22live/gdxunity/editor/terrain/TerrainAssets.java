package io.github.ldev22live.gdxunity.editor.terrain;

import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainGenerator;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

/** Creating {@code .terrain} files in a project (TD-082). */
public final class TerrainAssets {
    /** Folder terrains are created in, relative to the project root. */
    public static final String FOLDER = "terrains";
    /** File extension of a terrain, with the dot. */
    public static final String EXTENSION = ".terrain";
    /** Grid of a new terrain: 129 by 129 points, 40 units apart, about 5 km square at the editor's 100 units per metre. */
    public static final int NEW_RESOLUTION = 129;
    public static final float NEW_CELL_SIZE = 40f;
    /** Height of the largest hills of a new terrain, in world units. */
    public static final float NEW_HILL_HEIGHT = 300f;

    /** Square map presets, measured in cells at the usual terrain spacing. */
    public enum MapSize {
        SMALL(64), STANDARD(NEW_RESOLUTION - 1), LARGE(256);

        private final int cells;

        MapSize(int cells) {
            this.cells = cells;
        }

        @Override
        public String toString() {
            return cells + " x " + cells + " cells";
        }
    }

    private TerrainAssets() { }

    /** Whether {@code path} names a terrain file, by its extension. */
    public static boolean isTerrainPath(String path) {
        return path != null && path.trim().toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    /**
     * Creates a new terrain in {@code terrains/}, under a name no other terrain file uses ({@code Name.terrain}, then
     * {@code Name 2.terrain} and so on), and registers it with the library.
     *
     * @param assetRoot the project's asset folder
     * @param baseName  the name to start from; blank gives "Terrain"
     * @param flat      true for flat ground, false for rolling hills to start from
     * @return the project-relative path of the new file
     */
    public static String createNew(File assetRoot, TerrainLibrary library, String baseName, boolean flat) {
        return createNew(assetRoot, library, baseName, flat, Surface.AUTO);
    }

    /**
     * Like {@link #createNew(File, TerrainLibrary, String, boolean)}, with every point of the new terrain on {@code base}: the ground
     * a level is built on (TD-083).
     */
    public static String createNew(File assetRoot, TerrainLibrary library, String baseName, boolean flat, Surface base) {
        return createNew(assetRoot, library, baseName, flat, base, MapSize.STANDARD);
    }

    /** Creates a square terrain with the chosen cell count and the standard spacing. */
    static String createNew(File assetRoot, TerrainLibrary library, String baseName, boolean flat, Surface base, MapSize size) {
        int resolution = Objects.requireNonNull(size, "size cannot be null").cells + 1;
        Objects.requireNonNull(base, "base cannot be null");
        Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        Objects.requireNonNull(library, "library cannot be null");
        String name = baseName == null || baseName.isBlank() ? "Terrain" : baseName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        String path = FOLDER + "/" + name + EXTENSION;
        for (int n = 2; new File(assetRoot, path).exists(); n++) {
            path = FOLDER + "/" + name + " " + n + EXTENSION;
        }
        String title = path.substring(FOLDER.length() + 1, path.length() - EXTENSION.length());
        Terrain terrain = flat ? TerrainGenerator.flat(title, resolution, NEW_CELL_SIZE)
            : TerrainGenerator.hills(title, resolution, NEW_CELL_SIZE, path.hashCode(), NEW_HILL_HEIGHT);
        terrain.fillSurface(base);
        library.save(path, terrain);
        return path;
    }
}
