package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Presses Play (roadmap M3b.7, TD-088): the same {@link PlayModeController#play()} as the footer's button, on a copy of the scene,
 * with the genre profile applied. A refused Play is a result, not a failure, so a plan can read why: {@code started} is false and
 * {@code problems} lists the reasons with the labels of their safe fixes, which the editor offers to the person as buttons. The
 * tool never applies a fix. Not an undo step: Stop discards everything Play changed.
 *
 * <p>No parameters. Outputs: {@code started}, {@code state}, {@code message}, {@code problems}.
 */
public class PlayStartTool implements AgentTool {
    public static final String ID = "play_start";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Presses Play: runs a copy of the scene with its Game Genre and Game Mode, as the Play button does. No parameters. "
            + "Outputs started, state, message and problems (when Play is refused: each with message, entity and the label of a fix "
            + "the person can click). Use play_stop to end it; Stop discards everything Play changed.";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean actsOutsideUndoHistory() {
        return true;
    }

    @Override
    public List<String> validateParameters(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = new ArrayList<>();
        if (PlayToolSupport.play(context.getEditorState()).isEmpty()) {
            errors.add("This editor has no Play");
        }
        return errors;
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) {
        return null;
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = validateParameters(parameters, context);
        if (!errors.isEmpty()) {
            return AgentToolResult.failure(errors.get(0));
        }
        PlayModeController play = context.getEditorState().getPlayModeController();
        Map<String, Object> outputs = new LinkedHashMap<>();
        if (context.getEditorState().isPlayModeActive()) {
            outputs.put("started", false);
            outputs.put("state", play.getState().name().toLowerCase(Locale.ROOT));
            outputs.put("message", "The game is already playing");
            outputs.put("problems", List.of());
            return AgentToolResult.success("The game is already playing", outputs);
        }
        // Problems kept from an earlier refusal must not be reported as this one's (a later check, such as scripts, may refuse it)
        play.getProfileStep().clearProblems();
        boolean started = play.play();
        String message = context.getEditorState().getStatusMessage();
        outputs.put("started", started);
        outputs.put("state", play.getState().name().toLowerCase(Locale.ROOT));
        outputs.put("message", message);
        outputs.put("problems", started ? List.of() : PlayToolSupport.problems(play.getProfileStep().getLastProblems()));
        return AgentToolResult.success(started ? message : "Play was refused: " + message, outputs);
    }
}
