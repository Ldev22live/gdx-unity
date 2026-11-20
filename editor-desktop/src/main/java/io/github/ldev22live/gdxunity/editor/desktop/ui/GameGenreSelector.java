package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.play.GenreChoices;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.GameGenres;

import java.util.List;
import java.util.Objects;

/**
 * The footer's Game Genre drop-down (roadmap M3b.4, TD-088): "None" and every genre of the registry, each marked with whether it plays
 * in the scene's game mode ({@link GenreChoices}), and a short line saying what Play will do with the pair ({@link #getStatusLabel()},
 * which the footer places after Game Mode). Choosing sets the scene's genre, which is saved with it; it is not an undo step and is
 * locked during Play, like the game mode.
 */
public class GameGenreSelector extends Table {
    /** The closed drop-down's width: the longest entry is shortened, so Game Mode stays in view at the smallest window. */
    private static final float BOX_WIDTH = 190f;

    private final EditorState editorState;
    private final GameGenres genres;
    private final Skin skin;
    private final SelectBox<GenreChoices.Choice> box;
    private final Label status;
    private boolean refreshing;

    public GameGenreSelector(EditorState editorState, GameGenres genres, Skin skin) {
        super(skin);
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.genres = Objects.requireNonNull(genres, "genres cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        box = new SelectBox<>(skin);
        status = new Label("", skin);
        box.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (refreshing) {
                    return;
                }
                GenreChoices.Choice chosen = box.getSelected();
                if (chosen != null) {
                    GenreChoices.choose(editorState, genres, chosen.genreId());
                }
                // A refused change (during Play) puts the scene's genre back in the box
                refresh();
            }
        });
        add(box).width(BOX_WIDTH);
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

    /** Rebuilds the entries for the scene's mode, selects the scene's genre and updates the line beside it. */
    public void refresh() {
        refreshing = true;
        try {
            String current = editorState.getGameGenre();
            GameMode mode = editorState.getGameMode();
            List<GenreChoices.Choice> choices = GenreChoices.choices(genres, current, mode);
            Array<GenreChoices.Choice> items = new Array<>(GenreChoices.Choice.class);
            choices.forEach(items::add);
            box.setItems(items);
            GenreChoices.Choice selected = GenreChoices.current(choices, current);
            box.setSelected(selected);
            status.setText(GenreChoices.statusLine(genres, current, mode));
            String colour = selected.genreId() == null ? "text-muted" : selected.isPlayable() ? "success" : "warning";
            status.setColor(skin.getColor(colour));
            invalidateHierarchy();
        } finally {
            refreshing = false;
        }
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        // Play mode can start and stop without a genre change
        box.setDisabled(editorState.isPlayModeActive());
    }

    /** The entry shown, for tests and the smoke check. */
    public GenreChoices.Choice getSelected() {
        return box.getSelected();
    }

    /** The line saying what Play does with the scene's genre and mode; the footer places it. */
    public Label getStatusLabel() {
        return status;
    }
}
