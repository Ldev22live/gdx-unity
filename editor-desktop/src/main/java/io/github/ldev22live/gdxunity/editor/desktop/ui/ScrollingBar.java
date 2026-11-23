package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

import java.util.Objects;

/**
 * Makes a toolbar scroll sideways when it is wider than the room it has (TD-077).
 *
 * <p>A row of buttons that is wider than its panel does not clip in scene2d: its minimum width becomes the minimum width of the whole
 * column it sits in, so the editor was laid out about 1,900 pixels wide in every window and cropped on both sides when the window was
 * smaller. Inside a scroll pane the row has no minimum width, so it keeps its natural width and the panel keeps its own.
 */
public final class ScrollingBar {
    private ScrollingBar() {
    }

    /**
     * Wraps a whole panel that needs more room than a small window gives it, so it scrolls in both directions instead of making the
     * editor taller and wider than the window (the Sprites and Tiles tabs did, TD-077). The bars stay visible, and dragging on the
     * panel never scrolls it (the panel may be a drawing surface).
     */
    public static ScrollPane panel(Actor panel, Skin skin) {
        Objects.requireNonNull(panel, "panel cannot be null");
        Objects.requireNonNull(skin, "skin cannot be null");
        ScrollPane pane = new ScrollPane(panel, new ScrollPane.ScrollPaneStyle(skin.get(ScrollPane.ScrollPaneStyle.class)));
        pane.setFlickScroll(false);
        pane.setFadeScrollBars(false);
        pane.setOverscroll(false, false);
        return pane;
    }

    /**
     * Wraps {@code bar}. Put the result in a cell with {@code growX().minWidth(0)} and the bar's own height. The scroll bar shows over the bottom edge
     * while the mouse is over the toolbar (so the toolbar keeps its height), and the mouse wheel scrolls it; dragging on a button does not.
     */
    public static ScrollPane around(Actor bar, Skin skin) {
        Objects.requireNonNull(bar, "bar cannot be null");
        Objects.requireNonNull(skin, "skin cannot be null");
        // The skin's scroll style draws a panel behind the content; a toolbar has its own background
        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle(skin.get(ScrollPane.ScrollPaneStyle.class));
        style.background = null;
        ScrollPane pane = new ScrollPane(bar, style);
        pane.setScrollingDisabled(false, true);
        pane.setFlickScroll(false);
        pane.setFadeScrollBars(true);
        pane.setScrollbarsOnTop(true);
        pane.setOverscroll(false, false);
        return pane;
    }
}
