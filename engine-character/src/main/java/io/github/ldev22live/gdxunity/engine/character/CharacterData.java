package io.github.ldev22live.gdxunity.engine.character;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The data a character is built from (TD-053): a base body mesh, morph targets, the sliders that use them, a game rig and skin
 * weights. All of it is read from {@code assets/characters/makehuman}, written there by {@code tools/convert_makehuman_assets.py}
 * from MakeHuman's CC0 data; MakeHuman itself does not need to be installed. Loading is headless (no GL) and the result is
 * immutable, so one instance serves any number of characters.
 */
public final class CharacterData {
    /** Folder under the asset root with the converted files. */
    public static final String FOLDER = "characters/makehuman";
    /** Meters per MakeHuman unit (the base body is about 17 units tall). */
    public static final float METERS_PER_UNIT = 0.1f;
    /** Most bones that influence one vertex. */
    public static final int MAX_INFLUENCES = 4;

    private static final int VERSION = 1;

    /** One morph target: vertex numbers and the offset of each (x, y, z, in MakeHuman units). */
    public record Target(String name, int[] vertices, float[] offsets) {}

    /** A bone of the rig; parents come before children. {@code head} and {@code tail} are base vertices whose mean is the joint. */
    public record Bone(String name, int parent, int[] head, int[] tail) {}

    private final float[] basePositions;
    private final int[] renderSource;
    private final float[] renderUvs;
    private final int[] triangles;
    private final Map<String, Target> targets;
    private final List<SliderDef> sliders;
    private final List<Bone> bones;
    private final int[][] weightBones;
    private final float[][] weightValues;

    private CharacterData(float[] basePositions, int[] renderSource, float[] renderUvs, int[] triangles, Map<String, Target> targets,
                          List<SliderDef> sliders, List<Bone> bones, int[][] weightBones, float[][] weightValues) {
        this.basePositions = basePositions;
        this.renderSource = renderSource;
        this.renderUvs = renderUvs;
        this.triangles = triangles;
        this.targets = targets;
        this.sliders = sliders;
        this.bones = bones;
        this.weightBones = weightBones;
        this.weightValues = weightValues;
    }

    /**
     * Reads the converted files from {@code assetRoot/}{@value #FOLDER}.
     *
     * @throws IOException when a file is missing or is not in the format the converter writes
     */
    public static CharacterData load(File assetRoot) throws IOException {
        Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        File folder = new File(assetRoot, FOLDER);
        float[] positions;
        int[] source;
        float[] uvs;
        int[] triangles;
        try (DataInputStream in = open(new File(folder, "mesh.bin"), "FGCM")) {
            int baseCount = leInt(in);
            positions = readFloats(in, baseCount * 3);
            int renderCount = leInt(in);
            source = new int[renderCount];
            uvs = new float[renderCount * 2];
            ByteBuffer block = readBlock(in, renderCount * 12);
            for (int i = 0; i < renderCount; i++) {
                source[i] = block.getInt();
                uvs[i * 2] = block.getFloat();
                uvs[i * 2 + 1] = block.getFloat();
            }
            int triangleCount = leInt(in);
            triangles = readInts(in, triangleCount * 3);
        }
        Map<String, Target> targets = new LinkedHashMap<>();
        try (DataInputStream in = open(new File(folder, "targets.bin"), "FGCT")) {
            int count = leInt(in);
            for (int t = 0; t < count; t++) {
                byte[] name = new byte[leU16(in)];
                in.readFully(name);
                int entries = leInt(in);
                int[] vertices = new int[entries];
                ByteBuffer ids = readBlock(in, entries * 2);
                for (int i = 0; i < entries; i++) {
                    vertices[i] = ids.getShort() & 0xFFFF;
                }
                float[] offsets = new float[entries * 3];
                ByteBuffer halves = readBlock(in, entries * 6);
                for (int i = 0; i < offsets.length; i++) {
                    offsets[i] = Float.float16ToFloat(halves.getShort());
                }
                String targetName = new String(name, StandardCharsets.UTF_8);
                targets.put(targetName, new Target(targetName, vertices, offsets));
            }
        }
        List<Bone> bones = readBones(new File(folder, "rig.json"));
        int[][] weightBones;
        float[][] weightValues;
        try (DataInputStream in = open(new File(folder, "weights.bin"), "FGCW")) {
            int baseCount = leInt(in);
            if (baseCount * 3 != positions.length) {
                throw new IOException("weights.bin is for " + baseCount + " vertices, mesh.bin has " + positions.length / 3);
            }
            weightBones = new int[baseCount][];
            weightValues = new float[baseCount][];
            for (int v = 0; v < baseCount; v++) {
                int n = in.readUnsignedByte();
                weightBones[v] = new int[n];
                weightValues[v] = new float[n];
                for (int i = 0; i < n; i++) {
                    weightBones[v][i] = in.readUnsignedByte();
                    weightValues[v][i] = Float.intBitsToFloat(leInt(in));
                }
            }
        }
        List<SliderDef> sliders = readSliders(new File(folder, "sliders.json"), targets);
        return new CharacterData(positions, source, uvs, triangles, Collections.unmodifiableMap(targets),
            Collections.unmodifiableList(sliders), Collections.unmodifiableList(bones), weightBones, weightValues);
    }

    private static DataInputStream open(File file, String magic) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file), 1 << 16));
        try {
            byte[] head = new byte[4];
            in.readFully(head);
            if (!magic.equals(new String(head, StandardCharsets.US_ASCII))) {
                throw new IOException(file.getName() + " is not a " + magic + " file");
            }
            int version = leInt(in);
            if (version != VERSION) {
                throw new IOException(file.getName() + " has version " + version + ", this editor reads version " + VERSION);
            }
            return in;
        } catch (IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }

    private static int leInt(DataInputStream in) throws IOException {
        return Integer.reverseBytes(in.readInt());
    }

    private static int leU16(DataInputStream in) throws IOException {
        return Short.reverseBytes(in.readShort()) & 0xFFFF;
    }

    private static ByteBuffer readBlock(DataInputStream in, int bytes) throws IOException {
        byte[] data = new byte[bytes];
        in.readFully(data);
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static float[] readFloats(DataInputStream in, int count) throws IOException {
        ByteBuffer block = readBlock(in, count * 4);
        float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            result[i] = block.getFloat();
        }
        return result;
    }

    private static int[] readInts(DataInputStream in, int count) throws IOException {
        ByteBuffer block = readBlock(in, count * 4);
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = block.getInt();
        }
        return result;
    }

    private static List<Bone> readBones(File file) throws IOException {
        JsonValue root = new JsonReader().parse(new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        Map<String, Integer> indexOf = new HashMap<>();
        List<Bone> bones = new ArrayList<>();
        for (JsonValue bone = root.get("bones").child; bone != null; bone = bone.next) {
            String name = bone.getString("name");
            String parent = bone.getString("parent", null);
            if (parent != null && !indexOf.containsKey(parent)) {
                throw new IOException("rig.json lists bone " + name + " before its parent " + parent);
            }
            indexOf.put(name, bones.size());
            bones.add(new Bone(name, parent == null ? -1 : indexOf.get(parent), bone.get("head").asIntArray(), bone.get("tail").asIntArray()));
        }
        return bones;
    }

    private static List<SliderDef> readSliders(File file, Map<String, Target> targets) throws IOException {
        JsonValue root = new JsonReader().parse(new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        List<SliderDef> sliders = new ArrayList<>();
        for (JsonValue slider = root.get("sliders").child; slider != null; slider = slider.next) {
            String low = slider.getString("min", null);
            String high = slider.getString("max");
            if (!targets.containsKey(high) || (low != null && !targets.containsKey(low))) {
                throw new IOException("sliders.json uses a target that targets.bin does not have: " + slider.getString("id"));
            }
            sliders.add(new SliderDef(slider.getString("id"), slider.getString("group"), slider.getString("label"), low, high));
        }
        return sliders;
    }

    /** x, y, z of every vertex of the base body, in MakeHuman units. */
    public float[] basePositions() {
        return basePositions.clone();
    }

    public int baseVertexCount() {
        return basePositions.length / 3;
    }

    /** For each vertex of the drawn mesh, the base vertex it follows (a vertex on a texture seam appears more than once). */
    public int[] renderSource() {
        return renderSource.clone();
    }

    /** u, v of every drawn vertex. */
    public float[] renderUvs() {
        return renderUvs.clone();
    }

    public int renderVertexCount() {
        return renderSource.length;
    }

    /** Three drawn-vertex numbers per triangle, counter-clockwise seen from outside. */
    public int[] triangles() {
        return triangles.clone();
    }

    public Target target(String name) {
        Target target = targets.get(name);
        if (target == null) {
            throw new IllegalArgumentException("No such morph target: " + name);
        }
        return target;
    }

    public boolean hasTarget(String name) {
        return targets.containsKey(name);
    }

    public int targetCount() {
        return targets.size();
    }

    public List<SliderDef> sliders() {
        return sliders;
    }

    public SliderDef slider(String id) {
        for (SliderDef slider : sliders) {
            if (slider.id().equals(id)) {
                return slider;
            }
        }
        throw new IllegalArgumentException("No such slider: " + id);
    }

    public List<Bone> bones() {
        return bones;
    }

    public int boneIndex(String name) {
        for (int i = 0; i < bones.size(); i++) {
            if (bones.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No such bone: " + name);
    }

    /** Bones that move base vertex {@code vertex} (at most {@value #MAX_INFLUENCES}); empty when none does. */
    public int[] weightBones(int vertex) {
        return weightBones[vertex].clone();
    }

    /** Weights for {@link #weightBones}; they add up to 1. */
    public float[] weightValues(int vertex) {
        return weightValues[vertex].clone();
    }
}
