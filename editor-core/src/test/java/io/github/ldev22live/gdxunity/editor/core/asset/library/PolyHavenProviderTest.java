package io.github.ldev22live.gdxunity.editor.core.asset.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolyHavenProviderTest {
    /** A cut-down {@code /assets} answer in the API's shape: two models, a texture, an HDRI and an asset with an unsafe id. */
    static final String CATALOGUE = """
        {
          "stone_fire_pit": {"name": "Stone Fire Pit", "type": 2, "authors": {"Sebastian Platen": "All"},
            "categories": ["structures", "furniture"], "tags": ["fire", "stone", "garden"], "download_count": 24970,
            "thumbnail_url": "https://cdn.polyhaven.com/asset_img/thumbs/stone_fire_pit.png?width=256"},
          "wooden_crate_02": {"name": "Wooden Crate 02", "type": 2, "authors": {"Jorge Camacho": "All", "Andrea Bellini": "Textures"},
            "categories": ["props", "containers"], "tags": ["wood", "box"], "download_count": 90000},
          "red_brick_03": {"name": "Red Brick 03", "type": 1, "authors": {"Rob Tuytel": "All"}, "categories": ["brick", "wall"],
            "tags": ["red", "stone"], "download_count": 5000},
          "sky_hdri": {"name": "Sky", "type": 0, "authors": {"Greg Zaal": "All"}},
          "..": {"name": "Escape", "type": 2, "authors": {}}
        }
        """;

    static final String MODEL_FILES = """
        {"gltf": {
          "2k": {"gltf": {"url": "https://dl.polyhaven.org/file/ph-assets/Models/gltf/2k/stone_fire_pit/stone_fire_pit_2k.gltf",
                 "md5": "aa", "size": 10,
                 "include": {"textures/stone_fire_pit_diff_2k.jpg": {"url": "https://dl.polyhaven.org/2k/diff.jpg", "md5": "bb", "size": 300},
                             "stone_fire_pit.bin": {"url": "https://dl.polyhaven.org/fire.bin", "md5": "cc", "size": 200}}}},
          "1k": {"gltf": {"url": "https://dl.polyhaven.org/file/ph-assets/Models/gltf/1k/stone_fire_pit/stone_fire_pit_1k.gltf",
                 "md5": "dd", "size": 10, "include": {}}},
          "4k": {"gltf": {"url": "https://dl.polyhaven.org/4k/stone_fire_pit_4k.gltf", "md5": "ee", "size": 10}}
        },
        "fbx": {"2k": {"fbx": {"url": "https://dl.polyhaven.org/x.fbx", "md5": "ff", "size": 1}}}}
        """;

    static final String TEXTURE_FILES = """
        {"Diffuse": {"1k": {"jpg": {"url": "https://dl.polyhaven.org/1k/red_brick_03_diff_1k.jpg", "md5": "a1", "size": 5}},
                     "2k": {"jpg": {"url": "https://dl.polyhaven.org/2k/red_brick_03_diff_2k.jpg", "md5": "a2", "size": 6}},
                     "8k": {"jpg": {"url": "https://dl.polyhaven.org/8k/red_brick_03_diff_8k.jpg", "md5": "a8", "size": 9}}},
         "nor_gl": {"1k": {"jpg": {"url": "https://dl.polyhaven.org/1k/red_brick_03_nor_gl_1k.jpg", "md5": "b1", "size": 5}},
                    "2k": {"jpg": {"url": "https://dl.polyhaven.org/2k/red_brick_03_nor_gl_2k.jpg", "md5": "b2", "size": 6}},
                    "8k": {"jpg": {"url": "https://dl.polyhaven.org/8k/red_brick_03_nor_gl_8k.jpg", "md5": "b8", "size": 9}}},
         "arm": {"1k": {"jpg": {"url": "https://dl.polyhaven.org/1k/red_brick_03_arm_1k.jpg", "md5": "c1", "size": 5}},
                 "2k": {"jpg": {"url": "https://dl.polyhaven.org/2k/red_brick_03_arm_2k.jpg", "md5": "c2", "size": 6}}},
         "nor_dx": {"1k": {"jpg": {"url": "https://dl.polyhaven.org/1k/red_brick_03_nor_dx_1k.jpg", "md5": "d1", "size": 5}}}}
        """;

    @TempDir
    File cache;

    private static FakeHttpSource online() {
        return new FakeHttpSource()
            .answer(PolyHavenProvider.API + "/assets?t=models", CATALOGUE)
            .answer(PolyHavenProvider.API + "/assets?t=textures", CATALOGUE)
            .answer(PolyHavenProvider.API + "/files/stone_fire_pit", MODEL_FILES)
            .answer(PolyHavenProvider.API + "/files/red_brick_03", TEXTURE_FILES);
    }

    @Test
    void shouldListOnlyAssetsOfTheRequestedTypeWithAuthorsAndPage() throws IOException {
        PolyHavenProvider provider = new PolyHavenProvider(online(), cache);

        AssetProvider.Catalog models = provider.catalog(AssetType.MODEL);

        assertEquals(List.of("stone_fire_pit", "wooden_crate_02"), models.assets().stream().map(RemoteAsset::id).toList(),
            "only type 2, and never the unsafe id");
        RemoteAsset crate = models.assets().get(1);
        assertEquals("Wooden Crate 02", crate.name());
        assertEquals(List.of("Andrea Bellini", "Jorge Camacho"), crate.authors());
        assertEquals("https://polyhaven.com/a/wooden_crate_02", crate.page());
        assertEquals("polyhaven-wooden-crate-02", crate.manifestId());
        assertEquals("models/thirdparty/polyhaven/wooden_crate_02", crate.folder());
        assertFalse(models.offline());
        assertEquals(List.of("red_brick_03"), provider.catalog(AssetType.TEXTURE).assets().stream().map(RemoteAsset::id).toList());
    }

    @Test
    void shouldSearchByEveryWordInNameTagsAndCategoriesMostDownloadedFirst() throws IOException {
        AssetProvider.Catalog models = new PolyHavenProvider(online(), cache).catalog(AssetType.MODEL);

        assertEquals(List.of("wooden_crate_02", "stone_fire_pit"), models.search("").stream().map(RemoteAsset::id).toList());
        assertEquals(List.of("stone_fire_pit"), models.search("Stone garden").stream().map(RemoteAsset::id).toList(),
            "a name word and a tag");
        assertEquals(List.of("wooden_crate_02"), models.search("containers").stream().map(RemoteAsset::id).toList(), "a category");
        assertEquals(List.of("wooden_crate_02"), models.search("crate 02").stream().map(RemoteAsset::id).toList());
        assertTrue(models.search("fire box").isEmpty(), "every word must match");
    }

    @Test
    void shouldGoOnlineOnlyWhenAskedAndOncePerSession() throws IOException {
        FakeHttpSource http = online();
        PolyHavenProvider provider = new PolyHavenProvider(http, cache);
        assertTrue(http.requests.isEmpty(), "nothing at start-up");

        provider.catalog(AssetType.MODEL);
        provider.catalog(AssetType.MODEL);

        assertEquals(List.of(PolyHavenProvider.API + "/assets?t=models"), http.requests);
    }

    @Test
    void shouldServeTheCachedCatalogueMarkedOfflineWhenTheProviderCannotBeReached() throws IOException {
        new PolyHavenProvider(online(), cache).catalog(AssetType.MODEL);
        FakeHttpSource down = online();
        down.offline = true;

        AssetProvider.Catalog catalog = new PolyHavenProvider(down, cache).catalog(AssetType.MODEL);

        assertTrue(catalog.offline());
        assertEquals(2, catalog.assets().size());
        assertTrue(catalog.problem().contains("unreachable"), catalog.problem());
        assertEquals(new File(cache, "polyhaven/models.json").lastModified(), catalog.fetched().toEpochMilli());
    }

    @Test
    void shouldSayNothingIsCachedWhenTheProviderCannotBeReachedOnFirstUse() {
        FakeHttpSource down = online();
        down.offline = true;

        IOException e = assertThrows(IOException.class, () -> new PolyHavenProvider(down, cache).catalog(AssetType.MODEL));

        assertTrue(e.getMessage().contains("Poly Haven cannot be reached") && e.getMessage().contains("nothing is cached"), e.getMessage());
    }

    @Test
    void shouldOfferModelResolutionsSmallestFirstWithTheGltfItsBufferAndTextures() throws IOException {
        PolyHavenProvider provider = new PolyHavenProvider(online(), cache);
        RemoteAsset pit = provider.catalog(AssetType.MODEL).search("fire").get(0);

        List<DownloadOption> options = provider.options(pit);

        assertEquals(List.of("1k", "2k", "4k"), options.stream().map(DownloadOption::resolution).toList());
        DownloadOption twoK = options.get(1);
        assertEquals(List.of("stone_fire_pit_2k.gltf", "stone_fire_pit.bin", "textures/stone_fire_pit_diff_2k.jpg"),
            twoK.files().stream().map(DownloadOption.RemoteFile::path).toList());
        assertEquals(510L, twoK.totalBytes());
        assertEquals("aa", twoK.files().get(0).md5());
        assertEquals("2K", twoK.label());
    }

    @Test
    void shouldOfferTextureResolutionsOnlyWithAllThreeMaps() throws IOException {
        PolyHavenProvider provider = new PolyHavenProvider(online(), cache);
        RemoteAsset brick = provider.catalog(AssetType.TEXTURE).assets().get(0);

        List<DownloadOption> options = provider.options(brick);

        assertEquals(List.of("1k", "2k"), options.stream().map(DownloadOption::resolution).toList(), "8k has no arm map");
        assertEquals(List.of(DownloadOption.Role.BASE_COLOR, DownloadOption.Role.NORMAL, DownloadOption.Role.OCCLUSION_ROUGHNESS_METALLIC),
            options.get(0).files().stream().map(DownloadOption.RemoteFile::role).toList());
        assertEquals("red_brick_03_nor_gl_1k.jpg", options.get(0).files().get(1).path());
    }

    @Test
    void shouldPickTwoKFirstThenTheLargestBelowItThenTheSmallest() {
        DownloadOption one = option("1k");
        DownloadOption two = option("2k");
        DownloadOption four = option("4k");
        DownloadOption eight = option("8k");

        assertEquals(two, AssetProvider.preferred(List.of(one, two, four)));
        assertEquals(one, AssetProvider.preferred(List.of(one, four)));
        assertEquals(four, AssetProvider.preferred(List.of(four, eight)));
    }

    @Test
    void shouldSendNothingButThePublicAddressesOfTheApi() throws IOException {
        FakeHttpSource http = online();
        PolyHavenProvider provider = new PolyHavenProvider(http, cache);

        provider.options(provider.catalog(AssetType.MODEL).search("fire").get(0));

        assertTrue(http.requests.stream().allMatch(url -> url.startsWith(PolyHavenProvider.API + "/")), http.requests.toString());
    }

    private static DownloadOption option(String resolution) {
        return new DownloadOption(resolution, List.of(new DownloadOption.RemoteFile("a.jpg", "https://dl.polyhaven.org/a.jpg", 1, "",
            DownloadOption.Role.OTHER)));
    }
}
