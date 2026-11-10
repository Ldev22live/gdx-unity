package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Disposable;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.loaders.gltf.GLTFLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.shaders.PBRShaderConfig;
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Draws one glTF model on its own, outside any scene (TD-056): the character creator's live preview. It loads a glTF document
 * from memory, plays one of its animations in a loop (or shows the rest pose), and draws it with the same PBR shaders and skinning
 * as {@link SceneRenderer3D}, so what it shows is what the model looks like in the scene. The camera orbits the model and always
 * frames all of it.
 *
 * <p>Needs a GL context. The caller binds the target (a frame buffer) and passes its size to {@link #render}.
 */
public final class ModelPreview implements Disposable {
    /** Vertical field of view of the preview camera, degrees. */
    private static final float FIELD_OF_VIEW = 30f;
    /** Slack around the model so it does not touch the edges of the picture. */
    private static final float FRAMING_MARGIN = 1.15f;
    /** Largest upward or downward look angle, degrees: short of straight up so the camera never flips. */
    private static final float MAX_PITCH = 80f;
    /** Nearest and farthest the user can zoom, as a multiple of the framing distance. */
    private static final float MIN_ZOOM = 0.4f;
    private static final float MAX_ZOOM = 2.5f;
    /** Strength of the sky light on a surface it faces, in the PBR shader's units (see {@code SceneRenderer3D}). */
    private static final float KEY_LIGHT_LEVEL = 0.85f;
    private static final float FILL_LIGHT_LEVEL = 0.35f;
    private static final float AMBIENT_LEVEL = 0.12f;
    private static final float DISPLAY_GAMMA = 2.2f;
    /** Starting look: a little from the right and above, so the face and one side of the body show. */
    private static final float START_YAW = 25f;
    private static final float START_PITCH = 8f;

    private final ModelBatch batch;
    private final Environment environment = new Environment();
    private final PerspectiveCamera camera = new PerspectiveCamera(FIELD_OF_VIEW, 1f, 1f);
    private final BoundingBox bounds = new BoundingBox();
    private final Vector3 center = new Vector3();
    private SceneAsset asset;
    private ModelInstance instance;
    private AnimationController controller;
    private String animation;
    private float radius = 1f;
    private float yaw = START_YAW;
    private float pitch = START_PITCH;
    private float zoom = 1f;
    private boolean disposed;

    public ModelPreview() {
        PBRShaderConfig config = PBRShaderProvider.createDefaultConfig();
        config.numBones = ModelAssets.MAX_BONES;
        batch = new ModelBatch(PBRShaderProvider.createDefault(config));
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, AMBIENT_LEVEL, AMBIENT_LEVEL, AMBIENT_LEVEL, 1f));
        environment.add(light(KEY_LIGHT_LEVEL, -0.5f, -0.6f, -0.7f));
        environment.add(light(FILL_LIGHT_LEVEL, 0.7f, -0.2f, 0.5f));
        camera.near = 0.01f;
        camera.far = 100f;
    }

    private static DirectionalLight light(float level, float x, float y, float z) {
        float linear = (float) Math.pow(level, DISPLAY_GAMMA) * MathUtils.PI;
        return new DirectionalLight().set(linear, linear, linear, x, y, z);
    }

    /**
     * Replaces the model with the one in {@code gltfJson} (a self-contained glTF document, buffers embedded). The view angle and
     * zoom stay as the user left them; the animation that was playing is kept if the new model still has it.
     *
     * @throws IOException when the document cannot be read as glTF
     */
    public void load(String gltfJson) throws IOException {
        Objects.requireNonNull(gltfJson, "gltfJson cannot be null");
        requireLive();
        Path file = Files.createTempFile("forgeroom-preview", ".gltf");
        try {
            Files.writeString(file, gltfJson, StandardCharsets.UTF_8);
            SceneAsset loaded;
            try {
                loaded = new GLTFLoader().load(Gdx.files.absolute(file.toString()));
            } catch (RuntimeException e) {
                throw new IOException("Cannot read the preview model: " + e.getMessage(), e);
            }
            adopt(loaded);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * Replaces the model with the one in a {@code .glb} or {@code .gltf} file (a project's model, for the Content Browser's
     * thumbnails). The animation that was playing is kept if the new model still has it.
     *
     * @throws IOException when the file cannot be read as glTF
     */
    public void loadFile(FileHandle file) throws IOException {
        Objects.requireNonNull(file, "file cannot be null");
        requireLive();
        if (!file.exists() || file.isDirectory()) {
            throw new IOException("Model file not found: " + file.path());
        }
        SceneAsset loaded;
        try {
            loaded = "glb".equalsIgnoreCase(file.extension()) ? new GLBLoader().load(file) : new GLTFLoader().load(file);
        } catch (RuntimeException e) {
            throw new IOException("Cannot read the model " + file.name() + ": " + e.getMessage(), e);
        }
        adopt(loaded);
    }

    private void adopt(SceneAsset loaded) {
        SceneAsset old = asset;
        asset = loaded;
        if (old != null) {
            old.dispose();
        }
        String keep = animation;
        animation = null;
        newInstance();
        frame();
        setAnimation(keep != null && animationNames().contains(keep) ? keep : null);
    }

    /** Names of the animations in the loaded model; empty before {@link #load}. */
    public List<String> animationNames() {
        List<String> names = new ArrayList<>();
        if (asset != null) {
            asset.animations.forEach(animation -> names.add(animation.id));
        }
        return names;
    }

    /**
     * Plays an animation of the loaded model in a loop from its start, or shows the rest pose for null.
     *
     * @throws IllegalArgumentException when the model has no animation of that name
     */
    public void setAnimation(String name) {
        requireLive();
        if (name != null && !animationNames().contains(name)) {
            throw new IllegalArgumentException("No animation " + name + " in the preview model (it has " + animationNames() + ")");
        }
        animation = name;
        if (instance == null) {
            return;
        }
        newInstance();
        if (name != null) {
            controller.animate(name, -1, 1f, null, 0f);
        }
    }

    /** The animation being played, or null for the rest pose. */
    public String getAnimation() {
        return animation;
    }

    public boolean hasModel() {
        return instance != null;
    }

    /** Advances the playing animation. */
    public void update(float deltaSeconds) {
        if (controller != null && animation != null) {
            controller.update(deltaSeconds);
        }
    }

    /** Turns the camera around the model by {@code yawDegrees} and up or down by {@code pitchDegrees}. */
    public void orbit(float yawDegrees, float pitchDegrees) {
        yaw = (yaw + yawDegrees) % 360f;
        pitch = MathUtils.clamp(pitch + pitchDegrees, -MAX_PITCH, MAX_PITCH);
    }

    /** Moves the camera nearer (factor below 1) or farther (above 1), within limits. */
    public void zoomBy(float factor) {
        if (factor <= 0f) {
            throw new IllegalArgumentException("Zoom factor must be above 0: " + factor);
        }
        zoom = MathUtils.clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getZoom() {
        return zoom;
    }

    /** Back to the starting view. */
    public void resetView() {
        yaw = START_YAW;
        pitch = START_PITCH;
        zoom = 1f;
    }

    /**
     * Clears the bound target to {@code background} and draws the model into it. Does nothing but clear before a model is loaded.
     *
     * @param width  width of the target in pixels
     * @param height height of the target in pixels
     */
    public void render(Color background, int width, int height) {
        Objects.requireNonNull(background, "background cannot be null");
        requireLive();
        Gdx.gl.glClearColor(background.r, background.g, background.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        if (instance == null || width <= 0 || height <= 0) {
            return;
        }
        float aspect = width / (float) height;
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        // Frame the sphere around the model: the narrower of the two view angles decides the distance
        float verticalHalf = MathUtils.degreesToRadians * FIELD_OF_VIEW / 2f;
        float horizontalHalf = (float) Math.atan(Math.tan(verticalHalf) * aspect);
        float distance = radius * FRAMING_MARGIN / (float) Math.sin(Math.min(verticalHalf, horizontalHalf)) * zoom;
        float yawRad = yaw * MathUtils.degreesToRadians;
        float pitchRad = pitch * MathUtils.degreesToRadians;
        camera.position.set(
            center.x + distance * MathUtils.cos(pitchRad) * MathUtils.sin(yawRad),
            center.y + distance * MathUtils.sin(pitchRad),
            center.z + distance * MathUtils.cos(pitchRad) * MathUtils.cos(yawRad));
        camera.up.set(Vector3.Y);
        camera.lookAt(center);
        camera.near = Math.max(0.01f, distance - radius * 2f);
        camera.far = distance + radius * 4f;
        camera.update();
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        batch.begin(camera);
        batch.render(instance, environment);
        batch.end();
    }

    private void newInstance() {
        instance = new ModelInstance(asset.scene.model);
        controller = new AnimationController(instance);
    }

    /** Aims the camera at the middle of the model and sizes the view to its bounding sphere. */
    private void frame() {
        instance.calculateBoundingBox(bounds);
        bounds.getCenter(center);
        radius = Math.max(0.01f, bounds.getDimensions(new Vector3()).len() / 2f);
    }

    private void requireLive() {
        if (disposed) {
            throw new IllegalStateException("ModelPreview has been disposed");
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        batch.dispose();
        if (asset != null) {
            asset.dispose();
            asset = null;
        }
        instance = null;
        controller = null;
    }
}
