package io.github.ldev22live.gdxunity.engine.core.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssetCatalogTest {

    @Test
    void testDiscoveryOfSupportedImages(@TempDir Path tempDir) throws IOException {
        // Create test file structure
        Files.createDirectories(tempDir.resolve("textures/sub"));
        Files.writeString(tempDir.resolve("textures/image1.png"), "fake png data");
        Files.writeString(tempDir.resolve("textures/sub/image2.jpg"), "fake jpg data");
        Files.writeString(tempDir.resolve("textures/sub/image3.JPEG"), "fake jpeg data");
        Files.writeString(tempDir.resolve("textures/readme.txt"), "text file to ignore");
        Files.writeString(tempDir.resolve("textures/model.obj"), "unsupported format");

        AssetCatalog catalog = new AssetCatalog(tempDir.toFile());
        catalog.scan();

        List<AssetMetadata> assets = catalog.getAllAssets();
        assertEquals(3, assets.size(), "Should only discover .png, .jpg, .jpeg");

        AssetReference ref1 = new AssetReference("textures/image1.png");
        assertTrue(catalog.hasAsset(ref1));
        assertEquals(AssetType.TEXTURE, catalog.getMetadata(ref1).type());

        AssetReference ref2 = new AssetReference("textures/sub/image2.jpg");
        assertTrue(catalog.hasAsset(ref2));

        AssetReference ref3 = new AssetReference("textures/sub/image3.JPEG");
        assertTrue(catalog.hasAsset(ref3));

        AssetReference txtRef = new AssetReference("textures/readme.txt");
        assertFalse(catalog.hasAsset(txtRef));
    }

    @Test
    void testMissingDirectoryHandling() {
        File nonExistent = new File("some/non/existent/path/999");
        AssetCatalog catalog = new AssetCatalog(nonExistent);
        assertDoesNotThrow(catalog::scan);
        assertTrue(catalog.getAllAssets().isEmpty());
    }

    @Test
    void testDeterministicSorting(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("zebra.png"), "data");
        Files.writeString(tempDir.resolve("apple.png"), "data");
        Files.writeString(tempDir.resolve("banana.png"), "data");

        AssetCatalog catalog = new AssetCatalog(tempDir.toFile());
        catalog.scan();

        List<AssetMetadata> assets = catalog.getAllAssets();
        assertEquals(3, assets.size());
        assertEquals("apple.png", assets.get(0).getPath());
        assertEquals("banana.png", assets.get(1).getPath());
        assertEquals("zebra.png", assets.get(2).getPath());
    }
}
