package io.github.ldev22live.gdxunity.engine.scene.play;

/** Whether a genre and game mode pair can be played (TD-088). */
public enum GenreStatus {
    /** Play supplies a tested {@link GameTestProfile} for the pair. */
    PLAYABLE,
    /** Shown in the selector as planned and refused at Play with the reason. */
    PLANNED,
    /** Not meaningful for the mode (a first-person game in 2D); shown with the reason, never playable. */
    NOT_OFFERED
}
