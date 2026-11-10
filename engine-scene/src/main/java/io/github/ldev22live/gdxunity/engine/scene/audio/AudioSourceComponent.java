package io.github.ldev22live.gdxunity.engine.scene.audio;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Objects;

/**
 * A sound effect an entity can play (TD-043). It names a generated sound ({@link #getSound()}, an sfxr preset, or a recorded
 * {@code .wav} or {@code .ogg} clip in the project, Phase 20-C) and a
 * <em>cue</em>, the name scripts and graphs use to trigger it: {@code AudioSourceComponent.play(scene, "jump")}.
 * Nothing sounds outside Play: the host binds a {@link SoundSink} when the scene starts, and an unbound component
 * ignores {@link #play()}.
 */
public class AudioSourceComponent extends BaseComponent {
    /** Lowest pitch multiplier accepted: two octaves down. */
    public static final float MIN_PITCH = 0.25f;
    /** Highest pitch multiplier accepted: two octaves up. */
    public static final float MAX_PITCH = 4f;
    /** The sound a new component plays. */
    public static final String DEFAULT_SOUND = "Coin";
    /** The cue a new component answers to. */
    public static final String DEFAULT_CUE = "sound";
    /** Up to this far from the listener (scene units) a spatial source is at full volume. */
    public static final float DEFAULT_MIN_DISTANCE = 100f;
    /** This far from the listener and beyond, a spatial source is silent. */
    public static final float DEFAULT_MAX_DISTANCE = 2000f;
    /** Sounds with one of these extensions are recorded clips read from the project, not factory effects. */
    public static final java.util.List<String> CLIP_EXTENSIONS = java.util.List.of(".wav", ".ogg");

    private String cue = DEFAULT_CUE;
    private String sound = DEFAULT_SOUND;
    private float volume = 1f;
    private float pitch = 1f;
    private boolean playOnStart;
    private boolean spatial;
    private float minDistance = DEFAULT_MIN_DISTANCE;
    private float maxDistance = DEFAULT_MAX_DISTANCE;
    private SoundSink sink;

    /** Creates a source that plays {@link #DEFAULT_SOUND} for the cue {@link #DEFAULT_CUE}. */
    public AudioSourceComponent() {
        super();
    }

    /**
     * @param cue   the name scripts trigger it by
     * @param sound the sound to play, an sfxr preset name
     */
    public AudioSourceComponent(String cue, String sound) {
        setCue(cue);
        setSound(sound);
    }

    /** The name scripts and graphs trigger this source by; never blank. */
    public String getCue() {
        return cue;
    }

    /** @throws IllegalArgumentException if {@code cue} is null or blank */
    public void setCue(String cue) {
        this.cue = requireText(cue, "cue");
    }

    /** The generated sound this source plays (an sfxr preset name); never blank. */
    public String getSound() {
        return sound;
    }

    /** @throws IllegalArgumentException if {@code sound} is null or blank */
    public void setSound(String sound) {
        this.sound = requireText(sound, "sound");
    }

    /** Linear gain, 0 to 1. */
    public float getVolume() {
        return volume;
    }

    /** @throws IllegalArgumentException if {@code volume} is not within 0 to 1 */
    public void setVolume(float volume) {
        if (!(volume >= 0f && volume <= 1f)) {
            throw new IllegalArgumentException("Volume must be within 0 to 1: " + volume);
        }
        this.volume = volume;
    }

    /** Playback speed multiplier, {@value #MIN_PITCH} to {@value #MAX_PITCH}. */
    public float getPitch() {
        return pitch;
    }

    /** @throws IllegalArgumentException if {@code pitch} is outside {@value #MIN_PITCH} to {@value #MAX_PITCH} */
    public void setPitch(float pitch) {
        if (!(pitch >= MIN_PITCH && pitch <= MAX_PITCH)) {
            throw new IllegalArgumentException("Pitch must be within " + MIN_PITCH + " to " + MAX_PITCH + ": " + pitch);
        }
        this.pitch = pitch;
    }

    /** Whether the sound plays once when Play starts. */
    public boolean isPlayOnStart() {
        return playOnStart;
    }

    public void setPlayOnStart(boolean playOnStart) {
        this.playOnStart = playOnStart;
    }

    /** Whether the sound is a recorded clip (a {@code .wav} or {@code .ogg} file in the project) rather than a factory effect. */
    public boolean isClip() {
        return isClip(sound);
    }

    /** Whether {@code sound} names a recorded clip, by its extension. */
    public static boolean isClip(String sound) {
        if (sound == null) {
            return false;
        }
        String lower = sound.toLowerCase(java.util.Locale.ROOT);
        return CLIP_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    /**
     * Whether the sound is heard from the scene's {@link AudioListenerComponent}: quieter with distance and moved between the
     * speakers. Off by default, so a sound plays at its own volume in the middle. A spatial source in a scene with no listener
     * also plays that way.
     */
    public boolean isSpatial() {
        return spatial;
    }

    public void setSpatial(boolean spatial) {
        this.spatial = spatial;
    }

    /** Up to this distance from the listener, in scene units, a spatial source is at full volume. */
    public float getMinDistance() {
        return minDistance;
    }

    /** Beyond this distance from the listener, in scene units, a spatial source cannot be heard. */
    public float getMaxDistance() {
        return maxDistance;
    }

    /** @throws IllegalArgumentException unless {@code 0 <= minDistance < maxDistance} */
    public void setDistances(float minDistance, float maxDistance) {
        if (!(minDistance >= 0f && maxDistance > minDistance)) {
            throw new IllegalArgumentException("Distances must satisfy 0 <= min < max: " + minDistance + ", " + maxDistance);
        }
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
    }

    /** Whether a host is listening, that is, whether {@link #play()} makes a sound. */
    public boolean isBound() {
        return sink != null;
    }

    /** Connects this source to the host's sound output for the duration of play. */
    public void bind(SoundSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink cannot be null");
    }

    /** Disconnects from the host's sound output; {@link #play()} does nothing afterwards. */
    public void unbind() {
        this.sink = null;
    }

    /** Plays the sound once, at this component's volume and pitch. Does nothing while unbound or disabled. */
    public void play() {
        if (sink == null || !isEnabled()) {
            return;
        }
        Scene scene = getEntity() != null ? getEntity().getScene() : null;
        AudioListenerComponent listener = spatial && scene != null ? AudioListenerComponent.find(scene) : null;
        if (listener == null || listener.getEntity() == null) {
            sink.play(sound, volume, pitch);
            return;
        }
        var ears = listener.getEntity().getTransform();
        SpatialAudio.Heard heard = SpatialAudio.hear(ears.getWorldPosition(),
            new com.badlogic.gdx.math.Vector3(1f, 0f, 0f).mul(ears.getWorldRotation()),
            getEntity().getTransform().getWorldPosition(), minDistance, maxDistance);
        if (heard.gain() > 0f) {
            sink.play(sound, volume * heard.gain(), pitch, heard.pan());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (playOnStart) {
            play();
        }
    }

    @Override
    public void onDetach() {
        sink = null;
        super.onDetach();
    }

    /**
     * Plays every enabled source in {@code scene} whose cue is {@code cue}, on active entities only.
     *
     * @return how many sources played; 0 means no source has that cue (or the scene is not playing)
     */
    public static int play(Scene scene, String cue) {
        Objects.requireNonNull(scene, "scene cannot be null");
        Objects.requireNonNull(cue, "cue cannot be null");
        int played = 0;
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy()) {
                continue;
            }
            for (AudioSourceComponent source : entity.getComponents(AudioSourceComponent.class)) {
                if (source.isBound() && source.isEnabled() && source.cue.equals(cue)) {
                    source.play();
                    played++;
                }
            }
        }
        return played;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be null or blank");
        }
        return value.trim();
    }
}
