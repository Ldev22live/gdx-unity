package io.github.ldev22live.gdxunity.editor.desktop.ui;

import io.github.ldev22live.gdxunity.editor.core.play.GenreChoices;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The footer's Game Mode buttons with a genre (roadmap M3b.4): the modes the genre does not play in are dimmed, never hidden. */
class GameModeSelectorTest {
    private final GameGenres genres = GameGenres.standard();

    @Test
    void shouldDimTheModesTheScenesGenreDoesNotPlayIn() {
        List<GameMode> platformer = GenreChoices.playableModes(genres, GameGenres.PLATFORMER);

        assertEquals("accent", GameModeSelector.colourOf(GameMode.MODE_3D, GameMode.MODE_3D, true, platformer), "the scene's mode");
        assertEquals("text-primary", GameModeSelector.colourOf(GameMode.MODE_2_5D, GameMode.MODE_3D, true, platformer), "plays in 2.5D");
        assertEquals("text-primary", GameModeSelector.colourOf(GameMode.MODE_2D, GameMode.MODE_3D, true, platformer), "plays in 2D (TD-105)");
        List<GameMode> actionRpg = GenreChoices.playableModes(genres, GameGenres.ACTION_RPG);
        assertEquals("text-muted", GameModeSelector.colourOf(GameMode.MODE_2D, GameMode.MODE_3D, true, actionRpg), "planned in 2D");
    }

    @Test
    void shouldDimNothingWithoutAGenre() {
        for (GameMode mode : List.of(GameMode.MODE_2D, GameMode.MODE_2_5D)) {
            assertEquals("text-primary", GameModeSelector.colourOf(mode, GameMode.MODE_3D, false, List.of()));
        }
    }
}
