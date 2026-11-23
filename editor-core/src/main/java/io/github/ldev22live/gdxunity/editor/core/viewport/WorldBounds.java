package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import io.github.ldev22live.gdxunity.editor.core.picking.ViewportPicker;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The extent of a whole scene, for the full-map view (UI M2): a world-space box around every active entity's terrain, shapes,
 * models and dynamic meshes. Terrain counts with its full grid and its lowest and highest point, so a map with nothing on it
 * is still framed. Lights, cameras and empty entities have no extent and do not count.
 */
public final class WorldBounds {
    private WorldBounds() {
    }

    /**
     * The box around everything in {@code scene} that has an extent, or empty when nothing has one (an empty scene).
     *
     * @param terrains finds the terrain a Terrain component names (the editor's terrain library); null when it is missing
     */
    public static Optional<BoundingBox> of(Scene scene, Function<String, Terrain> terrains) {
        BoundingBox union = null;
        for (BoundingBox box : boxes(scene, terrains)) {
            union = union == null ? new BoundingBox(box) : union.ext(box);
        }
        return Optional.ofNullable(union);
    }

    /**
     * One world-space box per thing with an extent (an entity's shapes, models and meshes together; each terrain on its own),
     * for framing that measures each box rather than one box around all of them, whose empty corners would waste the view.
     * Empty for an empty scene.
     */
    public static List<BoundingBox> boxes(Scene scene, Function<String, Terrain> terrains) {
        Objects.requireNonNull(scene, "scene cannot be null");
        Objects.requireNonNull(terrains, "terrains cannot be null");
        List<BoundingBox> boxes = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy()) {
                continue;
            }
            BoundingBox mesh = ViewportPicker.computeMeshBounds(entity);
            if (mesh != null) {
                boxes.add(new BoundingBox(mesh));
            }
            for (TerrainComponent component : entity.getComponents(TerrainComponent.class)) {
                if (!component.isEnabled()) {
                    continue;
                }
                Terrain terrain = terrains.apply(component.getTerrainPath());
                if (terrain != null) {
                    boxes.add(terrainBox(terrain).mul(entity.getTransform().getWorldMatrix()));
                }
            }
        }
        return boxes;
    }

    /**
     * Whether every enabled model of an active entity has been loaded, or has failed to load: until then a model has no bounds,
     * and a box from {@link #of} would leave it out (a landscape whose ground is a model would be framed as its few shapes).
     */
    public static boolean isSettled(Scene scene) {
        Objects.requireNonNull(scene, "scene cannot be null");
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy()) {
                continue;
            }
            for (ModelComponent model : entity.getComponents(ModelComponent.class)) {
                ModelComponent.ModelInfo info = model.getModelInfo();
                if (model.isEnabled() && info.bounds() == null && info.error() == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A terrain's box in its own space: centred on its origin across x and z, from its lowest to its highest point. */
    static BoundingBox terrainBox(Terrain terrain) {
        float[] range = terrain.heightRange();
        float halfX = terrain.getSizeX() / 2f;
        float halfZ = terrain.getSizeZ() / 2f;
        return new BoundingBox(new Vector3(-halfX, range[0], -halfZ), new Vector3(halfX, range[1], halfZ));
    }
}
