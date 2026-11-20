package io.github.ldev22live.gdxunity.editor.core.asset.library;

import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Importing a downloaded asset pack (UI M4, TD-094, TD-095): what is copied, recorded, left out and refused. */
class PackImporterTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    private static final String CC0 = "Nature Kit by Kenney. License: (Creative Commons Zero, CC0) http://creativecommons.org/publicdomain/zero/1.0/";
    private static final CuratedPack NATURE = KenneyPacks.ALL.stream().filter(p -> p.id().equals("nature-kit")).findFirst().orElseThrow();

    @TempDir
    Path folder;

    private File zip(Map<String, String> entries) throws IOException {
        File file = folder.resolve("pack.zip").toFile();
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                if (!entry.getKey().endsWith("/")) {
                    out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                out.closeEntry();
            }
        }
        return file;
    }

    private static Map<String, String> pack(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private File assets() throws IOException {
        return Files.createDirectories(folder.resolve("assets")).toFile();
    }

    private static String sha256(String text) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldCopyTheReadableFilesKeepTheLayoutRecordEachAndListWhatItLeftOut() throws Exception {
        File assets = assets();
        File archive = zip(pack("License.txt", CC0, "Models/GLTF format/tree.glb", "glb", "Models/FBX format/tree.fbx", "fbx",
            "Models/GLTF format/", "", "Previews/tree.png", "png"));

        PackImporter.Result result = PackImporter.importPack(archive, NATURE, assets, TODAY);

        assertEquals(List.of("thirdparty/kenney/nature-kit/License.txt", "thirdparty/kenney/nature-kit/Models/GLTF format/tree.glb",
            "thirdparty/kenney/nature-kit/Previews/tree.png"), result.imported());
        assertEquals(List.of("Models/FBX format/tree.fbx"), result.leftOut(), "an FBX is listed, never imported silently");
        assertEquals("glb", Files.readString(assets.toPath().resolve("thirdparty/kenney/nature-kit/Models/GLTF format/tree.glb")));
        assertFalse(Files.exists(assets.toPath().resolve("thirdparty/kenney/nature-kit/Models/FBX format/tree.fbx")));

        ThirdPartyManifest manifest = ThirdPartyManifest.read(assets);
        ThirdPartyManifest.Entry entry = manifest.entry("kenney-nature-kit").orElseThrow();
        assertEquals("CC0-1.0", entry.licence());
        assertEquals("https://kenney.nl/assets/nature-kit", entry.source());
        assertEquals(List.of("Kenney"), entry.authors());
        assertEquals("2026-11-19", entry.retrieved());
        assertEquals("glb, png", entry.format());
        assertTrue(entry.importStatus().contains("left out 1 file(s) ForgeRoom cannot read (.fbx)"), entry.importStatus());
        assertTrue(entry.retrievedWith().contains("pack.zip"), "the archive's name, never a folder of the person's machine");
        assertEquals(sha256("glb"), manifest.find("thirdparty/kenney/nature-kit/Models/GLTF format/tree.glb").orElseThrow()
            .files().stream().filter(f -> f.original().equals("tree.glb")).findFirst().orElseThrow().sha256());
    }

    @Test
    void shouldRefuseUnsafePathsExecutablesAndScriptsWritingNothing() throws IOException {
        File assets = assets();
        for (Map<String, String> bad : List.of(
            pack("License.txt", CC0, "../outside.png", "x"),
            pack("License.txt", CC0, "/abs.png", "x"),
            pack("License.txt", CC0, "a\\b.png", "x"),
            pack("License.txt", CC0, "tree.png", "x", "tools/setup.exe", "x"),
            pack("License.txt", CC0, "tree.png", "x", "build.sh", "x"))) {
            File archive = zip(bad);

            PackImporter.RefusedException refused = assertThrows(PackImporter.RefusedException.class,
                () -> PackImporter.importPack(archive, NATURE, assets, TODAY), bad.keySet().toString());

            assertFalse(refused.getProblems().isEmpty());
            try (Stream<Path> written = Files.walk(assets.toPath())) {
                assertEquals(1L, written.count(), "nothing written for " + bad.keySet());
            }
        }
    }

    @Test
    void shouldRefuseAPackWithoutACc0LicenceFile() throws IOException {
        File assets = assets();
        File noLicence = zip(pack("tree.png", "x"));
        PackImporter.RefusedException none = assertThrows(PackImporter.RefusedException.class,
            () -> PackImporter.importPack(noLicence, NATURE, assets, TODAY));
        assertTrue(none.getProblems().get(0).contains("no licence file"), none.getProblems().toString());

        File byLicence = zip(pack("LICENSE.txt", "Creative Commons Attribution 4.0", "tree.png", "x"));
        PackImporter.RefusedException notCc0 = assertThrows(PackImporter.RefusedException.class,
            () -> PackImporter.importPack(byLicence, NATURE, assets, TODAY));
        assertTrue(notCc0.getProblems().get(0).contains("does not say CC0"), notCc0.getProblems().toString());
        assertFalse(Files.exists(assets.toPath().resolve(ThirdPartyManifest.PATH)), "no manifest written");
    }

    @Test
    void shouldReplaceOnlyTheFilesTheEarlierImportOfThePackWrote() throws IOException {
        File assets = assets();
        Path ownFile = assets.toPath().resolve("thirdparty/kenney/nature-kit/notes-of-mine.png");
        PackImporter.importPack(zip(pack("License.txt", CC0, "old.png", "old", "kept.png", "v1")), NATURE, assets, TODAY);
        Files.writeString(ownFile, "mine");

        PackImporter.Result again = PackImporter.importPack(zip(pack("License.txt", CC0, "kept.png", "v2")), NATURE, assets, TODAY);

        assertFalse(Files.exists(assets.toPath().resolve("thirdparty/kenney/nature-kit/old.png")), "the earlier import's file is gone");
        assertEquals("v2", Files.readString(assets.toPath().resolve("thirdparty/kenney/nature-kit/kept.png")));
        assertEquals("mine", Files.readString(ownFile), "a file the import did not write is left alone");
        assertEquals(1, ThirdPartyManifest.read(assets).entries().size(), "one entry for the pack");
        assertEquals(2, again.imported().size());
    }

    @Test
    void shouldOfferTheApprovedKenneyPacksWithHttpsPagesAndTheCc0Statement() {
        assertEquals(9, KenneyPacks.ALL.size());
        for (CuratedPack pack : KenneyPacks.ALL) {
            assertTrue(pack.page().startsWith("https://kenney.nl/assets/" + pack.id()), pack.page());
            assertEquals(KenneyPacks.LICENCE_EVIDENCE, pack.licenceEvidence());
            assertEquals("thirdparty/kenney/" + pack.id(), pack.folder());
        }
        assertThrows(IllegalArgumentException.class, () -> new CuratedPack("Kenney", "x", "X", "x", "https://a", "K", "https://b"));
        assertThrows(IllegalArgumentException.class, () -> new CuratedPack("kenney", "x", "X", "x", "http://a", "K", "https://b"));
    }

    @Test
    void shouldCheckPathsLikeTheDownloadTool() {
        assertTrue(PackImporter.isSafe("Models/GLTF format/tree.glb"));
        for (String bad : List.of("", "../a", "a/../../b", "/a", "C:/a", "a//b", "./a", "a\\b")) {
            assertFalse(PackImporter.isSafe(bad), bad);
        }
    }

    @SuppressWarnings("unused")
    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
