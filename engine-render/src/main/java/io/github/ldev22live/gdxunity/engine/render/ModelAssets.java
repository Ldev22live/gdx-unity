package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceDisposalException;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent.ModelInfo;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.loaders.gltf.GLTFLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Loads glTF 2.0 models ({@code .gltf}, {@code .glb}) from the asset root with gdx-gltf (TD-030) and owns them.
 * Each file is loaded once; a failure is remembered with its reason so a broken file is not reloaded every frame.
 * gdx-gltf types never leave this class: callers get a LibGDX {@link Model} and a {@link ModelInfo}.
 */
public class ModelAssets implements Disposable {

    /** Bones the skinning shader is configured for; models with more are refused with a clear message. */
    public static final int MAX_BONES = 64;

    /** A loaded model: shared by every entity showing the same file. */
    public record LoadedModel(Model model, ModelInfo info) {}

    private final AssetSource source;
    private final Map<String, SceneAsset> loaded = new LinkedHashMap<>();
    private final Map<String, ModelInfo> infos = new HashMap<>();
    private boolean disposed;

    /** Loads models from a project folder on disk. */
    public ModelAssets(File assetRoot) {
        this(AssetSource.directory(Objects.requireNonNull(assetRoot, "assetRoot cannot be null")));
    }

    /** Loads models through an asset source, such as the web runtime's bundled files (TD-036). */
    public ModelAssets(AssetSource source) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
    }

    /**
     * The model at {@code path}, loading it on first use (needs a GL context).
     *
     * @return the model, or null if it failed to load; {@link #info} then holds the reason
     */
    public LoadedModel get(String path) {
        if (disposed) {
            throw new IllegalStateException("ModelAssets has been disposed");
        }
        String key = new AssetReference(path).getPath();
        if (!infos.containsKey(key)) {
            load(key);
        }
        SceneAsset asset = loaded.get(key);
        return asset == null ? null : new LoadedModel(asset.scene.model, infos.get(key));
    }

    /** What is known about {@code path}: {@link ModelInfo#NONE} if it was never requested. */
    public ModelInfo info(String path) {
        return infos.getOrDefault(new AssetReference(path).getPath(), ModelInfo.NONE);
    }

    /** Forgets {@code path} so the next {@link #get} reads the file again (after it changed on disk). */
    public void reload(String path) {
        String key = new AssetReference(path).getPath();
        infos.remove(key);
        SceneAsset asset = loaded.remove(key);
        if (asset != null) {
            asset.dispose();
        }
    }

    private void load(String key) {
        FileHandle handle = source.resolve(new AssetReference(key));
        if (!handle.exists() || handle.isDirectory()) {
            infos.put(key, ModelInfo.failed("Model file not found: " + key));
            return;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        SceneAsset asset;
        try {
            asset = lower.endsWith(".glb") ? new GLBLoader().load(handle) : new GLTFLoader().load(handle);
        } catch (RuntimeException e) {
            // The parser reports malformed or unsupported files with assorted runtime exceptions; each becomes a
            // remembered load error shown to the user rather than a crash of the frame
            infos.put(key, ModelInfo.failed("Cannot load " + key + ": " + rootMessage(e)));
            return;
        }
        if (asset.maxBones > MAX_BONES) {
            asset.dispose();
            infos.put(key, ModelInfo.failed(key + " has " + asset.maxBones + " bones per mesh; up to "
                + MAX_BONES + " are supported"));
            return;
        }
        loaded.put(key, asset);
        infos.put(key, describe(asset.scene.model, asset.maxBones));
    }

    /** Clip lengths, rest-pose bounds and bone count of a loaded model. */
    static ModelInfo describe(Model model, int boneCount) {
        Map<String, Float> durations = new LinkedHashMap<>();
        for (Animation animation : model.animations) {
            durations.put(animation.id, animation.duration);
        }
        BoundingBox bounds = new ModelInstance(model).calculateBoundingBox(new BoundingBox());
        return new ModelInfo(durations, bounds.isValid() ? bounds : null, boneCount, null);
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }

    /** Disposes every loaded model; failures are collected and reported after all were attempted (TD-008). */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        List<Throwable> failures = new ArrayList<>();
        for (SceneAsset asset : loaded.values()) {
            try {
                asset.dispose();
            } catch (RuntimeException e) {
                failures.add(e);
            }
        }
        loaded.clear();
        infos.clear();
        if (!failures.isEmpty()) {
            ResourceDisposalException error = new ResourceDisposalException("Failed to dispose " + failures.size() + " model(s)");
            failures.forEach(error::addSuppressed);
            throw error;
        }
    }
}
