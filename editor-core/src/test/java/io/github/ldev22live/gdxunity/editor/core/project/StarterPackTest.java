package io.github.ldev22live.gdxunity.editor.core.project;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The curated CC0 starter pack (roadmap M2b.2): Kenney's tree, rock and house, and since M2b.6 a pine and a bush, recorded, and carried
 * into new projects with their licences.
 */
class StarterPackTest {
    private static final File SHIPPED = new File("../assets");
    private static final String TREE = "prefabs/level/CC0 Oak Tree.prefab";
    private static final String ROCK = "prefabs/level/CC0 Rock.prefab";
    private static final String PINE = "prefabs/level/CC0 Pine Tree.prefab";
    private static final String BUSH = "prefabs/level/CC0 Bush.prefab";
    private static final String HOUSE = "prefabs/buildings/CC0 House.prefab";
    private static final String COLORMAP = "thirdparty/kenney/building-kit/Models/GLB format/Textures/colormap.png";

    @TempDir
    Path temp;

    /** The model paths a prefab file names. */
    private static List<String> modelsOf(String prefab) throws IOException {
        JsonValue root = new JsonReader().parse(Files.readString(new File(SHIPPED, prefab).toPath()));
        List<String> models = new ArrayList<>();
        for (JsonValue entity = root.get("entities").child; entity != null; entity = entity.next) {
            for (JsonValue component = entity.get("components").child; component != null; component = component.next) {
                if ("ModelComponent".equals(component.getString("type"))) {
                    models.add(component.get("data").getString("model"));
                }
            }
        }
        return models;
    }

    @Test
    void shouldOfferTheCuratedTreeRockAndHouseWhoseEveryFileIsARecordedCc0Download() throws IOException {
        List<String> prefabs = StarterPalette.prefabs(SHIPPED);
        assertTrue(prefabs.containsAll(List.of(TREE, ROCK, PINE, BUSH, HOUSE)), prefabs.toString());

        ThirdPartyManifest manifest = ThirdPartyManifest.read(SHIPPED);
        Set<String> needed = new TreeSet<>();
        for (String prefab : List.of(TREE, ROCK, PINE, BUSH, HOUSE)) {
            List<String> models = modelsOf(prefab);
            assertFalse(models.isEmpty(), prefab + " places a model");
            needed.addAll(models);
        }
        needed.add(COLORMAP);
        assertTrue(StarterPalette.files(SHIPPED).containsAll(needed), "the palette carries its models and the house's colour map");
        for (String path : needed) {
            ThirdPartyManifest.Entry entry = manifest.find(path).orElseThrow(() -> new AssertionError(path + " is not recorded"));
            assertEquals("CC0-1.0", entry.licence(), path);
            assertTrue(entry.id().equals("kenney-nature-kit") || entry.id().equals("kenney-building-kit"), entry.id());
            ThirdPartyManifest.FileRecord record = entry.files().stream().filter(f -> f.path().equals(path)).findFirst().orElseThrow();
            assertEquals(record.sha256(), sha256(new File(SHIPPED, path)), "unchanged since import: " + path);
        }
        assertEquals(1, modelsOf(TREE).size());
        assertEquals(1, modelsOf(ROCK).size());
        assertEquals(1, modelsOf(PINE).size());
        assertEquals(1, modelsOf(BUSH).size());
        assertEquals(16, modelsOf(HOUSE).size(), "4 floor tiles, 8 walls, 4 roof corners");
    }

    @Test
    void shouldCopyTheModelsAndOnlyTheirLicenceRecordsIntoANewProject() throws IOException {
        File project = Files.createDirectories(temp.resolve("New Game")).toFile();
        TemplateProject.Result made = new TemplateProject.Result(project, "scenes/new.json", List.of());

        TemplateProject.Result withPalette = StarterPalette.addTo(SHIPPED, made, GameMode.MODE_3D);

        for (String prefab : List.of(TREE, ROCK, PINE, BUSH, HOUSE)) {
            assertTrue(new File(project, prefab).isFile(), prefab);
            for (String model : modelsOf(prefab)) {
                assertTrue(new File(project, model).isFile(), model);
            }
        }
        assertTrue(new File(project, COLORMAP).isFile(), "what the house's models name comes too");
        assertTrue(new File(project, "prefabs/buildings/CC0 House.png").isFile(), "and the palette's thumbnails (M2b.3)");
        assertTrue(new File(project, "prefabs/level/Oak Tree.png").isFile());
        assertTrue(withPalette.files().contains(ThirdPartyManifest.PATH));
        ThirdPartyManifest copied = ThirdPartyManifest.read(project);
        ThirdPartyManifest.Entry nature = copied.entry("kenney-nature-kit").orElseThrow();
        assertEquals(Set.copyOf(List.of(modelsOf(TREE).get(0), modelsOf(ROCK).get(0), modelsOf(PINE).get(0), modelsOf(BUSH).get(0))),
            Set.copyOf(nature.files().stream().map(ThirdPartyManifest.FileRecord::path).toList()), "only the files copied");
        assertEquals("https://kenney.nl/assets/nature-kit", nature.source());
        for (ThirdPartyManifest.Entry entry : copied.entries()) {
            for (ThirdPartyManifest.FileRecord file : entry.files()) {
                assertEquals(file.sha256(), sha256(new File(project, file.path())), file.path());
            }
        }
    }

    @Test
    void shouldGiveAProjectNoPaletteIn2d() throws IOException {
        File project = Files.createDirectories(temp.resolve("Flat")).toFile();

        StarterPalette.addTo(SHIPPED, new TemplateProject.Result(project, "scenes/new.json", List.of()), GameMode.MODE_2D);

        assertFalse(new File(project, ThirdPartyManifest.PATH).exists());
        assertFalse(new File(project, "thirdparty").exists());
    }

    @Test
    void shouldFollowTheOutsideFilesABinaryGltfNames() throws IOException {
        Path root = temp.resolve("glb");
        Files.createDirectories(root.resolve("models/Textures"));
        Files.writeString(root.resolve("models/Textures/colormap.png"), "png");
        Files.write(root.resolve("models/wall.glb"), glb("{\"asset\":{\"version\":\"2.0\"},\"images\":[{\"uri\":\"Textures/colormap.png\"}]}"));
        Files.writeString(root.resolve("wall.prefab"), "{\"model\": \"models/wall.glb\"}");

        TreeSet<String> found = TemplateProject.findDependencies(root, "wall.prefab");

        assertEquals(new TreeSet<>(List.of("wall.prefab", "models/wall.glb", "models/Textures/colormap.png")), found);
        Files.writeString(root.resolve("models/wall.glb"), "not a binary glTF");
        IOException e = assertThrows(IOException.class, () -> TemplateProject.findDependencies(root, "wall.prefab"));
        assertTrue(e.getMessage().contains("not a binary glTF"), e.getMessage());
    }

    @Test
    void shouldRecordOnlyCopiedFilesAndKeepTheTargetsOwnRecords() throws IOException {
        File source = Files.createDirectories(temp.resolve("source")).toFile();
        File target = Files.createDirectories(temp.resolve("target")).toFile();
        ThirdPartyManifest.empty()
            .with(entry("kenney-nature-kit", "a.glb", "b.glb"))
            .with(entry("polyhaven-x", "c.gltf"))
            .write(source);
        ThirdPartyManifest.empty().with(entry("ambientcg-y", "d.jpg")).write(target);

        assertTrue(ProjectLicences.carry(source, target, List.of("a.glb", "unrecorded.txt")));
        assertFalse(ProjectLicences.carry(source, target, List.of("a.glb")), "already recorded: nothing to write");

        ThirdPartyManifest result = ThirdPartyManifest.read(target);
        assertEquals(List.of("a.glb"), result.entry("kenney-nature-kit").orElseThrow().files().stream()
            .map(ThirdPartyManifest.FileRecord::path).toList());
        assertTrue(result.entry("polyhaven-x").isEmpty(), "nothing of it was copied");
        assertTrue(result.entry("ambientcg-y").isPresent(), "the target's own record stays");
        assertFalse(ProjectLicences.carry(target, temp.resolve("none").toFile(), List.of()), "nothing copied, nothing written");
    }

    private static ThirdPartyManifest.Entry entry(String id, String... paths) {
        List<ThirdPartyManifest.FileRecord> files = new ArrayList<>();
        for (String path : paths) {
            files.add(new ThirdPartyManifest.FileRecord(path, path, 1, "0".repeat(64)));
        }
        return new ThirdPartyManifest.Entry(id, id, "model", "https://example.org/" + id, "CC0-1.0", "https://example.org/licence",
            List.of("Someone"), "2026-11-20", "a test", "glb", "none", "imported", files);
    }

    /** A minimal binary glTF holding {@code json} as its JSON chunk. */
    private static byte[] glb(String json) throws IOException {
        byte[] chunk = json.getBytes(StandardCharsets.UTF_8);
        int padded = (chunk.length + 3) & ~3;
        ByteBuffer header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(0x46546C67).putInt(2).putInt(20 + padded).putInt(padded).putInt(0x4E4F534A);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header.array());
        out.write(chunk);
        for (int i = chunk.length; i < padded; i++) {
            out.write(' ');
        }
        return out.toByteArray();
    }

    private static String sha256(File file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath())));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
