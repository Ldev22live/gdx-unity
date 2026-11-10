package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.Renderable;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.StaticColliderSource2D;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteFile;
import io.github.ldev22live.gdxunity.engine.sprite.TileMapData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A tile map (TD-066, milestone E): a grid of cells on one or more layers, drawn from a tile set, which is a {@code .sprite} whose frames are the tiles
 * (tile id = frame index + 1, 0 = empty). Tiles whose id is marked solid become fixed boxes in the 2D physics ({@link StaticColliderSource2D}), merged into
 * as few boxes as possible. The entity's position is the bottom left corner of the map; each cell is {@link #getTileWidth()} x {@link #getTileHeight()}
 * scene units, times the entity's scale.
 */
public class TilemapComponent extends BaseComponent implements Renderable, StaticColliderSource2D {
    /** Default cell size in scene units. */
    public static final float DEFAULT_TILE_SIZE = 32f;
    /** Default map size in cells. */
    public static final int DEFAULT_COLS = 20;
    public static final int DEFAULT_ROWS = 12;

    private AssetReference tileSetReference;
    private SpriteDocument tileSet;
    private String loadError;
    private float tileWidth = DEFAULT_TILE_SIZE;
    private float tileHeight = DEFAULT_TILE_SIZE;
    private boolean collides = true;
    private Color tint = new Color(Color.WHITE);
    private TileMapData map = new TileMapData(DEFAULT_COLS, DEFAULT_ROWS);
    private FrameAtlas atlas;

    public TilemapComponent() {
    }

    public TilemapComponent(AssetReference tileSetReference) {
        this.tileSetReference = tileSetReference;
    }

    // ---- configuration ----

    public TileMapData getMap() {
        return map;
    }

    /** Replaces the cells (the scene file reader and the editor's undo use this). */
    public void setMap(TileMapData map) {
        this.map = Objects.requireNonNull(map, "map cannot be null");
    }

    public AssetReference getTileSetReference() {
        return tileSetReference;
    }

    /** Changes the tile set; it is loaded again on the next draw. */
    public void setTileSetReference(AssetReference tileSetReference) {
        this.tileSetReference = tileSetReference;
        releaseAtlas();
        tileSet = null;
        loadError = null;
    }

    public float getTileWidth() {
        return tileWidth;
    }

    public float getTileHeight() {
        return tileHeight;
    }

    /** Sets the size of a cell in scene units; both must be above 0. */
    public void setTileSize(float width, float height) {
        if (!(width > 0f) || !(height > 0f)) {
            throw new IllegalArgumentException("a tile size must be above 0 but was " + width + " x " + height);
        }
        tileWidth = width;
        tileHeight = height;
    }

    public boolean isCollides() {
        return collides;
    }

    /** Whether the solid tiles block 2D physics bodies. */
    public void setCollides(boolean collides) {
        this.collides = collides;
    }

    public Color getTint() {
        return tint;
    }

    public void setTint(Color tint) {
        this.tint = Objects.requireNonNull(tint, "tint cannot be null");
    }

    // ---- tile set ----

    public boolean needsLoad() {
        return tileSet == null && tileSetReference != null && loadError == null;
    }

    public String getLoadError() {
        return loadError;
    }

    /** The loaded tile set, or null. */
    public SpriteDocument getTileSet() {
        return tileSet;
    }

    /** Reads the tile set from a file; the reason for a failure is kept in {@link #getLoadError()}. */
    public void load(FileHandle file) {
        Objects.requireNonNull(file, "file cannot be null");
        if (!file.exists()) {
            loadError = "Tile set file not found: " + file.path();
            return;
        }
        try {
            setTileSet(SpriteFile.read(file.readString("UTF-8")));
        } catch (IOException | RuntimeException e) {
            loadError = "Cannot read " + file.name() + ": " + e.getMessage();
        }
    }

    /** Uses an already read tile set (tests, and tile sets built in code). */
    public void setTileSet(SpriteDocument doc) {
        releaseAtlas();
        tileSet = Objects.requireNonNull(doc, "doc cannot be null");
        loadError = null;
    }

    // ---- drawing ----

    @Override
    public boolean isSprite() {
        return tileSet != null;
    }

    @Override
    public void render(SpriteBatch spriteBatch) {
        if (tileSet == null || getEntity() == null || spriteBatch == null) {
            return;
        }
        if (atlas == null || atlas.frames() != tileSet.getFrameCount()) {
            releaseAtlas();
            atlas = new FrameAtlas(tileSet);
        }
        Vector3 origin = getEntity().getTransform().getWorldPosition();
        Vector3 scale = getEntity().getTransform().getWorldScale();
        float cellW = tileWidth * scale.x;
        float cellH = tileHeight * scale.y;
        float mapHeight = map.getRows() * cellH;
        Color previous = new Color(spriteBatch.getColor());
        spriteBatch.setColor(tint);
        for (int l = 0; l < map.getLayerCount(); l++) {
            if (!map.getLayer(l).isVisible()) {
                continue;
            }
            for (int r = 0; r < map.getRows(); r++) {
                for (int c = 0; c < map.getCols(); c++) {
                    int tile = map.get(l, c, r);
                    if (tile == TileMapData.EMPTY || tile > atlas.frames()) {
                        continue;
                    }
                    int frame = tile - 1;
                    spriteBatch.draw(atlas.texture(), origin.x + c * cellW, origin.y + mapHeight - (r + 1) * cellH, cellW, cellH,
                        atlas.srcX(frame), atlas.srcY(frame), atlas.frameWidth(), atlas.frameHeight(), false, false);
                }
            }
        }
        spriteBatch.setColor(previous);
    }

    // ---- collision ----

    @Override
    public List<StaticBox2D> getStaticBoxes() {
        List<StaticBox2D> out = new ArrayList<>();
        if (!collides || getEntity() == null) {
            return out;
        }
        Vector3 origin = getEntity().getTransform().getWorldPosition();
        Vector3 scale = getEntity().getTransform().getWorldScale();
        float cellW = tileWidth * Math.abs(scale.x);
        float cellH = tileHeight * Math.abs(scale.y);
        float mapHeight = map.getRows() * cellH;
        for (TileMapData.CellBox box : map.solidBoxes()) {
            float width = box.cols() * cellW;
            float height = box.rows() * cellH;
            float left = origin.x + box.col() * cellW;
            float top = origin.y + mapHeight - box.row() * cellH;
            out.add(new StaticBox2D(left + width / 2f, top - height / 2f, width, height));
        }
        return out;
    }

    /**
     * Whether the point (x, y) in world coordinates lies in a solid cell of a visible layer: a scripted character can test its body against the level without the
     * physics engine. False outside the map and when the map has no entity yet.
     */
    public boolean isSolidAt(float x, float y) {
        if (getEntity() == null) {
            return false;
        }
        Vector3 origin = getEntity().getTransform().getWorldPosition();
        Vector3 scale = getEntity().getTransform().getWorldScale();
        float cellW = tileWidth * Math.abs(scale.x);
        float cellH = tileHeight * Math.abs(scale.y);
        int col = (int) Math.floor((x - origin.x) / cellW);
        int row = map.getRows() - 1 - (int) Math.floor((y - origin.y) / cellH);
        if (!map.contains(col, row)) {
            return false;
        }
        for (int l = 0; l < map.getLayerCount(); l++) {
            if (map.getLayer(l).isVisible() && map.isSolid(map.get(l, col, row))) {
                return true;
            }
        }
        return false;
    }

    private void releaseAtlas() {
        if (atlas != null) {
            atlas.dispose();
            atlas = null;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        releaseAtlas();
    }
}
