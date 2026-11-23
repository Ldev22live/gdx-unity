# Roadmap M3.4: Terrain paint layers, holes and a water level (2026-11-16)

Milestone M3.4 of [milestone-chart.md](../milestone-chart.md) (roadmap R3), built to [TD-089](../TECHNICAL_DECISIONS.md) (**approved by the owner on 2026-11-16**, after this report was first written). **Status:**
- **Implemented and Integrated.** Done-when met in tests and seen in the game runtime.
- **Manually Not Verified (Headless Environment):** nobody painted, cut holes or set water with the mouse.
- **The single full test run was stopped for low memory** (section 3). Every module was then run on its own and passes.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted work up to M3c.8 (listed in its reports).
- No new module or dependency.
- **One new decision, TD-089**, written up for review because the milestone's gate is "none"; **the owner approved it on 2026-11-16.** It extends the `.terrain` format with optional fields only, so every existing terrain reads and draws as before. It replaces TD-083's hard Ground paint, which TD-083 itself lists as a limit.

**Why this milestone now.** R3c has no startable work left. The next critical-path item (M2b.1) needs owner approvals (TD, Legal, Dep). M3.4 has no gate and is part of journey step 2 ("sculpt terrain, paint ground").

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Splat weights per layer (grass, rock, sand, snow, dirt) | **Met** | Six paint layers (grass, sand, snow, gravel, and the new rock and dirt), each a weight 0 to 255 per point over the base ground, never summing past 255. `TerrainLayersHolesWaterTest`, `TerrainSurfaceTest` |
| 2 | A slope is repainted with soft edges | **Met** | On a template copy of the shipped valley, the left slope (over 150 units above the floor) is painted rock. Several points across the edge are part rock and part grass (`LayersHolesWaterEditingTest.shouldRepaintASlopeOfTheShippedValley...`). A synthetic slope fades monotonically from the centre (`TerrainLayersHolesWaterTest`). Seen in the runtime frame |
| 3 | A hole is cut | **Met** | The Hole tool cuts through the viewport; Ctrl fills; each is one undo step and the file follows. The drawn mesh leaves out the cells around the hole, and the collider has no ground there. On real Jolt in editor Play, Play is refused over the hole ("Nothing under Player to stand on"), and a player walking into it from beside falls through. **Mutation:** with holes not passed to the collider, the fall test and the height-field test fail |
| 4 | A water level | **Met** (drawn) | Add, Up, Down, Remove, each one undo step that the file follows; drawn as a translucent plane. It does not collide (TD-089 limits) |
| 5 | Existing terrains unchanged | **Met** | Optional fields are written only when used; a plain terrain writes the old text. An older file with fewer layers reads. Bad data is refused with the reason |
| 6 | Agent parity (chart rule 8) | **Met** | `terrain_edit` sculpts, paints, cuts holes or sets water. A three-step plan (paint, hole, water) is one undo step |
| 7 | UX gate | **Partly** | Seen in the game runtime; the editor palette was not looked at or clicked |

## 2. What was built

- **`engine-terrain`:**
  - `Surface` gains Rock and Dirt (codes 5 and 6) and paint layers;
  - `Terrain` gains layer weights, holes (`isHole`, `isCellOpen`, `isHoleAt`) and a water level;
  - `TerrainBrush.Type.HOLE`;
  - `TerrainStroke` paints soft weights (`PAINT_RATE` 2, the brush falloff, other layers scaled down to make room; Ctrl wipes) and cuts or fills holes;
  - `TerrainDelta` carries weight and hole changes (the old constructors are kept);
  - `TerrainIO` reads and writes `layerCount` and `layers`, `holes` and `waterLevel`, strictly.
- **`engine-render`:**
  - `TerrainMeshBuilder` blends layer colours by weight, writes triangles that skip open cells, and makes the water quad;
  - `TerrainGpu` re-uploads indices with each chunk rebuild (on the instance's own mesh part) and draws the water with blending;
  - `TerrainHeightFields` turns hole points into `NO_COLLISION`.
- **`editor-terrain`:**
  - the Hole tool;
  - `TerrainWaterCommand`;
  - `LevelSession.setWaterLevel`, `addWater` (a quarter of the way up the height range) and `moveWater` (20 units a step);
  - agent tool `TerrainEditTool` (`terrain_edit`).
- **`editor-desktop`:**
  - the level palette offers Rock and Dirt, shows Strength for painting, and has a Water row (Add, Up, Down, Remove) and a Hole hint;
  - the editor registers `terrain_edit`.
- **Docs:** TD-089 (approved 2026-11-16) and a pointer in TD-083's limits; the chart (M3.4 row, R3 line), `INDEX.md`, `FEATURE_TEST_MATRIX.md`, `AI_AGENT_ARCHITECTURE_AUDIT.md`.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-terrain:test` | 1, 1, 0 | First, the three TD-083 tests that pinned hard paint and the code list failed, as expected. I rewrote them for weights; they were not deleted. Then one new assertion used a point the later Dirt stroke also covered; fixed. Then 39 pass |
| `./gradlew :engine-render:test` | 0 | Passes, including +3 in `TerrainRenderingTest` and +1 in `TerrainHeightFieldsTest` |
| `./gradlew :editor-terrain:test` | 1, 1, 0 | First, `LevelEditingTest`'s paint test pinned hard paint; updated to read the dominant surface. Then the Jolt test found Play refused over the hole (correct, M3b.5's ground check); reworked to walk in from beside it. Then all pass |
| Mutation: hole points not made `NO_COLLISION` | 1, 1 | The fall test and the height-field test fail; restored |
| `./gradlew :editor-desktop:compileJava` | 0 | Compiles |
| `GDXUNITY_SCREENSHOT=... ./gradlew :engine-desktop:run --args="--project <copy of build/m3-4-valley/Valley> --scene scenes/terrain-3d.json --smoke-test=120"` | 0 | "120 of 120 frames, 0 runtime error(s)" |
| `./gradlew cleanTest test --continue` | **stopped** | Stopped by the session because the machine was low on memory (not a test failure). Before it stopped: `editor-animation` 17, `editor-audio` 57, `editor-character` 10, `editor-core` 513 (1 skipped, as before), all 0 failures. `editor-desktop` was running; the engine modules had not run yet |

**Completed afterwards (2026-11-16, one module at a time to spare memory):** `./gradlew :engine-physics:test`, `:engine-desktop:test`, `:editor-desktop:test`, each exit 0: 60, 113 and 162 tests, 0 failures. With the modules run during the milestone, every module has now passed since the last change. The untouched modules last passed in the M3c.8 full run.

**Screenshot** (looked at): [m3-4/runtime-valley-layers-hole-water.png](m3-4/runtime-valley-layers-hole-water.png). The game runtime on the edited valley copy, looking down on Hero:
- a grey rock patch on the left slope and a brown dirt patch on the right, each fading softly into the grass;
- the hole, open to the background;
- the water as a translucent sheet on the valley floor around Hero.

The water's edge on the nearly flat floor is jagged and striped (depth fighting between almost coplanar surfaces), recorded as a limit.

## 4. Failures found and fixed

- The test failures in section 3. All were tests of the old hard paint or my own test set-up, none a regression. The rewritten tests keep checking the same things (the centre painted, the far ground and heights untouched, undo and redo, Ctrl's inverse) under the new rules.
- **A design fact the test surfaced:** M3b.5's ground check correctly refuses Play over a hole. The hole is real to physics before Play starts, too.

## 5. Diff review

- **New:**
  - `TerrainWaterCommand`, `TerrainEditTool`;
  - tests `TerrainLayersHolesWaterTest`, `LayersHolesWaterEditingTest`;
  - this report and `docs/reports/m3-4/`.
- **Changed:**
  - `Surface`, `Terrain`, `TerrainBrush`, `TerrainStroke`, `TerrainDelta`, `TerrainIO`;
  - `TerrainMeshBuilder`, `TerrainGpu`, `TerrainHeightFields`;
  - `LevelSession`, `LevelPalette`, `EditorApplication`;
  - tests `TerrainSurfaceTest`, `TerrainRenderingTest`, `TerrainHeightFieldsTest`, `LevelEditingTest`, `OwnerJourneyRegressionTest`;
  - the docs listed in section 2.
- No shipped asset changed (the evidence is a template copy under `editor-terrain/build/m3-4-valley`). No files outside this repository were touched.
- Line endings kept; `TerrainDelta.java` was rewritten whole with LF, as it was.

## 6. Known limitations and next steps

- **Water:** does not collide or float anything, and its edge shimmers on nearly flat ground.
- **Layers:** colours, not textures (texture splatting waits on R2 materials).
- **Holes:** at least two cells wide.
- **Web:** the player ignores terrain.
- **Manual check for a person:**
  1. Open the Terrain sample and press Level > Terrain.
  2. Ground with Rock: paint a slope and watch the soft edge; Ctrl wipes it.
  3. Hole: cut, then Ctrl to fill.
  4. Water: Add, Up, Down, Remove.
  5. Undo each step.
  6. Play: walk into a hole and fall; you cannot start Play standing over one.
- **Next:**
  - In R3, M3.5 (erosion, ramp, heightmap import and export) has no gate and can follow.

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
