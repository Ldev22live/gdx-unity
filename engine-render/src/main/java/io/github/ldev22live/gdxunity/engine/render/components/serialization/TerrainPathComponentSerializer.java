package io.github.ldev22live.gdxunity.engine.render.components.serialization;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainPathComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Serializer for {@link TerrainPathComponent} (roadmap M3.7): kind, width, depth, paint, prefab, spacing and the control points as
 * {@code [[x, z], ...]}. An unknown kind or paint is an error that names it.
 */
public class TerrainPathComponentSerializer implements ComponentSerializer<TerrainPathComponent> {
    public static final String TYPE_ID = "TerrainPath";

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public Class<TerrainPathComponent> getComponentClass() {
        return TerrainPathComponent.class;
    }

    @Override
    public void serialize(TerrainPathComponent component, Json json) {
        json.writeValue("kind", component.getKind().name().toLowerCase(Locale.ROOT));
        json.writeValue("width", component.getWidth());
        json.writeValue("depth", component.getDepth());
        json.writeValue("paint", component.getPaint().name().toLowerCase(Locale.ROOT));
        json.writeValue("prefab", component.getPrefabPath());
        json.writeValue("spacing", component.getSpacing());
        json.writeArrayStart("points");
        for (Vector2 point : component.getPoints()) {
            json.writeArrayStart();
            json.writeValue(point.x);
            json.writeValue(point.y);
            json.writeArrayEnd();
        }
        json.writeArrayEnd();
    }

    @Override
    public TerrainPathComponent deserialize(JsonValue jsonData, Json json) {
        TerrainPathComponent component = new TerrainPathComponent();
        String kind = jsonData.getString("kind", "road");
        String paint = jsonData.getString("paint", "dirt");
        try {
            component.setKind(TerrainPath.Kind.valueOf(kind.toUpperCase(Locale.ROOT)));
            component.setPaint(Surface.valueOf(paint.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Terrain Path: unknown kind '" + kind + "' or paint '" + paint + "'", e);
        }
        component.setWidth(jsonData.getFloat("width", TerrainPathComponent.DEFAULT_WIDTH));
        component.setDepth(jsonData.getFloat("depth", TerrainPathComponent.DEFAULT_DEPTH));
        component.setPrefabPath(jsonData.getString("prefab", ""));
        component.setSpacing(jsonData.getFloat("spacing", TerrainPathComponent.DEFAULT_SPACING));
        List<Vector2> points = new ArrayList<>();
        JsonValue list = jsonData.get("points");
        if (list != null) {
            for (JsonValue point = list.child; point != null; point = point.next) {
                if (!point.isArray() || point.size != 2) {
                    throw new IllegalArgumentException("Terrain Path: a point must be [x, z]: " + point);
                }
                points.add(new Vector2(point.getFloat(0), point.getFloat(1)));
            }
        }
        component.setPoints(points);
        return component;
    }
}
