package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.asset.library.AmbientCgProvider;
import io.github.ldev22live.gdxunity.editor.core.asset.library.AssetProvider;
import io.github.ldev22live.gdxunity.editor.core.asset.library.AssetType;
import io.github.ldev22live.gdxunity.editor.core.asset.library.DownloadOption;
import io.github.ldev22live.gdxunity.editor.core.asset.library.HttpSource;
import io.github.ldev22live.gdxunity.editor.core.asset.library.KenneyPacks;
import io.github.ldev22live.gdxunity.editor.core.asset.library.PackImporter;
import io.github.ldev22live.gdxunity.editor.core.asset.library.PolyHavenProvider;
import io.github.ldev22live.gdxunity.editor.core.asset.library.RemoteAsset;
import io.github.ldev22live.gdxunity.editor.core.asset.library.RemoteAssetInstaller;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.material.MaterialAssignment;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import io.github.ldev22live.gdxunity.engine.material.MaterialLibrary;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.ModelComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TerrainComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.TerrainCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The full-map brief's asset journey end to end (UI M8), through the editor's own operations and with recorded provider answers, so
 * it runs offline and the same every time. A Kenney pack is imported, a Poly Haven model and an ambientCG material are found, checked,
 * downloaded and recorded; the model is placed standing on the terrain and the material put on a box; the scene is saved and
 * reopened; undo takes every step back.
 */
class AssetLibraryJourneyTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static final float HILL = 120f;
    private static final float TOLERANCE = 0.01f;

    /** Recorded answers by address; anything else is unreachable. */
    private static final class Recorded implements HttpSource {
        final Map<String, byte[]> answers = new HashMap<>();
        int requests;

        void answer(String url, byte[] body) {
            answers.put(url, body);
        }

        @Override
        public byte[] get(String url) throws IOException {
            requests++;
            byte[] body = answers.get(url);
            if (body == null) {
                throw new IOException("unreachable: " + url);
            }
            return body;
        }

        @Override
        public InputStream open(String url) throws IOException {
            return new ByteArrayInputStream(get(url));
        }
    }

    @TempDir
    Path project;
    @TempDir
    Path cache;
    @TempDir
    Path downloads;

    private Recorded http;
    private EditorState editorState;
    private LevelSession level;
    private Scene scene;
    private SceneSerializer serializer;
    private MaterialLibrary materials;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new TerrainComponentSerializer());
        registry.register(new TerrainCollider3DComponentSerializer());
        registry.register(new ModelComponentSerializer());
        registry.register(new MeshPrimitiveComponentSerializer());
        serializer = new SceneSerializer(registry);
        editorState = new EditorState(serializer);
        scene = new Scene("Journey");
        editorState.setActiveScene(scene);
        level = new LevelSession(editorState, new TerrainSession(new TerrainLibrary(AssetSource.directory(project.toFile()))),
            project.toFile());
        materials = new MaterialLibrary(AssetSource.directory(project.toFile()));
        http = new Recorded();
        recordPolyHaven();
        recordAmbientCg();
    }

    private void recordPolyHaven() {
        http.answer(PolyHavenProvider.API + "/assets?t=models", bytes("""
            {"wooden_crate_02": {"name": "Wooden Crate 02", "type": 2, "authors": {"James Ray Cock": "All"},
              "categories": ["props", "containers"], "tags": ["wood", "box"], "download_count": 90000},
             "stone_fire_pit": {"name": "Stone Fire Pit", "type": 2, "authors": {"Sebastian Platen": "All"},
              "categories": ["structures"], "tags": ["fire"], "download_count": 24970}}
            """));
        String gltf = "{\"asset\": {\"version\": \"2.0\"}}";
        String bin = "buffer";
        String diffuse = "jpeg";
        http.answer("https://dl.polyhaven.org/2k/wooden_crate_02_2k.gltf", bytes(gltf));
        http.answer("https://dl.polyhaven.org/wooden_crate_02.bin", bytes(bin));
        http.answer("https://dl.polyhaven.org/2k/wooden_crate_02_diff_2k.jpg", bytes(diffuse));
        http.answer(PolyHavenProvider.API + "/files/wooden_crate_02", bytes("""
            {"gltf": {"2k": {"gltf": {"url": "https://dl.polyhaven.org/2k/wooden_crate_02_2k.gltf", "md5": "%s", "size": %d,
              "include": {"wooden_crate_02.bin": {"url": "https://dl.polyhaven.org/wooden_crate_02.bin", "md5": "%s", "size": %d},
                          "textures/wooden_crate_02_diff_2k.jpg": {"url": "https://dl.polyhaven.org/2k/wooden_crate_02_diff_2k.jpg",
                            "md5": "%s", "size": %d}}}},
                      "1k": {"gltf": {"url": "https://dl.polyhaven.org/1k/wooden_crate_02_1k.gltf", "md5": "x", "size": 1}}}}
            """.formatted(md5(gltf), gltf.length(), md5(bin), bin.length(), md5(diffuse), diffuse.length())));
    }

    private void recordAmbientCg() throws IOException {
        http.answer(AmbientCgProvider.API + "?type=material&sort=popular&limit=500&offset=0&include=title,tags", bytes("""
            {"totalResults": 1, "assets": [{"id": "Ground111", "title": "Ground 111", "tags": ["ground", "gravel"]}]}
            """));
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zip)) {
            for (String map : List.of("Color", "NormalGL", "Roughness", "AmbientOcclusion")) {
                out.putNextEntry(new ZipEntry("Ground111_2K-JPG_" + map + ".jpg"));
                out.write(bytes("map " + map));
                out.closeEntry();
            }
            out.putNextEntry(new ZipEntry("Ground111_2K-JPG.blend"));
            out.write(1);
            out.closeEntry();
        }
        String url = "https://ambientcg.com/get?file=Ground111_2K-JPG.zip";
        http.answer(url, zip.toByteArray());
        http.answer(AmbientCgProvider.API + "?id=Ground111&include=downloads", bytes("""
            {"assets": [{"id": "Ground111", "downloads": [
              {"attributes": "2K-JPG", "extension": "zip", "url": "%s", "size": %d}]}]}
            """.formatted(url, zip.size())));
    }

    private RemoteAssetInstaller.Result download(AssetProvider provider, RemoteAsset asset) throws IOException {
        List<DownloadOption> options = provider.options(asset);
        return RemoteAssetInstaller.install(provider, asset, AssetProvider.preferred(options), project.toFile(), TODAY, http,
            (done, total, file) -> { }, () -> false);
    }

    @Test
    void shouldImportFindDownloadPlaceApplySaveAndReopenThenUndoEveryStep() throws IOException {
        // A world: a level whose ground is raised, as a person would sculpt it
        level.createLevel(Surface.GRASS);
        Terrain terrain = level.findTerrain();
        for (int iz = 0; iz < terrain.getResolutionZ(); iz++) {
            for (int ix = 0; ix < terrain.getResolutionX(); ix++) {
                terrain.setHeight(ix, iz, HILL);
            }
        }
        String emptyWorld = serializer.toJson(scene);
        assertEquals(0, http.requests, "nothing goes online until a provider is used");

        // Kenney: a pack a person downloaded, imported whole with its CC0 licence
        File pack = downloads.resolve("kenney_nature-kit.zip").toFile();
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(pack.toPath()))) {
            out.putNextEntry(new ZipEntry("License.txt"));
            out.write(bytes("Creative Commons Zero, CC0 http://creativecommons.org/publicdomain/zero/1.0/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("Models/GLTF format/tree_oak.glb"));
            out.write(bytes("glb"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("Models/FBX format/tree_oak.fbx"));
            out.write(bytes("fbx"));
            out.closeEntry();
        }
        PackImporter.Result kenney = PackImporter.importPack(pack, KenneyPacks.ALL.get(1), project.toFile(), TODAY);
        assertEquals(List.of("Models/FBX format/tree_oak.fbx"), kenney.leftOut(), "never silently imported");

        // Poly Haven: search, preview data, 2K first, download with MD5 checks
        PolyHavenProvider polyHaven = new PolyHavenProvider(http, cache.toFile());
        RemoteAsset crate = polyHaven.catalog(AssetType.MODEL).search("crate").get(0);
        assertEquals("Wooden Crate 02", crate.name());
        assertTrue(crate.thumbnailUrl().endsWith("format=png"));
        RemoteAssetInstaller.Result model = download(polyHaven, crate);
        String gltf = model.mainModel().orElseThrow();

        // ambientCG: search, download the zip, keep what ForgeRoom reads, make the material
        AmbientCgProvider ambient = new AmbientCgProvider(http, cache.toFile());
        RemoteAsset ground = ambient.catalog(AssetType.TEXTURE).search("gravel").get(0);
        RemoteAssetInstaller.Result material = download(ambient, ground);
        assertEquals(List.of("Ground111_2K-JPG.blend"), material.leftOut());
        assertNotNull(materials.get(material.materialPath()), "the made material reads back: " + materials.getError(material.materialPath()));

        // Into the world: the model stands on the ground; the material goes on a box
        Entity placed = level.placeModelOnGround(gltf, 40f, 40f, 0f);
        assertEquals(HILL, placed.getTransform().getWorldPosition().y, TOLERANCE);
        CreateEntityCommand box = new CreateEntityCommand(editorState, "Box", new Vector3(-50f, HILL, 0f),
            List.<Component>of(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX)));
        assertTrue(editorState.executeCommand(box));
        assertEquals(MaterialAssignment.Result.APPLIED,
            MaterialAssignment.assign(editorState, materials, box.getCreatedEntity(), material.materialPath()));

        // Every downloaded file is recorded and matches what is on disk
        ThirdPartyManifest manifest = ThirdPartyManifest.read(project.toFile());
        for (String id : List.of("kenney-nature-kit", "polyhaven-wooden-crate-02", "ambientcg-ground111")) {
            ThirdPartyManifest.Entry entry = manifest.entry(id).orElseThrow(() -> new AssertionError("no manifest entry " + id));
            assertEquals("CC0-1.0", entry.licence());
            for (ThirdPartyManifest.FileRecord file : entry.files()) {
                assertEquals(file.sha256(), sha256(Files.readAllBytes(project.resolve(file.path()))), file.path());
            }
        }

        // Save and reopen
        Path saved = project.resolve("scenes/journey.json");
        Files.createDirectories(saved.getParent());
        Files.writeString(saved, serializer.toJson(scene));
        Scene reopened = serializer.fromJson(Files.readString(saved));
        Entity crateAgain = reopened.getAllEntities().stream().filter(e -> e.getId().equals(placed.getId())).findFirst().orElseThrow();
        assertEquals(gltf, crateAgain.getComponent(ModelComponent.class).getModelPath());
        assertEquals(HILL, crateAgain.getTransform().getWorldPosition().y, TOLERANCE);
        Entity boxAgain = reopened.getAllEntities().stream().filter(e -> e.getId().equals(box.getCreatedEntity().getId())).findFirst()
            .orElseThrow();
        assertEquals(material.materialPath(), boxAgain.getComponent(MeshPrimitiveComponent.class).getMaterialPath());

        // Existing behaviour intact: undo takes the world back to before the assets arrived, step by step
        assertTrue(editorState.undo(), "material");
        assertTrue(editorState.undo(), "box");
        assertTrue(editorState.undo(), "model");
        assertEquals(emptyWorld, serializer.toJson(scene));
        assertFalse(scene.getAllEntities().stream().anyMatch(e -> e.getComponent(ModelComponent.class) != null));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String md5(String text) {
        return hex("MD5", bytes(text));
    }

    private static String sha256(byte[] data) {
        return hex("SHA-256", data);
    }

    private static String hex(String algorithm, byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
