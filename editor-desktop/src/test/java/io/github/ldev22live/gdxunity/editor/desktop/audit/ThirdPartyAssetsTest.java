package io.github.ldev22live.gdxunity.editor.desktop.audit;

import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped third-party assets (docs/asset-integration-report.md): every downloaded file is in {@code assets/licenses/manifest.json}
 * with its licence, and is byte for byte the file that was recorded. Nothing under a {@code thirdparty} folder may be unrecorded,
 * so a file cannot be added or swapped without its licence being stated.
 */
class ThirdPartyAssetsTest {
    private static final File ASSETS = new File("../assets");
    private static final String THIRD_PARTY_FOLDER = "/thirdparty/";

    @Test
    void shouldRecordEveryDownloadedFileWithTheChecksumItHasNow() throws IOException, NoSuchAlgorithmException {
        ThirdPartyManifest manifest = ThirdPartyManifest.read(ASSETS);

        assertFalse(manifest.entries().isEmpty(), "the Poly Haven assets of the asset integration are recorded");
        for (ThirdPartyManifest.Entry entry : manifest.entries()) {
            assertTrue(entry.source().startsWith("https://"), entry.id() + ": " + entry.source());
            assertTrue(entry.licenceEvidence().startsWith("https://"), entry.id());
            for (ThirdPartyManifest.FileRecord record : entry.files()) {
                Path file = ASSETS.toPath().resolve(record.path());
                assertTrue(Files.isRegularFile(file), entry.id() + " lists a missing file: " + record.path());
                byte[] data = Files.readAllBytes(file);
                assertEquals(record.bytes(), data.length, record.path());
                String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
                assertEquals(record.sha256(), sha256, record.path() + " is not the file that was downloaded");
                assertTrue(("/" + record.path()).contains(THIRD_PARTY_FOLDER), record.path() + " is kept in a thirdparty folder");
            }
        }
    }

    @Test
    void shouldHaveNoUnrecordedFileInAThirdPartyFolder() throws IOException {
        Set<String> recorded = ThirdPartyManifest.read(ASSETS).paths();
        Set<String> found;
        try (Stream<Path> walk = Files.walk(ASSETS.toPath())) {
            found = walk.filter(Files::isRegularFile)
                .map(p -> ASSETS.toPath().relativize(p).toString().replace(File.separatorChar, '/'))
                .filter(p -> ("/" + p).contains(THIRD_PARTY_FOLDER) && !p.startsWith("build/"))
                .collect(Collectors.toCollection(TreeSet::new));
        }
        Set<String> unrecorded = new TreeSet<>(found);
        unrecorded.removeAll(recorded);
        assertEquals(Set.of(), unrecorded, "files in a thirdparty folder with no licence record in licenses/manifest.json");
        assertEquals(List.copyOf(new TreeSet<>(recorded)), List.copyOf(found), "and every recorded file is there");
    }
}
