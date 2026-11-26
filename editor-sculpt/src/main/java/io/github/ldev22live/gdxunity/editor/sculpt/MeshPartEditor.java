package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.command.CommandHistory;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.sculpt.EditableMesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Detached triangle editing in primitive-local coordinates. Vertex ancestry retains the source attribute seam. */
public final class MeshPartEditor {
    public enum Kind { Vertex, Edge, Face }
    public enum Tool { Translate, Extrude, Inset, Bevel, Delete, Material }
    public record Vertex(float x, float y, float z, int sourceIndex) {
        Vector3 position() { return new Vector3(x, y, z); }
        Vertex at(Vector3 p) {
            if (!Float.isFinite(p.x) || !Float.isFinite(p.y) || !Float.isFinite(p.z)) {
                throw new IllegalArgumentException("Non-finite vertex " + p);
            }
            return new Vertex(p.x, p.y, p.z, sourceIndex);
        }
    }
    public record Face(int a, int b, int c, int material) {}
    public record Edge(int a, int b) {
        public Edge {
            if (a > b) { int swap = a; a = b; b = swap; }
        }
    }
    public record Geometry(List<Vertex> vertices, List<Face> faces, List<Edge> edges) {
        private Geometry(List<Vertex> vertices, List<Face> faces) {
            this(List.copyOf(vertices), List.copyOf(faces), edgesOf(faces));
        }
        private static List<Edge> edgesOf(List<Face> faces) {
            var result = new LinkedHashSet<Edge>();
            for (Face f : faces) {
                result.add(new Edge(f.a, f.b));
                result.add(new Edge(f.b, f.c));
                result.add(new Edge(f.c, f.a));
            }
            return List.copyOf(result);
        }
    }

    private final Geometry initialGeometry;
    private Geometry geometry;
    private final int materials;
    private final CommandHistory history = new CommandHistory();

    private MeshPartEditor(Geometry geometry, int materials) {
        this.initialGeometry = geometry;
        this.geometry = geometry;
        this.materials = materials;
    }

    /** Opens each mesh primitive in source order, preserving independent indices and material slots. */
    public static List<MeshPartEditor> open(EditableMesh source) {
        JsonValue model = Objects.requireNonNull(source, "source cannot be null").model();
        var buffers = new ArrayList<ByteBuffer>();
        for (JsonValue buffer : model.require("buffers")) {
            String uri = buffer.getString("uri");
            buffers.add(ByteBuffer.wrap(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1)))
                .order(ByteOrder.LITTLE_ENDIAN));
        }
        var result = new ArrayList<MeshPartEditor>();
        for (JsonValue mesh : model.require("meshes")) {
            for (JsonValue primitive : mesh.require("primitives")) {
                var positions = new Accessor(model, buffers, primitive.require("attributes").getInt("POSITION"), true);
                var vertices = new ArrayList<Vertex>();
                for (int i = 0; i < positions.count; i++) {
                    int offset = positions.offset + i * positions.stride;
                    vertices.add(new Vertex(positions.bytes.getFloat(offset), positions.bytes.getFloat(offset + 4),
                        positions.bytes.getFloat(offset + 8), i));
                }
                Accessor indices = primitive.has("indices")
                    ? new Accessor(model, buffers, primitive.getInt("indices"), false) : null;
                var faces = new ArrayList<Face>();
                int count = indices == null ? vertices.size() : indices.count;
                for (int i = 0; i < count; i += 3) {
                    faces.add(new Face(indices == null ? i : indices.index(i),
                        indices == null ? i + 1 : indices.index(i + 1),
                        indices == null ? i + 2 : indices.index(i + 2), primitive.getInt("material", -1)));
                }
                result.add(new MeshPartEditor(new Geometry(vertices, faces), model.has("materials") ? model.get("materials").size : 0));
            }
        }
        return List.copyOf(result);
    }

    private static final class Accessor {
        final ByteBuffer bytes;
        final int count, offset, stride, width;
        Accessor(JsonValue model, List<ByteBuffer> buffers, int id, boolean position) {
            JsonValue accessor = model.require("accessors").get(id);
            JsonValue view = model.require("bufferViews").get(accessor.getInt("bufferView"));
            bytes = buffers.get(view.getInt("buffer"));
            count = accessor.getInt("count");
            offset = accessor.getInt("byteOffset", 0) + view.getInt("byteOffset", 0);
            width = position ? 12 : switch (accessor.getInt("componentType")) {
                case 5121 -> 1;
                case 5123 -> 2;
                case 5125 -> 4;
                default -> throw new IllegalArgumentException("Unsupported index accessor " + id);
            };
            stride = view.getInt("byteStride", width);
        }
        int index(int i) {
            int location = offset + i * stride;
            return switch (width) {
                case 1 -> Byte.toUnsignedInt(bytes.get(location));
                case 2 -> Short.toUnsignedInt(bytes.getShort(location));
                default -> bytes.getInt(location);
            };
        }
    }

    /** Whether the current preview differs from the imported primitive. */
    public boolean isModified() { return geometry != initialGeometry; }

    public Geometry geometry() { return geometry; }
    public boolean undo() { return history.undo(); }
    public boolean redo() { return history.redo(); }

    /** Translates any element, or performs a face operation. Inset and bevel take a fraction from zero to one.
     * Extrusion uses signed source units along the face normal. Material takes a zero-based slot, or -1 for default. */
    public boolean apply(Tool tool, Kind kind, int index, float x, float y, float z) {
        Geometry before = geometry;
        var vertices = new ArrayList<>(before.vertices);
        var faces = new ArrayList<>(before.faces);
        int count = switch (kind) {
            case Vertex -> vertices.size();
            case Edge -> before.edges.size();
            case Face -> faces.size();
        };
        if (index < 0 || index >= count) throw new IllegalArgumentException("Invalid " + kind + " index " + index);
        if (tool == Tool.Translate) {
            int[] selected = switch (kind) {
                case Vertex -> new int[]{index};
                case Edge -> { Edge e = before.edges.get(index); yield new int[]{e.a, e.b}; }
                case Face -> { Face f = faces.get(index); yield new int[]{f.a, f.b, f.c}; }
            };
            for (int i : selected) {
                Vertex v = before.vertices.get(i);
                vertices.set(i, v.at(v.position().add(x, y, z)));
            }
        } else {
            if (kind != Kind.Face) throw new IllegalArgumentException(tool + " requires a face selection");
            Face face = faces.get(index);
            switch (tool) {
                case Delete -> faces.remove(index);
                case Material -> {
                    if (x != (int)x || x < -1 || x >= materials) throw new IllegalArgumentException("Invalid material slot " + x);
                    faces.set(index, new Face(face.a, face.b, face.c, (int)x));
                }
                default -> ring(vertices, faces, index, face, tool, x);
            }
        }
        if (tool == Tool.Translate) {
            for (Face face : faces) {
                if (areaSquared(vertices, face) == 0 && areaSquared(before.vertices, face) > 0) {
                    throw new IllegalArgumentException("Translation collapses triangle " + face);
                }
            }
        }
        Geometry after = new Geometry(vertices, faces);
        if (before.equals(after)) return false;
        return history.execute(new EditorCommand() {
            public boolean execute() { geometry = after; return true; }
            public boolean undo() { geometry = before; return true; }
            public String getDescription() { return tool + " " + kind; }
        });
    }

    private static float areaSquared(List<Vertex> vertices, Face face) {
        Vector3 a = vertices.get(face.a).position();
        return vertices.get(face.b).position().sub(a).crs(vertices.get(face.c).position().sub(a)).len2();
    }

    private static void ring(List<Vertex> vertices, List<Face> faces, int index, Face f, Tool tool, float amount) {
        Vector3 a = vertices.get(f.a).position(), b = vertices.get(f.b).position(), c = vertices.get(f.c).position();
        Vector3 normal = b.cpy().sub(a).crs(c.cpy().sub(a));
        if (normal.isZero() || !Float.isFinite(amount) || amount == 0
            || (tool != Tool.Extrude && (amount <= 0 || amount >= 1))) {
            throw new IllegalArgumentException("Invalid " + tool + " amount or degenerate face: " + amount);
        }
        Vector3 center = a.cpy().add(b).add(c).scl(1f / 3f);
        // A face bevel raises the inset cap by the inset distance measured at its nearest edge.
        float height = tool == Tool.Bevel ? amount * normal.len() / (3 * Math.max(a.dst(b), Math.max(b.dst(c), c.dst(a))))
            : tool == Tool.Extrude ? amount : 0;
        normal.nor().scl(height);
        int base = vertices.size();
        int[] outer = {f.a, f.b, f.c};
        for (int id : outer) {
            Vertex vertex = vertices.get(id);
            vertices.add(vertex.at(vertex.position().lerp(center, tool == Tool.Extrude ? 0 : amount).add(normal)));
        }
        faces.set(index, new Face(base, base + 1, base + 2, f.material));
        for (int i = 0; i < 3; i++) {
            int j = (i + 1) % 3;
            faces.add(new Face(outer[i], outer[j], base + j, f.material));
            faces.add(new Face(outer[i], base + j, base + i, f.material));
        }
    }
}
