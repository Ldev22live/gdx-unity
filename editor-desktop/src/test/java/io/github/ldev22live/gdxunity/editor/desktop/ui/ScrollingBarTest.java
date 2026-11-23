package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** A toolbar in a scrolling bar keeps its width but no longer forces the editor to be that wide (TD-077). */
class ScrollingBarTest {
    private static final float CELL_WIDTH = 400f;
    private static final int CELLS = 3;
    private static final float PANEL_WIDTH = 500f;
    private static final float BAR_HEIGHT = 30f;

    private static Files previousFiles;
    private static Application previousApp;

    /**
     * scene2d tables look at {@code Gdx.files} when they are made and recurse without end when there is none, and a scroll pane makes a gesture
     * timer that wants {@code Gdx.app} (no graphics in a unit test, so both are mocks).
     */
    @BeforeAll
    static void giveScene2dAFilesObject() {
        previousFiles = Gdx.files;
        previousApp = Gdx.app;
        Gdx.files = mock(Files.class);
        Gdx.app = mock(Application.class);
    }

    @AfterAll
    static void restoreFiles() {
        Gdx.files = previousFiles;
        Gdx.app = previousApp;
    }

    private static Skin skin() {
        Skin skin = new Skin();
        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle();
        skin.add("default", style);
        return skin;
    }

    private static Table wideBar() {
        Table bar = new Table();
        for (int i = 0; i < CELLS; i++) {
            bar.add(new Widget()).width(CELL_WIDTH).height(24f);
        }
        return bar;
    }

    @Test
    void shouldHaveNoMinimumWidthEvenThoughTheBarInsideDoes() {
        Table bar = wideBar();
        ScrollPane pane = ScrollingBar.around(bar, skin());
        assertEquals(CELLS * CELL_WIDTH, bar.getMinWidth(), 0.5f, "the bar needs its width");
        assertEquals(0f, pane.getMinWidth(), 0.001f, "but the panel it sits in does not have to be that wide");
    }

    @Test
    void shouldScrollSidewaysWhenThePanelIsNarrowerThanTheBar() {
        ScrollPane pane = ScrollingBar.around(wideBar(), skin());
        pane.setSize(PANEL_WIDTH, BAR_HEIGHT);
        pane.validate();
        assertTrue(pane.getMaxX() >= CELLS * CELL_WIDTH - PANEL_WIDTH - 1f, "it can scroll to the end: " + pane.getMaxX());
        pane.setScrollX(pane.getMaxX());
        assertEquals(pane.getMaxX(), pane.getScrollX(), 0.001f);
    }

    @Test
    void shouldNotScrollWhenTheBarFits() {
        ScrollPane pane = ScrollingBar.around(wideBar(), skin());
        pane.setSize(CELLS * CELL_WIDTH + 100f, BAR_HEIGHT);
        pane.validate();
        assertEquals(0f, pane.getMaxX(), 0.001f);
    }

    @Test
    void shouldLetAWholePanelScrollBothWaysWithoutForcingTheEditorToItsSize() {
        Table panel = new Table();
        panel.add(new Widget()).width(CELLS * CELL_WIDTH).height(900f);
        ScrollPane pane = ScrollingBar.panel(panel, skin());
        assertEquals(0f, pane.getMinWidth(), 0.001f);
        assertEquals(0f, pane.getMinHeight(), 0.001f);
        pane.setSize(PANEL_WIDTH, 300f);
        pane.validate();
        assertTrue(pane.getMaxX() > 0f, "scrolls sideways: " + pane.getMaxX());
        assertTrue(pane.getMaxY() > 0f, "and up and down: " + pane.getMaxY());
    }

    @Test
    void shouldScrollOnlySidewaysAndKeepTheToolbarsOwnBackground() {
        ScrollPane pane = ScrollingBar.around(wideBar(), skin());
        pane.setSize(PANEL_WIDTH, BAR_HEIGHT);
        pane.validate();
        assertTrue(pane.isScrollX() || pane.getMaxX() > 0f);
        assertFalse(pane.isScrollY(), "a toolbar never scrolls up and down");
        assertNull(pane.getStyle().background, "the toolbar keeps its own background");
    }

    private static InputEvent mouse(InputEvent.Type type, float x, float y) {
        InputEvent event = new InputEvent();
        event.setType(type);
        event.setStageX(x);
        event.setStageY(y);
        return event;
    }

    @Test
    void shouldScrollTheToolbarSidewaysWithTheMouseWheel() {
        ScrollPane pane = ScrollingBar.around(wideBar(), skin());
        pane.setSize(PANEL_WIDTH, BAR_HEIGHT);
        pane.validate();
        InputEvent wheel = mouse(InputEvent.Type.scrolled, 5f, 5f);
        wheel.setScrollAmountY(1f);
        pane.fire(wheel);
        assertTrue(pane.getScrollX() > 0f, "the wheel moved the toolbar sideways: " + pane.getScrollX());
        assertEquals(0f, pane.getScrollY(), 0.001f, "and never up or down");
    }

    @Test
    void shouldNotScrollAPanelWhenTheMouseIsDraggedOnIt() {
        Table panel = new Table();
        panel.add(new Widget()).width(CELLS * CELL_WIDTH).height(900f);
        ScrollPane pane = ScrollingBar.panel(panel, skin());
        pane.setSize(PANEL_WIDTH, 300f);
        pane.validate();
        pane.fire(mouse(InputEvent.Type.touchDown, 100f, 100f));
        pane.fire(mouse(InputEvent.Type.touchDragged, 40f, 20f));
        pane.fire(mouse(InputEvent.Type.touchUp, 40f, 20f));
        assertEquals(0f, pane.getScrollX(), 0.001f, "a drag on a drawing surface is the tool's, not a scroll");
        assertEquals(0f, pane.getScrollY(), 0.001f, "the panel stays at its top");
    }
}
