package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.core.play.GenreChoices;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sets the active scene's game genre and, optionally, its game mode (roadmap M3b.7, TD-088): what the footer's Game Genre and Game Mode
 * do. Like them it is not an undo step and is refused during Play. The outputs say whether Play can use the pair, so a plan can
 * check before it plays; choosing a pair that is only planned is allowed, as in the footer, and reported as not playable.
 *
 * <p>Parameters: {@code genre} (a genre id such as "action-rpg", or "none"), optional {@code mode} ("2d", "2.5d" or "3d").
 * Outputs: {@code genre}, {@code mode}, {@code playable}, {@code status}, {@code reason}, {@code playableModes}.
 */
public class PlayProfileSetTool implements AgentTool {
    public static final String ID = "play_profile_set";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Sets the scene's Game Genre and optionally its Game Mode, as the footer does. Parameters: genre (one of "
            + String.join(", ", GameGenres.standard().genres().stream().map(g -> g.id()).toList()) + ", or none), mode (optional: 2d, "
            + "2.5d or 3d). Outputs playable, status and reason for the pair, and playableModes for the genre. Not an undo step; "
            + "refused during Play.";
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
        Optional<PlayModeController> play = PlayToolSupport.play(context.getEditorState());
        if (play.isEmpty()) {
            errors.add("This editor has no Play, so it has no genres");
            return errors;
        }
        if (context.getEditorState().isPlayModeActive()) {
            errors.add("Stop the game first: the genre and mode cannot change during Play");
        }
        Object genre = parameters == null ? null : parameters.get("genre");
        if (genre == null || genre.toString().isBlank()) {
            errors.add("Missing required parameter 'genre' (a genre id, or none)");
        } else if (!genre.toString().trim().equalsIgnoreCase(PlayToolSupport.NONE)
            && PlayToolSupport.genres(play.get()).find(genre.toString().trim()).isEmpty()) {
            errors.add("Unknown genre '" + genre + "'. Known: " + PlayToolSupport.genres(play.get()).genres().stream()
                .map(g -> g.id()).collect(Collectors.joining(", ")) + ", or none");
        }
        Object mode = parameters == null ? null : parameters.get("mode");
        if (mode != null && PlayToolSupport.mode(mode).isEmpty()) {
            errors.add("Unknown mode '" + mode + "': use 2d, 2.5d or 3d");
        }
        return errors;
    }

    /** Not an undo step, like the footer's selectors: see {@link #actsOutsideUndoHistory()}. */
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
        EditorState editorState = context.getEditorState();
        GameGenres genres = PlayToolSupport.genres(editorState.getPlayModeController());
        Optional<GameMode> mode = PlayToolSupport.mode(parameters.get("mode"));
        mode.ifPresent(editorState::setGameMode);
        String raw = parameters.get("genre").toString().trim();
        String genreId = raw.equalsIgnoreCase(PlayToolSupport.NONE) ? null : raw;
        GenreChoices.choose(editorState, genres, genreId);
        GameMode now = editorState.getGameMode();
        GenreChoices.Choice choice = GenreChoices.current(GenreChoices.choices(genres, genreId, now), genreId);

        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("genre", genreId == null ? PlayToolSupport.NONE : genreId);
        outputs.put("mode", now.getFileValue());
        outputs.put("playable", genreId == null || choice.isPlayable());
        outputs.put("status", genreId == null ? "none" : choice.status().name().toLowerCase(Locale.ROOT).replace('_', '-'));
        outputs.put("reason", choice.reason());
        outputs.put("playableModes", GenreChoices.playableModes(genres, genreId).stream().map(GameMode::getFileValue).toList());
        return AgentToolResult.success(GenreChoices.changeMessage(genres, genreId, now), outputs);
    }
}
