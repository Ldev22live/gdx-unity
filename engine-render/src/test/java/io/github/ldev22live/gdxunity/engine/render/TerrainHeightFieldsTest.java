package io.github.ldev22live.gdxunity.engine.render;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightField3D;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainIO;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Terrain colliders read the heights the renderer draws (roadmap M3.3, TD-087), and say why when they cannot. */
class TerrainHeightFieldsTest {
    @TempDir
    Path project;

    private TerrainLibrary library;
    private TerrainHeightFields source;
    private Scene scene;

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(project.resolve("terrains"));
        Terrain terrain = new Terrain(4, 3, 50f);
        terrain.setHeight(1, 2, 120f);
        Files.writeString(project.resolve("terrains/small.terrain"), TerrainIO.toJson(terrain), StandardCharsets.UTF_8);
        library = new TerrainLibrary(AssetSource.directory(project.toFile()));
        source = new TerrainHeightFields(library);
        scene = new Scene("Ground");
    }

    private Entity entityWith(String path) {
        Entity entity = scene.createEntity("Ground");
        if (path != null) {
            entity.addComponent(new TerrainComponent(path));
        }
        return entity;
    }

    @Test
    void shouldReturnTheHeightsTheTerrainIsDrawnWith() {
        HeightField3D field = source.heightFieldOf(entityWith("terrains/small.terrain"));
        Terrain terrain = library.get("terrains/small.terrain");

        assertEquals(4, field.columns());
        assertEquals(3, field.rows());
        assertEquals(50f, field.cellSizeX());
        assertEquals(terrain.xAt(0), field.originX(), "centred on the entity, as drawn");
        assertEquals(terrain.zAt(0), field.originZ());
        assertEquals(120f, field.heightAt(1, 2));
        assertEquals(0f, field.heightAt(2, 1));
    }

    /** TD-089: a hole in the terrain is a hole in the collision, so a character falls through where nothing is drawn. */
    @Test
    void shouldMakeTheHolesOfTheTerrainHolesInTheHeightField() {
        Terrain terrain = library.get("terrains/small.terrain");
        terrain.setHole(2, 1, true);

        HeightField3D field = source.heightFieldOf(entityWith("terrains/small.terrain"));

        assertTrue(HeightField3D.isHole(field.heightAt(2, 1)), "the hole collides with nothing");
        assertEquals(120f, field.heightAt(1, 2), "the rest keeps its heights");
    }

    @Test
    void shouldChangeTheRevisionWhenTheTerrainIsEditedOrReloaded() {
        Entity ground = entityWith("terrains/small.terrain");
        long first = source.revisionOf(ground);
        assertEquals(first, source.revisionOf(ground), "unchanged terrain, unchanged revision");

        library.get("terrains/small.terrain").setHeight(0, 0, 10f);
        long edited = source.revisionOf(ground);
        assertNotEquals(first, edited, "a stroke changes it");

        library.reload("terrains/small.terrain");
        assertNotEquals(edited, source.revisionOf(ground), "a reload hands out a new terrain");
    }

    @Test
    void shouldSayWhyAnEntityHasNoGround() {
        assertTrue(assertThrows(IllegalStateException.class, () -> source.heightFieldOf(entityWith(null))).getMessage()
            .contains("Ground has a Terrain Collider 3D but no Terrain"));
        assertTrue(assertThrows(IllegalStateException.class, () -> source.heightFieldOf(entityWith(""))).getMessage()
            .contains("names no .terrain file"));
        String missing = assertThrows(IllegalStateException.class, () -> source.heightFieldOf(entityWith("terrains/gone.terrain")))
            .getMessage();
        assertTrue(missing.contains("cannot be read") && missing.contains("terrains/gone.terrain"), missing);
        assertEquals(TerrainHeightFields.NO_TERRAIN_REVISION, source.revisionOf(entityWith("terrains/gone.terrain")));
    }

    @Test
    void shouldBeGivenToTheSceneByTheRendererWithItsTerrainLibrary() {
        SceneRenderer3D renderer = new SceneRenderer3D();
        try {
            renderer.setActiveScene(scene);
            assertNull(scene.getHeightFields(), "no library, no terrain to collide with");
            renderer.setTerrainLibrary(library);
            assertTrue(scene.getHeightFields() instanceof TerrainHeightFields);
        } finally {
            renderer.dispose();
        }
    }
}
