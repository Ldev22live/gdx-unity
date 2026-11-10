package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Toolbar strip with one button per game mode (2D, 2.5D, 3D); the one for the active scene's mode is highlighted (TD-067). */
public class GameModeSelector extends Table {
    private static final float BUTTON_HEIGHT = 26f;
    private static final float BUTTON_WIDTH = 46f;

    private final EditorState editorState;
    private final Skin skin;
    private final Map<GameMode, TextButton> buttons = new EnumMap<>(GameMode.class);

    public GameModeSelector(EditorState editorState, Skin skin) {
        super(skin);
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
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
            public void onGameModeChanged(GameMode oldMode, GameMode newMode) {
                refresh();
            }
        });
        refresh();
    }

    /** Highlights the button of the active scene's mode. */
    public void refresh() {
        GameMode active = editorState.getGameMode();
        for (Map.Entry<GameMode, TextButton> entry : buttons.entrySet()) {
            entry.getValue().setColor(skin.getColor(entry.getKey() == active ? "accent" : "text-primary"));
            entry.getValue().setDisabled(editorState.isPlayModeActive());
        }
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
