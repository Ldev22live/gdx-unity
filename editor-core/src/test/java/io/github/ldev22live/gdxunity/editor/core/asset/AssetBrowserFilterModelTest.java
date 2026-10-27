package io.github.ldev22live.gdxunity.editor.core.asset;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetCatalog;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetMetadata;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssetBrowserFilterModelTest {

    @TempDir
    Path tempDir;

    private AssetCatalog catalog;
    private AssetBrowserFilterModel filterModel;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(tempDir.resolve("player.png"), "image-data");
        Files.writeString(tempDir.resolve("enemy.png"), "image-data");
        Files.writeString(tempDir.resolve("background.jpg"), "image-data");

        catalog = new AssetCatalog(tempDir.toFile());
        catalog.scan();

        filterModel = new AssetBrowserFilterModel(catalog);
    }

    @Test
    void testInitialEmptyFilterReturnsAll() {
        List<AssetMetadata> filtered = filterModel.getFilteredAssets();
        assertEquals(3, filtered.size());
        assertEquals(3, filterModel.getTotalCount());
        assertEquals(3, filterModel.getFilteredCount());
    }

    @Test
    void testFilterByNameQuery() {
        filterModel.setSearchFilter("player");
        List<AssetMetadata> filtered = filterModel.getFilteredAssets();
        assertEquals(1, filtered.size());
        assertEquals("player.png", filtered.get(0).getPath());
    }

    @Test
    void testFilterByType() {
        filterModel.setTypeFilter(AssetType.TEXTURE);
        List<AssetMetadata> filtered = filterModel.getFilteredAssets();
        assertEquals(3, filtered.size());
    }

    @Test
    void testFilterNoMatches() {
        filterModel.setSearchFilter("nonexistent");
        List<AssetMetadata> filtered = filterModel.getFilteredAssets();
        assertTrue(filtered.isEmpty());
        assertEquals(0, filterModel.getFilteredCount());
    }
}
