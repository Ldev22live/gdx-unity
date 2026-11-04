package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.math.collision.BoundingBox;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shows an imported glTF model (TD-030) and plays one of its own animations, such as a skeletal walk cycle.
 *
 * <p>The scene stores the asset path and playback settings. The renderer loads the file and reports what it found
 * through {@link #setModelInfo}: clip names and lengths, and the model's bounds. Playback advances during play
 * ({@link #onUpdate}); in the editor the pose follows {@link #getTime()}, which the Timeline sets while previewing.
 */
public class ModelComponent extends BaseComponent {

    /** glTF files are in metres; scenes use 100 units per metre (the 3D physics scale, TD-028). */
    public static final float DEFAULT_IMPORT_SCALE = 100f;

    /** What the renderer learned from the loaded file; {@link #NONE} until it has been loaded. */
    public record ModelInfo(Map<String, Float> animationDurations, BoundingBox bounds, int boneCount, String error) {
        public static final ModelInfo NONE = new ModelInfo(Map.of(), null, 0, null);

        public ModelInfo {
            animationDurations = Map.copyOf(Objects.requireNonNull(animationDurations, "animationDurations cannot be null"));
        }

        public static ModelInfo failed(String error) {
            return new ModelInfo(Map.of(), null, 0, Objects.requireNonNull(error, "error cannot be null"));
        }

        /** Clip names, sorted. */
        public List<String> animationNames() {
            return animationDurations.keySet().stream().sorted().toList();
        }
    }

    private String modelPath;
    private float importScale = DEFAULT_IMPORT_SCALE;
    private String animation;
    private boolean loop = true;
    private boolean playOnStart = true;
    private float speed = 1f;

    private float time;
    private boolean playing;
    private ModelInfo info = ModelInfo.NONE;

    public ModelComponent(String modelPath) {
        setModelPath(modelPath);
    }

    /** Asset path of the {@code .gltf} or {@code .glb} file, relative to the asset root. */
    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        Objects.requireNonNull(modelPath, "modelPath cannot be null");
        if (modelPath.isBlank()) {
            throw new IllegalArgumentException("modelPath cannot be blank");
        }
        if (!modelPath.equals(this.modelPath)) {
            this.modelPath = modelPath;
            this.info = ModelInfo.NONE;
            this.time = 0f;
        }
    }

    /** Scene units per model unit. */
    public float getImportScale() {
        return importScale;
    }

    public void setImportScale(float importScale) {
        if (!(importScale > 0f) || Float.isInfinite(importScale)) {
            throw new IllegalArgumentException("importScale must be positive, got: " + importScale);
        }
        this.importScale = importScale;
    }

    /** Name of the model's clip to show, or null for the rest pose. */
    public String getAnimation() {
        return animation;
    }

    public void setAnimation(String animation) {
        String clean = (animation == null || animation.isBlank()) ? null : animation;
        if (!Objects.equals(clean, this.animation)) {
            this.animation = clean;
            this.time = 0f;
        }
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public boolean isPlayOnStart() {
        return playOnStart;
    }

    public void setPlayOnStart(boolean playOnStart) {
        this.playOnStart = playOnStart;
    }

    /** Playback rate; negative plays backwards. */
    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        if (!Float.isFinite(speed)) {
            throw new IllegalArgumentException("speed must be finite, got: " + speed);
        }
        this.speed = speed;
    }

    /** Position in the current clip, in seconds. Not saved. */
    public float getTime() {
        return time;
    }

    /** Sets the clip position; it is wrapped (loop) or clamped to the clip once the clip's length is known. */
    public void setTime(float time) {
        if (!Float.isFinite(time)) {
            throw new IllegalArgumentException("time must be finite, got: " + time);
        }
        this.time = fitTime(time);
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public ModelInfo getModelInfo() {
        return info;
    }

    /** Called by the renderer after loading (or failing to load) {@link #getModelPath()}. */
    public void setModelInfo(ModelInfo info) {
        this.info = Objects.requireNonNull(info, "info cannot be null");
        this.time = fitTime(time);
    }

    /** Length of the selected clip in seconds, or 0 when there is none or the model is not loaded yet. */
    public float getAnimationDuration() {
        if (animation == null) {
            return 0f;
        }
        Float duration = info.animationDurations().get(animation);
        return duration == null ? 0f : duration;
    }

    @Override
    public void onStart() {
        super.onStart();
        playing = playOnStart && animation != null;
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (!playing || animation == null) {
            return;
        }
        float duration = getAnimationDuration();
        if (duration <= 0f) {
            return;
        }
        float next = time + deltaTime * speed;
        if (!loop && (next >= duration || next <= 0f)) {
            playing = false;
        }
        time = fitTime(next);
    }

    private float fitTime(float t) {
        float duration = getAnimationDuration();
        if (duration <= 0f) {
            return t;
        }
        if (loop) {
            float wrapped = t % duration;
            return wrapped < 0f ? wrapped + duration : wrapped;
        }
        return Math.max(0f, Math.min(duration, t));
    }
}
