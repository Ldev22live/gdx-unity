package io.github.ldev22live.gdxunity.engine.scene.play;

import io.github.ldev22live.gdxunity.engine.scene.GameMode;

import java.util.Objects;

/**
 * What a genre and game mode pair supports (TD-088): its status, the reason in plain words, and the profile when it is playable.
 *
 * @param genre   the genre
 * @param mode    the scene's game mode
 * @param status  playable, planned or not offered
 * @param reason  one sentence a person reads in the selector and in Play's refusal
 * @param profile the profile Play applies; present exactly when {@code status} is {@link GenreStatus#PLAYABLE}
 */
public record GenreSupport(GameGenre genre, GameMode mode, GenreStatus status, String reason, GameTestProfile profile) {
    public GenreSupport {
        Objects.requireNonNull(genre, "genre cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        if ((status == GenreStatus.PLAYABLE) != (profile != null)) {
            throw new IllegalArgumentException(genre.label() + " + " + mode.getLabel() + ": a profile is given exactly when the pair is "
                + "playable (status " + status + ")");
        }
        if (profile != null && (!profile.genre().equals(genre) || profile.mode() != mode)) {
            throw new IllegalArgumentException("The profile for " + genre.label() + " + " + mode.getLabel() + " is for "
                + profile.genre().label() + " + " + profile.mode().getLabel());
        }
    }

    /** Whether Play can run the pair. */
    public boolean isPlayable() {
        return status == GenreStatus.PLAYABLE;
    }
}
