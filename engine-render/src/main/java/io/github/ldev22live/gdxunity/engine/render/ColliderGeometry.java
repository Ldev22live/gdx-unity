package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.ModelAssets.LoadedModel;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ColliderGeometrySource;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshGeometry3D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Reads the triangles of the mesh an entity shows, for mesh and convex hull colliders (TD-052): its glTF model (every mesh
 * node, rest pose, with the model's import scale) or its mesh primitive. Models need a GL context to load, as for drawing.
 */
public class ColliderGeometry implements ColliderGeometrySource {
    /** Segments around, and rings from pole to pole, of the sphere primitive's collider mesh. */
    private static final int SPHERE_SEGMENTS = 16;
    private static final int SPHERE_RINGS = 12;

    private final ModelAssets models;

    /** @param models where glTF models are loaded from, shared with the renderer; may be null for primitives only */
    public ColliderGeometry(ModelAssets models) {
        this.models = models;
    }

    @Override
    public MeshGeometry3D geometryOf(Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        ModelComponent model = entity.getComponent(ModelComponent.class);
        if (model != null && model.isEnabled()) {
            return ofModel(entity, model);
        }
        MeshPrimitiveComponent primitive = entity.getComponent(MeshPrimitiveComponent.class);
        if (primitive != null && primitive.isEnabled()) {
            return ofPrimitive(primitive);
        }
        throw new IllegalStateException(entity.getName() + " has a mesh or convex hull collider but shows no mesh. Add a Model or a Mesh "
            + "Primitive to it: the collider is built from that mesh.");
    }

    private MeshGeometry3D ofModel(Entity entity, ModelComponent component) {
        if (models == null) {
            throw new IllegalStateException(entity.getName() + " has a collider built from a model, but models cannot be loaded here.");
        }
        LoadedModel loaded = models.get(component.getModelPath());
        if (loaded == null) {
            String reason = models.info(component.getModelPath()).error();
            throw new IllegalStateException(entity.getName() + ": the model " + component.getModelPath() + " could not be loaded"
                + (reason != null ? " (" + reason + ")" : "") + ", so its collider cannot be built.");
        }
        MeshGeometry3D geometry = ofModel(loaded.model(), component.getImportScale());
        if (geometry.triangleCount() == 0) {
            throw new IllegalStateException(entity.getName() + ": the model " + component.getModelPath() + " has no triangles to build a "
                + "collider from.");
        }
        return geometry;
    }

    /** Triangles of every mesh node of {@code model} in its rest pose, scaled by {@code importScale}. */
    static MeshGeometry3D ofModel(Model model, float importScale) {
        ModelInstance instance = new ModelInstance(model);
        instance.calculateTransforms();
        List<float[]> positions = new ArrayList<>();
        List<int[]> triangles = new ArrayList<>();
        int[] vertexBase = {0};
        for (Node node : instance.nodes) {
            collect(node, importScale, positions, triangles, vertexBase);
        }
        float[] allPositions = new float[vertexBase[0] * 3];
        int at = 0;
        for (float[] chunk : positions) {
            System.arraycopy(chunk, 0, allPositions, at, chunk.length);
            at += chunk.length;
        }
        int[] allIndices = new int[triangles.stream().mapToInt(t -> t.length).sum()];
        at = 0;
        for (int[] chunk : triangles) {
            System.arraycopy(chunk, 0, allIndices, at, chunk.length);
            at += chunk.length;
        }
        return new MeshGeometry3D(allPositions, allIndices);
    }

    private static void collect(Node node, float scale, List<float[]> positions, List<int[]> triangles, int[] vertexBase) {
        for (NodePart part : node.parts) {
            if (part.meshPart.primitiveType != GL20.GL_TRIANGLES) {
                continue;
            }
            collect(part.meshPart.mesh, part.meshPart.offset, part.meshPart.size, node.globalTransform, scale, positions, triangles, vertexBase);
        }
        for (Node child : node.getChildren()) {
            collect(child, scale, positions, triangles, vertexBase);
        }
    }

    private static void collect(Mesh mesh, int indexOffset, int indexCount, Matrix4 transform, float scale, List<float[]> positions,
                                List<int[]> triangles, int[] vertexBase) {
        VertexAttribute attribute = mesh.getVertexAttribute(Usage.Position);
        int stride = mesh.getVertexSize() / 4;
        int offset = attribute.offset / 4;
        float[] vertices = new float[mesh.getNumVertices() * stride];
        mesh.getVertices(0, vertices.length, vertices);
        int[] source = new int[indexCount];
        if (mesh.getNumIndices() > 0) {
            short[] shorts = new short[indexCount];
            mesh.getIndices(indexOffset, indexCount, shorts, 0);
            for (int i = 0; i < indexCount; i++) {
                source[i] = shorts[i] & 0xFFFF;
            }
        } else {
            for (int i = 0; i < indexCount; i++) {
                source[i] = indexOffset + i;
            }
        }
        // Only the vertices this part uses are copied, so a mesh shared by several parts is not duplicated whole
        int[] remap = new int[mesh.getNumVertices()];
        Arrays.fill(remap, -1);
        List<Float> used = new ArrayList<>();
        int[] result = new int[source.length - source.length % 3];
        Vector3 point = new Vector3();
        for (int i = 0; i < result.length; i++) {
            int vertex = source[i];
            if (remap[vertex] < 0) {
                remap[vertex] = used.size() / 3;
                point.set(vertices[vertex * stride + offset], vertices[vertex * stride + offset + 1], vertices[vertex * stride + offset + 2]);
                point.mul(transform).scl(scale);
                used.add(point.x);
                used.add(point.y);
                used.add(point.z);
            }
            result[i] = vertexBase[0] + remap[vertex];
        }
        float[] chunk = new float[used.size()];
        for (int i = 0; i < chunk.length; i++) {
            chunk[i] = used.get(i);
        }
        positions.add(chunk);
        triangles.add(result);
        vertexBase[0] += chunk.length / 3;
    }

    /** The box, ellipsoid or plane that {@code primitive} draws, with its size (before the entity's scale). */
    static MeshGeometry3D ofPrimitive(MeshPrimitiveComponent primitive) {
        Vector3 half = primitive.getExtent().scl(0.5f);
        return switch (primitive.getShape()) {
            case BOX -> box(half);
            case PLANE -> plane(half);
            case SPHERE -> sphere(half);
        };
    }

    private static MeshGeometry3D box(Vector3 h) {
        float[] positions = {
            -h.x, -h.y, -h.z, h.x, -h.y, -h.z, h.x, h.y, -h.z, -h.x, h.y, -h.z,
            -h.x, -h.y, h.z, h.x, -h.y, h.z, h.x, h.y, h.z, -h.x, h.y, h.z};
        int[] indices = {
            0, 2, 1, 0, 3, 2, // back (-z)
            4, 5, 6, 4, 6, 7, // front (+z)
            0, 4, 7, 0, 7, 3, // left (-x)
            1, 2, 6, 1, 6, 5, // right (+x)
            3, 7, 6, 3, 6, 2, // top (+y)
            0, 1, 5, 0, 5, 4}; // bottom (-y)
        return new MeshGeometry3D(positions, indices);
    }

    private static MeshGeometry3D plane(Vector3 h) {
        float[] positions = {-h.x, 0f, -h.z, h.x, 0f, -h.z, h.x, 0f, h.z, -h.x, 0f, h.z};
        // Counter-clockwise seen from above (+Y), so the plane's face points up
        int[] indices = {0, 2, 1, 0, 3, 2};
        return new MeshGeometry3D(positions, indices);
    }

    private static MeshGeometry3D sphere(Vector3 h) {
        int columns = SPHERE_SEGMENTS;
        int rows = SPHERE_RINGS;
        float[] positions = new float[((rows + 1) * columns) * 3];
        int at = 0;
        for (int r = 0; r <= rows; r++) {
            double polar = Math.PI * r / rows;
            for (int c = 0; c < columns; c++) {
                double azimuth = 2.0 * Math.PI * c / columns;
                positions[at++] = (float) (h.x * Math.sin(polar) * Math.cos(azimuth));
                positions[at++] = (float) (h.y * Math.cos(polar));
                positions[at++] = (float) (h.z * Math.sin(polar) * Math.sin(azimuth));
            }
        }
        int[] indices = new int[rows * columns * 6];
        at = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                int a = r * columns + c;
                int b = r * columns + (c + 1) % columns;
                int d = (r + 1) * columns + c;
                int e = (r + 1) * columns + (c + 1) % columns;
                indices[at++] = a;
                indices[at++] = b;
                indices[at++] = d;
                indices[at++] = b;
                indices[at++] = e;
                indices[at++] = d;
            }
        }
        return new MeshGeometry3D(positions, indices);
    }
}
