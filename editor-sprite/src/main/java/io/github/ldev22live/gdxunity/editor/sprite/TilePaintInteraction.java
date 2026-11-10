package io.github.ldev22live.gdxunity.editor.sprite;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Painting tiles in the 2D scene view (TD-066, milestone E): while a target is offered (the Tiles tab has "Paint in view" on and a tile map is selected) the
 * left mouse button paints with the tab's current tool, tile and layer; every other input (pan, zoom, picking when there is no target) goes to the
 * ordinary view interaction. A stroke is one undo step of the tile session, as in the tab.
 */
public final class TilePaintInteraction implements ViewportInteraction {
    /** The tile map to paint and the session that edits it. */
    public record Target(TilemapComponent tilemap, TilemapSession session) { }

    private final ViewportInteraction inner;
    private final Supplier<Target> targetSource;
    private Target painting;

    /**
     * @param inner        the ordinary 2D interaction
     * @param targetSource the tile map to paint, or null when painting is off
     */
    public TilePaintInteraction(ViewportInteraction inner, Supplier<Target> targetSource) {
        this.inner = Objects.requireNonNull(inner, "inner cannot be null");
        this.targetSource = Objects.requireNonNull(targetSource, "targetSource cannot be null");
    }

    /** The cell (column, row; row 0 is the top row) under a world point, which may lie outside the map. */
    static int[] cellAt(TilemapComponent tilemap, Vector3 world) {
        Vector3 origin = tilemap.getEntity().getTransform().getWorldPosition();
        Vector3 scale = tilemap.getEntity().getTransform().getWorldScale();
        float cellW = tilemap.getTileWidth() * Math.abs(scale.x);
        float cellH = tilemap.getTileHeight() * Math.abs(scale.y);
        int col = (int) Math.floor((world.x - origin.x) / cellW);
        int fromBottom = (int) Math.floor((world.y - origin.y) / cellH);
        return new int[] {col, tilemap.getMap().getRows() - 1 - fromBottom};
    }

    private int[] cell(Target target, float localX, float localY, float panelWidth, float panelHeight) {
        return cellAt(target.tilemap(), inner.worldPointAt(localX, localY, panelWidth, panelHeight));
    }

    @Override
    public boolean touchDown(float localX, float localY, float panelWidth, float panelHeight, int button, int modifiers) {
        Target target = targetSource.get();
        if (target != null && button == Input.Buttons.LEFT && target.tilemap().getEntity() != null) {
            painting = target;
            int[] c = cell(target, localX, localY, panelWidth, panelHeight);
            target.session().pointerDown(c[0], c[1]);
            return true;
        }
        return inner.touchDown(localX, localY, panelWidth, panelHeight, button, modifiers);
    }

    @Override
    public boolean touchDragged(float localX, float localY, float panelWidth, float panelHeight) {
        if (painting != null) {
            int[] c = cell(painting, localX, localY, panelWidth, panelHeight);
            painting.session().pointerDrag(c[0], c[1]);
            return true;
        }
        return inner.touchDragged(localX, localY, panelWidth, panelHeight);
    }

    @Override
    public boolean touchUp(int button) {
        if (painting != null && button == Input.Buttons.LEFT) {
            // The pointer is where the last drag left it: finish the stroke there
            Target target = painting;
            painting = null;
            target.session().pointerUp(lastCol(target), lastRow(target));
            return true;
        }
        return painting != null || inner.touchUp(button);
    }

    private int lastCol(Target target) {
        return target.session().getLastCol();
    }

    private int lastRow(Target target) {
        return target.session().getLastRow();
    }

    @Override
    public boolean scrolled(float amountY) {
        return inner.scrolled(amountY);
    }

    @Override
    public boolean keyDown(int keycode) {
        return inner.keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        return inner.keyUp(keycode);
    }

    @Override
    public void update(float deltaTime) {
        inner.update(deltaTime);
    }

    @Override
    public Vector3 worldPointAt(float localX, float localY, float panelWidth, float panelHeight) {
        return inner.worldPointAt(localX, localY, panelWidth, panelHeight);
    }
}
