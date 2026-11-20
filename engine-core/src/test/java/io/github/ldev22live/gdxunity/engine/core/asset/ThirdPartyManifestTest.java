package io.github.ldev22live.gdxunity.engine.core.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The third-party asset manifest (docs/asset-integration-report.md): what it reads and what it refuses. */
class ThirdPartyManifestTest {
    private static final String SHA = "a".repeat(64);

    @TempDir
    Path project;

    private static String manifest(String licence, String path, String extra) {
        return "{\"schemaVersion\":1,\"kind\":\"third-party-assets\",\"assets\":[{\"id\":\"polyhaven-red-brick-03\","
            + "\"name\":\"Red Brick 03\",\"category\":\"texture\",\"source\":\"https://polyhaven.com/a/red_brick_03\","
            + "\"licence\":\"" + licence + "\",\"licenceEvidence\":\"https://polyhaven.com/license\",\"authors\":[\"Rob Tuytel\"],"
            + "\"retrieved\":\"2026-11-19\",\"retrievedWith\":\"tools/thirdparty_assets.py\",\"format\":\"jpg\",\"dependencies\":[],"
            + "\"conversion\":\"none\",\"importStatus\":\"imported\"" + extra + ",\"files\":[{\"path\":\"" + path + "\","
            + "\"original\":\"red_brick_03_diff_1k.jpg\",\"bytes\":10,\"sha256\":\"" + SHA + "\"}]}]}";
    }

    @Test
    void shouldFindTheAssetOfADownloadedFileAndNothingForAProjectFile() {
        ThirdPartyManifest manifest = ThirdPartyManifest.parse(manifest("CC0-1.0", "textures/thirdparty/a.jpg", ""));

        ThirdPartyManifest.Entry entry = manifest.find("textures/thirdparty/a.jpg").orElseThrow();
        assertEquals("Red Brick 03", entry.name());
        assertEquals("CC0-1.0", entry.licence());
        assertEquals(List.of("Rob Tuytel"), entry.authors());
        assertEquals(SHA, entry.files().get(0).sha256());
        assertFalse(manifest.find("textures/brick.png").isPresent(), "made in the project");
        assertEquals(Set.of("textures/thirdparty/a.jpg"), manifest.paths());
    }

    @Test
    void shouldRefuseALicenceItCannotShipUnderAndAnUnsafePath() {
        IllegalArgumentException licence = assertThrows(IllegalArgumentException.class,
            () -> ThirdPartyManifest.parse(manifest("CC-BY-4.0", "textures/a.jpg", "")));
        assertTrue(licence.getMessage().contains("CC-BY-4.0"), licence.getMessage());
        for (String path : List.of("../outside.jpg", "/abs.jpg", "a\\\\b.jpg", "C:/x.jpg", "a//b.jpg")) {
            assertThrows(IllegalArgumentException.class, () -> ThirdPartyManifest.parse(manifest("CC0-1.0", path, "")), path);
        }
    }

    @Test
    void shouldRefuseAMissingFieldAMalformedHashAndAWrongKind() {
        String noAuthor = manifest("CC0-1.0", "a.jpg", "").replace("\"authors\":[\"Rob Tuytel\"]", "\"authors\":[]");
        assertThrows(IllegalArgumentException.class, () -> ThirdPartyManifest.parse(noAuthor));
        String noSource = manifest("CC0-1.0", "a.jpg", "").replace("\"source\":\"https://polyhaven.com/a/red_brick_03\",", "");
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> ThirdPartyManifest.parse(noSource));
        assertTrue(missing.getMessage().contains("'source'"), missing.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ThirdPartyManifest.parse(manifest("CC0-1.0", "a.jpg", "").replace(SHA, "xyz")));
        assertThrows(IllegalArgumentException.class, () -> ThirdPartyManifest.parse("{\"schemaVersion\":1,\"kind\":\"scene\",\"assets\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> ThirdPartyManifest.parse("not json"));
    }

    /** UI M4: the editor writes the manifest as the download tool does, so neither changes the other's lines. */
    @Test
    void shouldWriteTheShippedManifestBackByteForByte() throws IOException {
        File shipped = new File("../assets/" + ThirdPartyManifest.PATH);
        String text = Files.readString(shipped.toPath(), StandardCharsets.UTF_8);

        assertEquals(text, ThirdPartyManifest.parse(text).toJson());
    }

    @Test
    void shouldReplaceAnEntryByIdKeepIdOrderAndRefuseAFileTwice() {
        ThirdPartyManifest manifest = ThirdPartyManifest.parse(manifest("CC0-1.0", "textures/thirdparty/a.jpg", ""));
        ThirdPartyManifest.Entry first = manifest.entries().get(0);
        ThirdPartyManifest.Entry other = new ThirdPartyManifest.Entry("aaa-first", "A", "texture", "https://x", "CC0-1.0", "https://x",
            List.of("Ü Maker"), "2026-11-19", "tool", "jpg", "none", "imported",
            List.of(new ThirdPartyManifest.FileRecord("textures/thirdparty/b.jpg", "b.jpg", 1, SHA)));

        ThirdPartyManifest added = manifest.with(other);
        assertEquals(List.of("aaa-first", "polyhaven-red-brick-03"), added.entries().stream().map(ThirdPartyManifest.Entry::id).toList());
        assertEquals(added.entries(), ThirdPartyManifest.parse(added.toJson()).entries(), "what it writes reads back the same");
        assertTrue(added.toJson().contains("\"\\u00dc Maker\""), "non-ASCII escaped as the tool does");
        assertEquals(1, added.with(other).entries().stream().filter(e -> e.id().equals("aaa-first")).count(), "replaced, not added");
        ThirdPartyManifest.Entry clash = new ThirdPartyManifest.Entry("zzz", "Z", "texture", "https://x", "CC0-1.0", "https://x",
            List.of("Z"), "2026-11-19", "tool", "jpg", "none", "imported", first.files());
        assertThrows(IllegalArgumentException.class, () -> manifest.with(clash), "one file, two entries");
    }

    @Test
    void shouldReadNothingForAProjectThatDownloadedNothing() throws IOException {
        assertTrue(ThirdPartyManifest.read(project.toFile()).entries().isEmpty());
    }
}
