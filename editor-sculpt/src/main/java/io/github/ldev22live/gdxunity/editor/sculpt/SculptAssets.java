package io.github.ldev22live.gdxunity.editor.sculpt;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.sculpt.Subdivision;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptGltfWriter;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptMesh;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptMeshFactory;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptMeshIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Creates, loads and saves {@code .sculpt} assets in a project's asset folder (TD-038). */
public class SculptAssets {
    /** Folder new sculpts are created in. */
    public static final String SCULPT_FOLDER = "sculpts";
    /** File extension of a sculpt, with the dot. */
    public static final String EXTENSION = "." + SculptMeshIO.EXTENSION;
    /** Radius of a new sculpt in scene units: the size of a default 100-unit primitive. */
    public static final float DEFAULT_RADIUS = 50f;
    /** Quads per cube edge of a new sculpt: 6 x 128 x 128 + 2 = 98,306 vertices and 196,608 triangles. */
    public static final int DEFAULT_RESOLUTION = 128;

    /** Most triangles a sculpt should have where the view is drawn on the CPU (the streamed web editor). */
    public static final int SOFTWARE_TRIANGLE_LIMIT = 100_000;
    /** Quads per cube edge of a new sculpt on such a machine: 6 x 64 x 64 x 2 = 49,152 triangles. */
    public static final int SOFTWARE_RESOLUTION = 64;
    /** Folder exported models are written to. */
    public static final String MODEL_FOLDER = "models";
    /** Triangles an imported mesh is subdivided towards: about the size of a new sphere. */
    public static final int IMPORT_TARGET_TRIANGLES = 200_000;
    /** Most subdivision levels an import gets (each level makes four times the triangles). */
    static final int MAX_IMPORT_LEVELS = 4;
    /** Vertices closer than this fraction of the model's largest size are welded. */
    private static final float WELD_TOLERANCE = 1e-5f;
    /** Smallest weld tolerance in scene units, for a model that is a point. */
    private static final float MIN_WELD_TOLERANCE = 1e-6f;

    private final File assetRoot;

    /** @param assetRoot the project's asset folder; asset paths are relative to it */
    public SculptAssets(File assetRoot) {
        this.assetRoot = Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
    }

    /**
     * Creates a new sculpt file with a sphere to start from, under a name that is not used yet.
     *
     * @param name       wanted name; characters other than letters, digits, space, '_' and '-' are dropped
     * @param radius     sphere radius in scene units, greater than 0
     * @param resolution quads per cube edge, see {@link SculptMeshFactory#cubeSphere}
     * @return the new asset
     * @throws IOException if the file cannot be written
     */
    public AssetReference create(String name, float radius, int resolution) throws IOException {
        String base = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (base.isEmpty()) {
            base = "Sculpt";
        }
        SculptMesh mesh = SculptMeshFactory.cubeSphere(radius, resolution);
        String path = SCULPT_FOLDER + "/" + base + EXTENSION;
        for (int n = 1; fileOf(path).exists(); n++) {
            path = SCULPT_FOLDER + "/" + base + " " + n + EXTENSION;
        }
        AssetReference asset = new AssetReference(path);
        save(asset, mesh);
        return asset;
    }

    /** Creates a sphere with the default radius and resolution. */
    public AssetReference create(String name) throws IOException {
        return create(name, DEFAULT_RADIUS, DEFAULT_RESOLUTION);
    }

    /**
     * Reads a sculpt file.
     *
     * @throws IOException if the file is missing, unreadable or not a valid sculpt, with the path in the message
     */
    public SculptMesh load(AssetReference asset) throws IOException {
        File file = fileOf(asset.getPath());
        if (!file.isFile()) {
            throw new IOException("Sculpt not found: " + asset.getPath());
        }
        try {
            return SculptMeshIO.read(new ByteArrayInputStream(Files.readAllBytes(file.toPath())));
        } catch (IOException e) {
            throw new IOException(asset.getPath() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Writes a sculpt file. The data goes to a temporary file first and replaces the old file in one step, so a
     * crash while saving cannot leave a half-written sculpt behind.
     *
     * @throws IOException if the file cannot be written
     */
    public void save(AssetReference asset, SculptMesh mesh) throws IOException {
        Objects.requireNonNull(mesh, "mesh cannot be null");
        File file = fileOf(asset.getPath());
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SculptMeshIO.write(mesh, bytes);
        File temporary = new File(parent, file.getName() + ".tmp");
        Files.write(temporary.toPath(), bytes.toByteArray());
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Creates a sculpt from triangles taken from a model or a primitive (TD-038, 19-B step 5): vertices at the same
     * position are welded (a model splits them at UV seams and hard edges, which would tear the surface open under a
     * brush), and the triangles are subdivided until there are about {@link #IMPORT_TARGET_TRIANGLES}, so there is
     * enough surface to push around. A mesh that already has more is left as it is.
     *
     * @param positions x, y, z per vertex
     * @param triangles three vertex indices per triangle
     * @throws IllegalArgumentException if the geometry is empty or invalid
     * @throws IOException               if the file cannot be written
     */
    public AssetReference createFromGeometry(String name, float[] positions, int[] triangles) throws IOException {
        return createFromGeometry(name, positions, triangles, IMPORT_TARGET_TRIANGLES);
    }

    /** As {@link #createFromGeometry(String, float[], int[])}, subdividing towards {@code targetTriangles} instead. */
    public AssetReference createFromGeometry(String name, float[] positions, int[] triangles, int targetTriangles) throws IOException {
        Objects.requireNonNull(positions, "positions cannot be null");
        Objects.requireNonNull(triangles, "triangles cannot be null");
        if (positions.length == 0 || triangles.length == 0) {
            throw new IllegalArgumentException("There are no triangles to sculpt");
        }
        float size = 0f;
        float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < positions.length; i++) {
            low[i % 3] = Math.min(low[i % 3], positions[i]);
            high[i % 3] = Math.max(high[i % 3], positions[i]);
        }
        for (int axis = 0; axis < 3; axis++) {
            size = Math.max(size, high[axis] - low[axis]);
        }
        SculptMesh mesh = SculptMeshFactory.weld(positions, triangles, Math.max(size * WELD_TOLERANCE, MIN_WELD_TOLERANCE));
        int levels = 0;
        long count = mesh.getTriangleCount();
        while (levels < MAX_IMPORT_LEVELS && count * 4 <= targetTriangles) {
            count *= 4;
            levels++;
        }
        if (levels > 0) {
            mesh = Subdivision.midpoint(mesh, levels);
        }
        String base = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (base.isEmpty()) {
            base = "Sculpt";
        }
        String path = SCULPT_FOLDER + "/" + base + EXTENSION;
        for (int n = 1; fileOf(path).exists(); n++) {
            path = SCULPT_FOLDER + "/" + base + " " + n + EXTENSION;
        }
        AssetReference asset = new AssetReference(path);
        save(asset, mesh);
        return asset;
    }

    /**
     * Writes a sculpt as a glTF model, {@code models/<name>.glb}, that a Model component can place (19-C). It replaces the
     * file of an earlier export of the same sculpt, so exporting again after more strokes updates the model.
     *
     * @param color red, green, blue and alpha of the model's material
     * @return the model asset
     * @throws IOException if the file cannot be written
     */
    public AssetReference exportModel(AssetReference sculpt, SculptMesh mesh, float[] color) throws IOException {
        Objects.requireNonNull(sculpt, "sculpt cannot be null");
        String file = new File(sculpt.getPath()).getName();
        String base = file.endsWith(EXTENSION) ? file.substring(0, file.length() - EXTENSION.length()) : file;
        byte[] glb = SculptGltfWriter.write(mesh, 1f / SculptGltfWriter.DEFAULT_IMPORT_SCALE, color, base);
        AssetReference asset = new AssetReference(MODEL_FOLDER + "/" + base + ".glb");
        File target = fileOf(asset.getPath());
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }
        File temporary = new File(parent, target.getName() + ".tmp");
        Files.write(temporary.toPath(), glb);
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return asset;
    }

    private File fileOf(String path) {
        return new File(assetRoot, new AssetReference(path).getPath());
    }
}
