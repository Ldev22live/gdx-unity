package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import io.github.ldev22live.gdxunity.editor.core.GameModeProfile;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.engine.render.play.PlayProfiles;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileProblem;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileResolution;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks the active scene against its genre and game mode without playing it (roadmap M3b.7, TD-088): the same resolution and
 * checks Play runs first, in the mode the editor shows. Read-only. The ground under the player is not checked here, because that
 * needs the physics Play builds; Play checks it.
 *
 * <p>No parameters. Outputs: {@code outcome} ("no-genre", "profile" or "refused"), {@code canPlay}, {@code message}, {@code mode},
 * {@code problems} (each {message, entity, fix}, where fix is the label of the safe fix the person can click, or empty).
 */
public class PlayProfileCheckTool implements AgentTool {
    public static final String ID = "play_profile_check";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Checks whether the scene can be played as its Game Genre and Game Mode, without playing it. No parameters. Outputs "
            + "outcome (no-genre, profile or refused), canPlay, message, mode and problems (each with message, entity and the label "
            + "of a fix the person can click). The ground under the player is checked only when Play starts.";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public List<String> validateParameters(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = new ArrayList<>();
        if (PlayToolSupport.play(context.getEditorState()).isEmpty()) {
            errors.add("This editor has no Play, so nothing can be checked for it");
        }
        if (context.getActiveScene() == null) {
            errors.add("No scene is open");
        }
        return errors;
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) {
        return null; // Read-only
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = validateParameters(parameters, context);
        if (!errors.isEmpty()) {
            return AgentToolResult.failure(errors.get(0));
        }
        if (context.getEditorState().isPlayModeActive()) {
            return AgentToolResult.failure("The game is playing; stop it first, then check the scene");
        }
        PlayModeController play = context.getEditorState().getPlayModeController();
        PlayProfiles profiles = play.getProfileStep().getProfiles();
        Scene scene = context.getActiveScene();
        GameMode mode = GameModeProfile.resolve(scene);
        ProfileResolution resolution = profiles.resolve(scene, mode);
        List<ProfileProblem> problems = new ArrayList<>();
        String outcome;
        switch (resolution.outcome()) {
            case NO_GENRE -> outcome = "no-genre";
            case REFUSED -> {
                outcome = "refused";
                problems.add(new ProfileProblem(resolution.message(), null, null));
            }
            default -> {
                outcome = "profile";
                problems.addAll(profiles.validate(scene, resolution.profile()));
            }
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("outcome", outcome);
        outputs.put("canPlay", problems.isEmpty());
        outputs.put("message", resolution.message());
        outputs.put("mode", mode.getFileValue());
        outputs.put("problems", PlayToolSupport.problems(problems));
        String summary = problems.isEmpty()
            ? (outcome.equals("no-genre") ? "No genre: Play runs the scene's own scripts." : resolution.message() + " Nothing is missing.")
            : "Play would be refused: " + problems.get(0).message()
                + (problems.size() > 1 ? " (+" + (problems.size() - 1) + " more)" : "");
        return AgentToolResult.success(summary, outputs);
    }
}
