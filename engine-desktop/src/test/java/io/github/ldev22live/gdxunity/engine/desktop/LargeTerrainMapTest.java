package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.SkyLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainGenerator;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainIO;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Roadmap M3.8: the large map the level-of-detail milestone is measured on. A 1025 by 1025 terrain (the largest, a million points,
 * 10 units apart: 10 km across) of hills under a camera looking across it from one edge, written to build/m3-8-large-map for the game
 * runtime's timed runs ({@code GDXUNITY_RENDER_STATS=1}, with {@code GDXUNITY_TERRAIN_LOD=0} for the comparison). The test checks it
 * plays in the game runtime's session.
 */
class LargeTerrainMapTest {
    private static final Path OUT = Path.of("build", "m3-8-large-map");
    private static final String TERRAIN = "terrains/Large.terrain";
    private static final String SCENE = "scenes/Large.json";
    /** The largest map a terrain can be, in grid points per side. */
    private static final int RESOLUTION = Terrain.MAX_RESOLUTION;
    private static final float CELL = 10f;
    private static final float HILL_HEIGHT = 600f;
    /** The camera stands at the near edge, above the hills, looking across the map and a little down. */
    private static final float CAMERA_HEIGHT = 1100f;
    private static final float CAMERA_PITCH_DEGREES = -12f;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldWriteAndPlayTheLargestMap() throws IOException {
        Terrain terrain = TerrainGenerator.hills("Large", RESOLUTION, CELL, 11L, HILL_HEIGHT);
        assertEquals(32 * 32, terrain.getChunksX() * terrain.getChunksZ(), "a thousand chunks");
        Files.createDirectories(OUT.resolve("terrains"));
        Files.createDirectories(OUT.resolve("scenes"));
        Files.writeString(OUT.resolve(TERRAIN), TerrainIO.toJson(terrain), StandardCharsets.UTF_8);

        Scene scene = new Scene("Large");
        scene.setGameMode(GameMode.MODE_3D);
        Entity camera = scene.createEntity("Main Camera");
        camera.getTransform().setLocalPosition(0f, CAMERA_HEIGHT, terrain.getSizeZ() / 2f);
        camera.getTransform().setLocalEulerAngles(0f, CAMERA_PITCH_DEGREES, 0f);
        camera.addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        scene.createEntity("Sun").addComponent(new DirectionalLightComponent());
        scene.createEntity("Sky Light").addComponent(new SkyLightComponent());
        scene.createEntity("Ground").addComponent(new TerrainComponent(TERRAIN));
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        Files.writeString(OUT.resolve(SCENE), new SceneSerializer(registry).toJson(scene), StandardCharsets.UTF_8);
        Files.writeString(OUT.resolve("gdxunity.project"), "format=1\nname=Large\nstartScene=" + SCENE + "\n", StandardCharsets.UTF_8);

        AssetSource project = AssetSource.directory(OUT.toFile());
        Scene loaded = GameSession.loadScene(project, SCENE);
        loaded.setHeightFields(new TerrainHeightFields(new TerrainLibrary(project)));
        GameSession session = new GameSession(project, loaded, new ScriptRegistry(), GameSession.CONSOLE);
        try {
            assertEquals(List.of(), session.start());
            for (int i = 0; i < 30; i++) {
                session.update(1f / 60f);
            }
            assertEquals(List.of(), session.getRuntimeErrors());
        } finally {
            session.dispose();
        }
    }
}
