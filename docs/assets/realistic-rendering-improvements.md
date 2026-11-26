# Realistic rendering improvements

Reuse the desktop PBR renderer, sky and directional lighting, shadows, tone mapping, terrain LOD and object culling. Existing render quality profiles are LOW, MEDIUM and HIGH. They primarily adjust shadow and environment map sizes. No new ULTRA tier is defined by this work.

Preserve the original glTF material channels through conversion. Check roughness and normal map convention in the runtime, along with tiling, false seams and alpha masking. For terrain, prefer existing editable heightfields and paint layers. Preserve box/capsule blocking volumes where gameplay depends on them. Measure high foliage density, material splits, skinned vertices and shadow cost before increasing defaults. The [asset support matrix](../ASSET_SUPPORT_MATRIX.md) records format and backend gaps.

## Applied to the priority scenes

- Action RPG Valley uses the existing Rocky Terrain 02 and Wood Floor Deck PBR assignments and adds the Clear Day atmosphere. Its heightfield and lighting remain editable in the scene.
- Action RPG Isometric uses the existing rock, brick and wood material assets on the courtyard obstacles and ground.
- Terrain Platformer uses textured walls, platforms and a crate. The 3D profile uses the rock material on its starting platform and stepped stones. The bright final platform and flag still mark the destination.

The visual pass retains primitive obstacle silhouettes at the gameplay boundaries. The profile protagonists are still boxes. Resolving character assets and animation remains a separate acquisition and conversion task.
