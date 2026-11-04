# Phase 13 — 3D Asset Pipeline

**Decision:** TD-026 · **Library evaluation:** `docs/reports/gdx-gltf-evaluation.md`
**Status (2026-10-29):** library-independent parts implemented; glTF import, PBR and lighting **blocked** on the
gdx-gltf compatibility spike, which needs the project owner's permission to download and run third-party code.

## Goal

Export a model from Blender (or any glTF tool), drag it into the scene, and see it with correct materials,
shadows and sky lighting; it saves, reloads, selects, moves and undoes like any other entity.

## Steps

| Step | Content | Status |
|---|---|---|
| A — import and display | glTF/GLB loading, model component (`AssetReference`), serialization, warnings for unsupported extensions | **Done** in Phase 17 (TD-030): `ModelAssets`, `ModelComponent`, PBR batch; load errors shown in Details |
| B — lighting quality | PBR shading, directional shadows, image-based lighting, tone mapping, documented quality settings | **Done** (implemented and integrated, visual check pending): [phase-13b-lighting.md](phase-13b-lighting.md), TD-032 |
| C — Content Browser | Model files listed (`AssetType.MODEL`); drag textures into the viewport to create sprites at the drop point (one undo step); dropping models explains why it is not possible yet | **Done** (part) — folders, thumbnails and reimport-on-change still open |
| D — viewport polish | Rotation-aware 3D picking and outlines for primitives; fly speed on the mouse wheel while right-dragging, shown in the tool strip | **Done** (part) — depth-correct 2D/3D compositing still open |

## Done so far

- `AssetType.MODEL` (`.gltf`, `.glb`) and `AssetType.fromPath`; sprite creation ignores non-texture selections.
- `AssetPlacement` + Content Browser drag source + scene view drop target (`ViewportInteraction.worldPointAt`:
  2D point under the mouse; in 3D the z = 0 content plane, falling back to the orbit target).
- `ViewportPicker.computeMeshLocalBounds` / `intersectOriented`: primitives are picked as oriented boxes; world
  bounds enclose the rotated box; outlines follow rotation and scale.
- Fly speed: wheel while flying multiplies speed by 1.25 per notch (10–20000 units/s); "Fly speed N" in the
  tool strip in 3D.

Tests: `AssetPlacementTest` (3), `OrientedPickingTest` (4), `SceneViewportInteraction3DTest` (+2).
`./gradlew test --continue` → exit 0, 346 tests, 0 failures. Visual checks deferred by the project owner.

## Manual checklist (when visual testing resumes)

1. Put a `.png` and a `.glb` under `assets/`; both appear in the Content Browser (Refresh) with TEXTURE / MODEL badges.
2. Drag the `.png` into the 2D view → a sprite appears under the mouse, selected; Ctrl+Z removes it.
3. Drag it into the 3D view → it lands where the mouse ray meets the content plane.
4. Drag the `.glb` → an entity with a Model component is created under the mouse (one undo step).
5. Create → Box, rotate it 45° with the gizmo; clicking its visible corners selects it, clicking just outside does not;
   the orange outline follows the rotation.
6. In 3D hold right mouse and scroll: "Fly speed" in the tool strip changes; releasing and scrolling dollies again.

## Unblocking

Approve running the spike described in the evaluation (section 6). If it passes, approve gdx-gltf as an
`engine-render` dependency; steps A and B follow.
