package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.engine.core.EngineApplication;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer2D;
import io.github.ldev22live.gdxunity.engine.render.camera.OrthographicCamera2D;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

/**
 * Launches the desktop (LWJGL3) application demonstrating camera-driven scene entity rendering.
 */
public class DesktopLauncher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return;
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        // Setup demonstration scene with multiple entities in a parent-child hierarchy
        Scene demoScene = createDemoScene();

        // Setup camera-driven renderer
        OrthographicCamera2D camera = new OrthographicCamera2D(640, 480);
        SceneRenderer2D renderer = new SceneRenderer2D(camera);
        renderer.setActiveScene(demoScene);

        return new Lwjgl3Application(new EngineApplication(renderer), getDefaultConfiguration());
    }

    public static Scene createDemoScene() {
        Scene scene = new Scene("Milestone 3 Demo Scene");

        // 1. Root Entity: Sun / Parent Core (Center of 640x480 screen = 320, 240)
        Entity parentCore = scene.createEntity("ParentCore");
        parentCore.getTransform().setLocalPosition(320f, 240f, 0f);
        parentCore.addComponent(new ShapeRendererComponent(120f, 120f, new Color(0.2f, 0.7f, 0.9f, 1.0f))); // Cyan square

        // 2. Child Entity 1: Orbiting Planet (Inherits parent transform + offset (100, 50))
        Entity childPlanet = scene.createEntity("ChildPlanet");
        childPlanet.getTransform().setLocalPosition(100f, 50f, 0f);
        childPlanet.getTransform().setParent(parentCore.getTransform());
        childPlanet.addComponent(new ShapeRendererComponent(50f, 50f, new Color(0.9f, 0.4f, 0.2f, 1.0f))); // Orange square

        // 3. Child Entity 2: Moon (Child of planet, offset (40, 20), inherits multi-level world transform)
        Entity moon = scene.createEntity("Moon");
        moon.getTransform().setLocalPosition(40f, 20f, 0f);
        moon.getTransform().setParent(childPlanet.getTransform());
        moon.addComponent(new ShapeRendererComponent(20f, 20f, new Color(0.9f, 0.9f, 0.3f, 1.0f))); // Yellow square

        // 4. Independent Entity: Ground Platform (Bottom center = 320, 40)
        Entity ground = scene.createEntity("GroundPlatform");
        ground.getTransform().setLocalPosition(320f, 40f, 0f);
        ground.addComponent(new ShapeRendererComponent(400f, 30f, new Color(0.3f, 0.8f, 0.4f, 1.0f))); // Green bar

        return scene;
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("GdxUnity Engine — Milestone 3");
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(640, 480);
        return configuration;
    }
}
