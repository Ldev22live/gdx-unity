package io.github.ldev22live.gdxunity.engine.core.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginalAssetsTest {
    private static final String SHA = "a".repeat(64);

    static String record(String files) {
        return "{\"schemaVersion\":1,\"kind\":\"project-originals\",\"files\":[" + files + "]}";
    }

    static String file(String path, String licence) {
        return "{\"path\":\"" + path + "\",\"bytes\":12,\"sha256\":\"" + SHA + "\",\"licence\":\"" + licence
            + "\",\"madeWith\":\"tools/make_x.py\"}";
    }

    @Test
    void shouldReadEveryFileWithHowItWasMadeAndWhatItShows() {
        OriginalAssets originals = OriginalAssets.parse(record(file("sprites/hero.sprite", "Apache-2.0") + ","
            + "{\"path\":\"prefabs/level/CC0 Rock.png\",\"bytes\":3,\"sha256\":\"" + SHA + "\",\"licence\":\"Apache-2.0\","
            + "\"madeWith\":\"tools/make_palette_thumbnails.py\",\"derivedFrom\":[\"kenney-nature-kit\"]}"));

        assertEquals(2, originals.files().size());
        OriginalAssets.FileRecord hero = originals.find("sprites/hero.sprite").orElseThrow();
        assertEquals(12L, hero.bytes());
        assertEquals("tools/make_x.py", hero.madeWith());
        assertEquals(List.of(), hero.derivedFrom());
        assertEquals(List.of("kenney-nature-kit"), originals.find("prefabs/level/CC0 Rock.png").orElseThrow().derivedFrom());
        assertTrue(originals.find("sprites/coin.sprite").isEmpty());
    }

    @Test
    void shouldRefuseALicenceThatIsNotTheRepositorysOwn() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> OriginalAssets.parse(record(file("a.png", "CC-BY-4.0"))));
        assertTrue(e.getMessage().contains("CC-BY-4.0"), e.getMessage());
    }

    @Test
    void shouldRefuseAnUnsafePathAPathListedTwiceAndAMalformedChecksum() {
        assertThrows(IllegalArgumentException.class, () -> OriginalAssets.parse(record(file("../a.png", "Apache-2.0"))));
        assertThrows(IllegalArgumentException.class,
            () -> OriginalAssets.parse(record(file("a.png", "Apache-2.0") + "," + file("a.png", "Apache-2.0"))));
        assertThrows(IllegalArgumentException.class, () -> OriginalAssets.parse(record(file("a.png", "Apache-2.0").replace(SHA, "xyz"))));
        assertThrows(IllegalArgumentException.class, () -> OriginalAssets.parse("{\"schemaVersion\":1,\"kind\":\"third-party-assets\"}"));
    }

    @Test
    void shouldRefuseAFileWithNoMadeWith() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> OriginalAssets.parse(record(file("a.png", "Apache-2.0").replace(",\"madeWith\":\"tools/make_x.py\"", ""))));
        assertTrue(e.getMessage().contains("madeWith"), e.getMessage());
    }

    @Test
    void shouldReadAProjectWithoutTheRecordAsEmpty(@TempDir File project) throws IOException {
        assertEquals(List.of(), OriginalAssets.read(project).files());
    }
}
