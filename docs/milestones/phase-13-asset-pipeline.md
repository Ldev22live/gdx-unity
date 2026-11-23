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
| C — Content Browser | Model files listed (`AssetType.MODEL`); drag textures and models into the viewport (`AssetPlacement`); **folders** (breadcrumb, folder rows with asset counts, ".." row; a search looks through every folder), **reimport on change** (`AssetChangeWatcher` rescans every 2 s; changed or deleted textures and models are dropped from their caches, the list is refreshed, the status bar says what changed) and **texture thumbnails** (up to 64 per folder, textures up to 4 MB) | **Done** (2026-11-08; implemented and integrated; thumbnails and the look of the folder list seen once in an in-process capture, mouse use **Manually Not Verified**) — **model thumbnails** now drawn too (`ModelThumbnails`, 2026-11-09; seen in one capture) |
| D — viewport polish | Rotation-aware 3D picking and outlines for primitives; fly speed on the mouse wheel while right-dragging, shown in the tool strip; **depth-correct 2D/3D compositing** (TD-063: with a perspective camera 2D shapes and sprites are tested against the 3D depth at their entity's Z) | **Done** (2026-11-09; implemented and integrated; seen in two in-process captures of `depth-compositing-3d.json`, not in the editor window by a person) |

## Done so far

- `AssetType.MODEL` (`.gltf`, `.glb`) and `AssetType.fromPath`; sprite creation ignores non-texture selections.
- `AssetPlacement` + Content Browser drag source + scene view drop target (`ViewportInteraction.worldPointAt`:
  2D point under the mouse; in 3D the z = 0 content plane, falling back to the orbit target).
- `ViewportPicker.computeMeshLocalBounds` / `intersectOriented`: primitives are picked as oriented boxes; world
  bounds enclose the rotated box; outlines follow rotation and scale.
- Fly speed: wheel while flying multiplies speed by 1.25 per notch (10–20000 units/s); "Fly speed N" in the
  tool strip in 3D.

13-C remainder (2026-11-08): `AssetBrowserFilterModel` (current folder, `getSubfolders`, `openFolder`, `up`, `getBreadcrumb`, `revalidate`; tests +5), `AssetChangeWatcher` (editor-core; `AssetChangeWatcherTest`, 7), the Content Browser panel (breadcrumb, folder rows, thumbnails) and `EditorApplication.onAssetsChangedOnDisk`. `./gradlew :editor-core:test :editor-desktop:test`: 371 and 79 tests, 0 failures; editor smoke test 0 errors; one capture (`GDXUNITY_SCREENSHOT`) shows folder rows with counts, the "Assets" breadcrumb and the thumbnail of `libgdx.png`.

Tests: `AssetPlacementTest` (3), `OrientedPickingTest` (4), `SceneViewportInteraction3DTest` (+2).
`./gradlew test --continue` → exit 0, 346 tests, 0 failures. Visual checks deferred by the project owner.

## Manual checklist (when visual testing resumes)

00. Open `samples/depth-compositing-3d.json` in the editor's 3D view and in Play: the blue wall hides the left half of the green panel behind it, and the orange panel is drawn over the red cube it stands in front of. Orbit the camera: the panel and the wall keep their order as they should.

0. Content Browser: click a folder row, then a breadcrumb part and ".. (up)"; the list follows. Type in the filter: it finds files in every folder. Edit a `.png` that is used by a sprite (in another program) while the editor runs: within about 2 seconds the status bar says "Assets changed on disk" and the sprite shows the new picture; the same for a `.glb`.

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
