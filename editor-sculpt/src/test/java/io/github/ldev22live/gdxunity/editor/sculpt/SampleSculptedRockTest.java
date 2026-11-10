package io.github.ldev22live.gdxunity.editor.sculpt;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptGltfWriter;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptMesh;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptMeshFactory;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled sculpt sample (TD-038, 19-C): {@code sculpts/SculptedRock.sculpt}, the editable source, and
 * {@code models/SculptedRock.glb}, the model made from it, which the sample scene {@code sculpted-rock-3d.json} places
 * with a Model component. The test rebuilds both from code and compares them with the files, so the files cannot drift
 * from the exporter. Run with the environment variable {@code GDXUNITY_WRITE_SAMPLES=1} to write them again after changing the rock or the exporter.
 */
class SampleSculptedRockTest {

    private static final File ASSETS = new File("../assets");
    private static final String WRITE_VARIABLE = "GDXUNITY_WRITE_SAMPLES";
    /** Quads per cube edge of the sample rock: 6 x 48 x 48 x 2 = 27,648 triangles. */
    private static final int RESOLUTION = 48;
    private static final float RADIUS = 60f;
    private static final float[] STONE = {0.55f, 0.52f, 0.48f, 1f};

    /** A sphere with lumps: the radius changes with the direction, the same way every time. */
    private static SculptMesh rock() {
        SculptMesh sphere = SculptMeshFactory.cubeSphere(RADIUS, RESOLUTION);
        float[] positions = sphere.getPositions().clone();
        for (int v = 0; v < positions.length; v += 3) {
            float x = positions[v];
            float y = positions[v + 1];
            float z = positions[v + 2];
            float lump = 1f + 0.16f * (float) (Math.sin(x * 0.11) * Math.cos(z * 0.09) + 0.5 * Math.sin(y * 0.2 + x * 0.05));
            float flatten = y < 0f ? 0.7f : 1f;
            positions[v] = x * lump;
            positions[v + 1] = y * lump * flatten;
            positions[v + 2] = z * lump;
        }
        return SculptMeshFactory.weld(positions, sphere.getTriangles(), 1e-4f);
    }

    @Test
    void shouldMatchTheBundledSculptAndModelFiles() throws IOException {
        SculptMesh mesh = rock();
        Path scratch = Files.createTempDirectory("rock");
        SculptAssets assets = new SculptAssets(scratch.toFile());
        AssetReference sculpt = new AssetReference("sculpts/SculptedRock.sculpt");
        assets.save(sculpt, mesh);
        byte[] sculptBytes = Files.readAllBytes(scratch.resolve("sculpts/SculptedRock.sculpt"));
        byte[] modelBytes = SculptGltfWriter.write(mesh, 1f / SculptGltfWriter.DEFAULT_IMPORT_SCALE, STONE, "SculptedRock");

        File sculptFile = new File(ASSETS, "sculpts/SculptedRock.sculpt");
        File modelFile = new File(ASSETS, "models/SculptedRock.glb");
        if ("1".equals(System.getenv(WRITE_VARIABLE))) {
            Files.createDirectories(sculptFile.getParentFile().toPath());
            Files.write(sculptFile.toPath(), sculptBytes);
            Files.write(modelFile.toPath(), modelBytes);
        }

        assertTrue(sculptFile.isFile() && modelFile.isFile(), "missing sample files; run with " + WRITE_VARIABLE + "=1");
        assertArrayEquals(sculptBytes, Files.readAllBytes(sculptFile.toPath()), "sculpts/SculptedRock.sculpt is out of date");
        assertArrayEquals(modelBytes, Files.readAllBytes(modelFile.toPath()), "models/SculptedRock.glb is out of date");
        assertTrue(mesh.getTriangleCount() > 20_000 && mesh.getTriangleCount() <= 6 * RESOLUTION * RESOLUTION * 2,
            "tens of thousands of triangles: " + mesh.getTriangleCount() + " " + Arrays.toString(STONE));
    }
}
