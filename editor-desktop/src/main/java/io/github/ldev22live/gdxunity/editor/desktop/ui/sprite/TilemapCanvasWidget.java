package io.github.ldev22live.gdxunity.editor.desktop.ui.sprite;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.sprite.TilemapSession;
import io.github.ldev22live.gdxunity.engine.render.components.FrameAtlas;
import io.github.ldev22live.gdxunity.engine.sprite.TileMapData;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The tile map canvas: the cells of every visible layer drawn from the tile set, a grid, the solid tiles tinted, the rectangle being dragged, and the cell
 * under the pointer. The left button uses the current tile tool; the middle or right button pans; the wheel zooms around the pointer.
 */
final class TilemapCanvasWidget extends Widget implements Disposable {
    private static final float MIN_CELL = 4f;
    private static final float MAX_CELL = 128f;
    private static final float GRID_MIN_CELL = 12f;
    private static final float GRID_ALPHA = 0.25f;
    private static final float SOLID_ALPHA = 0.28f;
    private static final float PREVIEW_ALPHA = 0.45f;
    private static final float MARGIN = 24f;
    private static final float ZOOM_STEP = 1.25f;

    private final Supplier<TilemapSession> sessionSource;
    private final Supplier<FrameAtlas> atlasSource;
    private final Texture white;
    private float cell = 32f;
    private float originX;
    private float originY;
    private boolean placed;
    private boolean panning;
    private float lastX;
    private float lastY;
    private int hoverCol = -1;
    private int hoverRow = -1;

    TilemapCanvasWidget(Supplier<TilemapSession> sessionSource, Supplier<FrameAtlas> atlasSource) {
        this.sessionSource = Objects.requireNonNull(sessionSource, "sessionSource cannot be null");
        this.atlasSource = Objects.requireNonNull(atlasSource, "atlasSource cannot be null");
        Pixmap one = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        one.setColor(Color.WHITE);
        one.fill();
        white = new Texture(one);
        one.dispose();
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                TilemapSession session = sessionSource.get();
                if (session == null) {
                    return false;
                }
                if (button == Input.Buttons.LEFT) {
                    int[] c = toCell(x, y, session);
                    session.pointerDown(c[0], c[1]);
                } else {
                    panning = true;
                    lastX = x;
                    lastY = y;
                }
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                TilemapSession session = sessionSource.get();
                if (session == null) {
                    return;
                }
                if (panning) {
                    originX += x - lastX;
                    originY += y - lastY;
                    lastX = x;
                    lastY = y;
                } else {
                    int[] c = toCell(x, y, session);
                    session.pointerDrag(c[0], c[1]);
                    hoverCol = c[0];
                    hoverRow = c[1];
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                TilemapSession session = sessionSource.get();
                if (panning) {
                    panning = false;
                } else if (session != null) {
                    int[] c = toCell(x, y, session);
                    session.pointerUp(c[0], c[1]);
                }
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                TilemapSession session = sessionSource.get();
                if (session != null) {
                    int[] c = toCell(x, y, session);
                    hoverCol = c[0];
                    hoverRow = c[1];
                }
                return false;
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                float next = MathUtils.clamp(amountY < 0 ? cell * ZOOM_STEP : cell / ZOOM_STEP, MIN_CELL, MAX_CELL);
                float mapX = (x - originX) / cell;
                float mapY = (y - originY) / cell;
                cell = next;
                originX = x - mapX * cell;
                originY = y - mapY * cell;
                return true;
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (getStage() != null) {
                    getStage().setScrollFocus(TilemapCanvasWidget.this);
                }
            }
        });
    }

    private int[] toCell(float x, float y, TilemapSession session) {
        int rows = session.getMap().getRows();
        return new int[] {MathUtils.floor((x - originX) / cell), MathUtils.floor((originY + rows * cell - y) / cell)};
    }

    void fit() {
        TilemapSession session = sessionSource.get();
        if (session == null || getWidth() <= 0) {
            return;
        }
        TileMapData map = session.getMap();
        cell = MathUtils.clamp(Math.min((getWidth() - 2 * MARGIN) / map.getCols(), (getHeight() - 2 * MARGIN) / map.getRows()), MIN_CELL, MAX_CELL);
        originX = (getWidth() - map.getCols() * cell) / 2f;
        originY = (getHeight() - map.getRows() * cell) / 2f;
        placed = true;
    }

    void requestFit() {
        placed = false;
    }

    /** The cell under the pointer, or null when it is outside the map. */
    int[] getHover() {
        TilemapSession session = sessionSource.get();
        if (session == null || !session.getMap().contains(hoverCol, hoverRow)) {
            return null;
        }
        return new int[] {hoverCol, hoverRow};
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        TilemapSession session = sessionSource.get();
        if (session == null) {
            return;
        }
        if (!placed && getWidth() > 0) {
            fit();
        }
        TileMapData map = session.getMap();
        FrameAtlas atlas = atlasSource.get();
        batch.flush();
        if (!clipBegin()) {
            return;
        }
        float mapHeight = map.getRows() * cell;
        // Backdrop of the map area
        batch.setColor(0.13f, 0.14f, 0.17f, parentAlpha);
        batch.draw(white, getX() + originX, getY() + originY, map.getCols() * cell, mapHeight);
        for (int l = 0; l < map.getLayerCount(); l++) {
            if (!map.getLayer(l).isVisible() || atlas == null) {
                continue;
            }
            batch.setColor(1f, 1f, 1f, parentAlpha);
            for (int r = 0; r < map.getRows(); r++) {
                for (int c = 0; c < map.getCols(); c++) {
                    int tile = map.get(l, c, r);
                    if (tile == TileMapData.EMPTY || tile > atlas.frames()) {
                        continue;
                    }
                    int frame = tile - 1;
                    batch.draw(atlas.texture(), getX() + originX + c * cell, getY() + originY + mapHeight - (r + 1) * cell, cell, cell,
                        atlas.srcX(frame), atlas.srcY(frame), atlas.frameWidth(), atlas.frameHeight(), false, false);
                }
            }
        }
        drawSolid(batch, map, parentAlpha);
        if (cell >= GRID_MIN_CELL) {
            batch.setColor(1f, 1f, 1f, GRID_ALPHA * parentAlpha);
            for (int c = 0; c <= map.getCols(); c++) {
                batch.draw(white, getX() + originX + c * cell, getY() + originY, 1f, mapHeight);
            }
            for (int r = 0; r <= map.getRows(); r++) {
                batch.draw(white, getX() + originX, getY() + originY + r * cell, map.getCols() * cell, 1f);
            }
        }
        List<int[]> preview = session.getPreview();
        if (preview != null) {
            batch.setColor(1f, 1f, 1f, PREVIEW_ALPHA * parentAlpha);
            for (int[] p : preview) {
                if (map.contains(p[0], p[1])) {
                    batch.draw(white, getX() + originX + p[0] * cell, getY() + originY + mapHeight - (p[1] + 1) * cell, cell, cell);
                }
            }
        }
        int[] hover = getHover();
        if (hover != null) {
            batch.setColor(1f, 0.85f, 0.2f, parentAlpha);
            float hx = getX() + originX + hover[0] * cell;
            float hy = getY() + originY + mapHeight - (hover[1] + 1) * cell;
            batch.draw(white, hx, hy, cell, 2f);
            batch.draw(white, hx, hy + cell - 2f, cell, 2f);
            batch.draw(white, hx, hy, 2f, cell);
            batch.draw(white, hx + cell - 2f, hy, 2f, cell);
        }
        batch.setColor(Color.WHITE);
        batch.flush();
        clipEnd();
    }

    private void drawSolid(Batch batch, TileMapData map, float parentAlpha) {
        batch.setColor(1f, 0.2f, 0.2f, SOLID_ALPHA * parentAlpha);
        float mapHeight = map.getRows() * cell;
        for (TileMapData.CellBox box : map.solidBoxes()) {
            batch.draw(white, getX() + originX + box.col() * cell, getY() + originY + mapHeight - (box.row() + box.rows()) * cell,
                box.cols() * cell, box.rows() * cell);
        }
    }

    @Override
    public void dispose() {
        white.dispose();
    }
}
