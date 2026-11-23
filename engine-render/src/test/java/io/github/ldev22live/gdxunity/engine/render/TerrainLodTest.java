package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap M3.8: terrain chunks far from the camera are drawn with fewer triangles, with skirts so no gap shows between levels. */
class TerrainLodTest {
    private static final int FLOATS = TerrainMeshBuilder.FLOATS_PER_VERTEX;

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
        GltfTestFiles.installStubs(); // the GL stand-ins TerrainGpu's meshes need, as in TerrainRenderingTest
    }

    @AfterAll
    static void tearDown() {
        GltfTestFiles.removeStubs();
    }

    @Test
    void shouldKeepNearChunksFullAndHalveDetailWithEachDoublingOfDistance() {
        float chunk = 320f;
        assertEquals(0, TerrainLod.levelFor(0f, chunk));
        assertEquals(0, TerrainLod.levelFor(TerrainLod.FULL_DETAIL_CHUNKS * chunk, chunk), "full detail up to two chunk widths");
        assertEquals(1, TerrainLod.levelFor(TerrainLod.FULL_DETAIL_CHUNKS * chunk * 1.5f, chunk));
        assertEquals(2, TerrainLod.levelFor(TerrainLod.FULL_DETAIL_CHUNKS * chunk * 3f, chunk));
        assertEquals(3, TerrainLod.levelFor(TerrainLod.FULL_DETAIL_CHUNKS * chunk * 5f, chunk));
        assertEquals(TerrainLod.MAX_LEVEL, TerrainLod.levelFor(1e9f, chunk), "never coarser than every 8th point");
        assertEquals(8, TerrainLod.step(3));
        assertThrows(IllegalArgumentException.class, () -> TerrainLod.step(4));
    }

    @Test
    void shouldDrawAQuarterOfTheTrianglesPerLevelAndKeepShortChunksWhole() {
        Terrain terrain = new Terrain(Terrain.CHUNK_CELLS + 11, Terrain.CHUNK_CELLS + 1, 10f);
        short[] out = new short[Terrain.CHUNK_CELLS * Terrain.CHUNK_CELLS * 6];

        assertEquals(32 * 32 * 6, TerrainMeshBuilder.indices(terrain, 0, 0, out, 1));
        assertEquals(16 * 16 * 6, TerrainMeshBuilder.indices(terrain, 0, 0, out, 2));
        assertEquals(4 * 4 * 6, TerrainMeshBuilder.indices(terrain, 0, 0, out, 8));
        // The second chunk is 10 cells wide: at every 8th point it is a block of 8 and a block of 2, so it still reaches its edge
        assertEquals(2 * 4 * 6, TerrainMeshBuilder.indices(terrain, 1, 0, out, 8));
        int last = 0;
        for (int i = 0; i < 2 * 4 * 6; i++) {
            last = Math.max(last, out[i] % TerrainMeshBuilder.vertexCountX(terrain, 1));
        }
        assertEquals(10, last, "the coarse chunk still uses its last column");
    }

    @Test
    void shouldLeaveOutABlockWithAHoleAtACoarseLevel() {
        Terrain terrain = new Terrain(Terrain.CHUNK_CELLS + 1, Terrain.CHUNK_CELLS + 1, 10f);
        terrain.setHole(3, 3, true);
        short[] out = new short[Terrain.CHUNK_CELLS * Terrain.CHUNK_CELLS * 6];

        assertEquals((16 - 1) * 6, TerrainMeshBuilder.indices(terrain, 0, 0, out, 8), "the 8 by 8 block holding the hole is open");
    }

    @Test
    void shouldHangSkirtsBelowTheWholeBorderDeepEnoughForAnyGap() {
        Terrain terrain = TerrainGenerator.hills("Hills", Terrain.CHUNK_CELLS + 1, 10f, 7L, 200f);
        int countX = TerrainMeshBuilder.vertexCountX(terrain, 0);
        int countZ = TerrainMeshBuilder.vertexCountZ(terrain, 0);
        float[] vertices = new float[TerrainMeshBuilder.vertexCount(countX, countZ) * FLOATS];
        int written = new TerrainMeshBuilder().vertices(terrain, 0, 0, 0f, 200f, vertices);
        assertEquals(TerrainMeshBuilder.vertexCount(countX, countZ) * FLOATS, written);

        float lowestBorder = Float.MAX_VALUE;
        for (int x = 0; x < countX; x++) {
            lowestBorder = Math.min(lowestBorder, Math.min(terrain.getHeight(x, 0), terrain.getHeight(x, countZ - 1)));
        }
        for (int z = 0; z < countZ; z++) {
            lowestBorder = Math.min(lowestBorder, Math.min(terrain.getHeight(0, z), terrain.getHeight(countX - 1, z)));
        }
        for (int v = countX * countZ; v < TerrainMeshBuilder.vertexCount(countX, countZ); v++) {
            assertTrue(vertices[v * FLOATS + 1] < lowestBorder, "skirt vertex " + v + " is below every border point");
        }
        short[] out = new short[TerrainMeshBuilder.maxSkirtIndices()];
        assertEquals(4 * 16 * 12, TerrainMeshBuilder.skirtIndices(terrain, 0, 0, out, 0, 2), "four edges, a segment per two cells");
    }

    @Test
    void shouldDrawFarChunksWithFewerTrianglesAndFullDetailWithoutACamera() {
        // 8 by 8 chunks of 32 cells, 40 apart: a chunk is 1280 wide
        Terrain terrain = TerrainGenerator.hills("Large", 8 * Terrain.CHUNK_CELLS + 1, 40f, 3L, 300f);
        TerrainGpu gpu = new TerrainGpu(terrain);

        gpu.sync();
        int full = gpu.triangleCount();
        assertEquals(64 * 32 * 32 * 2, full, "without a camera every chunk is whole, with no skirts");

        Vector3 corner = new Vector3(terrain.xAt(0), 500f, terrain.zAt(0));
        gpu.sync(corner);
        int fromCorner = gpu.triangleCount();
        assertTrue(fromCorner < full / 3, "from a corner most chunks are far: " + fromCorner + " of " + full);

        Vector3 above = new Vector3(0f, 100_000f, 0f);
        gpu.sync(above);
        int farAway = gpu.triangleCount();
        assertTrue(farAway < fromCorner, "from far above every chunk is coarse: " + farAway);
        assertTrue(farAway > 0);
        gpu.dispose();
    }
}
