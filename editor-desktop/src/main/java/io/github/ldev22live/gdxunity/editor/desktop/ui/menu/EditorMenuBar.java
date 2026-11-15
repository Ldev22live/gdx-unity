package io.github.ldev22live.gdxunity.editor.desktop.ui.menu;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.ldev22live.gdxunity.editor.core.action.EditorAction;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.core.action.HeaderMenus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Desktop menu bar with drop-down menus generated from the {@link EditorActionRegistry}. Item enabled
 * states and shortcut hints are read from the actions each time a menu opens.
 */
public class EditorMenuBar extends Table {

    private static final float MENU_BUTTON_HEIGHT = 24f;

    private final EditorActionRegistry actions;
    private final ContextMenu dropDown;

    /** One dropdown per registry menu, labelled with the menu's name; menus with no actions are left out. */
    public EditorMenuBar(EditorActionRegistry actions, List<String> menuOrder, Skin skin) {
        this(actions, skin);
        Objects.requireNonNull(menuOrder, "menuOrder cannot be null");
        for (String menu : menuOrder) {
            if (actions.getMenuActions(menu).isEmpty()) {
                continue;
            }
            addMenu(menu, () -> ContextMenu.itemsFor(actions, actions.getMenuActions(menu).stream().map(EditorAction::getId).toList()));
        }
    }

    private EditorMenuBar(EditorActionRegistry actions, Skin skin) {
        super(skin);
        this.actions = Objects.requireNonNull(actions, "actions cannot be null");
        this.dropDown = new ContextMenu(skin);
        left();
    }

    /** The World-first header's Project and Settings dropdowns ({@link HeaderMenus}), with a separator between sections. */
    public static EditorMenuBar header(EditorActionRegistry actions, Skin skin) {
        EditorMenuBar bar = new EditorMenuBar(actions, skin);
        for (String menu : HeaderMenus.names()) {
            if (bar.hasChildren()) {
                // Side by side, "Project Settings" read as one item in the first screenshot; a divider keeps them two menus
                bar.add(new Label("|", skin, "muted")).padLeft(4).padRight(4);
            }
            bar.addMenu(menu, () -> {
                List<ContextMenu.Item> items = new ArrayList<>();
                for (List<String> section : HeaderMenus.sections(actions, menu)) {
                    if (!items.isEmpty()) {
                        items.add(ContextMenu.Item.separator());
                    }
                    items.addAll(ContextMenu.itemsFor(actions, section));
                }
                return items;
            });
        }
        return bar;
    }

    public boolean isMenuOpen() {
        return dropDown.isOpen();
    }

    public void closeMenu() {
        dropDown.close();
    }

    private void addMenu(String label, Supplier<List<ContextMenu.Item>> items) {
        TextButton menuButton = new TextButton(label, getSkin(), "flat");
        menuButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                openMenu(items.get(), menuButton);
            }
        });
        add(menuButton).height(MENU_BUTTON_HEIGHT).padLeft(2).padRight(2);
    }

    private void openMenu(List<ContextMenu.Item> items, Actor anchor) {
        Stage stage = getStage();
        if (stage == null || items.isEmpty()) {
            return;
        }
        Vector2 anchorPos = anchor.localToStageCoordinates(new Vector2(0f, 0f));
        dropDown.show(stage, anchorPos.x, anchorPos.y, items);
    }
}
