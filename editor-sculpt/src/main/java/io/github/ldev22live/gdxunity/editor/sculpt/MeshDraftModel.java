package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import io.github.ldev22live.gdxunity.engine.sculpt.EditableMesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rebuilds changed glTF primitives while preserving hierarchy, material definitions and vertex attribute seams. */
final class MeshDraftModel {
    private final JsonValue model;
    private final List<byte[]> originalBuffers = new ArrayList<>();

    private MeshDraftModel(EditableMesh source) {
        model = source.model();
        for (JsonValue buffer : model.require("buffers")) {
            String uri = buffer.getString("uri");
            originalBuffers.add(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1)));
        }
    }

    static EditableMesh build(EditableMesh source, List<MeshPartEditor> drafts) {
        MeshDraftModel writer = new MeshDraftModel(source);
        int index = 0;
        for (JsonValue mesh : writer.model.require("meshes")) {
            JsonValue replacements = array();
            for (JsonValue primitive : mesh.require("primitives")) {
                MeshPartEditor draft = drafts.get(index++);
                if (draft.isModified()) writer.append(replacements, primitive, draft.geometry());
                else replacements.addChild(copy(primitive));
            }
            if (replacements.size == 0) throw new IllegalArgumentException("A saved mesh must contain at least one triangle.");
            mesh.remove("primitives");
            mesh.addChild("primitives", replacements);
        }
        if (index != drafts.size()) throw new IllegalArgumentException("Primitive count differs from source");
        return new EditableMesh(source.sourcePath(), source.sourceSha256(), source.provenance(), writer.model.toJson(OutputType.json));
    }

    private void append(JsonValue result, JsonValue primitive, MeshPartEditor.Geometry geometry) {
        Map<Integer, List<MeshPartEditor.Face>> groups = new LinkedHashMap<>();
        for (var face : geometry.faces()) groups.computeIfAbsent(face.material(), key -> new ArrayList<>()).add(face);
        if (groups.isEmpty()) return;
        JsonValue attrs = object();
        for (JsonValue attribute : primitive.require("attributes")) {
            int accessor = attribute.name.equals("POSITION") ? positions(geometry.vertices())
                : duplicateAttribute(attribute.asInt(), geometry.vertices());
            attrs.addChild(attribute.name, new JsonValue(accessor));
        }
        for (var entry : groups.entrySet()) {
            boolean shortIndices = geometry.vertices().size() <= 65536;
            int width = shortIndices ? Short.BYTES : Integer.BYTES;
            int count = Math.multiplyExact(entry.getValue().size(), 3);
            ByteBuffer indices = bytes(Math.multiplyExact(count, width));
            for (var face : entry.getValue()) {
                if (shortIndices) indices.putShort((short)face.a()).putShort((short)face.b()).putShort((short)face.c());
                else indices.putInt(face.a()).putInt(face.b()).putInt(face.c());
            }
            JsonValue p = copy(primitive);
            p.remove("attributes");
            p.remove("indices");
            p.remove("material");
            p.addChild("attributes", copy(attrs));
            p.addChild("indices", new JsonValue(accessor(indices, "SCALAR", shortIndices ? 5123 : 5125, count, false)));
            if (entry.getKey() >= 0) p.addChild("material", new JsonValue(entry.getKey()));
            result.addChild(p);
        }
    }

    private int positions(List<MeshPartEditor.Vertex> vertices) {
        ByteBuffer data = bytes(Math.multiplyExact(vertices.size(), 3 * Float.BYTES));
        float[] low = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] high = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (var v : vertices) {
            float[] xyz = {v.x(), v.y(), v.z()};
            for (int i = 0; i < xyz.length; i++) {
                data.putFloat(xyz[i]);
                low[i] = Math.min(low[i], xyz[i]);
                high[i] = Math.max(high[i], xyz[i]);
            }
        }
        int id = accessor(data, "VEC3", 5126, vertices.size(), false);
        JsonValue position = model.require("accessors").get(id);
        JsonValue min = array(), max = array();
        for (int i = 0; i < 3; i++) {
            min.addChild(new JsonValue(low[i]));
            max.addChild(new JsonValue(high[i]));
        }
        position.addChild("min", min);
        position.addChild("max", max);
        return id;
    }

    private int duplicateAttribute(int id, List<MeshPartEditor.Vertex> vertices) {
        JsonValue attr = model.require("accessors").get(id);
        JsonValue view = model.require("bufferViews").get(attr.getInt("bufferView"));
        int type = attr.getInt("componentType");
        int size = switch (type) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            case 5125, 5126 -> 4;
            default -> throw new IllegalArgumentException("Unknown component type " + type);
        };
        String shape = attr.getString("type");
        int components = switch (shape) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            default -> throw new IllegalArgumentException("Unknown attribute shape " + shape);
        };
        int width = size * components;
        int stride = view.getInt("byteStride", width);
        int start = view.getInt("byteOffset", 0) + attr.getInt("byteOffset", 0);
        byte[] source = originalBuffers.get(view.getInt("buffer"));
        // Each vertex attribute starts on a four-byte boundary, including RGB byte colours.
        int packedStride = (width + 3) & ~3;
        ByteBuffer data = bytes(Math.multiplyExact(vertices.size(), packedStride));
        for (var v : vertices) {
            data.put(source, start + stride * v.sourceIndex(), width);
            data.position(data.position() + packedStride - width);
        }
        int result = accessor(data, shape, type, vertices.size(), attr.getBoolean("normalized", false));
        if (packedStride != width) model.require("bufferViews").get(model.require("accessors").get(result).getInt("bufferView"))
            .addChild("byteStride", new JsonValue(packedStride));
        return result;
    }

    private int accessor(ByteBuffer data, String shape, int type, int count, boolean normalized) {
        JsonValue buffers = model.require("buffers"), views = model.require("bufferViews"), accessors = model.require("accessors");
        JsonValue buffer = object(), view = object(), attr = object();
        buffer.addChild("uri", new JsonValue("data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(data.array())));
        buffer.addChild("byteLength", new JsonValue(data.capacity()));
        view.addChild("buffer", new JsonValue(buffers.size));
        view.addChild("byteLength", new JsonValue(data.capacity()));
        attr.addChild("bufferView", new JsonValue(views.size));
        attr.addChild("componentType", new JsonValue(type));
        attr.addChild("type", new JsonValue(shape));
        attr.addChild("count", new JsonValue(count));
        if (normalized) attr.addChild("normalized", new JsonValue(true));
        buffers.addChild(buffer);
        views.addChild(view);
        accessors.addChild(attr);
        return accessors.size - 1;
    }

    private static ByteBuffer bytes(int count) { return ByteBuffer.allocate(count).order(ByteOrder.LITTLE_ENDIAN); }
    private static JsonValue array() { return new JsonValue(JsonValue.ValueType.array); }
    private static JsonValue object() { return new JsonValue(JsonValue.ValueType.object); }
    private static JsonValue copy(JsonValue value) { return new JsonReader().parse(value.toJson(OutputType.json)); }
}
