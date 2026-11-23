package io.github.ldev22live.gdxunity.engine.scene.audio;

/**
 * Where an {@link AudioSourceComponent} sends a sound when it is played. The host (the game runtime or editor Play)
 * binds one while the scene plays; the scene module knows nothing about how sound is made or output (TD-043).
 */
public interface SoundSink {
    /**
     * Starts one sound effect. Must not block and may be called from the game thread every frame.
     *
     * @param sound  the sound's name, for example an sfxr preset such as {@code Coin}
     * @param volume linear gain, 0 to 1
     * @param pitch  playback speed multiplier; 1 is the sound as made, 2 is an octave up
     */
    void play(String sound, float volume, float pitch);

    /**
     * Starts one sound effect placed between the speakers (Phase 20-C). A sink that cannot pan plays it in the middle.
     *
     * @param pan -1 is all in the left speaker, 1 all in the right, 0 in the middle
     */
    default void play(String sound, float volume, float pitch, float pan) {
        play(sound, volume, pitch);
    }
}
