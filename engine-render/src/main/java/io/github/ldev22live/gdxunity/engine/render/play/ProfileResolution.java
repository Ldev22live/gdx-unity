package io.github.ldev22live.gdxunity.engine.render.play;

import io.github.ldev22live.gdxunity.engine.scene.play.GameTestProfile;

import java.util.Objects;

/**
 * What Play does with a scene's genre and game mode (TD-088).
 *
 * @param outcome what was found
 * @param message one sentence for the status line; empty for {@link Outcome#NO_GENRE}
 * @param profile the profile to apply; present exactly when {@code outcome} is {@link Outcome#PROFILE}
 */
public record ProfileResolution(Outcome outcome, String message, GameTestProfile profile) {
    /** What was found. */
    public enum Outcome {
        /** The scene has no genre: Play runs its own scripts and graphs, as before genres existed. */
        NO_GENRE,
        /** The pair is playable; {@link #profile()} is applied to the Play copy. */
        PROFILE,
        /** The pair is planned or not offered, or the scene names a genre this version does not know, or has no game mode. */
        REFUSED
    }

    public ProfileResolution {
        Objects.requireNonNull(outcome, "outcome cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        if ((outcome == Outcome.PROFILE) != (profile != null)) {
            throw new IllegalArgumentException("A profile is given exactly when the outcome is PROFILE, not " + outcome);
        }
    }

    /** Whether Play may go on (no genre, or a playable one); a refusal stops Play with {@link #message()}. */
    public boolean allowsPlay() {
        return outcome != Outcome.REFUSED;
    }
}
