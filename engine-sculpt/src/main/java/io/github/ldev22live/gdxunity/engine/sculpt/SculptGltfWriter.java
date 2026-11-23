package io.github.ldev22live.gdxunity.engine.sculpt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Writes a sculpt as a binary glTF 2.0 file ({@code .glb}) so it can be placed with a Model component, in the game
 * runtime and the web player (TD-038, 19-C). One node and one mesh, with positions and normals and a plain PBR material
 * of the sculpt's colour. The mesh is cut into runs of at most {@link MeshChunks#DEFAULT_MAX_VERTICES} vertices, one
 * glTF primitive each, because libGDX meshes take 16-bit indices. Needs no graphics context.
 */
public final class SculptGltfWriter {
    /**
     * Scale that makes a sculpt come back at the same size through a default Model component: positions are in scene
     * units and a Model component multiplies a glTF's metres by its import scale, which is 100 by default.
     */
    public static final float DEFAULT_IMPORT_SCALE = 100f;

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_VERSION = 2;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;
    private static final int COMPONENT_UNSIGNED_SHORT = 5123;
    private static final int COMPONENT_FLOAT = 5126;
    private static final int TARGET_ARRAY_BUFFER = 34962;
    private static final int TARGET_ELEMENT_ARRAY_BUFFER = 34963;
    private static final int BYTES_PER_VERTEX = MeshChunks.FLOATS_PER_VERTEX * Float.BYTES;
    private static final int NORMAL_OFFSET = 3 * Float.BYTES;
    /** Roughness of the exported material: a matt clay. */
    private static final float ROUGHNESS = 0.8f;

    private SculptGltfWriter() {}

    /**
     * @param exportScale positions are multiplied by it; {@code 1 / DEFAULT_IMPORT_SCALE} keeps the size under a default Model
     * @param color       red, green, blue and alpha of the material, each 0 to 1
     * @param name        name of the node and mesh
     * @return the bytes of the {@code .glb}
     * @throws IllegalArgumentException if the scale is not a positive finite number or the colour is not four numbers
     */
    public static byte[] write(SculptMesh mesh, float exportScale, float[] color, String name) {
        Objects.requireNonNull(mesh, "mesh cannot be null");
        Objects.requireNonNull(color, "color cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        if (!(exportScale > 0f) || !Float.isFinite(exportScale)) {
            throw new IllegalArgumentException("exportScale must be a finite number greater than 0: " + exportScale);
        }
        if (color.length != 4) {
            throw new IllegalArgumentException("color needs red, green, blue and alpha, got " + color.length + " values");
        }
        MeshChunks chunks = new MeshChunks(mesh);

        // Binary part: for each chunk its interleaved vertices, then its indices padded to 4 bytes
        int binaryLength = 0;
        int[] vertexOffsets = new int[chunks.getChunkCount()];
        int[] indexOffsets = new int[chunks.getChunkCount()];
        for (int c = 0; c < chunks.getChunkCount(); c++) {
            vertexOffsets[c] = binaryLength;
            binaryLength += chunks.getVertexCount(c) * BYTES_PER_VERTEX;
            indexOffsets[c] = binaryLength;
            binaryLength += pad4(chunks.getIndexCount(c) * Short.BYTES);
        }
        ByteBuffer binary = ByteBuffer.allocate(binaryLength).order(ByteOrder.LITTLE_ENDIAN);
        StringBuilder bufferViews = new StringBuilder();
        StringBuilder accessors = new StringBuilder();
        StringBuilder primitives = new StringBuilder();
        for (int c = 0; c < chunks.getChunkCount(); c++) {
            float[] vertices = new float[chunks.getVertexCount(c) * MeshChunks.FLOATS_PER_VERTEX];
            chunks.writeVertices(c, vertices);
            float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int v = 0; v < chunks.getVertexCount(c); v++) {
                for (int axis = 0; axis < 3; axis++) {
                    float position = vertices[v * MeshChunks.FLOATS_PER_VERTEX + axis] * exportScale;
                    vertices[v * MeshChunks.FLOATS_PER_VERTEX + axis] = position;
                    low[axis] = Math.min(low[axis], position);
                    high[axis] = Math.max(high[axis], position);
                }
            }
            binary.position(vertexOffsets[c]);
            for (float value : vertices) {
                binary.putFloat(value);
            }
            binary.position(indexOffsets[c]);
            for (short index : chunks.getIndices(c)) {
                binary.putShort(index);
            }
            int vertexView = c * 2;
            int indexView = c * 2 + 1;
            int positionAccessor = c * 3;
            if (c > 0) {
                bufferViews.append(',');
                accessors.append(',');
                primitives.append(',');
            }
            bufferViews.append(String.format(Locale.ROOT,
                "{\"buffer\":0,\"byteOffset\":%d,\"byteLength\":%d,\"byteStride\":%d,\"target\":%d},"
                    + "{\"buffer\":0,\"byteOffset\":%d,\"byteLength\":%d,\"target\":%d}",
                vertexOffsets[c], chunks.getVertexCount(c) * BYTES_PER_VERTEX, BYTES_PER_VERTEX, TARGET_ARRAY_BUFFER,
                indexOffsets[c], chunks.getIndexCount(c) * Short.BYTES, TARGET_ELEMENT_ARRAY_BUFFER));
            accessors.append(String.format(Locale.ROOT,
                "{\"bufferView\":%d,\"byteOffset\":0,\"componentType\":%d,\"count\":%d,\"type\":\"VEC3\",\"min\":[%s,%s,%s],\"max\":[%s,%s,%s]},"
                    + "{\"bufferView\":%d,\"byteOffset\":%d,\"componentType\":%d,\"count\":%d,\"type\":\"VEC3\"},"
                    + "{\"bufferView\":%d,\"componentType\":%d,\"count\":%d,\"type\":\"SCALAR\"}",
                vertexView, COMPONENT_FLOAT, chunks.getVertexCount(c), number(low[0]), number(low[1]), number(low[2]),
                number(high[0]), number(high[1]), number(high[2]),
                vertexView, NORMAL_OFFSET, COMPONENT_FLOAT, chunks.getVertexCount(c),
                indexView, COMPONENT_UNSIGNED_SHORT, chunks.getIndexCount(c)));
            primitives.append(String.format(Locale.ROOT,
                "{\"attributes\":{\"POSITION\":%d,\"NORMAL\":%d},\"indices\":%d,\"material\":0,\"mode\":4}",
                positionAccessor, positionAccessor + 1, positionAccessor + 2));
        }

        String json = String.format(Locale.ROOT,
            "{\"asset\":{\"version\":\"2.0\",\"generator\":\"Forgeroom sculpt export\"},"
                + "\"scene\":0,\"scenes\":[{\"nodes\":[0]}],\"nodes\":[{\"name\":%s,\"mesh\":0}],"
                + "\"meshes\":[{\"name\":%s,\"primitives\":[%s]}],"
                + "\"materials\":[{\"name\":\"Sculpt\",\"pbrMetallicRoughness\":{\"baseColorFactor\":[%s,%s,%s,%s],"
                + "\"metallicFactor\":0,\"roughnessFactor\":%s}%s}],"
                + "\"accessors\":[%s],\"bufferViews\":[%s],\"buffers\":[{\"byteLength\":%d}]}",
            quote(name), quote(name), primitives, number(color[0]), number(color[1]), number(color[2]), number(color[3]),
            number(ROUGHNESS), color[3] < 1f ? ",\"alphaMode\":\"BLEND\"" : "", accessors, bufferViews, binaryLength);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int jsonLength = pad4(jsonBytes.length);

        ByteBuffer glb = ByteBuffer.allocate(12 + 8 + jsonLength + 8 + binaryLength).order(ByteOrder.LITTLE_ENDIAN);
        glb.putInt(GLB_MAGIC).putInt(GLB_VERSION).putInt(glb.capacity());
        glb.putInt(jsonLength).putInt(CHUNK_JSON).put(jsonBytes);
        for (int i = jsonBytes.length; i < jsonLength; i++) {
            glb.put((byte) ' ');
        }
        glb.putInt(binaryLength).putInt(CHUNK_BIN).put(binary.array());
        return glb.array();
    }

    private static int pad4(int length) {
        return (length + 3) & ~3;
    }

    private static String number(float value) {
        return String.format(Locale.ROOT, "%s", Float.toString(value));
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') {
                out.append('\\');
            }
            out.append(c < 0x20 ? ' ' : c);
        }
        return out.append('"').toString();
    }
}
