package io.github.ldev22live.gdxunity.editor.sculpt;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
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

    private File fileOf(String path) {
        return new File(assetRoot, new AssetReference(path).getPath());
    }
}
