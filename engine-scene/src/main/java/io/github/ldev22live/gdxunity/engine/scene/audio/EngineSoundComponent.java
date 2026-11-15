package io.github.ldev22live.gdxunity.engine.scene.audio;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Objects;

/**
 * A running engine an entity carries (TD-080): a car, a boat, a generator. It names an engine <em>profile</em>
 * ({@link #getProfile()}, for example {@code Muscle V8}) and a script keeps it up to date with
 * {@link #setState(float, float)} (how fast it turns and the throttle) every frame. Unlike an {@link AudioSourceComponent}, which
 * plays a short effect once, the engine sounds all the time between the start of Play and its end, idling until the script
 * says otherwise. Nothing sounds outside Play: the host binds an {@link EngineVoice} when the scene starts, and an unbound
 * component ignores {@link #setState}.
 *
 * <p>Like an Audio Source it can be <em>spatial</em>: heard from the scene's {@link AudioListenerComponent}, quieter with
 * distance and moved between the speakers. A non-spatial engine, such as the player's own car, plays in the middle at its
 * own volume.
 */
public class EngineSoundComponent extends BaseComponent {
    /** The engine a new component sounds like. */
    public static final String DEFAULT_PROFILE = "Hatchback";
    /** Up to this far from the listener (scene units) a spatial engine is at full volume. */
    public static final float DEFAULT_MIN_DISTANCE = 300f;
    /** This far from the listener and beyond, a spatial engine is silent. */
    public static final float DEFAULT_MAX_DISTANCE = 3000f;

    private String profile = DEFAULT_PROFILE;
    private float volume = 1f;
    private boolean spatial;
    private float minDistance = DEFAULT_MIN_DISTANCE;
    private float maxDistance = DEFAULT_MAX_DISTANCE;
    private EngineVoice voice;

    /** Creates an engine that sounds like {@link #DEFAULT_PROFILE}. */
    public EngineSoundComponent() {
        super();
    }

    /** @param profile the engine to sound like, a factory profile name */
    public EngineSoundComponent(String profile) {
        setProfile(profile);
    }

    /** The engine this component sounds like (a factory profile name); never blank. */
    public String getProfile() {
        return profile;
    }

    /** @throws IllegalArgumentException if {@code profile} is null or blank */
    public void setProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            throw new IllegalArgumentException("profile cannot be null or blank");
        }
        this.profile = profile.trim();
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

    /** Whether the engine is heard from the scene's Audio Listener rather than at a fixed volume in the middle. */
    public boolean isSpatial() {
        return spatial;
    }

    public void setSpatial(boolean spatial) {
        this.spatial = spatial;
    }

    /** Up to this distance from the listener, in scene units, a spatial engine is at full volume. */
    public float getMinDistance() {
        return minDistance;
    }

    /** Beyond this distance from the listener, in scene units, a spatial engine cannot be heard. */
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

    /** Whether a host is listening, that is, whether {@link #setState} makes a sound. */
    public boolean isBound() {
        return voice != null;
    }

    /** Connects this engine to the host's sound output for the duration of play. */
    public void bind(EngineVoice voice) {
        this.voice = Objects.requireNonNull(voice, "voice cannot be null");
    }

    /** Disconnects from the host's sound output; {@link #setState} does nothing afterwards. */
    public void unbind() {
        this.voice = null;
    }

    /**
     * Tells the engine how fast it is turning and how hard the throttle is pressed. Call it every frame while the engine
     * should follow the car; between calls the engine keeps its last state. Does nothing while unbound; a disabled
     * component is silent.
     *
     * @param revs     how fast the engine turns: 0 is idling and 1 is the redline, where the rev limiter cuts in; values
     *                 outside are held at the nearest end. Scripts work in this share so they need not know the profile's rpm
     * @param throttle 0 (lifted off) to 1 (flat out); values outside are held at the nearest end
     * @throws IllegalArgumentException if either value is not a number
     */
    public void setState(float revs, float throttle) {
        if (Float.isNaN(revs) || Float.isNaN(throttle)) {
            throw new IllegalArgumentException("revs and throttle must be numbers: " + revs + ", " + throttle);
        }
        if (voice == null) {
            return;
        }
        float gain = isEnabled() ? volume : 0f;
        float pan = 0f;
        Scene scene = getEntity() != null ? getEntity().getScene() : null;
        AudioListenerComponent listener = spatial && scene != null ? AudioListenerComponent.find(scene) : null;
        if (listener != null && listener.getEntity() != null) {
            var ears = listener.getEntity().getTransform();
            SpatialAudio.Heard heard = SpatialAudio.hear(ears.getWorldPosition(),
                new Vector3(1f, 0f, 0f).mul(ears.getWorldRotation()),
                getEntity().getTransform().getWorldPosition(), minDistance, maxDistance);
            gain *= heard.gain();
            pan = heard.pan();
        }
        voice.update(Math.min(Math.max(revs, 0f), 1f), Math.min(Math.max(throttle, 0f), 1f), gain, pan);
    }

    @Override
    public void onDetach() {
        voice = null;
        super.onDetach();
    }
}
