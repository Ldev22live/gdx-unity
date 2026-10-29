package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.core.EngineRenderer;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.PointLightComponent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 3D scene renderer using LibGDX {@link ModelBatch}.
 * Provides the foundation for 3D rendering with perspective camera support and 3D lighting environment.
 *
 * Renders {@link MeshPrimitiveComponent} shapes (box, sphere, plane) lit by the scene's directional and
 * point lights. One unit-sized model per shape is built lazily and shared; each component gets a cached
 * {@link ModelInstance} scaled to its size and tinted with its color. Imported models (glTF) and materials
 * beyond a diffuse color are not supported yet.
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
    private boolean clearEnabled = true;
    private boolean defaultLightEnabled = false;

    /** Latitude/longitude segments of the unit sphere. */
    private static final int SPHERE_DIVISIONS_U = 32;
    private static final int SPHERE_DIVISIONS_V = 20;
    private static final Color DEFAULT_LIGHT_COLOR = new Color(0.85f, 0.85f, 0.82f, 1f);
    private static final Vector3 DEFAULT_LIGHT_DIRECTION = new Vector3(-0.4f, -1f, -0.6f).nor();

    private final Map<MeshPrimitiveComponent.Shape, Model> unitModels = new EnumMap<>(MeshPrimitiveComponent.Shape.class);
    private final Map<MeshPrimitiveComponent, CachedInstance> instances = new IdentityHashMap<>();

    /** A primitive to draw: its component and final world transform (entity world matrix scaled by size). */
    public record PrimitiveDraw(MeshPrimitiveComponent component, Matrix4 transform) {}

    private record CachedInstance(MeshPrimitiveComponent.Shape shape, ModelInstance instance) {}

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
     * Collects the enabled primitives of active entities with their world transforms. Pure scene traversal,
     * usable without a graphics context.
     */
    public static List<PrimitiveDraw> collectPrimitives(Scene scene) {
        List<PrimitiveDraw> draws = new ArrayList<>();
        if (scene == null || !scene.isActive()) {
            return draws;
        }
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy()) continue;
            for (Component comp : entity.getAllComponents()) {
                if (comp.isEnabled() && comp instanceof MeshPrimitiveComponent primitive) {
                    Vector3 size = primitive.getSize();
                    Matrix4 transform = new Matrix4(entity.getTransform().getWorldMatrix()).scale(size.x, size.y, size.z);
                    draws.add(new PrimitiveDraw(primitive, transform));
                }
            }
        }
        return draws;
    }

    /** Builds the shared unit-sized model for a shape (box/sphere of size 1, 1x1 plane facing +Y). */
    protected Model createUnitModel(MeshPrimitiveComponent.Shape shape) {
        ModelBuilder builder = new ModelBuilder();
        long attributes = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        Material material = new Material(ColorAttribute.createDiffuse(Color.WHITE));
        return switch (shape) {
            case BOX -> builder.createBox(1f, 1f, 1f, material, attributes);
            case SPHERE -> builder.createSphere(1f, 1f, 1f, SPHERE_DIVISIONS_U, SPHERE_DIVISIONS_V, material, attributes);
            case PLANE -> {
                // Visible from both sides
                material.set(IntAttribute.createCullFace(GL20.GL_NONE));
                yield builder.createRect(
                    -0.5f, 0f, 0.5f,
                    0.5f, 0f, 0.5f,
                    0.5f, 0f, -0.5f,
                    -0.5f, 0f, -0.5f,
                    0f, 1f, 0f, material, attributes);
            }
        };
    }

    private ModelInstance instanceFor(MeshPrimitiveComponent component) {
        CachedInstance cached = instances.get(component);
        if (cached == null || cached.shape() != component.getShape()) {
            Model model = unitModels.computeIfAbsent(component.getShape(), this::createUnitModel);
            cached = new CachedInstance(component.getShape(), new ModelInstance(model));
            instances.put(component, cached);
        }
        return cached.instance();
    }

    /**
     * Rebuilds the LibGDX 3D environment lights from the active scene's lighting components.
     * With {@link #setDefaultLightEnabled(boolean)} a fallback directional light is added when the scene
     * has none, so primitives are visible in an unlit scene.
     */
    public void updateEnvironmentLights() {
        if (environment == null) {
            return;
        }
        environment.clear();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, ambientLight.r, ambientLight.g, ambientLight.b, ambientLight.a));

        boolean hasDirectionalLight = false;
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
                        hasDirectionalLight = true;
                    } else if (comp instanceof PointLightComponent pointLight) {
                        PointLight gdxPointLight = new PointLight();
                        Color c = pointLight.getColor();
                        float intensity = pointLight.getIntensity();
                        Vector3 pos = entity.getTransform().getWorldPosition();
                        gdxPointLight.set(c.r * intensity, c.g * intensity, c.b * intensity,
                            pos.x, pos.y, pos.z, pointLight.getRange());
                        environment.add(gdxPointLight);
                    }
                }
            }
        }
        if (!hasDirectionalLight && defaultLightEnabled) {
            environment.add(new DirectionalLight().set(DEFAULT_LIGHT_COLOR, DEFAULT_LIGHT_DIRECTION));
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

        // 1. Clear. With clearing disabled the host has drawn a background (e.g. a grid); only depth is reset.
        if (Gdx.gl != null) {
            if (clearEnabled) {
                Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            } else {
                Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
            }
        }

        // 2. Update camera projection and restore this camera's GL viewport (see SceneRenderer2D.render)
        if (camera != null) {
            camera.update();
            if (Gdx.gl != null && camera.getViewport() != null) {
                camera.getViewport().apply();
            }
        }

        // 3. Skip scene rendering if no scene or scene is inactive
        if (activeScene == null || !activeScene.isActive() || modelBatch == null || camera == null) {
            return;
        }

        // 4. Synchronize scene lights with environment
        updateEnvironmentLights();

        // 5. Draw primitives with depth testing; depth test is switched off again for later 2D passes
        List<PrimitiveDraw> draws = collectPrimitives(activeScene);
        if (!draws.isEmpty()) {
            if (Gdx.gl != null) {
                Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
                Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
            }
            modelBatch.begin(camera.getGdxCamera());
            try {
                for (PrimitiveDraw draw : draws) {
                    ModelInstance instance = instanceFor(draw.component());
                    instance.transform.set(draw.transform());
                    ColorAttribute diffuse = (ColorAttribute) instance.materials.get(0).get(ColorAttribute.Diffuse);
                    diffuse.color.set(draw.component().getColor());
                    modelBatch.render(instance, environment);
                }
            } finally {
                modelBatch.end();
                if (Gdx.gl != null) {
                    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
                }
            }
        }

        // 6. Drop instances of components that are no longer drawn (removed or disabled)
        if (instances.size() > draws.size()) {
            List<MeshPrimitiveComponent> drawn = new ArrayList<>(draws.size());
            for (PrimitiveDraw draw : draws) {
                drawn.add(draw.component());
            }
            instances.keySet().removeIf(component -> !drawn.contains(component));
        }
    }

    public boolean isClearEnabled() {
        return clearEnabled;
    }

    /** Controls whether {@link #render(float)} clears color as well as depth. Default true. */
    public void setClearEnabled(boolean clearEnabled) {
        this.clearEnabled = clearEnabled;
    }

    public boolean isDefaultLightEnabled() {
        return defaultLightEnabled;
    }

    /** Adds a fallback directional light when the scene has none (editor convenience). Default false. */
    public void setDefaultLightEnabled(boolean defaultLightEnabled) {
        this.defaultLightEnabled = defaultLightEnabled;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        initialized = false;

        instances.clear();
        for (Model model : unitModels.values()) {
            model.dispose();
        }
        unitModels.clear();

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
