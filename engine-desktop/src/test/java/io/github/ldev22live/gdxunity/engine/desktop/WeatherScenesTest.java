package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.TerrainHeightFields;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmosphereComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmospherePreset;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterBodyComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterPreset;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Roadmap M3.9: the shipped valley under four sky and water presets, written to build/m3-9-weather for the game runtime's
 * screenshots. Each scene is read back by the game runtime's own loader, which must find the same presets (the done-when "a preset
 * applies and persists"), and plays 30 frames without errors.
 */
class WeatherScenesTest {
    private static final Path OUT = Path.of("build", "m3-9-weather");
    private static final File SAMPLE = new File("../assets/samples/terrain-3d.json");
    private static final File VALLEY = new File("../assets/terrains/Valley.terrain");
    /** The camera stands where the sample's does, looking along the valley a little downwards, so the sky and fog show. */
    private static final float CAMERA_PITCH_DEGREES = -10f;
    /** A lake along the valley floor (about -20 to 30 units high), deep enough that its shore lies on the slopes. */
    private static final float LAKE_HEIGHT = 60f;
    private static final float LAKE_WIDTH = 2000f;
    private static final float LAKE_LENGTH = 4200f;

    private record Look(String name, AtmospherePreset sky, WaterPreset water) {}

    private static final List<Look> LOOKS = List.of(
        new Look("Sunset", AtmospherePreset.SUNSET, WaterPreset.LAKE),
        new Look("Rain", AtmospherePreset.RAIN, WaterPreset.OCEAN),
        new Look("Snow", AtmospherePreset.SNOW, WaterPreset.ICY),
        new Look("Foggy", AtmospherePreset.FOGGY, WaterPreset.SWAMP));

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldWriteAndPlayTheValleyUnderEachPreset() throws IOException {
        Files.createDirectories(OUT.resolve("terrains"));
        Files.createDirectories(OUT.resolve("scenes"));
        Files.copy(VALLEY.toPath(), OUT.resolve("terrains/Valley.terrain"), StandardCopyOption.REPLACE_EXISTING);
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        SceneSerializer serializer = new SceneSerializer(registry);
        AssetSource project = AssetSource.directory(OUT.toFile());

        for (Look look : LOOKS) {
            Scene scene = GameSession.loadScene(SAMPLE);
            scene.setName("Valley " + look.name());
            Entity camera = scene.findEntityByName("Main Camera");
            camera.removeComponent(camera.getComponent(CameraRigComponent.class)); // a fixed view for the picture
            camera.getTransform().setLocalEulerAngles(0f, CAMERA_PITCH_DEGREES, 0f);
            Entity hero = scene.findEntityByName("Hero");
            hero.removeComponent(hero.getComponent(GraphScriptComponent.class)); // the graph is not part of this project
            AtmosphereComponent sky = new AtmosphereComponent();
            look.sky().applyTo(sky);
            scene.createEntity("Sky and Weather").addComponent(sky);
            WaterBodyComponent water = new WaterBodyComponent();
            water.setSize(LAKE_WIDTH, LAKE_LENGTH);
            look.water().applyTo(water);
            Entity lake = scene.createEntity("Lake");
            lake.getTransform().setLocalPosition(0f, LAKE_HEIGHT, 0f);
            lake.addComponent(water);
            String path = "scenes/" + look.name() + ".json";
            Files.writeString(OUT.resolve(path), serializer.toJson(scene), StandardCharsets.UTF_8);

            Scene loaded = GameSession.loadScene(project, path);
            AtmosphereComponent loadedSky = SceneRenderer3D.findAtmosphere(loaded);
            assertNotNull(loadedSky, look.name());
            assertEquals(look.sky(), AtmospherePreset.matching(loadedSky).orElseThrow(), look.name());
            WaterBodyComponent loadedWater = loaded.findEntityByName("Lake").getComponent(WaterBodyComponent.class);
            assertEquals(look.water(), WaterPreset.matching(loadedWater).orElseThrow(), look.name());

            loaded.setHeightFields(new TerrainHeightFields(new TerrainLibrary(project)));
            GameSession session = new GameSession(project, loaded, new ScriptRegistry(), GameSession.CONSOLE);
            try {
                assertEquals(List.of(), session.start(), look.name());
                for (int i = 0; i < 30; i++) {
                    session.update(1f / 60f);
                }
                assertEquals(List.of(), session.getRuntimeErrors(), look.name());
            } finally {
                session.dispose();
            }
        }
        Files.writeString(OUT.resolve("gdxunity.project"), "format=1\nname=Weather\nstartScene=scenes/Sunset.json\n",
            StandardCharsets.UTF_8);
    }
}
