package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import io.github.ldev22live.gdxunity.engine.scene.Entity;

/**
 * Reads the triangles that a mesh collider or convex hull collider is built from (TD-052). {@code engine-scene} does not
 * know models or primitives, so the renderer module implements this and registers it with the scene
 * ({@code Scene.setColliderGeometry}).
 */
public interface ColliderGeometrySource {

    /**
     * The mesh shown by {@code entity} (its glTF model or its mesh primitive), in the entity's local space.
     *
     * @throws IllegalStateException with a message a user can act on when the entity shows no mesh, the model failed to
     *                               load or it has no triangles
     */
    MeshGeometry3D geometryOf(Entity entity);
}
