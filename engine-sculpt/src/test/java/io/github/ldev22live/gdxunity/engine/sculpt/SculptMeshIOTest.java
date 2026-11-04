package io.github.ldev22live.gdxunity.engine.sculpt;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SculptMeshIOTest {
    private static byte[] write(SculptMesh mesh) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SculptMeshIO.write(mesh, out);
        return out.toByteArray();
    }

    private static String readError(byte[] data) {
        return assertThrows(IOException.class, () -> SculptMeshIO.read(new ByteArrayInputStream(data))).getMessage();
    }

    /** A file with the given header values and the given number of payload bytes. */
    private static byte[] file(int magic, int version, int vertices, int triangles, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(16 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(magic).putInt(version).putInt(vertices).putInt(triangles).put(payload);
        return buffer.array();
    }

    @Test
    void shouldRoundTripAMeshExactly() throws IOException {
        SculptMesh original = SculptTestSupport.sphere(6);
        original.getPositions()[7] = 1.2345678f;

        SculptMesh loaded = SculptMeshIO.read(new ByteArrayInputStream(write(original)));

        assertArrayEquals(original.getPositions(), loaded.getPositions(), 0f);
        assertArrayEquals(original.getTriangles(), loaded.getTriangles());
        assertEquals(original.getVertexCount(), loaded.getVertexCount());
    }

    @Test
    void shouldRebuildNormalsOnLoad() throws IOException {
        SculptMesh original = SculptTestSupport.sphere(4);

        SculptMesh loaded = SculptMeshIO.read(new ByteArrayInputStream(write(original)));

        assertArrayEquals(original.getNormals(), loaded.getNormals(), 1e-6f);
    }

    @Test
    void shouldRejectAFileThatIsNotASculpt() {
        assertTrue(readError(file(0x12345678, 1, 3, 1, new byte[48])).contains("Not a sculpt file"));
        assertTrue(readError("hello world, this is text".getBytes()).contains("Not a sculpt file"));
    }

    @Test
    void shouldRejectAnUnsupportedVersionAndNameIt() {
        String message = readError(file(SculptMeshIO.MAGIC, 7, 3, 1, new byte[48]));

        assertTrue(message.contains("version 7"), message);
    }

    @Test
    void shouldRejectTruncatedData() throws IOException {
        byte[] data = write(SculptTestSupport.sphere(3));

        assertTrue(readError(Arrays.copyOf(data, data.length - 5)).contains("truncated"));
        assertTrue(readError(Arrays.copyOf(data, 7)).contains("truncated"));
        assertTrue(readError(new byte[0]).contains("truncated"));
    }

    @Test
    void shouldRejectImpossibleCountsWithoutAllocatingForThem() {
        assertTrue(readError(file(SculptMeshIO.MAGIC, 1, 0, 1, new byte[0])).contains("vertex count"));
        assertTrue(readError(file(SculptMeshIO.MAGIC, 1, 3, -1, new byte[0])).contains("triangle count"));
        assertTrue(readError(file(SculptMeshIO.MAGIC, 1, Integer.MAX_VALUE, 1, new byte[0])).contains("vertex count"));
        assertTrue(readError(file(SculptMeshIO.MAGIC, 1, 3, SculptMesh.MAX_TRIANGLES + 1, new byte[0]))
            .contains("triangle count"));
    }

    @Test
    void shouldReportACorruptMeshAsAnIoError() {
        // One triangle that uses vertex 9 of 3
        ByteBuffer payload = ByteBuffer.allocate(4 * 9 + 4 * 3).order(ByteOrder.LITTLE_ENDIAN);
        payload.position(36);
        payload.putInt(0).putInt(1).putInt(9);

        String message = readError(file(SculptMeshIO.MAGIC, 1, 3, 1, payload.array()));

        assertTrue(message.contains("corrupt") && message.contains("vertex 9"), message);
    }
}
