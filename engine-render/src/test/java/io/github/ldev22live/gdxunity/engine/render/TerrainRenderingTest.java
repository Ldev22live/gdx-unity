package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.TerrainComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Terrain (TD-082) in the 3D renderer: chunk geometry and colours, rebuilding only what changed, collecting components, the file entry. */
class TerrainRenderingTest {
    private static final int FLOATS = TerrainMeshBuilder.FLOATS_PER_VERTEX;

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
        GltfTestFiles.installStubs();
    }

    @AfterAll
    static void tearDown() {
        GltfTestFiles.removeStubs();
    }

    @Test
    void shouldCutTheGridIntoChunksThatShareTheirBorderVertices() {
        Terrain terrain = new Terrain(Terrain.CHUNK_CELLS * 2 + 11, Terrain.CHUNK_CELLS + 1, 5f);

        assertEquals(33, TerrainMeshBuilder.vertexCountX(terrain, 0));
        assertEquals(33, TerrainMeshBuilder.vertexCountX(terrain, 1));
        assertEquals(11, TerrainMeshBuilder.vertexCountX(terrain, 2), "the last chunk is short: vertices 64 to 74");
        assertEquals(33, TerrainMeshBuilder.vertexCountZ(terrain, 0));
        float[] first = new float[33 * 33 * FLOATS];
        float[] second = new float[33 * 33 * FLOATS];
        terrain.setHeight(Terrain.CHUNK_CELLS, 3, 7f);
        new TerrainMeshBuilder().vertices(terrain, 0, 0, 0f, 10f, first);
        new TerrainMeshBuilder().vertices(terrain, 1, 0, 0f, 10f, second);
        int lastOfFirst = (3 * 33 + 32) * FLOATS;
        int firstOfSecond = 3 * 33 * FLOATS;
        assertEquals(first[lastOfFirst], second[firstOfSecond], "same x");
        assertEquals(7f, first[lastOfFirst + 1]);
        assertEquals(7f, second[firstOfSecond + 1], "the border point is in both chunks at the same height");
        assertEquals(first[lastOfFirst + 4], second[firstOfSecond + 4], 1e-6, "and has the same normal, so there is no seam");
    }

    @Test
    void shouldWindTheTrianglesSoTheGroundFacesUp() {
        Terrain terrain = new Terrain(3, 3, 10f);
        float[] vertices = new float[9 * FLOATS];
        new TerrainMeshBuilder().vertices(terrain, 0, 0, 0f, 1f, vertices);
        short[] indices = TerrainMeshBuilder.indices(3, 3);

        assertEquals(2 * 2 * 6, indices.length);
        for (int t = 0; t < indices.length; t += 3) {
            Vector3 a = position(vertices, indices[t]);
            Vector3 b = position(vertices, indices[t + 1]);
            Vector3 c = position(vertices, indices[t + 2]);
            Vector3 normal = new Vector3(b).sub(a).crs(new Vector3(c).sub(a));
            assertTrue(normal.y > 0f, "triangle " + t / 3 + " faces up");
        }
    }

    private static Vector3 position(float[] vertices, int index) {
        return new Vector3(vertices[index * FLOATS], vertices[index * FLOATS + 1], vertices[index * FLOATS + 2]);
    }

    @Test
    void shouldColourGentleGroundGreenSteepGroundGreyAndTheHighestGroundWhite() {
        float[] flatLow = new float[4];
        float[] steep = new float[4];
        float[] flatHigh = new float[4];
        TerrainMeshBuilder.color(1f, 0.2f, flatLow, 0);
        TerrainMeshBuilder.color(0.3f, 0.2f, steep, 0);
        TerrainMeshBuilder.color(1f, 1f, flatHigh, 0);

        assertTrue(flatLow[1] > flatLow[0] && flatLow[1] > flatLow[2], "grass is green");
        assertTrue(Math.abs(steep[0] - steep[1]) < 0.05f && steep[0] > flatLow[0], "rock is grey");
        assertTrue(flatHigh[0] > 0.7f && flatHigh[1] > 0.7f && flatHigh[2] > 0.7f, "snow is near white");
        assertEquals(1f, flatLow[3]);
        assertEquals(Math.pow(0.28, 2.2), flatLow[0], 1e-4, "colours are in linear light");
    }

    @Test
    void shouldColourEachChosenSurfaceByItsOwnGroundAndLetOnlyAutoTurnToSnowWithHeight() {
        float[] sand = new float[4];
        float[] gravel = new float[4];
        float[] snow = new float[4];
        float[] grass = new float[4];
        float[] sandHigh = new float[4];
        float[] sandSteep = new float[4];
        TerrainMeshBuilder.color(Surface.SAND, 1f, 0.2f, sand, 0);
        TerrainMeshBuilder.color(Surface.GRAVEL, 1f, 0.2f, gravel, 0);
        TerrainMeshBuilder.color(Surface.SNOW, 1f, 0.2f, snow, 0);
        TerrainMeshBuilder.color(Surface.GRASS, 1f, 0.2f, grass, 0);
        TerrainMeshBuilder.color(Surface.SAND, 1f, 1f, sandHigh, 0);
        TerrainMeshBuilder.color(Surface.SAND, 0.3f, 0.2f, sandSteep, 0);

        assertTrue(sand[0] > sand[2] * 1.5f && sand[1] > sand[2], "sand is yellow-brown");
        assertTrue(Math.abs(gravel[0] - gravel[2]) < 0.05f, "gravel is grey");
        assertTrue(snow[0] > 0.7f && snow[2] > 0.7f, "snow is white at any height");
        assertTrue(grass[1] > grass[0] && grass[1] > grass[2], "grass is green");
        assertEquals(sand[0], sandHigh[0], 1e-6, "a chosen surface does not change with height");
        assertTrue(sandSteep[0] < sand[0], "steep sand shows some rock");
        assertEquals(Math.pow(0.80, 2.2), sand[0], 1e-4, "sand is in linear light");
    }

    @Test
    void shouldGiveTheVerticesOfAChunkTheColourOfTheirSurface() {
        Terrain terrain = new Terrain(9, 9, 10f);
        terrain.fillSurface(Surface.SAND);
        terrain.setSurface(2, 2, Surface.SNOW);
        float[] out = new float[FLOATS * 81];

        new TerrainMeshBuilder().vertices(terrain, 0, 0, 0f, 1f, out);

        int snowAt = (2 * 9 + 2) * FLOATS + 6;
        int sandAt = (4 * 9 + 4) * FLOATS + 6;
        assertTrue(out[snowAt + 2] > 0.7f, "the snow point is white: " + out[snowAt + 2]);
        assertTrue(out[sandAt] > out[sandAt + 2] * 1.5f, "the rest is sand");
    }

    @Test
    void shouldMakeOneInstancePerChunkAndRebuildOnlyTheChunksAHeightChangeTouches() {
        int resolution = Terrain.CHUNK_CELLS * 2 + 1;
        Terrain terrain = new Terrain(resolution, resolution, 10f);
        TerrainGpu gpu = new TerrainGpu(terrain);

        List<ModelInstance> instances = gpu.sync();
        assertEquals(4, instances.size());
        Mesh farChunk = instances.get(3).model.meshes.first();
        float[] before = vertices(farChunk);
        float[] firstBefore = vertices(instances.get(0).model.meshes.first());
        assertEquals(0f, firstBefore[1], "flat ground");

        terrain.setHeight(5, 5, 4f);
        gpu.sync();

        assertEquals(4f, vertices(instances.get(0).model.meshes.first())[(5 * 33 + 5) * FLOATS + 1], "the chunk with the change was rebuilt");
        assertTrue(java.util.Arrays.equals(before, vertices(farChunk)), "a chunk far from the change is as it was");
        gpu.dispose();
        gpu.dispose();
    }

    private static float[] vertices(Mesh mesh) {
        float[] out = new float[mesh.getNumVertices() * FLOATS];
        mesh.getVertices(out);
        return out;
    }

    @Test
    void shouldCollectTheEnabledTerrainsOfActiveEntitiesWithTheirTransforms() {
        Scene scene = new Scene("S");
        Entity ground = scene.createEntity("Ground");
        TerrainComponent terrain = new TerrainComponent("terrains/a.terrain");
        ground.addComponent(terrain);
        ground.getTransform().setLocalPosition(10f, 20f, 30f);
        Entity off = scene.createEntity("Off");
        TerrainComponent disabled = new TerrainComponent("terrains/b.terrain");
        disabled.setEnabled(false);
        off.addComponent(disabled);

        List<SceneRenderer3D.TerrainDraw> draws = SceneRenderer3D.collectTerrains(scene);

        assertEquals(1, draws.size());
        assertEquals(terrain, draws.get(0).component());
        assertEquals(20f, draws.get(0).transform().getTranslation(new Vector3()).y);
        assertEquals(List.of(), SceneRenderer3D.collectTerrains(null));
    }

    @Test
    void shouldSaveThePathOfTheTerrainFileAndReadItBack() {
        TerrainComponentSerializer serializer = new TerrainComponentSerializer();
        Json json = new Json();
        java.io.StringWriter text = new java.io.StringWriter();
        json.setWriter(new com.badlogic.gdx.utils.JsonWriter(text));
        json.writeObjectStart();
        serializer.serialize(new TerrainComponent("  terrains/Valley.terrain "), json);
        json.writeObjectEnd();
        JsonValue data = new JsonReader().parse(text.toString());

        TerrainComponent read = serializer.deserialize(data, json);

        assertEquals("terrains/Valley.terrain", read.getTerrainPath());
        assertEquals("", serializer.deserialize(new JsonReader().parse("{}"), json).getTerrainPath());
        assertNotEquals(null, TerrainComponentSerializer.TYPE_ID);
        assertEquals("Terrain", TerrainComponentSerializer.TYPE_ID);
    }
}
