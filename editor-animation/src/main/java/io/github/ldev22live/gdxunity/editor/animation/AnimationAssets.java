package io.github.ldev22live.gdxunity.editor.animation;

import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClipSerializer;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

/** Creates, reads and writes {@code .anim} clip files under the asset root (TD-031). */
public class AnimationAssets {
    /** Folder under the asset root where new clips are created. */
    public static final String ANIMATION_FOLDER = "animations";
    public static final String EXTENSION = ".anim";

    private final File assetRoot;

    public AnimationAssets(File assetRoot) {
        this.assetRoot = Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
    }

    /** Creates an empty clip {@code animations/<name>.anim} (or {@code <name> 1.anim}, ... if taken). */
    public AssetReference create(String name) throws IOException {
        String base = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (base.isEmpty()) {
            base = "Clip";
        }
        String path = ANIMATION_FOLDER + "/" + base + EXTENSION;
        for (int n = 1; fileOf(path).exists(); n++) {
            path = ANIMATION_FOLDER + "/" + base + " " + n + EXTENSION;
        }
        AssetReference asset = new AssetReference(path);
        save(asset, new AnimationClip(base));
        return asset;
    }

    /** Reads the clip fresh from disk. */
    public AnimationClip load(AssetReference asset) throws IOException {
        File file = fileOf(asset.getPath());
        if (!file.isFile()) {
            throw new IOException("Animation not found: " + asset.getPath());
        }
        return AnimationClipSerializer.fromJson(Files.readString(file.toPath()));
    }

    public void save(AssetReference asset, AnimationClip clip) throws IOException {
        File file = fileOf(asset.getPath());
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }
        Files.writeString(file.toPath(), AnimationClipSerializer.toJson(clip));
    }

    private File fileOf(String path) {
        return new File(assetRoot, new AssetReference(path).getPath());
    }
}
