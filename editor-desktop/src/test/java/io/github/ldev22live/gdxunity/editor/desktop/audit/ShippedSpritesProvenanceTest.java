package io.github.ldev22live.gdxunity.editor.desktop.audit;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetProvenance;
import io.github.ldev22live.gdxunity.engine.core.asset.OriginalAssets;
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
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap M2b.5's done-when, "each shipped sprite has a manifest entry" (TD-099): every 2D file the editor package ships (all of
 * {@code assets/} but {@code build/}) has a record at its own path, in {@code licenses/manifest.json} when downloaded or in
 * {@code licenses/originals.json} when made here, and is byte for byte the file recorded.
 *
 * <p>One exception, reported and not hidden: a byte-for-byte copy of a recorded download at a path no record lists (a person's raw
 * download left in the asset folder). Its licence and maker are known from the record of the same bytes, so nothing of unknown
 * licence ships, but it should be deleted or recorded (the M2b.5 report).
 */
class ShippedSpritesProvenanceTest {
    private static final File ASSETS = new File("../assets");
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "sprite");
    private static final String FIX = " (regenerated? run python tools/record_original_assets.py)";

    @Test
    void shouldGiveEveryShipped2DFileARecordWithTheChecksumItHasNow() throws IOException {
        AssetProvenance provenance = AssetProvenance.read(ASSETS);
        Set<String> recordedSha = ThirdPartyManifest.read(ASSETS).entries().stream().flatMap(entry -> entry.files().stream())
            .map(ThirdPartyManifest.FileRecord::sha256).collect(Collectors.toSet());
        Set<String> unrecorded = new TreeSet<>();
        Set<String> copies = new TreeSet<>();
        int recorded = 0;

        for (String path : shipped2DFiles()) {
            String sha = sha256(ASSETS.toPath().resolve(path));
            AssetProvenance.Provenance found = provenance.of(path);
            if (found.kind() == AssetProvenance.Kind.UNRECORDED) {
                if (recordedSha.contains(sha)) {
                    copies.add(path);
                } else {
                    unrecorded.add(path);
                }
                continue;
            }
            assertEquals(found.sha256(), sha, path + " is not the file its record describes" + FIX);
            recorded++;
        }

        assertEquals(Set.of(), unrecorded, "shipped 2D files with no licence record" + FIX);
        assertTrue(recorded > 0, "the shipped samples have 2D art");
        if (!copies.isEmpty()) {
            System.out.println("M2b.5: " + copies.size() + " unrecorded byte-for-byte copies of recorded downloads, first "
                + copies.iterator().next());
        }
    }

    @Test
    void shouldRecordOnlyFilesThatExistAndDownloadsThatAreRecorded() throws IOException {
        OriginalAssets originals = OriginalAssets.read(ASSETS);
        ThirdPartyManifest manifest = ThirdPartyManifest.read(ASSETS);

        assertTrue(originals.files().size() >= 60, "the samples' sprites, textures, thumbnails and previews are recorded");
        for (OriginalAssets.FileRecord file : originals.files()) {
            Path path = ASSETS.toPath().resolve(file.path());
            assertTrue(Files.isRegularFile(path), "originals.json lists a missing file: " + file.path() + FIX);
            assertEquals(file.bytes(), Files.size(path), file.path() + FIX);
            assertTrue(manifest.find(file.path()).isEmpty(), file.path() + " is recorded both as downloaded and as made here");
            for (String id : file.derivedFrom()) {
                assertTrue(manifest.entry(id).isPresent(), file.path() + " shows '" + id + "', which the manifest does not record");
            }
        }
        // The CC0 starter pack's thumbnails say which kit they show
        assertEquals(List.of("kenney-nature-kit"), originals.find("prefabs/level/CC0 Oak Tree.png").orElseThrow().derivedFrom());
        assertEquals(List.of("kenney-building-kit"), originals.find("prefabs/buildings/CC0 House.png").orElseThrow().derivedFrom());
    }

    private static List<String> shipped2DFiles() throws IOException {
        Path root = ASSETS.toPath();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                .map(p -> root.relativize(p).toString().replace(File.separatorChar, '/'))
                .filter(p -> !p.startsWith("build/"))
                .filter(p -> EXTENSIONS.contains(p.substring(p.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)))
                .sorted().toList();
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JDK has no SHA-256", e);
        }
    }
}
