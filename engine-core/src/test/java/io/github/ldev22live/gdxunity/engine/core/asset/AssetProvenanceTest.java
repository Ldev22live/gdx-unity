package io.github.ldev22live.gdxunity.engine.core.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetProvenanceTest {
    private static final String SHA = "b".repeat(64);
    private static final String MANIFEST = "{\"schemaVersion\":1,\"kind\":\"third-party-assets\",\"assets\":[{\"id\":\"kenney-nature-kit\","
        + "\"name\":\"Nature Kit\",\"category\":\"models\",\"source\":\"https://kenney.nl/assets/nature-kit\",\"licence\":\"CC0-1.0\","
        + "\"licenceEvidence\":\"https://kenney.nl/support\",\"authors\":[\"Kenney\"],\"retrieved\":\"2026-11-20\","
        + "\"retrievedWith\":\"a person\",\"format\":\"png\",\"conversion\":\"none\",\"importStatus\":\"imported\","
        + "\"files\":[{\"path\":\"thirdparty/kenney/nature-kit/Side/tree.png\",\"original\":\"tree.png\",\"bytes\":5,\"sha256\":\""
        + SHA + "\"}]}]}";
    private static final String ORIGINALS = OriginalAssetsTest.record(OriginalAssetsTest.file("sprites/hero.sprite", "Apache-2.0")
        + ",{\"path\":\"prefabs/level/Tree.png\",\"bytes\":3,\"sha256\":\"" + "c".repeat(64) + "\",\"licence\":\"Apache-2.0\","
        + "\"madeWith\":\"tools/make_palette_thumbnails.py\",\"derivedFrom\":[\"kenney-nature-kit\"]}");

    private static AssetProvenance provenance() {
        return new AssetProvenance(ThirdPartyManifest.parse(MANIFEST), OriginalAssets.parse(ORIGINALS));
    }

    @Test
    void shouldNameTheLicenceAndMakerOfADownloadedFile() {
        AssetProvenance.Provenance tree = provenance().of("thirdparty/kenney/nature-kit/Side/tree.png");

        assertEquals(AssetProvenance.Kind.THIRD_PARTY, tree.kind());
        assertEquals("CC0-1.0", tree.licence());
        assertEquals("CC0-1.0: Nature Kit by Kenney (https://kenney.nl/assets/nature-kit)", tree.summary());
        assertEquals(SHA, tree.sha256());
    }

    @Test
    void shouldSayHowAnOriginalWasMadeAndWhichDownloadsItShows() {
        AssetProvenance.Provenance hero = provenance().of("sprites/hero.sprite");
        AssetProvenance.Provenance thumbnail = provenance().of("prefabs/level/Tree.png");

        assertEquals(AssetProvenance.Kind.ORIGINAL, hero.kind());
        assertEquals("Apache-2.0: made in this repository with tools/make_x.py", hero.summary());
        assertEquals("Apache-2.0: made in this repository with tools/make_palette_thumbnails.py; shows Nature Kit by Kenney, CC0-1.0",
            thumbnail.summary());
    }

    @Test
    void shouldSayPlainlyThatAFileInNeitherRecordHasNoLicenceRecord() {
        AssetProvenance.Provenance unknown = provenance().of("sprites/mine.sprite");

        assertEquals(AssetProvenance.Kind.UNRECORDED, unknown.kind());
        assertEquals("", unknown.licence());
        assertEquals(AssetProvenance.UNRECORDED_SUMMARY, unknown.summary());
    }

    @Test
    void shouldReadBothRecordsFromAProject(@TempDir File project) throws IOException {
        Files.createDirectories(project.toPath().resolve("licenses"));
        Files.writeString(project.toPath().resolve(ThirdPartyManifest.PATH), MANIFEST, StandardCharsets.UTF_8);
        Files.writeString(project.toPath().resolve(OriginalAssets.PATH), ORIGINALS, StandardCharsets.UTF_8);

        AssetProvenance read = AssetProvenance.read(project);

        assertEquals(AssetProvenance.Kind.THIRD_PARTY, read.of("thirdparty/kenney/nature-kit/Side/tree.png").kind());
        assertEquals(AssetProvenance.Kind.ORIGINAL, read.of("sprites/hero.sprite").kind());
    }
}
