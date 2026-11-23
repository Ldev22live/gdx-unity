package io.github.ldev22live.gdxunity.engine.animation;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * Plays an animation clip asset on its entity and the entity's children during play (TD-031). The scene stores the
 * clip path and settings; whoever starts play loads the clip and {@link #bind binds} it. Unbound, it does nothing.
 *
 * <p>Java, Kotlin and graph scripts can call {@link #play()}, {@link #pause()}, {@link #stop()} and
 * {@link #setTime(float)}.
 */
public class AnimatorComponent extends BaseComponent {

    private String clipPath;
    private boolean playOnStart = true;
    private WrapMode wrapMode = WrapMode.LOOP;
    private float speed = 1f;

    private AnimationClip clip;
    private AnimatedProperties properties;
    private float time;
    private boolean playing;
    /** +1 or -1: direction of travel in {@link WrapMode#PING_PONG}. */
    private int direction = 1;

    public AnimatorComponent(String clipPath) {
        setClipPath(clipPath);
    }

    /** Asset path of the {@code .anim} file, relative to the asset root. */
    public String getClipPath() {
        return clipPath;
    }

    public void setClipPath(String clipPath) {
        Objects.requireNonNull(clipPath, "clipPath cannot be null");
        if (clipPath.isBlank()) {
            throw new IllegalArgumentException("clipPath cannot be blank");
        }
        this.clipPath = clipPath;
    }

    public boolean isPlayOnStart() {
        return playOnStart;
    }

    public void setPlayOnStart(boolean playOnStart) {
        this.playOnStart = playOnStart;
    }

    public WrapMode getWrapMode() {
        return wrapMode;
    }

    public void setWrapMode(WrapMode wrapMode) {
        this.wrapMode = Objects.requireNonNull(wrapMode, "wrapMode cannot be null");
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

    /** Attaches the loaded clip; playback starts on the next start or {@link #play()}. */
    public void bind(AnimationClip clip, AnimatedProperties properties) {
        this.clip = Objects.requireNonNull(clip, "clip cannot be null");
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.time = speed < 0f ? clip.getDuration() : 0f;
        this.direction = 1;
        this.playing = isStarted() && playOnStart;
    }

    public void unbind() {
        clip = null;
        properties = null;
        playing = false;
    }

    /** The bound clip, or null while not playing. */
    public AnimationClip getClip() {
        return clip;
    }

    public float getTime() {
        return time;
    }

    /** Jumps to {@code time} (clamped to the clip) and applies the pose immediately. */
    public void setTime(float time) {
        if (clip == null) {
            return;
        }
        this.time = Math.max(0f, Math.min(clip.getDuration(), time));
        applyPose();
    }

    public boolean isPlaying() {
        return playing;
    }

    public void play() {
        if (clip != null) {
            playing = true;
        }
    }

    public void pause() {
        playing = false;
    }

    /** Stops and returns to the start of the clip (the end when the speed is negative). */
    public void stop() {
        playing = false;
        direction = 1;
        if (clip != null) {
            setTime(speed < 0f ? clip.getDuration() : 0f);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (clip != null) {
            playing = playOnStart;
            applyPose();
        }
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (clip == null || !playing) {
            return;
        }
        advance(deltaTime);
        applyPose();
    }

    /** Moves the play position by {@code deltaTime} seconds and handles the clip ends per {@link #getWrapMode()}. */
    void advance(float deltaTime) {
        float duration = clip.getDuration();
        float next = time + deltaTime * speed * direction;
        switch (wrapMode) {
            case ONCE -> {
                if (next >= duration || next <= 0f) {
                    playing = false;
                }
                time = Math.max(0f, Math.min(duration, next));
            }
            case LOOP -> {
                float wrapped = next % duration;
                time = wrapped < 0f ? wrapped + duration : wrapped;
            }
            case PING_PONG -> {
                // Reflect off each end as many times as the step crosses it
                while (next > duration || next < 0f) {
                    next = next > duration ? 2f * duration - next : -next;
                    direction = -direction;
                }
                time = next;
            }
        }
    }

    private void applyPose() {
        if (getEntity() != null) {
            ClipSampler.apply(clip, time, getEntity(), properties);
        }
    }

    @Override
    public void onDetach() {
        unbind();
        super.onDetach();
    }
}
