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
 * Presses Stop (roadmap M3b.7, TD-088): the same {@link PlayModeController#stop()} as the footer's button. The authored scene, the
 * selection and the undo history come back as they were before Play. Stopping when nothing plays is not an error.
 *
 * <p>No parameters. Outputs: {@code stopped} (whether a game was playing), {@code state}.
 */
public class PlayStopTool implements AgentTool {
    public static final String ID = "play_stop";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Presses Stop: ends Play and returns to the scene as it was before Play. No parameters. Outputs stopped (whether a "
            + "game was playing) and state.";
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
        boolean playing = context.getEditorState().isPlayModeActive();
        if (playing) {
            play.stop();
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("stopped", playing);
        outputs.put("state", play.getState().name().toLowerCase(Locale.ROOT));
        return AgentToolResult.success(playing ? "Stopped; the scene is as it was before Play" : "Nothing was playing", outputs);
    }
}
