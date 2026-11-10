package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code .glb} a sculpt is exported as (TD-038, 19-C): a valid container whose data is the sculpt's own. */
class SculptGltfWriterTest {

    private static final float[] CLAY = {0.8f, 0.7f, 0.6f, 1f};

    /** A parsed {@code .glb}: its JSON and its binary chunk. */
    private record Glb(JsonValue json, ByteBuffer binary) {}

    private static Glb parse(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x46546C67, buffer.getInt(), "magic 'glTF'");
        assertEquals(2, buffer.getInt(), "version");
        assertEquals(bytes.length, buffer.getInt(), "total length");
        int jsonLength = buffer.getInt();
        assertEquals(0x4E4F534A, buffer.getInt(), "first chunk is JSON");
        assertEquals(0, jsonLength % 4, "JSON chunk is 4-byte aligned");
        byte[] jsonBytes = new byte[jsonLength];
        buffer.get(jsonBytes);
        int binaryLength = buffer.getInt();
        assertEquals(0x004E4942, buffer.getInt(), "second chunk is BIN");
        assertEquals(0, binaryLength % 4, "BIN chunk is 4-byte aligned");
        ByteBuffer binary = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(binaryLength, binary.remaining());
        return new Glb(new JsonReader().parse(new String(jsonBytes, StandardCharsets.UTF_8)), binary);
    }

    @Test
    void shouldWriteAValidContainerWithOnePrimitiveForASmallSculpt() {
        SculptMesh mesh = SculptMeshFactory.cubeSphere(50f, 8);

        Glb glb = parse(SculptGltfWriter.write(mesh, 0.01f, CLAY, "Rock"));

        assertEquals("2.0", glb.json().get("asset").getString("version"));
        assertEquals("Rock", glb.json().get("nodes").get(0).getString("name"));
        JsonValue primitives = glb.json().get("meshes").get(0).get("primitives");
        assertEquals(1, primitives.size);
        JsonValue positions = glb.json().get("accessors").get(primitives.get(0).get("attributes").getInt("POSITION"));
        assertEquals(mesh.getVertexCount(), positions.getInt("count"));
        JsonValue indices = glb.json().get("accessors").get(primitives.get(0).getInt("indices"));
        assertEquals(mesh.getTriangleCount() * 3, indices.getInt("count"));
        assertEquals(0.8f, glb.json().get("materials").get(0).get("pbrMetallicRoughness").get("baseColorFactor").getFloat(0), 1e-6f);
    }

    @Test
    void shouldScalePositionsAndKeepTheBoundsAccurate() {
        SculptMesh mesh = SculptMeshFactory.cubeSphere(50f, 8);

        Glb glb = parse(SculptGltfWriter.write(mesh, 0.01f, CLAY, "Rock"));

        JsonValue positions = glb.json().get("accessors").get(0);
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(-0.5f, positions.get("min").getFloat(axis), 1e-4f);
            assertEquals(0.5f, positions.get("max").getFloat(axis), 1e-4f);
        }
        JsonValue view = glb.json().get("bufferViews").get(positions.getInt("bufferView"));
        assertEquals(24, view.getInt("byteStride"));
        float x = glb.binary().getFloat(view.getInt("byteOffset"));
        assertEquals(mesh.getPositions()[0] * 0.01f, x, 1e-6f);
    }

    @Test
    void shouldWriteTheSameTrianglesTheSculptHasInChunksOf16BitIndices() {
        // 6 * 100 * 100 + 2 = 60,002 vertices: one more chunk than fits 60,000
        SculptMesh mesh = SculptMeshFactory.cubeSphere(50f, 100);

        Glb glb = parse(SculptGltfWriter.write(mesh, 0.01f, CLAY, "Big"));

        JsonValue primitives = glb.json().get("meshes").get(0).get("primitives");
        assertTrue(primitives.size >= 2, "split into chunks: " + primitives.size);
        int triangles = 0;
        for (JsonValue primitive : primitives) {
            JsonValue accessor = glb.json().get("accessors").get(primitive.getInt("indices"));
            assertEquals(5123, accessor.getInt("componentType"), "unsigned short");
            int vertexCount = glb.json().get("accessors").get(primitive.get("attributes").getInt("POSITION")).getInt("count");
            assertTrue(vertexCount <= MeshChunks.DEFAULT_MAX_VERTICES);
            JsonValue view = glb.json().get("bufferViews").get(accessor.getInt("bufferView"));
            for (int i = 0; i < accessor.getInt("count"); i++) {
                int index = glb.binary().getShort(view.getInt("byteOffset") + i * 2) & 0xFFFF;
                assertTrue(index < vertexCount, "index " + index + " inside its chunk");
            }
            triangles += accessor.getInt("count") / 3;
        }
        assertEquals(mesh.getTriangleCount(), triangles);
    }

    @Test
    void shouldMakeAnAlphaMaterialBlendAndRejectBadArguments() {
        SculptMesh mesh = SculptMeshFactory.cubeSphere(1f, 2);

        Glb glb = parse(SculptGltfWriter.write(mesh, 1f, new float[] {1f, 1f, 1f, 0.5f}, "Ghost \"1\""));

        assertEquals("BLEND", glb.json().get("materials").get(0).getString("alphaMode"));
        assertEquals("Ghost \"1\"", glb.json().get("nodes").get(0).getString("name"));
        assertThrows(IllegalArgumentException.class, () -> SculptGltfWriter.write(mesh, 0f, CLAY, "x"));
        assertThrows(IllegalArgumentException.class, () -> SculptGltfWriter.write(mesh, Float.NaN, CLAY, "x"));
        assertThrows(IllegalArgumentException.class, () -> SculptGltfWriter.write(mesh, 1f, new float[] {1f}, "x"));
    }
}
