package io.github.ldev22live.gdxunity.engine.render.play;

import java.util.Objects;
import java.util.Optional;

/**
 * Something a scene lacks for its genre profile, in plain words (TD-088). Any problem stops Play.
 *
 * @param message    what is missing and what to add, for a person
 * @param entityName the entity it is about, or null when it is about the scene
 * @param fix        a safe fix, or null when the choice is the person's
 */
public record ProfileProblem(String message, String entityName, ProfileFix fix) {
    public ProfileProblem {
        Objects.requireNonNull(message, "message cannot be null");
    }

    /** The safe fix, when there is one. */
    public Optional<ProfileFix> safeFix() {
        return Optional.ofNullable(fix);
    }
}
