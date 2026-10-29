package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.PointLightComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}
