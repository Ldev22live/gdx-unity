package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceDisposalException;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.PointLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.SkyLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.ToneMappingComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SceneRenderer3DTest {

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    @Test
    void shouldInitializeWithCameraAndDefaultAmbientLighting() {
        PerspectiveCamera3D camera = new PerspectiveCamera3D(60f, 800f, 600f, 0.1f, 100f);
        Scene scene = new Scene("Test3DScene");

        SceneRenderer3D renderer = new SceneRenderer3D(camera, scene) {
            @Override
            protected ModelBatch createModelBatch() {
                // In headless mode without GL context, avoid actual ModelBatch allocation
                return null;
            }
        };

        renderer.initialize();
        assertTrue(renderer.isInitialized());
        assertFalse(renderer.isDisposed());

        assertEquals(camera, renderer.getCamera());
        assertEquals(scene, renderer.getActiveScene());

        Environment env = renderer.getEnvironment();
        assertNotNull(env);
        assertTrue(env.has(ColorAttribute.AmbientLight));

        renderer.dispose();
        assertTrue(renderer.isDisposed());
        assertFalse(renderer.isInitialized());
    }

    @Test
    void shouldSynchronizeDirectionalAndPointLightsFromScene() {
        PerspectiveCamera3D camera = new PerspectiveCamera3D(60f, 800f, 600f, 0.1f, 100f);
        Scene scene = new Scene("LightingScene");

        // Add Directional Light
        Entity sun = scene.createEntity("Sun");
        DirectionalLightComponent dirLight = new DirectionalLightComponent();
        dirLight.setColor(new Color(1f, 0.9f, 0.8f, 1f));
        dirLight.setDirection(0f, -1f, 0f);
        dirLight.setIntensity(1.5f);
        sun.addComponent(dirLight);

        // Add Point Light
        Entity lamp = scene.createEntity("Lamp");
        lamp.getTransform().setLocalPosition(5f, 2f, -3f);
        PointLightComponent pointLight = new PointLightComponent();
        pointLight.setColor(new Color(0.2f, 0.5f, 1f, 1f));
        pointLight.setIntensity(2.0f);
        pointLight.setRange(15f);
        lamp.addComponent(pointLight);

        SceneRenderer3D renderer = new SceneRenderer3D(camera, scene) {
            @Override
            protected ModelBatch createModelBatch() {
                return null;
            }
        };

        renderer.initialize();
        renderer.updateEnvironmentLights();

        Environment env = renderer.getEnvironment();
        assertNotNull(env);

        // Verify Directional Light was added to environment
        com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute dirAttr =
            env.get(com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute.class,
                    com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute.Type);
        assertNotNull(dirAttr);
        boolean foundDir = false;
        for (DirectionalLight l : dirAttr.lights) {
            if (Math.abs(l.direction.y - (-1f)) < 0.01f) {
                foundDir = true;
                break;
            }
        }
        assertTrue(foundDir, "Directional light should be present in Environment");

        // Verify Point Light was added to environment
        com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute pointAttr =
            env.get(com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute.class,
                    com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute.Type);
        assertNotNull(pointAttr);
        boolean foundPoint = false;
        for (PointLight p : pointAttr.lights) {
            if (Math.abs(p.position.x - 5f) < 0.01f && Math.abs(p.intensity - 15f) < 0.01f) {
                foundPoint = true;
                break;
            }
        }
        assertTrue(foundPoint, "Point light should be present in Environment");

        renderer.dispose();
    }

    @Test
    void shouldChooseFirstShadowCastingLightAndPutItFirst() {
        Scene scene = new Scene("Shadows");
        DirectionalLightComponent noShadows = new DirectionalLightComponent();
        noShadows.setCastShadows(false);
        noShadows.setDirection(1f, -1f, 0f);
        scene.createEntity("Fill").addComponent(noShadows);
        DirectionalLightComponent sun = new DirectionalLightComponent();
        sun.setDirection(0f, -1f, 0f);
        scene.createEntity("Sun").addComponent(sun);
        SceneRenderer3D renderer = headlessRenderer(scene);

        renderer.updateEnvironmentLights();

        assertSame(sun, renderer.getShadowCaster());
        DirectionalLightsAttribute lights = renderer.getEnvironment().get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type);
        assertEquals(2, lights.lights.size);
        assertEquals(-1f, lights.lights.get(0).direction.y, 1e-5f, "the shadow caster is light 0");

        sun.setCastShadows(false);
        renderer.updateEnvironmentLights();
        assertNull(renderer.getShadowCaster());
        renderer.dispose();
    }

    @Test
    void shouldScaleDirectLightByExposureAndPbrFactorInLinearSpace() {
        Scene scene = new Scene("Exposure");
        DirectionalLightComponent sun = new DirectionalLightComponent();
        sun.setColor(new Color(0.5f, 1f, 1f, 1f));
        scene.createEntity("Sun").addComponent(sun);
        ToneMappingComponent toneMapping = new ToneMappingComponent();
        toneMapping.setExposure(2f);
        toneMapping.setOperator(ToneMappingComponent.Operator.REINHARD);
        scene.createEntity("Settings").addComponent(toneMapping);
        SceneRenderer3D renderer = headlessRenderer(scene);

        renderer.updateEnvironmentLights();

        DirectionalLight light = renderer.getEnvironment()
            .get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type).lights.get(0);
        float scale = 2f * SceneRenderer3D.PBR_DIRECT_LIGHT_SCALE;
        assertEquals(scale, light.color.g, 1e-4f);
        assertEquals((float) Math.pow(0.5f, SceneRenderer3D.DISPLAY_GAMMA) * scale, light.color.r, 1e-4f);
        assertEquals(2f, renderer.getExposure());
        assertEquals(ToneMappingComponent.Operator.REINHARD, renderer.getToneMappingOperator());
        renderer.dispose();
    }

    @Test
    void shouldReuseLightObjectsAcrossUpdates() {
        Scene scene = new Scene("Reuse");
        scene.createEntity("Sun").addComponent(new DirectionalLightComponent());
        scene.createEntity("Lamp").addComponent(new PointLightComponent());
        SceneRenderer3D renderer = headlessRenderer(scene);

        renderer.updateEnvironmentLights();
        DirectionalLight sun = renderer.getEnvironment()
            .get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type).lights.get(0);
        PointLight lamp = renderer.getEnvironment().get(PointLightsAttribute.class, PointLightsAttribute.Type).lights.get(0);
        renderer.updateEnvironmentLights();

        assertSame(sun, renderer.getEnvironment()
            .get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type).lights.get(0));
        assertSame(lamp, renderer.getEnvironment().get(PointLightsAttribute.class, PointLightsAttribute.Type).lights.get(0));
        renderer.dispose();
    }

    @Test
    void shouldBakeSkyLightOnlyWhenColorsOrQualityChange() {
        Scene scene = new Scene("Sky");
        SkyLightComponent sky = new SkyLightComponent();
        sky.setIntensity(0.5f);
        scene.createEntity("Sky").addComponent(sky);
        List<FakeSkyLightMaps> baked = new ArrayList<>();
        SceneRenderer3D renderer = headlessRenderer(scene, baked);

        renderer.updateEnvironmentLights();
        renderer.updateEnvironmentLights();
        assertEquals(1, baked.size(), "same sky: baked once");
        assertSame(baked.get(0), renderer.getSkyLightMaps());
        assertTrue(baked.get(0).applied > 0);
        ColorAttribute ambient = renderer.getEnvironment().get(ColorAttribute.class, ColorAttribute.AmbientLight);
        assertEquals(0.5f, ambient.color.r, 1e-5f, "sky intensity × exposure multiplies the sky light");

        sky.setIntensity(2f);
        renderer.updateEnvironmentLights();
        assertEquals(1, baked.size(), "intensity is not baked into the maps");
        ambient = renderer.getEnvironment().get(ColorAttribute.class, ColorAttribute.AmbientLight);
        assertEquals(2f, ambient.color.r, 1e-5f, "not clamped to 1");

        sky.setSkyColor(Color.ORANGE);
        renderer.updateEnvironmentLights();
        assertEquals(2, baked.size());
        assertTrue(baked.get(0).disposed, "replaced maps are disposed");

        renderer.setQuality(RenderQuality3D.HIGH);
        renderer.updateEnvironmentLights();
        assertEquals(3, baked.size());

        sky.setEnabled(false);
        renderer.updateEnvironmentLights();
        assertTrue(baked.get(2).disposed);
        assertNull(renderer.getSkyLightMaps());
        renderer.dispose();
    }

    @Test
    void shouldUseFlatAmbientInLinearSpaceWithoutSkyLight() {
        SceneRenderer3D renderer = headlessRenderer(new Scene("Flat"));
        renderer.setAmbientLight(new Color(0.5f, 0.5f, 0.5f, 1f));

        renderer.updateEnvironmentLights();

        ColorAttribute ambient = renderer.getEnvironment().get(ColorAttribute.class, ColorAttribute.AmbientLight);
        assertEquals((float) Math.pow(0.5f, SceneRenderer3D.DISPLAY_GAMMA), ambient.color.r, 1e-5f);
        assertNull(renderer.getSkyLightMaps());
        renderer.dispose();
    }

    @Test
    void shouldDisposeEverythingAndReportFailuresTogether() {
        Scene scene = new Scene("Dispose");
        scene.createEntity("Sky").addComponent(new SkyLightComponent());
        List<FakeSkyLightMaps> baked = new ArrayList<>();
        SceneRenderer3D renderer = headlessRenderer(scene, baked);
        renderer.updateEnvironmentLights();
        baked.get(0).failOnDispose = true;

        ResourceDisposalException failure = assertThrows(ResourceDisposalException.class, renderer::dispose);

        assertEquals(1, failure.getSuppressed().length);
        assertTrue(baked.get(0).disposed);
        assertTrue(renderer.isDisposed());
        assertNull(renderer.getSkyLightMaps());
        assertDoesNotThrow(renderer::dispose, "idempotent");
    }

    private static SceneRenderer3D headlessRenderer(Scene scene) {
        return headlessRenderer(scene, new ArrayList<>());
    }

    /** Renderer without GL resources; sky light bakes produce fakes collected in {@code baked}. */
    private static SceneRenderer3D headlessRenderer(Scene scene, List<FakeSkyLightMaps> baked) {
        SceneRenderer3D renderer = new SceneRenderer3D(new PerspectiveCamera3D(60f, 800f, 600f, 0.1f, 100f), scene) {
            @Override
            protected ModelBatch createModelBatch() {
                return null;
            }

            @Override
            protected SkyLightMaps bakeSkyLight(SkyLightComponent sky, Vector3 sunDirection, RenderQuality3D quality) {
                FakeSkyLightMaps maps = new FakeSkyLightMaps();
                baked.add(maps);
                return maps;
            }
        };
        renderer.initialize();
        return renderer;
    }

    private static final class FakeSkyLightMaps implements SkyLightMaps {
        int applied;
        boolean disposed;
        boolean failOnDispose;

        @Override
        public void applyTo(Environment environment) {
            applied++;
        }

        @Override
        public void dispose() {
            disposed = true;
            if (failOnDispose) {
                throw new IllegalStateException("simulated GPU failure");
            }
        }
    }
}
