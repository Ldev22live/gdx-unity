package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterBodyComponent;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;

/**
 * The graphics copy of one {@link WaterBodyComponent} (roadmap M3.9, TD-090): a {@link WaterSurfaceBuilder} grid with a
 * translucent PBR material. The grid is rewritten when the waves move or the size changes, and the material follows the
 * component's colour and roughness every frame. Needs a GL context. Owned by {@link SceneRenderer3D}.
 */
final class WaterBodyGpu implements Disposable {
    private final float[] vertices = new float[WaterSurfaceBuilder.vertexCount() * WaterSurfaceBuilder.VERTEX_SIZE];
    private final Model model;
    private final Mesh mesh;
    private final ModelInstance instance;
    private float builtWidth = Float.NaN;
    private float builtLength = Float.NaN;
    private boolean builtAnimated;
    private boolean disposed;

    WaterBodyGpu() {
        short[] indices = WaterSurfaceBuilder.indices();
        mesh = new Mesh(false, WaterSurfaceBuilder.vertexCount(), indices.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE));
        mesh.setIndices(indices);
        Material material = new Material(PBRColorAttribute.createBaseColorFactor(Color.WHITE),
            PBRFloatAttribute.createRoughness(0.5f), PBRFloatAttribute.createMetallic(0f), new BlendingAttribute(1f),
            // Seen from below too
            IntAttribute.createCullFace(GL20.GL_NONE));
        MeshPart part = new MeshPart("water", mesh, 0, indices.length, GL20.GL_TRIANGLES);
        Node node = new Node();
        node.id = "water";
        node.parts.add(new NodePart(part, material));
        model = new Model();
        model.nodes.add(node);
        model.meshes.add(mesh);
        model.meshParts.add(part);
        model.materials.add(material);
        model.manageDisposable(mesh);
        instance = new ModelInstance(model);
    }

    /**
     * Brings the surface and material up to date for {@code water}, {@code seconds} after the scene started.
     *
     * @param linearColor the water's colour already in linear light, alpha its opacity
     */
    ModelInstance sync(WaterBodyComponent water, float seconds, Color linearColor) {
        boolean animated = WaterSurfaceBuilder.isAnimated(water);
        if (animated || builtAnimated || water.getWidth() != builtWidth || water.getLength() != builtLength) {
            WaterSurfaceBuilder.fill(water, animated ? seconds : 0f, vertices);
            mesh.setVertices(vertices);
            builtWidth = water.getWidth();
            builtLength = water.getLength();
            builtAnimated = animated;
        }
        // The instance shares the model's material only by copy; set the instance's own
        Material material = instance.materials.get(0);
        ((PBRColorAttribute) material.get(PBRColorAttribute.BaseColorFactor)).color.set(linearColor);
        ((PBRFloatAttribute) material.get(PBRFloatAttribute.Roughness)).value = water.getRoughness();
        ((BlendingAttribute) material.get(BlendingAttribute.Type)).opacity = linearColor.a;
        return instance;
    }

    /** Disposes the mesh. Idempotent. */
    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            model.dispose();
        }
    }
}
