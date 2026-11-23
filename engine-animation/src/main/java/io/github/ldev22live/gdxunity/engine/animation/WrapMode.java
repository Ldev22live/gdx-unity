package io.github.ldev22live.gdxunity.engine.animation;

/** What playback does at the end of a clip. */
public enum WrapMode {
    /** Stops at the end (or the start when playing backwards). */
    ONCE("Once"),
    /** Jumps back to the start and continues. */
    LOOP("Loop"),
    /** Turns around at each end. */
    PING_PONG("Ping-Pong");

    private final String displayName;

    WrapMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
