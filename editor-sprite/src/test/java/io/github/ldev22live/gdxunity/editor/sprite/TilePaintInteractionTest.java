package io.github.ldev22live.gdxunity.editor.sprite;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.sprite.TileMapData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TilePaintInteractionTest {
    /** A view where one local pixel is one world unit, so local (x, y) is world (x, y). */
    private static final class IdentityView implements ViewportInteraction {
        final List<String> calls = new ArrayList<>();

        @Override
        public boolean touchDown(float x, float y, float w, float h, int button, int modifiers) {
            calls.add("down" + button);
            return true;
        }

        @Override
        public boolean touchDragged(float x, float y, float w, float h) {
            calls.add("drag");
            return true;
        }

        @Override
        public boolean touchUp(int button) {
            calls.add("up" + button);
            return true;
        }

        @Override
        public boolean scrolled(float amountY) {
            calls.add("scroll");
            return true;
        }

        @Override
        public Vector3 worldPointAt(float x, float y, float w, float h) {
            return new Vector3(x, y, 0f);
        }
    }

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private final IdentityView view = new IdentityView();
    private final Scene scene = new Scene("S");
    private final Entity entity = scene.createEntity("Level");
    private final TilemapComponent tilemap = new TilemapComponent();
    private TilePaintInteraction.Target target;

    private TilePaintInteraction interaction(boolean on) {
        tilemap.setTileSize(10f, 10f);
        tilemap.setMap(new TileMapData(6, 4));
        entity.addComponent(tilemap);
        target = new TilePaintInteraction.Target(tilemap, new TilemapSession(tilemap.getMap()));
        return new TilePaintInteraction(view, () -> on ? target : null);
    }

    @Test
    void shouldFindTheCellUnderAWorldPointFromTheMapsBottomLeftCornerWithRowZeroOnTop() {
        entity.getTransform().setLocalPosition(100f, 50f, 0f);
        interaction(true);

        assertArrayEquals(new int[] {0, 3}, TilePaintInteraction.cellAt(tilemap, new Vector3(101f, 51f, 0f)), "bottom left cell");
        assertArrayEquals(new int[] {5, 0}, TilePaintInteraction.cellAt(tilemap, new Vector3(159f, 89f, 0f)), "top right cell");
        assertArrayEquals(new int[] {-1, 4}, TilePaintInteraction.cellAt(tilemap, new Vector3(99f, 49f, 0f)), "outside, below and left");
    }

    @Test
    void shouldPaintOneUndoStepFromPressToReleaseWithTheLeftButton() {
        TilePaintInteraction paint = interaction(true);
        target.session().setTile(7);

        paint.touchDown(5f, 35f, 400f, 300f, Input.Buttons.LEFT, 0);
        paint.touchDragged(35f, 35f, 400f, 300f);
        paint.touchUp(Input.Buttons.LEFT);

        assertEquals(7, tilemap.getMap().get(0, 0, 0));
        assertEquals(7, tilemap.getMap().get(0, 3, 0));
        assertEquals(0, tilemap.getMap().get(0, 4, 0));
        assertEquals(1, target.session().getHistory().getUndoCount());
        assertTrue(view.calls.isEmpty(), "the ordinary view saw none of it");
    }

    @Test
    void shouldGiveOtherButtonsAndScrollingToTheOrdinaryView() {
        TilePaintInteraction paint = interaction(true);

        paint.touchDown(5f, 5f, 400f, 300f, Input.Buttons.RIGHT, 0);
        paint.touchUp(Input.Buttons.RIGHT);
        paint.scrolled(1f);

        assertEquals(List.of("down1", "up1", "scroll"), view.calls);
        assertEquals(0, target.session().getHistory().getUndoCount());
    }

    @Test
    void shouldLeaveEverythingToTheOrdinaryViewWhenPaintingIsOff() {
        TilePaintInteraction paint = interaction(false);

        paint.touchDown(5f, 35f, 400f, 300f, Input.Buttons.LEFT, 0);
        paint.touchDragged(15f, 35f, 400f, 300f);
        paint.touchUp(Input.Buttons.LEFT);

        assertEquals(List.of("down0", "drag", "up0"), view.calls);
        assertEquals(0, tilemap.getMap().get(0, 0, 0));
    }

    @Test
    void shouldEraseWithTheEraserToolAndIgnoreCellsOutsideTheMap() {
        TilePaintInteraction paint = interaction(true);
        tilemap.getMap().set(0, 1, 1, 5);
        target.session().setTool(TilemapSession.Tool.ERASE);

        paint.touchDown(15f, 25f, 400f, 300f, Input.Buttons.LEFT, 0);
        paint.touchDragged(500f, 500f, 400f, 300f);
        paint.touchUp(Input.Buttons.LEFT);

        assertEquals(0, tilemap.getMap().get(0, 1, 1));
    }
}
