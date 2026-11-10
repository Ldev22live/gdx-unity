package io.github.ldev22live.gdxunity.editor.sculpt;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptMesh;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptMeshFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SculptAssetsTest {
    @TempDir
    Path root;

    private SculptAssets assets() {
        return new SculptAssets(root.toFile());
    }

    @Test
    void shouldCreateASphereFileInTheSculptsFolder() throws IOException {
        AssetReference asset = assets().create("Rock", 50f, 8);

        assertEquals("sculpts/Rock.sculpt", asset.getPath());
        assertTrue(new File(root.toFile(), "sculpts/Rock.sculpt").isFile());
        SculptMesh mesh = assets().load(asset);
        assertEquals(6 * 8 * 8 + 2, mesh.getVertexCount());
        assertEquals(50f, (float) Math.sqrt(mesh.getPositions()[0] * mesh.getPositions()[0]
            + mesh.getPositions()[1] * mesh.getPositions()[1] + mesh.getPositions()[2] * mesh.getPositions()[2]), 1e-3f);
    }

    /** A cube of edge 2 as a model has it: every face has its own four corners (24 vertices, 12 triangles). */
    private static float[] splitCubePositions() {
        float[][] corners = {{-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1}, {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}};
        int[][] faces = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 1, 5, 4}, {3, 7, 6, 2}, {0, 4, 7, 3}, {1, 2, 6, 5}};
        float[] positions = new float[24 * 3];
        for (int face = 0; face < 6; face++) {
            for (int corner = 0; corner < 4; corner++) {
                System.arraycopy(corners[faces[face][corner]], 0, positions, (face * 4 + corner) * 3, 3);
            }
        }
        return positions;
    }

    private static int[] splitCubeTriangles() {
        int[] indices = new int[36];
        for (int face = 0; face < 6; face++) {
            int base = face * 4;
            System.arraycopy(new int[] {base, base + 1, base + 2, base, base + 2, base + 3}, 0, indices, face * 6, 6);
        }
        return indices;
    }

    @Test
    void shouldWeldAndSubdivideGeometryTakenFromAModel() throws IOException {
        AssetReference asset = assets().createFromGeometry("Crate", splitCubePositions(), splitCubeTriangles());

        assertEquals("sculpts/Crate.sculpt", asset.getPath());
        SculptMesh mesh = assets().load(asset);
        assertEquals(12 * 256, mesh.getTriangleCount(), "welded to 12 triangles, subdivided " + SculptAssets.MAX_IMPORT_LEVELS + " levels");
        float high = 0f;
        for (float p : mesh.getPositions()) {
            high = Math.max(high, Math.abs(p));
        }
        assertEquals(1f, high, 1e-5f, "the shape and size are kept");
        // A torn surface would have boundary edges; a welded closed mesh has 3 triangles worth of edges per 2 triangles
        assertEquals(mesh.getTriangleCount() / 2 + 2, mesh.getVertexCount(), "closed surface: V - E + F = 2");
    }

    @Test
    void shouldNotSubdivideAMeshThatAlreadyHasEnoughTriangles() throws IOException {
        int n = 300;
        float[] positions = new float[(n + 1) * (n + 1) * 3];
        int[] triangles = new int[n * n * 6];
        for (int y = 0; y <= n; y++) {
            for (int x = 0; x <= n; x++) {
                int v = (y * (n + 1) + x) * 3;
                positions[v] = x;
                positions[v + 1] = y;
            }
        }
        int t = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int a = y * (n + 1) + x;
                int[] quad = {a, a + 1, a + n + 2, a, a + n + 2, a + n + 1};
                System.arraycopy(quad, 0, triangles, t, 6);
                t += 6;
            }
        }

        SculptMesh mesh = assets().load(assets().createFromGeometry("Grid", positions, triangles));

        assertEquals(n * n * 2, mesh.getTriangleCount(), "180,000 triangles are already near the target");
    }

    @Test
    void shouldRefuseEmptyGeometryAndNameTheSculptLikeCreateDoes() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> assets().createFromGeometry("Nothing", new float[0], new int[0]));

        assets().createFromGeometry("Crate", splitCubePositions(), splitCubeTriangles());
        assertEquals("sculpts/Crate 1.sculpt", assets().createFromGeometry("Crate", splitCubePositions(), splitCubeTriangles()).getPath());
    }

    @Test
    void shouldNeverOverwriteAnExistingSculptWhenCreating() throws IOException {
        assertEquals("sculpts/Rock.sculpt", assets().create("Rock", 1f, 2).getPath());
        assertEquals("sculpts/Rock 1.sculpt", assets().create("Rock", 1f, 2).getPath());
        assertEquals("sculpts/Rock 2.sculpt", assets().create("Rock", 1f, 2).getPath());
    }

    @Test
    void shouldCleanTheNameAndFallBackToADefault() throws IOException {
        assertEquals("sculpts/Big Rock-2x.sculpt", assets().create("Big Rock-2/../x?", 1f, 2).getPath(),
            "slashes, dots and question marks are dropped"
        );
        assertEquals("sculpts/Sculpt.sculpt", assets().create(null, 1f, 2).getPath());
        assertEquals("sculpts/Sculpt 1.sculpt", assets().create("///", 1f, 2).getPath());
    }

    @Test
    void shouldSaveAndLoadWithoutLoss() throws IOException {
        AssetReference asset = assets().create("Rock", 5f, 4);
        SculptMesh mesh = assets().load(asset);
        mesh.getPositions()[4] = 123.456f;

        assets().save(asset, mesh);

        assertArrayEquals(mesh.getPositions(), assets().load(asset).getPositions(), 0f);
    }

    @Test
    void shouldReplaceTheFileAndLeaveNoTemporaryFileBehind() throws IOException {
        AssetReference asset = assets().create("Rock", 5f, 4);

        assets().save(asset, SculptMeshFactory.cubeSphere(2f, 3));

        assertFalse(new File(root.toFile(), "sculpts/Rock.sculpt.tmp").exists());
        assertEquals(6 * 9 + 2, assets().load(asset).getVertexCount());
    }

    @Test
    void shouldNameTheFileWhenItIsMissingOrBroken() throws IOException {
        IOException missing = assertThrows(IOException.class, () -> assets().load(new AssetReference("sculpts/none.sculpt")));
        assertTrue(missing.getMessage().contains("Sculpt not found: sculpts/none.sculpt"), missing.getMessage());

        Files.createDirectories(root.resolve("sculpts"));
        Files.writeString(root.resolve("sculpts/bad.sculpt"), "this is not a sculpt file at all");
        IOException broken = assertThrows(IOException.class, () -> assets().load(new AssetReference("sculpts/bad.sculpt")));
        assertTrue(broken.getMessage().startsWith("sculpts/bad.sculpt: "), broken.getMessage());
        assertTrue(broken.getMessage().contains("Not a sculpt file"), broken.getMessage());
    }

    @Test
    void shouldRefuseAPathThatEscapesTheProject() {
        assertThrows(IllegalArgumentException.class, () -> assets().load(new AssetReference("../outside.sculpt")));
    }
}
