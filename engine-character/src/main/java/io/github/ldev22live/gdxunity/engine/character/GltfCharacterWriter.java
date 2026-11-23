package io.github.ldev22live.gdxunity.engine.character;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Writes a {@link BuiltCharacter} as a glTF 2.0 file with its buffer embedded (TD-053): one skinned mesh, one node per bone (every
 * bone unrotated at rest, translated from its parent), inverse bind matrices, a flat skin material and the baked animations.
 * The result loads like any other model ({@code ModelComponent}, whose animation field names one of the baked clips).
 */
public final class GltfCharacterWriter {
    private static final int FLOAT = 5126;
    private static final int UNSIGNED_BYTE = 5121;
    private static final int UNSIGNED_INT = 5125;
    private static final int ARRAY_BUFFER = 34962;
    private static final int ELEMENT_ARRAY_BUFFER = 34963;

    private GltfCharacterWriter() {}

    /**
     * @param character  the body and skeleton
     * @param skinColor  red, green, blue of the skin as shown on screen (sRGB), 0 to 1
     * @param animations clips to include (may be empty)
     * @return the text of the {@code .gltf} file
     */
    public static String write(BuiltCharacter character, float[] skinColor, List<BakedAnimation> animations) {
        Objects.requireNonNull(character, "character cannot be null");
        Objects.requireNonNull(skinColor, "skinColor cannot be null");
        Objects.requireNonNull(animations, "animations cannot be null");
        int bones = character.bones().size();
        int vertices = character.vertexCount();
        Buffer buffer = new Buffer();

        int positionView = buffer.floats(character.positions(), ARRAY_BUFFER);
        int normalView = buffer.floats(character.normals(), ARRAY_BUFFER);
        int uvView = buffer.floats(character.uvs(), ARRAY_BUFFER);
        byte[] joints = new byte[vertices * 4];
        for (int i = 0; i < joints.length; i++) {
            joints[i] = (byte) character.joints()[i];
        }
        int jointView = buffer.bytes(joints, ARRAY_BUFFER);
        int weightView = buffer.floats(character.weights(), ARRAY_BUFFER);
        int indexView = buffer.ints(character.triangles(), ELEMENT_ARRAY_BUFFER);
        float[] inverseBind = new float[bones * 16];
        for (int b = 0; b < bones; b++) {
            float[] head = character.jointHead(b);
            float[] m = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, -head[0], -head[1], -head[2], 1};
            System.arraycopy(m, 0, inverseBind, b * 16, 16);
        }
        int inverseBindView = buffer.floats(inverseBind, 0);

        List<Accessor> accessors = new ArrayList<>();
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < character.positions().length; i += 3) {
            for (int k = 0; k < 3; k++) {
                min[k] = Math.min(min[k], character.positions()[i + k]);
                max[k] = Math.max(max[k], character.positions()[i + k]);
            }
        }
        accessors.add(new Accessor(positionView, FLOAT, vertices, "VEC3", min, max));          // 0 POSITION
        accessors.add(new Accessor(normalView, FLOAT, vertices, "VEC3", null, null));          // 1 NORMAL
        accessors.add(new Accessor(uvView, FLOAT, vertices, "VEC2", null, null));              // 2 TEXCOORD_0
        accessors.add(new Accessor(jointView, UNSIGNED_BYTE, vertices, "VEC4", null, null));   // 3 JOINTS_0
        accessors.add(new Accessor(weightView, FLOAT, vertices, "VEC4", null, null));          // 4 WEIGHTS_0
        accessors.add(new Accessor(indexView, UNSIGNED_INT, character.triangles().length, "SCALAR", null, null)); // 5 indices
        accessors.add(new Accessor(inverseBindView, FLOAT, bones, "MAT4", null, null));        // 6 inverse bind

        StringBuilder animationJson = new StringBuilder();
        for (int a = 0; a < animations.size(); a++) {
            BakedAnimation animation = animations.get(a);
            int timeView = buffer.floats(animation.times(), 0);
            int timeAccessor = accessors.size();
            accessors.add(new Accessor(timeView, FLOAT, animation.times().length, "SCALAR",
                new float[] {animation.times()[0]}, new float[] {animation.times()[animation.times().length - 1]}));
            StringBuilder samplers = new StringBuilder();
            StringBuilder channels = new StringBuilder();
            int sampler = 0;
            for (BakedAnimation.Channel channel : animation.channels()) {
                for (int kind = 0; kind < 2; kind++) {
                    float[] values = kind == 0 ? channel.translations() : channel.rotations();
                    if (values == null) {
                        continue;
                    }
                    int accessor = accessors.size();
                    accessors.add(new Accessor(buffer.floats(values, 0), FLOAT, animation.times().length, kind == 0 ? "VEC3" : "VEC4",
                        null, null));
                    if (sampler > 0) {
                        samplers.append(',');
                        channels.append(',');
                    }
                    samplers.append("{\"input\":").append(timeAccessor).append(",\"output\":").append(accessor)
                        .append(",\"interpolation\":\"LINEAR\"}");
                    channels.append("{\"sampler\":").append(sampler).append(",\"target\":{\"node\":").append(channel.bone())
                        .append(",\"path\":\"").append(kind == 0 ? "translation" : "rotation").append("\"}}");
                    sampler++;
                }
            }
            if (sampler == 0) {
                continue; // a clip that moves nothing the rig has would be an empty animation, which glTF does not allow
            }
            if (animationJson.length() > 0) {
                animationJson.append(',');
            }
            animationJson.append("{\"name\":").append(quote(animation.name())).append(",\"samplers\":[").append(samplers)
                .append("],\"channels\":[").append(channels).append("]}");
        }

        StringBuilder json = new StringBuilder(1 << 16);
        json.append("{\"asset\":{\"version\":\"2.0\",\"generator\":\"Forgeroom character creator (TD-053), body data from MakeHuman, CC0\"},");
        json.append("\"scene\":0,\"scenes\":[{\"nodes\":[0,").append(bones).append("]}],");
        json.append("\"nodes\":[");
        for (int b = 0; b < bones; b++) {
            float[] t = HumanoidTemplates.restTranslation(character, b);
            json.append(b > 0 ? "," : "").append("{\"name\":").append(quote(character.bones().get(b).name()))
                .append(",\"translation\":[").append(num(t[0])).append(',').append(num(t[1])).append(',').append(num(t[2])).append(']');
            List<Integer> children = new ArrayList<>();
            for (int c = 0; c < bones; c++) {
                if (character.bones().get(c).parent() == b) {
                    children.add(c);
                }
            }
            if (!children.isEmpty()) {
                json.append(",\"children\":").append(children);
            }
            json.append('}');
        }
        json.append(",{\"name\":\"Body\",\"mesh\":0,\"skin\":0}],");
        json.append("\"skins\":[{\"skeleton\":0,\"inverseBindMatrices\":6,\"joints\":[");
        for (int b = 0; b < bones; b++) {
            json.append(b > 0 ? "," : "").append(b);
        }
        json.append("]}],");
        json.append("\"materials\":[{\"name\":\"Skin\",\"pbrMetallicRoughness\":{\"baseColorFactor\":[").append(num(toLinear(skinColor[0]))).append(',')
            .append(num(toLinear(skinColor[1]))).append(',').append(num(toLinear(skinColor[2]))).append(",1],\"metallicFactor\":0,\"roughnessFactor\":0.8}}],");
        json.append("\"meshes\":[{\"name\":\"Body\",\"primitives\":[{\"attributes\":{\"POSITION\":0,\"NORMAL\":1,\"TEXCOORD_0\":2,")
            .append("\"JOINTS_0\":3,\"WEIGHTS_0\":4},\"indices\":5,\"material\":0}]}],");
        if (animationJson.length() > 0) {
            json.append("\"animations\":[").append(animationJson).append("],");
        }
        json.append("\"accessors\":[");
        for (int i = 0; i < accessors.size(); i++) {
            json.append(i > 0 ? "," : "").append(accessors.get(i).toJson());
        }
        json.append("],\"bufferViews\":[").append(buffer.viewsJson()).append("],");
        json.append("\"buffers\":[{\"byteLength\":").append(buffer.size()).append(",\"uri\":\"data:application/octet-stream;base64,")
            .append(Base64.getEncoder().encodeToString(buffer.toByteArray())).append("\"}]}");
        return json.toString();
    }

    /** The skin color is chosen as the screen shows it (sRGB); glTF material colors are linear. */
    static float toLinear(float srgb) {
        return srgb <= 0.04045f ? srgb / 12.92f : (float) Math.pow((srgb + 0.055) / 1.055, 2.4);
    }

    private static String num(float value) {
        return String.format(Locale.ROOT, "%.7g", value);
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private record Accessor(int view, int componentType, int count, String type, float[] min, float[] max) {
        String toJson() {
            StringBuilder s = new StringBuilder("{\"bufferView\":").append(view).append(",\"componentType\":").append(componentType)
                .append(",\"count\":").append(count).append(",\"type\":\"").append(type).append('"');
            if (min != null) {
                s.append(",\"min\":").append(array(min)).append(",\"max\":").append(array(max));
            }
            return s.append('}').toString();
        }

        private static String array(float[] values) {
            StringBuilder s = new StringBuilder("[");
            for (int i = 0; i < values.length; i++) {
                s.append(i > 0 ? "," : "").append(num(values[i]));
            }
            return s.append(']').toString();
        }
    }

    /** The growing binary buffer and the views into it, each starting on a 4 byte boundary. */
    private static final class Buffer {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final List<int[]> views = new ArrayList<>();

        int floats(float[] values, int target) {
            ByteBuffer block = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (float value : values) {
                block.putFloat(value);
            }
            return add(block.array(), target);
        }

        int ints(int[] values, int target) {
            ByteBuffer block = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int value : values) {
                block.putInt(value);
            }
            return add(block.array(), target);
        }

        int bytes(byte[] values, int target) {
            return add(values, target);
        }

        private int add(byte[] data, int target) {
            while (bytes.size() % 4 != 0) {
                bytes.write(0);
            }
            views.add(new int[] {bytes.size(), data.length, target});
            bytes.write(data, 0, data.length);
            return views.size() - 1;
        }

        int size() {
            return bytes.size();
        }

        byte[] toByteArray() {
            return bytes.toByteArray();
        }

        String viewsJson() {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < views.size(); i++) {
                int[] v = views.get(i);
                s.append(i > 0 ? "," : "").append("{\"buffer\":0,\"byteOffset\":").append(v[0]).append(",\"byteLength\":").append(v[1]);
                if (v[2] != 0) {
                    s.append(",\"target\":").append(v[2]);
                }
                s.append('}');
            }
            return s.toString();
        }
    }
}
