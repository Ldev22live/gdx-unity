package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetProvenance.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EditableMeshIOTest {
    @TempDir Path temporary;

    @Test
    void shouldRoundTripKenneyBuildingWithProvenance() throws IOException {
        Path assets = Path.of("..", "assets");
        Path wall = Path.of("thirdparty", "kenney", "building-kit", "Models", "GLB format", "wall.glb");
        byte[] original = Files.readAllBytes(assets.resolve(wall));

        EditableMesh mesh = StaticMeshImport.read(assets, wall);
        Path destination = temporary.resolve("wall.mesh");
        EditableMeshIO.write(destination, mesh);

        assertEquals(mesh, EditableMeshIO.read(destination));
        assertEquals(Kind.THIRD_PARTY, mesh.provenance().kind());
        assertEquals("CC0-1.0", mesh.provenance().licence());
        assertEquals(mesh.provenance().sha256(), mesh.sourceSha256());
        int jsonLength = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
        int binaryStart = 28 + jsonLength;
        String uri = mesh.model().get("buffers").get(0).getString("uri");
        assertArrayEquals(Arrays.copyOfRange(original, binaryStart, original.length),
            Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1)));
        assertArrayEquals(original, Files.readAllBytes(assets.resolve(wall)));
    }

    @Test
    void shouldPreserveSharedMeshTransformsAndMaterialSlotsWhenReopened() throws IOException {
        Path source = makeModel("");
        EditableMesh mesh = StaticMeshImport.read(temporary, source);
        Path target = temporary.resolve("shared.mesh");
        EditableMeshIO.write(target, mesh);
        Files.delete(temporary.resolve("positions.bin"));

        JsonValue model = EditableMeshIO.read(target).model();
        assertEquals(1, model.get("nodes").get(0).get("children").getInt(0));
        assertArrayEquals(new float[]{1, 2, 3}, model.get("nodes").get(1).get("translation").asFloatArray());
        assertEquals(0, model.get("nodes").get(1).getInt("mesh"));
        assertEquals(0, model.get("nodes").get(2).getInt("mesh"));
        assertEquals(1, model.get("meshes").get(0).get("primitives").get(1).getInt("material"));
        assertEquals("Paint", model.get("materials").get(1).getString("name"));
    }

    @Test
    void shouldRejectSkinnedSourceAndMissingResources() throws IOException {
        Path source = makeModel(",\"skins\":[{\"joints\":[0]}]");
        assertTrue(assertThrows(IOException.class, () -> StaticMeshImport.read(temporary, source))
            .getMessage().contains("skins"));

        makeModel("");
        Files.delete(temporary.resolve("positions.bin"));
        assertTrue(assertThrows(IOException.class, () -> StaticMeshImport.read(temporary, source))
            .getMessage().contains("positions.bin"));
        assertFalse(Files.exists(temporary.resolve("shared.mesh")));
    }

    @Test
    void shouldRejectTruncatedGeometry() throws IOException {
        Path source = makeModel("");
        Files.write(temporary.resolve("positions.bin"), new byte[35]);

        assertTrue(assertThrows(IOException.class, () -> StaticMeshImport.read(temporary, source))
            .getMessage().contains("buffer length"));
    }

    @Test
    void shouldRejectUnsupportedDocumentVersion() throws IOException {
        Path file = temporary.resolve("future.mesh");
        Files.writeString(file, "{\"version\":2}");

        assertTrue(assertThrows(IOException.class, () -> EditableMeshIO.read(file))
            .getMessage().contains("Unsupported mesh version"));
    }

    private Path makeModel(String extra) throws IOException {
        ByteBuffer vertices = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float coordinate : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) {
            vertices.putFloat(coordinate);
        }
        Files.write(temporary.resolve("positions.bin"), vertices.array());
        Files.writeString(temporary.resolve("shared.gltf"), """
            {
              "asset":{"version":"2.0"},
              "buffers":[{"byteLength":36,"uri":"positions.bin"}],
              "bufferViews":[{"buffer":0,"byteLength":36}],
              "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"}],
              "materials":[{"name":"Timber"},{"name":"Paint"}],
              "meshes":[{"primitives":[{"attributes":{"POSITION":0},"material":0},
                                       {"attributes":{"POSITION":0},"material":1}]}],
              "nodes":[{"children":[1,2]},{"mesh":0,"translation":[1,2,3]},{"mesh":0}],
              "scenes":[{"nodes":[0]}],"scene":0%s
            }
            """.formatted(extra));
        return Path.of("shared.gltf");
    }
}