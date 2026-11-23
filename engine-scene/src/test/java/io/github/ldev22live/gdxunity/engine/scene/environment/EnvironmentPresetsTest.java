package io.github.ldev22live.gdxunity.engine.scene.environment;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.environment.serialization.AtmosphereComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.environment.serialization.WaterBodyComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sky and Weather and Water Body presets, validation and files (roadmap M3.9, TD-090). */
class EnvironmentPresetsTest {

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new AtmosphereComponentSerializer());
        registry.register(new WaterBodyComponentSerializer());
        return new SceneSerializer(registry);
    }

    @Test
    void shouldStartAsAClearDay() {
        AtmosphereComponent sky = new AtmosphereComponent();
        assertEquals(AtmospherePreset.CLEAR_DAY, AtmospherePreset.matching(sky).orElseThrow());
        assertEquals(WaterPreset.LAKE, WaterPreset.matching(new WaterBodyComponent()).orElseThrow());
    }

    @Test
    void shouldGiveEveryPresetDistinctSettingsThatItAloneMatches() {
        for (AtmospherePreset preset : AtmospherePreset.values()) {
            AtmosphereComponent sky = new AtmosphereComponent();
            preset.applyTo(sky);
            assertEquals(preset, AtmospherePreset.matching(sky).orElseThrow(), preset.name());
        }
        for (WaterPreset preset : WaterPreset.values()) {
            WaterBodyComponent water = new WaterBodyComponent();
            preset.applyTo(water);
            assertEquals(preset, WaterPreset.matching(water).orElseThrow(), preset.name());
        }
    }

    @Test
    void shouldApplyEveryPresetSettingAndKeepTheEnabledState() {
        AtmosphereComponent sky = new AtmosphereComponent();
        sky.setEnabled(false);
        AtmospherePreset.RAIN.applyTo(sky);
        assertEquals(AtmosphereComponent.Weather.RAIN, sky.getWeather());
        assertEquals(0.7f, sky.getWeatherIntensity(), 1e-6f);
        assertEquals(0.4f, sky.getSunlight(), 1e-6f);
        assertTrue(sky.isFogEnabled());
        assertEquals(1000f, sky.getFogStart(), 1e-3f);
        assertFalse(sky.isEnabled());

        WaterBodyComponent water = new WaterBodyComponent();
        water.setSize(123f, 456f);
        WaterPreset.OCEAN.applyTo(water);
        assertEquals(30f, water.getWaveHeight(), 1e-6f);
        assertEquals(0.88f, water.getColor().a, 1e-6f);
        assertEquals(123f, water.getWidth(), 1e-6f, "a preset leaves the size alone");
        assertEquals(456f, water.getLength(), 1e-6f);
    }

    @Test
    void shouldReadATweakedPresetAsCustom() {
        AtmosphereComponent sky = new AtmosphereComponent();
        AtmospherePreset.SUNSET.applyTo(sky);
        sky.setSunlight(0.9f);
        assertTrue(AtmospherePreset.matching(sky).isEmpty());

        WaterBodyComponent water = new WaterBodyComponent();
        water.setRoughness(0.9f);
        assertTrue(WaterPreset.matching(water).isEmpty());
    }

    @Test
    void shouldFindPresetsByDisplayOrConstantName() {
        assertEquals(AtmospherePreset.CLEAR_DAY, AtmospherePreset.find("clear day").orElseThrow());
        assertEquals(AtmospherePreset.CLEAR_DAY, AtmospherePreset.find("CLEAR_DAY").orElseThrow());
        assertEquals(WaterPreset.TROPICAL, WaterPreset.find("Tropical").orElseThrow());
        assertTrue(AtmospherePreset.find("Hurricane").isEmpty());
        assertTrue(WaterPreset.find("Lava").isEmpty());
    }

    @Test
    void shouldRejectSettingsOutOfRange() {
        AtmosphereComponent sky = new AtmosphereComponent();
        assertThrows(IllegalArgumentException.class, () -> sky.setFogRange(500f, 500f));
        assertThrows(IllegalArgumentException.class, () -> sky.setFogRange(-1f, 500f));
        assertThrows(IllegalArgumentException.class, () -> sky.setWeatherIntensity(1.5f));
        assertThrows(IllegalArgumentException.class, () -> sky.setSunlight(-0.1f));
        assertThrows(IllegalArgumentException.class, () -> sky.setSunlight(Float.NaN));
        WaterBodyComponent water = new WaterBodyComponent();
        assertThrows(IllegalArgumentException.class, () -> water.setSize(0f, 10f));
        assertThrows(IllegalArgumentException.class, () -> water.setRoughness(2f));
        assertThrows(IllegalArgumentException.class, () -> water.setWaves(1f, 0f, 1f));
        assertThrows(IllegalArgumentException.class, () -> water.setWaves(-1f, 10f, 1f));
    }

    @Test
    void shouldCopySettingsIntoANewIndependentComponent() {
        AtmosphereComponent sky = new AtmosphereComponent();
        AtmospherePreset.NIGHT.applyTo(sky);
        AtmosphereComponent copy = sky.copy();
        assertTrue(copy.sameSettings(sky, 0f));
        copy.setSunlight(2f);
        assertNotEquals(copy.getSunlight(), sky.getSunlight());
    }

    @Test
    void shouldKeepAnAppliedPresetThroughSaveAndLoad() {
        Scene scene = new Scene("Weather");
        AtmosphereComponent sky = new AtmosphereComponent();
        AtmospherePreset.SNOW.applyTo(sky);
        scene.createEntity("Sky and Weather").addComponent(sky);
        WaterBodyComponent water = new WaterBodyComponent();
        water.setSize(2500f, 1800f);
        WaterPreset.SWAMP.applyTo(water);
        scene.createEntity("Pond").addComponent(water);

        SceneSerializer serializer = serializer();
        Scene restored = serializer.fromJson(serializer.toJson(scene));

        AtmosphereComponent skyCopy = restored.findEntityByName("Sky and Weather").getComponent(AtmosphereComponent.class);
        assertEquals(AtmospherePreset.SNOW, AtmospherePreset.matching(skyCopy).orElseThrow());
        assertTrue(skyCopy.sameSettings(sky, 1e-6f));
        WaterBodyComponent waterCopy = restored.findEntityByName("Pond").getComponent(WaterBodyComponent.class);
        assertEquals(WaterPreset.SWAMP, WaterPreset.matching(waterCopy).orElseThrow());
        assertEquals(2500f, waterCopy.getWidth(), 1e-3f);
        assertEquals(1800f, waterCopy.getLength(), 1e-3f);
        assertEquals(0.92f, waterCopy.getColor().a, 1e-6f);
    }

    @Test
    void shouldRefuseAFileWithAnUnknownWeatherOrBadOpacity() {
        SceneSerializer serializer = serializer();
        Scene scene = new Scene("Weather");
        scene.createEntity("Sky").addComponent(new AtmosphereComponent());
        String json = serializer.toJson(scene).replace("\"CLEAR\"", "\"HAIL\"");
        SceneSerializationException weather = assertThrows(SceneSerializationException.class, () -> serializer.fromJson(json));
        assertTrue(weather.getMessage().contains("HAIL"), weather.getMessage());

        Scene pond = new Scene("Pond");
        WaterBodyComponent water = new WaterBodyComponent();
        water.setColor(new Color(0.1f, 0.2f, 0.3f, 0.5f));
        pond.createEntity("Pond").addComponent(water);
        String bad = serializer.toJson(pond).replaceFirst("\"opacity\"\\s*:\\s*0\\.5", "\"opacity\":3");
        assertNotEquals(serializer.toJson(pond), bad, "the test must change the opacity");
        assertThrows(SceneSerializationException.class, () -> serializer.fromJson(bad));
    }
}
