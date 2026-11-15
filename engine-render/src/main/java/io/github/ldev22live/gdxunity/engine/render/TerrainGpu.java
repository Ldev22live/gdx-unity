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
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceManager;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;

import java.util.ArrayList;
import java.util.List;

/**
 * The graphics copy of one {@link Terrain} (TD-082): a mesh per chunk, each rebuilt only when the terrain says its chunk changed.
 * Chunks are model instances, so the renderer draws them like any other model with the same PBR shading. Needs a graphics context.
 */
final class TerrainGpu implements Disposable {
    /** Terrain is matt ground. */
    static final float ROUGHNESS = 0.95f;
    /** How far, as a share of the height range in use, the real range may move before the colours are made again. */
    static final float RANGE_TOLERANCE = 0.05f;

    private final Terrain terrain;
    private final TerrainMeshBuilder builder = new TerrainMeshBuilder();
    private final Chunk[] chunks;
    private final float[] scratch = new float[(Terrain.CHUNK_CELLS + 1) * (Terrain.CHUNK_CELLS + 1) * TerrainMeshBuilder.FLOATS_PER_VERTEX];
    private float rangeMin;
    private float rangeMax;
    private int rangeRevision = -1;
    private final List<ModelInstance> instances = new ArrayList<>();

    private static final class Chunk {
        Model model;
        ModelInstance instance;
        Mesh mesh;
        int revision = -1;
        /** The height range the colours were made with; a changed range recolours every chunk. */
        float rangeMin = Float.NaN;
        float rangeMax = Float.NaN;
    }

    TerrainGpu(Terrain terrain) {
        this.terrain = terrain;
        this.chunks = new Chunk[terrain.getChunksX() * terrain.getChunksZ()];
    }

    Terrain getTerrain() {
        return terrain;
    }

    /** Brings every chunk whose terrain changed up to date and returns the instances to draw, placed by the caller's transform. */
    List<ModelInstance> sync() {
        if (rangeRevision != terrain.getRevision()) {
            float[] range = terrain.heightRange();
            // The snow line follows the height range, but a small change keeps the colours already made: raising a hill by a few
            // units would otherwise recolour (rebuild) every chunk on every frame of a stroke
            float tolerance = Math.max(rangeMax - rangeMin, 1f) * RANGE_TOLERANCE;
            if (rangeRevision < 0 || Math.abs(range[0] - rangeMin) > tolerance || Math.abs(range[1] - rangeMax) > tolerance) {
                rangeMin = range[0];
                rangeMax = range[1];
            }
            rangeRevision = terrain.getRevision();
        }
        instances.clear();
        for (int cz = 0; cz < terrain.getChunksZ(); cz++) {
            for (int cx = 0; cx < terrain.getChunksX(); cx++) {
                int index = cz * terrain.getChunksX() + cx;
                Chunk chunk = chunks[index];
                if (chunk == null) {
                    chunk = create(cx, cz);
                    chunks[index] = chunk;
                }
                int revision = terrain.getChunkRevision(cx, cz);
                if (chunk.revision != revision || chunk.rangeMin != rangeMin || chunk.rangeMax != rangeMax) {
                    int floats = builder.vertices(terrain, cx, cz, rangeMin, rangeMax, scratch);
                    chunk.mesh.setVertices(scratch, 0, floats);
                    chunk.revision = revision;
                    chunk.rangeMin = rangeMin;
                    chunk.rangeMax = rangeMax;
                }
                instances.add(chunk.instance);
            }
        }
        return instances;
    }

    private Chunk create(int cx, int cz) {
        int countX = TerrainMeshBuilder.vertexCountX(terrain, cx);
        int countZ = TerrainMeshBuilder.vertexCountZ(terrain, cz);
        short[] indices = TerrainMeshBuilder.indices(countX, countZ);
        Chunk chunk = new Chunk();
        chunk.mesh = new Mesh(false, countX * countZ, indices.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE));
        chunk.mesh.setIndices(indices);
        Material material = new Material(
            PBRColorAttribute.createBaseColorFactor(Color.WHITE),
            PBRFloatAttribute.createMetallic(0f),
            PBRFloatAttribute.createRoughness(ROUGHNESS));
        MeshPart part = new MeshPart("terrain", chunk.mesh, 0, indices.length, GL20.GL_TRIANGLES);
        Node node = new Node();
        node.id = "terrain";
        node.parts.add(new NodePart(part, material));
        chunk.model = new Model();
        chunk.model.nodes.add(node);
        chunk.model.meshes.add(chunk.mesh);
        chunk.model.meshParts.add(part);
        chunk.model.materials.add(material);
        chunk.model.manageDisposable(chunk.mesh);
        chunk.instance = new ModelInstance(chunk.model);
        return chunk;
    }

    /** Disposes every chunk; all are attempted and failures reported together (TD-008). Idempotent. */
    @Override
    public void dispose() {
        ResourceManager owned = new ResourceManager();
        for (int i = 0; i < chunks.length; i++) {
            if (chunks[i] != null) {
                owned.register(chunks[i].model);
                chunks[i] = null;
            }
        }
        instances.clear();
        owned.dispose();
    }
}
