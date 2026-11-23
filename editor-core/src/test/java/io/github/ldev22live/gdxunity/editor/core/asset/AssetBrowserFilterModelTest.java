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

    private void addNested() throws IOException {
        Files.createDirectories(tempDir.resolve("models/props"));
        Files.createDirectories(tempDir.resolve("ui"));
        Files.writeString(tempDir.resolve("models/hero.glb"), "model");
        Files.writeString(tempDir.resolve("models/props/crate.glb"), "model");
        Files.writeString(tempDir.resolve("models/props/barrel.glb"), "model");
        Files.writeString(tempDir.resolve("ui/button.png"), "sound");
        catalog.scan();
    }

    @Test
    void shouldListTheRootFilesAndFoldersWithTheirAssetCounts() throws IOException {
        addNested();

        assertEquals(List.of("background.jpg", "enemy.png", "player.png"),
            filterModel.getFilteredAssets().stream().map(AssetMetadata::getPath).toList());
        assertEquals(List.of("models:3", "ui:1"),
            filterModel.getSubfolders().stream().map(f -> f.name() + ":" + f.assetCount()).toList());
    }

    @Test
    void shouldOpenAFolderShowOnlyItsDirectFilesAndGoBackUp() throws IOException {
        addNested();

        filterModel.openFolder("models");
        assertEquals(List.of("models/hero.glb"), filterModel.getFilteredAssets().stream().map(AssetMetadata::getPath).toList());
        assertEquals(List.of("models/props"), filterModel.getSubfolders().stream().map(AssetBrowserFilterModel.Folder::path).toList());
        filterModel.openFolder("props");
        assertEquals(List.of("models", "props"), filterModel.getBreadcrumb());
        assertEquals(2, filterModel.getFilteredAssets().size());

        filterModel.up();
        assertEquals("models", filterModel.getCurrentFolder());
        filterModel.up();
        filterModel.up();
        assertEquals("", filterModel.getCurrentFolder());
    }

    @Test
    void shouldSearchEveryFolderWhateverFolderIsOpenAndHideTheFolderList() throws IOException {
        addNested();
        filterModel.openFolder("ui");

        filterModel.setSearchFilter("crate");

        assertEquals(List.of("models/props/crate.glb"), filterModel.getFilteredAssets().stream().map(AssetMetadata::getPath).toList());
        assertTrue(filterModel.getSubfolders().isEmpty());
    }

    @Test
    void shouldFallBackToTheNearestExistingFolderWhenTheOpenOneDisappears() throws IOException {
        addNested();
        filterModel.openFolder("models");
        filterModel.openFolder("props");
        Files.delete(tempDir.resolve("models/props/crate.glb"));
        Files.delete(tempDir.resolve("models/props/barrel.glb"));
        Files.delete(tempDir.resolve("models/props"));
        catalog.scan();

        filterModel.revalidate();

        assertEquals("models", filterModel.getCurrentFolder());
    }

    @Test
    void shouldApplyTheTypeFilterToFolderCountsAndIgnoreUnknownFolders() throws IOException {
        addNested();
        filterModel.setTypeFilter(AssetType.MODEL);

        assertEquals(List.of("models:3"), filterModel.getSubfolders().stream().map(f -> f.name() + ":" + f.assetCount()).toList());
        filterModel.setCurrentFolder("nowhere/at/all");
        assertEquals("", filterModel.getCurrentFolder());
    }
}
