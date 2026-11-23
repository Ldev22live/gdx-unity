package io.github.ldev22live.gdxunity.engine.terrain;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code .terrain} file, the shared library and the generators (TD-082). */
class TerrainFilesTest {
    @TempDir
    Path project;

    private TerrainLibrary library;

    @BeforeAll
    static void init() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(project.resolve("terrains"));
        library = new TerrainLibrary(AssetSource.directory(project.toFile()));
    }

    @Test
    void shouldRoundTripEveryHeightExactly() {
        Terrain terrain = TerrainGenerator.hills("Valley \"North\"", 33, 12.5f, 7L, 400f);

        Terrain read = TerrainIO.fromJson(TerrainIO.toJson(terrain));

        assertEquals("Valley \"North\"", read.getName());
        assertEquals(33, read.getResolutionX());
        assertEquals(12.5f, read.getCellSize());
        assertTrue(java.util.Arrays.equals(terrain.copyHeights(), read.copyHeights()), "heights are stored as exact 32-bit floats");
        assertEquals(TerrainIO.toJson(terrain), TerrainIO.toJson(read), "the same terrain gives the same text");
    }

    @Test
    void shouldRefuseFilesThatAreNotTerrainsNamingTheProblem() {
        assertThrows(TerrainFormatException.class, () -> TerrainIO.fromJson("not json"));
        assertThrows(TerrainFormatException.class, () -> TerrainIO.fromJson("[]"));
        assertTrue(assertThrows(TerrainFormatException.class, () -> TerrainIO.fromJson("{}")).getMessage().contains("no version"));
        assertTrue(assertThrows(TerrainFormatException.class, () -> TerrainIO.fromJson("{\"version\": 2}")).getMessage().contains("not supported"));
        assertTrue(assertThrows(TerrainFormatException.class,
            () -> TerrainIO.fromJson("{\"version\":1,\"resolutionX\":1,\"resolutionZ\":4,\"cellSize\":1,\"heights\":\"\"}")).getMessage().contains("Resolution"));
        assertTrue(assertThrows(TerrainFormatException.class,
            () -> TerrainIO.fromJson("{\"version\":1,\"resolutionX\":3,\"resolutionZ\":3,\"cellSize\":1,\"heights\":\"AAAA\"}")).getMessage().contains("bytes"));
        assertThrows(TerrainFormatException.class,
            () -> TerrainIO.fromJson("{\"version\":1,\"resolutionX\":3,\"resolutionZ\":3,\"cellSize\":1}"));
    }

    @Test
    void shouldRefuseAFileWhoseHeightsAreNotNumbers() {
        // 2 x 2 grid whose first float is NaN (0x7FC00000, little endian)
        String nan = java.util.Base64.getEncoder().encodeToString(new byte[]{0, 0, (byte) 0xC0, 0x7F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        assertTrue(assertThrows(TerrainFormatException.class,
            () -> TerrainIO.fromJson("{\"version\":1,\"resolutionX\":2,\"resolutionZ\":2,\"cellSize\":1,\"heights\":\"" + nan + "\"}"))
            .getMessage().contains("finite"));
    }

    @Test
    void shouldGiveEveryUserOfAFileTheSameTerrainAndSaveEdits() throws IOException {
        library.save("terrains/a.terrain", TerrainGenerator.flat("A", 5, 1f));

        Terrain first = library.get("terrains/a.terrain");
        Terrain second = library.get("terrains\\a.terrain");
        first.setHeight(2, 2, 9f);
        library.save("terrains/a.terrain", first);

        assertSame(first, second);
        assertEquals(9f, TerrainIO.fromJson(Files.readString(project.resolve("terrains/a.terrain"))).getHeight(2, 2));
    }

    @Test
    void shouldGiveNoTerrainAndAReasonForAMissingOrBrokenFile() throws IOException {
        Files.writeString(project.resolve("terrains/bad.terrain"), "{\"version\": 9}");

        assertNull(library.get("terrains/none.terrain"));
        assertTrue(library.getError("terrains/none.terrain").contains("not found"));
        assertNull(library.get("terrains/bad.terrain"));
        assertTrue(library.getError("terrains/bad.terrain").contains("not supported"));
        assertNull(library.getError("terrains/never-asked.terrain"));
    }

    @Test
    void shouldReloadAChangedFileIntoTheSharedTerrain() throws IOException {
        library.save("terrains/a.terrain", TerrainGenerator.flat("A", 5, 1f));
        Terrain shared = library.get("terrains/a.terrain");
        Terrain edited = TerrainGenerator.flat("A", 5, 1f);
        edited.setHeight(1, 1, 4f);
        Files.writeString(project.resolve("terrains/a.terrain"), TerrainIO.toJson(edited));

        library.reload("terrains/a.terrain");

        assertSame(shared, library.get("terrains/a.terrain"));
        assertEquals(4f, shared.getHeight(1, 1));

        Files.writeString(project.resolve("terrains/a.terrain"), TerrainIO.toJson(TerrainGenerator.flat("A", 9, 1f)));
        library.reload("terrains/a.terrain");
        assertEquals(9, library.get("terrains/a.terrain").getResolutionX(), "a different grid is read as a new terrain");
    }

    @Test
    void shouldMakeTheSameHillsForTheSameSeedAndDifferentOnesForAnother() {
        Terrain a = TerrainGenerator.hills("H", 33, 10f, 1L, 300f);
        Terrain same = TerrainGenerator.hills("H", 33, 10f, 1L, 300f);
        Terrain other = TerrainGenerator.hills("H", 33, 10f, 2L, 300f);

        assertTrue(java.util.Arrays.equals(a.copyHeights(), same.copyHeights()));
        assertTrue(!java.util.Arrays.equals(a.copyHeights(), other.copyHeights()));
        float[] range = a.heightRange();
        assertTrue(range[1] - range[0] > 30f, "hills have relief: " + range[0] + " to " + range[1]);
        assertTrue(range[1] <= 300f * 1.5f + 1f, "and stay within the amplitude");
        assertNotNull(TerrainGenerator.flat("F", 4, 1f));
    }
}
