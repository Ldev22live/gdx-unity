package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.GameModeProfile;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.ContextMenu;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The mode screen (TD-067): three cards, one per game mode, each with what the mode gives you and a button that starts a new scene in it.
 * Shown by File > New Scene.
 */
public final class GameModeScreen {
    private static final float CARD_WIDTH = 230f;
    private static final float CARD_HEIGHT = 150f;
    private static final float TOP_FRACTION = 0.7f;

    private final ContextMenu menu;
    private final Skin skin;

    public GameModeScreen(Skin skin) {
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.menu = new ContextMenu(skin);
    }

    /** Shows the screen centered on {@code stage}; {@code onChoose} gets the mode whose button was pressed, after the screen has closed. */
    public void show(Stage stage, GameMode current, Consumer<GameMode> onChoose) {
        Objects.requireNonNull(stage, "stage cannot be null");
        Objects.requireNonNull(onChoose, "onChoose cannot be null");
        Table content = new Table(skin);
        content.add(new Label("New scene: what kind of game?", skin, "section")).left().colspan(GameMode.values().length).padBottom(6).row();
        for (GameMode mode : GameMode.values()) {
            content.add(card(mode, mode == current, onChoose)).width(CARD_WIDTH).height(CARD_HEIGHT).pad(3);
        }
        float x = stage.getWidth() / 2f - (CARD_WIDTH + 6f) * GameMode.values().length / 2f;
        menu.showContent(stage, x, stage.getHeight() * TOP_FRACTION, content);
    }

    private Table card(GameMode mode, boolean current, Consumer<GameMode> onChoose) {
        GameModeProfile profile = GameModeProfile.of(mode);
        Table card = new Table(skin);
        card.top().left().pad(6);
        card.add(new Label(mode.getLabel() + (current ? "  (current)" : ""), skin, "section")).left().row();
        Label description = new Label(profile.getDescription(), skin, "muted");
        description.setWrap(true);
        card.add(description).growX().padTop(4).row();
        card.add().expandY().row();
        TextButton create = new TextButton("New " + mode.getLabel() + " Scene", skin, "primary");
        create.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                menu.close();
                onChoose.accept(mode);
            }
        });
        card.add(create).growX().height(26f);
        return card;
    }
}
