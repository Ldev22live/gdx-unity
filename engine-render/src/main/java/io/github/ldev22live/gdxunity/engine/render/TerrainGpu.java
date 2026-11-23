package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
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
    private final float[] scratch = new float[TerrainMeshBuilder.vertexCount(Terrain.CHUNK_CELLS + 1, Terrain.CHUNK_CELLS + 1)
        * TerrainMeshBuilder.FLOATS_PER_VERTEX];
    /** Room for every index of a full chunk and its skirts; holes and coarser levels leave some out (TD-089, M3.8). */
    private final short[] indexScratch = new short[Terrain.CHUNK_CELLS * Terrain.CHUNK_CELLS * 6 + TerrainMeshBuilder.maxSkirtIndices()];
    /** Water colour in linear light and how opaque it is (TD-089). */
    static final Color WATER_COLOR = new Color(0.10f, 0.28f, 0.40f, 0.72f);
    /** Water is smooth, so it catches highlights. */
    static final float WATER_ROUGHNESS = 0.15f;
    private Model waterModel;
    private ModelInstance waterInstance;
    private Mesh waterMesh;
    private float waterBuiltAt = Float.NaN;
    private float rangeMin;
    private float rangeMax;
    private int rangeRevision = -1;
    private final List<ModelInstance> instances = new ArrayList<>();

    private static final class Chunk {
        Model model;
        ModelInstance instance;
        Mesh mesh;
        MeshPart part;
        int revision = -1;
        /** The level of detail the triangles were made at, or -1 for full detail without skirts (level of detail off). */
        int level = Integer.MIN_VALUE;
        /** The chunk revision the triangles were made from. */
        int indexRevision = -1;
        /** A sphere around the chunk in the terrain's own space, for culling chunks out of view (roadmap M3.8). */
        final Vector3 center = new Vector3();
        float radius;
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

    /** Brings every chunk whose terrain changed up to date at full detail and returns the instances to draw. */
    List<ModelInstance> sync() {
        return sync(null);
    }

    /**
     * Brings every chunk up to date and returns the instances to draw, placed by the caller's transform. With a camera position (in
     * the terrain's own space), each chunk is drawn at the level of detail its distance calls for, with skirts (roadmap M3.8); its
     * triangles are made again only when its level or its terrain changes. With null, every chunk is at full detail without skirts.
     */
    List<ModelInstance> sync(Vector3 cameraLocal) {
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
                    bound(chunk, floats);
                    chunk.revision = revision;
                    chunk.rangeMin = rangeMin;
                    chunk.rangeMax = rangeMax;
                }
                int level = cameraLocal == null ? -1 : levelOf(cx, cz, cameraLocal);
                if (chunk.level != level || chunk.indexRevision != revision) {
                    // Holes and the level decide the triangles (TD-089, M3.8)
                    int indexCount = TerrainMeshBuilder.indices(terrain, cx, cz, indexScratch, level < 0 ? 1 : TerrainLod.step(level));
                    if (level >= 0) {
                        indexCount = TerrainMeshBuilder.skirtIndices(terrain, cx, cz, indexScratch, indexCount, TerrainLod.step(level));
                    }
                    chunk.mesh.setIndices(indexScratch, 0, indexCount);
                    chunk.part.size = indexCount;
                    chunk.level = level;
                    chunk.indexRevision = revision;
                }
                instances.add(chunk.instance);
            }
        }
        if (terrain.hasWater()) {
            instances.add(water());
        }
        return instances;
    }

    /** The level of detail for chunk (cx, cz) seen from {@code camera} (terrain space): by the distance to its centre on the ground. */
    private int levelOf(int cx, int cz, Vector3 camera) {
        int ix = Math.min(Terrain.chunkStart(cx) + Terrain.CHUNK_CELLS / 2, terrain.getResolutionX() - 1);
        int iz = Math.min(Terrain.chunkStart(cz) + Terrain.CHUNK_CELLS / 2, terrain.getResolutionZ() - 1);
        float dx = terrain.xAt(ix) - camera.x;
        float dy = terrain.getHeight(ix, iz) - camera.y;
        float dz = terrain.zAt(iz) - camera.z;
        return TerrainLod.levelFor((float) Math.sqrt(dx * dx + dy * dy + dz * dz), Terrain.CHUNK_CELLS * terrain.getCellSize());
    }

    /** A sphere around every vertex of the chunk just built (skirts included), from the scratch vertices. */
    private void bound(Chunk chunk, int floats) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < floats; i += TerrainMeshBuilder.FLOATS_PER_VERTEX) {
            minX = Math.min(minX, scratch[i]);
            maxX = Math.max(maxX, scratch[i]);
            minY = Math.min(minY, scratch[i + 1]);
            maxY = Math.max(maxY, scratch[i + 1]);
            minZ = Math.min(minZ, scratch[i + 2]);
            maxZ = Math.max(maxZ, scratch[i + 2]);
        }
        chunk.center.set((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f);
        chunk.radius = (float) Math.sqrt((maxX - minX) * (maxX - minX) + (maxY - minY) * (maxY - minY) + (maxZ - minZ) * (maxZ - minZ)) / 2f;
    }

    /** Chunks the terrain has; the instances {@link #sync} returns are these first, then the water when there is some. */
    int chunkCount() {
        return chunks.length;
    }

    /** Centre of chunk {@code index}'s bounding sphere, in the terrain's own space, written to {@code out}. */
    Vector3 chunkCenter(int index, Vector3 out) {
        return out.set(chunks[index].center);
    }

    /** Radius of chunk {@code index}'s bounding sphere, in the terrain's own units. */
    float chunkRadius(int index) {
        return chunks[index].radius;
    }

    /** Triangles the chunks drew at their last sync: for tests and the renderer's statistics. */
    int triangleCount() {
        int total = 0;
        for (Chunk chunk : chunks) {
            if (chunk != null) {
                total += chunk.part.size / 3;
            }
        }
        return total;
    }

    /** The water quad, made the first time and moved when the level changes. */
    private ModelInstance water() {
        if (waterModel == null) {
            short[] indices = TerrainMeshBuilder.waterIndices();
            waterMesh = new Mesh(false, 4, indices.length,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE));
            waterMesh.setIndices(indices);
            Material material = new Material(
                PBRColorAttribute.createBaseColorFactor(WATER_COLOR),
                PBRFloatAttribute.createMetallic(0f),
                PBRFloatAttribute.createRoughness(WATER_ROUGHNESS),
                new BlendingAttribute(WATER_COLOR.a));
            MeshPart part = new MeshPart("water", waterMesh, 0, indices.length, GL20.GL_TRIANGLES);
            Node node = new Node();
            node.id = "water";
            node.parts.add(new NodePart(part, material));
            waterModel = new Model();
            waterModel.nodes.add(node);
            waterModel.meshes.add(waterMesh);
            waterModel.meshParts.add(part);
            waterModel.materials.add(material);
            waterModel.manageDisposable(waterMesh);
            waterInstance = new ModelInstance(waterModel);
        }
        if (Float.compare(waterBuiltAt, terrain.getWaterLevel()) != 0) {
            waterMesh.setVertices(TerrainMeshBuilder.waterVertices(terrain));
            waterBuiltAt = terrain.getWaterLevel();
        }
        return waterInstance;
    }

    private Chunk create(int cx, int cz) {
        int countX = TerrainMeshBuilder.vertexCountX(terrain, cx);
        int countZ = TerrainMeshBuilder.vertexCountZ(terrain, cz);
        short[] indices = TerrainMeshBuilder.indices(countX, countZ);  // the most a chunk can have; sync() sets the real ones
        Chunk chunk = new Chunk();
        // Room for the skirts' vertices and indices too (roadmap M3.8)
        chunk.mesh = new Mesh(false, TerrainMeshBuilder.vertexCount(countX, countZ), indices.length + TerrainMeshBuilder.maxSkirtIndices(),
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
        // The instance draws its own copy of the part, so that is the one whose index count follows the holes
        chunk.part = chunk.instance.nodes.get(0).parts.get(0).meshPart;
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
        if (waterModel != null) {
            owned.register(waterModel);
            waterModel = null;
            waterInstance = null;
            waterMesh = null;
        }
        instances.clear();
        owned.dispose();
    }
}
