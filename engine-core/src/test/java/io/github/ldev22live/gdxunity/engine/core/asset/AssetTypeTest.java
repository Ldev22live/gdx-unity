package io.github.ldev22live.gdxunity.engine.core.asset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetTypeTest {
    @Test
    void shouldRecognizeSpriteFilesByExtensionAndPathInAnyCase() {
        assertEquals(AssetType.SPRITE, AssetType.fromExtension("sprite"));
        assertEquals(AssetType.SPRITE, AssetType.fromExtension("SPRITE"));
        assertEquals(AssetType.SPRITE, AssetType.fromPath("sprites/hero.sprite"));
        assertEquals(AssetType.VECTOR, AssetType.fromPath("vectors/house.vector"));
    }

    @Test
    void shouldStillListTheSheetPngAsATextureAndItsAtlasAsUnknown() {
        assertEquals(AssetType.TEXTURE, AssetType.fromPath("sprites/hero.png"));
        assertEquals(AssetType.UNKNOWN, AssetType.fromPath("sprites/hero.sheet.json"));
    }

    @Test
    void shouldRecognizeSongFilesSoTheContentBrowserListsThem() {
        assertEquals(AssetType.SONG, AssetType.fromExtension("song"));
        assertEquals(AssetType.SONG, AssetType.fromPath("music/demo.song"));
    }

    @Test
    void shouldKeepTheOtherTypes() {
        assertEquals(AssetType.GRAPH, AssetType.fromPath("a.graph"));
        assertEquals(AssetType.SOUND, AssetType.fromPath("a.wav"));
        assertEquals(AssetType.UNKNOWN, AssetType.fromPath("noextension"));
    }
}
