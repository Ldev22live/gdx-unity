package io.github.ldev22live.gdxunity.editor.desktop.ui.menu;

import com.badlogic.gdx.Input;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A popup shown above the rest of the UI: drop-down menus, right-click menus and small popups such as
 * the Add Component search. Clicking outside or pressing Escape closes it.
 */
public class ContextMenu {

    private static final float ITEM_HEIGHT = 24f;
    private static final float MIN_WIDTH = 200f;
    private static final float SHORTCUT_GAP = 32f;

    /** A menu row; a {@code null} label is a separator. */
    public record Item(String label, String shortcut, boolean enabled, Runnable action) {
        public static Item separator() {
            return new Item(null, "", false, () -> {});
        }

        boolean isSeparator() {
            return label == null;
        }
    }

    private final Skin skin;
    private Table popup;
    private Actor dismissLayer;

    public ContextMenu(Skin skin) {
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
    }

    /** Menu items for the given actions, with separators between action groups; unknown ids are skipped. */
    public static List<Item> itemsFor(EditorActionRegistry actions, List<String> actionIds) {
        List<Item> items = new ArrayList<>();
        Integer previousGroup = null;
        for (String id : actionIds) {
            EditorAction action = actions.find(id).orElse(null);
            if (action == null) {
                continue;
            }
            if (previousGroup != null && previousGroup != action.getGroup()) {
                items.add(Item.separator());
            }
            previousGroup = action.getGroup();
            items.add(new Item(action.getLabel(), action.getShortcutText(), action.isEnabled(), () -> actions.execute(id)));
        }
        return items;
    }

    public boolean isOpen() {
        return popup != null;
    }

    /** Shows menu items with their top-left corner at the given stage position. */
    public void show(Stage stage, float stageX, float stageTopY, List<Item> items) {
        Table content = new Table(skin);
        for (Item item : items) {
            if (item.isSeparator()) {
                content.add(new Image(skin.getDrawable("subheader-bg"))).growX().height(1f).padTop(3).padBottom(3).row();
            } else {
                content.add(createItemButton(item)).growX().height(ITEM_HEIGHT).row();
            }
        }
        showContent(stage, stageX, stageTopY, content);
    }

    /** Shows arbitrary content (e.g. a search field and results) as a popup. */
    public void showContent(Stage stage, float stageX, float stageTopY, Table content) {
        Objects.requireNonNull(stage, "stage cannot be null");
        Objects.requireNonNull(content, "content cannot be null");
        close();

        Table layer = new Table();
        layer.setFillParent(true);
        layer.setTouchable(Touchable.enabled);
        layer.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                close();
            }
        });

        Table frame = new Table(skin);
        frame.setBackground("elevated-bg");
        frame.pad(4f);
        frame.add(content).grow();
        frame.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    close();
                    return true;
                }
                return false;
            }
        });
        frame.pack();
        frame.setWidth(Math.max(frame.getWidth(), MIN_WIDTH));

        // Keep the popup inside the window
        float x = Math.max(0f, Math.min(stageX, stage.getWidth() - frame.getWidth()));
        float y = Math.max(0f, Math.min(stageTopY - frame.getHeight(), stage.getHeight() - frame.getHeight()));
        frame.setPosition(x, y);

        stage.addActor(layer);
        stage.addActor(frame);
        if (stage.getKeyboardFocus() == null || !stage.getKeyboardFocus().isDescendantOf(frame)) {
            stage.setKeyboardFocus(frame);
        }
        dismissLayer = layer;
        popup = frame;
    }

    public void close() {
        if (popup != null) {
            popup.remove();
            popup = null;
        }
        if (dismissLayer != null) {
            dismissLayer.remove();
            dismissLayer = null;
        }
    }

    private TextButton createItemButton(Item item) {
        TextButton button = new TextButton(item.label(), skin, "flat");
        Label label = button.getLabel();
        label.setAlignment(Align.left);
        button.clearChildren();
        button.add(label).left().expandX().padLeft(6);
        if (item.shortcut() != null && !item.shortcut().isEmpty()) {
            button.add(new Label(item.shortcut(), skin, "muted")).right().padLeft(SHORTCUT_GAP).padRight(6);
        }
        button.setDisabled(!item.enabled());
        if (!item.enabled()) {
            label.setColor(skin.getColor("text-muted"));
        }
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                close();
                item.action().run();
            }
        });
        return button;
    }
}
