package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmosphereComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmospherePreset;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterBodyComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterPreset;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import net.mgsx.gltf.scene3d.attributes.FogAttribute;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sky, fog, sunlight, weather and water drawing for roadmap M3.9 (TD-090), checked without a graphics context. */
class AtmosphereRenderingTest {

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    private static SceneRenderer3D headlessRenderer(Scene scene) {
        SceneRenderer3D renderer = new SceneRenderer3D(new PerspectiveCamera3D(60f, 800f, 600f, 0.1f, 100f), scene) {
            @Override
            protected ModelBatch createModelBatch() {
                return null;
            }
        };
        renderer.initialize();
        return renderer;
    }

    // ---- fog and sunlight through the environment ----------------------------------------------------------------------

    @Test
    void shouldSetTheFogColourAndRangeFromTheSkyAndWeather() {
        Scene scene = new Scene("Fog");
        AtmosphereComponent sky = new AtmosphereComponent();
        AtmospherePreset.FOGGY.applyTo(sky);
        scene.createEntity("Sky and Weather").addComponent(sky);
        SceneRenderer3D renderer = headlessRenderer(scene);

        renderer.updateEnvironmentLights();

        ColorAttribute fog = renderer.getEnvironment().get(ColorAttribute.class, ColorAttribute.Fog);
        assertNotNull(fog, "fog colour is set");
        assertEquals(sky.getFogColor().r, fog.color.r, 1e-6f, "sRGB as authored: the shader fogs after gamma");
        FogAttribute range = renderer.getEnvironment().get(FogAttribute.class, FogAttribute.FogEquation);
        assertNotNull(range);
        assertEquals(200f, range.value.x, 1e-4f);
        assertEquals(4000f, range.value.y, 1e-4f);
        assertSame(sky, renderer.getAtmosphere());
        renderer.dispose();
    }

    @Test
    void shouldLeaveFogOutWhenItIsOffOrThereIsNoSky() {
        Scene scene = new Scene("Clear");
        AtmosphereComponent sky = new AtmosphereComponent();
        sky.setFogEnabled(false);
        scene.createEntity("Sky and Weather").addComponent(sky);
        SceneRenderer3D renderer = headlessRenderer(scene);
        renderer.updateEnvironmentLights();
        assertFalse(renderer.getEnvironment().has(ColorAttribute.Fog));
        renderer.dispose();

        SceneRenderer3D plain = headlessRenderer(new Scene("None"));
        plain.updateEnvironmentLights();
        assertFalse(plain.getEnvironment().has(ColorAttribute.Fog));
        assertNull(plain.getAtmosphere());
        plain.dispose();
    }

    @Test
    void shouldDimEveryDirectionalLightBySunlight() {
        Scene scene = new Scene("Night");
        DirectionalLightComponent sun = new DirectionalLightComponent();
        sun.setColor(new Color(1f, 1f, 1f, 1f));
        scene.createEntity("Sun").addComponent(sun);
        AtmosphereComponent sky = new AtmosphereComponent();
        AtmospherePreset.NIGHT.applyTo(sky);
        scene.createEntity("Sky and Weather").addComponent(sky);
        SceneRenderer3D renderer = headlessRenderer(scene);

        renderer.updateEnvironmentLights();
        DirectionalLight light = renderer.getEnvironment()
            .get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type).lights.get(0);
        assertEquals(0.15f * SceneRenderer3D.PBR_DIRECT_LIGHT_SCALE, light.color.r, 1e-4f);

        sky.setEnabled(false);
        renderer.updateEnvironmentLights();
        assertEquals(SceneRenderer3D.PBR_DIRECT_LIGHT_SCALE, light.color.r, 1e-4f, "a disabled sky leaves the sun alone");
        renderer.dispose();
    }

    @Test
    void shouldUseOnlyTheFirstEnabledSkyAndCollectOnlyEnabledWater() {
        Scene scene = new Scene("Two skies");
        AtmosphereComponent off = new AtmosphereComponent();
        off.setEnabled(false);
        scene.createEntity("Off").addComponent(off);
        AtmosphereComponent first = new AtmosphereComponent();
        scene.createEntity("First").addComponent(first);
        scene.createEntity("Second").addComponent(new AtmosphereComponent());
        assertSame(first, SceneRenderer3D.findAtmosphere(scene));

        scene.createEntity("Lake").addComponent(new WaterBodyComponent());
        WaterBodyComponent hidden = new WaterBodyComponent();
        hidden.setEnabled(false);
        scene.createEntity("Hidden").addComponent(hidden);
        assertEquals(1, SceneRenderer3D.collectWaterBodies(scene).size());
    }

    // ---- the sky gradient -------------------------------------------------------------------------------------------

    @Test
    void shouldPutTheHorizonInTheMiddleOfALevelView() {
        SkyGradient gradient = new SkyGradient();
        Color horizon = new Color(1f, 1f, 1f, 1f);
        Color zenith = new Color(0f, 0f, 1f, 1f);
        gradient.compute(0f, 60f, true, horizon, zenith);
        assertEquals(0.5f, gradient.horizonFraction, 1e-5f);
        assertEquals(horizon.r, gradient.lowColor.r, 1e-6f, "the view's lower half is the horizon colour");
        assertEquals(1f - (float) Math.sin(Math.toRadians(30)), gradient.topColor.r, 1e-3f, "30 degrees up at the top edge");
    }

    @Test
    void shouldMoveTheHorizonDownWhenLookingUpAndOffTheTopWhenLookingDown() {
        SkyGradient gradient = new SkyGradient();
        Color horizon = new Color(1f, 1f, 1f, 1f);
        Color zenith = new Color(0f, 0f, 1f, 1f);
        gradient.compute(30f, 60f, true, horizon, zenith);
        assertEquals(0f, gradient.horizonFraction, 1e-5f, "pitched up by half the view: the horizon is the bottom edge");
        gradient.compute(60f, 60f, true, horizon, zenith);
        assertTrue(gradient.horizonFraction < 0f);
        assertTrue(gradient.lowColor.r < 1f, "the bottom edge already shows sky above the horizon");
        gradient.compute(-45f, 60f, true, horizon, zenith);
        assertTrue(gradient.horizonFraction > 1f, "looking down: no sky above the horizon is in view");
    }

    @Test
    void shouldShowTheWholeGradientInAnOrthographicView() {
        SkyGradient gradient = new SkyGradient();
        Color horizon = new Color(1f, 0.5f, 0f, 1f);
        Color zenith = new Color(0f, 0f, 1f, 1f);
        gradient.compute(-20f, 0f, false, horizon, zenith);
        assertEquals(0f, gradient.horizonFraction);
        assertEquals(horizon, gradient.lowColor);
        assertEquals(zenith, gradient.topColor);
    }

    // ---- rain and snow ------------------------------------------------------------------------------------------------

    @Test
    void shouldFallAsManyParticlesAsTheIntensityAsksAndNoneWhenClear() {
        WeatherParticles particles = new WeatherParticles(1L);
        Vector3 camera = new Vector3(0f, 500f, 0f);
        particles.update(0.016f, camera, AtmosphereComponent.Weather.RAIN, 0.5f);
        assertEquals(WeatherParticles.MAX_PARTICLES / 2, particles.count());
        particles.update(0.016f, camera, AtmosphereComponent.Weather.CLEAR, 1f);
        assertEquals(0, particles.count());
    }

    @Test
    void shouldFallRainFasterThanSnow() {
        Vector3 camera = new Vector3(0f, 0f, 0f);
        WeatherParticles rain = new WeatherParticles(7L);
        WeatherParticles snow = new WeatherParticles(7L);
        rain.update(0f, camera, AtmosphereComponent.Weather.RAIN, 1f);
        snow.update(0f, camera, AtmosphereComponent.Weather.SNOW, 1f);
        float rainStart = rain.y(0);
        float snowStart = snow.y(0);
        rain.update(0.1f, camera, AtmosphereComponent.Weather.RAIN, 1f);
        snow.update(0.1f, camera, AtmosphereComponent.Weather.SNOW, 1f);
        float rainFall = rainStart - rain.y(0);
        float snowFall = snowStart - snow.y(0);
        assertEquals(WeatherParticles.RAIN_SPEED * 0.1f, rainFall, 1e-2f);
        assertEquals(WeatherParticles.SNOW_SPEED * 0.1f, snowFall, 1e-2f);
    }

    @Test
    void shouldKeepTheWeatherAroundTheCameraWhenItMoves() {
        WeatherParticles particles = new WeatherParticles(3L);
        Vector3 camera = new Vector3(0f, 0f, 0f);
        particles.update(0.016f, camera, AtmosphereComponent.Weather.SNOW, 1f);
        camera.set(25_000f, 3_000f, -40_000f);
        for (int frame = 0; frame < 120; frame++) {
            particles.update(0.016f, camera, AtmosphereComponent.Weather.SNOW, 1f);
        }
        for (int i = 0; i < particles.count(); i++) {
            assertTrue(Math.abs(particles.x(i) - camera.x) <= WeatherParticles.HALF_EXTENT + 1e-2f, "x of " + i);
            assertTrue(Math.abs(particles.z(i) - camera.z) <= WeatherParticles.HALF_EXTENT + 1e-2f, "z of " + i);
            assertTrue(particles.y(i) >= camera.y - WeatherParticles.BOTTOM - 1e-2f
                && particles.y(i) <= camera.y + WeatherParticles.TOP + 1e-2f, "y of " + i);
        }
    }

    @Test
    void shouldWrapIntoTheBoxAroundTheCentre() {
        float size = WeatherParticles.HALF_EXTENT * 2f;
        assertEquals(100f, WeatherParticles.wrap(100f + 3 * size, 0f), 1e-2f);
        assertEquals(-100f, WeatherParticles.wrap(-100f - 5 * size, 0f), 1e-2f);
        assertEquals(1000f, WeatherParticles.wrap(1000f, 900f), 1e-3f);
    }

    // ---- water surface --------------------------------------------------------------------------------------------------

    @Test
    void shouldBuildAFlatGridFacingUpForStillWater() {
        WaterBodyComponent water = new WaterBodyComponent();
        WaterPreset.ICY.applyTo(water);
        water.setSize(1000f, 600f);
        float[] vertices = new float[WaterSurfaceBuilder.vertexCount() * WaterSurfaceBuilder.VERTEX_SIZE];
        WaterSurfaceBuilder.fill(water, 5f, vertices);
        assertFalse(WaterSurfaceBuilder.isAnimated(water));
        for (int v = 0; v < WaterSurfaceBuilder.vertexCount(); v++) {
            int at = v * WaterSurfaceBuilder.VERTEX_SIZE;
            assertEquals(0f, vertices[at + 1], 0f);
            assertEquals(1f, vertices[at + 4], 1e-6f);
        }
        assertEquals(-500f, vertices[0], 1e-3f, "first corner");
        assertEquals(-300f, vertices[2], 1e-3f);
        int last = (WaterSurfaceBuilder.vertexCount() - 1) * WaterSurfaceBuilder.VERTEX_SIZE;
        assertEquals(500f, vertices[last], 1e-3f, "last corner");
        assertEquals(300f, vertices[last + 2], 1e-3f);
        for (short index : WaterSurfaceBuilder.indices()) {
            assertTrue(index >= 0 && index < WaterSurfaceBuilder.vertexCount());
        }
    }

    @Test
    void shouldMoveWavesOverTimeWithinTheirHeightAndMatchingNormals() {
        WaterBodyComponent water = new WaterBodyComponent();
        WaterPreset.OCEAN.applyTo(water);
        water.setSize(20_000f, 20_000f);
        assertTrue(WaterSurfaceBuilder.isAnimated(water));
        float amplitude = WaterSurfaceBuilder.shownWaveHeight(water);
        assertEquals(water.getWaveHeight(), amplitude, 1e-6f, "2400-unit waves fit a 20000-unit grid of 64 cells");

        float[] early = new float[WaterSurfaceBuilder.vertexCount() * WaterSurfaceBuilder.VERTEX_SIZE];
        float[] later = new float[early.length];
        WaterSurfaceBuilder.fill(water, 0f, early);
        WaterSurfaceBuilder.fill(water, 1.5f, later);
        boolean moved = false;
        for (int v = 0; v < WaterSurfaceBuilder.vertexCount(); v++) {
            int at = v * WaterSurfaceBuilder.VERTEX_SIZE;
            assertTrue(Math.abs(early[at + 1]) <= amplitude + 1e-3f);
            moved |= Math.abs(early[at + 1] - later[at + 1]) > 1f;
        }
        assertTrue(moved, "the waves travel");

        // A vertex's normal agrees with the surface's slope measured from heights either side of it
        float[] grid = new float[early.length];
        WaterSurfaceBuilder.fill(water, 0.3f, grid);
        float step = 20f;
        for (int vertex : new int[] {10 * WaterSurfaceBuilder.VERTICES_PER_SIDE + 17, 40 * WaterSurfaceBuilder.VERTICES_PER_SIDE + 3}) {
            int at = vertex * WaterSurfaceBuilder.VERTEX_SIZE;
            float gx = grid[at];
            float gz = grid[at + 2];
            float dx = (WaterSurfaceBuilder.heightAt(water, gx + step, gz, 0.3f) - WaterSurfaceBuilder.heightAt(water, gx - step, gz, 0.3f)) / (2 * step);
            float dz = (WaterSurfaceBuilder.heightAt(water, gx, gz + step, 0.3f) - WaterSurfaceBuilder.heightAt(water, gx, gz - step, 0.3f)) / (2 * step);
            Vector3 expected = new Vector3(-dx, 1f, -dz).nor();
            assertEquals(expected.x, grid[at + 3], 0.005f, "normal x at vertex " + vertex);
            assertEquals(expected.y, grid[at + 4], 0.005f, "normal y at vertex " + vertex);
            assertEquals(expected.z, grid[at + 5], 0.005f, "normal z at vertex " + vertex);
            assertEquals(WaterSurfaceBuilder.heightAt(water, gx, gz, 0.3f), grid[at + 1], 1e-3f);
        }
    }

    @Test
    void shouldFlattenWavesTooShortForALargeWaterBody() {
        WaterBodyComponent water = new WaterBodyComponent();
        WaterPreset.LAKE.applyTo(water); // 600-unit waves
        water.setSize(WaterBodyComponent.MAX_SIZE, WaterBodyComponent.MAX_SIZE);
        assertTrue(WaterSurfaceBuilder.shownWaveHeight(water) < water.getWaveHeight() * 0.01f);
    }
}
