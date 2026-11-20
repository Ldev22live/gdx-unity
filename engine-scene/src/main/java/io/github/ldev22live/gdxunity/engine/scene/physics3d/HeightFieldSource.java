package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import io.github.ldev22live.gdxunity.engine.scene.Entity;

/**
 * Reads the ground a {@link TerrainCollider3DComponent} collides with (TD-087). {@code engine-scene} does not know terrain, so the
 * renderer module implements this from the entity's Terrain component and registers it with the scene
 * ({@code Scene.setHeightFields}), as it does for meshes ({@link ColliderGeometrySource}).
 */
public interface HeightFieldSource {
    /**
     * The heights of the terrain {@code entity} shows, in scene units in the entity's local space.
     *
     * @throws IllegalStateException with a message a person can act on when the entity has no terrain, or its file is missing or
     *                               broken
     */
    HeightField3D heightFieldOf(Entity entity);

    /**
     * A number that changes whenever the heights {@link #heightFieldOf} would return change, so physics can rebuild only then;
     * any value when the entity has no terrain.
     */
    long revisionOf(Entity entity);
}
