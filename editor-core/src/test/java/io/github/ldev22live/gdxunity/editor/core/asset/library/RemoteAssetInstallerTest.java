package io.github.ldev22live.gdxunity.editor.core.asset.library;

import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import io.github.ldev22live.gdxunity.engine.material.Material;
import io.github.ldev22live.gdxunity.engine.material.MaterialIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteAssetInstallerTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    private static final long PLENTY = Long.MAX_VALUE / 4;

    @TempDir
    File assets;
    @TempDir
    File staging;
    @TempDir
    File cache;

    private FakeHttpSource http;
    private PolyHavenProvider provider;

    @BeforeEach
    void setUp() {
        http = new FakeHttpSource();
        provider = new PolyHavenProvider(http, cache);
    }

    private static RemoteAsset asset(String id, AssetType type) {
        return new RemoteAsset("polyhaven", id, "Name of " + id, type, List.of("Rob Tuytel"), List.of("brick"), List.of(),
            "https://polyhaven.com/a/" + id, "", 1);
    }

    /** A file served by the fake provider, with its true MD5 unless {@code md5} is given. */
    private DownloadOption.RemoteFile served(String path, String content, DownloadOption.Role role) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String url = "https://dl.polyhaven.org/" + path;
        http.answer(url, bytes);
        return new DownloadOption.RemoteFile(path, url, bytes.length, md5(bytes), role);
    }

    private DownloadOption textureSet(String id, String resolution) {
        return new DownloadOption(resolution, List.of(
            served(id + "_diff_" + resolution + ".jpg", "diffuse " + resolution, DownloadOption.Role.BASE_COLOR),
            served(id + "_nor_gl_" + resolution + ".jpg", "normal " + resolution, DownloadOption.Role.NORMAL),
            served(id + "_arm_" + resolution + ".jpg", "arm " + resolution, DownloadOption.Role.OCCLUSION_ROUGHNESS_METALLIC)));
    }

    private RemoteAssetInstaller.Result install(RemoteAsset asset, DownloadOption option) throws IOException {
        return RemoteAssetInstaller.install(provider, asset, option, assets, TODAY, http, (done, total, file) -> { }, () -> false,
            staging, folder -> PLENTY);
    }

    @Test
    void shouldInstallAModelWithItsFilesAndAManifestEntryWithSha256() throws IOException {
        RemoteAsset pit = asset("stone_fire_pit", AssetType.MODEL);
        DownloadOption option = new DownloadOption("2k", List.of(
            served("stone_fire_pit_2k.gltf", "{\"asset\":{}}", DownloadOption.Role.OTHER),
            served("stone_fire_pit.bin", "binary", DownloadOption.Role.OTHER),
            served("textures/stone_fire_pit_diff_2k.jpg", "jpeg", DownloadOption.Role.OTHER)));

        RemoteAssetInstaller.Result result = install(pit, option);

        String folder = "models/thirdparty/polyhaven/stone_fire_pit/";
        assertEquals(List.of(folder + "stone_fire_pit_2k.gltf", folder + "stone_fire_pit.bin", folder + "textures/stone_fire_pit_diff_2k.jpg"),
            result.files());
        assertArrayEquals("binary".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(new File(assets, folder + "stone_fire_pit.bin").toPath()));
        ThirdPartyManifest.Entry entry = ThirdPartyManifest.read(assets).entry("polyhaven-stone-fire-pit").orElseThrow();
        assertEquals("model", entry.category());
        assertEquals("gltf", entry.format());
        assertEquals("CC0-1.0", entry.licence());
        assertEquals(PolyHavenProvider.LICENCE, entry.licenceEvidence());
        assertEquals("2026-11-19", entry.retrieved());
        assertEquals("ForgeRoom Asset Library: Poly Haven public API, 2K, MD5 checked", entry.retrievedWith());
        assertEquals(List.of("Rob Tuytel"), entry.authors());
        assertEquals(sha256("binary".getBytes(StandardCharsets.UTF_8)), entry.files().get(1).sha256());
        assertEquals("stone_fire_pit_diff_2k.jpg", entry.files().get(2).original());
        assertEquals("", result.materialPath(), "a model keeps its own materials");
        assertEquals(0, staging.listFiles().length, "staging removed");
    }

    @Test
    void shouldMakeAMaterialThatUsesADownloadedTextureSet() throws IOException {
        RemoteAssetInstaller.Result result = install(asset("red_brick_03", AssetType.TEXTURE), textureSet("red_brick_03", "2k"));

        assertEquals("materials/RedBrick03.material", result.materialPath());
        Material material = MaterialIO.fromJson(Files.readString(new File(assets, result.materialPath()).toPath()));
        String folder = "textures/thirdparty/polyhaven/red_brick_03/";
        assertEquals("RedBrick03", material.getName(), "a material is named as its file");
        assertEquals(folder + "red_brick_03_diff_2k.jpg", material.getTexture(Material.Slot.BASE_COLOR));
        assertEquals(folder + "red_brick_03_nor_gl_2k.jpg", material.getTexture(Material.Slot.NORMAL));
        assertEquals(folder + "red_brick_03_arm_2k.jpg", material.getTexture(Material.Slot.METALLIC_ROUGHNESS));
        assertEquals(folder + "red_brick_03_arm_2k.jpg", material.getTexture(Material.Slot.OCCLUSION));
        assertEquals(1f, material.getRoughness());
        assertEquals("jpg", ThirdPartyManifest.read(assets).entry("polyhaven-red-brick-03").orElseThrow().format());
    }

    @Test
    void shouldReplaceAnEarlierDownloadAndKeepThePersonsMaterialSettingsAndFiles() throws IOException {
        RemoteAsset brick = asset("red_brick_03", AssetType.TEXTURE);
        RemoteAssetInstaller.Result first = install(brick, textureSet("red_brick_03", "1k"));
        File materialFile = new File(assets, first.materialPath());
        Material edited = MaterialIO.fromJson(Files.readString(materialFile.toPath()));
        edited.setTiling(3f, 1.5f);
        Files.writeString(materialFile.toPath(), MaterialIO.toJson(edited));
        File persons = new File(assets, "textures/thirdparty/polyhaven/red_brick_03/my_notes.txt");
        Files.writeString(persons.toPath(), "mine");

        install(brick, textureSet("red_brick_03", "2k"));

        File folder = new File(assets, "textures/thirdparty/polyhaven/red_brick_03");
        assertFalse(new File(folder, "red_brick_03_diff_1k.jpg").exists(), "the earlier download's files are gone");
        assertTrue(new File(folder, "red_brick_03_diff_2k.jpg").isFile());
        assertTrue(persons.isFile(), "a file the download did not record stays");
        Material after = MaterialIO.fromJson(Files.readString(materialFile.toPath()));
        assertEquals(3f, after.getTilingU(), "the person's tiling stays");
        assertEquals("textures/thirdparty/polyhaven/red_brick_03/red_brick_03_diff_2k.jpg", after.getTexture(Material.Slot.BASE_COLOR));
        assertEquals(3, ThirdPartyManifest.read(assets).entry("polyhaven-red-brick-03").orElseThrow().files().size());
    }

    @Test
    void shouldRefuseADamagedFileAndLeaveTheProjectAsItWas() throws IOException {
        RemoteAsset brick = asset("red_brick_03", AssetType.TEXTURE);
        install(brick, textureSet("red_brick_03", "1k"));
        String manifestBefore = Files.readString(new File(assets, ThirdPartyManifest.PATH).toPath());
        DownloadOption good = textureSet("red_brick_03", "2k");
        DownloadOption.RemoteFile damaged = good.files().get(2);
        DownloadOption bad = new DownloadOption("2k", List.of(good.files().get(0), good.files().get(1),
            new DownloadOption.RemoteFile(damaged.path(), damaged.url(), damaged.bytes(), "00000000000000000000000000000000", damaged.role())));

        RemoteAssetInstaller.RefusedException e = assertThrows(RemoteAssetInstaller.RefusedException.class, () -> install(brick, bad));

        assertTrue(e.getMessage().contains("arrived damaged"), e.getMessage());
        assertEquals(manifestBefore, Files.readString(new File(assets, ThirdPartyManifest.PATH).toPath()));
        File folder = new File(assets, "textures/thirdparty/polyhaven/red_brick_03");
        assertTrue(new File(folder, "red_brick_03_arm_1k.jpg").isFile(), "the earlier download is untouched");
        assertFalse(new File(folder, "red_brick_03_diff_2k.jpg").exists(), "nothing of the failed download");
        assertEquals(0, staging.listFiles().length);
    }

    @Test
    void shouldRefuseAFileLargerThanTheProviderStated() {
        DownloadOption.RemoteFile real = served("a.jpg", "twelve bytes", DownloadOption.Role.OTHER);
        DownloadOption option = new DownloadOption("1k", List.of(new DownloadOption.RemoteFile("a.jpg", real.url(), 4, "", real.role())));

        RemoteAssetInstaller.RefusedException e = assertThrows(RemoteAssetInstaller.RefusedException.class,
            () -> install(asset("thing", AssetType.MODEL), option));

        assertTrue(e.getMessage().contains("larger than"), e.getMessage());
        assertFalse(new File(assets, "models").exists());
    }

    @Test
    void shouldRefuseMoreThanTheLimitBeforeDownloadingAnything() {
        long half = RemoteAssetInstaller.MAX_BYTES / 2 + 1;
        DownloadOption huge = new DownloadOption("16k", List.of(
            new DownloadOption.RemoteFile("a.jpg", "https://dl.polyhaven.org/a", half, "", DownloadOption.Role.OTHER),
            new DownloadOption.RemoteFile("b.jpg", "https://dl.polyhaven.org/b", half, "", DownloadOption.Role.OTHER)));

        RemoteAssetInstaller.RefusedException e = assertThrows(RemoteAssetInstaller.RefusedException.class,
            () -> install(asset("thing", AssetType.TEXTURE), huge));

        assertTrue(e.getMessage().contains("4 GB limit"), e.getMessage());
        assertTrue(http.requests.isEmpty(), "nothing downloaded");
    }

    @Test
    void shouldRefuseWhenThereIsNotEnoughFreeDiskSpace() {
        DownloadOption option = textureSet("red_brick_03", "1k");

        RemoteAssetInstaller.RefusedException e = assertThrows(RemoteAssetInstaller.RefusedException.class,
            () -> RemoteAssetInstaller.install(provider, asset("red_brick_03", AssetType.TEXTURE), option, assets, TODAY, http,
                (done, total, file) -> { }, () -> false, staging, folder -> folder.equals(assets) ? 1024L : PLENTY));

        assertTrue(e.getMessage().contains("not enough free disk space"), e.getMessage());
        assertTrue(http.requests.isEmpty());
    }

    @Test
    void shouldRefuseUnsafePathsAndFormatsForgeRoomDoesNotRead() {
        DownloadOption option = new DownloadOption("1k", List.of(
            new DownloadOption.RemoteFile("../escape.jpg", "https://dl.polyhaven.org/a", 1, "", DownloadOption.Role.OTHER),
            new DownloadOption.RemoteFile("model.fbx", "https://dl.polyhaven.org/b", 1, "", DownloadOption.Role.OTHER),
            new DownloadOption.RemoteFile("run.exe", "https://dl.polyhaven.org/c", 1, "", DownloadOption.Role.OTHER)));

        RemoteAssetInstaller.RefusedException e = assertThrows(RemoteAssetInstaller.RefusedException.class,
            () -> install(asset("thing", AssetType.MODEL), option));

        assertEquals(3, e.getProblems().size(), e.getProblems().toString());
        assertTrue(http.requests.isEmpty());
    }

    @Test
    void shouldStopWhenCancelledAndLeaveNothing() {
        DownloadOption option = textureSet("red_brick_03", "1k");
        AtomicInteger files = new AtomicInteger();
        List<String> started = new ArrayList<>();

        assertThrows(RemoteAssetInstaller.CancelledException.class, () -> RemoteAssetInstaller.install(provider,
            asset("red_brick_03", AssetType.TEXTURE), option, assets, TODAY, http, (done, total, file) -> {
                if (!started.contains(file)) {
                    started.add(file);
                    files.incrementAndGet();
                }
            }, () -> files.get() >= 2, staging, folder -> PLENTY));

        assertFalse(new File(assets, "textures").exists());
        assertFalse(new File(assets, ThirdPartyManifest.PATH).exists());
        assertFalse(new File(assets, "materials").exists());
        assertEquals(0, staging.listFiles().length);
    }

    @Test
    void shouldAskFirstOnlyAboveFiveHundredMegabytes() {
        DownloadOption small = new DownloadOption("2k", List.of(new DownloadOption.RemoteFile("a.jpg", "https://dl.polyhaven.org/a",
            RemoteAssetInstaller.CONFIRM_BYTES, "", DownloadOption.Role.OTHER)));
        DownloadOption large = new DownloadOption("8k", List.of(new DownloadOption.RemoteFile("a.jpg", "https://dl.polyhaven.org/a",
            RemoteAssetInstaller.CONFIRM_BYTES + 1, "", DownloadOption.Role.OTHER)));

        assertFalse(RemoteAssetInstaller.needsConfirmation(small));
        assertTrue(RemoteAssetInstaller.needsConfirmation(large));
        assertEquals("500.0 MB", DownloadOption.formatBytes(RemoteAssetInstaller.CONFIRM_BYTES));
    }

    /** A zip shaped like ambientCG's, served by the fake provider; {@code extra} names more entries (with "x" as content). */
    private DownloadOption.RemoteFile servedZip(String id, String... extra) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String map : List.of("Color", "NormalGL", "NormalDX", "Roughness", "AmbientOcclusion", "Displacement")) {
                zip.putNextEntry(new ZipEntry(id + "_1K-JPG_" + map + ".jpg"));
                zip.write(("map " + map).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            for (String name : List.of(id + "_1K-JPG.blend", id + "_1K-JPG.mtlx", id + "_1K-JPG.usdc", id + "_1K-JPG.tres")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(1);
                zip.closeEntry();
            }
            for (String name : extra) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write('x');
                zip.closeEntry();
            }
        }
        String url = "https://ambientcg.com/get?file=" + id + "_1K-JPG.zip";
        http.answer(url, bytes.toByteArray());
        return new DownloadOption.RemoteFile(id + "_1K-JPG.zip", url, bytes.size(), "", DownloadOption.Role.ARCHIVE);
    }

    private static RemoteAsset ambientCg(String id) {
        return new RemoteAsset("ambientcg", id, id, AssetType.TEXTURE, List.of("ambientCG"), List.of(), List.of(),
            "https://ambientcg.com/view?id=" + id, "", 1);
    }

    @Test
    void shouldUnpackAnArchiveKeepingReadableFormatsAndListingTheRest() throws IOException {
        AmbientCgProvider ambient = new AmbientCgProvider(http, cache);
        DownloadOption option = new DownloadOption("1K-JPG", List.of(servedZip("Ground111")));

        RemoteAssetInstaller.Result result = RemoteAssetInstaller.install(ambient, ambientCg("Ground111"), option, assets, TODAY, http,
            (done, total, file) -> { }, () -> false, staging, folder -> PLENTY);

        String folder = "textures/thirdparty/ambientcg/Ground111/";
        assertEquals(6, result.files().size(), result.files().toString());
        assertTrue(new File(assets, folder + "Ground111_1K-JPG_Color.jpg").isFile());
        assertFalse(new File(assets, folder + "Ground111_1K-JPG.blend").exists());
        assertFalse(new File(assets, folder + "Ground111_1K-JPG.zip").exists(), "the zip itself is not kept");
        assertEquals(List.of("Ground111_1K-JPG.blend", "Ground111_1K-JPG.mtlx", "Ground111_1K-JPG.usdc", "Ground111_1K-JPG.tres"),
            result.leftOut());
        ThirdPartyManifest.Entry entry = ThirdPartyManifest.read(assets).entry("ambientcg-ground111").orElseThrow();
        assertEquals("imported; left out 4 file(s) ForgeRoom cannot read (.blend, .mtlx, .tres, .usdc)", entry.importStatus());
        assertEquals(AmbientCgProvider.LICENCE, entry.licenceEvidence());
        assertEquals(sha256("map Color".getBytes(StandardCharsets.UTF_8)),
            entry.files().stream().filter(f -> f.original().endsWith("_Color.jpg")).findFirst().orElseThrow().sha256());
        assertEquals(0, staging.listFiles().length);
    }

    @Test
    void shouldMakeAMaterialFromSeparateRoughnessAndOcclusionMapsWithNoMetalness() throws IOException {
        AmbientCgProvider ambient = new AmbientCgProvider(http, cache);
        DownloadOption option = new DownloadOption("1K-JPG", List.of(servedZip("Ground111")));

        RemoteAssetInstaller.Result result = RemoteAssetInstaller.install(ambient, ambientCg("Ground111"), option, assets, TODAY, http,
            (done, total, file) -> { }, () -> false, staging, folder -> PLENTY);

        assertEquals("materials/Ground111.material", result.materialPath());
        Material material = MaterialIO.fromJson(Files.readString(new File(assets, result.materialPath()).toPath()));
        String folder = "textures/thirdparty/ambientcg/Ground111/Ground111_1K-JPG_";
        assertEquals(folder + "Color.jpg", material.getTexture(Material.Slot.BASE_COLOR));
        assertEquals(folder + "NormalGL.jpg", material.getTexture(Material.Slot.NORMAL), "the OpenGL normal, not DirectX");
        assertEquals(folder + "Roughness.jpg", material.getTexture(Material.Slot.METALLIC_ROUGHNESS));
        assertEquals(folder + "AmbientOcclusion.jpg", material.getTexture(Material.Slot.OCCLUSION));
        assertEquals(0f, material.getMetallic(), "a grey roughness map's blue channel must not read as metal");
        assertEquals(1f, material.getRoughness());
    }

    @Test
    void shouldRefuseAnArchiveWithAnExecutableOrUnsafePathAndWriteNothing() throws IOException {
        AmbientCgProvider ambient = new AmbientCgProvider(http, cache);
        DownloadOption option = new DownloadOption("1K-JPG", List.of(servedZip("Ground111", "setup.exe", "../outside.jpg")));

        RemoteAssetInstaller.RefusedException e = assertThrows(RemoteAssetInstaller.RefusedException.class,
            () -> RemoteAssetInstaller.install(ambient, ambientCg("Ground111"), option, assets, TODAY, http,
                (done, total, file) -> { }, () -> false, staging, folder -> PLENTY));

        assertEquals(2, e.getProblems().size(), e.getProblems().toString());
        assertFalse(new File(assets, "textures").exists());
        assertFalse(new File(assets, "materials").exists());
        assertFalse(new File(assets, ThirdPartyManifest.PATH).exists());
        assertFalse(new File(assets.getParentFile(), "outside.jpg").exists());
        assertEquals(0, staging.listFiles().length);
    }

    @Test
    void shouldNameMaterialsAfterTheAssetInPascalCase() {
        assertEquals("RedBrick03", RemoteAssetInstaller.materialName("red_brick_03"));
        assertEquals("WoodFloorDeck", RemoteAssetInstaller.materialName("wood_floor_deck"));
    }

    private static String md5(byte[] bytes) {
        return hex("MD5", bytes);
    }

    private static String sha256(byte[] bytes) {
        return hex("SHA-256", bytes);
    }

    private static String hex(String algorithm, byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
