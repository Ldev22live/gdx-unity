package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.sprite.AutoTileRule;
import io.github.ldev22live.gdxunity.engine.sprite.TileMapData;

import java.util.ArrayList;
import java.util.List;

/** Serializer for {@link TilemapComponent}: the tile set's path, the cell size, the layers of tile ids, the solid tiles and the auto-tile rules. */
public class TilemapComponentSerializer implements ComponentSerializer<TilemapComponent> {
    public static final String TYPE_ID = "TilemapComponent";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<TilemapComponent> getComponentClass() {
        return TilemapComponent.class;
    }

    @Override
    public void serialize(TilemapComponent component, Json json) {
        TileMapData map = component.getMap();
        json.writeValue("tileSet", component.getTileSetReference() != null ? component.getTileSetReference().getPath() : null);
        json.writeValue("tileWidth", component.getTileWidth());
        json.writeValue("tileHeight", component.getTileHeight());
        json.writeValue("collides", component.isCollides());
        json.writeValue("cols", map.getCols());
        json.writeValue("rows", map.getRows());
        json.writeValue("solid", map.getSolidTiles(), int[].class);
        json.writeArrayStart("layers");
        for (int l = 0; l < map.getLayerCount(); l++) {
            json.writeObjectStart();
            json.writeValue("name", map.getLayer(l).getName());
            json.writeValue("visible", map.getLayer(l).isVisible());
            json.writeValue("cells", map.copyCells(l), int[].class);
            json.writeObjectEnd();
        }
        json.writeArrayEnd();
        json.writeArrayStart("rules");
        for (AutoTileRule rule : map.getRules()) {
            json.writeObjectStart();
            json.writeValue("name", rule.name());
            json.writeValue("tiles", rule.tiles(), int[].class);
            json.writeObjectEnd();
        }
        json.writeArrayEnd();
        Color c = component.getTint();
        json.writeObjectStart("tint");
        json.writeValue("r", c.r);
        json.writeValue("g", c.g);
        json.writeValue("b", c.b);
        json.writeValue("a", c.a);
        json.writeObjectEnd();
    }

    @Override
    public TilemapComponent deserialize(JsonValue data, Json json) {
        TilemapComponent component = new TilemapComponent();
        String path = data.getString("tileSet", null);
        if (path != null && !path.isBlank()) {
            component.setTileSetReference(new AssetReference(path));
        }
        component.setTileSize(data.getFloat("tileWidth", TilemapComponent.DEFAULT_TILE_SIZE), data.getFloat("tileHeight", TilemapComponent.DEFAULT_TILE_SIZE));
        component.setCollides(data.getBoolean("collides", true));
        TileMapData map = new TileMapData(data.getInt("cols", TilemapComponent.DEFAULT_COLS), data.getInt("rows", TilemapComponent.DEFAULT_ROWS));
        JsonValue solid = data.get("solid");
        if (solid != null) {
            for (int tile : solid.asIntArray()) {
                map.setSolid(tile, true);
            }
        }
        JsonValue layers = data.get("layers");
        if (layers != null && layers.size > 0) {
            List<String> names = new ArrayList<>();
            List<Boolean> visible = new ArrayList<>();
            List<int[]> cells = new ArrayList<>();
            for (JsonValue layer = layers.child; layer != null; layer = layer.next) {
                names.add(layer.getString("name", "Layer"));
                visible.add(layer.getBoolean("visible", true));
                JsonValue values = layer.get("cells");
                cells.add(values != null ? values.asIntArray() : new int[map.getCols() * map.getRows()]);
            }
            map.loadLayers(names, visible, cells);
        }
        JsonValue rules = data.get("rules");
        if (rules != null) {
            for (JsonValue rule = rules.child; rule != null; rule = rule.next) {
                map.putRule(new AutoTileRule(rule.getString("name"), rule.get("tiles").asIntArray()));
            }
        }
        component.setMap(map);
        JsonValue tint = data.get("tint");
        if (tint != null) {
            component.setTint(new Color(tint.getFloat("r", 1f), tint.getFloat("g", 1f), tint.getFloat("b", 1f), tint.getFloat("a", 1f)));
        }
        return component;
    }
}
