package io.github.ldev22live.gdxunity.editor.desktop.ui.menu;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import io.github.ldev22live.gdxunity.editor.core.action.EditorAction;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;

import java.util.List;
import java.util.Objects;

/**
 * Desktop menu bar with drop-down menus generated from the {@link EditorActionRegistry}. Item enabled
 * states and shortcut hints are read from the actions each time a menu opens.
 */
public class EditorMenuBar extends Table {

    private static final float MENU_BUTTON_HEIGHT = 24f;
    private static final float ITEM_HEIGHT = 24f;
    private static final float POPUP_MIN_WIDTH = 200f;
    private static final float SHORTCUT_GAP = 32f;

    private final EditorActionRegistry actions;
    private final Skin skin;

    private Table openPopup;
    private Actor dismissLayer;

    public EditorMenuBar(EditorActionRegistry actions, List<String> menuOrder, Skin skin) {
        super(skin);
        this.actions = Objects.requireNonNull(actions, "actions cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        Objects.requireNonNull(menuOrder, "menuOrder cannot be null");
        left();

        for (String menu : menuOrder) {
            if (actions.getMenuActions(menu).isEmpty()) {
                continue;
            }
            TextButton menuButton = new TextButton(menu, skin, "flat");
            menuButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    openMenu(menu, menuButton);
                }
            });
            add(menuButton).height(MENU_BUTTON_HEIGHT).padLeft(2).padRight(2);
        }
    }

    public boolean isMenuOpen() {
        return openPopup != null;
    }

    /** Closes the open drop-down, if any. */
    public void closeMenu() {
        if (openPopup != null) {
            openPopup.remove();
            openPopup = null;
        }
        if (dismissLayer != null) {
            dismissLayer.remove();
            dismissLayer = null;
        }
    }

    private void openMenu(String menu, Actor anchor) {
        closeMenu();
        Stage stage = getStage();
        if (stage == null) {
            return;
        }

        // Full-screen transparent layer behind the popup: a click anywhere outside closes the menu
        Table layer = new Table();
        layer.setFillParent(true);
        layer.setTouchable(Touchable.enabled);
        layer.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                closeMenu();
            }
        });

        Table popup = buildPopup(menu);
        popup.pack();
        popup.setWidth(Math.max(popup.getWidth(), POPUP_MIN_WIDTH));
        Vector2 anchorPos = anchor.localToStageCoordinates(new Vector2(0f, 0f));
        popup.setPosition(anchorPos.x, anchorPos.y - popup.getHeight());

        stage.addActor(layer);
        stage.addActor(popup);
        stage.setKeyboardFocus(popup);
        dismissLayer = layer;
        openPopup = popup;
    }

    private Table buildPopup(String menu) {
        Table popup = new Table(skin);
        popup.setBackground("elevated-bg");
        popup.pad(4f);
        popup.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    closeMenu();
                    return true;
                }
                return false;
            }
        });

        Integer previousGroup = null;
        for (EditorAction action : actions.getMenuActions(menu)) {
            if (previousGroup != null && previousGroup != action.getGroup()) {
                popup.add(new Image(skin.getDrawable("subheader-bg"))).growX().height(1f).padTop(3).padBottom(3).row();
            }
            previousGroup = action.getGroup();
            popup.add(createItem(action)).growX().height(ITEM_HEIGHT).row();
        }
        return popup;
    }

    private TextButton createItem(EditorAction action) {
        TextButton item = new TextButton(action.getLabel(), skin, "flat");
        Label label = item.getLabel();
        label.setAlignment(Align.left);
        item.clearChildren();
        item.add(label).left().expandX().padLeft(6);
        if (action.hasShortcut()) {
            item.add(new Label(action.getShortcutText(), skin, "muted")).right().padLeft(SHORTCUT_GAP).padRight(6);
        }

        boolean enabled = action.isEnabled();
        item.setDisabled(!enabled);
        if (!enabled) {
            label.setColor(skin.getColor("text-muted"));
        }

        String id = action.getId();
        item.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                closeMenu();
                actions.execute(id);
            }
        });
        return item;
    }
}
