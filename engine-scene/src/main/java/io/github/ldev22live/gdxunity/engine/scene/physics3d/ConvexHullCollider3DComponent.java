package io.github.ldev22live.gdxunity.engine.scene.physics3d;

/**
 * A convex hull around the mesh the entity shows (its glTF model or mesh primitive), for any motion type (TD-052).
 * The mesh is read once, when physics starts, with the entity's scale at that moment; changing the scale during play
 * does not change the hull. A hull of more than {@link PhysicsSystem3D#MAX_HULL_POINTS} points is reduced.
 */
public class ConvexHullCollider3DComponent extends Collider3DComponent {
}
