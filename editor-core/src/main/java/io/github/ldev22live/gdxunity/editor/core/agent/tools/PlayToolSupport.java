package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileProblem;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** What the genre and Play tools share (roadmap M3b.7, TD-088): the editor's Play, its genre registry, and problems as outputs. */
final class PlayToolSupport {
    /** The genre parameter's value for "no genre". */
    static final String NONE = "none";

    private PlayToolSupport() {
    }

    /** The editor's Play controller, or empty when this editor has none (a headless state without Play). */
    static Optional<PlayModeController> play(EditorState editorState) {
        return Optional.ofNullable(editorState.getPlayModeController());
    }

    /** The genre registry Play uses, so the tools and Play cannot disagree. */
    static GameGenres genres(PlayModeController play) {
        return play.getProfileStep().getProfiles().getGenres();
    }

    /** A game mode from its file value ("2d", "2.5d", "3d"), or empty. */
    static Optional<GameMode> mode(Object value) {
        return value == null ? Optional.empty() : Optional.ofNullable(GameMode.fromFileValue(value.toString().trim().toLowerCase(Locale.ROOT)));
    }

    /** Each problem as {message, entity, fix}: the fix's label, or empty when there is none. */
    static List<Map<String, Object>> problems(List<ProfileProblem> problems) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProfileProblem problem : problems) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("message", problem.message());
            entry.put("entity", problem.entityName() == null ? "" : problem.entityName());
            entry.put("fix", problem.safeFix().map(fix -> fix.label()).orElse(""));
            out.add(entry);
        }
        return out;
    }
}
