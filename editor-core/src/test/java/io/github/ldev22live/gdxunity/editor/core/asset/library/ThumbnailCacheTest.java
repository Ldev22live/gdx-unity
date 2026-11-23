package io.github.ldev22live.gdxunity.editor.core.asset.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThumbnailCacheTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};
    private static final byte[] WEBP = "RIFF....WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    File cache;

    private static RemoteAsset asset(String thumbnail) {
        return new RemoteAsset("polyhaven", "stone_fire_pit", "Stone Fire Pit", AssetType.MODEL, List.of(), List.of(), List.of(),
            "https://polyhaven.com/a/stone_fire_pit", thumbnail, 1);
    }

    @Test
    void shouldFetchOnceAndServeFromDiskAfterwardsEvenOffline() throws IOException {
        String url = "https://cdn.polyhaven.com/asset_img/thumbs/stone_fire_pit.png?width=256&height=256&format=png";
        FakeHttpSource http = new FakeHttpSource().answer(url, PNG);

        assertArrayEquals(PNG, new ThumbnailCache(http, cache).thumbnail(asset(url)));
        http.offline = true;
        assertArrayEquals(PNG, new ThumbnailCache(http, cache).thumbnail(asset(url)), "from the disk cache");

        assertEquals(List.of(url), http.requests, "fetched once");
        assertTrue(new File(cache, "polyhaven/thumbs/stone_fire_pit").isFile());
    }

    @Test
    void shouldRefuseAnImageTheEditorCannotDecode() {
        String url = "https://cdn.polyhaven.com/asset_img/thumbs/stone_fire_pit.png";
        FakeHttpSource http = new FakeHttpSource().answer(url, WEBP);

        IOException e = assertThrows(IOException.class, () -> new ThumbnailCache(http, cache).thumbnail(asset(url)));

        assertTrue(e.getMessage().contains("not a PNG or JPEG"), e.getMessage());
        assertTrue(!new File(cache, "polyhaven/thumbs/stone_fire_pit").exists(), "nothing cached");
    }

    @Test
    void shouldSayWhenThereIsNoPreview() {
        IOException e = assertThrows(IOException.class, () -> new ThumbnailCache(new FakeHttpSource(), cache).thumbnail(asset("")));

        assertTrue(e.getMessage().contains("has no preview"), e.getMessage());
    }

    @Test
    void shouldAskBothProvidersForPngThumbnails() throws IOException {
        FakeHttpSource http = new FakeHttpSource()
            .answer(PolyHavenProvider.API + "/assets?t=models", PolyHavenProviderTest.CATALOGUE)
            .answer(AmbientCgProvider.API + "?type=material&sort=popular&limit=500&offset=0&include=title,tags",
                "{\"totalResults\": 1, \"assets\": [{\"id\": \"Ground111\", \"title\": \"Ground 111\", \"tags\": []}]}");

        RemoteAsset pit = new PolyHavenProvider(http, cache).catalog(AssetType.MODEL).search("fire").get(0);
        RemoteAsset ground = new AmbientCgProvider(http, cache).catalog(AssetType.TEXTURE).assets().get(0);

        assertEquals("https://cdn.polyhaven.com/asset_img/thumbs/stone_fire_pit.png?width=256&height=256&format=png", pit.thumbnailUrl(),
            "never the API's WebP address");
        assertEquals("https://acg-media.struffelproductions.com/file/ambientCG-Web/media/thumbnail/256-PNG/Ground111.png",
            ground.thumbnailUrl());
    }
}
