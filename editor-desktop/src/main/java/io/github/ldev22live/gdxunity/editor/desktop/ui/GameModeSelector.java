package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.play.GenreChoices;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Strip with one button per game mode (2D, 2.5D, 3D); the one for the active scene's mode is highlighted (TD-067). When the scene
 * has a genre, the other modes in which that genre does not play are dimmed (roadmap M3b.4): they can still be chosen, and the
 * Game Genre line then says Play will refuse the pair.
 */
public class GameModeSelector extends Table {
    private static final float BUTTON_HEIGHT = 26f;
    private static final float BUTTON_WIDTH = 46f;

    private final EditorState editorState;
    private final GameGenres genres;
    private final Skin skin;
    private final Map<GameMode, TextButton> buttons = new EnumMap<>(GameMode.class);

    public GameModeSelector(EditorState editorState, GameGenres genres, Skin skin) {
        super(skin);
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.genres = Objects.requireNonNull(genres, "genres cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        for (GameMode mode : GameMode.values()) {
            TextButton button = new TextButton(mode.getLabel(), skin);
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    editorState.setGameMode(mode);
                    refresh();
                }
            });
            buttons.put(mode, button);
            add(button).height(BUTTON_HEIGHT).width(BUTTON_WIDTH).padRight(2);
        }
        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onActiveSceneChanged(Scene oldScene, Scene newScene) {
                refresh();
            }

            @Override
            public void onGameModeChanged(GameMode oldMode, GameMode newMode) {
                refresh();
            }

            @Override
            public void onGameGenreChanged(String oldGenre, String newGenre) {
                refresh();
            }
        });
        refresh();
    }

    /** Highlights the button of the active scene's mode and dims the modes the scene's genre does not play in. */
    public void refresh() {
        GameMode active = editorState.getGameMode();
        String genre = editorState.getGameGenre();
        List<GameMode> playable = GenreChoices.playableModes(genres, genre);
        for (Map.Entry<GameMode, TextButton> entry : buttons.entrySet()) {
            String colour = colourOf(entry.getKey(), active, genre != null, playable);
            // The highlight tints the button; dimming tints its text, which a tinted dark background would not show
            entry.getValue().setColor(skin.getColor("accent".equals(colour) ? "accent" : "text-primary"));
            entry.getValue().getLabel().setColor("text-muted".equals(colour) ? skin.getColor("text-muted") : Color.WHITE);
            entry.getValue().setDisabled(editorState.isPlayModeActive());
        }
    }

    /** The skin colour of a mode's button: accent for the scene's mode, muted for a mode the scene's genre does not play in. */
    static String colourOf(GameMode mode, GameMode active, boolean hasGenre, List<GameMode> playable) {
        if (mode == active) {
            return "accent";
        }
        return hasGenre && !playable.contains(mode) ? "text-muted" : "text-primary";
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        // Play mode can start and stop without a mode change
        for (TextButton button : buttons.values()) {
            button.setDisabled(editorState.isPlayModeActive());
        }
    }
}
