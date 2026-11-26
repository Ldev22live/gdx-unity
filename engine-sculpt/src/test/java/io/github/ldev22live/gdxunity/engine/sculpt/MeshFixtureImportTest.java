package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class MeshFixtureImportTest {
    private static final Path ASSETS = Path.of("../assets");
    private static final Path DEMO = Path.of("../fixtures/mesh-sources");
    private static final Path WALL = Path.of("thirdparty/kenney/building-kit/Models/GLB format/wall.glb");
    private static final Path DOOR = Path.of("models/thirdparty/polyhaven/rollershutter_door/rollershutter_door_1k.gltf");
    private static final Path BLENDER = Path.of("models/thirdparty/blender/cube-diorama-wall.glb");

    @TempDir Path temp;

    @Test void shouldDeriveThreeVerifiedFixturesWithoutModifyingTheirSources() throws IOException {
        verify(ASSETS, WALL, "kenney-wall", 2);
        verify(ASSETS, DOOR, "polyhaven-door", 6);
        verify(DEMO, BLENDER, "blender-wall", 1);
    }

    private void verify(Path root, Path source, String name, int inputCount) throws IOException {
        Path output = temp.resolve(name);
        MeshFixtureImport.write(root, source, output);
        var mesh = EditableMeshIO.read(output.resolve("model.mesh"));
        assertEquals(StaticMeshImport.read(root, source), mesh);
        assertEquals(mesh.modelJson(), Files.readString(output.resolve("model.gltf")));
        var manifest = json(output.resolve("manifest.json"));
        assertEquals(inputCount, manifest.require("inputs").size);
        for (var input : manifest.require("inputs")) {
            assertEquals("CC0-1.0", input.getString("licence"));
            assertEquals(hash(root.resolve(input.getString("path"))), input.getString("sha256"));
        }
        Path shipped = DEMO.resolve("derived").resolve(name);
        assertEquals(Files.readString(output.resolve("manifest.json")), Files.readString(shipped.resolve("manifest.json")));
        for (var derived : manifest.require("outputs")) {
            assertEquals(hash(output.resolve(derived.name)), derived.asString());
            assertEquals(hash(shipped.resolve(derived.name)), derived.asString());
        }
        assertThrows(IOException.class, () -> MeshFixtureImport.write(root, source, output));
    }

    @Test void shouldLinkTheConvertedWallToTheOriginalBlendAndDownloadedArchive() throws IOException {
        var record = json(DEMO.resolve("licenses/manifest.json")).require("assets").get(0).require("originalArchive");
        Path archive = Path.of("..").resolve(record.getString("repositoryPath"));
        assertEquals(hash(archive), record.getString("sha256"));
        try (var zip = new ZipFile(archive.toFile()); var input = zip.getInputStream(zip.getEntry(record.getString("member")))) {
            assertEquals(record.getString("memberSha256"), StaticMeshImport.checksum(input.readAllBytes()));
        }
        var model = StaticMeshImport.read(DEMO, BLENDER).model();
        var extras = model.require("nodes").get(0).require("extras");
        assertEquals(record.getString("memberSha256"), extras.getString("sourceSha256"));
        assertEquals("Wall1", extras.getString("sourceObject"));
        assertTrue(extras.getBoolean("geometryOnly"));
        assertFalse(model.has("materials"));
    }

    @Test void shouldRejectAnAlteredTextureBeforePublishingTheDerivedDirectory() throws IOException {
        Path copy = temp.resolve("assets");
        Path texture = WALL.resolveSibling("Textures/colormap.png");
        for (Path path : new Path[]{WALL, texture, Path.of("licenses/manifest.json")}) {
            Files.createDirectories(copy.resolve(path).getParent());
            Files.copy(ASSETS.resolve(path), copy.resolve(path));
        }
        Files.writeString(copy.resolve(texture), "different content");
        Path output = temp.resolve("new-model");
        var failure = assertThrows(IOException.class, () -> MeshFixtureImport.write(copy, WALL, output));
        assertTrue(failure.getMessage().contains("colormap.png"), failure.getMessage());
        assertFalse(Files.exists(output));
    }

    @Test void shouldExplainHowToConvertBlenderSourcesWithoutOpeningThemAsGltf() throws IOException {
        Path source = temp.resolve("reference.blend");
        Files.writeString(source, "BLENDER");
        var failure = assertThrows(IOException.class, () -> StaticMeshImport.read(temp, source.getFileName()));
        assertTrue(failure.getMessage().contains("tools/blend_to_static_glb.py"));
        assertEquals("BLENDER", Files.readString(source));
    }

    private static JsonValue json(Path file) throws IOException { return new JsonReader().parse(Files.readString(file)); }
    private static String hash(Path file) throws IOException { return StaticMeshImport.checksum(Files.readAllBytes(file)); }
}
