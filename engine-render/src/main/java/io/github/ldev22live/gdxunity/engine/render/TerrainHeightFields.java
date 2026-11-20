package io.github.ldev22live.gdxunity.engine.render;

import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightField3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightFieldSource;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;

import java.util.Objects;

/**
 * Gives Terrain Collider 3D the heights of the entity's Terrain component (TD-087), from the same shared {@link TerrainLibrary} the
 * renderer draws from, so the ground that collides is the ground that is drawn, holes included (TD-089). Needs no GL context.
 */
public class TerrainHeightFields implements HeightFieldSource {
    /** {@link #revisionOf} for an entity with no readable terrain. */
    static final long NO_TERRAIN_REVISION = -1L;

    private final TerrainLibrary library;

    public TerrainHeightFields(TerrainLibrary library) {
        this.library = Objects.requireNonNull(library, "library cannot be null");
    }

    @Override
    public HeightField3D heightFieldOf(Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Terrain terrain = terrainOf(entity);
        int columns = terrain.getResolutionX();
        int rows = terrain.getResolutionZ();
        float[] heights = terrain.copyHeights();
        if (terrain.hasHoles()) {
            // A hole point collides with nothing, so the cells around it are open, as they are drawn (TD-089)
            byte[] holes = terrain.copyHoles();
            for (int i = 0; i < heights.length; i++) {
                if (holes[i] != 0) {
                    heights[i] = HeightField3D.NO_COLLISION;
                }
            }
        }
        return new HeightField3D(columns, rows, terrain.getCellSize(), terrain.getCellSize(), terrain.xAt(0), terrain.zAt(0), heights);
    }

    /** Changes with every edit of the terrain, and when the library hands out a different terrain for the file (a reload). */
    @Override
    public long revisionOf(Entity entity) {
        TerrainComponent component = entity.getComponent(TerrainComponent.class);
        if (component == null || component.getTerrainPath().isEmpty()) {
            return NO_TERRAIN_REVISION;
        }
        Terrain terrain = library.get(component.getTerrainPath());
        if (terrain == null) {
            return NO_TERRAIN_REVISION;
        }
        return ((long) System.identityHashCode(terrain) << Integer.SIZE) | (terrain.getRevision() & 0xFFFF_FFFFL);
    }

    private Terrain terrainOf(Entity entity) {
        TerrainComponent component = entity.getComponent(TerrainComponent.class);
        if (component == null) {
            throw new IllegalStateException(entity.getName() + " has a Terrain Collider 3D but no Terrain. Add a Terrain component, or "
                + "remove the collider.");
        }
        if (component.getTerrainPath().isEmpty()) {
            throw new IllegalStateException(entity.getName() + " has a Terrain Collider 3D, but its Terrain names no .terrain file.");
        }
        Terrain terrain = library.get(component.getTerrainPath());
        if (terrain == null) {
            throw new IllegalStateException(entity.getName() + " has a Terrain Collider 3D, but its terrain cannot be read: "
                + library.getError(component.getTerrainPath()));
        }
        return terrain;
    }
}
