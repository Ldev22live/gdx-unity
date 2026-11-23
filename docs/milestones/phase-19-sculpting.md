# Phase 19 — Sculpting

**Decision:** TD-038 (proposed 2026-11-02; scope, structure and "desktop first" chosen by the project owner)
**Reference tools:** Mudbox and ZBrush for the workflow only. Original implementation, generic names (TD-020).
**Status (2026-11-02):** 19-A implemented (headless core). 19-B partly implemented: a sculpt can be created, drawn in
the editor's 3D view, sculpted with the mouse and undone, through menus and keys; **there is no brush panel and no brush
outline yet, sculpts are invisible in the game runtime and the web player, and mouse sculpting was not tried in the
running editor**. 19-C (glTF export and runtime use) done (2026-11-08, TD-062).
Reports: [19-A](../reports/phase-19a-report.md), [19-B](../reports/phase-19b-report.md).

## Goal

Sculpt a dense mesh with brushes in the editor and use the result in scenes: pick a base shape (a sphere, a
primitive, or an imported model), push, pull, smooth and drag its surface with undo, then export it as glTF so a
Model component can place it like any other model.

## Scope of the first milestone (chosen by the owner)

**In:** fixed topology. Triangles are never added or removed; brushes move vertices. Brushes: draw, inflate, smooth,
flatten, pinch, grab. Radius, strength, falloff, spacing, subtract. Mirror symmetry on X, Y and Z (tested on X; the three axes share one code path). One undo step per
stroke. Save and load a `.sculpt` file. Export to glTF.

**Out (not planned for this milestone):** multi-resolution subdivision levels, dynamic topology, masks, layers,
polypaint and vertex colors, UV unwrapping or texture baking, stencils and alpha stamps, decimation or retopology,
tablet pen pressure (libGDX gives no pressure), sculpting in the streamed web editor beyond a triangle limit.

## Modules

| Module | Holds | Depends on | Status |
|---|---|---|---|
| `engine-sculpt` | `SculptMesh`, `SculptMeshFactory`, `Subdivision`, `TriangleBvh`, `SculptSurface`, `BrushSettings`, `BrushType`, `Falloff`, `SymmetrySettings`, `SculptStroke`, `StrokeDelta`, `SculptMeshIO` | `engine-core` | 19-A done |
| `editor-sculpt` | `SculptAssets`, `SculptSession` (open sculpts, brush, mode; the renderer's mesh source), `SculptDocument`, `SculptDynamicMesh`, `SculptStrokeCommand`, `SculptViewportInteraction`, `SculptActions` | `editor-core`, `engine-sculpt` | 19-B done except the panel and outline |
| `engine-sculpt` (19-B addition) | `MeshChunks`: the mesh cut into runs of at most 60,000 vertices (16-bit indices), with a revision per chunk | `engine-core` | 19-B done |
| `engine-render` (extended) | `DynamicMesh` and `DynamicMeshSource` interfaces, `DynamicMeshComponent` (+ serializer), `DynamicMeshGpu` (re-uploads only changed chunks), `GlChunkTarget`, drawing in `SceneRenderer3D`. glTF export of a mesh is still to do (gdx-gltf 2.3.0 has `net.mgsx.gltf.exporters`; its types stay inside this module, TD-030) | `engine-sculpt` is **not** a dependency | 19-B done; export 19-C |

## 19-A — Sculpting core (done)

| Part | Content |
|---|---|
| Mesh | `SculptMesh`: indexed triangles; vertex-to-triangle and vertex-to-neighbour adjacency; area-weighted vertex normals that can be recomputed for a few vertices. Inputs are validated with the offending index in the message. |
| Starting shapes | `SculptMeshFactory.cubeSphere(radius, resolution)`: even triangles, no pole pinching, exact shared seams (12 resolution² triangles). `weld(...)` merges split vertices of imported meshes. `Subdivision.midpoint(mesh, levels)` raises the triangle count without changing the shape. |
| Spatial queries | `TriangleBvh`: ray picking and "vertices inside a sphere", with partial refit after a dab (only the paths from moved triangles to the root). |
| Consistency | `SculptSurface` is the only way positions change: it recomputes normals for moved vertices and their neighbours and refits the hierarchy, and counts a revision for the renderer. |
| Brushes | `SculptStroke`: dabs spaced by `spacing x radius` along the path (independent of mouse event rate), at most 64 dabs per move, mirrored copies without double application on the mirror plane, grab with weights fixed at the start, settings snapshot at the start, cancel. |
| Undo | `StrokeDelta`: touched vertices with positions before and after; applying it is exact. |
| File | `SculptMeshIO`: `.sculpt` binary (signature, version, counts, floats, ints), rejects wrong signature, version, truncation, impossible counts and corrupt meshes without huge allocations. |

### Measured speed (2026-11-02, this development PC, JDK 21, single thread)

480,000 triangles / 240,000 vertices (a cube-sphere of resolution 200): mesh and adjacency 0.4 s, hierarchy 0.5 s,
about 90 MB of heap, one pick 0.01 ms. A dab costs 0.2 ms (radius 0.05 of a unit sphere), 0.9 ms (0.15) and 4.6 to
8.2 ms (0.4, about 30,000 vertices); a grab move costs 5.5 ms at radius 0.4. So brush maths is not the limit on
the desktop; uploading changed vertices to the GPU will be (19-B).

## 19-B — Editor integration (partly done)

| Step | Content | Status |
|---|---|---|
| 1 | `editor-sculpt` module; `Create > Sculpt` makes `sculpts/Sculpt.sculpt` (a 128-resolution sphere of radius 50: 196,608 triangles) and an entity with a `DynamicMeshComponent`, and turns sculpt mode on | Done, tested headless |
| 2 | Drawing: the renderer asks a `DynamicMeshSource` (the session) for the geometry of each `DynamicMeshComponent` every frame and uploads only the chunks whose revision changed; picking and framing use the mesh bounds | Done. Upload logic tested with a fake target; the OpenGL part checked by starting the real editor on a 120,000-triangle sculpt in Play mode (300 frames, no errors) and looking at a screenshot |
| 3 | Sculpt mode in the 3D view: left-drag sculpts the selected sculpt, Shift smooths, Ctrl inverts, Alt+left (orbit), right (fly) and middle (pan) are unchanged, clicks that miss the sculpt select as usual, one undo step per stroke | Done and tested headless with a camera, an entity moved and scaled, and a refusing editor state. **Not tried with a real mouse in the running editor** |
| 4 | Brush controls: Tools menu (enter/exit, six brushes, mirror X/Y/Z), `]` `[` brush size, `=` `-` strength, Ctrl+Alt+S save; a status line in the viewport shows the brush; modified sculpts are saved when the editor closes | Done as menu items and keys. **No brush panel with sliders, no falloff or spacing control in the UI, no hover outline** |
| 5 | Start from an imported model: weld, optionally subdivide, sculpt | Done, tested headless: Create > Sculpt From Selected makes a sculpt from the mesh of the selected entity (a glTF model at its import scale, or a primitive), welds split vertices, subdivides towards 200,000 triangles (at most 4 levels, never for a mesh that already has that many), writes `sculpts/<Name>.sculpt` and a new entity at the same position; the original stays. Rotation and entity scale are not copied. Needs a graphics run to see it |
| 6 | The Content Browser lists `.sculpt` files, drag-and-drop into the viewport, a Details editor for the component | Done, tested headless: `AssetType.SCULPT`, `AssetPlacement` places a dropped sculpt as an entity, `DynamicMeshComponentEditorProvider` shows file, triangle count, unsaved state and colour with Sculpt and Save buttons. The editor starts (smoke test); dragging and the Details panel were **not tried by hand** |

## 19-C — Using the result (done, TD-062)

| Step | Content | Status |
|---|---|---|
| 1 | Export to `.glb` with a material, so it appears in the Content Browser. | Done: `SculptGltfWriter` (hand-written, chunks of 60,000 vertices), File > Export Sculpt as Model, the Details button. Read back through gdx-gltf in a test: same triangles, same size |
| 2 | Placing the exported model with the existing Model component, prefabs, and the web player. | Done: sample `sculpted-rock-3d.json` (files `sculpts/SculptedRock.sculpt`, `models/SculptedRock.glb`); the game runtime ran it 120 frames with 0 errors, and Babylon loaded the file in a test |
| 3 | Triangle limit and a warning when sculpting in the streamed web editor (software OpenGL). | Done as advice: smaller new sculpts, a 100,000-triangle warning, chosen when `LIBGL_ALWAYS_SOFTWARE=1`. **Not tried in the container** (no Docker here) |

## Acceptance criteria (19-A)

| # | Criterion | Result |
|---|---|---|
| 1 | A mesh of at least 400,000 triangles can be built, picked and edited | Met: 480,000 triangles, timings above |
| 2 | Every brush changes only vertices inside its radius | Met: tested for every brush, with and without subtract |
| 3 | Normals and the hierarchy are correct after every kind of brush, with X symmetry | Met: compared with a full recompute and a fresh hierarchy after each brush, with and without subtract |
| 4 | Undo and redo are exact, also for strokes with many overlapping dabs | Met |
| 5 | Dab placement does not depend on how often the cursor reports | Met (same result for one move and ten moves) |
| 6 | A sculpt can be saved and loaded without loss, and bad files are refused with a reason | Met |
| 7 | Brush maths fast enough for interactive use | Met on the desktop for the CPU part; **GPU upload not measured** (19-B) |
| 8 | Tests would fail without the code | Met: five injected bugs were each caught; a sixth gap (undo of many-dab strokes) was found and closed (see the report) |

## Manual checklist (when 19-B exists)

Nothing can be checked visually yet: 19-A has no user interface. This list is a placeholder for 19-B.

1. Create a sculpt, switch to Sculpt mode, and drag over the surface with each brush: the surface follows the cursor
   and the brush ring stays on it.
2. Undo and redo a long stroke: the surface returns exactly.
3. Turn on X mirror: both sides change together; a stroke on the centre line is not doubled.
4. Export to glTF and place it with a Model component: it looks the same, lit and shadowed.

## Known limitations

- **Sculpts do not appear in the game runtime or the web player yet.** Only the editor provides the geometry; a
  `DynamicMeshComponent` in a game draws nothing until 19-C adds a loader for `.sculpt` files or glTF export.
- Undoing the creation of a sculpt entity leaves its `.sculpt` file in the project.
- Brush radius is in the sculpt's own units, so on an entity scaled by 2 the brush covers twice the world distance.
- Closing the editor saves modified sculpts without asking; if one cannot be written the editor stays open.
- No user interface and no rendering yet (19-A is the headless core).
- Fixed topology: a stroke cannot add detail where the mesh is too coarse; subdivide first.
- A coarse mesh subdivided by `Subdivision.midpoint` is faceted until it is smoothed (no smoothing subdivision yet).
- `weld` merges by rounding to a grid, so near-duplicates on opposite sides of a grid line may stay apart.
- Brushes work in the mesh's local space: scaling or rotating the entity does not change brush sizes in mesh units.
- Single-threaded; the hierarchy never rebalances, so extreme deformation makes picking slower over time.
