package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.math.Matrix4;
import io.github.ldev22live.gdxunity.engine.core.EngineRenderer;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.PointLightComponent;

import java.util.Objects;

/**
 * 3D scene renderer using LibGDX {@link ModelBatch}.
 * Provides the foundation for 3D rendering with perspective camera support and 3D lighting environment.
 *
 * Current scope: camera update, depth state, and synchronization of scene lights into the
 * {@link Environment}. No geometry is submitted yet; model and mesh rendering are not
 * implemented and are tracked in {@code docs/FEATURE_TEST_MATRIX.md}.
 */
public class SceneRenderer3D implements EngineRenderer {
    private Camera camera;
    private Scene activeScene;

    private ModelBatch modelBatch;
    private Environment environment;

    private Color clearColor = new Color(0.12f, 0.14f, 0.18f, 1.0f);
    private Color ambientLight = new Color(0.2f, 0.2f, 0.2f, 1.0f);
    private boolean initialized = false;
    private boolean disposed = false;

    public SceneRenderer3D() {
        this(null, null);
    }

    public SceneRenderer3D(Camera camera) {
        this(camera, null);
    }

    public SceneRenderer3D(Camera camera, Scene activeScene) {
        this.camera = camera;
        this.activeScene = activeScene;
    }

    @Override
    public void initialize() {
        if (initialized || disposed) {
            return;
        }

        this.modelBatch = createModelBatch();
        this.environment = createEnvironment();
        updateEnvironmentLights();
        this.initialized = true;
    }

    protected ModelBatch createModelBatch() {
        return new ModelBatch();
    }

    protected Environment createEnvironment() {
        Environment env = new Environment();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, ambientLight.r, ambientLight.g, ambientLight.b, ambientLight.a));
        return env;
    }

    /**
     * Rebuilds the LibGDX 3D environment lights from the active scene's lighting components.
     */
    public void updateEnvironmentLights() {
        if (environment == null) {
            return;
        }
        environment.clear();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, ambientLight.r, ambientLight.g, ambientLight.b, ambientLight.a));

        if (activeScene != null && activeScene.isActive()) {
            for (Entity entity : activeScene.getAllEntities()) {
                if (!entity.isActiveInHierarchy()) continue;

                for (Component comp : entity.getAllComponents()) {
                    if (!comp.isEnabled()) continue;

                    if (comp instanceof DirectionalLightComponent dirLight) {
                        DirectionalLight gdxDirLight = new DirectionalLight();
                        Color c = dirLight.getColor();
                        float intensity = dirLight.getIntensity();
                        gdxDirLight.set(c.r * intensity, c.g * intensity, c.b * intensity,
                            dirLight.getDirection().x, dirLight.getDirection().y, dirLight.getDirection().z);
                        environment.add(gdxDirLight);
                    } else if (comp instanceof PointLightComponent pointLight) {
                        PointLight gdxPointLight = new PointLight();
                        Color c = pointLight.getColor();
                        float intensity = pointLight.getIntensity();
                        com.badlogic.gdx.math.Vector3 pos = entity.getTransform().getWorldPosition();
                        gdxPointLight.set(c.r * intensity, c.g * intensity, c.b * intensity,
                            pos.x, pos.y, pos.z, pointLight.getRange());
                        environment.add(gdxPointLight);
                    }
                }
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        if (camera != null) {
            camera.resize(width, height);
        }
    }

    @Override
    public void render(float deltaTime) {
        if (!initialized || disposed) {
            return;
        }

        // 1. Clear frame buffer
        if (Gdx.gl != null) {
            Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        }

        // 2. Update camera projection
        if (camera != null) {
            camera.update();
        }

        // 3. Skip scene rendering if no scene or scene is inactive
        if (activeScene == null || !activeScene.isActive()) {
            return;
        }

        // 4. Synchronize scene lights with environment
        updateEnvironmentLights();

        // 5. 3D pass. No renderables are submitted yet (model rendering is not implemented);
        // ModelInstances will be rendered here with `environment` once a model component exists.
        if (modelBatch != null && camera != null) {
            modelBatch.begin(camera.getGdxCamera());
            modelBatch.end();
        }

        // Disable depth test for subsequent rendering passes (such as 2D UI overlay)
        if (Gdx.gl != null) {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        initialized = false;

        if (modelBatch != null) {
            try {
                modelBatch.dispose();
            } catch (Throwable t) {
                // Ignore disposal errors on shutdown
            } finally {
                modelBatch = null;
            }
        }
    }

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
    }

    public Scene getActiveScene() {
        return activeScene;
    }

    public void setActiveScene(Scene activeScene) {
        this.activeScene = activeScene;
    }

    public Color getAmbientLight() {
        return new Color(ambientLight);
    }

    public void setAmbientLight(Color ambientLight) {
        this.ambientLight = Objects.requireNonNull(ambientLight, "ambientLight cannot be null");
        if (environment != null) {
            updateEnvironmentLights();
        }
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public Color getClearColor() {
        return clearColor;
    }

    public void setClearColor(Color clearColor) {
        this.clearColor = Objects.requireNonNull(clearColor, "clearColor cannot be null");
    }

    public ModelBatch getModelBatch() {
        return modelBatch;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isDisposed() {
        return disposed;
    }
}
