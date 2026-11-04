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
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.core.EngineRenderer;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceDisposalException;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceManager;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.PointLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.SkyLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.ToneMappingComponent;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.lights.DirectionalShadowLight;
import net.mgsx.gltf.scene3d.shaders.PBRShaderConfig;
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider;

/**
 * 3D scene renderer using LibGDX {@link ModelBatch} with gdx-gltf's PBR shaders (TD-030, TD-032).
 *
 * <p>Draws {@link MeshPrimitiveComponent} shapes (box, sphere, plane) and imported glTF models
 * ({@link ModelComponent}, loaded through {@link ModelAssets} and posed by a {@link ModelPoser}) with one shading
 * model, lit by the scene's lights:
 * <ul>
 *   <li>{@link DirectionalLightComponent} and {@link PointLightComponent}. The first enabled directional light with
 *       {@link DirectionalLightComponent#isCastShadows()} casts shadows from a single shadow map that follows the
 *       camera ({@link ShadowBounds}).</li>
 *   <li>{@link SkyLightComponent}: image-based lighting from a two-color sky ({@link BakedSkyLightMaps}), rebuilt
 *       only when its colors or the quality preset change. Without one, the flat {@link #setAmbientLight ambient
 *       color} is used.</li>
 *   <li>{@link ToneMappingComponent}: exposure scales all light; the operator is compiled into the shader
 *       ({@link ToneMappingShader}). Without one: exposure 1, no tone mapping.</li>
 * </ul>
 *
 * <p>Colors in components are authored in sRGB, like any color picker; they are converted to linear before
 * lighting, and the shader converts back. Shadow and sky quality come from {@link RenderQuality3D}.
 *
 * <p>The shadow pass renders into its own frame buffer and then restores whatever frame buffer and viewport the
 * host had bound (LibGDX 1.14 frame buffers do not nest), so the renderer can draw into an editor's off-screen view.
 */
public class SceneRenderer3D implements EngineRenderer {
    /** Default width of the shadowed area in world units: 30 m at the editor's 100 units per meter. */
    public static final float DEFAULT_SHADOW_DISTANCE = 3000f;
    /**
     * Direct light multiplier. The PBR shader divides diffuse light by π (energy conservation); LibGDX's default
     * shader, used for primitives before Phase 13-B, did not. With this factor a white light of intensity 1 still
     * lights a white surface facing it to full white.
     */
    static final float PBR_DIRECT_LIGHT_SCALE = MathUtils.PI;
    /** Gamma of the sRGB-to-linear conversion; the same value as gdx-gltf's default output gamma. */
    static final float DISPLAY_GAMMA = 2.2f;
    /** Shadow depth comparison bias, in fractions of the shadow volume depth (2 × shadow distance). */
    static final float SHADOW_BIAS = 0.001f;
    /** Roughness of primitives: matte, with a soft sky highlight. */
    static final float PRIMITIVE_ROUGHNESS = 0.7f;
    /** Latitude/longitude segments of the unit sphere. */
    private static final int SPHERE_DIVISIONS_U = 32;
    private static final int SPHERE_DIVISIONS_V = 20;
    private static final Color DEFAULT_LIGHT_COLOR = new Color(0.85f, 0.85f, 0.82f, 1f);
    private static final Vector3 DEFAULT_LIGHT_DIRECTION = new Vector3(-0.4f, -1f, -0.6f).nor();
    /** Size of the glGetIntegerv result buffer; LibGDX requires room for 16 ints even for single values. */
    private static final int GL_INT_QUERY_SIZE = 16;

    private Camera camera;
    private Scene activeScene;

    /** PBR batch for primitives and imported models, compiled for {@link #compiledOperator}. */
    private ModelBatch modelBatch;
    private ToneMappingComponent.Operator compiledOperator = ToneMappingComponent.Operator.NONE;
    private ModelBatch shadowBatch;
    private DirectionalShadowLight shadowLight;
    private SkyLightMaps skyLightMaps;
    private final Color bakedSkyColor = new Color();
    private final Color bakedGroundColor = new Color();
    private RenderQuality3D bakedQuality;
    private ModelAssets modelAssets;
    private Environment environment;

    private Color clearColor = new Color(0.12f, 0.14f, 0.18f, 1.0f);
    private Color ambientLight = new Color(0.2f, 0.2f, 0.2f, 1.0f);
    private boolean initialized = false;
    private boolean disposed = false;
    private boolean clearEnabled = true;
    private boolean defaultLightEnabled = false;
    private boolean shadowsEnabled = true;
    private float shadowDistance = DEFAULT_SHADOW_DISTANCE;
    private RenderQuality3D quality = RenderQuality3D.MEDIUM;

    // Scene lighting found by the last updateEnvironmentLights(); reused every frame
    private final List<DirectionalLightComponent> sceneDirectionalLights = new ArrayList<>();
    private DirectionalLightComponent shadowCaster;
    private SkyLightComponent skyLight;
    private float exposure = 1f;
    private ToneMappingComponent.Operator requestedOperator = ToneMappingComponent.Operator.NONE;

    // Environment content, reused every frame
    private final ColorAttribute ambientAttribute = new ColorAttribute(ColorAttribute.AmbientLight);
    private final DirectionalLightsAttribute directionalLightsAttribute = new DirectionalLightsAttribute();
    private final PointLightsAttribute pointLightsAttribute = new PointLightsAttribute();
    private final PBRFloatAttribute shadowBiasAttribute = new PBRFloatAttribute(PBRFloatAttribute.ShadowBias, SHADOW_BIAS);
    private final List<DirectionalLight> directionalLightPool = new ArrayList<>();
    private final List<PointLight> pointLightPool = new ArrayList<>();
    private final Color linearColor = new Color();

    private final ShadowBounds shadowBounds = new ShadowBounds();
    private final List<ModelInstance> drawList = new ArrayList<>();
    private IntBuffer glQuery;
    private int savedFrameBuffer;
    /** x, y, width, height of the viewport saved by {@link #saveRenderTarget()}. */
    private final int[] savedViewport = new int[4];

    private final Map<MeshPrimitiveComponent.Shape, Model> unitModels = new EnumMap<>(MeshPrimitiveComponent.Shape.class);
    private final Map<MeshPrimitiveComponent, CachedInstance> instances = new IdentityHashMap<>();
    private final Map<ModelComponent, CachedModel> modelInstances = new IdentityHashMap<>();
    private final Map<DynamicMeshComponent, DynamicMeshGpu<GlChunkTarget>> dynamicMeshes = new IdentityHashMap<>();
    private final BoundingBox dynamicBounds = new BoundingBox();
    /** Finds the geometry of {@link DynamicMeshComponent}s; null (the default) skips them. */
    private DynamicMeshSource dynamicMeshSource;

    /** A primitive to draw: its component and final world transform (entity world matrix scaled by size). */
    public record PrimitiveDraw(MeshPrimitiveComponent component, Matrix4 transform) {}

    /** An imported model to draw: its component and world transform (entity world matrix scaled by import scale). */
    public record ModelDraw(ModelComponent component, Matrix4 transform) {}

    /** An editable mesh to draw: its component and the entity's world transform. */
    public record DynamicMeshDraw(DynamicMeshComponent component, Matrix4 transform) {}

    private record CachedInstance(MeshPrimitiveComponent.Shape shape, ModelInstance instance) {}

    private record CachedModel(Model model, ModelInstance instance, ModelPoser poser) {}

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

    /**
     * PBR batch for primitives and imported models: skinning for up to {@link ModelAssets#MAX_BONES} bones and the
     * tone mapping operator of {@link #getToneMappingOperator()}. Called again when the operator changes.
     */
    protected ModelBatch createModelBatch() {
        PBRShaderConfig config = PBRShaderProvider.createDefaultConfig();
        config.numBones = ModelAssets.MAX_BONES;
        config.fragmentShader = ToneMappingShader.apply(config.fragmentShader, compiledOperator);
        return new ModelBatch(PBRShaderProvider.createDefault(config));
    }

    /** Depth-only batch for the shadow pass, with the same bone limit as {@link #createModelBatch()}. */
    protected ModelBatch createShadowBatch() {
        return new ModelBatch(PBRShaderProvider.createDefaultDepth(ModelAssets.MAX_BONES));
    }

    /** Shadow-casting light with a square shadow map of {@code mapSize} texels. Needs a GL context. */
    protected DirectionalShadowLight createShadowLight(int mapSize) {
        return new DirectionalShadowLight(mapSize, mapSize);
    }

    /** Builds the image-based lighting maps for {@code sky}. Needs a GL context. */
    protected SkyLightMaps bakeSkyLight(SkyLightComponent sky, Vector3 sunDirection, RenderQuality3D quality) {
        return BakedSkyLightMaps.bake(sky.getSkyColor(), sky.getGroundColor(), sunDirection, quality);
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

    /**
     * Collects the enabled imported models of active entities with their world transforms. Pure scene traversal,
     * usable without a graphics context.
     */
    public static List<ModelDraw> collectModels(Scene scene) {
        List<ModelDraw> draws = new ArrayList<>();
        if (scene == null || !scene.isActive()) {
            return draws;
        }
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy()) continue;
            for (Component comp : entity.getAllComponents()) {
                if (comp.isEnabled() && comp instanceof ModelComponent model) {
                    float scale = model.getImportScale();
                    Matrix4 transform = new Matrix4(entity.getTransform().getWorldMatrix()).scale(scale, scale, scale);
                    draws.add(new ModelDraw(model, transform));
                }
            }
        }
        return draws;
    }

    /**
     * Collects the enabled dynamic meshes of active entities with their world transforms. Pure scene traversal,
     * usable without a graphics context.
     */
    public static List<DynamicMeshDraw> collectDynamicMeshes(Scene scene) {
        List<DynamicMeshDraw> draws = new ArrayList<>();
        if (scene == null || !scene.isActive()) {
            return draws;
        }
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy()) continue;
            for (Component comp : entity.getAllComponents()) {
                if (comp.isEnabled() && comp instanceof DynamicMeshComponent mesh) {
                    draws.add(new DynamicMeshDraw(mesh, new Matrix4(entity.getTransform().getWorldMatrix())));
                }
            }
        }
        return draws;
    }

    /**
     * Sets where {@link DynamicMeshComponent}s get their geometry, such as the sculpts open in the editor. The
     * source is asked every frame, so the geometry may change while it is displayed. Null stops drawing them.
     */
    public void setDynamicMeshSource(DynamicMeshSource source) {
        this.dynamicMeshSource = source;
    }

    /** Builds the shared unit-sized model for a shape (box/sphere of size 1, 1x1 plane facing +Y). */
    protected Model createUnitModel(MeshPrimitiveComponent.Shape shape) {
        ModelBuilder builder = new ModelBuilder();
        long attributes = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        Material material = new Material(
            PBRColorAttribute.createBaseColorFactor(Color.WHITE),
            PBRFloatAttribute.createMetallic(0f),
            PBRFloatAttribute.createRoughness(PRIMITIVE_ROUGHNESS));
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
     * Rebuilds the 3D environment from the active scene: lights, shadow caster, sky light and exposure (see the
     * class description). With {@link #setDefaultLightEnabled(boolean)} a fallback directional light without
     * shadows is added when the scene has none, so primitives are visible in an unlit scene.
     *
     * <p>Light objects and environment attributes are reused across calls.
     */
    public void updateEnvironmentLights() {
        if (environment == null) {
            return;
        }
        scanSceneLighting();
        environment.clear();
        environment.shadowMap = null;

        // Directional lights; the shadow caster goes first because the PBR shader shadows only light 0
        directionalLightsAttribute.lights.clear();
        boolean casting = shadowCaster != null && shadowsEnabled && shadowLight != null;
        int pooled = 0;
        if (shadowCaster != null) {
            DirectionalLight light = casting ? shadowLight : pooledDirectionalLight(pooled++);
            setDirectionalLight(light, shadowCaster.getColor(), shadowCaster.getIntensity(), shadowCaster.getDirection());
        }
        for (DirectionalLightComponent component : sceneDirectionalLights) {
            if (component != shadowCaster) {
                setDirectionalLight(pooledDirectionalLight(pooled++), component.getColor(), component.getIntensity(),
                    component.getDirection());
            }
        }
        if (sceneDirectionalLights.isEmpty() && defaultLightEnabled) {
            setDirectionalLight(pooledDirectionalLight(pooled), DEFAULT_LIGHT_COLOR, 1f, DEFAULT_LIGHT_DIRECTION);
        }
        if (directionalLightsAttribute.lights.size > 0) {
            environment.set(directionalLightsAttribute);
        }
        if (casting) {
            environment.shadowMap = shadowLight;
            environment.set(shadowBiasAttribute);
        }

        // Point lights at their entity positions
        pointLightsAttribute.lights.clear();
        if (activeScene != null && activeScene.isActive()) {
            int pointIndex = 0;
            for (Entity entity : activeScene.getAllEntities()) {
                if (!entity.isActiveInHierarchy()) continue;
                for (Component comp : entity.getAllComponents()) {
                    if (comp.isEnabled() && comp instanceof PointLightComponent pointLight) {
                        PointLight light = pooledPointLight(pointIndex++);
                        float scale = pointLight.getIntensity() * exposure * PBR_DIRECT_LIGHT_SCALE;
                        toLinear(pointLight.getColor(), scale);
                        light.set(linearColor, entity.getTransform().getWorldPosition(), pointLight.getRange());
                        pointLightsAttribute.lights.add(light);
                    }
                }
            }
        }
        if (pointLightsAttribute.lights.size > 0) {
            environment.set(pointLightsAttribute);
        }

        // Sky light (image-based) or flat ambient
        if (skyLight != null && ensureSkyLightMaps()) {
            float level = skyLight.getIntensity() * exposure;
            // With image-based lighting the PBR shader uses the ambient color as the sky light's multiplier
            // (assigned per field: Color.set clamps to 1)
            ambientAttribute.color.r = level;
            ambientAttribute.color.g = level;
            ambientAttribute.color.b = level;
            ambientAttribute.color.a = 1f;
            skyLightMaps.applyTo(environment);
        } else {
            releaseSkyLightMaps();
            toLinear(ambientLight, exposure);
            ambientAttribute.color.set(linearColor);
        }
        environment.set(ambientAttribute);
    }

    /** Finds the scene's directional lights, shadow caster, sky light and tone mapping. */
    private void scanSceneLighting() {
        sceneDirectionalLights.clear();
        shadowCaster = null;
        skyLight = null;
        ToneMappingComponent toneMapping = null;
        if (activeScene != null && activeScene.isActive()) {
            for (Entity entity : activeScene.getAllEntities()) {
                if (!entity.isActiveInHierarchy()) continue;
                for (Component comp : entity.getAllComponents()) {
                    if (!comp.isEnabled()) continue;
                    if (comp instanceof DirectionalLightComponent light) {
                        sceneDirectionalLights.add(light);
                        if (shadowCaster == null && light.isCastShadows()) {
                            shadowCaster = light;
                        }
                    } else if (comp instanceof SkyLightComponent sky && skyLight == null) {
                        skyLight = sky;
                    } else if (comp instanceof ToneMappingComponent tone && toneMapping == null) {
                        toneMapping = tone;
                    }
                }
            }
        }
        exposure = toneMapping == null ? 1f : toneMapping.getExposure();
        requestedOperator = toneMapping == null ? ToneMappingComponent.Operator.NONE : toneMapping.getOperator();
    }

    private void setDirectionalLight(DirectionalLight light, Color color, float intensity, Vector3 direction) {
        toLinear(color, intensity * exposure * PBR_DIRECT_LIGHT_SCALE);
        light.set(linearColor, direction);
        directionalLightsAttribute.lights.add(light);
    }

    private DirectionalLight pooledDirectionalLight(int index) {
        while (directionalLightPool.size() <= index) {
            directionalLightPool.add(new DirectionalLight());
        }
        return directionalLightPool.get(index);
    }

    private PointLight pooledPointLight(int index) {
        while (pointLightPool.size() <= index) {
            pointLightPool.add(new PointLight());
        }
        return pointLightPool.get(index);
    }

    /**
     * Sets {@link #linearColor} to the linear-space version of the sRGB {@code color}, times {@code scale}. Fields
     * are assigned directly: {@code Color.set(r, g, b, a)} clamps to 1, which would cap light above white.
     */
    private void toLinear(Color color, float scale) {
        linearColor.r = (float) Math.pow(color.r, DISPLAY_GAMMA) * scale;
        linearColor.g = (float) Math.pow(color.g, DISPLAY_GAMMA) * scale;
        linearColor.b = (float) Math.pow(color.b, DISPLAY_GAMMA) * scale;
        linearColor.a = 1f;
    }

    /** Bakes the sky light maps when there are none or the sky colors or quality changed. */
    private boolean ensureSkyLightMaps() {
        Color sky = skyLight.getSkyColor();
        Color ground = skyLight.getGroundColor();
        if (skyLightMaps != null && sky.equals(bakedSkyColor) && ground.equals(bakedGroundColor) && quality == bakedQuality) {
            return true;
        }
        releaseSkyLightMaps();
        Vector3 sunDirection = shadowCaster != null ? shadowCaster.getDirection() : DEFAULT_LIGHT_DIRECTION;
        // Baking renders into the builder's own frame buffers, which rebinds the default one
        saveRenderTarget();
        try {
            skyLightMaps = bakeSkyLight(skyLight, sunDirection, quality);
        } finally {
            restoreRenderTarget();
        }
        bakedSkyColor.set(sky);
        bakedGroundColor.set(ground);
        bakedQuality = quality;
        return skyLightMaps != null;
    }

    private void releaseSkyLightMaps() {
        if (skyLightMaps != null) {
            SkyLightMaps maps = skyLightMaps;
            skyLightMaps = null;
            maps.dispose();
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

        // 4. Synchronize scene lights with the environment; create the shadow light the first time one is needed,
        //    and recompile the shaders when the tone mapping operator changed
        updateEnvironmentLights();
        if (shadowCaster != null && shadowsEnabled && shadowLight == null) {
            // Creating a frame buffer rebinds the default one; keep the host's target
            saveRenderTarget();
            try {
                shadowLight = createShadowLight(quality.getShadowMapSize());
            } finally {
                restoreRenderTarget();
            }
            updateEnvironmentLights();
        }
        if (requestedOperator != compiledOperator) {
            recompileForOperator(requestedOperator);
        }

        // 5. Pose and place everything to draw
        List<PrimitiveDraw> draws = collectPrimitives(activeScene);
        List<ModelDraw> modelDraws = modelAssets != null ? collectModels(activeScene) : List.of();
        drawList.clear();
        for (PrimitiveDraw draw : draws) {
            ModelInstance instance = instanceFor(draw.component());
            instance.transform.set(draw.transform());
            PBRColorAttribute baseColor = (PBRColorAttribute) instance.materials.get(0).get(PBRColorAttribute.BaseColorFactor);
            toLinear(draw.component().getColor(), 1f);
            baseColor.color.set(linearColor.r, linearColor.g, linearColor.b, draw.component().getColor().a);
            drawList.add(instance);
        }
        for (ModelDraw draw : modelDraws) {
            ModelInstance instance = posedInstanceFor(draw.component());
            if (instance != null) {
                instance.transform.set(draw.transform());
                drawList.add(instance);
            }
        }
        List<DynamicMeshDraw> dynamicDraws = dynamicMeshSource != null ? collectDynamicMeshes(activeScene) : List.of();
        for (DynamicMeshDraw draw : dynamicDraws) {
            addDynamicMesh(draw);
        }

        // 6. Shadow pass, then the lit pass with depth testing; depth test is switched off again for later 2D passes
        if (!drawList.isEmpty()) {
            if (environment.shadowMap != null && Gdx.gl != null) {
                renderShadowPass();
            }
            if (Gdx.gl != null) {
                Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
                Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
            }
            try {
                modelBatch.begin(camera.getGdxCamera());
                try {
                    for (ModelInstance instance : drawList) {
                        modelBatch.render(instance, environment);
                    }
                } finally {
                    modelBatch.end();
                }
            } finally {
                if (Gdx.gl != null) {
                    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
                }
            }
        }

        // 7. Drop instances of components that are no longer drawn (removed or disabled)
        if (instances.size() > draws.size()) {
            List<MeshPrimitiveComponent> drawn = new ArrayList<>(draws.size());
            for (PrimitiveDraw draw : draws) {
                drawn.add(draw.component());
            }
            instances.keySet().removeIf(component -> !drawn.contains(component));
        }
        releaseUndrawnDynamicMeshes(dynamicDraws);
        if (modelInstances.size() > modelDraws.size()) {
            List<ModelComponent> drawn = new ArrayList<>(modelDraws.size());
            for (ModelDraw draw : modelDraws) {
                drawn.add(draw.component());
            }
            modelInstances.keySet().removeIf(component -> !drawn.contains(component));
        }
    }

    /** Brings the graphics copy of a dynamic mesh up to date and adds its chunks to the draw list. */
    private void addDynamicMesh(DynamicMeshDraw draw) {
        DynamicMeshComponent component = draw.component();
        DynamicMesh mesh = dynamicMeshSource.find(component.getMeshPath());
        if (mesh == null) {
            component.setBounds(null);
            DynamicMeshGpu<GlChunkTarget> stale = dynamicMeshes.remove(component);
            if (stale != null) {
                stale.dispose();
            }
            return;
        }
        DynamicMeshGpu<GlChunkTarget> gpu = dynamicMeshes.get(component);
        if (gpu == null || gpu.getSource() != mesh) {
            if (gpu != null) {
                gpu.dispose();
            }
            gpu = new DynamicMeshGpu<>(mesh, GlChunkTarget::new);
            dynamicMeshes.put(component, gpu);
        }
        gpu.sync();
        boolean wireframe = dynamicMeshSource.isWireframe();
        component.setBounds(mesh.getBounds(dynamicBounds));
        Color color = component.getColor();
        toLinear(color, 1f);
        for (int chunk = 0; chunk < gpu.getChunkCount(); chunk++) {
            GlChunkTarget target = gpu.getTarget(chunk);
            target.setColor(linearColor, color.a);
            target.getInstance().transform.set(draw.transform());
            drawList.add(target.getInstance());
            if (wireframe) {
                target.getWireInstance().transform.set(draw.transform());
                drawList.add(target.getWireInstance());
            }
        }
    }

    /** Disposes the graphics copies of dynamic meshes whose components are removed, disabled or inactive. */
    private void releaseUndrawnDynamicMeshes(List<DynamicMeshDraw> drawn) {
        if (dynamicMeshes.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<DynamicMeshComponent, DynamicMeshGpu<GlChunkTarget>>> entries =
            dynamicMeshes.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<DynamicMeshComponent, DynamicMeshGpu<GlChunkTarget>> entry = entries.next();
            boolean stillDrawn = false;
            for (DynamicMeshDraw draw : drawn) {
                stillDrawn |= draw.component() == entry.getKey();
            }
            if (!stillDrawn) {
                entries.remove();
                entry.getValue().dispose();
            }
        }
    }

    /** Renders scene depth from the shadow caster into the shadow map, then restores the host's frame buffer. */
    private void renderShadowPass() {
        if (shadowBatch == null) {
            shadowBatch = createShadowBatch();
        }
        var view = camera.getGdxCamera();
        shadowBounds.fit(view.position, view.direction, shadowLight.direction, shadowDistance, quality.getShadowMapSize());
        shadowLight.setViewport(shadowBounds.getSize(), shadowBounds.getSize(), 0f, 2f * shadowBounds.getDepth());
        shadowLight.setCenter(shadowBounds.getCenter());

        saveRenderTarget();
        shadowLight.begin();
        try {
            shadowBatch.begin(shadowLight.getCamera());
            try {
                for (ModelInstance instance : drawList) {
                    shadowBatch.render(instance);
                }
            } finally {
                shadowBatch.end();
            }
        } finally {
            shadowLight.end();
            restoreRenderTarget();
        }
    }

    /**
     * Remembers the bound frame buffer and viewport. LibGDX 1.14 frame buffers do not nest: creating one or calling
     * {@code end()} binds the default frame buffer, which would send the rest of the frame past a host's off-screen
     * target. No-op without a GL context.
     */
    private void saveRenderTarget() {
        if (Gdx.gl == null) {
            return;
        }
        if (glQuery == null) {
            glQuery = BufferUtils.newIntBuffer(GL_INT_QUERY_SIZE);
        }
        glQuery.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, glQuery);
        savedFrameBuffer = glQuery.get(0);
        glQuery.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, glQuery);
        glQuery.get(savedViewport, 0, savedViewport.length);
    }

    /** Rebinds what {@link #saveRenderTarget()} remembered. */
    private void restoreRenderTarget() {
        if (Gdx.gl == null) {
            return;
        }
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, savedFrameBuffer);
        Gdx.gl.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
    }

    private void recompileForOperator(ToneMappingComponent.Operator operator) {
        ModelBatch previous = modelBatch;
        compiledOperator = operator;
        modelBatch = createModelBatch();
        if (previous != null) {
            previous.dispose();
        }
    }

    /**
     * Loads the component's model if needed, reports what was loaded back to the component, and poses the
     * instance at the component's clip and time.
     *
     * @return the posed instance, or null if the model failed to load (the reason is in the component's info)
     */
    ModelInstance posedInstanceFor(ModelComponent component) {
        ModelAssets.LoadedModel loaded = modelAssets.get(component.getModelPath());
        ModelComponent.ModelInfo info = loaded != null ? loaded.info() : modelAssets.info(component.getModelPath());
        if (!info.equals(component.getModelInfo())) {
            component.setModelInfo(info);
        }
        if (loaded == null) {
            modelInstances.remove(component);
            return null;
        }
        CachedModel cached = modelInstances.get(component);
        if (cached == null || cached.model() != loaded.model()) {
            ModelInstance instance = new ModelInstance(loaded.model());
            cached = new CachedModel(loaded.model(), instance, new ModelPoser(instance));
            modelInstances.put(component, cached);
        }
        cached.poser().pose(component.getAnimation(), component.getTime());
        return cached.instance();
    }

    /**
     * The posed instance last drawn for {@code component} (for skeleton overlays), or null if it has not been
     * drawn. Owned by this renderer; callers must not keep it across frames.
     */
    public ModelInstance getModelInstance(ModelComponent component) {
        CachedModel cached = modelInstances.get(component);
        return cached == null ? null : cached.instance();
    }

    public ModelAssets getModelAssets() {
        return modelAssets;
    }

    /**
     * Source of imported models; null (the default) skips {@link ModelComponent}s. The caller keeps ownership and
     * disposes it after this renderer.
     */
    public void setModelAssets(ModelAssets modelAssets) {
        this.modelAssets = modelAssets;
        modelInstances.clear();
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

    public boolean isShadowsEnabled() {
        return shadowsEnabled;
    }

    /** Master switch for shadows, on top of each light's own setting. Default true. */
    public void setShadowsEnabled(boolean shadowsEnabled) {
        this.shadowsEnabled = shadowsEnabled;
    }

    public float getShadowDistance() {
        return shadowDistance;
    }

    /**
     * Width of the shadowed area in front of the camera, in world units. Larger values shadow more of the scene
     * with blurrier edges. Default {@link #DEFAULT_SHADOW_DISTANCE}.
     *
     * @throws IllegalArgumentException if not a finite value above 0
     */
    public void setShadowDistance(float shadowDistance) {
        if (!(shadowDistance > 0f) || Float.isInfinite(shadowDistance)) {
            throw new IllegalArgumentException("shadowDistance must be a finite value > 0: " + shadowDistance);
        }
        this.shadowDistance = shadowDistance;
    }

    public RenderQuality3D getQuality() {
        return quality;
    }

    /**
     * Lighting quality preset. Resizes the shadow map and rebuilds the sky light maps on the next frame. Default
     * {@link RenderQuality3D#MEDIUM}.
     */
    public void setQuality(RenderQuality3D quality) {
        this.quality = Objects.requireNonNull(quality, "quality cannot be null");
        if (shadowLight != null) {
            shadowLight.setShadowMapSize(quality.getShadowMapSize(), quality.getShadowMapSize());
        }
    }

    /**
     * The light that casts shadows according to the last environment update: the first enabled, active directional
     * light with shadows on. Null when there is none. Shadows are drawn only when {@link #isShadowsEnabled()} too.
     */
    public DirectionalLightComponent getShadowCaster() {
        return shadowCaster;
    }

    /** Exposure found by the last environment update (1 without a {@link ToneMappingComponent}). */
    public float getExposure() {
        return exposure;
    }

    /** Tone mapping operator the scene asks for, as of the last environment update. */
    public ToneMappingComponent.Operator getToneMappingOperator() {
        return requestedOperator;
    }

    /** Sky light maps in use, or null when the scene has no sky light. Owned by this renderer. */
    public SkyLightMaps getSkyLightMaps() {
        return skyLightMaps;
    }

    /**
     * Disposes all GPU resources this renderer owns. Every resource is attempted; failures are reported together
     * as a {@link ResourceDisposalException} (TD-008).
     * Idempotent.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        initialized = false;

        instances.clear();
        modelInstances.clear();
        drawList.clear();
        ResourceManager owned = new ResourceManager();
        for (DynamicMeshGpu<GlChunkTarget> gpu : dynamicMeshes.values()) {
            owned.register(gpu);
        }
        dynamicMeshes.clear();
        for (Model model : unitModels.values()) {
            owned.register(model);
        }
        unitModels.clear();
        registerIfPresent(owned, modelBatch);
        registerIfPresent(owned, shadowBatch);
        registerIfPresent(owned, shadowLight);
        registerIfPresent(owned, skyLightMaps);
        modelBatch = null;
        shadowBatch = null;
        shadowLight = null;
        skyLightMaps = null;
        if (environment != null) {
            environment.shadowMap = null;
        }
        owned.dispose();
    }

    private static void registerIfPresent(ResourceManager owner, Disposable resource) {
        if (resource != null) {
            owner.register(resource);
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

    /** Flat ambient light (sRGB) used when the scene has no {@link SkyLightComponent}. */
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
