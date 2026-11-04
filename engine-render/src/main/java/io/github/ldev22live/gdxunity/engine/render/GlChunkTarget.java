package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;

/**
 * One chunk of a {@link DynamicMesh} on the graphics card: a LibGDX mesh with a position and a normal per vertex,
 * wrapped as a model instance so the renderer draws it like any other model, with the same PBR shading. Needs a
 * graphics context.
 */
final class GlChunkTarget implements DynamicMeshGpu.Target {
    /** Surface roughness of a sculpt: matt, like clay. */
    private static final float ROUGHNESS = 0.8f;

    /** Emissive colour of the wireframe lines, so they read the same in any lighting. */
    private static final Color WIRE_COLOR = new Color(0.55f, 0.78f, 1f, 1f);
    /** How far the wireframe sits above the surface, as a fraction of the chunk's size, so it does not z-fight with it. */
    private static final float WIRE_LIFT = 0.0015f;
    private static final int FLOATS = DynamicMesh.FLOATS_PER_VERTEX;

    private final Model model;
    private final ModelInstance instance;
    private final Mesh mesh;
    private final Model wireModel;
    private final ModelInstance wireInstance;
    private final Mesh wireMesh;
    private float[] liftedVertices = new float[0];

    GlChunkTarget(int vertexCount, short[] indices) {
        mesh = new Mesh(false, vertexCount, indices.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE));
        mesh.setIndices(indices);
        Material material = new Material(
            PBRColorAttribute.createBaseColorFactor(Color.WHITE),
            PBRFloatAttribute.createMetallic(0f),
            PBRFloatAttribute.createRoughness(ROUGHNESS));
        MeshPart part = new MeshPart("surface", mesh, 0, indices.length, GL20.GL_TRIANGLES);
        Node node = new Node();
        node.id = "surface";
        node.parts.add(new NodePart(part, material));
        model = new Model();
        model.nodes.add(node);
        model.meshes.add(mesh);
        model.meshParts.add(part);
        model.materials.add(material);
        model.manageDisposable(mesh);
        instance = new ModelInstance(model);

        // The same vertices again, nudged out along their normals, drawn as the unique edges of the triangles
        short[] edges = uniqueEdges(indices);
        wireMesh = new Mesh(false, vertexCount, edges.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE));
        wireMesh.setIndices(edges);
        Material wireMaterial = new Material(
            PBRColorAttribute.createBaseColorFactor(Color.BLACK),
            PBRColorAttribute.createEmissive(WIRE_COLOR));
        MeshPart wirePart = new MeshPart("wire", wireMesh, 0, edges.length, GL20.GL_LINES);
        Node wireNode = new Node();
        wireNode.id = "wire";
        wireNode.parts.add(new NodePart(wirePart, wireMaterial));
        wireModel = new Model();
        wireModel.nodes.add(wireNode);
        wireModel.meshes.add(wireMesh);
        wireModel.meshParts.add(wirePart);
        wireModel.materials.add(wireMaterial);
        wireModel.manageDisposable(wireMesh);
        wireInstance = new ModelInstance(wireModel);
    }

    /** Each edge of the triangles once, as pairs of vertex indices. */
    private static short[] uniqueEdges(short[] triangles) {
        Set<Integer> seen = new HashSet<>();
        short[] edges = new short[triangles.length * 2];
        int count = 0;
        for (int t = 0; t + 2 < triangles.length; t += 3) {
            for (int e = 0; e < 3; e++) {
                short a = triangles[t + e];
                short b = triangles[t + (e + 1) % 3];
                int low = Math.min(a & 0xFFFF, b & 0xFFFF);
                int high = Math.max(a & 0xFFFF, b & 0xFFFF);
                if (seen.add(low << 16 | high)) {
                    edges[count++] = (short) low;
                    edges[count++] = (short) high;
                }
            }
        }
        return Arrays.copyOf(edges, count);
    }

    @Override
    public void upload(float[] vertices, int vertexCount) {
        mesh.setVertices(vertices, 0, vertexCount * FLOATS);
        wireMesh.setVertices(lift(vertices, vertexCount), 0, vertexCount * FLOATS);
    }

    /** Copies the vertices with every position moved a little along its normal. */
    private float[] lift(float[] vertices, int vertexCount) {
        int floats = vertexCount * FLOATS;
        if (liftedVertices.length < floats) {
            liftedVertices = new float[floats];
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int i = 0; i < floats; i += FLOATS) {
            min = Math.min(min, Math.min(vertices[i], Math.min(vertices[i + 1], vertices[i + 2])));
            max = Math.max(max, Math.max(vertices[i], Math.max(vertices[i + 1], vertices[i + 2])));
        }
        float lift = (max - min) * WIRE_LIFT;
        for (int i = 0; i < floats; i += FLOATS) {
            liftedVertices[i] = vertices[i] + vertices[i + 3] * lift;
            liftedVertices[i + 1] = vertices[i + 1] + vertices[i + 4] * lift;
            liftedVertices[i + 2] = vertices[i + 2] + vertices[i + 5] * lift;
            liftedVertices[i + 3] = vertices[i + 3];
            liftedVertices[i + 4] = vertices[i + 4];
            liftedVertices[i + 5] = vertices[i + 5];
        }
        return liftedVertices;
    }

    /** The instance to draw; place it with its {@code transform}. */
    ModelInstance getInstance() {
        return instance;
    }

    /** The wireframe instance to draw over the surface when the wireframe is on; place it with its {@code transform}. */
    ModelInstance getWireInstance() {
        return wireInstance;
    }

    /** Sets the base colour of this chunk's surface (already converted to linear space). */
    void setColor(Color linear, float alpha) {
        PBRColorAttribute baseColor = (PBRColorAttribute) instance.materials.get(0).get(PBRColorAttribute.BaseColorFactor);
        baseColor.color.set(linear.r, linear.g, linear.b, alpha);
    }

    @Override
    public void dispose() {
        model.dispose();
        wireModel.dispose();
    }
}
