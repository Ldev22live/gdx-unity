package io.github.ldev22live.gdxunity.engine.scene.audio;

/**
 * Where an {@link EngineSoundComponent} sends its engine (TD-080). The host (the game runtime or editor Play) binds one while
 * the scene plays; the scene module knows nothing about how the sound is made.
 */
public interface EngineVoice {
    /**
     * Updates the engine's state. Must not block and may be called from the game thread every frame.
     *
     * @param revs     how fast the engine turns: 0 is idling, 1 is the redline
     * @param throttle 0 (lifted off) to 1 (flat out)
     * @param gain     linear volume, 0 to 1, with the component's volume and distance already applied
     * @param pan      -1 (left speaker) to 1 (right speaker)
     */
    void update(float revs, float throttle, float gain, float pan);
}
