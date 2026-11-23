package io.github.ldev22live.gdxunity.engine.render.play;

import java.util.List;
import java.util.Objects;

/**
 * The result of the profile step when Play starts (TD-088, {@link PlayProfiles#start}).
 *
 * @param problems what stops Play, in plain words, some with a safe fix; empty when Play goes on
 * @param message  for the status line when Play goes on: the pair played and what was left to the author; empty without a genre
 */
public record ProfileStart(List<ProfileProblem> problems, String message) {
    public ProfileStart {
        problems = List.copyOf(Objects.requireNonNull(problems, "problems cannot be null"));
        Objects.requireNonNull(message, "message cannot be null");
    }

    /** Whether Play may go on. */
    public boolean allowsPlay() {
        return problems.isEmpty();
    }
}
