package io.github.ldev22live.gdxunity.engine.scene.physics3d;

/**
 * The exact triangles of the mesh the entity shows (its glTF model or mesh primitive), for terrain, ramps and bowls
 * (TD-052). Jolt supports triangle meshes on static bodies only: on a dynamic or kinematic body this collider is refused,
 * naming the entity. The mesh is read once, when physics starts, with the entity's scale at that moment; changing the
 * scale during play does not change it.
 */
public class MeshCollider3DComponent extends Collider3DComponent {
}
