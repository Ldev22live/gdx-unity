package io.github.ldev22live.gdxunity.engine.scene.audio;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * A song an entity plays while the scene plays (TD-061): a {@code .song} file made in the music studio, a volume, and
 * whether it loops and starts by itself. Like {@link AudioSourceComponent} it only holds data; the host binds a
 * {@link MusicSink} when the scene starts, and an unbound component ignores {@link #play()} and {@link #stop()}.
 */
public class MusicSourceComponent extends BaseComponent {
    private String song = "";
    private float volume = 1f;
    private boolean loop = true;
    private boolean playOnStart = true;
    private MusicSink sink;

    /** Creates a source with no song yet; a source without a song is refused when the scene starts. */
    public MusicSourceComponent() {
        super();
    }

    /** @param song project-relative path of a {@code .song} file, for example {@code music/demo.song} */
    public MusicSourceComponent(String song) {
        setSong(song);
    }

    /** Project-relative path of the song; may be empty only before one is chosen. */
    public String getSong() {
        return song;
    }

    /** @throws IllegalArgumentException if {@code song} is null */
    public void setSong(String song) {
        if (song == null) {
            throw new IllegalArgumentException("song cannot be null");
        }
        this.song = song.trim();
    }

    public float getVolume() {
        return volume;
    }

    /** @throws IllegalArgumentException if {@code volume} is not within 0 to 1 */
    public void setVolume(float volume) {
        if (!(volume >= 0f && volume <= 1f)) {
            throw new IllegalArgumentException("volume must be within 0 to 1: " + volume);
        }
        this.volume = volume;
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

    /** Connects the source to the host's music output; called by the host when the scene starts. */
    public void bind(MusicSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink cannot be null");
    }

    /** Disconnects the source; called when the scene stops. */
    public void unbind() {
        this.sink = null;
    }

    public boolean isBound() {
        return sink != null;
    }

    /** Starts the song from the beginning; nothing happens while unbound or disabled. */
    public void play() {
        if (sink != null && isEnabled()) {
            sink.start(this);
        }
    }

    /** Stops the song; nothing happens while unbound. */
    public void stop() {
        if (sink != null) {
            sink.stop(this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (playOnStart) {
            play();
        }
    }
}
