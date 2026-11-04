package io.github.ldev22live.gdxunity.engine.sculpt;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Reads and writes the {@code .sculpt} file: the exact positions and triangles of a sculpt, so work in progress is
 * saved without loss. (A sculpt is exported to glTF to be used in a scene; this is the editing format.)
 *
 * <p>Layout, little-endian: the magic {@code GSCM}, version 1, vertex count, triangle count, then
 * {@code 3 * vertexCount} floats and {@code 3 * triangleCount} ints. Normals and adjacency are rebuilt on load.
 */
public final class SculptMeshIO {
    /** File extension of a sculpt, without the dot. */
    public static final String EXTENSION = "sculpt";
    /** Identifies the file type: "GSCM" read as little-endian bytes. */
    static final int MAGIC = 0x4D435347;
    /** Current file format version. */
    static final int VERSION = 1;
    private static final int HEADER_BYTES = 16;

    private SculptMeshIO() {
    }

    /** Writes a mesh. The caller closes the stream. */
    public static void write(SculptMesh mesh, OutputStream out) throws IOException {
        Objects.requireNonNull(mesh, "mesh cannot be null");
        Objects.requireNonNull(out, "out cannot be null");
        float[] positions = mesh.getPositions();
        int[] triangles = mesh.getTriangles();
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + 4 * positions.length + 4 * triangles.length)
            .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(MAGIC).putInt(VERSION).putInt(mesh.getVertexCount()).putInt(mesh.getTriangleCount());
        buffer.asFloatBuffer().put(positions);
        buffer.position(buffer.position() + 4 * positions.length);
        buffer.asIntBuffer().put(triangles);
        out.write(buffer.array());
    }

    /**
     * Reads a mesh. The caller closes the stream.
     *
     * @throws IOException if the data is not a sculpt file, has an unsupported version, is truncated, or describes an
     *                     invalid mesh
     */
    public static SculptMesh read(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in cannot be null");
        DataInputStream data = new DataInputStream(in);
        byte[] headerBytes = new byte[HEADER_BYTES];
        readFully(data, headerBytes);
        ByteBuffer header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        if (header.getInt() != MAGIC) {
            throw new IOException("Not a sculpt file (wrong file signature)");
        }
        int version = header.getInt();
        if (version != VERSION) {
            throw new IOException("Unsupported sculpt file version " + version + "; this editor reads version "
                + VERSION);
        }
        int vertexCount = header.getInt();
        int triangleCount = header.getInt();
        if (vertexCount < 1 || vertexCount > SculptMesh.MAX_TRIANGLES) {
            throw new IOException("The sculpt file has an invalid vertex count: " + vertexCount);
        }
        if (triangleCount < 1 || triangleCount > SculptMesh.MAX_TRIANGLES) {
            throw new IOException("The sculpt file has an invalid triangle count: " + triangleCount);
        }
        byte[] body = new byte[4 * (3 * vertexCount + 3 * triangleCount)];
        readFully(data, body);
        ByteBuffer buffer = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        float[] positions = new float[3 * vertexCount];
        buffer.asFloatBuffer().get(positions);
        buffer.position(4 * positions.length);
        int[] triangles = new int[3 * triangleCount];
        buffer.asIntBuffer().get(triangles);
        try {
            return new SculptMesh(positions, triangles);
        } catch (IllegalArgumentException e) {
            throw new IOException("The sculpt file is corrupt: " + e.getMessage(), e);
        }
    }

    private static void readFully(DataInputStream data, byte[] target) throws IOException {
        try {
            data.readFully(target);
        } catch (EOFException e) {
            throw new IOException("The sculpt file is truncated", e);
        }
    }
}
