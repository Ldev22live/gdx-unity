# Roadmap M3.7: Roads, rivers and cliffs along curves (2026-11-16)

Milestone M3.7 ("Splines") of [milestone-chart.md](../milestone-chart.md) (roadmap R3). **Status:**
- **Implemented and Integrated.** Done-when met in a test on the shipped valley (a template copy), and seen in the game runtime.
- **Manually Not Verified (Headless Environment):** nobody clicked a path.
- **The curve drawn over the 3D view (GL) was not seen:** this machine's saved editor layout shows the side view, where the curve lies edge-on. It is listed as **needs graphics test**.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted work up to M3.5 (listed in its reports).
- No new module, dependency or technical decision. The milestone's gate is "none".
- One new component, **Terrain Path**: a serializer, a catalog entry, round-trip tests, and the registry-driven consistency tests.
- The `.terrain` format is unchanged.

**Why this milestone:** M3.6 (scatter) waits on M2b.4, so M3.7 was next with no gate.

**Design choices (mine):**
- **A path is data on its own entity:** a `Terrain Path` component saved with the scene. It holds the control points (x and z in the ground's own space), kind, width, depth, paint layer, the prefab placed beside it and their spacing. The curve is a Catmull-Rom spline, so it passes through every point.
- **The Path tool** (Level > Terrain):
  - a click adds a point to the selected path, or starts a new road;
  - each click is one undo step;
  - the palette offers Road, River, Cliff, Undo Point, New Path, Apply, and what stands "Beside" the path.
- **Apply carves destructively, as one undo step**, and the path stays so it can be extended and applied again:
  - a **road** sits at the ground's height smoothed along the curve, its grade held to at most 0.25, level across, blending into the ground beside it, painted Dirt by default;
  - a **river** cuts a bowl-shaped channel whose bed never rises along the curve;
  - a **cliff** raises the ground on the curve's left, with a steep face.
  - The prefab is placed every `spacing` units, alternating sides, turned along the path and standing on the carved ground.
- **`PrefabService.instantiateCommand`** returns a placement without running it, so the carve and every placement fit into one `CompoundCommand`. `instantiate` now uses it.

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Editable curves | **Met** | Points are added by clicking (each an undo step), the last removed with Undo Point; kind, width, depth, paint, prefab and spacing are editable in Details and undoable; the path is saved with the scene and reopens with its points (`TerrainPathEditingTest`, `RoadAcrossAHillTest`). Points are not dragged after placing (section 6) |
| 2 | Rivers, roads and cliffs that deform terrain | **Met** | `TerrainPathTest`: the road climbs a round hill at an even grade and is level across; the river bed never rises along a falling, bumpy slope; the cliff raises the left side by its height with a steep face, and leaves the right side alone; each undoes exactly |
| 3 | Place meshes | **Met** | Apply places pines along both sides as part of the same undo step; undo removes them (`TerrainPathEditingTest`; at least 6 pines on the valley road) |
| 4 | Done when: a road follows a curve across a hill | **Met** | `RoadAcrossAHillTest`, on a template copy of the shipped valley. A four-point road from the valley floor beside the hero goes up and over the left ridge, which is more than 250 units above the floor. The road holds a grade of at most 0.25 (plus a 3-unit tolerance) at every cell, rises over 200 units, and is level across on the slope (within 3 units at 30 to each side). It is painted Dirt with pines along it. Apply is one undo step; undo restores every height and removes the pines; redo lays it again. Saved and reopened, the path keeps its points. The shipped valley file is unchanged |
| 5 | Agent parity (chart rule 8) | **Met** | `terrain_path` makes and applies a path in one undo step. It refuses fewer than two points, points off the ground, an unknown kind, and a prefab that does not exist |
| 6 | UX gate | **Partly** | The game runtime and the editor's Details were seen; the palette and overlay were not clicked or seen |

## 2. What was built

- **`engine-terrain`:** `TerrainPath` (`sample`, `carve`, `Kind`; `MAX_ROAD_GRADE`, `EDGE_SHARE`).
- **`engine-render`:** `TerrainPathComponent` and `TerrainPathComponentSerializer` (type `TerrainPath`).
- **`engine-desktop`:** the serializer is in `GameComponentSerializers` (38 types).
- **`editor-core`:** the catalog entry "Terrain Path" and `PrefabService.instantiateCommand`.
- **`editor-terrain`:**
  - `LevelSession`: the Path tool, `isGround`, `addPathPoint`, `removeLastPathPoint`, `setPathKind`, `setPathPrefab`, `startNewPath`, `applyPath`, `applyPathCommand`, `listObjects(folder)`;
  - `LevelViewportInteraction`: Path clicks;
  - the agent tool `TerrainPathTool`.
- **`editor-desktop`:**
  - `LevelPalette`: the Path row and status;
  - `TerrainPathComponentEditorProvider`: Details, registered in `InspectorPanel`;
  - `Collider3DRenderer`: draws every path's curve and control points on the ground, coloured by kind, re-sampled only when the points or ground change;
  - `EditorApplication`: registers `terrain_path`.
- **Docs:** the chart (M3.7 row, R3 line), `INDEX.md`, `FEATURE_TEST_MATRIX.md`, `AI_AGENT_ARCHITECTURE_AUDIT.md`.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-terrain:test` | 0 | 46 (+3 `TerrainPathTest`) |
| `./gradlew :editor-terrain:test` | 1, 0 | First a compile error: I referred to a helper class that does not exist; the test now builds its own serializer registry. Then `LevelEditingTest` pinned "Terrain offers exactly the brushes"; Path is now deliberately a Terrain tool, so the expectation was updated. Then 41 pass |
| `./gradlew :editor-desktop:test --tests "*RoadAcrossAHillTest"` | 0 | The valley road passes |
| `./gradlew :engine-desktop:test` | 1, 0 | `GameComponentSerializersTest` pins the type count; updated to 38 and checks `TerrainPath`. Then 113 |
| `./gradlew :engine-render:test` | 1, 0 | My serializer test assumed compact JSON (the scene is pretty-printed); fixed with a pattern. Then 173 |
| `./gradlew :editor-core:test` | 0 | 513 (1 skipped, as before), including the catalog and consistency tests with the new component |
| `./gradlew :editor-desktop:test` | 0 | 163 (+1), after the Details editor was added |
| `GDXUNITY_SCREENSHOT=... ./gradlew :engine-desktop:run --args="--project <copy of build/m3-7-road/Road> --scene scenes/terrain-3d.json --smoke-test=120"` | 0 | "120 of 120 frames, 0 runtime error(s)" |
| `GDXUNITY_SCREENSHOT=... GDXUNITY_EDITOR_SELECT=Road ./gradlew :editor-desktop:run --args="--project <same copy> ... --smoke-test"` | 0 (twice) | 180 frames, 0 errors |

**No full-suite run:** the last one was stopped for low memory. Every module M3.7 touches was run on its own and passes.

**Screenshots** (looked at):
- [m3-7/runtime-road-across-ridge.png](m3-7/runtime-road-across-ridge.png): the game runtime. A brown dirt road runs from beside Hero up and away toward the ridge, with pines alternating along its sides. The first pine stands next to Hero, because the road was started beside him.
- [m3-7/editor-road-path.png](m3-7/editor-road-path.png): the editor with the Road selected. Details lists its 4 points and the Kind ROAD, Width 120, Depth 40, Paint DIRT, Beside the Pine Tree prefab, and Spacing 400.
  - The first screenshot showed "No editable properties": the Details editor was missing, and was then added.
  - The view is this machine's saved side ("X axis") pane, where the curve overlay cannot be judged.

## 4. Failures found and fixed

- **The component had no Details editor**, so most of its settings could not be changed in the editor. Found in the first editor screenshot; fixed.
- The rest are listed in section 3, all in tests: pinned counts and behaviour, or my own test mistakes.

## 5. Diff review

- **New:**
  - `TerrainPath`, `TerrainPathComponent`, `TerrainPathComponentSerializer`, `TerrainPathTool`, `TerrainPathComponentEditorProvider`;
  - tests `TerrainPathTest`, `TerrainPathComponentSerializerTest`, `TerrainPathEditingTest`, `RoadAcrossAHillTest`;
  - this report and `docs/reports/m3-7/`.
- **Changed:**
  - `GameComponentSerializers`, `ComponentCatalog`, `PrefabService`;
  - `LevelSession`, `LevelViewportInteraction`;
  - `LevelPalette`, `Collider3DRenderer`, `InspectorPanel`, `EditorApplication`;
  - tests `LevelEditingTest`, `GameComponentSerializersTest`;
  - the docs listed in section 2.
- No shipped asset changed: the evidence is on a template copy under `editor-desktop/build/m3-7-road`.

## 6. Known limitations and next steps

- **Carving is destructive:** Apply again carves on top of the last carve; undo is the way back. A live, re-shapable path would need the ground's shape before the path to be kept.
- **Points are added by clicking**, not dragged once placed. To move a point: Undo Point, then click again.
- **Road and river ends are rounded.** A cliff's ends are open.
- **Placed meshes** can land near other objects at the path's start, and they do not avoid each other.
- **The curve overlay** has not been seen. It is GL code: **needs graphics test**.
- **Web:** the player ignores terrain.
- **Manual check for a person:**
  1. Level > Terrain > Path.
  2. Click three or four points across a hill; the curve appears on the ground.
  3. Choose Beside > Pine Tree, then Apply. A road with pines appears.
  4. Ctrl+Z: the ground, paint and pines all go back.
  5. Change Width in Details and Apply again.
  6. Try River and Cliff.
- **Next in R3:**
  - M3.8 (level of detail for large maps) needs only M3.1 and has no gate.
  - M3.6 waits on M2b.4; M3.9 waits on M3.4 (done) and has no gate.

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
