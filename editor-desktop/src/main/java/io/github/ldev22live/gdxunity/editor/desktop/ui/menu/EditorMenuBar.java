package io.github.ldev22live.gdxunity.editor.desktop.ui.menu;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
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

    private final EditorActionRegistry actions;
    private final ContextMenu dropDown;

    public EditorMenuBar(EditorActionRegistry actions, List<String> menuOrder, Skin skin) {
        super(skin);
        this.actions = Objects.requireNonNull(actions, "actions cannot be null");
        this.dropDown = new ContextMenu(skin);
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
        return dropDown.isOpen();
    }

    public void closeMenu() {
        dropDown.close();
    }

    private void openMenu(String menu, Actor anchor) {
        Stage stage = getStage();
        if (stage == null) {
            return;
        }
        List<String> ids = actions.getMenuActions(menu).stream().map(EditorAction::getId).toList();
        Vector2 anchorPos = anchor.localToStageCoordinates(new Vector2(0f, 0f));
        dropDown.show(stage, anchorPos.x, anchorPos.y, ContextMenu.itemsFor(actions, ids));
    }
}
