package io.github.ldev22live.gdxunity.editor.core.play;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.GameModeProfile;
import io.github.ldev22live.gdxunity.engine.render.play.PlayProfiles;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileFix;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileProblem;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileStart;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import io.github.ldev22live.gdxunity.engine.scene.play.InputSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The genre profile step of editor Play (TD-088): on the copy Play runs, after physics is created and before the other extensions,
 * it resolves the scene's genre in the game mode the editor shows, checks the scene and applies the profile through
 * {@link PlayProfiles#start}, the same code the game runtime runs. Problems stop Play; they are kept, with their safe fixes, for the
 * editor to show. {@link PlayModeController} registers it first.
 */
public final class ProfilePlayStep implements PlayModeController.Extension {
    private final PlayProfiles profiles;
    private InputSource input = InputSource.keyboard();
    private List<ProfileProblem> lastProblems = List.of();
    private String lastMessage = "";

    public ProfilePlayStep(PlayProfiles profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles cannot be null");
    }

    /** A standard registry ({@link GameGenres#standard()}). */
    public ProfilePlayStep() {
        this(new PlayProfiles(GameGenres.standard()));
    }

    /** Where the profile's movement reads keys; the keyboard unless a test sets another source. */
    public void setInput(InputSource input) {
        this.input = Objects.requireNonNull(input, "input cannot be null");
    }

    public PlayProfiles getProfiles() {
        return profiles;
    }

    /** The problems that stopped the last Play, with their safe fixes; empty after a Play that started. */
    public List<ProfileProblem> getLastProblems() {
        return lastProblems;
    }

    /**
     * The safe fixes of the problems that stopped the last Play, in the problems' order, for the editor to offer as buttons. Empty
     * after a Play that started, after {@link #clearProblems}, and once each was used through {@link #applyFix}.
     */
    public List<ProfileFix> getLastFixes() {
        List<ProfileFix> fixes = new ArrayList<>();
        for (ProfileProblem problem : lastProblems) {
            problem.safeFix().ifPresent(fixes::add);
        }
        return fixes;
    }

    /**
     * Applies one of {@link #getLastFixes()} to the authored scene as one undoable command, because a person clicked it, and stops
     * offering it. The status bar says what was done; Play is not started by itself.
     *
     * @return false when the editor refused the fix (during Play, or the entity was deleted since)
     */
    public boolean applyFix(EditorState editorState, ProfileFix fix) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(fix, "fix cannot be null");
        if (!getLastFixes().contains(fix)) {
            throw new IllegalArgumentException("\"" + fix.label() + "\" is not offered by the last Play");
        }
        if (!ProfileFixes.apply(editorState, fix)) {
            return false;
        }
        List<ProfileProblem> left = new ArrayList<>();
        for (ProfileProblem problem : lastProblems) {
            if (!problem.safeFix().map(fix::equals).orElse(false)) {
                left.add(problem);
            }
        }
        lastProblems = List.copyOf(left);
        editorState.setStatusMessage("Done: " + fix.label() + " (undoable). Press Play again.", false);
        return true;
    }

    /** Forgets the last refusal's problems, for example when another scene is opened. */
    public void clearProblems() {
        lastProblems = List.of();
    }

    /** What the last Play that started played as, and what it left to the author; empty without a genre. */
    public String getLastMessage() {
        return lastMessage;
    }

    @Override
    public List<String> onPlay(Scene runtimeScene, PlayModeController controller) {
        ProfileStart start = profiles.start(runtimeScene, GameModeProfile.resolve(runtimeScene), input);
        lastProblems = start.problems();
        lastMessage = start.message();
        List<String> messages = new ArrayList<>();
        for (ProfileProblem problem : start.problems()) {
            messages.add(problem.message());
        }
        return messages;
    }

    @Override
    public void onStop(Scene runtimeScene) {
        // The profile's parts live on the copy, which Play disposes
    }
}
