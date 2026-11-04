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
