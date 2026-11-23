package io.github.ldev22live.gdxunity.engine.scene.audio;

/** Where a {@link MusicSourceComponent} sends its start and stop requests; the host binds one while a scene plays (TD-061). */
public interface MusicSink {
    /** Starts (or restarts) the source's song. */
    void start(MusicSourceComponent source);

    /** Stops the source's song; does nothing if it is not playing. */
    void stop(MusicSourceComponent source);
}
