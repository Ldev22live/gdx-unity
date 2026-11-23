package io.github.ldev22live.gdxunity.editor.core.asset.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmbientCgProviderTest {
    private static final String PAGE = AmbientCgProvider.API + "?type=material&sort=popular&limit=2&offset=%d&include=title,tags";

    /** Two pages of the API's answer with a page size of 2: three materials, most popular first, one with an unsafe id. */
    private static FakeHttpSource online() {
        return new FakeHttpSource()
            .answer(String.format(PAGE, 0), """
                {"totalResults": 4, "assets": [
                  {"id": "Bricks075A", "title": "Bricks 075 A", "tags": ["brick", "wall", "red"]},
                  {"id": "Ground111", "title": "Ground 111", "tags": ["ground", "gravel", "brick"]}]}
                """)
            .answer(String.format(PAGE, 2), """
                {"totalResults": 4, "assets": [
                  {"id": "Grass004", "title": "Grass 004", "tags": ["grass", "green"]},
                  {"id": "..", "title": "Escape", "tags": []}]}
                """)
            .answer(AmbientCgProvider.API + "?id=Ground111&include=downloads", """
                {"assets": [{"id": "Ground111", "downloads": [
                  {"attributes": "2K-JPG", "extension": "zip", "url": "https://ambientcg.com/get?file=Ground111_2K-JPG.zip", "size": 34401646},
                  {"attributes": "1K-JPG", "extension": "zip", "url": "https://ambientcg.com/get?file=Ground111_1K-JPG.zip", "size": 9761806},
                  {"attributes": "8K-JPG", "extension": "zip", "url": "https://ambientcg.com/get?file=Ground111_8K-JPG.zip", "size": 483894133},
                  {"attributes": "1K-PNG", "extension": "zip", "url": "https://ambientcg.com/get?file=Ground111_1K-PNG.zip", "size": 20316735},
                  {"attributes": "SBSAR", "extension": "sbsar", "url": "https://ambientcg.com/get?file=Ground111.sbsar", "size": 100}]}]}
                """);
    }

    @TempDir
    File cache;

    @Test
    void shouldReadEveryPageAndKeepThePopularOrder() throws IOException {
        FakeHttpSource http = online();

        AssetProvider.Catalog catalog = new AmbientCgProvider(http, cache, 2).catalog(AssetType.TEXTURE);

        assertEquals(List.of(String.format(PAGE, 0), String.format(PAGE, 2)), http.requests, "stops at totalResults");
        assertEquals(List.of("Bricks075A", "Ground111", "Grass004"), catalog.search("").stream().map(RemoteAsset::id).toList(),
            "most popular first, never the unsafe id");
        RemoteAsset ground = catalog.search("gravel").get(0);
        assertEquals("Ground 111", ground.name());
        assertEquals("https://ambientcg.com/view?id=Ground111", ground.page());
        assertEquals("ambientcg-ground111", ground.manifestId());
        assertEquals("textures/thirdparty/ambientcg/Ground111", ground.folder());
        assertEquals(List.of("Bricks075A", "Ground111"), catalog.search("brick").stream().map(RemoteAsset::id).toList());
    }

    @Test
    void shouldOfferMaterialsOnly() {
        AmbientCgProvider provider = new AmbientCgProvider(online(), cache, 2);

        assertEquals(List.of(AssetType.TEXTURE), provider.types());
        assertThrows(IllegalArgumentException.class, () -> provider.catalog(AssetType.MODEL));
    }

    @Test
    void shouldServeTheCachedCatalogueMarkedOfflineAndSayWhenNothingIsCached() throws IOException {
        FakeHttpSource down = online();
        down.offline = true;
        IOException nothing = assertThrows(IOException.class, () -> new AmbientCgProvider(down, cache, 2).catalog(AssetType.TEXTURE));
        assertTrue(nothing.getMessage().contains("nothing is cached"), nothing.getMessage());

        new AmbientCgProvider(online(), cache, 2).catalog(AssetType.TEXTURE);
        AssetProvider.Catalog offline = new AmbientCgProvider(down, cache, 2).catalog(AssetType.TEXTURE);

        assertTrue(offline.offline());
        assertEquals(3, offline.assets().size());
    }

    @Test
    void shouldOfferJpegZipsSmallestFirstWithoutAHash() throws IOException {
        AmbientCgProvider provider = new AmbientCgProvider(online(), cache, 2);
        RemoteAsset ground = provider.catalog(AssetType.TEXTURE).search("Ground111").get(0);

        List<DownloadOption> options = provider.options(ground);

        assertEquals(List.of("1K-JPG", "2K-JPG", "8K-JPG"), options.stream().map(DownloadOption::resolution).toList());
        DownloadOption.RemoteFile zip = options.get(1).files().get(0);
        assertEquals("Ground111_2K-JPG.zip", zip.path());
        assertEquals(DownloadOption.Role.ARCHIVE, zip.role());
        assertEquals("", zip.md5(), "ambientCG gives no hash");
        assertEquals(34401646L, options.get(1).totalBytes());
        assertEquals("2K-JPG", AssetProvider.preferred(options).resolution());
        assertTrue(provider.retrievedWith(options.get(1)).contains("no source hash; SHA-256 recorded"));
    }

    @Test
    void shouldNameTheRoleOfEachMapInTheZip() {
        AmbientCgProvider provider = new AmbientCgProvider(online(), cache);

        assertEquals(DownloadOption.Role.BASE_COLOR, provider.roleOf("Ground111_1K-JPG_Color.jpg"));
        assertEquals(DownloadOption.Role.NORMAL, provider.roleOf("Ground111_1K-JPG_NormalGL.jpg"));
        assertEquals(DownloadOption.Role.ROUGHNESS, provider.roleOf("Ground111_1K-JPG_Roughness.jpg"));
        assertEquals(DownloadOption.Role.AMBIENT_OCCLUSION, provider.roleOf("Ground111_1K-JPG_AmbientOcclusion.jpg"));
        assertEquals(DownloadOption.Role.OTHER, provider.roleOf("Ground111_1K-JPG_NormalDX.jpg"));
        assertEquals(DownloadOption.Role.OTHER, provider.roleOf("Ground111.png"));
        assertEquals(DownloadOption.Role.BASE_COLOR, provider.roleOf("folder/x_Color.png"));
    }
}
